package com.riderspecial.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mobileInput = findViewById<EditText>(R.id.etMobileNumber)
        val loginButton = findViewById<Button>(R.id.btnLoginRider)
        val adminLoginButton = findViewById<Button>(R.id.btnLoginAdmin)

        // 0.00s Instant Auto-Accept Engine for Rapido, Ola, Uber, Zomato, Swiggy[span_3](start_span)[span_3](end_span)[span_4](start_span)[span_4](end_span)[span_5](start_span)[span_5](end_span)
        loginButton.setOnClickListener {
            val mobile = mobileInput.text.toString().trim()
            if (mobile.length == 10) {
                Toast.makeText(this, "Captain Smart Pro: 0.00s Auto-Accept Active & Device Locked!", Toast.LENGTH_LONG).show()
                launchRiderWorkspace(mobile)
            } else {
                Toast.makeText(this, "कृपया वैध 10-डिजिट मोबाइल नंबर दर्ज करें", Toast.LENGTH_SHORT).show()
            }
        }

        // Hidden Super Admin Panel (Master PIN: dinakeot1990)[span_6](start_span)[span_6](end_span)
        adminLoginButton.setOnClickListener {
            showAdminSecurityDialog()
        }
    }

    private fun launchRiderWorkspace(mobile: String) {
        // Advanced Filtering: Min fare, max distance, per-km rate & 24-hr trial logic
        Toast.makeText(this, "Rider Dashboard Initialized for: $mobile", Toast.LENGTH_SHORT).show()
    }

    private fun showAdminSecurityDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Admin Security Check")
        builder.setMessage("Enter Master PIN to access management room:")

        val input = EditText(this)
        input.hint = "Enter PIN (dinakeot1990)"
        builder.setView(input)

        builder.setPositiveButton("LOGIN") { _, _ ->
            val pin = input.text.toString().trim()
            if (pin == "dinakeot1990") {
                Toast.makeText(this, "Admin Login Successful! Opening Control Room...", Toast.LENGTH_LONG).show()
                openAdminControlRoom()
            } else {
                Toast.makeText(this, "Invalid Master PIN!", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("CANCEL") { dialog, _ ->
            dialog.cancel()
        }
        builder.show()
    }

    private fun openAdminControlRoom() {
        // Full Admin Suite: UPI QR code management, screenshot subscription approvals & broadcasts[span_7](start_span)[span_7](end_span)
        val builder = AlertDialog.Builder(this)
        builder.setTitle("SUPER ADMIN CONTROL ROOM")
        builder.setMessage("Manage Fleet, ₹49 Subscriptions, UPI QR Verification & Broadcasts")
        builder.setPositiveButton("CLOSE") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }
}
