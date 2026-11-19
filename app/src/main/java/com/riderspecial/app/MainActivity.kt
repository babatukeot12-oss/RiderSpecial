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
    private var configurableTrialDays = 3L

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

    private val bgDark = Color.parseColor("#070C18")
    private val headerDark = Color.parseColor("#0B1324")
    private val cardDark = Color.parseColor("#111B31")
    private val cardElevated = Color.parseColor("#182644")
    private val borderSlate = Color.parseColor("#23324D")
    private val accentGreen = Color.parseColor("#16A34A")
    private val accentGreenBright = Color.parseColor("#22C55E")
    private val accentBlue = Color.parseColor("#2563EB")
    private val accentPurple = Color.parseColor("#7C3AED")
    private val accentRed = Color.parseColor("#DC2626")
    private val accentGold = Color.parseColor("#FACC15")
    private val accentTeal = Color.parseColor("#0284C7")
    private val textWhite = Color.parseColor("#F8FAFC")
    private val textMuted = Color.parseColor("#94A3B8")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ctx: Context = this
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
                    val scaled = Bitmap.createScaledBitmap(original, 220, 220, true)
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
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

    private fun loadAndStart(ctx: Context, hardwareId: String) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val nowMillis = System.currentTimeMillis()

        configurableTrialDays = prefs.getLong("trial_days_global", 3L).coerceAtLeast(0L)
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
                fbUrl = snap.child("facebook").getValue(String::class.java) ?: fbUrl
                waUrl = snap.child("whatsapp").getValue(String::class.java) ?: waUrl
                twUrl = snap.child("twitter").getValue(String::class.java) ?: twUrl
                ytUrl = snap.child("youtube").getValue(String::class.java) ?: ytUrl
                broadcastMessage = snap.child("broadcast").getValue(String::class.java) ?: broadcastMessage
                customQrTitle = snap.child("qrTitle").getValue(String::class.java) ?: customQrTitle
                customQrBase64 = snap.child("qrBase64").getValue(String::class.java) ?: customQrBase64

                prefs.edit()
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
            setPadding(22, 72, 22, 28)
        }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 14)
        }
        topRow.addView(makeStyledButton(ctx, "ADMIN", accentBlue).apply {
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
            text = "Rider Details (Name & Mobile ID)"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
            setPadding(0, 0, 0, 10)
        })

        val nameEt = makeStyledInput(ctx, existingName, "Full Name")
        val mobileEt = makeStyledInput(ctx, existingMobile, "Mobile Number (Rider ID)")
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

        val pickPhotoBtn = makeStyledButton(ctx, "Choose Rider Photo", cardElevated).apply {
            textSize = 12f
            setOnClickListener {
                pickPhotoFromGallery(ctx) { b64 ->
                    photoB64 = b64
                    decodeBase64Bitmap(b64)?.let { photoPreview.setImageBitmap(it) }
                    Toast.makeText(ctx, "Photo selected", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val saveBtn = makeStyledButton(ctx, "Continue to App", accentGreen).apply {
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
            .setTitle("Admin Login")
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
            text = "ADMIN"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = makeRoundedBg(accentBlue, accentBlue, 12f)
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
            background = makeRoundedBg(cardElevated, borderSlate, 12f)
            setPadding(14, 6, 14, 6)
        }

        topBar.addView(adminLeftBtn)
        topBar.addView(brandCol)
        topBar.addView(adminDotsBtn)
        root.addView(topBar)

        val scroll = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            setPadding(16, 14, 16, 20)
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

        val paymentAlertCard = makeCard(ctx, Color.parseColor("#1D190B"), accentGold)
        val paymentAlertTv = TextView(ctx).apply {
            text = "Payment Alerts: Checking..."
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

        val totalCard = makeClickableMetricBox(ctx, "Total Riders", totalRidersTv, "Tap List", Color.parseColor("#132854"), accentBlue, 6) {
            openRidersListDialog(ctx, onlyOnline = false)
        }
        val onlineCard = makeClickableMetricBox(ctx, "Online Riders", onlineRidersTv, "Tap Online", Color.parseColor("#0D3B26"), accentGreen, 6) {
            openRidersListDialog(ctx, onlyOnline = true)
        }
        val offlineCard = makeClickableMetricBox(ctx, "Offline Riders", offlineRidersTv, "Tap All", Color.parseColor("#3B1219"), accentRed, 0) {
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
        mid2Row.addView(makeMetricCard(ctx, "Total Earning", "₹$totalEarnings", "Live", Color.parseColor("#2E1654"), accentPurple, 6))
        mid2Row.addView(makeMetricCard(ctx, "Total Rides", "$totalRides", "Trips", Color.parseColor("#0D2E47"), accentTeal, 0))
        col.addView(mid2Row)

        val summaryCard = makeCard(ctx, cardDark, borderSlate)
        summaryCard.addView(TextView(ctx).apply {
            text = "Today's Summary"
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
            text = "Send Message to All Riders"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
        })
        val broadcastInputEt = makeStyledInput(ctx, broadcastMessage, "Type your message here...")
        msgCard.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 8) })
        msgCard.addView(broadcastInputEt)
        msgCard.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 10) })

        val msgBtnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        msgBtnRow.addView(makeStyledButton(ctx, "Send Message", accentBlue).apply {
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
        msgBtnRow.addView(makeStyledButton(ctx, "All Riders", accentPurple).apply {
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
                                pendingList.add("$rName ($rId)")
                            }
                        }
                        paymentAlertTv.text = if (pendingList.isEmpty()) {
                            "Payment Alerts: No pending payment requests"
                        } else {
                            "Payment Alert (${pendingList.size}): ${pendingList.joinToString(", ")} — Open ⋮ > Approve Subscription"
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}
                })
        } catch (_: Exception) {
            paymentAlertTv.text = "Payment Alerts: Local mode active"
        }

        adminDotsBtn.setOnClickListener {
            val popup = PopupMenu(ctx, adminDotsBtn)
            popup.menu.add("Approve / Adjust Subscription (₹49 / 30d)")
            popup.menu.add("Free Trial Days (+/-)")
            popup.menu.add("Rider Chat & Photos / Call")
            popup.menu.add("Set QR Code & Social Links")
            popup.menu.add("Language: Hindi / English")
            popup.menu.add("Back to Rider Panel")
            popup.setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Approve / Adjust Subscription (₹49 / 30d)" -> openSubscriptionManagerDialog(ctx, defaultRiderId)
                    "Free Trial Days (+/-)" -> openTrialManagerDialog(ctx, defaultRiderId)
                    "Rider Chat & Photos / Call" -> openAdminRiderChatDialog(ctx, defaultRiderId)
                    "Set QR Code & Social Links" -> openAdminQrAndLinksDialog(ctx)
                    "Language: Hindi / English" -> {
                        toggleAppLanguage(ctx)
                        showAdminDashboardScreen(ctx, defaultRiderId)
                    }
                    "Back to Rider Panel" -> {
                        val hw = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
                        loadAndStart(ctx, hw)
                    }
                }
                true
            }
            popup.show()
        }

        scroll.addView(col)
        root.addView(scroll)
        setContentView(root)
    }

    private fun openRidersListDialog(ctx: Context, onlyOnline: Boolean) {
        val scroll = ScrollView(ctx)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }
        val infoTv = TextView(ctx).apply {
            text = "Loading riders..."
            textSize = 13f
            setPadding(0, 0, 0, 12)
        }
        box.addView(infoTv)
        scroll.addView(box)

        AlertDialog.Builder(ctx)
            .setTitle(if (onlyOnline) "Online Riders" else "Total Riders")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .show()

        try {
            val now = System.currentTimeMillis()
            FirebaseDatabase.getInstance().reference.child("captain_smart_pro").child("riders")
                .get()
                .addOnSuccessListener { snap ->
                    box.removeAllViews()
                    var count = 0
                    for (child in snap.children) {
                        val rId = child.child("riderId").getValue(String::class.java) ?: (child.key ?: "")
                        val name = child.child("fullName").getValue(String::class.java) ?: "Unknown"
                        val mobile = child.child("mobile").getValue(String::class.java) ?: rId
                        val photoB64 = child.child("photoB64").getValue(String::class.java) ?: ""
                        val lastSeen = child.child("lastSeen").getValue(Long::class.java) ?: 0L
                        val isOnline = (child.child("online").getValue(Boolean::class.java) ?: false) &&
                            ((now - lastSeen) < TimeUnit.MINUTES.toMillis(15))

                        if (onlyOnline && !isOnline) continue
                        count++

                        val row = LinearLayout(ctx).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(0, 8, 0, 8)
                        }
                        val photoView = ImageView(ctx).apply {
                            layoutParams = LinearLayout.LayoutParams(96, 96).apply { rightMargin = 12 }
                            val bmp = decodeBase64Bitmap(photoB64)
                            if (bmp != null) setImageBitmap(bmp)
                        }
                        val detailsTv = TextView(ctx).apply {
                            text = "$name\nID / Mobile: $mobile\nStatus: ${if (isOnline) "ONLINE" else "OFFLINE"}"
                            textSize = 12f
                        }
                        row.addView(photoView)
                        row.addView(detailsTv)
                        box.addView(row)
                    }
                    if (count == 0) {
                        box.addView(TextView(ctx).apply {
                            text = if (onlyOnline) "No riders online right now." else "No riders registered yet."
                        })
                    }
                }
        } catch (_: Exception) {
            infoTv.text = "Unable to load riders list."
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
            hint = "Days (e.g. 30, 7, 1, -5)"
            setText("30")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        box.addView(TextView(ctx).apply { text = "Rider ID (Mobile Number):" })
        box.addView(riderIdEt)
        box.addView(TextView(ctx).apply { text = "Subscription Days (Activate / Free Days / Reduce):" })
        box.addView(daysEt)

        AlertDialog.Builder(ctx)
            .setTitle("Subscription Manager (₹49 / 30 Days)")
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
            .setTitle("Free Trial Manager")
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
        val callBtn = Button(ctx).apply { text = "Call Rider" }

        fun loadChatFor(targetId: String) {
            if (targetId.isBlank()) return
            try {
                FirebaseDatabase.getInstance().reference
                    .child("captain_smart_pro")
                    .child("chats")
                    .child(targetId)
                    .get()
                    .addOnSuccessListener { snap ->
                        messagesBox.removeAllViews()
                        for (item in snap.children) {
                            val sender = item.child("sender").getValue(String::class.java) ?: "User"
                            val text = item.child("message").getValue(String::class.java) ?: ""
                            val photoB64 = item.child("photoB64").getValue(String::class.java) ?: ""
                            if (text.isNotBlank()) {
                                messagesBox.addView(TextView(ctx).apply {
                                    text = "$sender: $text"
                                    textSize = 12f
                                })
                            }
                            val bmp = decodeBase64Bitmap(photoB64)
                            if (bmp != null) {
                                messagesBox.addView(ImageView(ctx).apply {
                                    setImageBitmap(bmp)
                                    layoutParams = LinearLayout.LayoutParams(220, 220).apply {
                                        topMargin = 4
                                        bottomMargin = 8
                                    }
                                })
                            }
                        }
                    }
            } catch (_: Exception) {
            }
        }

        loadChatFor(defaultRiderId)

        callBtn.setOnClickListener {
            val num = riderIdEt.text.toString().trim()
            if (num.isEmpty()) Toast.makeText(ctx, "Enter Rider Mobile ID", Toast.LENGTH_SHORT).show()
            else ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")))
        }

        sendBtn.setOnClickListener {
            val targetId = riderIdEt.text.toString().trim()
            val txt = msgEt.text.toString().trim()
            if (targetId.isEmpty() || txt.isEmpty()) return@setOnClickListener
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
                loadChatFor(targetId)
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
            .setTitle("Rider Chat & Photos")
            .setView(scroll)
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
            layoutParams = LinearLayout.LayoutParams(220, 220).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 8
                bottomMargin = 8
            }
            val bmp = decodeBase64Bitmap(tempQrB64)
            if (bmp != null) setImageBitmap(bmp)
        }
        val pickQrBtn = Button(ctx).apply {
            text = "Select My QR Code Image"
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
            text = "Save QR & Social Links"
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
            .setTitle("QR Code & Social Links")
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
            layoutParams = LinearLayout.LayoutParams(160, 160).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 6
                bottomMargin = 6
            }
        }
        val pickPhotoBtn = Button(ctx).apply {
            text = "Attach Photo"
            setOnClickListener {
                pickPhotoFromGallery(ctx) { b64 ->
                    attachPhotoB64 = b64
                    decodeBase64Bitmap(b64)?.let { photoPreview.setImageBitmap(it) }
                    Toast.makeText(ctx, "Photo attached", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val sendBtn = Button(ctx).apply {
            text = if (selectedLang == "HI") "भेजें" else "Send"
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
            .setTitle(if (selectedLang == "HI") "लाइव चैट" else "Live Chat")
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
            hint = "Pickup Distance (Km)"
            setText(pickupEt.text.toString())
        }
        val dEt = EditText(ctx).apply {
            hint = "Drop Distance (Km)"
            setText(dropEt.text.toString())
        }
        val aEt = EditText(ctx).apply {
            hint = "Ride Amount / Fare (₹)"
            setText(amountEt.text.toString())
        }
        val s1 = Switch(ctx).apply {
            text = "Only Ola/Rapido"
            isChecked = swOnlyOlaRapido.isChecked
        }
        val s2 = Switch(ctx).apply {
            text = "Play Sound / TTS"
            isChecked = swSoundTts.isChecked
        }
        val s3 = Switch(ctx).apply {
            text = "Auto Accept"
            isChecked = swAutoAccept.isChecked
        }
        val s4 = Switch(ctx).apply {
            text = "Screenshot Save to Gallery"
            isChecked = swScreenshot.isChecked
        }

        box.addView(TextView(ctx).apply { text = "Pickup (Km):" })
        box.addView(pEt)
        box.addView(TextView(ctx).apply { text = "Drop (Km):" })
        box.addView(dEt)
        box.addView(TextView(ctx).apply { text = "Ride Amount (₹):" })
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
            .setTitle("Settings")
            .setView(scroll)
            .setPositiveButton("Save") { _, _ ->
                pickupEt.setText(pEt.text.toString().trim())
                dropEt.setText(dEt.text.toString().trim())
                amountEt.setText(aEt.text.toString().trim())
                swOnlyOlaRapido.isChecked = s1.isChecked
                swSoundTts.isChecked = s2.isChecked
                swAutoAccept.isChecked = s3.isChecked
                swScreenshot.isChecked = s4.isChecked
                onSaved()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openRiderLinksDialog(ctx: Context) {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }
        box.addView(buildCompactSocialLinksCard(ctx))
        AlertDialog.Builder(ctx)
            .setTitle("Social Links")
            .setView(box)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showPaymentLockScreen(ctx: Context, riderId: String, riderName: String) {
        val scroll = ScrollView(ctx).apply {
            setBackgroundColor(bgDark)
            setPadding(22, 72, 22, 28)
        }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 14)
        }
        topRow.addView(makeStyledButton(ctx, "ADMIN", accentBlue).apply {
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

        col.addView(makeStyledButton(ctx, "I Have Paid • Notify Admin", accentGreen).apply {
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
                                "status" to "PENDING_APPROVAL",
                                "time" to System.currentTimeMillis()
                            )
                        )
                } catch (_: Exception) {
                }
                Toast.makeText(ctx, "Payment notification sent to Admin", Toast.LENGTH_LONG).show()
            }
        })
        col.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 12) })
        col.addView(makeStyledButton(ctx, "Live Chat", accentPurple).apply {
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
        val dropDef = prefs.getString("drop_km", "4.5") ?: "4.5"
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
            text = "ADMIN"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = makeRoundedBg(cardElevated, borderSlate, 12f)
            setPadding(12, 8, 12, 8)
            setOnClickListener { promptAdminPassport(ctx, riderId) }
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
            text = "$riderName • $riderId"
            textSize = 11f
            setTextColor(textMuted)
        })

        val riderDotsBtn = Button(ctx).apply {
            text = "⋮"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
            background = makeRoundedBg(cardElevated, borderSlate, 12f)
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

        val homeScroll = ScrollView(ctx).apply { setPadding(18, 14, 18, 24) }
        val homeLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        if (broadcastMessage.isNotBlank()) {
            val noticeCard = makeCard(ctx, cardElevated, accentGold)
            noticeCard.addView(TextView(ctx).apply {
                text = "Notice: $broadcastMessage"
                textSize = 12f
                setTextColor(accentGold)
            })
            homeLayout.addView(noticeCard)
        }

        val fleetCard = makeCard(ctx, cardDark, borderSlate)
        val fleetRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val fleetLeft = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        fleetLeft.addView(TextView(ctx).apply {
            text = "Rapido / Ola / Uber"
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
            text = "Standby (Open ⋮ to Activate)"
            textSize = 12f
            setTextColor(Color.parseColor("#F87171"))
        }
        fleetLeft.addView(fleetStateTv)

        val swOnlyOlaRapido = makeSwitchRow(ctx, "Only Ola/Rapido", true)
        val swSoundTts = makeSwitchRow(ctx, "Play Sound / TTS", true)
        val swAutoAccept = makeSwitchRow(ctx, "Auto Accept", true)
        val swScreenshot = makeSwitchRow(ctx, "Screenshot Save to Gallery", true)

        val pickupEt = makeStyledInput(ctx, pickupDef, "Pickup Km")
        val dropEt = makeStyledInput(ctx, dropDef, "Drop Km")
        val amountEt = makeStyledInput(ctx, fareDef, "Amount ₹")

        val statsSummaryTv = TextView(ctx).apply {
            text = "Rides: $totalRides | Earnings: ₹$totalEarnings | Distance: ${String.format(Locale.US, "%.1f", totalDistance)} Km"
            textSize = 12f
            setTextColor(accentGreenBright)
            setPadding(0, 6, 0, 0)
        }

        val savePrefsAction: () -> Unit = {
            prefs.edit()
                .putString("pickup_km", pickupEt.text.toString().trim())
                .putString("drop_km", dropEt.text.toString().trim())
                .putString("fare_rs", amountEt.text.toString().trim())
                .apply()
            speakText(
                ctx,
                swSoundTts.isChecked,
                if (selectedLang == "HI") "सेटिंग्स सेव हो गई" else "Rider preferences saved"
            )
            Toast.makeText(ctx, "Preferences Saved", Toast.LENGTH_SHORT).show()
        }

        val autoAcceptBtn = makeStyledButton(ctx, "AUTO ACCEPT\nOFF", Color.parseColor("#2A3954")).apply {
            setOnClickListener {
                if (!isEngineActivated) {
                    Toast.makeText(ctx, "Open ⋮ on top to Activate first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                isAutoAcceptEnabled = !isAutoAcceptEnabled
                if (isAutoAcceptEnabled) {
                    text = "AUTO ACCEPT\nON"
                    background = makeRoundedBg(accentGreen, accentGreenBright, 14f)
                    fleetStateTv.text = "Active • 0.00s Auto Accept ON"
                    fleetStateTv.setTextColor(accentGreenBright)
                    speakText(ctx, swSoundTts.isChecked, "Auto Accept On")
                } else {
                    text = "AUTO ACCEPT\nOFF"
                    background = makeRoundedBg(Color.parseColor("#2A3954"), borderSlate, 14f)
                    fleetStateTv.text = "Activated • Auto Accept OFF"
                    fleetStateTv.setTextColor(accentGold)
                    speakText(ctx, swSoundTts.isChecked, "Auto Accept Off")
                }
            }
        }

        riderDotsBtn.setOnClickListener {
            val popup = PopupMenu(ctx, riderDotsBtn)
            popup.menu.add(if (isEngineActivated) "Deactivate Engine" else "Activate Engine")
            popup.menu.add("Settings")
            popup.menu.add("Language: Hindi / English")
            popup.menu.add("Live Chat")
            popup.menu.add("Social Links")
            popup.menu.add("Admin Panel")
            popup.setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Activate Engine", "Deactivate Engine" -> {
                        isEngineActivated = !isEngineActivated
                        if (isEngineActivated) {
                            fleetStateTv.text = "Activated • Ready for Auto Accept"
                            fleetStateTv.setTextColor(accentGold)
                            speakText(ctx, swSoundTts.isChecked, "Activated")
                            Toast.makeText(ctx, "Engine Activated", Toast.LENGTH_SHORT).show()
                        } else {
                            isAutoAcceptEnabled = false
                            autoAcceptBtn.text = "AUTO ACCEPT\nOFF"
                            autoAcceptBtn.background = makeRoundedBg(Color.parseColor("#2A3954"), borderSlate, 14f)
                            fleetStateTv.text = "Standby (Deactivated)"
                            fleetStateTv.setTextColor(Color.parseColor("#F87171"))
                            speakText(ctx, swSoundTts.isChecked, "Deactivated")
                            Toast.makeText(ctx, "Engine Deactivated", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "Settings" -> openRiderSettingsDialog(
                        ctx, riderId, pickupEt, dropEt, amountEt,
                        swOnlyOlaRapido, swSoundTts, swAutoAccept, swScreenshot, savePrefsAction
                    )
                    "Language: Hindi / English" -> {
                        toggleAppLanguage(ctx)
                        showRiderDashboard(ctx, riderId, riderName)
                    }
                    "Live Chat" -> openRiderLiveChatDialog(ctx, riderId, riderName)
                    "Social Links" -> openRiderLinksDialog(ctx)
                    "Admin Panel" -> promptAdminPassport(ctx, riderId)
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
        row1.addView(makeStyledButton(ctx, "TEST RIDE ACCEPT & SAVE", accentBlue).apply {
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 6
            }
            setOnClickListener {
                val pKm = pickupEt.text.toString().trim().ifEmpty { "1.0" }
                val dKm = dropEt.text.toString().trim().ifEmpty { "4.5" }
                val rs = amountEt.text.toString().trim().toIntOrNull() ?: 20
                val addKm = (pKm.toFloatOrNull() ?: 1.0f) + (dKm.toFloatOrNull() ?: 4.5f)
                totalRides += 1
                totalEarnings += rs
                totalDistance += addKm
                prefs.edit()
                    .putInt("total_rides", totalRides)
                    .putInt("total_earnings", totalEarnings)
                    .putFloat("total_distance", totalDistance)
                    .apply()
                statsSummaryTv.text =
                    "Rides: $totalRides | Earnings: ₹$totalEarnings | Distance: ${String.format(Locale.US, "%.1f", totalDistance)} Km"
                val saved = if (swScreenshot.isChecked) saveScreenshotToGallery(ctx, root) else false
                speakText(
                    ctx,
                    swSoundTts.isChecked,
                    if (selectedLang == "HI")
                        "$rs रुपये की राइड एक्सेप्ट हुई, पिकअप $pKm किलोमीटर, ड्रॉप $dKm किलोमीटर"
                    else
                        "Ride accepted for $rs rupees, pickup $pKm kilometer, drop $dKm kilometer"
                )
                Toast.makeText(
                    ctx,
                    if (saved) "Ride Accepted & Screenshot Saved" else "Ride Accepted",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
        row1.addView(makeStyledButton(ctx, "ORDER SPLIT FORM", accentPurple).apply {
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
            text = "Rider Ride Rules (Pickup • Drop • Amount)"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
            setPadding(0, 0, 0, 10)
        })

        rulesCard.addView(TextView(ctx).apply {
            text = "Pickup Distance (Km)"
            textSize = 12f
            setTextColor(textMuted)
        })
        rulesCard.addView(pickupEt)
        rulesCard.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 8) })

        rulesCard.addView(TextView(ctx).apply {
            text = "Drop Distance (Km)"
            textSize = 12f
            setTextColor(textMuted)
        })
        rulesCard.addView(dropEt)
        rulesCard.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 8) })

        rulesCard.addView(TextView(ctx).apply {
            text = "Ride Amount / Fare (₹)"
            textSize = 12f
            setTextColor(textMuted)
        })
        rulesCard.addView(amountEt)

        rulesCard.addView(swOnlyOlaRapido)
        rulesCard.addView(swSoundTts)
        rulesCard.addView(swAutoAccept)
        rulesCard.addView(swScreenshot)

        rulesCard.addView(makeStyledButton(ctx, "SAVE RIDER PREFERENCES", accentGreen).apply {
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
            text = "Social Links"
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
            background = makeRoundedBg(cardElevated, borderSlate, 10f)
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
            background = makeRoundedBg(bgColor, strokeColor, 16f)
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
            background = makeRoundedBg(bgColor, strokeColor, 16f)
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

            val fileName = "CaptainSmartPro_${System.currentTimeMillis()}.png"
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
            background = makeRoundedBg(cardElevated, borderSlate, 14f)
            setPadding(16, 12, 16, 12)
        }
    }

    private fun makeStyledButton(ctx: Context, label: String, bgColor: Int): Button {
        return Button(ctx).apply {
            text = label
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = makeRoundedBg(bgColor, bgColor, 16f)
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
            background = makeRoundedBg(fillColor, strokeColor, 18f)
            setPadding(18, 18, 18, 18)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 14 }
        }
    }

    private fun makeRoundedBg(fillColor: Int, strokeColor: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fillColor)
            setStroke(2, strokeColor)
        }
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
