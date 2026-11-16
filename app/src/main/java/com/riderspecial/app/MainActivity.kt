package com.riderspecial.app

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Device-locked trial initialization
        val prefs = getSharedPreferences("RiderSpecialPrefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_hardware_id", null)
        
        if (deviceId == null) {
            deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "device_id_123"
            prefs.edit().putString("device_hardware_id", deviceId)
                .putLong("trial_start_time", System.currentTimeMillis())
                .putBoolean("is_paid", false)
                .apply()
        }

        // Load Main App Dashboard
        setContentView(R.layout.activity_main)
        Toast.makeText(this, "Welcome to Captain Smart Pro!", Toast.LENGTH_SHORT).show()
    }
}
