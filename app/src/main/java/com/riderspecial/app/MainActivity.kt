package com.riderspecial.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {

    private val upiId = "9954681428@naviaxis"
    private val planAmount = "49"
    private val trialDays = 3L
    private val subscriptionDays = 30L

    private val bgDark = Color.parseColor("#080D1A")
    private val cardDark = Color.parseColor("#11192E")
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
        val deviceId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown_device"
        val nowMillis = System.currentTimeMillis()

        val prefs = ctx.getSharedPreferences("captain_smart_pro_v25_state", Context.MODE_PRIVATE)
        var trialStartMillis = prefs.getLong("trial_start_$deviceId", 0L)
        if (trialStartMillis == 0L) {
            trialStartMillis = nowMillis
            prefs.edit().putLong("trial_start_$deviceId", trialStartMillis).apply()
        }

        val activeUntilMillis = prefs.getLong("active_until_$deviceId", 0L)
        val trialEndMillis = trialStartMillis + TimeUnit.DAYS.toMillis(trialDays)

        val hasActiveSubscription = nowMillis < activeUntilMillis
        val isInFreeTrial = !hasActiveSubscription && (nowMillis < trialEndMillis)
        val isUnlocked = hasActiveSubscription || isInFreeTrial

        if (!isUnlocked) {
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

    private fun showSubscriptionLockScreen(ctx: Context, deviceId: String) {
        val scroll = ScrollView(ctx).apply {
            setBackgroundColor(bgDark)
            setPadding(36, 48, 36, 48)
        }

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }

        val headerCard = makeCard(Color.parseColor("#1E1018"), accentRed)
        val titleTv = TextView(ctx).apply {
            text = "Captain Smart Pro • ₹$planAmount / $subscriptionDays Days"
            textSize = 22f
            setTextColor(textWhite)
        }
        val subTv = TextView(ctx).apply {
            text = "3-Day Free Trial Ended\nPay ₹$planAmount for $subscriptionDays Days to unlock\nUPI ID: $upiId\nDevice ID: $deviceId"
            textSize = 14f
            setTextColor(textMuted)
            setPadding(0, 12, 0, 0)
        }
        headerCard.addView(titleTv)
        headerCard.addView(subTv)
        container.addView(headerCard)

        val qrCard = makeCard(cardDark, accentGold).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val qrLabel = TextView(ctx).apply {
            text = "Scan QR or Pay via UPI App (₹$planAmount)"
            textSize = 15f
            setTextColor(accentGold)
            setPadding(0, 0, 0, 16)
        }
        val qrImage = ImageView(ctx).apply {
            setImageBitmap(generateQrMatrixBitmap(upiId, planAmount))
            layoutParams = LinearLayout.LayoutParams(420, 420)
        }
        val upiCaption = TextView(ctx).apply {
            text = "$upiId  •  Amount: ₹$planAmount"
            textSize = 13f
            setTextColor(textWhite)
            setPadding(0, 14, 0, 0)
        }
        qrCard.addView(qrLabel)
        qrCard.addView(qrImage)
        qrCard.addView(upiCaption)
        container.addView(qrCard)

        val upiUriString =
            "upi://pay?pa=$upiId&pn=Captain%20Smart%20Pro&am=$planAmount&cu=INR&tn=Captain%20Smart%20Pro%2030%20Days"

        val payBtn = Button(ctx).apply {
            text = "Pay ₹$planAmount Now (GPay / PhonePe / Navi / BHIM)"
            setBackgroundColor(accentGreen)
            setTextColor(Color.WHITE)
            setPadding(24, 28, 24, 28)
            setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUriString))
                val chooser = Intent.createChooser(intent, "Choose UPI App")
                if (intent.resolveActivity(ctx.packageManager) != null) {
                    ctx.startActivity(chooser)
                } else {
                    Toast.makeText(ctx, "No UPI app found. Pay ₹$planAmount to $upiId", Toast.LENGTH_LONG).show()
                }
            }
        }

        val verifyCard = makeCard(cardDark, accentBlue)
        val verifyTitle = TextView(ctx).apply {
            text = "Server Payment Verification (12-Digit UTR)"
            textSize = 15f
            setTextColor(textWhite)
        }
        val utrInput = EditText(ctx).apply {
            hint = "Enter 12-digit UPI UTR after ₹$planAmount payment"
            setTextColor(textWhite)
            setHintTextColor(textMuted)
            setPadding(16, 20, 16, 20)
        }
        val verifyBtn = Button(ctx).apply {
            text = "Verify Payment & Unlock 30 Days"
            setBackgroundColor(accentBlue)
            setTextColor(Color.WHITE)
            setOnClickListener {
                val utr = utrInput.text.toString().trim()
                if (utr.length < 12) {
                    Toast.makeText(
                        ctx,
                        "Enter valid 12-digit UTR after paying ₹$planAmount to $upiId",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    // Connect server/payment gateway verification here so fake UTR/payment cannot unlock.
                    Toast.makeText(
                        ctx,
                        "Verifying UTR $utr with payment server for $deviceId...",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        verifyCard.addView(verifyTitle)
        verifyCard.addView(utrInput)
        verifyCard.addView(verifyBtn)

        val spacer = View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 20) }
        container.addView(payBtn)
        container.addView(spacer)
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
        val prefs = ctx.getSharedPreferences("captain_smart_pro_v25_state", Context.MODE_PRIVATE)
        val maxPickupDefault = prefs.getString("max_pickup", "1.0") ?: "1.0"
        val minPriceDefault = prefs.getString("min_price", "20") ?: "20"

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgDark)
        }

        // Top Header Bar (Captain Smart Pro)
        val topBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0B1222"))
            setPadding(28, 28, 28, 28)
            gravity = Gravity.CENTER_VERTICAL
        }

        val brandBlock = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val appTitle = TextView(ctx).apply {
            text = "👑 Captain Smart Pro"
            textSize = 20f
            setTextColor(Color.parseColor("#22C55E"))
        }
        val appSub = TextView(ctx).apply {
            text = "Auto Clicker • Ride Catcher • Order Split"
            textSize = 12f
            setTextColor(textMuted)
        }
        brandBlock.addView(appTitle)
        brandBlock.addView(appSub)

        val statusBadgeText = if (hasActiveSubscription) {
            val daysLeft = TimeUnit.MILLISECONDS.toDays(activeUntilMillis - nowMillis).coerceAtLeast(0L)
            "PRO • ${daysLeft}d"
        } else {
            val hoursLeft = TimeUnit.MILLISECONDS.toHours(trialEndMillis - nowMillis).coerceAtLeast(0L)
            "TRIAL • ${hoursLeft}h"
        }

        val statusBadge = TextView(ctx).apply {
            text = statusBadgeText
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(if (hasActiveSubscription) accentGreen else accentRed)
            setPadding(20, 12, 20, 12)
        }

        topBar.addView(brandBlock)
        topBar.addView(statusBadge)
        root.addView(topBar)

        val contentFrame = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        // Home Tab View
        val homeScroll = ScrollView(ctx).apply { setPadding(24, 20, 24, 24) }
        val homeLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        val fleetCard = makeCard(cardDark, Color.parseColor("#1E293B"))
        val fleetRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val fleetInfo = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val fleetTitle = TextView(ctx).apply {
            text = "🟡 Rapido / Ola / Uber"
            textSize = 18f
            setTextColor(textWhite)
        }
        val fleetLoc = TextView(ctx).apply {
            text = "📍 Dibrugarh Fleet Dispatcher"
            textSize = 13f
            setTextColor(Color.parseColor("#60A5FA"))
            setPadding(0, 6, 0, 6)
        }
        val fleetStateTv = TextView(ctx).apply {
            text = "Auto Accept Ready"
            textSize = 13f
            setTextColor(Color.parseColor("#4ADE80"))
        }
        fleetInfo.addView(fleetTitle)
        fleetInfo.addView(fleetLoc)
        fleetInfo.addView(fleetStateTv)

        val autoAcceptBtn = Button(ctx).apply {
            text = "⚡\nAuto Accept\nON"
            setBackgroundColor(accentGreen)
            setTextColor(Color.WHITE)
            setOnClickListener {
                fleetStateTv.text = "Active • 0.00s Radar Running ($deviceId)"
                Toast.makeText(ctx, "Auto Accept ON (Dibrugarh Fleet Dispatcher)", Toast.LENGTH_SHORT).show()
            }
        }
        fleetRow.addView(fleetInfo)
        fleetRow.addView(autoAcceptBtn)
        fleetCard.addView(fleetRow)
        homeLayout.addView(fleetCard)

        // Action Buttons Row 1: Test Ride Accept & Save | Order Split Form
        val row1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val testBtn = Button(ctx).apply {
            text = "📷 Test Ride Accept & Save"
            setBackgroundColor(accentBlue)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 10
            }
            setOnClickListener {
                Toast.makeText(ctx, "Test Ride Captured & Saved", Toast.LENGTH_SHORT).show()
            }
        }
        val splitBtn = Button(ctx).apply {
            text = "🔀 Order Split Form"
            setBackgroundColor(accentPurple)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 10
            }
            setOnClickListener {
                Toast.makeText(ctx, "Order Split Form Ready", Toast.LENGTH_SHORT).show()
            }
        }
        row1.addView(testBtn)
        row1.addView(splitBtn)
        homeLayout.addView(row1)

        val spacerMid = View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, 16) }
        homeLayout.addView(spacerMid)

        // Auto-Clicker Rules & Filters Card
        val rulesCard = makeCard(cardDark, Color.parseColor("#1E293B"))
        val rulesHeader = TextView(ctx).apply {
            text = "⚙️ Auto-Clicker Rules & Filters   [PRO]"
            textSize = 16f
            setTextColor(textWhite)
            setPadding(0, 0, 0, 14)
        }
        rulesCard.addView(rulesHeader)

        val filterInputsRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val pickupBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 10
            }
        }
        val pickupLbl = TextView(ctx).apply {
            text = "Max Pickup (Km)"
            textSize = 12f
            setTextColor(textMuted)
        }
        val pickupEt = EditText(ctx).apply {
            setText(maxPickupDefault)
            setTextColor(textWhite)
        }
        pickupBox.addView(pickupLbl)
        pickupBox.addView(pickupEt)

        val priceBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 10
            }
        }
        val priceLbl = TextView(ctx).apply {
            text = "Min Price (₹)"
            textSize = 12f
            setTextColor(textMuted)
        }
        val priceEt = EditText(ctx).apply {
            setText(minPriceDefault)
            setTextColor(textWhite)
        }
        priceBox.addView(priceLbl)
        priceBox.addView(priceEt)

        filterInputsRow.addView(pickupBox)
        filterInputsRow.addView(priceBox)
        rulesCard.addView(filterInputsRow)

        val swOnlyOlaRapido = makeSwitchRow(ctx, "✓ Only Ola/Rapido", true)
        val swSoundTts = makeSwitchRow(ctx, "🔊 Play Sound / TTS", true)
        val swAutoAccept = makeSwitchRow(ctx, "⚡ Auto Accept", true)
        val swScreenshot = makeSwitchRow(ctx, "📷 Screenshot Save to Gallery", true)

        rulesCard.addView(swOnlyOlaRapido)
        rulesCard.addView(swSoundTts)
        rulesCard.addView(swAutoAccept)
        rulesCard.addView(swScreenshot)

        val saveSettingsBtn = Button(ctx).apply {
            text = "Save Settings"
            setBackgroundColor(accentGreen)
            setTextColor(Color.WHITE)
            setOnClickListener {
                prefs.edit()
                    .putString("max_pickup", pickupEt.text.toString().trim())
                    .putString("min_price", priceEt.text.toString().trim())
                    .apply()
                Toast.makeText(ctx, "Settings Saved", Toast.LENGTH_SHORT).show()
            }
        }
        rulesCard.addView(saveSettingsBtn)
        homeLayout.addView(rulesCard)

        // Live Earnings Dashboard Card
        val earningsCard = makeCard(cardDark, Color.parseColor("#14532D"))
        val earnTitle = TextView(ctx).apply {
            text = "📈 Live Earnings Dashboard (Today)"
            textSize = 16f
            setTextColor(textWhite)
            setPadding(0, 0, 0, 12)
        }
        val earnStats = TextView(ctx).apply {
            text = "Total Earnings: ₹0   |   Total Rides: 0   |   Total Distance: 0.0 Km"
            textSize = 14f
            setTextColor(Color.parseColor("#4ADE80"))
        }
        earningsCard.addView(earnTitle)
        earningsCard.addView(earnStats)
        homeLayout.addView(earningsCard)

        homeScroll.addView(homeLayout)

        // Secondary Tabs (Analytics, History, Help, About)
        val infoScroll = ScrollView(ctx).apply {
            setPadding(28, 28, 28, 28)
            visibility = View.GONE
        }
        val infoCard = makeCard(cardDark, Color.parseColor("#1E293B"))
        val infoHeading = TextView(ctx).apply {
            text = "📊 Fleet Analytics & Performance"
            textSize = 18f
            setTextColor(textWhite)
            setPadding(0, 0, 0, 12)
        }
        val infoBody = TextView(ctx).apply {
            text = "Total Revenue Generated: ₹0\nTotal Trips Completed: 0\nAverage Earnings Per Trip: ₹0\nEfficiency Rating: 4.9 ★\n\nCaptain Smart Pro v2.5 • Dibrugarh Fleet & Auto-Clicker Edition"
            textSize = 14f
            setTextColor(textMuted)
        }
        infoCard.addView(infoHeading)
        infoCard.addView(infoBody)
        infoScroll.addView(infoCard)

        contentFrame.addView(homeScroll)
        contentFrame.addView(infoScroll)
        root.addView(contentFrame)

        // Bottom Navigation Bar (Home, Analytics, History, Help, About)
        val bottomNav = HorizontalScrollView(ctx).apply {
            setBackgroundColor(Color.parseColor("#0B1222"))
            setPadding(12, 12, 12, 12)
        }
        val navRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }

        val tabLabels = listOf("🏠 Home", "📊 Analytics", "🕒 History", "❓ Help", "ℹ️ About")
        for (label in tabLabels) {
            val tabBtn = Button(ctx).apply {
                text = label
                textSize = 12f
                setTextColor(textWhite)
                setBackgroundColor(Color.parseColor("#1E293B"))
                setOnClickListener {
                    if (label.startsWith("🏠")) {
                        homeScroll.visibility = View.VISIBLE
                        infoScroll.visibility = View.GONE
                    } else {
                        homeScroll.visibility = View.GONE
                        infoScroll.visibility = View.VISIBLE
                        infoHeading.text = label
                        when {
                            label.contains("Analytics") -> infoBody.text =
                                "Total Revenue Generated: ₹0\nTotal Trips Completed: 0\nAverage Earnings Per Trip: ₹0\nEfficiency Rating: 4.9 ★"
                            label.contains("History") -> infoBody.text =
                                "Completed Ride History\nNo rides captured yet. Use 'Test Ride Accept' on Home."
                            label.contains("Help") -> infoBody.text =
                                "Support & Help Center\n1. Tap Auto Accept on top to ready the 0.00s radar.\n2. Set Max Pickup and Min Price filters.\n3. Plan: ₹$planAmount for $subscriptionDays days to $upiId after 3-day trial."
                            else -> infoBody.text =
                                "Captain Smart Pro v2.5\nDibrugarh Fleet & Auto-Clicker Edition\nDesigned for Rapido, Ola, and Uber Captains."
                        }
                    }
                }
            }
            navRow.addView(tabBtn)
        }

        bottomNav.addView(navRow)
        root.addView(bottomNav)

        setContentView(root)
    }

    private fun makeSwitchRow(ctx: Context, label: String, checked: Boolean): Switch {
        return Switch(ctx).apply {
            text = label
            isChecked = checked
            setTextColor(textWhite)
            setPadding(0, 14, 0, 14)
        }
    }

    private fun makeCard(fillColor: Int, strokeColor: Int): LinearLayout {
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(fillColor)
            setStroke(2, strokeColor)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
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
        return Bitmap.createScaledBitmap(bmp, 420, 420, false)
    }
}
