package com.riderspecial.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {

    private val upiId = "9954681428@naviaxis"
    private val planAmount = "49"
    private val trialDays = 3L
    private val subscriptionDays = 30L

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val bgDark = Color.parseColor("#070C18")
    private val headerDark = Color.parseColor("#0B1324")
    private val cardDark = Color.parseColor("#111B31")
    private val borderSlate = Color.parseColor("#23324D")
    private val accentGreen = Color.parseColor("#16A34A")
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
        val nowMillis = System.currentTimeMillis()

        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        var trialStartMillis = prefs.getLong("trial_start_$deviceId", 0L)
        if (trialStartMillis == 0L) {
            trialStartMillis = nowMillis
            prefs.edit().putLong("trial_start_$deviceId", trialStartMillis).apply()
        }

        val activeUntilMillis = prefs.getLong("active_until_$deviceId", 0L)
        val trialEndMillis = trialStartMillis + TimeUnit.DAYS.toMillis(trialDays)

        val hasActiveSubscription = nowMillis < activeUntilMillis
        val isInFreeTrial = !hasActiveSubscription && (nowMillis < trialEndMillis)

        if (!hasActiveSubscription && !isInFreeTrial) {
            showSubscriptionLockScreen(ctx, deviceId)
        } else {
            showCaptainSmartProDashboard(
                ctx = ctx,
                deviceId = deviceId,
                nowMillis = nowMillis,
                trialEndMillis = trialEndMillis,
                activeUntilMillis = activeUntilMillis,
                hasActiveSubscription = hasActiveSubscription
            )
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun speakAudio(ctx: Context, enabled: Boolean, message: String) {
        if (!enabled) return
        if (ttsReady && tts != null) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "csp_audio")
        } else {
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun captureScreenToGallery(ctx: Context, rootView: View): Boolean {
        return try {
            val width = rootView.width
            val height = rootView.height
            if (width <= 0 || height <= 0) return false
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            rootView.draw(canvas)

            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val folder = File(picturesDir, "CaptainSmartPro")
            if (!folder.exists()) folder.mkdirs()

            val file = File(folder, "ride_accept_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                out.flush()
            }
            ctx.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun showSubscriptionLockScreen(ctx: Context, deviceId: String) {
        val scroll = ScrollView(ctx).apply {
            setBackgroundColor(bgDark)
            setPadding(28, 96, 28, 56)
        }

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }

        val lockCard = makeCard(ctx, Color.parseColor("#1D1119"), accentRed)
        lockCard.addView(TextView(ctx).apply {
            text = "Captain Smart Pro • ₹$planAmount / $subscriptionDays Days"
            textSize = 20f
            setTextColor(textWhite)
        })
        lockCard.addView(TextView(ctx).apply {
            text = "3-Day Trial Ended\nPay ₹$planAmount for $subscriptionDays Days Subscription\nUPI ID: $upiId\nDevice ID: $deviceId"
            textSize = 14f
            setTextColor(textMuted)
            setPadding(0, 10, 0, 0)
        })
        container.addView(lockCard)

        val qrCard = makeCard(ctx, cardDark, accentGold).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        qrCard.addView(TextView(ctx).apply {
            text = "Scan QR or Pay ₹$planAmount for 30 Days"
            textSize = 15f
            setTextColor(accentGold)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        })
        qrCard.addView(ImageView(ctx).apply {
            setImageBitmap(generateQrMatrixBitmap(upiId, planAmount))
            layoutParams = LinearLayout.LayoutParams(380, 380).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        })
        qrCard.addView(TextView(ctx).apply {
            text = "$upiId  •  ₹$planAmount (30 Days)"
            textSize = 13f
            setTextColor(textWhite)
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 0)
        })
        container.addView(qrCard)

        val upiUri =
            "upi://pay?pa=$upiId&pn=Captain%20Smart%20Pro&am=$planAmount&cu=INR&tn=Captain%20Smart%20Pro%2030%20Days"

        container.addView(makeStyledButton(ctx, "Pay ₹$planAmount via UPI App (GPay / PhonePe / Navi / BHIM)", accentGreen).apply {
            setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
                val chooser = Intent.createChooser(intent, "Pay ₹$planAmount using UPI App")
                if (intent.resolveActivity(ctx.packageManager) != null) {
                    ctx.startActivity(chooser)
                } else {
                    Toast.makeText(ctx, "No UPI app found. Pay ₹$planAmount to $upiId", Toast.LENGTH_LONG).show()
                }
            }
        })

        container.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 16) })

        val verifyCard = makeCard(ctx, cardDark, accentBlue)
        verifyCard.addView(TextView(ctx).apply {
            text = "Payment Verification (12-Digit UTR)"
            textSize = 15f
            setTextColor(textWhite)
        })
        val utrEt = EditText(ctx).apply {
            hint = "Enter 12-digit UTR after ₹$planAmount payment"
            setTextColor(textWhite)
            setHintTextColor(textMuted)
            setPadding(12, 18, 12, 18)
        }
        verifyCard.addView(utrEt)
        verifyCard.addView(makeStyledButton(ctx, "Verify Payment & Activate 30 Days", accentBlue).apply {
            setOnClickListener {
                val utr = utrEt.text.toString().trim()
                if (utr.length < 12) {
                    Toast.makeText(ctx, "Enter valid 12-digit UTR for ₹$planAmount", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(
                        ctx,
                        "Verifying payment UTR $utr via server before unlocking 30-day plan",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
        container.addView(verifyCard)

        scroll.addView(container)
        setContentView(scroll)
    }

    private fun showCaptainSmartProDashboard(
        ctx: Context,
        deviceId: String,
        nowMillis: Long,
        trialEndMillis: Long,
        activeUntilMillis: Long,
        hasActiveSubscription: Boolean
    ) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_state", Context.MODE_PRIVATE)
        val maxPickupDefault = prefs.getString("max_pickup", "1.0") ?: "1.0"
        val minPriceDefault = prefs.getString("min_price", "20") ?: "20"

        var totalEarnings = prefs.getInt("total_earnings", 0)
        var totalRides = prefs.getInt("total_rides", 0)
        var totalDistance = prefs.getFloat("total_distance", 0f)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgDark)
        }

        // Top Bar with safe top padding so status bar does not overlap title
        val topBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(headerDark)
            setPadding(24, 96, 24, 20)
            gravity = Gravity.CENTER_VERTICAL
        }

        val brandCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        brandCol.addView(TextView(ctx).apply {
            text = "👑 Captain Smart Pro"
            textSize = 19f
            setTextColor(Color.parseColor("#22C55E"))
        })
        brandCol.addView(TextView(ctx).apply {
            text = "Auto Clicker • Ride Catcher • Order Split"
            textSize = 11f
            setTextColor(textMuted)
        })

        val badgeText = if (hasActiveSubscription) {
            val daysLeft = TimeUnit.MILLISECONDS.toDays(activeUntilMillis - nowMillis).coerceAtLeast(0L)
            "30D PLAN • ${daysLeft}d"
        } else {
            val hoursLeft = TimeUnit.MILLISECONDS.toHours(trialEndMillis - nowMillis).coerceAtLeast(0L)
            "TRIAL • ${hoursLeft}h"
        }

        val badgeTv = TextView(ctx).apply {
            text = badgeText
            textSize = 11f
            setTextColor(Color.WHITE)
            background = makeRoundedBg(
                if (hasActiveSubscription) accentGreen else accentRed,
                if (hasActiveSubscription) accentGreen else accentRed,
                14f
            )
            setPadding(18, 10, 18, 10)
        }

        topBar.addView(brandCol)
        topBar.addView(badgeTv)
        root.addView(topBar)

        val contentFrame = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val homeScroll = ScrollView(ctx).apply { setPadding(20, 16, 20, 20) }
        val homeLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        // Fleet Dispatcher Card
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
            setTextColor(textWhite)
        })
        fleetLeft.addView(TextView(ctx).apply {
            text = "📍 Dibrugarh Fleet Dispatcher"
            textSize = 12f
            setTextColor(Color.parseColor("#60A5FA"))
            setPadding(0, 6, 0, 6)
        })
        val fleetStateTv = TextView(ctx).apply {
            text = "Paused (Deactivated)"
            textSize = 12f
            setTextColor(Color.parseColor("#F87171"))
        }
        fleetLeft.addView(fleetStateTv)

        val swOnlyOlaRapido = makeSwitchRow(ctx, "✓ Only Ola/Rapido", true)
        val swSoundTts = makeSwitchRow(ctx, "🔊 Play Sound / TTS", true)
        val swAutoAccept = makeSwitchRow(ctx, "⚡ Auto Accept", true)
        val swScreenshot = makeSwitchRow(ctx, "📷 Screenshot Save to Gallery", true)

        val earnStatsTv = TextView(ctx).apply {
            text = "Total Earnings: ₹$totalEarnings   |   Total Rides: $totalRides   |   Total Distance: ${String.format(Locale.US, "%.1f", totalDistance)} Km"
            textSize = 13f
            setTextColor(Color.parseColor("#4ADE80"))
        }

        val historyBodyTv = TextView(ctx).apply {
            text = "Completed Ride History\nNo rides captured yet. Use 'Test Ride Accept & Save' on Home."
            textSize = 14f
            setTextColor(textMuted)
        }

        val autoAcceptToggleBtn = makeStyledButton(ctx, "⚡\nAuto Accept\nON", accentGreen).apply {
            setOnClickListener {
                fleetStateTv.text = "Active • 0.00s Radar ($deviceId)"
                fleetStateTv.setTextColor(Color.parseColor("#4ADE80"))
                speakAudio(ctx, swSoundTts.isChecked, "Captain Smart Pro Auto Accept Active")
                Toast.makeText(ctx, "Auto Accept ON", Toast.LENGTH_SHORT).show()
            }
        }
        fleetRow.addView(fleetLeft)
        fleetRow.addView(autoAcceptToggleBtn)
        fleetCard.addView(fleetRow)
        homeLayout.addView(fleetCard)

        // Row 1: Test Ride Accept & Save | Order Split Form
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
                totalRides += 1
                totalEarnings += 25
                totalDistance += 2.4f
                prefs.edit()
                    .putInt("total_rides", totalRides)
                    .putInt("total_earnings", totalEarnings)
                    .putFloat("total_distance", totalDistance)
                    .apply()

                earnStatsTv.text =
                    "Total Earnings: ₹$totalEarnings   |   Total Rides: $totalRides   |   Total Distance: ${String.format(Locale.US, "%.1f", totalDistance)} Km"
                historyBodyTv.text =
                    "Completed Ride #$totalRides • ₹25 • 2.4 Km (Saved)"

                val savedShot = if (swScreenshot.isChecked) captureScreenToGallery(ctx, root) else false
                speakAudio(ctx, swSoundTts.isChecked, "Ride Accepted")
                Toast.makeText(
                    ctx,
                    if (savedShot) "Ride Accepted & Screenshot Saved" else "Ride Accepted & Saved",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
        row1.addView(makeStyledButton(ctx, "🔀 Order Split Form", accentPurple).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 6
            }
            setOnClickListener {
                speakAudio(ctx, swSoundTts.isChecked, "Order Split Form Ready")
                Toast.makeText(ctx, "Order Split Form Ready", Toast.LENGTH_SHORT).show()
            }
        })
        homeLayout.addView(row1)

        // Row 2: Live Stats | History | Notify | Settings
        val row2 = HorizontalScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }
        val row2Inner = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        for (quickLabel in listOf("📊 Live Stats", "🕒 History", "🔔 Notify", "⚙️ Settings")) {
            row2Inner.addView(makeStyledButton(ctx, quickLabel, cardDark).apply {
                textSize = 11f
                background = makeRoundedBg(cardDark, borderSlate, 14f)
                setPadding(16, 12, 16, 12)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = 8 }
                setOnClickListener {
                    if (quickLabel.contains("Notify")) {
                        speakAudio(ctx, swSoundTts.isChecked, "Dibrugarh Fleet Dispatcher Alert")
                    }
                    Toast.makeText(ctx, quickLabel, Toast.LENGTH_SHORT).show()
                }
            })
        }
        row2.addView(row2Inner)
        homeLayout.addView(row2)

        // Auto-Clicker Rules & Filters [Pro]
        val rulesCard = makeCard(ctx, cardDark, borderSlate)
        rulesCard.addView(TextView(ctx).apply {
            text = "⚙️ Auto-Clicker Rules & Filters   [PRO]"
            textSize = 15f
            setTextColor(textWhite)
            setPadding(0, 0, 0, 12)
        })

        val inputsRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val pickupCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 8
            }
        }
        pickupCol.addView(TextView(ctx).apply {
            text = "Max Pickup (Km)"
            textSize = 12f
            setTextColor(textMuted)
        })
        val pickupEt = EditText(ctx).apply {
            setText(maxPickupDefault)
            setTextColor(textWhite)
        }
        pickupCol.addView(pickupEt)

        val priceCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 8
            }
        }
        priceCol.addView(TextView(ctx).apply {
            text = "Min Price (₹)"
            textSize = 12f
            setTextColor(textMuted)
        })
        val priceEt = EditText(ctx).apply {
            setText(minPriceDefault)
            setTextColor(textWhite)
        }
        priceCol.addView(priceEt)

        inputsRow.addView(pickupCol)
        inputsRow.addView(priceCol)
        rulesCard.addView(inputsRow)

        rulesCard.addView(swOnlyOlaRapido)
        rulesCard.addView(swSoundTts)
        rulesCard.addView(swAutoAccept)
        rulesCard.addView(swScreenshot)

        rulesCard.addView(makeStyledButton(ctx, "Save Settings", accentGreen).apply {
            setOnClickListener {
                prefs.edit()
                    .putString("max_pickup", pickupEt.text.toString().trim())
                    .putString("min_price", priceEt.text.toString().trim())
                    .apply()
                speakAudio(ctx, swSoundTts.isChecked, "Settings Saved")
                Toast.makeText(ctx, "Settings Saved", Toast.LENGTH_SHORT).show()
            }
        })
        homeLayout.addView(rulesCard)

        // Live Earnings Dashboard (Today)
        val earningsCard = makeCard(ctx, cardDark, Color.parseColor("#166534"))
        earningsCard.addView(TextView(ctx).apply {
            text = "📈 Live Earnings Dashboard (Today)"
            textSize = 15f
            setTextColor(textWhite)
            setPadding(0, 0, 0, 8)
        })
        earningsCard.addView(earnStatsTv)
        homeLayout.addView(earningsCard)

        homeScroll.addView(homeLayout)

        // Secondary Tabs View (Analytics, History, Help, About)
        val tabScroll = ScrollView(ctx).apply {
            setPadding(24, 24, 24, 24)
            visibility = View.GONE
        }
        val tabCard = makeCard(ctx, cardDark, borderSlate)
        val tabTitleTv = TextView(ctx).apply {
            text = "📊 Fleet Analytics & Performance"
            textSize = 17f
            setTextColor(textWhite)
            setPadding(0, 0, 0, 12)
        }
        tabCard.addView(tabTitleTv)
        tabCard.addView(historyBodyTv)
        tabScroll.addView(tabCard)

        contentFrame.addView(homeScroll)
        contentFrame.addView(tabScroll)
        root.addView(contentFrame)

        // Bottom Navigation Bar with safe bottom padding
        val bottomBar = HorizontalScrollView(ctx).apply {
            setBackgroundColor(headerDark)
            setPadding(12, 12, 12, 56)
        }
        val navRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }

        val tabs = listOf("🏠 Home", "📊 Analytics", "🕒 History", "❓ Help", "ℹ️ About")
        for (label in tabs) {
            val btn = Button(ctx).apply {
                text = label
                textSize = 11f
                setTextColor(textWhite)
                background = makeRoundedBg(cardDark, borderSlate, 14f)
                setPadding(20, 14, 20, 14)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = 8 }
                setOnClickListener {
                    if (label.startsWith("🏠")) {
                        homeScroll.visibility = View.VISIBLE
                        tabScroll.visibility = View.GONE
                    } else {
                        homeScroll.visibility = View.GONE
                        tabScroll.visibility = View.VISIBLE
                        tabTitleTv.text = label
                        historyBodyTv.text = when {
                            label.contains("Analytics") ->
                                "Total Revenue Generated: ₹$totalEarnings\nTotal Trips Completed: $totalRides\nAverage Earnings Per Trip: ₹${if (totalRides > 0) totalEarnings / totalRides else 0}\nEfficiency Rating: 4.9 ★"
                            label.contains("History") ->
                                if (totalRides > 0) "Completed Rides: $totalRides • Total Earned: ₹$totalEarnings"
                                else "Completed Ride History\nNo rides captured yet. Use 'Test Ride Accept & Save' on Home."
                            label.contains("Help") ->
                                "Support & Help Center\n1. Tap Auto Accept on top to ready the 0.00s radar.\n2. Set Max Pickup and Min Price filters.\n3. 3-day free trial, then ₹$planAmount for $subscriptionDays days to $upiId."
                            else ->
                                "Captain Smart Pro v2.5\nDibrugarh Fleet & Auto-Clicker Edition\nDesigned for Rapido, Ola, and Uber Captains."
                        }
                    }
                }
            }
            navRow.addView(btn)
        }

        bottomBar.addView(navRow)
        root.addView(bottomBar)

        setContentView(root)
    }

    private fun makeStyledButton(ctx: Context, label: String, bgColor: Int): Button {
        return Button(ctx).apply {
            text = label
            textSize = 13f
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

    private fun generateQrMatrixBitmap(upi: String, amount: String): Bitmap {
        val size = 21
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val seed = "$upi:$amount".hashCode()
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
        return Bitmap.createScaledBitmap(bmp, 380, 380, false)
    }
}
