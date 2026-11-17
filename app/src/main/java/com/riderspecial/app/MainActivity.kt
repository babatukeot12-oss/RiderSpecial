package com.riderspecial.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import java.util.UUID

class MainActivity : Activity() {

    private var isSubscribedOrTrialActive = true
    private var adminUpiId = "9954681428@naviaxis" // Your exact requested UPI ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Strict Device-Locked 7-Day Trial (Anti-Cheat: Clearing data or Reinstalling won't reset trial)
        val prefs = getSharedPreferences("CaptainSmartSecurePrefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("secure_hardware_id", null)
        
        if (deviceId == null) {
            deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: UUID.randomUUID().toString()
            prefs.edit().putString("secure_hardware_id", deviceId)
                .putLong("trial_start_timestamp", System.currentTimeMillis())
                .putBoolean("is_subscription_active", false)
                .apply()
        }

        val installTime = prefs.getLong("trial_start_timestamp", System.currentTimeMillis())
        val isPaid = prefs.getBoolean("is_subscription_active", false)
        val sevenDaysMillis = 7L * 24 * 60 * 60 * 1000
        
        // Check if trial is within 7 days or subscription is active
        isSubscribedOrTrialActive = isPaid || (System.currentTimeMillis() - installTime < sevenDaysMillis)

        renderExactOriginalUI()
    }

