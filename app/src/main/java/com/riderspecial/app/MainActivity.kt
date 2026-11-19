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

        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val nowMillis = System.currentTimeMillis()
        configurableTrialDays = prefs.getLong("trial_days_global", 3L).coerceAtLeast(0L)

        var trialStart = prefs.getLong("trial_start_$deviceId", 0L)
        if (trialStart == 0L) {
            trialStart = nowMillis
            prefs.edit().putLong("trial_start_$deviceId", trialStart).apply()
        }
        val activeUntil = prefs.getLong("active_until_$deviceId", 0L)
        val trialEnd = trialStart + TimeUnit.DAYS.toMillis(configurableTrialDays)
        val hasActiveSub = nowMillis < activeUntil

        showDashboard(
            ctx = ctx,
            deviceId = deviceId,
            nowMillis = nowMillis,
            trialEndMillis = trialEnd,
            activeUntilMillis = activeUntil,
            hasActiveSubscription = hasActiveSub
        )
    }

    private fun openHiddenAdminPassportDialog(ctx: Context, currentDeviceId: String) {
        val passEt = EditText(ctx).apply {
            hint = "Enter Admin Passport"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(28, 20, 28, 20)
        }
        AlertDialog.Builder(ctx)
            .setTitle("Admin Panel Login")
            .setView(passEt)
            .setPositiveButton("Unlock") { _, _ ->
                if (passEt.text.toString() == adminPassport) {
                    openAdminPanelDialog(ctx, currentDeviceId)
                } else {
                    Toast.makeText(ctx, "Wrong Passport", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAdminPanelDialog(ctx: Context, defaultDevId: String) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val scroll = ScrollView(ctx)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 20, 28, 24)
        }

        val devIdEt = EditText(ctx).apply {
            hint = "Rider Device ID"
            setText(defaultDevId)
        }
        val daysEt = EditText(ctx).apply {
            hint = "Subscription Days (e.g. 30, 7, -5)"
            setText("30")
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val trialEt = EditText(ctx).apply {
            hint = "Free Trial Days (e.g. 3, 7, 1)"
            setText(configurableTrialDays.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val fbEt = EditText(ctx).apply {
            hint = "Facebook Link"
            setText(prefs.getString("social_fb", "") ?: "")
        }
        val twEt = EditText(ctx).apply {
            hint = "Twitter Link"
            setText(prefs.getString("social_tw", "") ?: "")
        }
        val ytEt = EditText(ctx).apply {
            hint = "YouTube Link"
            setText(prefs.getString("social_yt", "") ?: "")
        }
        val waEt = EditText(ctx).apply {
            hint = "WhatsApp Link"
            setText(prefs.getString("social_wa", "") ?: "")
        }
        val otherEt = EditText(ctx).apply {
            hint = "Others Link"
            setText(prefs.getString("social_other", "") ?: "")
        }

        box.addView(devIdEt)
        box.addView(daysEt)
        box.addView(Button(ctx).apply {
            text = "Update Subscription Days"
            setOnClickListener {
                val targetId = devIdEt.text.toString().trim()
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
        })

        box.addView(trialEt)
        box.addView(Button(ctx).apply {
            text = "Update Free Trial Days"
            setOnClickListener {
                val tDays = trialEt.text.toString().trim().toLongOrNull() ?: 3L
                configurableTrialDays = tDays.coerceAtLeast(0L)
                prefs.edit().putLong("trial_days_global", configurableTrialDays).apply()
                try {
                    FirebaseDatabase.getInstance().reference
                        .child("captain_smart_pro")
                        .child("config")
                        .child("trialDays")
                        .setValue(configurableTrialDays)
                } catch (_: Exception) {
                }
                Toast.makeText(ctx, "Free trial set to $configurableTrialDays days", Toast.LENGTH_LONG).show()
            }
        })

        box.addView(fbEt)
        box.addView(twEt)
        box.addView(ytEt)
        box.addView(waEt)
        box.addView(otherEt)
        box.addView(Button(ctx).apply {
            text = "Save Social Links"
            setOnClickListener {
                val fb = fbEt.text.toString().trim()
                val tw = twEt.text.toString().trim()
                val yt = ytEt.text.toString().trim()
                val wa = waEt.text.toString().trim()
                val oth = otherEt.text.toString().trim()
                prefs.edit()
                    .putString("social_fb", fb)
                    .putString("social_tw", tw)
                    .putString("social_yt", yt)
                    .putString("social_wa", wa)
                    .putString("social_other", oth)
                    .apply()
                try {
                    FirebaseDatabase.getInstance().reference
                        .child("captain_smart_pro")
                        .child("config")
                        .updateChildren(
                            mapOf(
                                "facebook" to fb,
                                "twitter" to tw,
                                "youtube" to yt,
                                "whatsapp" to wa,
                                "others" to oth
                            )
                        )
                } catch (_: Exception) {
                }
                Toast.makeText(ctx, "Links Saved", Toast.LENGTH_SHORT).show()
            }
        })

        scroll.addView(box)
        AlertDialog.Builder(ctx)
            .setTitle("👑 Admin Panel")
            .setView(scroll)
            .setNegativeButton("Close", null)
            .show()
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
            setPadding(18, 72, 18, 16)
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

        val badgeText = if (hasActiveSubscription) {
            val dLeft = TimeUnit.MILLISECONDS.toDays(activeUntilMillis - nowMillis).coerceAtLeast(0L)
            "ACTIVE • ${dLeft}d"
        } else {
            val hLeft = TimeUnit.MILLISECONDS.toHours(trialEndMillis - nowMillis).coerceAtLeast(0L)
            "TRIAL • ${hLeft}h"
        }

        val badgeTv = TextView(ctx).apply {
            text = badgeText
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = makeRoundedBg(accentBlue, borderSlate, 12f)
            setPadding(12, 8, 12, 8)
        }

        val threeDotsBtn = Button(ctx).apply {
            text = "⋮"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
            background = makeRoundedBg(cardElevated, borderSlate, 12f)
            setPadding(14, 6, 14, 6)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = 8 }
        }

        topBar.addView(brandCol)
        topBar.addView(badgeTv)
        topBar.addView(threeDotsBtn)
        root.addView(topBar)

        val frame = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val homeScroll = ScrollView(ctx).apply { setPadding(18, 14, 18, 14) }
        val homeLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

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
            setPadding(0, 4, 0, 4)
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
            text = "Total Earnings: ₹$totalEarnings | Total Rides: $totalRides | Total Distance: ${String.format(Locale.US, "%.1f", totalDistance)} Km"
            textSize = 12f
            setTextColor(accentGreenBright)
        }

        val tabDetailCard = makeCard(ctx, cardDark, borderSlate).apply { visibility = View.GONE }
        val tabDetailTv = TextView(ctx).apply {
            textSize = 13f
            setTextColor(textWhite)
        }
        tabDetailCard.addView(tabDetailTv)

        val autoAcceptBtn = makeStyledButton(ctx, "⚡\nAUTO ACCEPT\nOFF", Color.parseColor("#2A3954")).apply {
            setOnClickListener {
                if (!isEngineActivated) {
                    Toast.makeText(ctx, "Open ⋮ on top to Activate first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                isAutoAcceptEnabled = !isAutoAcceptEnabled
                if (isAutoAcceptEnabled) {
                    text = "⚡\nAUTO ACCEPT\nON"
                    background = makeRoundedBg(accentGreen, accentGreenBright, 14f)
                    fleetStateTv.text = "Active • 0.00s Auto Accept ON"
                    fleetStateTv.setTextColor(accentGreenBright)
                    speakText(ctx, swSoundTts.isChecked, "Auto Accept On")
                } else {
                    text = "⚡\nAUTO ACCEPT\nOFF"
                    background = makeRoundedBg(Color.parseColor("#2A3954"), borderSlate, 14f)
                    fleetStateTv.text = "Activated • Auto Accept OFF"
                    fleetStateTv.setTextColor(accentGold)
                    speakText(ctx, swSoundTts.isChecked, "Auto Accept Off")
                }
            }
        }

        threeDotsBtn.setOnClickListener {
            val popup = PopupMenu(ctx, threeDotsBtn)
            popup.menu.add(if (isEngineActivated) "Deactivate Engine" else "Activate Engine")
            popup.menu.add("Admin Panel")
            popup.menu.add("Device ID")
            popup.setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Activate Engine", "Deactivate Engine" -> {
                        isEngineActivated = !isEngineActivated
                        if (isEngineActivated) {
                            fleetStateTv.text = "Activated • Ready for Auto Accept"
                            fleetStateTv.setTextColor(accentGold)
                            speakText(ctx, swSoundTts.isChecked, "Captain Smart Pro Activated")
                            Toast.makeText(ctx, "Engine Activated", Toast.LENGTH_SHORT).show()
                        } else {
                            isAutoAcceptEnabled = false
                            autoAcceptBtn.text = "⚡\nAUTO ACCEPT\nOFF"
                            autoAcceptBtn.background = makeRoundedBg(Color.parseColor("#2A3954"), borderSlate, 14f)
                            fleetStateTv.text = "Paused (Deactivated)"
                            fleetStateTv.setTextColor(Color.parseColor("#F87171"))
                            speakText(ctx, swSoundTts.isChecked, "Deactivated")
                            Toast.makeText(ctx, "Engine Deactivated", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "Admin Panel" -> openHiddenAdminPassportDialog(ctx, deviceId)
                    "Device ID" -> Toast.makeText(ctx, "Device ID: $deviceId", Toast.LENGTH_LONG).show()
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
        row1.addView(makeStyledButton(ctx, "📷 TEST RIDE ACCEPT & SAVE", accentBlue).apply {
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
                earnStatsTv.text =
                    "Total Earnings: ₹$totalEarnings | Total Rides: $totalRides | Total Distance: ${String.format(Locale.US, "%.1f", totalDistance)} Km"
                val saved = if (swScreenshot.isChecked) saveScreenshotToGallery(ctx, root) else false
                speakText(ctx, swSoundTts.isChecked, "Ride accepted for $rs rupees, pickup $pKm kilometer, drop $dKm kilometer")
                Toast.makeText(
                    ctx,
                    if (saved) "Ride Accepted & Screenshot Saved" else "Ride Accepted",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
        row1.addView(makeStyledButton(ctx, "🔀 ORDER SPLIT FORM", accentPurple).apply {
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
            text = "⚙️ Rider Ride Rules (Pickup • Drop • Amount)"
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
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textWhite)
            setPadding(0, 0, 0, 6)
        })
        earningsCard.addView(earnStatsTv)
        homeLayout.addView(earningsCard)
        homeLayout.addView(tabDetailCard)

        homeScroll.addView(homeLayout)
        frame.addView(homeScroll)
        root.addView(frame)

        // Clean, visible Bottom Tab Strip
        val bottomBar = HorizontalScrollView(ctx).apply {
            setBackgroundColor(headerDark)
            setPadding(10, 8, 10, 20)
        }
        val navRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val bottomTabs = listOf("🏠 HOME", "📊 ANALYTICS", "🕒 HISTORY", "❓ HELP", "🔐 ADMIN")
        for (tabLabel in bottomTabs) {
            navRow.addView(Button(ctx).apply {
                text = tabLabel
                textSize = 10f
                setTypeface(null, Typeface.BOLD)
                setTextColor(textWhite)
                background = makeRoundedBg(cardElevated, borderSlate, 12f)
                setPadding(14, 10, 14, 10)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = 8 }
                setOnClickListener {
                    when {
                        tabLabel.contains("HOME") -> tabDetailCard.visibility = View.GONE
                        tabLabel.contains("ADMIN") -> openHiddenAdminPassportDialog(ctx, deviceId)
                        tabLabel.contains("ANALYTICS") -> {
                            tabDetailCard.visibility = View.VISIBLE
                            tabDetailTv.text = "Analytics • Total Rides: $totalRides | Earnings: ₹$totalEarnings"
                        }
                        tabLabel.contains("HISTORY") -> {
                            tabDetailCard.visibility = View.VISIBLE
                            tabDetailTv.text = "History • Completed Rides: $totalRides | Distance: ${String.format(Locale.US, "%.1f", totalDistance)} Km"
                        }
                        else -> {
                            tabDetailCard.visibility = View.VISIBLE
                            tabDetailTv.text = "Help • Open ⋮ or ADMIN tab with passport to manage subscription & links."
                        }
                    }
                }
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
}
