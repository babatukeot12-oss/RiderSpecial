package com.riderspecial.app

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.database.ChildEventListener
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
    private var configurableTrialDays = 3L

    private var plan1MonthPrice = 49
    private var plan2MonthPrice = 70
    private var plan3MonthPrice = 99

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var selectedLang = "EN"

    private var fbUrl = ""
    private var waUrl = ""
    private var twUrl = ""
    private var ytUrl = ""
    private var broadcastMessage = ""
    private var customQrTitle = "Rider Payment QR"
    private var customQrBase64 = ""

    private var pendingPhotoCallback: ((String) -> Unit)? = null
    private val reqPickImage = 4091
    private var lastNotifiedMsgKey = ""

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
        createChatNotificationChannel(ctx)
        startRiderMessageWatcher(ctx)

        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        selectedLang = prefs.getString("app_lang", "EN") ?: "EN"

        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                applyTtsLanguage()
                ttsReady = true
            }
        }

        val hardwareId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown_device"

        loadAndStart(ctx, hardwareId)
    }

    private fun resetLocalLoginForFreshAccount(ctx: Context, hardwareId: String) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val prevRiderId = prefs.getString("rider_mobile_id", "")?.trim().orEmpty()
        prefs.edit()
            .remove("rider_full_name")
            .remove("rider_mobile_id")
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
            if (selectedLang == "HI") "पुराना लॉगिन हटाया गया, नया अकाउंट बनाएं" else "Previous login cleared for fresh account",
            Toast.LENGTH_LONG
        ).show()
        showRiderRegistrationScreen(ctx, hardwareId, "", "", "")
    }

    private fun openAccessibilityScreen(ctx: Context) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(ctx, "Open Phone Settings > Accessibility", Toast.LENGTH_LONG).show()
        }
    }

    private fun createChatNotificationChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "csp_rider_chat",
                "Rider Chat Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a rider sends a message or photo"
            }
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun showRiderMessageNotification(ctx: Context, title: String, bodyText: String) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.Notification.Builder(ctx, "csp_rider_chat")
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(ctx)
            }
            val notif = builder
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(title)
                .setContentText(bodyText)
                .setAutoCancel(true)
                .build()
            nm.notify((System.currentTimeMillis() % 100000).toInt(), notif)
        } catch (_: Exception) {
        }
    }

    private fun startRiderMessageWatcher(ctx: Context) {
        val startTime = System.currentTimeMillis()
        try {
            FirebaseDatabase.getInstance().reference
                .child("captain_smart_pro")
                .child("chats")
                .addChildEventListener(object : ChildEventListener {
                    override fun onChildAdded(riderSnap: DataSnapshot, previousChildName: String?) {
                        attachSingleRiderChatListener(ctx, riderSnap.key ?: return, startTime)
                    }

                    override fun onChildChanged(riderSnap: DataSnapshot, previousChildName: String?) {
                        attachSingleRiderChatListener(ctx, riderSnap.key ?: return, startTime)
                    }

                    override fun onChildRemoved(snapshot: DataSnapshot) {}
                    override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                    override fun onCancelled(error: DatabaseError) {}
                })
        } catch (_: Exception) {
        }
    }

    private fun attachSingleRiderChatListener(ctx: Context, riderId: String, startTime: Long) {
        try {
            FirebaseDatabase.getInstance().reference
                .child("captain_smart_pro")
                .child("chats")
                .child(riderId)
                .addChildEventListener(object : ChildEventListener {
                    override fun onChildAdded(msgSnap: DataSnapshot, previousChildName: String?) {
                        val key = msgSnap.key ?: return
                        if (key == lastNotifiedMsgKey) return
                        val sender = msgSnap.child("sender").getValue(String::class.java) ?: "Rider"
                        if (sender.startsWith("Admin", ignoreCase = true)) return
                        val msgTime = msgSnap.child("time").getValue(Long::class.java) ?: 0L
                        if (msgTime < startTime - 2000L) return

                        lastNotifiedMsgKey = key
                        val message = msgSnap.child("message").getValue(String::class.java).orEmpty()
                        val hasPhoto = !(msgSnap.child("photoB64").getValue(String::class.java).isNullOrBlank())
                        val preview = when {
                            message.isNotBlank() && hasPhoto -> "$message [Photo]"
                            hasPhoto -> "Photo / Screenshot bheja hai"
                            else -> message
                        }
                        showRiderMessageNotification(
                            ctx,
                            "Rider Message: $sender ($riderId)",
                            preview
                        )
                        Toast.makeText(ctx, "$sender ne message bheja: $preview", Toast.LENGTH_LONG).show()
                    }

                    override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                    override fun onChildRemoved(snapshot: DataSnapshot) {}
                    override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                    override fun onCancelled(error: DatabaseError) {}
                })
        } catch (_: Exception) {
        }
    }

    private fun applyTtsLanguage() {
        tts?.language = if (selectedLang == "HI") Locale("hi", "IN") else Locale.US
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
        val screenW = ctx.resources.displayMetrics.widthPixels
        val baseW = (screenW * 0.85f).toInt().coerceAtLeast(420)
        val aspect = if (bmp.width > 0) bmp.height.toFloat() / bmp.width.toFloat() else 1.5f
        val baseH = (baseW * aspect).toInt().coerceIn(360, 1400)

        var zoomFactor = 1.0f

        val vScroll = ScrollView(ctx)
        val hScroll = HorizontalScrollView(ctx)
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
                bottomMargin = 12
            }
        }

        val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomFactor = (zoomFactor * detector.scaleFactor).coerceIn(1.0f, 3.0f)
                bigView.layoutParams = LinearLayout.LayoutParams(
                    (baseW * zoomFactor).toInt(),
                    (baseH * zoomFactor).toInt()
                ).apply { bottomMargin = 12 }
                return true
            }
        })

        bigView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            true
        }

        val zoomRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 10)
        }
        val zoomInBtn = Button(ctx).apply {
            text = "Zoom +"
            setOnClickListener {
                zoomFactor = (zoomFactor + 0.5f).coerceAtMost(3.0f)
                bigView.layoutParams = LinearLayout.LayoutParams(
                    (baseW * zoomFactor).toInt(),
                    (baseH * zoomFactor).toInt()
                ).apply { bottomMargin = 12 }
            }
        }
        val zoomResetBtn = Button(ctx).apply {
            text = "Reset"
            setOnClickListener {
                zoomFactor = 1.0f
                bigView.layoutParams = LinearLayout.LayoutParams(baseW, baseH).apply { bottomMargin = 12 }
            }
        }
        zoomRow.addView(zoomInBtn)
        zoomRow.addView(zoomResetBtn)

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

        hScroll.addView(bigView)
        box.addView(hScroll)
        box.addView(zoomRow)
        box.addView(dlBtn)
        vScroll.addView(box)

        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(vScroll)
            .setNegativeButton("Close", null)
            .show()
    }

    // Updated Ride Rules:
    // - Pickup <= maxPickupKm
    // - If dropLimitEnabled is ON: Drop <= maxDropKm; if OFF: no upper drop limit
    // - Fare >= minFareRs (higher allowed, lower not allowed)
    private fun matchesRideRules(
        ridePickupKm: Float,
        rideDropKm: Float,
        rideFareRs: Int,
        maxPickupKm: Float,
        maxDropKm: Float,
        dropLimitEnabled: Boolean,
        minFareRs: Int
    ): Boolean {
        val pickupOk = ridePickupKm <= maxPickupKm
        val dropOk = if (dropLimitEnabled) (rideDropKm <= maxDropKm) else true
        val fareOk = rideFareRs >= minFareRs
        return pickupOk && dropOk && fareOk
    }

    private fun loadAndStart(ctx: Context, hardwareId: String) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val nowMillis = System.currentTimeMillis()

        configurableTrialDays = prefs.getLong("trial_days_global", 3L).coerceAtLeast(0L)
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
        val riderPhotoB64 = prefs.getString("rider_photo_b64", "") ?: ""

        if (riderName.isBlank() || riderMobile.isBlank()) {
            showRiderRegistrationScreen(ctx, hardwareId, riderName, riderMobile, riderPhotoB64)
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

        try {
            val rootRef = FirebaseDatabase.getInstance().reference.child("captain_smart_pro")
            rootRef.child("riders").child(riderId).updateChildren(
                mapOf(
                    "riderId" to riderId,
                    "fullName" to riderName,
                    "mobile" to riderMobile,
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
            text = "🚫 Account Paused by Admin"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F87171"))
        })
        card.addView(TextView(ctx).apply {
            text = "\n$riderName ($riderId)\nContact Admin for support."
            textSize = 13f
            setTextColor(textWhite)
        })
        col.addView(card)
        scroll.addView(col)
        setContentView(scroll)
    }

    private fun showRiderRegistrationScreen(
        ctx: Context,
        hardwareId: String,
        existingName: String,
        existingMobile: String,
        existingPhotoB64: String
    ) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        var photoB64 = existingPhotoB64

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
            text = if (selectedLang == "HI") "🪪 नया राइडर अकाउंट (नाम व मोबाइल ID)" else "🪪 Fresh Rider Account (Name & Mobile ID)"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
            setPadding(0, 0, 0, 10)
        })

        val nameEt = makeStyledInput(ctx, existingName, if (selectedLang == "HI") "पूरा नाम" else "Full Name")
        val mobileEt = makeStyledInput(ctx, existingMobile, if (selectedLang == "HI") "मोबाइल नंबर (Rider ID)" else "Mobile Number (Rider ID)")
        mobileEt.inputType = InputType.TYPE_CLASS_PHONE

        val photoPreview = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(180, 180).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 10
                bottomMargin = 10
            }
            val bmp = decodeBase64Bitmap(photoB64)
            if (bmp != null) setImageBitmap(bmp)
        }

        val pickPhotoBtn = makeStyledButton(
            ctx,
            if (selectedLang == "HI") "📷 राइडर फ़ोटो चुनें" else "📷 Choose Rider Photo",
            cardElevated
        ).apply {
            textSize = 12f
            setOnClickListener {
                pickPhotoFromGallery(ctx) { b64 ->
                    photoB64 = b64
                    decodeBase64Bitmap(b64)?.let { photoPreview.setImageBitmap(it) }
                    Toast.makeText(ctx, "Photo selected", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val saveBtn = makeStyledButton(
            ctx,
            if (selectedLang == "HI") "✅ नया अकाउंट शुरू करें" else "✅ Start Fresh Account",
            accentGreen
        ).apply {
            setOnClickListener {
                val fullName = nameEt.text.toString().trim()
                val mobileId = mobileEt.text.toString().trim()
                if (fullName.isEmpty() || mobileId.length < 10) {
                    Toast.makeText(ctx, "Enter Full Name and valid Mobile Number", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                prefs.edit()
                    .putString("rider_full_name", fullName)
                    .putString("rider_mobile_id", mobileId)
                    .putString("rider_photo_b64", photoB64)
                    .putLong("trial_start_$mobileId", System.currentTimeMillis())
                    .apply()
                loadAndStart(ctx, hardwareId)
            }
        }

        card.addView(nameEt)
        card.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 10) })
        card.addView(mobileEt)
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
                        if (selectedLang == "HI") "गलत पासवर्ड" else "Invalid Passport",
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
        val mLang = "🌐 Language: Hindi / English"
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
                    mLang -> {
                        toggleAppLanguage(ctx)
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
            hint = "🔍 Search by name or mobile ID..."
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
            .setTitle(if (onlyOnline) "🟢 Online Riders" else "👥 Total Riders")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .show()

        data class RiderEntry(
            val rId: String,
            val name: String,
            val mobile: String,
            val photoB64: String,
            val isOnline: Boolean
        )

        val allEntries = mutableListOf<RiderEntry>()

        fun renderEntries(filterQuery: String) {
            listContainer.removeAllViews()
            val q = filterQuery.trim().lowercase(Locale.US)
            var count = 0
            for (entry in allEntries) {
                if (q.isNotEmpty() &&
                    !entry.name.lowercase(Locale.US).contains(q) &&
                    !entry.mobile.lowercase(Locale.US).contains(q)
                ) {
                    continue
                }
                count++
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 8, 0, 8)
                }
                val bmp = decodeBase64Bitmap(entry.photoB64)
                val photoView = ImageView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(96, 96).apply { rightMargin = 12 }
                    if (bmp != null) {
                        setImageBitmap(bmp)
                        setOnClickListener { openLargePhotoDialog(ctx, bmp, "${entry.name} (${entry.mobile})") }
                    }
                }
                val detailsTv = TextView(ctx).apply {
                    text = "${entry.name}\nID / Mobile: ${entry.mobile}\nStatus: ${if (entry.isOnline) "ONLINE" else "OFFLINE"}"
                    textSize = 12f
                }
                row.addView(photoView)
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
                        val photoB64 = child.child("photoB64").getValue(String::class.java) ?: ""
                        val lastSeen = child.child("lastSeen").getValue(Long::class.java) ?: 0L
                        val isOnline = (child.child("online").getValue(Boolean::class.java) ?: false) &&
                            ((now - lastSeen) < TimeUnit.MINUTES.toMillis(15))
                        if (onlyOnline && !isOnline) continue
                        allEntries.add(RiderEntry(rId, name, mobile, photoB64, isOnline))
                    }
                    renderEntries(searchEt.text.toString())
                }
        } catch (_: Exception) {
            listContainer.removeAllViews()
            listContainer.addView(TextView(ctx).apply { text = "Unable to load riders list." })
        }
    }

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
            hint = "Free Trial Days (e.g. 3, 7, 1)"
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
                val tDays = trialEt.text.toString().trim().toLongOrNull() ?: 3L
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
            hint = if (selectedLang == "HI") "संदेश लिखें..." else "Type message..."
        }
        val photoPreview = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(180, 180).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 6
                bottomMargin = 6
            }
        }
        val pickPhotoBtn = Button(ctx).apply {
            text = if (selectedLang == "HI") "📷 फ़ोटो लगाएं" else "📷 Attach Photo"
            setOnClickListener {
                pickPhotoFromGallery(ctx) { b64 ->
                    attachPhotoB64 = b64
                    decodeBase64Bitmap(b64)?.let { photoPreview.setImageBitmap(it) }
                    Toast.makeText(ctx, "Photo attached", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val sendBtn = Button(ctx).apply {
            text = if (selectedLang == "HI") "📤 भेजें" else "📤 Send"
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
            .setTitle(if (selectedLang == "HI") "💬 लाइव चैट" else "💬 Live Chat")
            .setView(scroll)
            .setNegativeButton(if (selectedLang == "HI") "बंद करें" else "Close", null)
            .show()
    }

    private fun openRiderSettingsDialog(
        ctx: Context,
        riderId: String,
        pickupEt: EditText,
        dropEt: EditText,
        amountEt: EditText,
        swDropLimit: Switch,
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
            hint = if (selectedLang == "HI") "अधिकतम पिकअप दूरी (किमी)" else "Max Pickup Distance (Km)"
            setText(pickupEt.text.toString())
        }
        val dEt = EditText(ctx).apply {
            hint = if (selectedLang == "HI") "ड्रॉप दूरी (किमी)" else "Drop Distance (Km)"
            setText(dropEt.text.toString())
        }
        val dSw = Switch(ctx).apply {
            text = if (selectedLang == "HI") "ड्रॉप सीमा लागू करें (On/Off)" else "Enable Drop Limit (On/Off)"
            isChecked = swDropLimit.isChecked
        }
        val aEt = EditText(ctx).apply {
            hint = if (selectedLang == "HI") "न्यूनतम किराया (₹)" else "Min Ride Amount / Fare (₹)"
            setText(amountEt.text.toString())
        }
        val s1 = Switch(ctx).apply {
            text = if (selectedLang == "HI") "केवल Ola/Rapido" else "Only Ola/Rapido"
            isChecked = swOnlyOlaRapido.isChecked
        }
        val s2 = Switch(ctx).apply {
            text = if (selectedLang == "HI") "आवाज़ / TTS चलाएं" else "Play Sound / TTS"
            isChecked = swSoundTts.isChecked
        }
        val s3 = Switch(ctx).apply {
            text = if (selectedLang == "HI") "ऑटो एक्सेप्ट" else "Auto Accept"
            isChecked = swAutoAccept.isChecked
        }
        val s4 = Switch(ctx).apply {
            text = if (selectedLang == "HI") "स्क्रीनशॉट गैलरी में सेव करें" else "Screenshot Save to Gallery"
            isChecked = swScreenshot.isChecked
        }

        box.addView(TextView(ctx).apply { text = if (selectedLang == "HI") "अधिकतम पिकअप (किमी):" else "Max Pickup (Km):" })
        box.addView(pEt)
        box.addView(TextView(ctx).apply { text = if (selectedLang == "HI") "ड्रॉप (किमी):" else "Drop (Km):" })
        box.addView(dEt)
        box.addView(dSw)
        box.addView(TextView(ctx).apply { text = if (selectedLang == "HI") "न्यूनतम अमाउंट (₹):" else "Min Ride Amount (₹):" })
        box.addView(aEt)
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
            .setTitle(if (selectedLang == "HI") "⚙️ सेटिंग्स" else "⚙️ Settings")
            .setView(scroll)
            .setPositiveButton(if (selectedLang == "HI") "सेव करें" else "Save") { _, _ ->
                pickupEt.setText(pEt.text.toString().trim())
                dropEt.setText(dEt.text.toString().trim())
                swDropLimit.isChecked = dSw.isChecked
                amountEt.setText(aEt.text.toString().trim())
                swOnlyOlaRapido.isChecked = s1.isChecked
                swSoundTts.isChecked = s2.isChecked
                swAutoAccept.isChecked = s3.isChecked
                swScreenshot.isChecked = s4.isChecked
                onSaved()
            }
            .setNegativeButton(if (selectedLang == "HI") "बंद करें" else "Close", null)
            .show()
    }

    private fun openRiderLinksDialog(ctx: Context) {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }
        box.addView(buildCompactSocialLinksCard(ctx))
        AlertDialog.Builder(ctx)
            .setTitle(if (selectedLang == "HI") "📱 सोशल लिंक्स" else "📱 Social Links")
            .setView(box)
            .setNegativeButton(if (selectedLang == "HI") "बंद करें" else "Close", null)
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
            text = if (selectedLang == "HI") "🏷️ सब्सक्रिप्शन प्लान चुनें" else "🏷️ Select Subscription Plan"
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
            if (selectedLang == "HI") "✅ मैंने पेमेंट कर दिया • एडमिन को बताएं" else "✅ I Have Paid • Notify Admin",
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
            if (selectedLang == "HI") "💬 लाइव चैट" else "💬 Live Chat",
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
        val isHi = selectedLang == "HI"
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val pickupDef = prefs.getString("pickup_km", "1.0") ?: "1.0"
        val dropDef = prefs.getString("drop_km", "4.0") ?: "4.0"
        val dropLimitDef = prefs.getBoolean("drop_limit_on", false)
        val fareDef = prefs.getString("fare_rs", "20") ?: "20"

        var totalEarnings = prefs.getInt("total_earnings", 0)
        var totalRides = prefs.getInt("total_rides", 0)
        var totalDistance = prefs.getFloat("total_distance", 0f)

        var isEngineActivated = false
        var isAutoAcceptEnabled = false

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
            text = "$riderName • $riderId"
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
                text = (if (isHi) "📢 सूचना: " else "📢 Notice: ") + broadcastMessage
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
            text = "Dibrugarh Fleet Dispatcher"
            textSize = 12f
            setTextColor(Color.parseColor("#60A5FA"))
            setPadding(0, 4, 0, 4)
        })
        val fleetStateTv = TextView(ctx).apply {
            text = if (isHi) "⏸️ स्टैंडबाय (⋮ से एक्टिव करें)" else "⏸️ Standby (Open ⋮ to Activate)"
            textSize = 12f
            setTextColor(Color.parseColor("#F87171"))
        }
        fleetLeft.addView(fleetStateTv)

        val swDropLimit = makeSwitchRow(ctx, if (isHi) "🏁 ड्रॉप सीमा लागू करें (On/Off)" else "🏁 Drop Limit (On/Off)", dropLimitDef)
        val swOnlyOlaRapido = makeSwitchRow(ctx, if (isHi) "⚡ केवल Ola/Rapido" else "⚡ Only Ola/Rapido", true)
        val swSoundTts = makeSwitchRow(ctx, if (isHi) "🔊 आवाज़ / TTS चलाएं" else "🔊 Play Sound / TTS", true)
        val swAutoAccept = makeSwitchRow(ctx, if (isHi) "🤖 ऑटो एक्सेप्ट" else "🤖 Auto Accept", true)
        val swScreenshot = makeSwitchRow(ctx, if (isHi) "📸 स्क्रीनशॉट गैलरी में सेव करें" else "📸 Screenshot Save to Gallery", true)

        val pickupEt = makeStyledInput(ctx, pickupDef, "Pickup Km (<= set)")
        val dropEt = makeStyledInput(ctx, dropDef, "Drop Km")
        val amountEt = makeStyledInput(ctx, fareDef, "Min Amount ₹ (>= set)")

        val statsSummaryTv = TextView(ctx).apply {
            text = if (isHi)
                "📊 राइड्स: $totalRides | कमाई: ₹$totalEarnings | दूरी: ${String.format(Locale.US, "%.1f", totalDistance)} किमी"
            else
                "📊 Rides: $totalRides | Earnings: ₹$totalEarnings | Distance: ${String.format(Locale.US, "%.1f", totalDistance)} Km"
            textSize = 12f
            setTextColor(accentGreenBright)
            setPadding(0, 6, 0, 0)
        }

        val savePrefsAction: () -> Unit = {
            prefs.edit()
                .putString("pickup_km", pickupEt.text.toString().trim())
                .putString("drop_km", dropEt.text.toString().trim())
                .putBoolean("drop_limit_on", swDropLimit.isChecked)
                .putString("fare_rs", amountEt.text.toString().trim())
                .apply()
            speakText(
                ctx,
                swSoundTts.isChecked,
                if (isHi) "सेटिंग्स सेव हो गई" else "Rider preferences saved"
            )
            Toast.makeText(ctx, if (isHi) "सेटिंग्स सेव हुईं" else "Preferences Saved", Toast.LENGTH_SHORT).show()
        }

        val autoAcceptBtn = makeStyledButton(
            ctx,
            if (isHi) "⚡ ऑटो एक्सेप्ट\nबंद" else "⚡ AUTO ACCEPT\nOFF",
            Color.parseColor("#2A3954")
        ).apply {
            setOnClickListener {
                if (!isEngineActivated) {
                    Toast.makeText(
                        ctx,
                        if (isHi) "पहले ऊपर ⋮ से एक्टिव करें" else "Open ⋮ on top to Activate first",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                isAutoAcceptEnabled = !isAutoAcceptEnabled
                if (isAutoAcceptEnabled) {
                    text = if (isHi) "⚡ ऑटो एक्सेप्ट\nचालू" else "⚡ AUTO ACCEPT\nON"
                    background = make3dPill(accentGreen, accentGreenBright, 14f)
                    fleetStateTv.text = if (isHi) "✅ सक्रिय • ऑटो एक्सेप्ट चालू" else "✅ Active • Auto Accept ON"
                    fleetStateTv.setTextColor(accentGreenBright)
                    speakText(ctx, swSoundTts.isChecked, if (isHi) "ऑटो एक्सेप्ट चालू" else "Auto Accept On")
                } else {
                    text = if (isHi) "⚡ ऑटो एक्सेप्ट\nबंद" else "⚡ AUTO ACCEPT\nOFF"
                    background = make3dPill(Color.parseColor("#2A3954"), borderSlate, 14f)
                    fleetStateTv.text = if (isHi) "⏸️ एक्टिव • ऑटो एक्सेप्ट बंद" else "⏸️ Activated • Auto Accept OFF"
                    fleetStateTv.setTextColor(accentGold)
                    speakText(ctx, swSoundTts.isChecked, if (isHi) "ऑटो एक्सेप्ट बंद" else "Auto Accept Off")
                }
            }
        }

        riderDotsBtn.setOnClickListener {
            val popup = PopupMenu(ctx, riderDotsBtn)
            val actLabel = if (isEngineActivated) {
                if (isHi) "⏹️ इंजन बंद करें" else "⏹️ Deactivate Engine"
            } else {
                if (isHi) "▶️ इंजन चालू करें" else "▶️ Activate Engine"
            }
            val accLabel = if (isHi) "♿ एक्सेसिबिलिटी सेटिंग" else "♿ Accessibility Settings"
            val setLabel = if (isHi) "⚙️ सेटिंग्स" else "⚙️ Settings"
            val langLabel = if (isHi) "🌐 भाषा: हिन्दी / English" else "🌐 Language: Hindi / English"
            val chatLabel = if (isHi) "💬 लाइव चैट" else "💬 Live Chat"
            val linksLabel = if (isHi) "📱 सोशल लिंक्स" else "📱 Social Links"

            popup.menu.add(actLabel)
            popup.menu.add(accLabel)
            popup.menu.add(setLabel)
            popup.menu.add(langLabel)
            popup.menu.add(chatLabel)
            popup.menu.add(linksLabel)

            popup.setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    actLabel -> {
                        isEngineActivated = !isEngineActivated
                        if (isEngineActivated) {
                            fleetStateTv.text = if (isHi) "✅ एक्टिव • तैयार" else "✅ Activated • Ready for Auto Accept"
                            fleetStateTv.setTextColor(accentGold)
                            speakText(ctx, swSoundTts.isChecked, if (isHi) "इंजन चालू" else "Activated")
                            Toast.makeText(ctx, if (isHi) "इंजन चालू" else "Engine Activated", Toast.LENGTH_SHORT).show()
                        } else {
                            isAutoAcceptEnabled = false
                            autoAcceptBtn.text = if (isHi) "⚡ ऑटो एक्सेप्ट\nबंद" else "⚡ AUTO ACCEPT\nOFF"
                            autoAcceptBtn.background = make3dPill(Color.parseColor("#2A3954"), borderSlate, 14f)
                            fleetStateTv.text = if (isHi) "⏸️ स्टैंडबाय" else "⏸️ Standby (Deactivated)"
                            fleetStateTv.setTextColor(Color.parseColor("#F87171"))
                            speakText(ctx, swSoundTts.isChecked, if (isHi) "इंजन बंद" else "Deactivated")
                            Toast.makeText(ctx, if (isHi) "इंजन बंद" else "Engine Deactivated", Toast.LENGTH_SHORT).show()
                        }
                    }
                    accLabel -> openAccessibilityScreen(ctx)
                    setLabel -> openRiderSettingsDialog(
                        ctx, riderId, pickupEt, dropEt, amountEt,
                        swDropLimit, swOnlyOlaRapido, swSoundTts, swAutoAccept, swScreenshot, savePrefsAction
                    )
                    langLabel -> {
                        toggleAppLanguage(ctx)
                        showRiderDashboard(ctx, riderId, riderName)
                    }
                    chatLabel -> openRiderLiveChatDialog(ctx, riderId, riderName)
                    linksLabel -> openRiderLinksDialog(ctx)
                }
                true
            }
            popup.show()
        }

        fleetRow.addView(fleetLeft)
        fleetRow.addView(autoAcceptBtn)
        fleetCard.addView(fleetRow)
        fleetCard.addView(statsSummaryTv)
        homeLayout.addView(fleetCard)

        val row1 = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
        }
        row1.addView(makeStyledButton(
            ctx,
            if (isHi) "🎯 टेस्ट राइड एक्सेप्ट और सेव" else "🎯 TEST RIDE ACCEPT & SAVE",
            accentBlue
        ).apply {
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 6
            }
            setOnClickListener {
                val maxP = pickupEt.text.toString().trim().toFloatOrNull() ?: 1.0f
                val dKm = dropEt.text.toString().trim().toFloatOrNull() ?: 4.0f
                val minRs = amountEt.text.toString().trim().toIntOrNull() ?: 20

                if (!matchesRideRules(maxP, dKm, minRs, maxP, dKm, swDropLimit.isChecked, minRs)) {
                    Toast.makeText(ctx, "Ride did not match rules", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                totalRides += 1
                totalEarnings += minRs
                totalDistance += (maxP + dKm)
                prefs.edit()
                    .putInt("total_rides", totalRides)
                    .putInt("total_earnings", totalEarnings)
                    .putFloat("total_distance", totalDistance)
                    .apply()
                statsSummaryTv.text = if (isHi)
                    "📊 राइड्स: $totalRides | कमाई: ₹$totalEarnings | दूरी: ${String.format(Locale.US, "%.1f", totalDistance)} किमी"
                else
                    "📊 Rides: $totalRides | Earnings: ₹$totalEarnings | Distance: ${String.format(Locale.US, "%.1f", totalDistance)} Km"
                val saved = if (swScreenshot.isChecked) saveScreenshotToGallery(ctx, root) else false
                speakText(
                    ctx,
                    swSoundTts.isChecked,
                    if (isHi)
                        "$minRs रुपये की राइड एक्सेप्ट हुई, पिकअप $maxP किलोमीटर, ड्रॉप $dKm किलोमीटर"
                    else
                        "Ride accepted for $minRs rupees, pickup $maxP kilometer, drop $dKm kilometer"
                )
                Toast.makeText(
                    ctx,
                    if (saved) "Ride Accepted & Screenshot Saved" else "Ride Accepted",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
        row1.addView(makeStyledButton(
            ctx,
            if (isHi) "📋 ऑर्डर स्प्लिट फ़ॉर्म" else "📋 ORDER SPLIT FORM",
            accentPurple
        ).apply {
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 6
            }
            setOnClickListener {
                speakText(ctx, swSoundTts.isChecked, "Order Split Form Ready")
                Toast.makeText(ctx, "Order Split Form Ready", Toast.LENGTH_SHORT).show()
            }
        })
        homeLayout.addView(row1)

        val rulesCard = makeCard(ctx, cardDark, borderSlate)
        rulesCard.addView(TextView(ctx).apply {
            text = if (isHi) "🎯 राइडर नियम (पिकअप <= सेट • न्यूनतम ₹)" else "🎯 Rider Ride Rules (Pickup <= Set • Min ₹)"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
            setPadding(0, 0, 0, 10)
        })

        rulesCard.addView(TextView(ctx).apply {
            text = if (isHi) "📍 पिकअप दूरी (किमी) — सेट सीमा के अंदर" else "📍 Pickup Distance (Km) — Within set limit only"
            textSize = 12f
            setTextColor(textMuted)
        })
        rulesCard.addView(pickupEt)
        rulesCard.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 8) })

        rulesCard.addView(TextView(ctx).apply {
            text = if (isHi) "🏁 ड्रॉप दूरी (किमी) — On होने पर अंदर, Off होने पर खुली" else "🏁 Drop Distance (Km) — Capped when On, open when Off"
            textSize = 12f
            setTextColor(textMuted)
        })
        rulesCard.addView(dropEt)
        rulesCard.addView(swDropLimit)
        rulesCard.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 8) })

        rulesCard.addView(TextView(ctx).apply {
            text = if (isHi) "💰 राइड अमाउंट / किराया (₹) — सेट से ज़्यादा या बराबर" else "💰 Ride Amount / Fare (₹) — Equal or higher only"
            textSize = 12f
            setTextColor(textMuted)
        })
        rulesCard.addView(amountEt)

        rulesCard.addView(swOnlyOlaRapido)
        rulesCard.addView(swSoundTts)
        rulesCard.addView(swAutoAccept)
        rulesCard.addView(swScreenshot)

        rulesCard.addView(makeStyledButton(
            ctx,
            if (isHi) "💾 राइडर सेटिंग्स सेव करें" else "💾 SAVE RIDER PREFERENCES",
            accentGreen
        ).apply {
            setOnClickListener { savePrefsAction() }
        })
        homeLayout.addView(rulesCard)

        homeLayout.addView(buildCompactSocialLinksCard(ctx))

        homeScroll.addView(homeLayout)
        frame.addView(homeScroll)
        root.addView(frame)

        setContentView(root)
    }

    private fun toggleAppLanguage(ctx: Context) {
        selectedLang = if (selectedLang == "EN") "HI" else "EN"
        applyTtsLanguage()
        ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
            .edit()
            .putString("app_lang", selectedLang)
            .apply()
        val label = if (selectedLang == "HI") "भाषा: हिन्दी (Hindi)" else "Language: English"
        Toast.makeText(ctx, label, Toast.LENGTH_SHORT).show()
        speakText(ctx, true, if (selectedLang == "HI") "हिन्दी भाषा चुनी गई" else "English language selected")
    }

    private fun buildCompactSocialLinksCard(ctx: Context): LinearLayout {
        val linksCard = makeCard(ctx, cardDark, borderSlate)
        linksCard.addView(TextView(ctx).apply {
            text = if (selectedLang == "HI") "📱 सोशल लिंक्स" else "📱 Social Links"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
            setPadding(0, 0, 0, 8)
        })

        val rowA = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        }
        rowA.addView(makeSmallSocialBtn(ctx, "facebook", fbUrl, 6))
        rowA.addView(makeSmallSocialBtn(ctx, "whatsapp", waUrl, 0))
        linksCard.addView(rowA)

        val rowB = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rowB.addView(makeSmallSocialBtn(ctx, "twitter", twUrl, 6))
        rowB.addView(makeSmallSocialBtn(ctx, "youtube", ytUrl, 0))
        linksCard.addView(rowB)

        return linksCard
    }

    private fun makeSmallSocialBtn(ctx: Context, name: String, url: String, rightMarginPx: Int): Button {
        val hasLink = url.isNotBlank()
        return Button(ctx).apply {
            text = if (hasLink) "$name • link" else "$name • not set"
            textSize = 10f
            isAllCaps = false
            setTextColor(if (hasLink) textWhite else textMuted)
            background = make3dPill(cardElevated, borderSlate, 10f)
            setPadding(8, 10, 8, 10)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = rightMarginPx
            }
            setOnClickListener {
                if (hasLink) {
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: Exception) {
                        Toast.makeText(ctx, "Invalid $name link", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(ctx, "$name link not set", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun makeClickableMetricBox(
        ctx: Context,
        title: String,
        valueTv: TextView,
        subLabel: String,
        bgColor: Int,
        strokeColor: Int,
        rightMarginPx: Int,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = make3dPill(bgColor, strokeColor, 16f)
            setPadding(8, 14, 8, 14)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = rightMarginPx
            }
            addView(TextView(ctx).apply {
                text = title
                textSize = 11f
                setTextColor(textWhite)
                gravity = Gravity.CENTER
            })
            addView(valueTv.apply { setPadding(0, 4, 0, 2) })
            addView(TextView(ctx).apply {
                text = subLabel
                textSize = 10f
                setTextColor(accentGold)
                gravity = Gravity.CENTER
            })
            setOnClickListener { onClick() }
        }
    }

    private fun makeMetricCard(
        ctx: Context,
        title: String,
        value: String,
        delta: String,
        bgColor: Int,
        strokeColor: Int,
        rightMarginPx: Int
    ): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = make3dPill(bgColor, strokeColor, 16f)
            setPadding(10, 14, 10, 14)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = rightMarginPx
            }
            addView(TextView(ctx).apply {
                text = title
                textSize = 11f
                setTextColor(textWhite)
                gravity = Gravity.CENTER
            })
            addView(TextView(ctx).apply {
                text = value
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(textWhite)
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 2)
            })
            addView(TextView(ctx).apply {
                text = delta
                textSize = 10f
                setTextColor(accentGold)
                gravity = Gravity.CENTER
            })
        }
    }

    private fun speakText(ctx: Context, enabled: Boolean, message: String) {
        if (!enabled) return
        if (ttsReady && tts != null) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "csp_tts")
        } else {
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveScreenshotToGallery(ctx: Context, targetView: View): Boolean {
        return try {
            val w = targetView.width
            val h = targetView.height
            if (w <= 0 || h <= 0) return false
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            targetView.draw(canvas)
            saveBitmapToGallery(ctx, bmp, "CaptainSmartPro")
        } catch (_: Exception) {
            false
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun makeStyledInput(ctx: Context, defVal: String, hintVal: String): EditText {
        return EditText(ctx).apply {
            setText(defVal)
            hint = hintVal
            setTextColor(textWhite)
            setHintTextColor(textMuted)
            textSize = 14f
            background = make3dPill(cardElevated, borderSlate, 14f)
            setPadding(16, 12, 16, 12)
        }
    }

    private fun makeStyledButton(ctx: Context, label: String, bgColor: Int): Button {
        return Button(ctx).apply {
            text = label
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = make3dPill(bgColor, Color.parseColor("#93C5FD"), 16f)
            setPadding(16, 14, 16, 14)
        }
    }

    private fun makeSwitchRow(ctx: Context, label: String, checked: Boolean): Switch {
        return Switch(ctx).apply {
            text = label
            isChecked = checked
            setTextColor(textWhite)
            textSize = 14f
            setPadding(0, 10, 0, 10)
        }
    }

    private fun makeCard(ctx: Context, fillColor: Int, strokeColor: Int): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = make3dPill(fillColor, strokeColor, 18f)
            setPadding(18, 18, 18, 18)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 14 }
        }
    }

    private fun make3dPill(fillColor: Int, strokeColor: Int, radius: Float): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                lightenColor(fillColor, 0.14f),
                fillColor,
                darkenColor(fillColor, 0.18f)
            )
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setStroke(3, strokeColor)
        }
    }

    private fun lightenColor(color: Int, factor: Float): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) + (255 - Color.green(color)) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) + (255 - Color.blue(color)) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        val r = (Color.red(color) * (1f - factor)).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * (1f - factor)).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * (1f - factor)).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun generateQrMatrix(seedText: String): Bitmap {
        val size = 21
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val seed = seedText.hashCode()
        for (y in 0 until size) {
            for (x in 0 until size) {
                val isFinder = (x < 5 && y < 5) || (x >= size - 5 && y < 5) || (x < 5 && y >= size - 5)
                val isDark = if (isFinder) {
                    x == 0 || x == 4 || y == 0 || y == 4 || (x in 1..3 && y in 1..3)
                } else {
                    (((x * 31 + y * 17) xor seed) and 1) == 0
                }
                bmp.setPixel(x, y, if (isDark) Color.BLACK else Color.WHITE)
            }
        }
        return Bitmap.createScaledBitmap(bmp, 320, 320, false)
    }
}
