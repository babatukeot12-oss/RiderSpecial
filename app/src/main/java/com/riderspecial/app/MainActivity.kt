package com.riderspecial.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import android.view.Gravity
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
import com.google.firebase.database.FirebaseDatabase
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

        val deviceId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown_device"

        loadLocalAndSyncSafe(ctx, deviceId)
    }

    private fun applyTtsLanguage() {
        tts?.language = if (selectedLang == "HI") Locale("hi", "IN") else Locale.US
    }

    private fun loadLocalAndSyncSafe(ctx: Context, deviceId: String) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val nowMillis = System.currentTimeMillis()

        configurableTrialDays = prefs.getLong("trial_days_global", 3L).coerceAtLeast(0L)
        fbUrl = prefs.getString("social_fb", "") ?: ""
        waUrl = prefs.getString("social_wa", "") ?: ""
        twUrl = prefs.getString("social_tw", "") ?: ""
        ytUrl = prefs.getString("social_yt", "") ?: ""
        broadcastMessage = prefs.getString("broadcast_msg", "") ?: ""
        customQrTitle = prefs.getString("custom_qr_title", "Rider Payment QR") ?: "Rider Payment QR"

        var trialStart = prefs.getLong("trial_start_$deviceId", 0L)
        if (trialStart == 0L) {
            trialStart = nowMillis
            prefs.edit().putLong("trial_start_$deviceId", trialStart).apply()
        }
        val activeUntil = prefs.getLong("active_until_$deviceId", 0L)
        val trialEnd = trialStart + TimeUnit.DAYS.toMillis(configurableTrialDays)
        val hasActiveSub = nowMillis < activeUntil
        val inTrial = !hasActiveSub && (nowMillis < trialEnd)

        if (!hasActiveSub && !inTrial) {
            showLockScreen(ctx, deviceId)
        } else {
            showRiderDashboard(
                ctx = ctx,
                deviceId = deviceId,
                nowMillis = nowMillis,
                trialEndMillis = trialEnd,
                activeUntilMillis = activeUntil,
                hasActiveSubscription = hasActiveSub
            )
        }

        try {
            FirebaseDatabase.getInstance().reference
                .child("captain_smart_pro")
                .child("config")
                .get()
                .addOnSuccessListener { snap ->
                    val trialFromDb = snap.child("trialDays").getValue(Long::class.java)
                    if (trialFromDb != null && trialFromDb >= 0L) {
                        configurableTrialDays = trialFromDb
                        prefs.edit().putLong("trial_days_global", configurableTrialDays).apply()
                    }
                    fbUrl = snap.child("facebook").getValue(String::class.java) ?: fbUrl
                    waUrl = snap.child("whatsapp").getValue(String::class.java) ?: waUrl
                    twUrl = snap.child("twitter").getValue(String::class.java) ?: twUrl
                    ytUrl = snap.child("youtube").getValue(String::class.java) ?: ytUrl
                    broadcastMessage = snap.child("broadcast").getValue(String::class.java) ?: broadcastMessage
                    customQrTitle = snap.child("qrTitle").getValue(String::class.java) ?: customQrTitle

                    prefs.edit()
                        .putString("social_fb", fbUrl)
                        .putString("social_wa", waUrl)
                        .putString("social_tw", twUrl)
                        .putString("social_yt", ytUrl)
                        .putString("broadcast_msg", broadcastMessage)
                        .putString("custom_qr_title", customQrTitle)
                        .apply()
                }
        } catch (_: Exception) {
        }
    }

    private fun promptAdminPassport(ctx: Context, currentDeviceId: String) {
        val passEt = EditText(ctx).apply {
            hint = if (selectedLang == "HI") "एडमिन पासवर्ड डालें" else "Enter Admin Passport"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(28, 20, 28, 20)
        }
        AlertDialog.Builder(ctx)
            .setTitle(if (selectedLang == "HI") "एडमिन लॉगिन" else "Admin Login")
            .setView(passEt)
            .setPositiveButton(if (selectedLang == "HI") "खोलें" else "Unlock") { _, _ ->
                if (passEt.text.toString() == adminPassport) {
                    showAdminDashboardScreen(ctx, currentDeviceId)
                } else {
                    Toast.makeText(
                        ctx,
                        if (selectedLang == "HI") "गलत पासवर्ड" else "Invalid Passport",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(if (selectedLang == "HI") "रद्द करें" else "Cancel", null)
            .show()
    }

    private fun showAdminDashboardScreen(ctx: Context, currentDeviceId: String) {
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
            text = "Admin"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = makeRoundedBg(accentBlue, accentBlue, 12f)
            setPadding(12, 8, 12, 8)
            setOnClickListener {
                Toast.makeText(ctx, "Admin Active • ID: $currentDeviceId", Toast.LENGTH_SHORT).show()
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

        val top3Row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
        }
        top3Row.addView(makeMetricCard(ctx, "Total Riders", "1", "+1", Color.parseColor("#132854"), accentBlue, 6))
        top3Row.addView(makeMetricCard(ctx, "Online Riders", "1", "Active", Color.parseColor("#0D3B26"), accentGreen, 6))
        top3Row.addView(makeMetricCard(ctx, "Offline Riders", "0", "0%", Color.parseColor("#3B1219"), accentRed, 0))
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
                Toast.makeText(ctx, "Message saved & broadcast", Toast.LENGTH_SHORT).show()
            }
        })
        msgBtnRow.addView(makeStyledButton(ctx, "All Riders", accentPurple).apply {
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 6
            }
            setOnClickListener {
                Toast.makeText(ctx, "Target: All Riders", Toast.LENGTH_SHORT).show()
            }
        })
        msgCard.addView(msgBtnRow)
        col.addView(msgCard)

        val statusCard = makeCard(ctx, cardDark, borderSlate)
        statusCard.addView(TextView(ctx).apply {
            text = "Rider Status Details"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
            setPadding(0, 0, 0, 8)
        })
        statusCard.addView(TextView(ctx).apply {
            text = "Online Now: 1   |   Offline Now: 0\nOn Ride: 0   |   Available: 1"
            textSize = 13f
            setTextColor(textMuted)
        })
        col.addView(statusCard)

        col.addView(buildCompactSocialLinksCard(ctx))

        adminDotsBtn.setOnClickListener {
            val popup = PopupMenu(ctx, adminDotsBtn)
            popup.menu.add("Subscription Days (+/-)")
            popup.menu.add("Free Trial Days (+/-)")
            popup.menu.add("Live Chat / Call Rider")
            popup.menu.add("QR & Social Links")
            popup.menu.add("Language: Hindi / English")
            popup.menu.add("Back to Rider Panel")
            popup.setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Subscription Days (+/-)" -> openSubscriptionManagerDialog(ctx, currentDeviceId)
                    "Free Trial Days (+/-)" -> openTrialManagerDialog(ctx, currentDeviceId)
                    "Live Chat / Call Rider" -> openDirectChatOrCallDialog(ctx, currentDeviceId)
                    "QR & Social Links" -> openAdminLinksAndQrDialog(ctx)
                    "Language: Hindi / English" -> {
                        toggleAppLanguage(ctx)
                        showAdminDashboardScreen(ctx, currentDeviceId)
                    }
                    "Back to Rider Panel" -> loadLocalAndSyncSafe(ctx, currentDeviceId)
                }
                true
            }
            popup.show()
        }

        scroll.addView(col)
        root.addView(scroll)
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

    // Compact 2x2 small-text social buttons inside a single card above the bottom edge
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

    private fun openSubscriptionManagerDialog(ctx: Context, defaultDevId: String) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 20)
        }
        val devEt = EditText(ctx).apply {
            hint = "Rider Device ID"
            setText(defaultDevId)
        }
        val daysEt = EditText(ctx).apply {
            hint = "Days (e.g. 30, 7, 1, -5)"
            setText("30")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        box.addView(TextView(ctx).apply { text = "Rider Device ID:" })
        box.addView(devEt)
        box.addView(TextView(ctx).apply { text = "Subscription Days:" })
        box.addView(daysEt)

        AlertDialog.Builder(ctx)
            .setTitle("Subscription Manager")
            .setView(box)
            .setPositiveButton("Apply") { _, _ ->
                val targetId = devEt.text.toString().trim()
                val days = daysEt.text.toString().trim().toLongOrNull() ?: 0L
                val newUntil = (System.currentTimeMillis() + TimeUnit.DAYS.toMillis(days)).coerceAtLeast(0L)
                prefs.edit().putLong("active_until_$targetId", newUntil).apply()
                try {
                    FirebaseDatabase.getInstance().reference
                        .child("captain_smart_pro")
                        .child("devices")
                        .child(targetId)
                        .child("activeUntil")
                        .setValue(newUntil)
                } catch (_: Exception) {
                }
                Toast.makeText(ctx, "Updated $days days for $targetId", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openTrialManagerDialog(ctx: Context, defaultDevId: String) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 20)
        }
        val devEt = EditText(ctx).apply {
            hint = "Rider Device ID"
            setText(defaultDevId)
        }
        val trialEt = EditText(ctx).apply {
            hint = "Free Trial Days (e.g. 3, 7, 1)"
            setText(configurableTrialDays.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        box.addView(TextView(ctx).apply { text = "Rider Device ID:" })
        box.addView(devEt)
        box.addView(TextView(ctx).apply { text = "Free Trial Days:" })
        box.addView(trialEt)

        AlertDialog.Builder(ctx)
            .setTitle("Free Trial Manager")
            .setView(box)
            .setPositiveButton("Save Trial") { _, _ ->
                val tDays = trialEt.text.toString().trim().toLongOrNull() ?: 3L
                val targetId = devEt.text.toString().trim()
                configurableTrialDays = tDays.coerceAtLeast(0L)
                prefs.edit().putLong("trial_days_global", configurableTrialDays).apply()
                try {
                    val rootRef = FirebaseDatabase.getInstance().reference.child("captain_smart_pro")
                    rootRef.child("config").child("trialDays").setValue(configurableTrialDays)
                    if (targetId.isNotEmpty()) {
                        rootRef.child("devices").child(targetId).child("customTrialDays").setValue(configurableTrialDays)
                    }
                } catch (_: Exception) {
                }
                Toast.makeText(ctx, "Free trial set to $configurableTrialDays days", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openDirectChatOrCallDialog(ctx: Context, defaultDevId: String) {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 20)
        }
        val devEt = EditText(ctx).apply {
            hint = "Rider Device ID"
            setText(defaultDevId)
        }
        val phoneEt = EditText(ctx).apply {
            hint = "Rider Phone Number"
            inputType = InputType.TYPE_CLASS_PHONE
        }
        val msgEt = EditText(ctx).apply {
            hint = "Direct chat message..."
        }
        val callBtn = Button(ctx).apply {
            text = "Call Rider"
            setOnClickListener {
                val num = phoneEt.text.toString().trim()
                if (num.isEmpty()) Toast.makeText(ctx, "Enter phone number", Toast.LENGTH_SHORT).show()
                else ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")))
            }
        }
        val chatBtn = Button(ctx).apply {
            text = "Send Direct Message"
            setOnClickListener {
                val targetId = devEt.text.toString().trim()
                val m = msgEt.text.toString().trim()
                if (targetId.isEmpty() || m.isEmpty()) {
                    Toast.makeText(ctx, "Enter Device ID and message", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                try {
                    val chatRef = FirebaseDatabase.getInstance().reference
                        .child("captain_smart_pro")
                        .child("chats")
                        .child(targetId)
                    val k = chatRef.push().key ?: return@setOnClickListener
                    chatRef.child(k).setValue(
                        mapOf(
                            "sender" to "Admin",
                            "message" to m,
                            "time" to System.currentTimeMillis()
                        )
                    )
                    msgEt.setText("")
                    Toast.makeText(ctx, "Message sent", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(ctx, "Failed to send message", Toast.LENGTH_SHORT).show()
                }
            }
        }
        box.addView(devEt)
        box.addView(phoneEt)
        box.addView(callBtn)
        box.addView(msgEt)
        box.addView(chatBtn)

        AlertDialog.Builder(ctx)
            .setTitle("Direct Rider Chat & Call")
            .setView(box)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openAdminLinksAndQrDialog(ctx: Context) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val scroll = ScrollView(ctx)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 20)
        }
        val qrEt = EditText(ctx).apply {
            hint = "QR Code Title / Note"
            setText(customQrTitle)
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

        box.addView(qrEt)
        box.addView(fbEt)
        box.addView(waEt)
        box.addView(twEt)
        box.addView(ytEt)
        box.addView(Button(ctx).apply {
            text = "Save QR & Social Links"
            setOnClickListener {
                customQrTitle = qrEt.text.toString().trim().ifEmpty { "Rider Payment QR" }
                fbUrl = fbEt.text.toString().trim()
                waUrl = waEt.text.toString().trim()
                twUrl = twEt.text.toString().trim()
                ytUrl = ytEt.text.toString().trim()

                prefs.edit()
                    .putString("custom_qr_title", customQrTitle)
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
                                "facebook" to fbUrl,
                                "whatsapp" to waUrl,
                                "twitter" to twUrl,
                                "youtube" to ytUrl
                            )
                        )
                } catch (_: Exception) {
                }
                Toast.makeText(ctx, "Saved QR & Links", Toast.LENGTH_SHORT).show()
            }
        })
        scroll.addView(box)

        AlertDialog.Builder(ctx)
            .setTitle("QR & Social Links")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openRiderLiveChatDialog(ctx: Context, deviceId: String) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 20)
        }
        val chatTv = TextView(ctx).apply {
            text = prefs.getString("local_chat_$deviceId", "No messages yet.") ?: "No messages yet."
            textSize = 13f
            setPadding(0, 0, 0, 12)
        }
        val msgEt = EditText(ctx).apply {
            hint = if (selectedLang == "HI") "संदेश लिखें..." else "Type message..."
        }
        val sendBtn = Button(ctx).apply {
            text = if (selectedLang == "HI") "भेजें" else "Send"
            setOnClickListener {
                val txt = msgEt.text.toString().trim()
                if (txt.isEmpty()) return@setOnClickListener
                val updated = (chatTv.text.toString() + "\nRider: $txt").trim()
                chatTv.text = updated
                prefs.edit().putString("local_chat_$deviceId", updated).apply()
                try {
                    val chatRef = FirebaseDatabase.getInstance().reference
                        .child("captain_smart_pro")
                        .child("chats")
                        .child(deviceId)
                    val key = chatRef.push().key
                    if (key != null) {
                        chatRef.child(key).setValue(
                            mapOf(
                                "sender" to "Rider ($deviceId)",
                                "message" to txt,
                                "time" to System.currentTimeMillis()
                            )
                        )
                    }
                } catch (_: Exception) {
                }
                msgEt.setText("")
            }
        }

        box.addView(chatTv)
        box.addView(msgEt)
        box.addView(sendBtn)

        AlertDialog.Builder(ctx)
            .setTitle(if (selectedLang == "HI") "लाइव चैट" else "Live Chat")
            .setView(box)
            .setNegativeButton(if (selectedLang == "HI") "बंद करें" else "Close", null)
            .show()
    }

    private fun openRiderSettingsDialog(
        ctx: Context,
        deviceId: String,
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
            text = "\nDevice ID: $deviceId"
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

    private fun openRiderLinksAndQrDialog(ctx: Context) {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 20)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        box.addView(TextView(ctx).apply {
            text = customQrTitle
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 10)
        })
        box.addView(ImageView(ctx).apply {
            setImageBitmap(generateQrMatrix(customQrTitle))
            layoutParams = LinearLayout.LayoutParams(300, 300).apply { bottomMargin = 12 }
        })
        box.addView(buildCompactSocialLinksCard(ctx))

        AlertDialog.Builder(ctx)
            .setTitle("QR & Links")
            .setView(box)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showLockScreen(ctx: Context, deviceId: String) {
        val scroll = ScrollView(ctx).apply {
            setBackgroundColor(bgDark)
            setPadding(24, 72, 24, 28)
        }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 14)
        }
        topRow.addView(makeStyledButton(ctx, "Admin", accentBlue).apply {
            textSize = 11f
            setOnClickListener { promptAdminPassport(ctx, deviceId) }
        })
        topRow.addView(TextView(ctx).apply {
            text = "  Captain Smart Pro"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentGreenBright)
        })
        col.addView(topRow)

        val card = makeCard(ctx, Color.parseColor("#1D1119"), accentRed)
        card.addView(TextView(ctx).apply {
            text = "Subscription Required"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
        })
        card.addView(TextView(ctx).apply {
            text = "Device ID: $deviceId"
            textSize = 12f
            setTextColor(textMuted)
            setPadding(0, 6, 0, 0)
        })
        col.addView(card)

        val qrCard = makeCard(ctx, cardDark, accentGold).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        qrCard.addView(TextView(ctx).apply {
            text = customQrTitle
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentGold)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 10)
        })
        qrCard.addView(ImageView(ctx).apply {
            setImageBitmap(generateQrMatrix(customQrTitle))
            layoutParams = LinearLayout.LayoutParams(320, 320).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        })
        col.addView(qrCard)

        col.addView(makeStyledButton(ctx, "Live Chat", accentPurple).apply {
            setOnClickListener { openRiderLiveChatDialog(ctx, deviceId) }
        })
        col.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 12) })
        col.addView(buildCompactSocialLinksCard(ctx))

        scroll.addView(col)
        setContentView(scroll)
    }

    private fun showRiderDashboard(
        ctx: Context,
        deviceId: String,
        nowMillis: Long,
        trialEndMillis: Long,
        activeUntilMillis: Long,
        hasActiveSubscription: Boolean
    ) {
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
            setOnClickListener { promptAdminPassport(ctx, deviceId) }
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
            text = "Auto Clicker • Ride Catcher ($selectedLang)"
            textSize = 11f
            setTextColor(textMuted)
        })

        val badgeText = if (hasActiveSubscription) {
            val dLeft = TimeUnit.MILLISECONDS.toDays(activeUntilMillis - nowMillis).coerceAtLeast(0L)
            "30D•${dLeft}d"
        } else {
            val hLeft = TimeUnit.MILLISECONDS.toHours(trialEndMillis - nowMillis).coerceAtLeast(0L)
            "TRIAL•${hLeft}h"
        }

        val badgeTv = TextView(ctx).apply {
            text = badgeText
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = makeRoundedBg(accentBlue, borderSlate, 12f)
            setPadding(10, 8, 10, 8)
        }

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
        topBar.addView(badgeTv)
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
            popup.menu.add("QR & Links")
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
                        ctx, deviceId, pickupEt, dropEt, amountEt,
                        swOnlyOlaRapido, swSoundTts, swAutoAccept, swScreenshot, savePrefsAction
                    )
                    "Language: Hindi / English" -> {
                        toggleAppLanguage(ctx)
                        loadLocalAndSyncSafe(ctx, deviceId)
                    }
                    "Live Chat" -> openRiderLiveChatDialog(ctx, deviceId)
                    "QR & Links" -> openRiderLinksAndQrDialog(ctx)
                    "Admin Panel" -> promptAdminPassport(ctx, deviceId)
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
