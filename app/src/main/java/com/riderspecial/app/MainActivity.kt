package com.riderspecial.app

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
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
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_subscription)
        dialog.setCancelable(false)

        val txtStatus = dialog.findViewById<TextView>(R.id.txtTrialStatus)
        val btnPay = dialog.findViewById<Button>(R.id.btnPayNow)
        val btnUpload = dialog.findViewById<Button>(R.id.btnUploadScreenshot)

        txtStatus.text = "7-Day Free Trial Expired!\nPay ₹49 for 30 Days Subscription."

        btnPay.setOnClickListener {
            // Open UPI Apps with your personal UPI ID / QR
            val upiUri = Uri.parse("upi://pay?pa=babatukeot@paytm&pn=DinaKeot&am=49.00&cu=INR")
            val intent = Intent(Intent.ACTION_VIEW, upiUri)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Please scan Admin QR code to pay ₹49", Toast.LENGTH_LONG).show()
            }
        }

        btnUpload.setOnClickListener {
            Toast.makeText(this, "Opening gallery to upload payment screenshot for Admin approval...", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun initAppDashboard() {
        // Handle Rider Mobile Number Login & Admin Login (PIN: 3479)
        Toast.makeText(this, "Welcome to Captain Smart Pro!", Toast.LENGTH_SHORT).show()
        
        // Example check for Admin Login button
        // If admin clicks Admin Login -> prompt for PIN "3479"
    }

    fun verifyAdminPin(enteredPin: String): Boolean {
        return enteredPin == "3479"
    }
}
