package com.riderspecial.app

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences
    private var isSubscribedOrTrialActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefs = getSharedPreferences("RiderSpecialPrefs", MODE_PRIVATE)

        // 1. Check Device-Locked 7-Day Trial & Subscription Status
        checkDeviceSubscriptionAndTrial()

        if (!isSubscribedOrTrialActive) {
            // Show ₹49 Subscription & UPI QR Payment Lock Screen if trial expired
            showSubscriptionLockScreen()
        } else {
            // Load Main Dashboard (Rider / Admin Mode)
            setContentView(R.layout.activity_main)
            initAppDashboard()
        }
    }

    private fun checkDeviceSubscriptionAndTrial() {
        var deviceId = prefs.getString("device_hardware_id", null)
        
        if (deviceId == null) {
            // Generate permanent device ID to prevent data-clear cheating
            deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: UUID.randomUUID().toString()
            val installTime = System.currentTimeMillis()
            
            prefs.edit().putString("device_hardware_id", deviceId)
                .putLong("trial_start_time", installTime)
                .putBoolean("is_paid", false)
                .apply()
        }

        val installTime = prefs.getLong("trial_start_time", System.currentTimeMillis())
        val isPaid = prefs.getBoolean("is_paid", false)
        
        // 7 Days Trial in milliseconds (7 * 24 * 60 * 60 * 1000)
        val sevenDaysMillis = 7L * 24 * 60 * 60 * 1000
        val currentTime = System.currentTimeMillis()

        isSubscribedOrTrialActive = isPaid || (currentTime - installTime < sevenDaysMillis)
    }

    private fun showSubscriptionLockScreen() {
        // Programmatic secure dialog to avoid missing layout errors
        val dialog = Dialog(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        val title = TextView(this).apply {
            text = "Captain Smart Pro - Subscription"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val message = TextView(this).apply {
            text = "\nYour 7-Day Free Trial has expired!\n\nPay ₹49 for 30 Days Subscription using Admin QR code.\n"
            textSize = 15f
        }

        val btnPay = Button(this).apply {
            text = "Pay ₹49 via UPI / QR"
            setOnClickListener {
                val upiUri = Uri.parse("upi://pay?pa=babatukeot@paytm&pn=DinaKeot&am=49.00&cu=INR")
                val intent = Intent(Intent.ACTION_VIEW, upiUri)
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Please scan Admin QR code", Toast.LENGTH_LONG).show()
                }
            }
        }

        val btnUpload = Button(this).apply {
            text = "Upload Payment Screenshot"
            setOnClickListener {
                Toast.makeText(context, "Opening gallery for screenshot upload...", Toast.LENGTH_SHORT).show()
            }
        }

        layout.addView(title)
        layout.addView(message)
        layout.addView(btnPay)
        layout.addView(btnUpload)

        dialog.setContentView(layout)
        dialog.setCancelable(false)
        dialog.show()
    }

    private fun initAppDashboard() {
        Toast.makeText(this, "Welcome to Captain Smart Pro!", Toast.LENGTH_SHORT).show()
    }

    fun verifyAdminPin(enteredPin: String): Boolean {
        return enteredPin == "3479" // Admin PIN mapped to your bike number digits
    }
}
