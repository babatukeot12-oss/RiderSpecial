package com.riderspecial.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {

    private val adminPassport = "dinakeot1990"
    private var configurableTrialDays = 30L

    private var plan1MonthPrice = 49
    private var plan2MonthPrice = 70
    private var plan3MonthPrice = 99

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var selectedLang = "EN" // "EN", "HI", "AS"

    private var fbUrl = ""
    private var waUrl = ""
    private var twUrl = ""
    private var ytUrl = ""
    private var broadcastMessage = ""
    private var customQrTitle = "Rider Payment QR"
    private var customQrBase64 = ""

    private var pendingPhotoCallback: ((String) -> Unit)? = null
    private var pendingEngineActivateCallback: (() -> Unit)? = null
    private val reqPickImage = 4091
    private val reqEngineLocationPerm = 4092

    private var latestLat: Double? = null
    private var latestLng: Double? = null
    private var lastLocationFixTime: Long = 0L
    private var lastMovementText: String = "Live"

    private val locationHandler = Handler(Looper.getMainLooper())
    private var isLocationLoopRunning = false
    private var activeLocationRiderId: String? = null
    private var activeLocationRiderName: String = ""
    private var activeLocationVehicleKey: String? = null
    private var activeLocationPhotoB64: String = ""
    private var activeLocationHardwareId: String = ""

    private enum class VehicleCategory(val key: String, val label: String) {
        BIKE("bike", "Bike"),
        AUTO("auto", "Auto"),
        CAB("cab", "Cab"),
        E_RICKSHAW("e_rickshaw", "E-Rickshaw"),
        EV("ev", "EV"),
        SCOOTY("scooty", "Scooty");

        companion object {
            fun fromKey(raw: String?): VehicleCategory? {
                val k = raw?.trim()?.lowercase(Locale.US) ?: return null
                return when (k) {
                    "bike", "motorcycle", "moto" -> BIKE
                    "auto", "autorickshaw", "auto_rickshaw", "three_wheeler", "3w" -> AUTO
                    "cab", "car", "taxi" -> CAB
                    "e_rickshaw", "erickshaw", "e-rickshaw", "toto" -> E_RICKSHAW
                    "ev", "electric", "electric_vehicle" -> EV
                    "scooty", "scooter" -> SCOOTY
                    else -> null
                }
            }

            fun safeLabel(raw: String?): String = fromKey(raw)?.label ?: "Bike"
        }
    }

    private fun createVehicleIconDrawable(category: VehicleCategory): Drawable {
        val accent = when (category) {
            VehicleCategory.BIKE -> Color.parseColor("#38BDF8")
            VehicleCategory.AUTO -> Color.parseColor("#FACC15")
            VehicleCategory.CAB -> Color.parseColor("#A78BFA")
            VehicleCategory.E_RICKSHAW -> Color.parseColor("#34D399")
            VehicleCategory.EV -> Color.parseColor("#22D3EE")
            VehicleCategory.SCOOTY -> Color.parseColor("#FB923C")
        }
        return object : Drawable() {
            override fun draw(canvas: Canvas) {
                val w = bounds.width().toFloat()
                val h = bounds.height().toFloat()
                if (w <= 0f || h <= 0f) return
                val pad = minOf(w, h) * 0.08f
                val bgRect = RectF(pad, pad, w - pad, h - pad)
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#0F172A")
                    style = Paint.Style.FILL
                }
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accent
                    style = Paint.Style.STROKE
                    strokeWidth = maxOf(2f, minOf(w, h) * 0.05f)
                }
                val r = minOf(w, h) * 0.22f
                canvas.drawRoundRect(bgRect, r, r, bgPaint)
                canvas.drawRoundRect(bgRect, r, r, borderPaint)

                val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accent
                    style = Paint.Style.STROKE
                    strokeWidth = maxOf(2.2f, minOf(w, h) * 0.065f)
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val fillFg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accent
                    style = Paint.Style.FILL
                }

                when (category) {
                    VehicleCategory.BIKE, VehicleCategory.SCOOTY -> {
                        val wheelY = h * 0.68f
                        val wheelR = minOf(w, h) * 0.13f
                        canvas.drawCircle(w * 0.28f, wheelY, wheelR, fg)
                        canvas.drawCircle(w * 0.72f, wheelY, wheelR, fg)
                        val body = Path().apply {
                            moveTo(w * 0.28f, wheelY)
                            lineTo(w * 0.46f, h * 0.48f)
                            lineTo(w * 0.64f, h * 0.48f)
                            lineTo(w * 0.72f, wheelY)
                            moveTo(w * 0.60f, h * 0.48f)
                            lineTo(w * 0.56f, h * 0.34f)
                            lineTo(w * 0.66f, h * 0.34f)
                        }
                        canvas.drawPath(body, fg)
                    }
                    VehicleCategory.AUTO, VehicleCategory.E_RICKSHAW -> {
                        val wheelY = h * 0.70f
                        val wheelR = minOf(w, h) * 0.12f
                        canvas.drawCircle(w * 0.30f, wheelY, wheelR, fg)
                        canvas.drawCircle(w * 0.70f, wheelY, wheelR, fg)
                        val cabin = Path().apply {
                            moveTo(w * 0.22f, h * 0.62f)
                            lineTo(w * 0.26f, h * 0.34f)
                            lineTo(w * 0.56f, h * 0.34f)
                            lineTo(w * 0.76f, h * 0.54f)
                            lineTo(w * 0.76f, h * 0.62f)
                            close()
                        }
                        canvas.drawPath(cabin, fg)
                    }
                    VehicleCategory.CAB, VehicleCategory.EV -> {
                        val wheelY = h * 0.70f
                        val wheelR = minOf(w, h) * 0.11f
                        canvas.drawCircle(w * 0.28f, wheelY, wheelR, fg)
                        canvas.drawCircle(w * 0.72f, wheelY, wheelR, fg)
                        val carBody = RectF(w * 0.18f, h * 0.48f, w * 0.82f, h * 0.65f)
                        canvas.drawRoundRect(carBody, 6f, 6f, fg)
                        val roof = Path().apply {
                            moveTo(w * 0.30f, h * 0.48f)
                            lineTo(w * 0.38f, h * 0.33f)
                            lineTo(w * 0.64f, h * 0.33f)
                            lineTo(w * 0.72f, h * 0.48f)
                        }
                        canvas.drawPath(roof, fg)
                        if (category == VehicleCategory.EV) {
                            canvas.drawCircle(w * 0.50f, h * 0.56f, maxOf(2.5f, w * 0.04f), fillFg)
                        }
                    }
                }
            }

            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
            @Deprecated("Deprecated in Java")
            override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        }
    }

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (isLocationMocked(location)) return
            if (location.latitude != 0.0 || location.longitude != 0.0) {
                val prevLat = latestLat
                val prevLng = latestLng
                latestLat = location.latitude
                latestLng = location.longitude
                lastLocationFixTime = System.currentTimeMillis()
                lastMovementText = if (prevLat != null && prevLng != null) {
                    val moved = FloatArray(1)
                    Location.distanceBetween(prevLat, prevLng, location.latitude, location.longitude, moved)
                    if (moved[0] > 8f) "Moving" else "Stopped"
                } else {
                    "Live"
                }
                val rId = activeLocationRiderId
                if (isLocationLoopRunning && !rId.isNullOrBlank() && hasRiderLocationPermission(this@MainActivity)) {
                    pushRiderLiveLocation(
                        ctx = this@MainActivity,
                        riderId = rId,
                        riderName = activeLocationRiderName,
                        vehicleKey = activeLocationVehicleKey,
                        photoB64 = activeLocationPhotoB64,
                        hardwareId = activeLocationHardwareId
                    )
                }
            }
        }
        @Deprecated("Deprecated in Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private val locationRunnable = object : Runnable {
        override fun run() {
            if (!isLocationLoopRunning) return
            val rId = activeLocationRiderId
            if (!rId.isNullOrBlank() && hasRiderLocationPermission(this@MainActivity) && isDeviceLocationEnabled(this@MainActivity)) {
                pushRiderLiveLocation(
                    ctx = this@MainActivity,
                    riderId = rId,
                    riderName = activeLocationRiderName,
                    vehicleKey = activeLocationVehicleKey,
                    photoB64 = activeLocationPhotoB64,
                    hardwareId = activeLocationHardwareId
                )
            }
            locationHandler.postDelayed(this, 8_000L)
        }
    }

    private val bgDark = Color.parseColor("#060A14")
    private val headerDark = Color.parseColor("#0A1224")
    private val cardDark = Color.parseColor("#101A30")
    private val cardElevated = Color.parseColor("#182747")
    private val borderSlate = Color.parseColor("#2A3F66")
    private val accentGreen = Color.parseColor("#15803D")
    private val accentGreenBright = Color.parseColor("#22C55E")
    private val accentBlue = Color.parseColor("#1D4ED8")
    private val accentPurple = Color.parseColor("#6D28D9")
    private val accentRed = Color.parseColor("#B91C1C")
    private val accentGold = Color.parseColor("#EAB308")
    private val accentTeal = Color.parseColor("#0369A1")
    private val textWhite = Color.parseColor("#F8FAFC")
    private val textMuted = Color.parseColor("#94A3B8")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ctx: Context = this
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        selectedLang = prefs.getString("app_lang", "EN") ?: "EN"

        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                applyTtsLanguageAndSoftVoice()
                ttsReady = true
            }
        }

        val hardwareId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown_device"

        loadAndStart(ctx, hardwareId)
    }

    private fun tr(en: String, hi: String, asText: String): String {
        return when (selectedLang) {
            "HI" -> hi
            "AS" -> asText
            else -> en
        }
    }

    private fun speakSwitchState(ctx: Context, soundEnabled: Boolean, featureName: String, isOn: Boolean) {
        if (!soundEnabled) return
        val stateWord = if (isOn) {
            tr("is on", "चालू है", "চালু হৈছে")
        } else {
            tr("is off", "बंद है", "বন্ধ হৈছে")
        }
        speakText(ctx, true, "$featureName $stateWord")
    }

    private fun countWords(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        return trimmed.split(Regex("\\s+")).size
    }

    private fun computeRemainingDays(
        trialStart: Long,
        activeUntil: Long,
        customTrialDays: Long?,
        nowMillis: Long
    ): Long {
        val effectiveTrialDays = (customTrialDays ?: configurableTrialDays).coerceAtLeast(0L)
        val trialEnd = if (trialStart > 0L) trialStart + TimeUnit.DAYS.toMillis(effectiveTrialDays) else 0L
        val validUntil = maxOf(activeUntil, trialEnd)
        if (validUntil <= nowMillis) return 0L
        val diffMillis = validUntil - nowMillis
        val dayMs = TimeUnit.DAYS.toMillis(1)
        return (diffMillis + dayMs - 1L) / dayMs
    }

    private fun shouldAcceptOrderDirectly(pickupOn: Boolean, dropOn: Boolean, fareOn: Boolean): Boolean {
        return !pickupOn && !dropOn && !fareOn
    }

    private fun evaluateOrderAgainstRules(
        pickupOn: Boolean,
        maxPickupKm: Double,
        dropOn: Boolean,
        maxDropKm: Double,
        fareOn: Boolean,
        minFareRs: Double,
        incomingPickupKm: Double,
        incomingDropKm: Double,
        incomingFareRs: Double
    ): Boolean {
        if (shouldAcceptOrderDirectly(pickupOn, dropOn, fareOn)) {
            return true
        }
        if (pickupOn && incomingPickupKm > maxPickupKm) {
            return false
        }
        if (dropOn && incomingDropKm > maxDropKm) {
            return false
        }
        if (fareOn && incomingFareRs < minFareRs) {
            return false
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun isLocationMocked(loc: Location): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                loc.isMock
            } else {
                loc.isFromMockProvider
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun hasRiderLocationPermission(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == 0 ||
                ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == 0
        } else {
            true
        }
    }

    private fun isDeviceLocationEnabled(ctx: Context): Boolean {
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                lm.isLocationEnabled
            } else {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun requireLocationAndGpsForEngine(ctx: Context, onReady: () -> Unit) {
        if (!hasRiderLocationPermission(ctx)) {
            pendingEngineActivateCallback = onReady
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    reqEngineLocationPerm
                )
            } else {
                showPermissionRequiredGateDialog(ctx, onReady)
            }
            return
        }

        if (!isDeviceLocationEnabled(ctx)) {
            showDeviceGpsSettingsDialog(ctx)
            return
        }

        registerLocationUpdates(ctx)
        onReady()
    }

    private fun showDeviceGpsSettingsDialog(ctx: Context) {
        AlertDialog.Builder(ctx)
            .setTitle(tr("📡 Phone GPS / Location is OFF", "📡 फ़ोन का GPS / Location बंद है", "📡 ফোনৰ GPS / Location বন্ধ আছে"))
            .setMessage(
                tr(
                    "Phone Location (GPS) must be turned ON to activate the engine.",
                    "इंजन चालू करने के लिए फ़ोन की Location (GPS) चालू करना अनिवार्य है।",
                    "ইঞ্জিন চালু কৰিবলৈ ফোনৰ Location (GPS) অন কৰাটো বাধ্যতামূলক।"
                )
            )
            .setCancelable(false)
            .setPositiveButton(tr("Open Location Settings", "Location सेटिंग खोलें", "Location ছেটিং খোলক")) { _, _ ->
                try {
                    ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } catch (_: Exception) {
                }
            }
            .setNegativeButton(tr("Cancel", "बंद रखें", "বন্ধ ৰাখক"), null)
            .show()
    }

    private fun showPermissionRequiredGateDialog(ctx: Context, onRetryGranted: () -> Unit) {
        AlertDialog.Builder(ctx)
            .setTitle(tr("📍 Location Permission Required", "📍 लोकेशन परमिशन ज़रूरी है", "📍 লোকেচন অনুমতি প্ৰয়োজন"))
            .setMessage(
                tr(
                    "Engine cannot start without Location permission. Please allow Location permission.",
                    "लोकेशन परमिशन दिए बिना इंजन चालू नहीं होगा। कृपया परमिशन दें।",
                    "লোকেচন অনুমতি নিদিয়াকৈ ইঞ্জিন চালু নহয়। অনুগ্ৰহ কৰি অনুমতি দিয়ক।"
                )
            )
            .setCancelable(false)
            .setPositiveButton(tr("Allow Permission", "परमिशन दें", "অনুমতি দিয়ক")) { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    pendingEngineActivateCallback = onRetryGranted
                    requestPermissions(
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
                        reqEngineLocationPerm
                    )
                }
            }
            .setNeutralButton(tr("App Settings", "सेटिंग्स खोलें", "এপ ছেটিংছ")) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${ctx.packageName}")
                    }
                    ctx.startActivity(intent)
                } catch (_: Exception) {
                }
            }
            .setNegativeButton(tr("Keep Off", "बंद रखें", "বন্ধ ৰাখক")) { _, _ ->
                pendingEngineActivateCallback = null
            }
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == reqEngineLocationPerm) {
            val callback = pendingEngineActivateCallback
            pendingEngineActivateCallback = null
            if (hasRiderLocationPermission(this)) {
                if (!isDeviceLocationEnabled(this)) {
                    showDeviceGpsSettingsDialog(this)
                } else {
                    registerLocationUpdates(this)
                    callback?.invoke()
                }
            } else {
                showPermissionRequiredGateDialog(this) {
                    callback?.invoke()
                }
            }
        }
    }

    private fun startLocationLoop(
        riderId: String,
        riderName: String,
        vehicleKey: String?,
        photoB64: String,
        hardwareId: String
    ) {
        activeLocationRiderId = riderId
        activeLocationRiderName = riderName
        activeLocationVehicleKey = vehicleKey
        activeLocationPhotoB64 = photoB64
        activeLocationHardwareId = hardwareId
        if (hasRiderLocationPermission(this) && isDeviceLocationEnabled(this)) {
            isLocationLoopRunning = true
            registerLocationUpdates(this)
            locationHandler.removeCallbacks(locationRunnable)
            locationHandler.post(locationRunnable)
        }
    }

    private fun stopLocationLoop() {
        isLocationLoopRunning = false
        locationHandler.removeCallbacks(locationRunnable)
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.removeUpdates(gpsListener)
        } catch (_: Exception) {
        }
    }

    private fun registerLocationUpdates(ctx: Context) {
        try {
            if (!hasRiderLocationPermission(ctx) || !isDeviceLocationEnabled(ctx)) return
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                try {
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 4_000L, 2f, gpsListener, Looper.getMainLooper())
                    val gpsLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    if (gpsLoc != null && !isLocationMocked(gpsLoc) && (gpsLoc.latitude != 0.0 || gpsLoc.longitude != 0.0)) {
                        latestLat = gpsLoc.latitude
                        latestLng = gpsLoc.longitude
                        lastLocationFixTime = System.currentTimeMillis()
                    }
                } catch (_: Exception) {
                }
            } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                try {
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 6_000L, 5f, gpsListener, Looper.getMainLooper())
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun pushRiderLiveLocation(
        ctx: Context,
        riderId: String,
        riderName: String,
        vehicleKey: String?,
        photoB64: String,
        hardwareId: String
    ) {
        if (!hasRiderLocationPermission(ctx) || !isDeviceLocationEnabled(ctx)) return
        val nowMillis = System.currentTimeMillis()
        val coords = readCurrentLocation(ctx)
        val cleanVehicle = VehicleCategory.fromKey(vehicleKey)?.key ?: "bike"
        try {
            val rootRef = FirebaseDatabase.getInstance().reference.child("captain_smart_pro")
            val riderMap = mutableMapOf<String, Any>(
                "riderId" to riderId,
                "fullName" to riderName,
                "mobile" to riderId,
                "vehicleType" to cleanVehicle,
                "hardwareId" to hardwareId,
                "photoB64" to photoB64,
                "online" to true,
                "liveLocationOn" to true,
                "movementStatus" to lastMovementText,
                "isSharingLocation" to (coords != null),
                "lastSeen" to nowMillis
            )
            if (coords != null) {
                riderMap["lat"] = coords.first
                riderMap["lng"] = coords.second
                riderMap["locationUpdatedAt"] = nowMillis
            }
            rootRef.child("riders").child(riderId).updateChildren(riderMap)
        } catch (_: Exception) {
        }
    }

    private fun readCurrentLocation(ctx: Context): Pair<Double, Double>? {
        if (!hasRiderLocationPermission(ctx) || !isDeviceLocationEnabled(ctx)) return null
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val now = System.currentTimeMillis()
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                try {
                    val gpsLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    val isFreshGps = gpsLoc != null && (now - gpsLoc.time) <= TimeUnit.MINUTES.toMillis(5)
                    if (gpsLoc != null && isFreshGps && !isLocationMocked(gpsLoc) && (gpsLoc.latitude != 0.0 || gpsLoc.longitude != 0.0)) {
                        latestLat = gpsLoc.latitude
                        latestLng = gpsLoc.longitude
                        lastLocationFixTime = now
                        return Pair(gpsLoc.latitude, gpsLoc.longitude)
                    }
                } catch (_: Exception) {
                }
            }
            val cachedLat = latestLat
            val cachedLng = latestLng
            if (cachedLat != null && cachedLng != null && (cachedLat != 0.0 || cachedLng != 0.0)) {
                Pair(cachedLat, cachedLng)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun formatLastSyncAge(nowMillis: Long): String {
        if (lastLocationFixTime <= 0L) {
            return tr("GPS: Waiting", "GPS: प्रतीक्षा में", "GPS: অপেক্ষাৰত")
        }
        val sec = ((nowMillis - lastLocationFixTime) / 1000L).coerceAtLeast(0L)
        return when {
            sec < 60L -> tr("GPS: ${sec}s ago", "GPS: ${sec} सेकंड पहले", "GPS: ${sec} ছেকেণ্ড পূৰ্বে")
            else -> tr("GPS: ${sec / 60L}m ago", "GPS: ${sec / 60L} मिनट पहले", "GPS: ${sec / 60L} মিনিট পূৰ্বে")
        }
    }

    private fun shareRiderLiveLocationWithFamily(ctx: Context, riderName: String, riderId: String) {
        if (!hasRiderLocationPermission(ctx)) {
            Toast.makeText(
                ctx,
                tr("Please allow Location permission first", "कृपया पहले लोकेशन परमिशन दें", "অনুগ্ৰহ কৰি প্ৰথমে লোকেচন অনুমতি দিয়ক"),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (!isDeviceLocationEnabled(ctx)) {
            showDeviceGpsSettingsDialog(ctx)
            return
        }
        registerLocationUpdates(ctx)
        val coords = readCurrentLocation(ctx)
        if (coords == null) {
            Toast.makeText(
                ctx,
                tr(
                    "Waiting for GPS signal... Keep GPS ON and try again in a few seconds.",
                    "GPS सिग्नल मिल रहा है... GPS चालू रखें और कुछ सेकंड में फिर दबाएं।",
                    "GPS সংকেত পোৱা হৈছে... GPS অন ৰাখক আৰু কেইছেকেণ্ডমান পাছত পুনৰ টিপক।"
                ),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val mapsLink = "https://maps.google.com/?q=${coords.first},${coords.second}"
        val shareBody = tr(
            "📍 Live Location of $riderName ($riderId)\nStatus: $lastMovementText\nMap: $mapsLink",
            "📍 $riderName ($riderId) की लाइव लोकेशन\nस्थिति: $lastMovementText\nमैप लिंक: $mapsLink",
            "📍 $riderName ($riderId) ৰ লাইভ লোকেচন\nঅৱস্থা: $lastMovementText\nমেপ লিংক: $mapsLink"
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Live Location - $riderName")
            putExtra(Intent.EXTRA_TEXT, shareBody)
        }
        try {
            ctx.startActivity(
                Intent.createChooser(
                    shareIntent,
                    tr("Share Live Location via", "लाइव लोकेशन शेयर करें", "লাইভ লোকেচন শ্বেয়াৰ কৰক")
                )
            )
        } catch (_: Exception) {
            Toast.makeText(ctx, mapsLink, Toast.LENGTH_LONG).show()
        }
    }

    private fun deleteRiderFromAdmin(ctx: Context, targetRiderId: String, onDone: () -> Unit) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("trial_start_$targetRiderId")
            .remove("active_until_$targetRiderId")
            .remove("blocked_$targetRiderId")
            .remove("local_chat_$targetRiderId")
            .apply()
        if ((prefs.getString("rider_mobile_id", "")?.trim() ?: "") == targetRiderId) {
            prefs.edit()
                .remove("rider_full_name")
                .remove("rider_mobile_id")
                .remove("rider_vehicle_type")
                .remove("rider_photo_b64")
                .apply()
        }
        try {
            val root = FirebaseDatabase.getInstance().reference.child("captain_smart_pro")
            root.child("riders").child(targetRiderId).removeValue()
            root.child("payment_requests").child(targetRiderId).removeValue()
        } catch (_: Exception) {
        }
        Toast.makeText(
            ctx,
            "Rider $targetRiderId deleted. Can create fresh account with same number.",
            Toast.LENGTH_LONG
        ).show()
        onDone()
    }

    private fun resetLocalLoginForFreshAccount(ctx: Context, hardwareId: String) {
        stopLocationLoop()
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val prevRiderId = prefs.getString("rider_mobile_id", "")?.trim().orEmpty()
        prefs.edit()
            .remove("rider_full_name")
            .remove("rider_mobile_id")
            .remove("rider_vehicle_type")
            .remove("rider_photo_b64")
            .remove("total_rides")
            .remove("total_earnings")
            .remove("total_distance")
            .apply()
        if (prevRiderId.isNotEmpty()) {
            prefs.edit()
                .remove("trial_start_$prevRiderId")
                .remove("active_until_$prevRiderId")
                .remove("blocked_$prevRiderId")
                .remove("local_chat_$prevRiderId")
                .apply()
        }
        Toast.makeText(
            ctx,
            tr("Previous login cleared for fresh account", "पुराना लॉगिन हटाया गया, नया अकाउंट बनाएं", "পুৰণি লগইন আঁতৰোৱা হ'ল, নতুন একাউণ্ট বনাওক"),
            Toast.LENGTH_LONG
        ).show()
        showRiderRegistrationScreen(ctx, hardwareId, "", "", "", "")
    }

    private fun openAccessibilityScreen(ctx: Context) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(ctx, "Open Phone Settings > Accessibility", Toast.LENGTH_LONG).show()
        }
    }

    private fun applyTtsLanguageAndSoftVoice() {
        val engine = tts ?: return
        val targetLocale = when (selectedLang) {
            "HI" -> Locale("hi", "IN")
            "AS" -> Locale("as", "IN")
            else -> Locale.US
        }
        val res = engine.setLanguage(targetLocale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.language = if (selectedLang == "AS") Locale("hi", "IN") else Locale.US
        }
        engine.setPitch(1.08f)
        engine.setSpeechRate(0.95f)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val voices = engine.voices
                if (voices != null) {
                    val softVoice = voices.firstOrNull { v ->
                        val n = v.name.lowercase(Locale.US)
                        val langMatch = v.locale.language == targetLocale.language || v.locale.language == "hi" || v.locale.language == "en"
                        langMatch && (n.contains("female") || n.contains("wavenet-a") || n.contains("wavenet-c") || n.contains("wavenet-e") || n.contains("sfg"))
                    }
                    if (softVoice != null) {
                        engine.voice = softVoice
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun openLanguageSelectionDialog(ctx: Context, onSelected: () -> Unit) {
        val options = arrayOf(
            "English",
            "हिन्दी (Hindi)",
            "অসমীয়া (Assamese)"
        )
        val codes = arrayOf("EN", "HI", "AS")
        val currentIdx = codes.indexOf(selectedLang).coerceAtLeast(0)

        AlertDialog.Builder(ctx)
            .setTitle(tr("🌐 Select Language", "🌐 भाषा चुनें", "🌐 ভাষা বাছনি কৰক"))
            .setSingleChoiceItems(options, currentIdx) { dialog, which ->
                val chosen = codes.getOrElse(which) { "EN" }
                selectedLang = chosen
                applyTtsLanguageAndSoftVoice()
                ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
                    .edit()
                    .putString("app_lang", selectedLang)
                    .apply()
                val msg = when (selectedLang) {
                    "HI" -> "हिन्दी भाषा चुनी गई है"
                    "AS" -> "অসমীয়া ভাষা বাছনি কৰা হৈছে"
                    else -> "English language selected"
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                speakText(ctx, true, msg)
                dialog.dismiss()
                onSelected()
            }
            .setNegativeButton(tr("Cancel", "बंद करें", "বন্ধ কৰক"), null)
            .show()
    }

    private fun pickPhotoFromGallery(ctx: Context, onBase64Ready: (String) -> Unit) {
        pendingPhotoCallback = onBase64Ready
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        try {
            startActivityForResult(intent, reqPickImage)
        } catch (_: Exception) {
            Toast.makeText(ctx, "Gallery not available", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val callback = pendingPhotoCallback
        pendingPhotoCallback = null
        if (requestCode == reqPickImage && resultCode == RESULT_OK && data?.data != null && callback != null) {
            try {
                val inputStream = contentResolver.openInputStream(data.data!!)
                val original = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (original != null) {
                    val maxDim = 1280
                    val ratio = minOf(
                        maxDim.toFloat() / original.width.coerceAtLeast(1),
                        maxDim.toFloat() / original.height.coerceAtLeast(1),
                        1f
                    )
                    val w = (original.width * ratio).toInt().coerceAtLeast(1)
                    val h = (original.height * ratio).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(original, w, h, true)
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 92, baos)
                    val encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    callback(encoded)
                }
            } catch (_: Exception) {
                Toast.makeText(this, "Could not read photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun decodeBase64Bitmap(base64Str: String): Bitmap? {
        if (base64Str.isBlank()) return null
        return try {
            val bytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun saveBitmapToGallery(ctx: Context, bmp: Bitmap, prefix: String): Boolean {
        return try {
            val fileName = "${prefix}_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CaptainSmartPro")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 95, out)
                    out.flush()
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                ctx.contentResolver.update(uri, values, null, null)
                true
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "CaptainSmartPro")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 95, out)
                    out.flush()
                }
                ctx.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun openLargePhotoDialog(ctx: Context, bmp: Bitmap, title: String) {
        try {
            val screenW = ctx.resources.displayMetrics.widthPixels
            val baseW = (screenW * 0.85f).toInt().coerceAtLeast(360)
            val aspect = if (bmp.width > 0) bmp.height.toFloat() / bmp.width.toFloat() else 1.3f
            val baseH = (baseW * aspect).toInt().coerceIn(300, 1100)

            val scroll = ScrollView(ctx)
            val box = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20, 16, 20, 20)
                gravity = Gravity.CENTER_HORIZONTAL
            }

            val bigView = ImageView(ctx).apply {
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                layoutParams = LinearLayout.LayoutParams(baseW, baseH).apply {
                    bottomMargin = 14
                }
            }

            val dlBtn = Button(ctx).apply {
                text = "Download Photo"
                setOnClickListener {
                    val ok = saveBitmapToGallery(ctx, bmp, "ChatPhoto_Clear")
                    Toast.makeText(
                        ctx,
                        if (ok) "Photo downloaded to Gallery" else "Download failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            box.addView(bigView)
            box.addView(dlBtn)
            scroll.addView(box)

            AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(scroll)
                .setNegativeButton("Close", null)
                .show()
        } catch (_: Exception) {
            Toast.makeText(ctx, "Unable to open photo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadAndStart(ctx: Context, hardwareId: String) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val nowMillis = System.currentTimeMillis()

        configurableTrialDays = prefs.getLong("trial_days_global", 30L).coerceAtLeast(0L)
        plan1MonthPrice = prefs.getInt("plan_1m_price", 49)
        plan2MonthPrice = prefs.getInt("plan_2m_price", 70)
        plan3MonthPrice = prefs.getInt("plan_3m_price", 99)

        fbUrl = prefs.getString("social_fb", "") ?: ""
        waUrl = prefs.getString("social_wa", "") ?: ""
        twUrl = prefs.getString("social_tw", "") ?: ""
        ytUrl = prefs.getString("social_yt", "") ?: ""
        broadcastMessage = prefs.getString("broadcast_msg", "") ?: ""
        customQrTitle = prefs.getString("custom_qr_title", "Rider Payment QR") ?: "Rider Payment QR"
        customQrBase64 = prefs.getString("custom_qr_b64", "") ?: ""

        val riderName = prefs.getString("rider_full_name", "") ?: ""
        val riderMobile = prefs.getString("rider_mobile_id", "") ?: ""
        val riderVehicleKey = prefs.getString("rider_vehicle_type", "") ?: ""
        val riderPhotoB64 = prefs.getString("rider_photo_b64", "") ?: ""

        if (riderName.isBlank() || riderMobile.isBlank()) {
            showRiderRegistrationScreen(ctx, hardwareId, riderName, riderMobile, riderVehicleKey, riderPhotoB64)
            return
        }

        val riderId = riderMobile.trim()
        val isBlocked = prefs.getBoolean("blocked_$riderId", false)
        if (isBlocked) {
            showBlockedScreen(ctx, riderId, riderName)
            return
        }

        var trialStart = prefs.getLong("trial_start_$riderId", 0L)
        if (trialStart == 0L) {
            trialStart = nowMillis
            prefs.edit().putLong("trial_start_$riderId", trialStart).apply()
        }
        val activeUntil = prefs.getLong("active_until_$riderId", 0L)
        val trialEnd = trialStart + TimeUnit.DAYS.toMillis(configurableTrialDays)
        val hasActiveSub = nowMillis < activeUntil
        val inTrial = !hasActiveSub && (nowMillis < trialEnd)

        activeLocationRiderId = riderId
        activeLocationRiderName = riderName
        activeLocationVehicleKey = riderVehicleKey
        activeLocationPhotoB64 = riderPhotoB64
        activeLocationHardwareId = hardwareId

        try {
            val rootRef = FirebaseDatabase.getInstance().reference.child("captain_smart_pro")
            rootRef.child("riders").child(riderId).updateChildren(
                mapOf(
                    "riderId" to riderId,
                    "fullName" to riderName,
                    "mobile" to riderMobile,
                    "vehicleType" to (VehicleCategory.fromKey(riderVehicleKey)?.key ?: "bike"),
                    "hardwareId" to hardwareId,
                    "photoB64" to riderPhotoB64,
                    "trialStart" to trialStart,
                    "activeUntil" to activeUntil,
                    "online" to true,
                    "lastSeen" to nowMillis
                )
            )
            rootRef.child("config").get().addOnSuccessListener { snap ->
                val dbTrial = snap.child("trialDays").getValue(Long::class.java)
                if (dbTrial != null && dbTrial >= 0L) {
                    configurableTrialDays = dbTrial
                    prefs.edit().putLong("trial_days_global", configurableTrialDays).apply()
                }
                plan1MonthPrice = snap.child("plan1m").getValue(Int::class.java) ?: plan1MonthPrice
                plan2MonthPrice = snap.child("plan2m").getValue(Int::class.java) ?: plan2MonthPrice
                plan3MonthPrice = snap.child("plan3m").getValue(Int::class.java) ?: plan3MonthPrice

                fbUrl = snap.child("facebook").getValue(String::class.java) ?: fbUrl
                waUrl = snap.child("whatsapp").getValue(String::class.java) ?: waUrl
                twUrl = snap.child("twitter").getValue(String::class.java) ?: twUrl
                ytUrl = snap.child("youtube").getValue(String::class.java) ?: ytUrl
                broadcastMessage = snap.child("broadcast").getValue(String::class.java) ?: broadcastMessage
                customQrTitle = snap.child("qrTitle").getValue(String::class.java) ?: customQrTitle
                customQrBase64 = snap.child("qrBase64").getValue(String::class.java) ?: customQrBase64

                prefs.edit()
                    .putInt("plan_1m_price", plan1MonthPrice)
                    .putInt("plan_2m_price", plan2MonthPrice)
                    .putInt("plan_3m_price", plan3MonthPrice)
                    .putString("social_fb", fbUrl)
                    .putString("social_wa", waUrl)
                    .putString("social_tw", twUrl)
                    .putString("social_yt", ytUrl)
                    .putString("broadcast_msg", broadcastMessage)
                    .putString("custom_qr_title", customQrTitle)
                    .putString("custom_qr_b64", customQrBase64)
                    .apply()
            }
        } catch (_: Exception) {
        }

        if (!hasActiveSub && !inTrial) {
            showPaymentLockScreen(ctx, riderId, riderName)
        } else {
            showRiderDashboard(ctx, riderId, riderName)
        }
    }

    private fun showBlockedScreen(ctx: Context, riderId: String, riderName: String) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val scroll = ScrollView(ctx).apply {
            setBackgroundColor(bgDark)
            setPadding(22, 72, 22, 116)
        }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(makeStyledButton(ctx, "🛡️ ADMIN", accentBlue).apply {
            textSize = 11f
            setOnClickListener { promptAdminPassport(ctx, riderId) }
        })
        col.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 14) })

        val card = makeCard(ctx, cardDark, accentRed)
        card.addView(TextView(ctx).apply {
            text = tr("🚫 Account Paused by Admin", "🚫 आपकी आईडी ब्लॉक है", "🚫 আপোনাৰ আইডি ব্লক হৈছে")
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F87171"))
        })
        card.addView(TextView(ctx).apply {
            text = tr(
                "\n$riderName ($riderId)\nWrite your issue (up to 200 words) below to send an unblock request to Admin.",
                "\n$riderName ($riderId)\nआईडी खुलवाने के लिए नीचे 200 शब्दों में अपनी समस्या लिखकर एडमिन को रिक्वेस्ट भेजें।",
                "\n$riderName ($riderId)\nআইডি খুলিবলৈ তলত ২০০ শব্দৰ ভিতৰত আপোনাৰ সমস্যা লিখি এডমিনলৈ অনুৰোধ পঠিয়াওক।"
            )
            textSize = 13f
            setTextColor(textWhite)
        })

        val wordCountTv = TextView(ctx).apply {
            text = tr("Words: 0 / 200", "शब्द: 0 / 200", "শব্দ: 0 / 200")
            textSize = 12f
            setTextColor(accentGold)
            setPadding(0, 12, 0, 6)
        }

        val reqEt = EditText(ctx).apply {
            hint = tr(
                "Write your problem here (max 200 words)...",
                "यहाँ अपनी समस्या लिखें (अधिकतम 200 शब्द)...",
                "ইয়াত আপোনাৰ সমস্যা লিখক (সৰ্বাধিক ২০০ শব্দ)..."
            )
            setTextColor(textWhite)
            setHintTextColor(textMuted)
            textSize = 13f
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            background = make3dPill(cardElevated, borderSlate, 14f)
            setPadding(14, 12, 14, 12)
        }

        val sentHistoryTv = TextView(ctx).apply {
            text = prefs.getString("unblock_req_$riderId", "") ?: ""
            textSize = 12f
            setTextColor(accentGreenBright)
            setPadding(0, 10, 0, 6)
        }

        reqEt.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val words = countWords(s?.toString() ?: "")
                wordCountTv.text = tr("Words: $words / 200", "शब्द: $words / 200", "শব্দ: $words / 200")
                wordCountTv.setTextColor(if (words > 200) Color.parseColor("#F87171") else accentGold)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        val sendReqBtn = makeStyledButton(
            ctx,
            tr("📩 Send Unblock Request", "📩 आईडी खोलने की रिक्वेस्ट भेजें", "📩 আইডি খোলাৰ অনুৰোধ পঠিয়াওক"),
            accentGreen
        ).apply {
            setOnClickListener {
                val msg = reqEt.text.toString().trim()
                if (msg.isEmpty()) {
                    Toast.makeText(
                        ctx,
                        tr("Please write your message first", "कृपया पहले अपनी समस्या लिखें", "অনুগ্ৰহ কৰি প্ৰথমে আপোনাৰ সমস্যা লিখক"),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                val words = countWords(msg)
                if (words > 200) {
                    Toast.makeText(
                        ctx,
                        tr("Please keep within 200 words ($words words)", "कृपया 200 शब्दों के अंदर लिखें ($words शब्द)", "অনুগ্ৰহ কৰি ২০০ শব্দৰ ভিতৰত লিখক ($words শব্দ)"),
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

                val formattedReq = "[Unblock Request - $words words]: $msg"
                val prevHist = prefs.getString("unblock_req_$riderId", "") ?: ""
                val updatedHist = (prevHist + "\n• $formattedReq").trim()
                prefs.edit().putString("unblock_req_$riderId", updatedHist).apply()
                sentHistoryTv.text = updatedHist

                try {
                    val chatRef = FirebaseDatabase.getInstance().reference
                        .child("captain_smart_pro")
                        .child("chats")
                        .child(riderId)
                    val key = chatRef.push().key
                    if (key != null) {
                        chatRef.child(key).setValue(
                            mapOf(
                                "sender" to "$riderName ($riderId) [BLOCKED]",
                                "message" to formattedReq,
                                "photoB64" to "",
                                "time" to System.currentTimeMillis()
                            )
                        )
                    }
                    FirebaseDatabase.getInstance().reference
                        .child("captain_smart_pro")
                        .child("riders")
                        .child(riderId)
                        .child("unblockRequest")
                        .setValue(formattedReq)
                } catch (_: Exception) {
                }

                reqEt.setText("")
                Toast.makeText(
                    ctx,
                    tr("Request sent to Admin", "आपकी रिक्वेस्ट एडमिन को भेज दी गई है", "আপোনাৰ অনুৰোধ এডমিনলৈ পঠিওৱা হৈছে"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        card.addView(wordCountTv)
        card.addView(reqEt)
        card.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 10) })
        card.addView(sendReqBtn)
        card.addView(sentHistoryTv)
        col.addView(card)
        scroll.addView(col)
        setContentView(scroll)
    }

    private fun showRiderRegistrationScreen(
        ctx: Context,
        hardwareId: String,
        existingName: String,
        existingMobile: String,
        existingVehicleKey: String,
        existingPhotoB64: String
    ) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        var photoB64 = existingPhotoB64
        var selectedVehicle: VehicleCategory? = VehicleCategory.fromKey(existingVehicleKey)

        val scroll = ScrollView(ctx).apply {
            setBackgroundColor(bgDark)
            setPadding(22, 72, 22, 116)
        }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 14)
        }
        topRow.addView(makeStyledButton(ctx, "🛡️ ADMIN", accentBlue).apply {
            textSize = 11f
            setOnClickListener { promptAdminPassport(ctx, existingMobile.ifBlank { hardwareId }) }
        })
        topRow.addView(TextView(ctx).apply {
            text = "  Captain Smart Pro"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentGreenBright)
        })
        col.addView(topRow)

        val card = makeCard(ctx, cardDark, borderSlate)
        card.addView(TextView(ctx).apply {
            text = tr(
                "🪪 Fresh Rider Account (Name, Mobile & Vehicle)",
                "🪪 नया राइडर अकाउंट (नाम, मोबाइल व वाहन)",
                "🪪 নতুন ৰাইডাৰ একাউণ্ট (নাম, মোবাইল আৰু বাহন)"
            )
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
            setPadding(0, 0, 0, 10)
        })

        val nameEt = makeStyledInput(ctx, existingName, tr("Full Name", "पूरा नाम", "সম্পূৰ্ণ নাম"))
        val mobileEt = makeStyledInput(ctx, existingMobile, tr("Mobile Number (Rider ID)", "मोबाइल नंबर (Rider ID)", "মোবাইল নম্বৰ (Rider ID)"))
        mobileEt.inputType = InputType.TYPE_CLASS_PHONE

        card.addView(nameEt)
        card.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 10) })
        card.addView(mobileEt)

        card.addView(TextView(ctx).apply {
            text = tr("\n🚗 Tap Your Vehicle Icon:", "\n🚗 अपना वाहन आइकन चुनें:", "\n🚗 আপোনাৰ বাহনৰ আইকন বাছনি কৰক:")
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentGold)
            setPadding(0, 4, 0, 8)
        })

        val vehicleStatusTv = TextView(ctx).apply {
            text = "Selected Vehicle: ${selectedVehicle?.label ?: "None (Tap an icon below)"}"
            textSize = 12f
            setTextColor(accentGreenBright)
            setPadding(0, 0, 0, 8)
        }

        val vehicleViews = mutableMapOf<VehicleCategory, LinearLayout>()

        fun refreshVehicleHighlight() {
            vehicleViews.forEach { (vType, box) ->
                val isChosen = vType == selectedVehicle
                box.background = make3dPill(
                    if (isChosen) accentBlue else cardElevated,
                    if (isChosen) accentGreenBright else borderSlate,
                    12f
                )
                box.alpha = if (isChosen) 1.0f else 0.75f
            }
            vehicleStatusTv.text = "Selected Vehicle: ${selectedVehicle?.label ?: "None"}"
        }

        val allVehicles = VehicleCategory.entries
        val rows = listOf(
            allVehicles.take(3),
            allVehicles.drop(3)
        )

        rows.forEach { rowItems ->
            val rowLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 8 }
            }
            rowItems.forEachIndexed { idx, vType ->
                val tile = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(6, 10, 6, 10)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        if (idx > 0) leftMargin = 6
                    }
                    addView(ImageView(ctx).apply {
                        setImageDrawable(createVehicleIconDrawable(vType))
                        layoutParams = LinearLayout.LayoutParams(54, 54)
                    })
                    addView(TextView(ctx).apply {
                        text = vType.label
                        textSize = 11f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(textWhite)
                        gravity = Gravity.CENTER
                        setPadding(0, 4, 0, 0)
                    })
                    setOnClickListener {
                        selectedVehicle = vType
                        refreshVehicleHighlight()
                    }
                }
                vehicleViews[vType] = tile
                rowLayout.addView(tile)
            }
            card.addView(rowLayout)
        }

        refreshVehicleHighlight()
        card.addView(vehicleStatusTv)

        val photoPreview = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(180, 180).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 8
                bottomMargin = 10
            }
            val bmp = decodeBase64Bitmap(photoB64)
            if (bmp != null) setImageBitmap(bmp)
        }

        val pickPhotoBtn = makeStyledButton(
            ctx,
            tr("📷 Choose Profile Photo", "📷 प्रोफ़ाइल फ़ोटो चुनें", "📷 প্ৰফাইল ফটো বাছনি কৰক"),
            cardElevated
        ).apply {
            textSize = 12f
            setOnClickListener {
                pickPhotoFromGallery(ctx) { b64 ->
                    photoB64 = b64
                    decodeBase64Bitmap(b64)?.let { photoPreview.setImageBitmap(it) }
                    Toast.makeText(ctx, "Profile photo uploaded", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val saveBtn = makeStyledButton(
            ctx,
            tr("▶️ START", "▶️ स्टार्ट करें", "▶️ আৰম্ভ কৰক"),
            accentGreen
        ).apply {
            setOnClickListener {
                val fullName = nameEt.text.toString().trim()
                val mobileId = mobileEt.text.toString().trim()
                val chosenVehicle = selectedVehicle
                if (fullName.isEmpty() || mobileId.length < 10) {
                    Toast.makeText(ctx, "Enter Full Name and valid Mobile Number", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (chosenVehicle == null) {
                    Toast.makeText(ctx, "Please tap a vehicle icon first", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                prefs.edit()
                    .putString("rider_full_name", fullName)
                    .putString("rider_mobile_id", mobileId)
                    .putString("rider_vehicle_type", chosenVehicle.key)
                    .putString("rider_photo_b64", photoB64)
                    .putLong("trial_start_$mobileId", System.currentTimeMillis())
                    .remove("blocked_$mobileId")
                    .apply()
                loadAndStart(ctx, hardwareId)
            }
        }

        card.addView(photoPreview)
        card.addView(pickPhotoBtn)
        card.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 12) })
        card.addView(saveBtn)
        col.addView(card)

        scroll.addView(col)
        setContentView(scroll)
    }

    private fun promptAdminPassport(ctx: Context, targetRiderId: String) {
        val passEt = EditText(ctx).apply {
            hint = "Enter Admin Passport"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(28, 20, 28, 20)
        }
        AlertDialog.Builder(ctx)
            .setTitle("🛡️ Admin Login")
            .setView(passEt)
            .setPositiveButton("Unlock") { _, _ ->
                if (passEt.text.toString() == adminPassport) {
                    showAdminDashboardScreen(ctx, targetRiderId)
                } else {
                    Toast.makeText(
                        ctx,
                        tr("Invalid Passport", "गलत पासवर्ड", "ভুল পাছৱৰ্ড"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAdminDashboardScreen(ctx: Context, defaultRiderId: String) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val totalRides = prefs.getInt("total_rides", 0)
        val totalEarnings = prefs.getInt("total_earnings", 0)
        val totalDistance = prefs.getFloat("total_distance", 0f)
        val hwId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgDark)
        }

        val topBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(headerDark)
            setPadding(18, 72, 18, 16)
            gravity = Gravity.CENTER_VERTICAL
        }

        val adminLeftBtn = Button(ctx).apply {
            text = "🛡️ ADMIN"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = make3dPill(accentBlue, Color.parseColor("#60A5FA"), 12f)
            setPadding(12, 8, 12, 8)
            setOnClickListener {
                Toast.makeText(ctx, "Admin Active • Target ID: $defaultRiderId", Toast.LENGTH_SHORT).show()
            }
        }

        val brandCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 10
            }
        }
        brandCol.addView(TextView(ctx).apply {
            text = "Captain Smart Pro"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentGreenBright)
        })
        brandCol.addView(TextView(ctx).apply {
            text = "Admin Panel • Dibrugarh Fleet"
            textSize = 11f
            setTextColor(textMuted)
        })

        val adminDotsBtn = Button(ctx).apply {
            text = "⋮"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
            background = make3dPill(cardElevated, Color.parseColor("#60A5FA"), 12f)
            setPadding(14, 6, 14, 6)
        }

        topBar.addView(adminLeftBtn)
        topBar.addView(brandCol)
        topBar.addView(adminDotsBtn)
        root.addView(topBar)

        val scroll = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            setPadding(16, 14, 16, 116)
        }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        val totalRidersTv = TextView(ctx).apply {
            text = "1"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
            gravity = Gravity.CENTER
        }
        val onlineRidersTv = TextView(ctx).apply {
            text = "1"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
            gravity = Gravity.CENTER
        }
        val offlineRidersTv = TextView(ctx).apply {
            text = "0"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
            gravity = Gravity.CENTER
        }

        val paymentAlertCard = makeCard(ctx, Color.parseColor("#1D1807"), accentGold)
        val paymentAlertTv = TextView(ctx).apply {
            text = "🔔 Payment Alerts: Checking..."
            textSize = 12f
            setTextColor(accentGold)
        }
        paymentAlertCard.addView(paymentAlertTv)
        col.addView(paymentAlertCard)

        val top3Row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
        }

        val totalCard = makeClickableMetricBox(ctx, "👥 Total Riders", totalRidersTv, "Tap List", Color.parseColor("#132854"), Color.parseColor("#3B82F6"), 6) {
            openRidersListDialog(ctx, onlyOnline = false)
        }
        val onlineCard = makeClickableMetricBox(ctx, "🟢 Online Riders", onlineRidersTv, "Tap Online", Color.parseColor("#0D3B26"), accentGreenBright, 6) {
            openRidersListDialog(ctx, onlyOnline = true)
        }
        val offlineCard = makeClickableMetricBox(ctx, "⚫ Offline Riders", offlineRidersTv, "Tap All", Color.parseColor("#3B1219"), Color.parseColor("#EF4444"), 0) {
            openRidersListDialog(ctx, onlyOnline = false)
        }

        top3Row.addView(totalCard)
        top3Row.addView(onlineCard)
        top3Row.addView(offlineCard)
        col.addView(top3Row)

        val mid2Row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 14 }
        }
        mid2Row.addView(makeMetricCard(ctx, "💰 Total Earning", "₹$totalEarnings", "Live", Color.parseColor("#2E1654"), Color.parseColor("#A855F7"), 6))
        mid2Row.addView(makeMetricCard(ctx, "🏍️ Total Rides", "$totalRides", "Trips", Color.parseColor("#0D2E47"), Color.parseColor("#38BDF8"), 0))
        col.addView(mid2Row)

        val summaryCard = makeCard(ctx, cardDark, borderSlate)
        summaryCard.addView(TextView(ctx).apply {
            text = "📊 Today's Summary"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
            setPadding(0, 0, 0, 8)
        })
        summaryCard.addView(TextView(ctx).apply {
            text = "Rides: $totalRides   |   Earnings: ₹$totalEarnings   |   Distance: ${String.format(Locale.US, "%.1f", totalDistance)} Km"
            textSize = 13f
            setTextColor(accentGreenBright)
        })
        col.addView(summaryCard)

        val msgCard = makeCard(ctx, cardDark, borderSlate)
        msgCard.addView(TextView(ctx).apply {
            text = "📢 Send Message to All Riders"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
        })
        val broadcastInputEt = makeStyledInput(ctx, broadcastMessage, "Type your message here...")
        msgCard.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 8) })
        msgCard.addView(broadcastInputEt)
        msgCard.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 10) })

        val msgBtnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        msgBtnRow.addView(makeStyledButton(ctx, "📨 Send Message", accentBlue).apply {
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 6
            }
            setOnClickListener {
                val msg = broadcastInputEt.text.toString().trim()
                broadcastMessage = msg
                prefs.edit().putString("broadcast_msg", msg).apply()
                try {
                    FirebaseDatabase.getInstance().reference
                        .child("captain_smart_pro")
                        .child("config")
                        .child("broadcast")
                        .setValue(msg)
                } catch (_: Exception) {
                }
                Toast.makeText(ctx, "Message sent to all riders", Toast.LENGTH_SHORT).show()
            }
        })
        msgBtnRow.addView(makeStyledButton(ctx, "👥 All Riders", accentPurple).apply {
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 6
            }
            setOnClickListener { openRidersListDialog(ctx, onlyOnline = false) }
        })
        msgCard.addView(msgBtnRow)
        col.addView(msgCard)

        col.addView(buildCompactSocialLinksCard(ctx))

        try {
            val now = System.currentTimeMillis()
            FirebaseDatabase.getInstance().reference.child("captain_smart_pro").child("riders")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        var totalCount = 0
                        var onlineCount = 0
                        for (child in snapshot.children) {
                            totalCount++
                            val lastSeen = child.child("lastSeen").getValue(Long::class.java) ?: 0L
                            val isOnlineFlag = child.child("online").getValue(Boolean::class.java) ?: false
                            if (isOnlineFlag && (now - lastSeen) < TimeUnit.MINUTES.toMillis(15)) {
                                onlineCount++
                            }
                        }
                        val offlineCount = (totalCount - onlineCount).coerceAtLeast(0)
                        totalRidersTv.text = totalCount.toString()
                        onlineRidersTv.text = onlineCount.toString()
                        offlineRidersTv.text = offlineCount.toString()
                    }

                    override fun onCancelled(error: DatabaseError) {}
                })

            FirebaseDatabase.getInstance().reference.child("captain_smart_pro").child("payment_requests")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val pendingList = mutableListOf<String>()
                        for (req in snapshot.children) {
                            val status = req.child("status").getValue(String::class.java) ?: ""
                            if (status == "PENDING_APPROVAL") {
                                val rName = req.child("riderName").getValue(String::class.java) ?: "Rider"
                                val rId = req.child("riderId").getValue(String::class.java) ?: (req.key ?: "")
                                val planInfo = req.child("planLabel").getValue(String::class.java) ?: ""
                                pendingList.add(if (planInfo.isNotBlank()) "$rName ($rId - $planInfo)" else "$rName ($rId)")
                            }
                        }
                        paymentAlertTv.text = if (pendingList.isEmpty()) {
                            "🔔 Payment Alerts: No pending payment requests"
                        } else {
                            "🔔 Payment Alert (${pendingList.size}): ${pendingList.joinToString(", ")} — Open ⋮ > 💳 Subscription"
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}
                })
        } catch (_: Exception) {
            paymentAlertTv.text = "🔔 Payment Alerts: Local mode active"
        }

        val mSub = "💳 Subscription (Days +/-)"
        val mPlans = "🏷️ Plan Prices (1M/2M/3M)"
        val mTrial = "⏳ Free Trial Days"
        val mChat = "💬 Rider Chat & Clear Photos"
        val mBlock = "🛡️ Block / Unblock Rider"
        val mQrLinks = "📱 QR Code & Social Links"
        val mFresh = "🔄 Reset Login (Fresh Account)"
        val mLang = "🌐 Language"
        val mBack = "🏍️ Rider Panel"

        adminDotsBtn.setOnClickListener {
            val popup = PopupMenu(ctx, adminDotsBtn)
            popup.menu.add(mSub)
            popup.menu.add(mPlans)
            popup.menu.add(mTrial)
            popup.menu.add(mChat)
            popup.menu.add(mBlock)
            popup.menu.add(mQrLinks)
            popup.menu.add(mFresh)
            popup.menu.add(mLang)
            popup.menu.add(mBack)
            popup.setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    mSub -> openSubscriptionManagerDialog(ctx, defaultRiderId)
                    mPlans -> openPlanPricesDialog(ctx)
                    mTrial -> openTrialManagerDialog(ctx, defaultRiderId)
                    mChat -> openAdminRiderChatDialog(ctx, defaultRiderId)
                    mBlock -> openBlockRiderDialog(ctx, defaultRiderId)
                    mQrLinks -> openAdminQrAndLinksDialog(ctx)
                    mFresh -> confirmFreshAccountReset(ctx, hwId)
                    mLang -> openLanguageSelectionDialog(ctx) {
                        showAdminDashboardScreen(ctx, defaultRiderId)
                    }
                    mBack -> loadAndStart(ctx, hwId)
                }
                true
            }
            popup.show()
        }

        scroll.addView(col)
        root.addView(scroll)
        setContentView(root)
    }

    private data class RiderEntry(
        val rId: String,
        val name: String,
        val mobile: String,
        val vehicleTypeKey: String?,
        val photoB64: String,
        val isOnline: Boolean,
        val lat: Double?,
        val lng: Double?,
        val locTime: Long,
        val movementStatus: String = "Live",
        val remainingDays: Long = 0L
    )

    private fun openSingleRiderActionsDialog(ctx: Context, entry: RiderEntry, refreshList: () -> Unit) {
        val vLabel = VehicleCategory.safeLabel(entry.vehicleTypeKey)
        val hasCoords = entry.lat != null && entry.lng != null && (entry.lat != 0.0 || entry.lng != 0.0)
        val locText = if (hasCoords) {
            "Live (${entry.movementStatus}): ${String.format(Locale.US, "%.5f", entry.lat)}, ${String.format(Locale.US, "%.5f", entry.lng)}"
        } else {
            "Live Location: Waiting for GPS"
        }

        val options = arrayOf(
            "📞 Call Rider (${entry.mobile})",
            "💬 Chat & Message Rider",
            "📍 Open Live Location on Map",
            "💳 Subscription Days (+/-) [Left: ${entry.remainingDays}d]",
            "🛡️ Block / Unblock Rider",
            "🗑️ Delete Rider (Allow Fresh Account)"
        )

        AlertDialog.Builder(ctx)
            .setTitle("${entry.name} (${entry.mobile})\nVehicle: $vLabel • Remaining: ${entry.remainingDays} days\n$locText")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (entry.mobile.isNotBlank()) {
                            ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${entry.mobile}")))
                        }
                    }
                    1 -> openAdminRiderChatDialog(ctx, entry.rId)
                    2 -> {
                        if (hasCoords) {
                            val label = Uri.encode("${entry.name} ($vLabel)")
                            val geoUri = Uri.parse("geo:${entry.lat},${entry.lng}?q=${entry.lat},${entry.lng}($label)")
                            try {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, geoUri))
                            } catch (_: Exception) {
                                Toast.makeText(ctx, locText, Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(ctx, "Rider location not available right now", Toast.LENGTH_SHORT).show()
                        }
                    }
                    3 -> openSubscriptionManagerDialog(ctx, entry.rId)
                    4 -> openBlockRiderDialog(ctx, entry.rId)
                    5 -> {
                        AlertDialog.Builder(ctx)
                            .setTitle("🗑️ Delete Rider")
                            .setMessage("Delete ${entry.name} (${entry.mobile})? They can create a fresh account with the same number.")
                            .setPositiveButton("Delete") { _, _ ->
                                deleteRiderFromAdmin(ctx, entry.rId, refreshList)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun confirmFreshAccountReset(ctx: Context, hardwareId: String) {
        AlertDialog.Builder(ctx)
            .setTitle("🔄 Fresh Account Reset")
            .setMessage("Clear local rider login, stats, and trial cache so you can start a fresh account?")
            .setPositiveButton("Clear & Reset") { _, _ ->
                resetLocalLoginForFreshAccount(ctx, hardwareId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openBlockRiderDialog(ctx: Context, defaultRiderId: String) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 20)
        }
        val riderIdEt = EditText(ctx).apply {
            hint = "Rider Mobile Number (ID)"
            setText(defaultRiderId)
        }
        val blockSw = Switch(ctx).apply {
            text = "Block Rider Access"
            isChecked = prefs.getBoolean("blocked_${defaultRiderId.trim()}", false)
        }
        box.addView(TextView(ctx).apply { text = "Rider ID (Mobile Number):" })
        box.addView(riderIdEt)
        box.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 10) })
        box.addView(blockSw)

        AlertDialog.Builder(ctx)
            .setTitle("🛡️ Block / Unblock Rider")
            .setView(box)
            .setPositiveButton("Apply") { _, _ ->
                val targetId = riderIdEt.text.toString().trim()
                if (targetId.isEmpty()) return@setPositiveButton
                val blocked = blockSw.isChecked
                prefs.edit().putBoolean("blocked_$targetId", blocked).apply()
                try {
                    FirebaseDatabase.getInstance().reference
                        .child("captain_smart_pro")
                        .child("riders")
                        .child(targetId)
                        .child("blocked")
                        .setValue(blocked)
                } catch (_: Exception) {
                }
                Toast.makeText(
                    ctx,
                    if (blocked) "Rider $targetId blocked" else "Rider $targetId unblocked",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openPlanPricesDialog(ctx: Context) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 20)
        }
        val p1Et = EditText(ctx).apply {
            hint = "1 Month Price (₹)"
            setText(plan1MonthPrice.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val p2Et = EditText(ctx).apply {
            hint = "2 Months Price (₹)"
            setText(plan2MonthPrice.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val p3Et = EditText(ctx).apply {
            hint = "3 Months Price (₹)"
            setText(plan3MonthPrice.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        box.addView(TextView(ctx).apply { text = "1 Month Plan (₹):" })
        box.addView(p1Et)
        box.addView(TextView(ctx).apply { text = "2 Months Plan (₹):" })
        box.addView(p2Et)
        box.addView(TextView(ctx).apply { text = "3 Months Plan (₹):" })
        box.addView(p3Et)

        AlertDialog.Builder(ctx)
            .setTitle("🏷️ Subscription Plan Prices")
            .setView(box)
            .setPositiveButton("Save Prices") { _, _ ->
                plan1MonthPrice = p1Et.text.toString().trim().toIntOrNull() ?: 49
                plan2MonthPrice = p2Et.text.toString().trim().toIntOrNull() ?: 70
                plan3MonthPrice = p3Et.text.toString().trim().toIntOrNull() ?: 99

                prefs.edit()
                    .putInt("plan_1m_price", plan1MonthPrice)
                    .putInt("plan_2m_price", plan2MonthPrice)
                    .putInt("plan_3m_price", plan3MonthPrice)
                    .apply()

                try {
                    FirebaseDatabase.getInstance().reference
                        .child("captain_smart_pro")
                        .child("config")
                        .updateChildren(
                            mapOf(
                                "plan1m" to plan1MonthPrice,
                                "plan2m" to plan2MonthPrice,
                                "plan3m" to plan3MonthPrice
                            )
                        )
                } catch (_: Exception) {
                }
                Toast.makeText(
                    ctx,
                    "Saved: 1M ₹$plan1MonthPrice | 2M ₹$plan2MonthPrice | 3M ₹$plan3MonthPrice",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openRidersListDialog(ctx: Context, onlyOnline: Boolean) {
        val scroll = ScrollView(ctx)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }
        val searchEt = EditText(ctx).apply {
            hint = "🔍 Search by name, mobile or vehicle..."
            setPadding(16, 12, 16, 12)
        }
        val listContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 0)
        }
        listContainer.addView(TextView(ctx).apply {
            text = "Loading riders..."
            textSize = 13f
        })
        box.addView(searchEt)
        box.addView(listContainer)
        scroll.addView(box)

        AlertDialog.Builder(ctx)
            .setTitle(if (onlyOnline) "🟢 Online Riders (Tap for Actions)" else "👥 Total Riders (Tap for Actions)")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .show()

        val allEntries = mutableListOf<RiderEntry>()

        fun loadEntriesFromDb() {
            try {
                val now = System.currentTimeMillis()
                FirebaseDatabase.getInstance().reference.child("captain_smart_pro").child("riders")
                    .get()
                    .addOnSuccessListener { snap ->
                        allEntries.clear()
                        for (child in snap.children) {
                            val rId = child.child("riderId").getValue(String::class.java) ?: (child.key ?: "")
                            val name = child.child("fullName").getValue(String::class.java) ?: "Unknown"
                            val mobile = child.child("mobile").getValue(String::class.java) ?: rId
                            val vehicleTypeKey = child.child("vehicleType").getValue(String::class.java)
                            val photoB64 = child.child("photoB64").getValue(String::class.java) ?: ""
                            val lastSeen = child.child("lastSeen").getValue(Long::class.java) ?: 0L
                            val isOnline = (child.child("online").getValue(Boolean::class.java) ?: false) &&
                                ((now - lastSeen) < TimeUnit.MINUTES.toMillis(15))
                            val lat = child.child("lat").getValue(Double::class.java)
                            val lng = child.child("lng").getValue(Double::class.java)
                            val locTime = child.child("locationUpdatedAt").getValue(Long::class.java) ?: 0L
                            val moveStatus = child.child("movementStatus").getValue(String::class.java) ?: "Live"
                            val trialStart = child.child("trialStart").getValue(Long::class.java) ?: 0L
                            val activeUntil = child.child("activeUntil").getValue(Long::class.java) ?: 0L
                            val customTrial = child.child("customTrialDays").getValue(Long::class.java)
                            val remDays = computeRemainingDays(trialStart, activeUntil, customTrial, now)
                            if (onlyOnline && !isOnline) continue
                            allEntries.add(
                                RiderEntry(
                                    rId, name, mobile, vehicleTypeKey, photoB64,
                                    isOnline, lat, lng, locTime, moveStatus, remDays
                                )
                            )
                        }
                        renderEntries(searchEt.text.toString())
                    }
            } catch (_: Exception) {
                listContainer.removeAllViews()
                listContainer.addView(TextView(ctx).apply { text = "Unable to load riders list." })
            }
        }

        renderEntries = label@{ filterQuery: String ->
            listContainer.removeAllViews()
            val q = filterQuery.trim().lowercase(Locale.US)
            var count = 0
            for (entry in allEntries) {
                val vCat = VehicleCategory.fromKey(entry.vehicleTypeKey) ?: VehicleCategory.BIKE
                val vLabel = vCat.label
                if (q.isNotEmpty() &&
                    !entry.name.lowercase(Locale.US).contains(q) &&
                    !entry.mobile.lowercase(Locale.US).contains(q) &&
                    !vLabel.lowercase(Locale.US).contains(q)
                ) {
                    continue
                }
                count++
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 10, 0, 10)
                    setOnClickListener {
                        openSingleRiderActionsDialog(ctx, entry) { loadEntriesFromDb() }
                    }
                }
                val bmp = decodeBase64Bitmap(entry.photoB64)
                val photoView = ImageView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(90, 90).apply { rightMargin = 8 }
                    if (bmp != null) setImageBitmap(bmp)
                }
                val vehicleIconView = ImageView(ctx).apply {
                    setImageDrawable(createVehicleIconDrawable(vCat))
                    layoutParams = LinearLayout.LayoutParams(62, 62).apply { rightMargin = 10 }
                }
                val hasCoords = entry.lat != null && entry.lng != null && (entry.lat != 0.0 || entry.lng != 0.0)
                val locInfo = if (hasCoords) {
                    "\nLive (${entry.movementStatus}): ${String.format(Locale.US, "%.4f", entry.lat)}, ${String.format(Locale.US, "%.4f", entry.lng)}"
                } else {
                    "\nLive Location: Waiting for GPS"
                }
                val detailsTv = TextView(ctx).apply {
                    text = "${entry.name} (${entry.mobile})\nVehicle: $vLabel • ${entry.remainingDays} days left • ${if (entry.isOnline) "ONLINE" else "OFFLINE"}$locInfo"
                    textSize = 12f
                }
                row.addView(photoView)
                row.addView(vehicleIconView)
                row.addView(detailsTv)
                listContainer.addView(row)
            }
            if (count == 0) {
                listContainer.addView(TextView(ctx).apply {
                    text = if (onlyOnline) "No riders online right now." else "No matching riders found."
                })
            }
        }

        searchEt.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderEntries(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        loadEntriesFromDb()
    }

    private var renderEntries: (String) -> Unit = {}

    private fun openSubscriptionManagerDialog(ctx: Context, defaultRiderId: String) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 20)
        }
        val riderIdEt = EditText(ctx).apply {
            hint = "Rider Mobile Number (ID)"
            setText(defaultRiderId)
        }
        val daysEt = EditText(ctx).apply {
            hint = "Days (e.g. 30, 60, 90, 7, -5)"
            setText("30")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        box.addView(TextView(ctx).apply { text = "Rider ID (Mobile Number):" })
        box.addView(riderIdEt)
        box.addView(TextView(ctx).apply { text = "Subscription Days (Activate / Free Days / Reduce):" })
        box.addView(daysEt)

        AlertDialog.Builder(ctx)
            .setTitle("💳 Subscription Manager")
            .setView(box)
            .setPositiveButton("Approve & Apply") { _, _ ->
                val targetId = riderIdEt.text.toString().trim()
                val days = daysEt.text.toString().trim().toLongOrNull() ?: 0L
                val newUntil = (System.currentTimeMillis() + TimeUnit.DAYS.toMillis(days)).coerceAtLeast(0L)
                prefs.edit().putLong("active_until_$targetId", newUntil).apply()
                try {
                    val root = FirebaseDatabase.getInstance().reference.child("captain_smart_pro")
                    root.child("riders").child(targetId).updateChildren(
                        mapOf(
                            "activeUntil" to newUntil,
                            "updatedAt" to System.currentTimeMillis()
                        )
                    )
                    root.child("payment_requests").child(targetId).child("status").setValue("APPROVED")
                } catch (_: Exception) {
                }
                Toast.makeText(ctx, "Approved $days days for Rider ID $targetId", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openTrialManagerDialog(ctx: Context, defaultRiderId: String) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 20)
        }
        val riderIdEt = EditText(ctx).apply {
            hint = "Rider ID (Mobile Number)"
            setText(defaultRiderId)
        }
        val trialEt = EditText(ctx).apply {
            hint = "Free Trial Days (e.g. 30, 7, 3)"
            setText(configurableTrialDays.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        box.addView(TextView(ctx).apply { text = "Rider ID:" })
        box.addView(riderIdEt)
        box.addView(TextView(ctx).apply { text = "Free Trial Days:" })
        box.addView(trialEt)

        AlertDialog.Builder(ctx)
            .setTitle("⏳ Free Trial Manager")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val tDays = trialEt.text.toString().trim().toLongOrNull() ?: 30L
                val targetId = riderIdEt.text.toString().trim()
                configurableTrialDays = tDays.coerceAtLeast(0L)
                prefs.edit().putLong("trial_days_global", configurableTrialDays).apply()
                try {
                    val root = FirebaseDatabase.getInstance().reference.child("captain_smart_pro")
                    root.child("config").child("trialDays").setValue(configurableTrialDays)
                    if (targetId.isNotEmpty()) {
                        root.child("riders").child(targetId).child("customTrialDays").setValue(configurableTrialDays)
                    }
                } catch (_: Exception) {
                }
                Toast.makeText(ctx, "Trial set to $configurableTrialDays days", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openAdminRiderChatDialog(ctx: Context, defaultRiderId: String) {
        val scroll = ScrollView(ctx)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 20)
        }
        val riderIdEt = EditText(ctx).apply {
            hint = "Rider Mobile Number (ID)"
            setText(defaultRiderId)
        }
        val messagesBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 12)
        }
        val msgEt = EditText(ctx).apply { hint = "Reply to rider..." }
        val sendBtn = Button(ctx).apply { text = "Send Reply" }
        val callBtn = Button(ctx).apply { text = "📞 Call Rider" }

        var activeListener: ValueEventListener? = null
        var activeRefPath: String? = null

        fun attachLiveChatListener(targetId: String) {
            if (targetId.isBlank()) return
            try {
                val dbRef = FirebaseDatabase.getInstance().reference
                    .child("captain_smart_pro")
                    .child("chats")
                    .child(targetId)
                if (activeListener != null && activeRefPath != null) {
                    FirebaseDatabase.getInstance().reference.child(activeRefPath!!).removeEventListener(activeListener!!)
                }
                activeRefPath = "captain_smart_pro/chats/$targetId"
                val listener = object : ValueEventListener {
                    override fun onDataChange(snap: DataSnapshot) {
                        messagesBox.removeAllViews()
                        for (item in snap.children) {
                            val sender = item.child("sender").getValue(String::class.java) ?: "User"
                            val text = item.child("message").getValue(String::class.java) ?: ""
                            val photoB64 = item.child("photoB64").getValue(String::class.java) ?: ""
                            if (text.isNotBlank()) {
                                val msgTv = TextView(ctx)
                                msgTv.text = "$sender: $text"
                                msgTv.textSize = 13f
                                messagesBox.addView(msgTv)
                            }
                            val bmp = decodeBase64Bitmap(photoB64)
                            if (bmp != null) {
                                val thumbView = ImageView(ctx).apply {
                                    setImageBitmap(bmp)
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                    adjustViewBounds = true
                                    layoutParams = LinearLayout.LayoutParams(440, 440).apply {
                                        topMargin = 6
                                        bottomMargin = 10
                                    }
                                    setOnClickListener { openLargePhotoDialog(ctx, bmp, "Photo from $sender") }
                                }
                                messagesBox.addView(thumbView)
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}
                }
                activeListener = listener
                dbRef.addValueEventListener(listener)
            } catch (_: Exception) {
            }
        }

        attachLiveChatListener(defaultRiderId)

        callBtn.setOnClickListener {
            val num = riderIdEt.text.toString().trim()
            if (num.isEmpty()) Toast.makeText(ctx, "Enter Rider Mobile ID", Toast.LENGTH_SHORT).show()
            else ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")))
        }

        sendBtn.setOnClickListener {
            val targetId = riderIdEt.text.toString().trim()
            val txt = msgEt.text.toString().trim()
            if (targetId.isEmpty() || txt.isEmpty()) return@setOnClickListener
            attachLiveChatListener(targetId)
            try {
                val chatRef = FirebaseDatabase.getInstance().reference
                    .child("captain_smart_pro")
                    .child("chats")
                    .child(targetId)
                val key = chatRef.push().key
                if (key != null) {
                    chatRef.child(key).setValue(
                        mapOf(
                            "sender" to "Admin",
                            "message" to txt,
                            "photoB64" to "",
                            "time" to System.currentTimeMillis()
                        )
                    )
                }
                msgEt.setText("")
                Toast.makeText(ctx, "Sent", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(ctx, "Send failed", Toast.LENGTH_SHORT).show()
            }
        }

        box.addView(riderIdEt)
        box.addView(callBtn)
        box.addView(messagesBox)
        box.addView(msgEt)
        box.addView(sendBtn)
        scroll.addView(box)

        AlertDialog.Builder(ctx)
            .setTitle("💬 Rider Chat & Photos")
            .setView(scroll)
            .setOnDismissListener {
                if (activeListener != null && activeRefPath != null) {
                    try {
                        FirebaseDatabase.getInstance().reference.child(activeRefPath!!).removeEventListener(activeListener!!)
                    } catch (_: Exception) {
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openAdminQrAndLinksDialog(ctx: Context) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val scroll = ScrollView(ctx)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 20)
        }
        val qrTitleEt = EditText(ctx).apply {
            hint = "QR Code Title"
            setText(customQrTitle)
        }
        var tempQrB64 = customQrBase64
        val qrPreview = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(240, 240).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 8
                bottomMargin = 8
            }
            val bmp = decodeBase64Bitmap(tempQrB64)
            if (bmp != null) setImageBitmap(bmp)
        }
        val pickQrBtn = Button(ctx).apply {
            text = "📱 Select My QR Code Image"
            setOnClickListener {
                pickPhotoFromGallery(ctx) { b64 ->
                    tempQrB64 = b64
                    decodeBase64Bitmap(b64)?.let { qrPreview.setImageBitmap(it) }
                    Toast.makeText(ctx, "QR image selected", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val fbEt = EditText(ctx).apply {
            hint = "Facebook Link"
            setText(fbUrl)
        }
        val waEt = EditText(ctx).apply {
            hint = "WhatsApp Link"
            setText(waUrl)
        }
        val twEt = EditText(ctx).apply {
            hint = "Twitter Link"
            setText(twUrl)
        }
        val ytEt = EditText(ctx).apply {
            hint = "YouTube Link"
            setText(ytUrl)
        }

        box.addView(qrTitleEt)
        box.addView(qrPreview)
        box.addView(pickQrBtn)
        box.addView(fbEt)
        box.addView(waEt)
        box.addView(twEt)
        box.addView(ytEt)
        box.addView(Button(ctx).apply {
            text = "💾 Save QR & Social Links"
            setOnClickListener {
                customQrTitle = qrTitleEt.text.toString().trim().ifEmpty { "Rider Payment QR" }
                customQrBase64 = tempQrB64
                fbUrl = fbEt.text.toString().trim()
                waUrl = waEt.text.toString().trim()
                twUrl = twEt.text.toString().trim()
                ytUrl = ytEt.text.toString().trim()

                prefs.edit()
                    .putString("custom_qr_title", customQrTitle)
                    .putString("custom_qr_b64", customQrBase64)
                    .putString("social_fb", fbUrl)
                    .putString("social_wa", waUrl)
                    .putString("social_tw", twUrl)
                    .putString("social_yt", ytUrl)
                    .apply()

                try {
                    FirebaseDatabase.getInstance().reference
                        .child("captain_smart_pro")
                        .child("config")
                        .updateChildren(
                            mapOf(
                                "qrTitle" to customQrTitle,
                                "qrBase64" to customQrBase64,
                                "facebook" to fbUrl,
                                "whatsapp" to waUrl,
                                "twitter" to twUrl,
                                "youtube" to ytUrl
                            )
                        )
                } catch (_: Exception) {
                }
                Toast.makeText(ctx, "QR & Social Links Saved", Toast.LENGTH_SHORT).show()
            }
        })
        scroll.addView(box)

        AlertDialog.Builder(ctx)
            .setTitle("📱 QR Code & Social Links")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openRiderLiveChatDialog(ctx: Context, riderId: String, riderName: String) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        var attachPhotoB64 = ""

        val scroll = ScrollView(ctx)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 20)
        }
        val chatTv = TextView(ctx).apply {
            text = prefs.getString("local_chat_$riderId", "No messages yet.") ?: "No messages yet."
            textSize = 13f
            setPadding(0, 0, 0, 10)
        }
        val msgEt = EditText(ctx).apply {
            hint = tr("Type message...", "संदेश लिखें...", "বাৰ্তা লিখক...")
        }
        val photoPreview = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(180, 180).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 6
                bottomMargin = 6
            }
        }
        val pickPhotoBtn = Button(ctx).apply {
            text = tr("📷 Attach Photo", "📷 फ़ोटो लगाएं", "📷 ফটো সংলগ্ন কৰক")
            setOnClickListener {
                pickPhotoFromGallery(ctx) { b64 ->
                    attachPhotoB64 = b64
                    decodeBase64Bitmap(b64)?.let { photoPreview.setImageBitmap(it) }
                    Toast.makeText(ctx, "Photo attached", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val sendBtn = Button(ctx).apply {
            text = tr("📤 Send", "📤 भेजें", "📤 পঠিয়াওক")
            setOnClickListener {
                val txt = msgEt.text.toString().trim()
                if (txt.isEmpty() && attachPhotoB64.isBlank()) return@setOnClickListener
                val line = if (attachPhotoB64.isNotBlank()) "$txt [Photo]" else txt
                val updated = (chatTv.text.toString() + "\n$riderName ($riderId): $line").trim()
                chatTv.text = updated
                prefs.edit().putString("local_chat_$riderId", updated).apply()

                try {
                    val chatRef = FirebaseDatabase.getInstance().reference
                        .child("captain_smart_pro")
                        .child("chats")
                        .child(riderId)
                    val key = chatRef.push().key
                    if (key != null) {
                        chatRef.child(key).setValue(
                            mapOf(
                                "sender" to "$riderName ($riderId)",
                                "message" to txt,
                                "photoB64" to attachPhotoB64,
                                "time" to System.currentTimeMillis()
                            )
                        )
                    }
                } catch (_: Exception) {
                }
                msgEt.setText("")
                attachPhotoB64 = ""
                photoPreview.setImageDrawable(null)
            }
        }

        box.addView(chatTv)
        box.addView(msgEt)
        box.addView(photoPreview)
        box.addView(pickPhotoBtn)
        box.addView(sendBtn)
        scroll.addView(box)

        AlertDialog.Builder(ctx)
            .setTitle(tr("💬 Live Chat", "💬 लाइव चैट", "💬 লাইভ চেট"))
            .setView(scroll)
            .setNegativeButton(tr("Close", "बंद करें", "বন্ধ কৰক"), null)
            .show()
    }

    private fun openRiderSettingsDialog(
        ctx: Context,
        riderId: String,
        pickupEt: EditText,
        swPickupLimit: Switch,
        dropEt: EditText,
        swDropLimit: Switch,
        amountEt: EditText,
        swFareLimit: Switch,
        swOnlyOlaRapido: Switch,
        swSoundTts: Switch,
        swAutoAccept: Switch,
        swScreenshot: Switch,
        onSaved: () -> Unit
    ) {
        val scroll = ScrollView(ctx)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 20)
        }
        val pEt = EditText(ctx).apply {
            hint = tr("Pickup Distance (Km)", "पिकअप दूरी (किमी)", "পিকআপ দূৰত্ব (কিঃমিঃ)")
            setText(pickupEt.text.toString())
        }
        val pSw = Switch(ctx).apply {
            text = tr("Pickup Limit (On/Off)", "पिकअप सीमा (On/Off)", "পিকআপ সীমা (On/Off)")
            isChecked = swPickupLimit.isChecked
        }
        val dEt = EditText(ctx).apply {
            hint = tr("Drop Distance (Km)", "ड्रॉप दूरी (किमी)", "ড্ৰপ দূৰত্ব (কিঃমিঃ)")
            setText(dropEt.text.toString())
        }
        val dSw = Switch(ctx).apply {
            text = tr("Drop Limit (On/Off)", "ड्रॉप सीमा (On/Off)", "ড্ৰপ সীমা (On/Off)")
            isChecked = swDropLimit.isChecked
        }
        val aEt = EditText(ctx).apply {
            hint = tr("Max Fare / Ride Amount (₹)", "अधिकतम किराया / अमाउंट (₹)", "সৰ্বাধিক ভাড়া / অমাউণ্ট (₹)")
            setText(amountEt.text.toString())
        }
        val fSw = Switch(ctx).apply {
            text = tr("Fare / Amount Limit (On/Off)", "किराया / अमाउंट सीमा (On/Off)", "ভাড়া / অমাউণ্ট সীমা (On/Off)")
            isChecked = swFareLimit.isChecked
        }
        val s1 = Switch(ctx).apply {
            text = tr("Only Ola/Rapido", "केवल Ola/Rapido", "কেৱল Ola/Rapido")
            isChecked = swOnlyOlaRapido.isChecked
        }
        val s2 = Switch(ctx).apply {
            text = tr("Play Sound / TTS", "आवाज़ / TTS चलाएं", "আৱাজ / TTS চলাওক")
            isChecked = swSoundTts.isChecked
        }
        val s3 = Switch(ctx).apply {
            text = tr("Auto Accept", "ऑटो एक्सेप्ट", "অটো এক্সেপ্ট")
            isChecked = swAutoAccept.isChecked
        }
        val s4 = Switch(ctx).apply {
            text = tr("Screenshot Save to Gallery", "स्क्रीनशॉट गैलरी में सेव करें", "স্ক্ৰীণশ্বট গেলাৰীত ছেভ কৰক")
            isChecked = swScreenshot.isChecked
        }

        box.addView(TextView(ctx).apply { text = tr("Pickup (Km):", "पिकअप (किमी):", "পিকআপ (কিঃমিঃ):") })
        box.addView(pEt)
        box.addView(pSw)
        box.addView(TextView(ctx).apply { text = tr("Drop (Km):", "ड्रॉप (किमी):", "ড্ৰপ (কিঃমিঃ):") })
        box.addView(dEt)
        box.addView(dSw)
        box.addView(TextView(ctx).apply { text = tr("Fare / Amount (₹):", "किराया / अमाउंट (₹):", "ভাড়া / অমাউণ্ট (₹):") })
        box.addView(aEt)
        box.addView(fSw)
        box.addView(s1)
        box.addView(s2)
        box.addView(s3)
        box.addView(s4)
        box.addView(TextView(ctx).apply {
            text = "\nRider ID: $riderId"
            textSize = 12f
        })
        scroll.addView(box)

        AlertDialog.Builder(ctx)
            .setTitle(tr("⚙️ Settings", "⚙️ सेटिंग्स", "⚙️ ছেটিংছ"))
            .setView(scroll)
            .setPositiveButton(tr("Save", "सेव करें", "ছেভ কৰক")) { _, _ ->
                pickupEt.setText(pEt.text.toString().trim())
                swPickupLimit.isChecked = pSw.isChecked
                dropEt.setText(dEt.text.toString().trim())
                swDropLimit.isChecked = dSw.isChecked
                amountEt.setText(aEt.text.toString().trim())
                swFareLimit.isChecked = fSw.isChecked
                swOnlyOlaRapido.isChecked = s1.isChecked
                swSoundTts.isChecked = s2.isChecked
                swAutoAccept.isChecked = s3.isChecked
                swScreenshot.isChecked = s4.isChecked
                onSaved()
            }
            .setNegativeButton(tr("Close", "बंद करें", "বন্ধ কৰক"), null)
            .show()
    }

    private fun openRiderLinksDialog(ctx: Context) {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }
        box.addView(buildCompactSocialLinksCard(ctx))
        AlertDialog.Builder(ctx)
            .setTitle(tr("📱 Social Links", "📱 सोशल लिंक्स", "📱 ছ'চিয়েল লিংকসমূহ"))
            .setView(box)
            .setNegativeButton(tr("Close", "बंद करें", "বন্ধ কৰক"), null)
            .show()
    }

    private fun showPaymentLockScreen(ctx: Context, riderId: String, riderName: String) {
        var selectedPlanLabel = "1 Month (₹$plan1MonthPrice)"

        val scroll = ScrollView(ctx).apply {
            setBackgroundColor(bgDark)
            setPadding(22, 72, 22, 116)
        }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 14)
        }
        topRow.addView(makeStyledButton(ctx, "🛡️ ADMIN", accentBlue).apply {
            textSize = 11f
            setOnClickListener { promptAdminPassport(ctx, riderId) }
        })
        topRow.addView(TextView(ctx).apply {
            text = "  Captain Smart Pro"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentGreenBright)
        })
        col.addView(topRow)

        val planCard = makeCard(ctx, cardDark, borderSlate)
        planCard.addView(TextView(ctx).apply {
            text = tr("🏷️ Select Subscription Plan", "🏷️ सब्सक्रिप्शन प्लान चुनें", "🏷️ ছাবস্ক্ৰিপচন প্লেন বাছনি কৰক")
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
            setPadding(0, 0, 0, 8)
        })
        val selectedPlanTv = TextView(ctx).apply {
            text = "Selected: $selectedPlanLabel"
            textSize = 12f
            setTextColor(accentGreenBright)
            setPadding(0, 0, 0, 10)
        }
        planCard.addView(selectedPlanTv)

        planCard.addView(makeStyledButton(ctx, "1 Month — ₹$plan1MonthPrice", accentBlue).apply {
            textSize = 12f
            setOnClickListener {
                selectedPlanLabel = "1 Month (₹$plan1MonthPrice)"
                selectedPlanTv.text = "Selected: $selectedPlanLabel"
            }
        })
        planCard.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 8) })
        planCard.addView(makeStyledButton(ctx, "2 Months — ₹$plan2MonthPrice", accentPurple).apply {
            textSize = 12f
            setOnClickListener {
                selectedPlanLabel = "2 Months (₹$plan2MonthPrice)"
                selectedPlanTv.text = "Selected: $selectedPlanLabel"
            }
        })
        planCard.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 8) })
        planCard.addView(makeStyledButton(ctx, "3 Months — ₹$plan3MonthPrice", accentTeal).apply {
            textSize = 12f
            setOnClickListener {
                selectedPlanLabel = "3 Months (₹$plan3MonthPrice)"
                selectedPlanTv.text = "Selected: $selectedPlanLabel"
            }
        })
        col.addView(planCard)

        val payCard = makeCard(ctx, cardDark, accentGold).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        payCard.addView(TextView(ctx).apply {
            text = "$riderName ($riderId)"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
            gravity = Gravity.CENTER
        })
        payCard.addView(TextView(ctx).apply {
            text = customQrTitle
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentGold)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 10)
        })
        payCard.addView(ImageView(ctx).apply {
            val uploadedQr = decodeBase64Bitmap(customQrBase64)
            setImageBitmap(uploadedQr ?: generateQrMatrix(customQrTitle))
            layoutParams = LinearLayout.LayoutParams(320, 320).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        })
        col.addView(payCard)

        col.addView(makeStyledButton(
            ctx,
            tr("✅ I Have Paid • Notify Admin", "✅ मैंने पेमेंट कर दिया • एडमिन को बताएं", "✅ মই পেমেণ্ট কৰিলোঁ • এডমিনক জনাওক"),
            accentGreen
        ).apply {
            setOnClickListener {
                try {
                    FirebaseDatabase.getInstance().reference
                        .child("captain_smart_pro")
                        .child("payment_requests")
                        .child(riderId)
                        .setValue(
                            mapOf(
                                "riderId" to riderId,
                                "riderName" to riderName,
                                "planLabel" to selectedPlanLabel,
                                "status" to "PENDING_APPROVAL",
                                "time" to System.currentTimeMillis()
                            )
                        )
                } catch (_: Exception) {
                }
                Toast.makeText(ctx, "Payment notification sent ($selectedPlanLabel)", Toast.LENGTH_LONG).show()
            }
        })
        col.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 12) })
        col.addView(makeStyledButton(
            ctx,
            tr("💬 Live Chat", "💬 लाइव चैट", "💬 লাইভ চেট"),
            accentPurple
        ).apply {
            setOnClickListener { openRiderLiveChatDialog(ctx, riderId, riderName) }
        })
        col.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 14) })
        col.addView(buildCompactSocialLinksCard(ctx))

        scroll.addView(col)
        setContentView(scroll)
    }

    private fun showRiderDashboard(ctx: Context, riderId: String, riderName: String) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val pickupDef = prefs.getString("pickup_km", "1.0") ?: "1.0"
        val pickupLimitDef = prefs.getBoolean("pickup_limit_on", true)
        val dropDef = prefs.getString("drop_km", "4.0") ?: "4.0"
        val dropLimitDef = prefs.getBoolean("drop_limit_on", false)
        val fareDef = prefs.getString("fare_rs", "20") ?: "20"
        val fareLimitDef = prefs.getBoolean("fare_limit_on", true)
        val vehicleCat = VehicleCategory.fromKey(prefs.getString("rider_vehicle_type", "bike")) ?: VehicleCategory.BIKE

        var isEngineActivated = false
        var isAutoAcceptEnabled = false
        var isParcelModeOn = prefs.getBoolean("parcel_mode_on", false)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgDark)
        }

        val topBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(headerDark)
            setPadding(16, 72, 16, 16)
            gravity = Gravity.CENTER_VERTICAL
        }

        val leftAdminBtn = Button(ctx).apply {
            text = "🛡️ ADMIN"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = make3dPill(cardElevated, Color.parseColor("#60A5FA"), 12f)
            setPadding(12, 8, 12, 8)
            setOnClickListener { promptAdminPassport(ctx, riderId) }
        }

        val riderVehicleBadge = ImageView(ctx).apply {
            setImageDrawable(createVehicleIconDrawable(vehicleCat))
            layoutParams = LinearLayout.LayoutParams(48, 48).apply { leftMargin = 8 }
        }

        val brandCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 8
            }
        }
        brandCol.addView(TextView(ctx).apply {
            text = "Captain Smart Pro"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentGreenBright)
        })
        brandCol.addView(TextView(ctx).apply {
            text = "$riderName • $riderId • ${vehicleCat.label}"
            textSize = 11f
            setTextColor(textMuted)
        })

        val riderDotsBtn = Button(ctx).apply {
            text = "⋮"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
            background = make3dPill(cardElevated, Color.parseColor("#60A5FA"), 12f)
            setPadding(14, 6, 14, 6)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = 6 }
        }

        topBar.addView(leftAdminBtn)
        topBar.addView(riderVehicleBadge)
        topBar.addView(brandCol)
        topBar.addView(riderDotsBtn)
        root.addView(topBar)

        val frame = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val homeScroll = ScrollView(ctx).apply { setPadding(18, 14, 18, 116) }
        val homeLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        if (broadcastMessage.isNotBlank()) {
            val noticeCard = makeCard(ctx, cardElevated, accentGold)
            noticeCard.addView(TextView(ctx).apply {
                text = tr("📢 Notice: ", "📢 सूचना: ", "📢 জাননী: ") + broadcastMessage
                textSize = 12f
                setTextColor(accentGold)
            })
            homeLayout.addView(noticeCard)
        }

        val fleetCard = makeCard(ctx, cardDark, Color.parseColor("#3B82F6"))
        val fleetRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val fleetLeft = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        fleetLeft.addView(TextView(ctx).apply {
            text = "⚡ Rapido / Ola / Uber"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
        })
        fleetLeft.addView(TextView(ctx).apply {
            text = "Dibrugarh Fleet Dispatcher (${vehicleCat.label})"
            textSize = 12f
            setTextColor(Color.parseColor("#60A5FA"))
            setPadding(0, 4, 0, 4)
        })
        val fleetStateTv = TextView(ctx).apply {
            text = tr(
                "⏸️ Standby (Open ⋮ to Activate Engine)",
                "⏸️ स्टैंडबाय (⋮ से इंजन एक्टिव करें)",
                "⏸️ ষ্টেণ্ডবাই (⋮ ৰ পৰা ইঞ্জিন এক্টিভ কৰক)"
            )
            textSize = 12f
            setTextColor(Color.parseColor("#F87171"))
        }
        val gpsSyncTv = TextView(ctx).apply {
            text = formatLastSyncAge(System.currentTimeMillis())
            textSize = 11f
            setTextColor(accentGreenBright)
            setPadding(0, 4, 0, 0)
        }
        fleetLeft.addView(fleetStateTv)
        fleetLeft.addView(gpsSyncTv)

        val gpsUiHandler = Handler(Looper.getMainLooper())
        val gpsUiRunnable = object : Runnable {
            override fun run() {
                gpsSyncTv.text = formatLastSyncAge(System.currentTimeMillis())
                gpsUiHandler.postDelayed(this, 4_000L)
            }
        }
        gpsUiHandler.post(gpsUiRunnable)

        val pickupEt = makeStyledInput(ctx, pickupDef, "Pickup Km")
        val dropEt = makeStyledInput(ctx, dropDef, "Drop Km")
        val amountEt = makeStyledInput(ctx, fareDef, "Fare / Amount ₹")

        val rulesSummaryTv = TextView(ctx).apply {
            textSize = 12f
            setTextColor(accentGold)
            setPadding(0, 0, 0, 10)
        }

        val swPickupLimit = Switch(ctx).apply {
            text = tr("📍 Pickup Limit (On/Off)", "📍 पिकअप सीमा (On/Off)", "📍 পিকআপ সীমা (On/Off)")
            isChecked = pickupLimitDef
            setTextColor(textWhite)
            textSize = 14f
            setPadding(0, 8, 0, 8)
        }
        val swDropLimit = Switch(ctx).apply {
            text = tr("🏁 Drop Limit (On/Off)", "🏁 ड्रॉप सीमा (On/Off)", "🏁 ড্ৰপ সীমা (On/Off)")
            isChecked = dropLimitDef
            setTextColor(textWhite)
            textSize = 14f
            setPadding(0, 8, 0, 8)
        }
        val swFareLimit = Switch(ctx).apply {
            text = tr("💰 Fare / Amount Limit (On/Off)", "💰 किराया / अमाउंट सीमा (On/Off)", "💰 ভাড়া / অমাউণ্ট সীমা (On/Off)")
            isChecked = fareLimitDef
            setTextColor(textWhite)
            textSize = 14f
            setPadding(0, 8, 0, 8)
        }
        val swOnlyOlaRapido = Switch(ctx).apply {
            text = tr("⚡ Only Ola/Rapido", "⚡ केवल Ola/Rapido", "⚡ কেৱল Ola/Rapido")
            isChecked = true
            setTextColor(textWhite)
            textSize
