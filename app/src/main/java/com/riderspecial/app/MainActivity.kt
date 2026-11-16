package com.riderspecial.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Check Device-Locked 7-Day Trial & Subscription Status
        val prefs = getSharedPreferences("RiderSpecialPrefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_hardware_id", null)
        
        if (deviceId == null) {
            deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "device_id_123"
            prefs.edit().putString("device_hardware_id", deviceId)
                .putLong("trial_start_time", System.currentTimeMillis())
                .putBoolean("is_paid", false)
                .apply()
        }

        val installTime = prefs.getLong("trial_start_time", System.currentTimeMillis())
        val isPaid = prefs.getBoolean("is_paid", false)
        val sevenDaysMillis = 7L * 24 * 60 * 60 * 1000
        val isTrialActive = (System.currentTimeMillis() - installTime < sevenDaysMillis)

        if (!isPaid && !isTrialActive) {
            // Trial Expired: Open UPI Payment Intent directly
            Toast.makeText(this, "7-Day Trial Expired! Please pay ₹49 via UPI.", Toast.LENGTH_LONG).show()
            val upiUri = Uri.parse("upi://pay?pa=babatukeot@paytm&pn=DinaKeot&am=49.00&cu=INR")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, upiUri))
            } catch (e: Exception) {
                // Ignore if UPI app not found
            }
        }

        // Load Main Dashboard UI
        setContentView(R.layout.activity_main)
        Toast.makeText(this, "Welcome to Captain Smart Pro!", Toast.LENGTH_SHORT).show()
    }

    fun verifyAdminPin(enteredPin: String): Boolean {
        return enteredPin == "3479" // Admin PIN mapped to your bike number digits
    }
}
