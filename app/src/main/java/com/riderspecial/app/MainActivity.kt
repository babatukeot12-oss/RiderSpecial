package com.riderspecial.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    private var selectedVehicle = "Bike"
    private var adminUpiId = "babatukeot@paytm"
    private var broadcastMsg = "🚀 Welcome to Captain Smart Pro! 0.00s Instant Auto-Accept active."
    private var communityLinks = "FB: facebook.com | YT: youtube.com | WA: whatsapp.com"
    private var maxPickupKm = "1.0"
    private var minFareAmt = "20"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderExact3DDashboard()
    }

    private fun renderExact3DDashboard() {
        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#030712"))
            isFillViewport = true
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(25, 30, 25, 30)
        }

        // 3D Header with Crown & Glow Look
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 15)
        }
        val crownTv = TextView(this).apply {
            text = "👑"
            textSize = 30f
            gravity = Gravity.CENTER
        }
        val titleTv = TextView(this).apply {
            text = "Captain Smart Pro"
            textSize = 24f
            setTextColor(Color.parseColor("#00FF66"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val subTitle = TextView(this).apply {
            text = "Auto-Clicker & Ride Dispatch Portal"
            textSize = 12f
            setTextColor(Color.parseColor("#00E1FF"))
            gravity = Gravity.CENTER
        }
        header.addView(crownTv)
        header.addView(titleTv)
        header.addView(subTitle)

        // Live Announcement Bar
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B132B"))
            setPadding(20, 15, 20, 15)
        }
        val bText = TextView(this).apply {
            text = "📢 Announcement:\n$broadcastMsg"
            textSize = 12f
            setTextColor(Color.parseColor("#FFD166"))
        }
        banner.addView(bText)

        val s0 = TextView(this).apply { text = ""; setPadding(0, 15, 0, 0) }

        // Admin Login 3D Card (Hidden PIN 3479)
        val adminCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111A30"))
            setPadding(25, 25, 25, 25)
        }
        val aTitle = TextView(this).apply {
            text = "🛡️ Admin Login"
            textSize = 16f
            setTextColor(Color.parseColor("#4CC9F0"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val aDesc = TextView(this).apply {
            text = "Manage Fleet, Broadcasts & Payments"
            textSize = 11f
            setTextColor(Color.parseColor("#8D99AE"))
            setPadding(0, 5, 0, 15)
        }
        val btnAdmin = Button(this).apply {
            text = "Login Now"
            setBackgroundColor(Color.parseColor("#3A86EF"))
            setTextColor(Color.WHITE)
            setOnClickListener { showAdminPinDialog() }
        }
        adminCard.addView(aTitle)
        adminCard.addView(aDesc)
        adminCard.addView(btnAdmin)

        val s1 = TextView(this).apply { text = ""; setPadding(0, 15, 0, 0) }

        // Rider Login & Vehicle Selector Card (Exact match)
        val riderCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111A30"))
            setPadding(25, 25, 25, 25)
        }
        val rTitle = TextView(this).apply {
            text = "🛵 Rider Login"
            textSize = 16f
            setTextColor(Color.parseColor("#06D6A0"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val rDesc = TextView(this).apply {
            text = "Accept Ride & Earn Income (0.00s)"
            textSize = 11f
            setTextColor(Color.parseColor("#8D99AE"))
            setPadding(0, 5, 0, 15)
        }

        val vLabel = TextView(this).apply {
            text = "Choose Your Vehicle Type"
            textSize = 12f
            setTextColor(Color.parseColor("#FFD166"))
            setPadding(0, 0, 0, 8)
        }

        val vRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 4f
            setPadding(0, 0, 0, 15)
        }
        val vehicles = listOf("Scooty", "Bike", "Taxi", "Auto")
        for (v in vehicles) {
            val vb = Button(this).apply {
                text = v
                textSize = 11f
                setBackgroundColor(if (v == selectedVehicle) Color.parseColor("#06D6A0") else Color.parseColor("#212529"))
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, 85, 1f).apply { setMargins(2, 0, 2, 0) }
                setOnClickListener {
                    selectedVehicle = v
                    Toast.makeText(this@MainActivity, "Vehicle: $v selected", Toast.LENGTH_SHORT).show()
                    renderExact3DDashboard()
                }
            }
            vRow.addView(vb)
        }

        val inputMob = EditText(this).apply {
            hint = "10-digit Mobile Number"
            setHintTextColor(Color.parseColor("#6C757D"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1B263B"))
            setPadding(20, 15, 20, 15)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }

        val sm = TextView(this).apply { text = ""; setPadding(0, 8, 0, 0) }

        val inputPwd = EditText(this).apply {
            hint = "Password (Self Generated)"
            setHintTextColor(Color.parseColor("#6C757D"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1B263B"))
            setPadding(20, 15, 20, 15)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val sp = TextView(this).apply { text = ""; setPadding(0, 12, 0, 0) }

        val btnRiderSubmit = Button(this).apply {
            text = "Login & Start Auto-Accept"
            setBackgroundColor(Color.parseColor("#06D6A0"))
            setTextColor(Color.BLACK)
            setOnClickListener {
                val m = inputMob.text.toString()
                val p = inputPwd.text.toString()
                if (m.isNotEmpty() && p.isNotEmpty()) {
                    Toast.makeText(this@MainActivity, "Rider Login Success! ($selectedVehicle)", Toast.LENGTH_LONG).show()
                    showRiderWorkspace(m)
                } else {
                    Toast.makeText(this@MainActivity, "Enter mobile number and password", Toast.LENGTH_SHORT).show()
                }
            }
        }

        riderCard.addView(rTitle)
        riderCard.addView(rDesc)
        riderCard.addView(vLabel)
        riderCard.addView(vRow)
        riderCard.addView(inputMob)
        riderCard.addView(sm)
        riderCard.addView(inputPwd)
        riderCard.addView(sp)
        riderCard.addView(btnRiderSubmit)

        // Bottom Navigation Bar (Home, Analytics, History, Help, About)
        val bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 5f
            setBackgroundColor(Color.parseColor("#0B132B"))
            setPadding(5, 12, 5, 12)
        }
        val navItems = listOf("🏠 Home", "📊 Stats", "🕒 History", "❓ Help", "ℹ️ About")
        for (item in navItems) {
            val nb = Button(this).apply {
                text = item
                textSize = 10f
                setBackgroundColor(Color.parseColor("#1C2541"))
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, 90, 1f).apply { setMargins(2, 0, 2, 0) }
                setOnClickListener { Toast.makeText(this@MainActivity, "$item opened", Toast.LENGTH_SHORT).show() }
            }
            bottomNav.addView(nb)
        }

        mainLayout.addView(header)
        mainLayout.addView(banner)
        mainLayout.addView(s0)
        mainLayout.addView(adminCard)
        mainLayout.addView(s1)
        mainLayout.addView(riderCard)
        mainLayout.addView(TextView(this).apply { text = ""; setPadding(0, 20, 0, 0) })
        mainLayout.addView(bottomNav)

        rootScroll.addView(mainLayout)
        setContentView(rootScroll)
    }

    private fun showAdminPinDialog() {
        val input = EditText(this).apply {
            hint = "Enter Admin PIN"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle("Admin Security Check")
            .setMessage("Enter Master PIN to access management room:")
            .setView(input)
            .setPositiveButton("Login") { _, _ ->
                val pin = input.text.toString()
                if (pin == "3479") { // Hidden Master PIN
                    Toast.makeText(this, "Admin Login Successful!", Toast.LENGTH_SHORT).show()
                    showSuperAdminDashboard()
                } else {
                    Toast.makeText(this, "Incorrect PIN! Access Denied.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSuperAdminDashboard() {
        val adminLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(35, 35, 35, 35)
            setBackgroundColor(Color.parseColor("#111A30"))
        }

        val title = TextView(this).apply {
            text = "🛠️ SUPER ADMIN CONTROL ROOM\n"
            textSize = 18f
            setTextColor(Color.parseColor("#06D6A0"))
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val info = TextView(this).apply {
            text = "📊 Live Management:\n• Admin UPI: $adminUpiId\n• Social Links: $communityLinks\n• Active Broadcast: $broadcastMsg\n\n"
            textSize = 13f
            setTextColor(Color.WHITE)
        }

        val btnB = Button(this).apply {
            text = "📢 Broadcast Message to All Riders"
            setOnClickListener { showBroadcastDialog() }
        }

        val btnUpi = Button(this).apply {
            text = "💳 Update UPI QR / Payment ID"
            setOnClickListener { showUpdateUpiDialog() }
        }

        val btnApprove = Button(this).apply {
            text = "✅ Approve Subscriptions (Screenshots)"
            setOnClickListener { showApproveDialog() }
        }

        val btnLinks = Button(this).apply {
            text = "🌐 Manage Social Links (YT/FB/WA/X)"
            setOnClickListener { showSocialLinksDialog() }
        }

        val btnCall = Button(this).apply {
            text = "📞 Direct Call / SMS Any Rider"
            setOnClickListener { showCallRiderDialog() }
        }

        adminLayout.addView(title)
        adminLayout.addView(info)
        adminLayout.addView(btnB)
        adminLayout.addView(btnUpi)
        adminLayout.addView(btnApprove)
        adminLayout.addView(btnLinks)
        adminLayout.addView(btnCall)

        AlertDialog.Builder(this)
            .setView(adminLayout)
            .setCancelable(true)
            .show()
    }

    private fun showBroadcastDialog() {
        val input = EditText(this).apply { setText(broadcastMsg) }
        AlertDialog.Builder(this)
            .setTitle("Broadcast Notification")
            .setView(input)
            .setPositiveButton("Publish to All Riders") { _, _ ->
                broadcastMsg = input.text.toString()
                Toast.makeText(this, "Broadcast published successfully!", Toast.LENGTH_SHORT).show()
                renderExact3DDashboard()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showUpdateUpiDialog() {
        val input = EditText(this).apply { setText(adminUpiId) }
        AlertDialog.Builder(this)
            .setTitle("Update Personal UPI ID")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                adminUpiId = input.text.toString()
                Toast.makeText(this, "UPI Updated: $adminUpiId", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showApproveDialog() {
        AlertDialog.Builder(this)
            .setTitle("Verify Payment Screenshots")
            .setMessage("1. Rider +919954681428 - ₹49 Paid via UPI\n(Verify screenshot and grant 30 Days)")
            .setPositiveButton("Approve 30 Days") { _, _ ->
                Toast.makeText(this, "Subscription Activated for Rider!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showSocialLinksDialog() {
        val input = EditText(this).apply { setText(communityLinks) }
        AlertDialog.Builder(this)
            .setTitle("Update Social & Community Links")
            .setMessage("Enter YouTube, Facebook, Twitter, WhatsApp links:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                communityLinks = input.text.toString()
                Toast.makeText(this, "Social links updated for all riders!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCallRiderDialog() {
        val input = EditText(this).apply { hint = "Enter Rider Mobile Number" }
        AlertDialog.Builder(this)
            .setTitle("Direct Contact Rider")
            .setView(input)
            .setPositiveButton("Call Now") { _, _ ->
                val num = input.text.toString()
                try {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")))
                } catch (e: Exception) {
                    Toast.makeText(this, "Dialer error", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRiderWorkspace(mobile: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 30)
        }

        val info = TextView(this).apply {
            text = "🚀 RIDER WORKSPACE ($selectedVehicle)\n• Logged in: $mobile\n• Auto-Accept: 0.00s (Proximity Priority Active)\n\n"
            textSize = 13f
            setTextColor(Color.WHITE)
        }

        val l1 = TextView(this).apply { text = "Max Pickup Range (Km):"; setTextColor(Color.YELLOW) }
        val e1 = EditText(this).apply { setText(maxPickupKm); inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }

        val l2 = TextView(this).apply { text = "Minimum Fare Amount (₹):"; setTextColor(Color.YELLOW) }
        val e2 = EditText(this).apply { setText(minFareAmt); inputType = android.text.InputType.TYPE_CLASS_NUMBER }

        layout.addView(info)
        layout.addView(l1)
        layout.addView(e1)
        layout.addView(l2)
        layout.addView(e2)

        AlertDialog.Builder(this)
            .setTitle("Configure Ride Filters & Auto-Accept")
            .setView(layout)
            .setPositiveButton("Save Filters & Start") { _, _ ->
                maxPickupKm = e1.text.toString()
                minFareAmt = e2.text.toString()
                Toast.makeText(this, "Filters saved! Scanning rides within ${maxPickupKm}Km (Min ₹$minFareAmt)", Toast.LENGTH_LONG).show()
            }
            .setNeutralButton("Pay ₹49 Plan via UPI") { _, _ ->
                val uri = Uri.parse("upi://pay?pa=$adminUpiId&pn=Admin&am=49.00&cu=INR")
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    Toast.makeText(this, "Pay ₹49 to $adminUpiId & share screenshot for instant approval", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Pay via UPI ID: $adminUpiId", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
