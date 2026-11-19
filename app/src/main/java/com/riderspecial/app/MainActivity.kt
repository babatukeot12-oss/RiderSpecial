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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {

    private val adminPassport = "dinakeot1990"
    private var configurableTrialDays = 3L

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var fbUrl = ""
    private var twUrl = ""
    private var ytUrl = ""
    private var waUrl = ""
    private var otherUrl = ""
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
    private val textWhite = Color.parseColor("#F8FAFC")
    private val textMuted = Color.parseColor("#94A3B8")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ctx: Context = this
        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
                ttsReady = true
            }
        }

        val deviceId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown_device"

        loadStateAndSync(ctx, deviceId)
    }

    private fun loadStateAndSync(ctx: Context, deviceId: String) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val nowMillis = System.currentTimeMillis()

        configurableTrialDays = prefs.getLong("trial_days_global", 3L).coerceAtLeast(0L)
        fbUrl = prefs.getString("social_fb", "") ?: ""
        twUrl = prefs.getString("social_tw", "") ?: ""
        ytUrl = prefs.getString("social_yt", "") ?: ""
        waUrl = prefs.getString("social_wa", "") ?: ""
        otherUrl = prefs.getString("social_other", "") ?: ""
        broadcastMessage = prefs.getString("broadcast_msg", "") ?: ""
        customQrTitle = prefs.getString("custom_qr_title", "Rider Payment QR") ?: "Rider Payment QR"

        var localTrialStart = prefs.getLong("trial_start_$deviceId", 0L)
        if (localTrialStart == 0L) {
            localTrialStart = nowMillis
            prefs.edit().putLong("trial_start_$deviceId", localTrialStart).apply()
        }
        val localActiveUntil = prefs.getLong("active_until_$deviceId", 0L)

        renderRiderView(ctx, deviceId, nowMillis, localTrialStart, localActiveUntil)

        try {
            val rootRef = FirebaseDatabase.getInstance().reference.child("captain_smart_pro")

            rootRef.child("config").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val trialFromDb = snap.child("trialDays").getValue(Long::class.java)
                    if (trialFromDb != null && trialFromDb >= 0L) {
                        configurableTrialDays = trialFromDb
                        prefs.edit().putLong("trial_days_global", configurableTrialDays).apply()
                    }
                    fbUrl = snap.child("facebook").getValue(String::class.java) ?: fbUrl
                    twUrl = snap.child("twitter").getValue(String::class.java) ?: twUrl
                    ytUrl = snap.child("youtube").getValue(String::class.java) ?: ytUrl
                    waUrl = snap.child("whatsapp").getValue(String::class.java) ?: waUrl
                    otherUrl = snap.child("others").getValue(String::class.java) ?: otherUrl
                    broadcastMessage = snap.child("broadcast").getValue(String::class.java) ?: broadcastMessage
                    customQrTitle = snap.child("qrTitle").getValue(String::class.java) ?: customQrTitle

                    prefs.edit()
                        .putString("social_fb", fbUrl)
                        .putString("social_tw", twUrl)
                        .putString("social_yt", ytUrl)
                        .putString("social_wa", waUrl)
                        .putString("social_other", otherUrl)
                        .putString("broadcast_msg", broadcastMessage)
                        .putString("custom_qr_title", customQrTitle)
                        .apply()
                }

                override fun onCancelled(error: DatabaseError) {}
            })

            val devRef = rootRef.child("devices").child(deviceId)
            devRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    val now = System.currentTimeMillis()
                    var tStart = snap.child("trialStart").getValue(Long::class.java) ?: 0L
                    val aUntil = snap.child("activeUntil").getValue(Long::class.java) ?: 0L
                    val customDays = snap.child("customTrialDays").getValue(Long::class.java)

                    if (tStart == 0L) {
                        tStart = prefs.getLong("trial_start_$deviceId", now)
                        devRef.updateChildren(
                            mapOf(
                                "deviceId" to deviceId,
                                "trialStart" to tStart,
                                "activeUntil" to aUntil,
                                "lastSeen" to now
                            )
                        )
                    } else {
                        devRef.child("lastSeen").setValue(now)
                    }

                    prefs.edit()
                        .putLong("trial_start_$deviceId", tStart)
                        .putLong("active_until_$deviceId", aUntil)
                        .apply()

                    val effectiveTrialDays = (customDays ?: configurableTrialDays).coerceAtLeast(0L)
                    val trialEnd = tStart + TimeUnit.DAYS.toMillis(effectiveTrialDays)
                    val hasActiveSub = now < aUntil
                    val inTrial = !hasActiveSub && (now < trialEnd)

                    if (!hasActiveSub && !inTrial) {
                        showLockScreen(ctx, deviceId)
                    } else {
                        showDashboard(
                            ctx = ctx,
                            deviceId = deviceId,
                            nowMillis = now,
                            trialEndMillis = trialEnd,
                            activeUntilMillis = aUntil,
                            hasActiveSubscription = hasActiveSub
                        )
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
        } catch (_: Exception) {
        }
    }

    private fun renderRiderView(
        ctx: Context,
        deviceId: String,
        nowMillis: Long,
        trialStartMillis: Long,
        activeUntilMillis: Long
    ) {
        val trialEndMillis = trialStartMillis + TimeUnit.DAYS.toMillis(configurableTrialDays)
        val hasActiveSub = nowMillis < activeUntilMillis
        val inTrial = !hasActiveSub && (nowMillis < trialEndMillis)

        if (!hasActiveSub && !inTrial) {
            showLockScreen(ctx, deviceId)
        } else {
            showDashboard(
                ctx = ctx,
                deviceId = deviceId,
                nowMillis = nowMillis,
                trialEndMillis = trialEndMillis,
                activeUntilMillis = activeUntilMillis,
                hasActiveSubscription = hasActiveSub
            )
        }
    }

    private fun openHiddenAdminPassportDialog(ctx: Context, currentDeviceId: String) {
        val passEt = EditText(ctx).apply {
            hint = "Admin Passport"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(32, 24, 32, 24)
        }
        AlertDialog.Builder(ctx)
            .setTitle("Admin Access")
            .setView(passEt)
            .setPositiveButton("Open") { _, _ ->
                if (passEt.text.toString() == adminPassport) {
                    openAdminPanelWithThreeDots(ctx, currentDeviceId)
                } else {
                    Toast.makeText(ctx, "Invalid Passport", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAdminPanelWithThreeDots(ctx: Context, defaultDevId: String) {
        val mainContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }

        val adminTopRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12)
        }
        val titleTv = TextView(ctx).apply {
            text = "👑 Admin Command Center"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val adminDotsBtn = Button(ctx).apply {
            text = "⋮"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
        }
        adminTopRow.addView(titleTv)
        adminTopRow.addView(adminDotsBtn)
        mainContainer.addView(adminTopRow)

        val scroll = ScrollView(ctx)
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        val devIdEt = EditText(ctx).apply {
            hint = "Rider Device ID"
            setText(defaultDevId)
        }
        val phoneEt = EditText(ctx).apply {
            hint = "Rider Phone Number (for Call)"
            inputType = InputType.TYPE_CLASS_PHONE
        }
        val subDaysEt = EditText(ctx).apply {
            hint = "Subscription Days (e.g. 30, 15, -10)"
            setText("30")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val trialDaysEt = EditText(ctx).apply {
            hint = "Free Trial Days (e.g. 3, 7, 1)"
            setText(configurableTrialDays.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val broadcastEt = EditText(ctx).apply {
            hint = "Message to Show All Riders"
            setText(broadcastMessage)
        }
        val directMsgEt = EditText(ctx).apply {
            hint = "Direct Message to Selected Rider"
        }
        val fbEt = EditText(ctx).apply {
            hint = "Facebook Link"
            setText(fbUrl)
        }
        val twEt = EditText(ctx).apply {
            hint = "Twitter Link"
            setText(twUrl)
        }
        val ytEt = EditText(ctx).apply {
            hint = "YouTube Link"
            setText(ytUrl)
        }
        val waEt = EditText(ctx).apply {
            hint = "WhatsApp Link"
            setText(waUrl)
        }
        val otherEt = EditText(ctx).apply {
            hint = "Others Link"
            setText(otherUrl)
        }
        val qrEt = EditText(ctx).apply {
            hint = "QR Code Title / Label"
            setText(customQrTitle)
        }

        val applySubBtn = Button(ctx).apply {
            text = "Apply / Adjust Subscription Days"
            setOnClickListener {
                val targetId = devIdEt.text.toString().trim()
                val days = subDaysEt.text.toString().trim().toLongOrNull() ?: 0L
                if (targetId.isEmpty()) {
                    Toast.makeText(ctx, "Enter Rider Device ID", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val newUntil = (System.currentTimeMillis() + TimeUnit.DAYS.toMillis(days)).coerceAtLeast(0L)
                try {
                    FirebaseDatabase.getInstance().reference
                        .child("captain_smart_pro")
                        .child("devices")
                        .child(targetId)
                        .updateChildren(
                            mapOf(
                                "deviceId" to targetId,
                                "activeUntil" to newUntil,
                                "updatedAt" to System.currentTimeMillis()
                            )
                        )
                    Toast.makeText(ctx, "Subscription updated ($days days) for $targetId", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(ctx, "Update failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val applyTrialBtn = Button(ctx).apply {
            text = "Increase / Decrease Free Trial Days"
            setOnClickListener {
                val tDays = trialDaysEt.text.toString().trim().toLongOrNull() ?: 3L
                val targetId = devIdEt.text.toString().trim()
                configurableTrialDays = tDays.coerceAtLeast(0L)
                try {
                    val root = FirebaseDatabase.getInstance().reference.child("captain_smart_pro")
                    root.child("config").child("trialDays").setValue(configurableTrialDays)
                    if (targetId.isNotEmpty()) {
                        root.child("devices").child(targetId).child("customTrialDays").setValue(configurableTrialDays)
                    }
                    Toast.makeText(ctx, "Free trial set to $configurableTrialDays days", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(ctx, "Trial update failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val sendBroadcastBtn = Button(ctx).apply {
            text = "Send Message to All Riders"
            setOnClickListener {
                val bMsg = broadcastEt.text.toString().trim()
                broadcastMessage = bMsg
                try {
                    FirebaseDatabase.getInstance().reference
                        .child("captain_smart_pro")
                        .child("config")
                        .child("broadcast")
                        .setValue(bMsg)
                    Toast.makeText(ctx, "Broadcast sent to all riders", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(ctx, "Broadcast failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val sendDirectBtn = Button(ctx).apply {
            text = "Send Direct Chat Message to Rider"
            setOnClickListener {
                val targetId = devIdEt.text.toString().trim()
                val msg = directMsgEt.text.toString().trim()
                if (targetId.isEmpty() || msg.isEmpty()) {
                    Toast.makeText(ctx, "Enter Rider Device ID and message", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                try {
                    val chatRef = FirebaseDatabase.getInstance().reference
                        .child("captain_smart_pro")
                        .child("chats")
                        .child(targetId)
                    val key = chatRef.push().key ?: return@setOnClickListener
                    chatRef.child(key).setValue(
                        mapOf(
                            "sender" to "Admin",
                            "message" to msg,
                            "time" to System.currentTimeMillis()
                        )
                    )
                    directMsgEt.setText("")
                    Toast.makeText(ctx, "Sent to $targetId", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(ctx, "Message failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val callRiderBtn = Button(ctx).apply {
            text = "Call Rider"
            setOnClickListener {
                val num = phoneEt.text.toString().trim()
                if (num.isEmpty()) {
                    Toast.makeText(ctx, "Enter Rider Phone Number", Toast.LENGTH_SHORT).show()
                } else {
                    ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")))
                }
            }
        }

        val saveLinksQrBtn = Button(ctx).apply {
            text = "Save QR Title & Social Links"
            setOnClickListener {
                fbUrl = fbEt.text.toString().trim()
                twUrl = twEt.text.toString().trim()
                ytUrl = ytEt.text.toString().trim()
                waUrl = waEt.text.toString().trim()
                otherUrl = otherEt.text.toString().trim()
                customQrTitle = qrEt.text.toString().trim().ifEmpty { "Rider Payment QR" }

                try {
                    FirebaseDatabase.getInstance().reference
                        .child("captain_smart_pro")
                        .child("config")
                        .updateChildren(
                            mapOf(
                                "facebook" to fbUrl,
                                "twitter" to twUrl,
                                "youtube" to ytUrl,
                                "whatsapp" to waUrl,
                                "others" to otherUrl,
                                "qrTitle" to customQrTitle,
                                "updatedAt" to System.currentTimeMillis()
                            )
                        )
                    Toast.makeText(ctx, "QR & Links updated", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(ctx, "Update failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        adminDotsBtn.setOnClickListener {
            val popup = PopupMenu(ctx, adminDotsBtn)
            popup.menu.add("Apply Subscription Days")
            popup.menu.add("Update Free Trial Days")
            popup.menu.add("Broadcast to All Riders")
            popup.menu.add("Save QR & Social Links")
            popup.setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Apply Subscription Days" -> applySubBtn.performClick()
                    "Update Free Trial Days" -> applyTrialBtn.performClick()
                    "Broadcast to All Riders" -> sendBroadcastBtn.performClick()
                    "Save QR & Social Links" -> saveLinksQrBtn.performClick()
                }
                true
            }
            popup.show()
        }

        box.addView(devIdEt)
        box.addView(subDaysEt)
        box.addView(applySubBtn)
        box.addView(trialDaysEt)
        box.addView(applyTrialBtn)
        box.addView(broadcastEt)
        box.addView(sendBroadcastBtn)
        box.addView(phoneEt)
        box.addView(callRiderBtn)
        box.addView(directMsgEt)
        box.addView(sendDirectBtn)
        box.addView(qrEt)
        box.addView(fbEt)
        box.addView(twEt)
        box.addView(ytEt)
        box.addView(waEt)
        box.addView(otherEt)
        box.addView(saveLinksQrBtn)

        scroll.addView(box)
        scroll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        mainContainer.addView(scroll)

        AlertDialog.Builder(ctx)
            .setView(mainContainer)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openLiveChatDialog(ctx: Context, deviceId: String) {
        val scroll = ScrollView(ctx)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 20)
        }
        val chatTv = TextView(ctx).apply {
            text = "Loading chat...\n"
            textSize = 13f
            setPadding(0, 0, 0, 12)
        }
        val msgEt = EditText(ctx).apply { hint = "Type message..." }
        val sendBtn = Button(ctx).apply { text = "Send" }

        val chatRef = FirebaseDatabase.getInstance().reference
            .child("captain_smart_pro")
            .child("chats")
            .child(deviceId)

        chatRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val sb = StringBuilder()
                for (row in snapshot.children) {
                    val sender = row.child("sender").getValue(String::class.java) ?: "User"
                    val msg = row.child("message").getValue(String::class.java) ?: ""
                    sb.append("$sender: $msg\n")
                }
                chatTv.text = if (sb.isEmpty()) "No messages yet." else sb.toString()
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        sendBtn.setOnClickListener {
            val txt = msgEt.text.toString().trim()
            if (txt.isEmpty()) return@setOnClickListener
            val key = chatRef.push().key ?: return@setOnClickListener
            chatRef.child(key).setValue(
                mapOf(
                    "sender" to "Rider ($deviceId)",
                    "message" to txt,
                    "time" to System.currentTimeMillis()
                )
            )
            msgEt.setText("")
        }

        box.addView(chatTv)
        box.addView(msgEt)
        box.addView(sendBtn)
        scroll.addView(box)

        AlertDialog.Builder(ctx)
            .setTitle("💬 Live Chat")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openCustomizeRiderDialog(
        ctx: Context,
        prefs: android.content.SharedPreferences,
        pickupEt: EditText,
        dropEt: EditText,
        amountEt: EditText,
        swSound: Switch,
        swShot: Switch,
        onDone: () -> Unit
    ) {
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
        val sSw = Switch(ctx).apply {
            text = "Voice / TTS Notification"
            isChecked = swSound.isChecked
        }
        val shotSw = Switch(ctx).apply {
            text = "Screenshot Save to Gallery"
            isChecked = swShot.isChecked
        }

        box.addView(TextView(ctx).apply { text = "Pickup Distance (Km):" })
        box.addView(pEt)
        box.addView(TextView(ctx).apply { text = "Drop Distance (Km):" })
        box.addView(dEt)
        box.addView(TextView(ctx).apply { text = "Ride Amount (₹):" })
        box.addView(aEt)
        box.addView(sSw)
        box.addView(shotSw)

        AlertDialog.Builder(ctx)
            .setTitle("🎨 Customize Rider App")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                pickupEt.setText(pEt.text.toString().trim())
                dropEt.setText(dEt.text.toString().trim())
                amountEt.setText(aEt.text.toString().trim())
                swSound.isChecked = sSw.isChecked
                swShot.isChecked = shotSw.isChecked
                prefs.edit()
                    .putString("pickup_km", pickupEt.text.toString().trim())
                    .putString("drop_km", dropEt.text.toString().trim())
                    .putString("fare_rs", amountEt.text.toString().trim())
                    .apply()
                onDone()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openLinksAndQrDialog(ctx: Context) {
        val scroll = ScrollView(ctx)
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
            layoutParams = LinearLayout.LayoutParams(320, 320).apply { bottomMargin = 14 }
        })

        val links = listOf(
            "Facebook" to fbUrl,
            "Twitter" to twUrl,
            "YouTube" to ytUrl,
            "WhatsApp" to waUrl,
            "Others" to otherUrl
        )
        for ((name, url) in links) {
            box.addView(Button(ctx).apply {
                text = if (url.isNotBlank()) "$name • Open" else "$name • Not Set"
                setOnClickListener {
                    if (url.isNotBlank()) {
                        try {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: Exception) {
                            Toast.makeText(ctx, "Invalid Link", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(ctx, "$name link not set", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
        scroll.addView(box)

        AlertDialog.Builder(ctx)
            .setTitle("🌐 QR & Social Links")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showLockScreen(ctx: Context, deviceId: String) {
        val scroll = ScrollView(ctx).apply {
            setBackgroundColor(bgDark)
            setPadding(28, 96, 28, 56)
        }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        val card = makeCard(ctx, Color.parseColor("#1D1119"), accentRed)
        card.addView(TextView(ctx).apply {
            text = "👑 Captain Smart Pro"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
        })
        card.addView(TextView(ctx).apply {
            text = "Subscription Required • Device ID: $deviceId"
            textSize = 13f
            setTextColor(textMuted)
            setPadding(0, 8, 0, 0)
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
            setPadding(0, 0, 0, 12)
        })
        qrCard.addView(ImageView(ctx).apply {
            setImageBitmap(generateQrMatrix(customQrTitle))
            layoutParams = LinearLayout.LayoutParams(360, 360).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        })
        col.addView(qrCard)

        col.addView(makeStyledButton(ctx, "💬 Live Chat", accentBlue).apply {
            setOnClickListener { openLiveChatDialog(ctx, deviceId) }
        })
        col.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 12) })
        col.addView(makeStyledButton(ctx, "⋮ Admin / More", accentPurple).apply {
            setOnClickListener { openHiddenAdminPassportDialog(ctx, deviceId) }
        })

        scroll.addView(col)
        setContentView(scroll)
    }

    private fun showDashboard(
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
            setPadding(18, 96, 18, 20)
            gravity = Gravity.CENTER_VERTICAL
        }

        val brandCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        brandCol.addView(TextView(ctx).apply {
            text = "👑 Captain Smart Pro"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accentGreenBright)
        })
        brandCol.addView(TextView(ctx).apply {
            text = "Auto Clicker • Ride Catcher • Order Split"
            textSize = 11f
            setTextColor(textMuted)
        })

        val statusLabel = if (hasActiveSubscription) {
            val dLeft = TimeUnit.MILLISECONDS.toDays(activeUntilMillis - nowMillis).coerceAtLeast(0L)
            "ACTIVE • ${dLeft}d"
        } else {
            val hLeft = TimeUnit.MILLISECONDS.toHours(trialEndMillis - nowMillis).coerceAtLeast(0L)
            "TRIAL • ${hLeft}h"
        }

        val statusBadgeTv = TextView(ctx).apply {
            text = statusLabel
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = makeRoundedBg(
                if (hasActiveSubscription) accentGreen else accentBlue,
                borderSlate,
                12f
            )
            setPadding(12, 8, 12, 8)
        }

        val threeDotsBtn = Button(ctx).apply {
            text = "⋮"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
            background = makeRoundedBg(cardElevated, borderSlate, 14f)
            setPadding(16, 8, 16, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = 8 }
        }

        topBar.addView(brandCol)
        topBar.addView(statusBadgeTv)
        topBar.addView(threeDotsBtn)
        root.addView(topBar)

        val frame = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val homeScroll = ScrollView(ctx).apply { setPadding(20, 16, 20, 20) }
        val homeLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        if (broadcastMessage.isNotBlank()) {
            val noticeCard = makeCard(ctx, cardElevated, accentGold)
            noticeCard.addView(TextView(ctx).apply {
                text = "📢 Notice: $broadcastMessage"
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
            text = "🟡 Rapido / Ola / Uber"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
        })
        fleetLeft.addView(TextView(ctx).apply {
            text = "📍 Dibrugarh Fleet Dispatcher"
            textSize = 12f
            setTextColor(Color.parseColor("#60A5FA"))
            setPadding(0, 6, 0, 6)
        })
        val fleetStateTv = TextView(ctx).apply {
            text = "Engine Standby (Open ⋮ to Activate)"
            textSize = 12f
            setTextColor(Color.parseColor("#F87171"))
        }
        fleetLeft.addView(fleetStateTv)

        val swOnlyOlaRapido = makeSwitchRow(ctx, "✓ Only Ola/Rapido", true)
        val swSoundTts = makeSwitchRow(ctx, "🔊 Play Sound / TTS", true)
        val swAutoAccept = makeSwitchRow(ctx, "⚡ Auto Accept", true)
        val swScreenshot = makeSwitchRow(ctx, "📷 Screenshot Save to Gallery", true)

        val pickupEt = makeStyledInput(ctx, pickupDef, "Pickup Km")
        val dropEt = makeStyledInput(ctx, dropDef, "Drop Km")
        val amountEt = makeStyledInput(ctx, fareDef, "Amount ₹")

        val earnStatsTv = TextView(ctx).apply {
            text = "Total Earnings: ₹$totalEarnings  |  Total Rides: $totalRides  |  Total Distance: ${String.format(Locale.US, "%.1f", totalDistance)} Km"
            textSize = 13f
            setTextColor(accentGreenBright)
        }

        val autoAcceptBtn = makeStyledButton(ctx, "⚡\nAuto Accept\nOFF", Color.parseColor("#2A3954")).apply {
            setOnClickListener {
                if (!isEngineActivated) {
                    Toast.makeText(ctx, "Open ⋮ menu on top to Activate first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                isAutoAcceptEnabled = !isAutoAcceptEnabled
                if (isAutoAcceptEnabled) {
                    text = "⚡\nAuto Accept\nON"
                    background = makeRoundedBg(accentGreen, accentGreenBright, 16f)
                    fleetStateTv.text = "Active • 0.00s Auto Accept ON"
                    fleetStateTv.setTextColor(accentGreenBright)
                    speakText(ctx, swSoundTts.isChecked, "Auto Accept On")
                } else {
                    text = "⚡\nAuto Accept\nOFF"
                    background = makeRoundedBg(Color.parseColor("#2A3954"), borderSlate, 16f)
                    fleetStateTv.text = "Activated • Auto Accept OFF"
                    fleetStateTv.setTextColor(accentGold)
                    speakText(ctx, swSoundTts.isChecked, "Auto Accept Off")
                }
            }
        }

        threeDotsBtn.setOnClickListener {
            val popup = PopupMenu(ctx, threeDotsBtn)
            popup.menu.add(if (isEngineActivated) "🔴 Deactivate Engine" else "🟢 Activate Engine")
            popup.menu.add("🎨 Customize Rider App")
            popup.menu.add("💬 Live Chat")
            popup.menu.add("🌐 QR & Links")
            popup.menu.add("🔐 Admin Panel")
            popup.setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "🟢 Activate Engine", "🔴 Deactivate Engine" -> {
                        isEngineActivated = !isEngineActivated
                        if (isEngineActivated) {
                            fleetStateTv.text = "Activated • Ready for Auto Accept"
                            fleetStateTv.setTextColor(accentGold)
                            speakText(ctx, swSoundTts.isChecked, "Captain Smart Pro Activated")
                            Toast.makeText(ctx, "Activated", Toast.LENGTH_SHORT).show()
                        } else {
                            isAutoAcceptEnabled = false
                            autoAcceptBtn.text = "⚡\nAuto Accept\nOFF"
                            autoAcceptBtn.background = makeRoundedBg(Color.parseColor("#2A3954"), borderSlate, 16f)
                            fleetStateTv.text = "Paused (Deactivated)"
                            fleetStateTv.setTextColor(Color.parseColor("#F87171"))
                            speakText(ctx, swSoundTts.isChecked, "Deactivated")
                            Toast.makeText(ctx, "Deactivated", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "🎨 Customize Rider App" -> openCustomizeRiderDialog(
                        ctx, prefs, pickupEt, dropEt, amountEt, swSoundTts, swScreenshot
                    ) {
                        speakText(ctx, swSoundTts.isChecked, "Customized")
                        Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
                    }
                    "💬 Live Chat" -> openLiveChatDialog(ctx, deviceId)
                    "🌐 QR & Links" -> openLinksAndQrDialog(ctx)
                    "🔐 Admin Panel" -> openHiddenAdminPassportDialog(ctx, deviceId)
                }
                true
            }
            popup.show()
        }

        fleetRow.addView(fleetLeft)
        fleetRow.addView(autoAcceptBtn)
        fleetCard.addView(fleetRow)
        homeLayout.addView(fleetCard)

        val row1 = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
        }
        row1.addView(makeStyledButton(ctx, "📷 Test Ride Accept & Save", accentBlue).apply {
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
                earnStatsTv.text =
                    "Total Earnings: ₹$totalEarnings  |  Total Rides: $totalRides  |  Total Distance: ${String.format(Locale.US, "%.1f", totalDistance)} Km"
                val saved = if (swScreenshot.isChecked) saveScreenshotToGallery(ctx, root) else false
                speakText(ctx, swSoundTts.isChecked, "Ride accepted for $rs rupees, pickup $pKm kilometer, drop $dKm kilometer")
                Toast.makeText(
                    ctx,
                    if (saved) "Ride Accepted & Screenshot Saved" else "Ride Accepted",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
        row1.addView(makeStyledButton(ctx, "🔀 Order Split Form", accentPurple).apply {
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
            text = "⚙️ Rider Ride Rules (Pickup • Drop • Amount)"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
            setPadding(0, 0, 0, 12)
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

        rulesCard.addView(makeStyledButton(ctx, "Save Rider Preferences", accentGreen).apply {
            setOnClickListener {
                prefs.edit()
                    .putString("pickup_km", pickupEt.text.toString().trim())
                    .putString("drop_km", dropEt.text.toString().trim())
                    .putString("fare_rs", amountEt.text.toString().trim())
                    .apply()
                speakText(ctx, swSoundTts.isChecked, "Preferences Saved")
                Toast.makeText(ctx, "Preferences Saved", Toast.LENGTH_SHORT).show()
            }
        })
        homeLayout.addView(rulesCard)

        val earningsCard = makeCard(ctx, cardDark, Color.parseColor("#166534"))
        earningsCard.addView(TextView(ctx).apply {
            text = "📈 Live Earnings Dashboard (Today)"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
            setPadding(0, 0, 0, 8)
        })
        earningsCard.addView(earnStatsTv)
        homeLayout.addView(earningsCard)

        homeScroll.addView(homeLayout)
        frame.addView(homeScroll)
        root.addView(frame)

        val bottomBar = HorizontalScrollView(ctx).apply {
            setBackgroundColor(headerDark)
            setPadding(12, 12, 12, 56)
        }
        val navRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        for (tabLabel in listOf("🏠 Home", "📊 Analytics", "🕒 History", "❓ Help", "ℹ️ About")) {
            navRow.addView(Button(ctx).apply {
                text = tabLabel
                textSize = 11f
                setTextColor(textWhite)
                background = makeRoundedBg(cardDark, borderSlate, 14f)
                setPadding(20, 14, 20, 14)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = 8 }
                setOnClickListener { Toast.makeText(ctx, tabLabel, Toast.LENGTH_SHORT).show() }
            })
        }
        bottomBar.addView(navRow)
        root.addView(bottomBar)

        setContentView(root)
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
            setPadding(18, 18, 18, 18)
        }
    }

    private fun makeSwitchRow(ctx: Context, label: String, checked: Boolean): Switch {
        return Switch(ctx).apply {
            text = label
            isChecked = checked
            setTextColor(textWhite)
            textSize = 14f
            setPadding(0, 12, 0, 12)
        }
    }

    private fun makeCard(ctx: Context, fillColor: Int, strokeColor: Int): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = makeRoundedBg(fillColor, strokeColor, 20f)
            setPadding(22, 22, 22, 22)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
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
        return Bitmap.createScaledBitmap(bmp, 360, 360, false)
    }
}