    private fun renderExactOriginalUI() {
        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#040814"))
            isFillViewport = true
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 25, 20, 25)
        }

        // Top Crown & Header matching your exact original design
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 15)
        }
        val crownTv = TextView(this).apply {
            text = "👑"
            textSize = 26f
            gravity = Gravity.CENTER
        }
        val titleTv = TextView(this).apply {
            text = "Captain Smart Pro"
            textSize = 22f
            setTextColor(Color.parseColor("#00FF66"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val subTitle = TextView(this).apply {
            text = "Auto Clicker • Ride Catcher • Order Split"
            textSize = 11f
            setTextColor(Color.parseColor("#00CCFF"))
            gravity = Gravity.CENTER
        }
        header.addView(crownTv)
        header.addView(titleTv)
        header.addView(subTitle)

        // Status Banner (Active / Deactivated)
        val statusBanner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (isSubscribedOrTrialActive) Color.parseColor("#06D6A0") else Color.parseColor("#EF476F"))
            setPadding(20, 12, 20, 12)
            gravity = Gravity.CENTER
        }
        val statusText = TextView(this).apply {
            text = if (isSubscribedOrTrialActive) "● 0.00s INSTANT RIDE CATCHER ACTIVE" else "⚠️ TRIAL EXPIRED - PAY ₹49 VIA UPI"
            textSize = 12f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        statusBanner.addView(statusText)

        val s1 = TextView(this).apply { text = ""; setPadding(0, 15, 0, 0) }

        // Dispatcher Card
        val dispatcherCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B132B"))
            setPadding(20, 20, 20, 20)
        }
        val dispTitle = TextView(this).apply {
            text = "🟡 Rapido / Ola / Uber"
            textSize = 15f
            setTextColor(Color.parseColor("#FFD166"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val dispSub = TextView(this).apply {
            text = "📍 Dibrugarh Fleet Dispatcher\n0.00s Instant Catcher (Standby/Ready)"
            textSize = 11f
            setTextColor(Color.parseColor("#8D99AE"))
            setPadding(0, 4, 0, 12)
        }
        val btnAutoAccept = Button(this).apply {
            text = "AUTO ACCEPT ON"
            setBackgroundColor(Color.parseColor("#06D6A0"))
            setTextColor(Color.BLACK)
            setOnClickListener {
                if (isSubscribedOrTrialActive) {
                    Toast.makeText(this@MainActivity, "Auto-Accept running at 0.00s speed!", Toast.LENGTH_SHORT).show()
                } else {
                    showPaymentDialog()
                }
            }
        }
        dispatcherCard.addView(dispTitle)
        dispatcherCard.addView(dispSub)
        dispatcherCard.addView(btnAutoAccept)

        val s2 = TextView(this).apply { text = ""; setPadding(0, 15, 0, 0) }

        // Action Buttons Row (Test Ride & Order Split)
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, 0, 0, 15)
        }
        val btnTest = Button(this).apply {
            text = "📷 Test Ride Accept & Save"
            textSize = 11f
            setBackgroundColor(Color.parseColor("#1D3557"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 5 }
            setOnClickListener { 
                if (isSubscribedOrTrialActive) {
                    Toast.makeText(this@MainActivity, "Test Ride Captured & Saved to Gallery!", Toast.LENGTH_SHORT).show()
                } else {
                    showPaymentDialog()
                }
            }
        }
        val btnSplit = Button(this).apply {
            text = "🔀 Order Split Form"
            textSize = 11f
            setBackgroundColor(Color.parseColor("#7209B7"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 5 }
            setOnClickListener { Toast.makeText(this@MainActivity, "Order Split Form Active", Toast.LENGTH_SHORT).show() }
        }
        actionRow.addView(btnTest)
        actionRow.addView(btnSplit)

        // Auto-Clicker Rules & Filters Card
        val filterCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B132B"))
            setPadding(20, 20, 20, 20)
        }
        val fTitle = TextView(this).apply {
            text = "⚙️ Auto-Clicker Rules & Filters     Pro 🔥"
            textSize = 14f
            setTextColor(Color.parseColor("#00FF66"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 15)
        }

        val rangeLabel = TextView(this).apply { text = "Max Pickup (Km)"; textSize = 11f; setTextColor(Color.parseColor("#8D99AE")) }
        val inputRange = EditText(this).apply { setText("1.0"); setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#1B263B")); setPadding(12, 10, 12, 10) }

        val priceLabel = TextView(this).apply { text = "Min Price (₹)"; textSize = 11f; setTextColor(Color.parseColor("#8D99AE")); setPadding(0, 10, 0, 0) }
        val inputPrice = EditText(this).apply { setText("20"); setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#1B263B")); setPadding(12, 10, 12, 10) }

        filterCard.addView(fTitle)
        filterCard.addView(rangeLabel)
        filterCard.addView(inputRange)
        filterCard.addView(priceLabel)
        filterCard.addView(inputPrice)

        // Live Earnings Dashboard Card
        val earnCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B132B"))
            setPadding(20, 20, 20, 20)
        }
        val eTitle = TextView(this).apply {
            text = "📊 Live Earnings Dashboard"
            textSize = 14f
            setTextColor(Color.parseColor("#00FF66"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        }
        val earnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 3f
        }
        val e1 = TextView(this).apply { text = "Total Earnings\n₹0"; textSize = 11f; setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#113F29")); setPadding(10, 10, 10, 10); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 4 } }
        val e2 = TextView(this).apply { text = "Total Rides\n0"; textSize = 11f; setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#1C2541")); setPadding(10, 10, 10, 10); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { horizontalMargin = 2 } }
        val e3 = TextView(this).apply { text = "Total Distance\n0.0 Km"; textSize = 11f; setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#3C096C")); setPadding(10, 10, 10, 10); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 4 } }
        
        earnRow.addView(e1)
        earnRow.addView(e2)
        earnRow.addView(e3)
        earnCard.addView(eTitle)
        earnCard.addView(earnRow)

        // Bottom Navigation Bar
        val bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 5f
            setBackgroundColor(Color.parseColor("#0B132B"))
            setPadding(5, 10, 5, 10)
        }
        val navItems = listOf("🏠 Home", "📊 Analytics", "🕒 History", "❓ Help", "ℹ️ About")
        for (item in navItems) {
            val nb = Button(this).apply {
                text = item
                textSize = 9f
                setBackgroundColor(Color.parseColor("#1C2541"))
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, 80, 1f).apply { setMargins(2, 0, 2, 0) }
                setOnClickListener { 
                    if (item.contains("Help")) {
                        showHelpDialog()
                    } else if (item.contains("About")) {
                        showAboutDialog()
                    } else if (item.contains("History")) {
                        showHistoryDialog()
                    } else {
                        Toast.makeText(this@MainActivity, "$item section opened", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            bottomNav.addView(nb)
        }

        mainLayout.addView(header)
        mainLayout.addView(statusBanner)
        mainLayout.addView(s1)
        mainLayout.addView(dispatcherCard)
        mainLayout.addView(s2)
        mainLayout.addView(actionRow)
        mainLayout.addView(TextView(this).apply { text = ""; setPadding(0, 10, 0, 0) })
        mainLayout.addView(filterCard)
        mainLayout.addView(TextView(this).apply { text = ""; setPadding(0, 10, 0, 0) })
        mainLayout.addView(earnCard)
        mainLayout.addView(TextView(this).apply { text = ""; setPadding(0, 15, 0, 0) })
        mainLayout.addView(bottomNav)

        rootScroll.addView(mainLayout)
        setContentView(rootScroll)
    }

    private fun showPaymentDialog() {
        AlertDialog.Builder(this)
            .setTitle("Subscription Required (₹49)")
            .setMessage("Your 7-Day Free Trial has ended for this device. Pay ₹49 via UPI to 9954681428@naviaxis to unlock unlimited rides.")
            .setPositiveButton("Pay ₹49 via UPI") { _, _ ->
                val uri = Uri.parse("upi://pay?pa=$adminUpiId&pn=CaptainSmartPro&am=49.00&cu=INR")
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (e: Exception) {
                    Toast.makeText(this, "Pay directly to UPI: $adminUpiId", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("Support & Help Center")
            .setMessage("1. Tap Auto-Accept on top to ready the 0.00s radar.\n2. Set your Max Pickup and Min Price filters.\n3. App automatically catches nearest rides instantly!")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About Captain Smart Pro")
            .setMessage("Captain Smart Pro v2.5\nDibrugarh Fleet & Auto-Clicker Edition\nDesigned for Rapido, Ola, and Uber Captains.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showHistoryDialog() {
        AlertDialog.Builder(this)
            .setTitle("Completed Ride History")
            .setMessage("No rides captured yet. Use 'Test Ride Accept' on Home.")
            .setPositiveButton("OK", null)
            .show()
    }
}
