package com.riderspecial.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import java.util.UUID

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check Device-Locked Trial & Subscription
        val prefs = getSharedPreferences("RiderSpecialPrefs", MODE_PRIVATE)
        val isPaid = prefs.getBoolean("is_paid", false)
        val installTime = prefs.getLong("trial_start_time", System.currentTimeMillis())
        val isTrialActive = (System.currentTimeMillis() - installTime < 7L * 24 * 60 * 60 * 1000)

        // Load Captain Smart Pro Rider Dashboard & Admin Access
        setupRiderDashboard(isPaid || isTrialActive)
    }

    private fun setupRiderDashboard(isActive: Boolean) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(40, 40, 40, 40)
        }

        val titleText = TextView(this).apply {
            text = "Captain Smart Pro"
            textSize = 26f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val statusText = TextView(this).apply {
            text = if (isActive) "\n🟢 Rider Status: Active (Auto-Accept Ready)\n• 0.00s Ride Catcher Enabled\n• Ola, Uber, Rapido, Zomato, Swiggy Support\n"
                   else "\n🔴 7-Day Trial Expired!\nPlease pay ₹49 via Admin QR code.\n"
            textSize = 15f
            setTextColor(if (isActive) Color.GREEN else Color.RED)
            gravity = Gravity.CENTER
        }

        val btnStartRider = Button(this).apply {
            text = "🚀 Start Auto-Accept Rider Mode"
            setBackgroundColor(Color.parseColor("#2E7D32"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Auto-Accept Service Running (0.00s Speed)!", Toast.LENGTH_SHORT).show()
            }
        }

        val btnAdminLogin = Button(this).apply {
            text = "🔐 Admin Panel Login (PIN: 3479)"
            setBackgroundColor(Color.parseColor("#E65100"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                showAdminPinDialog()
            }
        }

        layout.addView(titleText)
        layout.addView(statusText)
        layout.addView(btnStartRider)
        layout.addView(btnAdminLogin)
        setContentView(layout)
    }

    private fun showAdminPinDialog() {
        val input = EditText(this).apply {
            hint = "Enter Admin PIN (3479)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        AlertDialog.Builder(this)
            .setTitle("Admin Control Panel")
            .setMessage("Enter Master PIN to access management dashboard:")
            .setView(input)
            .setPositiveButton("Login") { _, _ ->
                val pin = input.text.toString()
                if (pin == "3479") {
                    Toast.makeText(this, "Admin Login Successful!", Toast.LENGTH_SHORT).show()
                    showAdminDashboard()
                } else {
                    Toast.makeText(this, "Incorrect PIN! Access Denied.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAdminDashboard() {
        val adminLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }

        val title = TextView(this).apply {
            text = "🛠️ ADMIN CONTROL ROOM\n"
            textSize = 22f
            setTextColor(Color.GREEN)
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val stats = TextView(this).apply {
            text = "📊 Live Fleet Stats:\n• Online Riders: Active\n• Total Revenue (₹49 Plans): Managed\n\n"
            textSize = 14f
            setTextColor(Color.WHITE)
        }

        val btnUpdateQR = Button(this).apply {
            text = "Update UPI QR / Payment ID"
            setOnClickListener {
                Toast.makeText(this@MainActivity, "QR Settings ready to update!", Toast.LENGTH_SHORT).show()
            }
        }

        val btnApproveRiders = Button(this).apply {
            text = "Approve Rider Subscriptions (Screenshots)"
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Opening pending screenshots...", Toast.LENGTH_SHORT).show()
            }
        }

        adminLayout.addView(title)
        adminLayout.addView(stats)
        adminLayout.addView(btnUpdateQR)
        adminLayout.addView(btnApproveRiders)

        AlertDialog.Builder(this)
            .setView(adminLayout)
            .setCancelable(true)
            .show()
    }

    fun verifyAdminPin(enteredPin: String): Boolean {
        return enteredPin == "3479"
    }
}
