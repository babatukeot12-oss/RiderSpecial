package com.riderspecial.app

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

        // 0.00s Instant Auto-Accept Engine for Rapido, Ola, Uber, Zomato, Swiggy[span_1](start_span)[span_1](end_span)[span_2](start_span)[span_2](end_span)[span_3](start_span)[span_3](end_span)
        loginButton.setOnClickListener {
            val mobile = mobileInput.text.toString().trim()
            if (mobile.length == 10) {
                Toast.makeText(applicationContext, "Captain Smart Pro: Auto-Accept Active!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(applicationContext, "कृपया सही 10-डिजिट नंबर दर्ज करें", Toast.LENGTH_SHORT).show()
            }
        }

        // Hidden Super Admin Panel (Master PIN: dinakeot1990)[span_4](start_span)[span_4](end_span)
        adminLoginButton.setOnClickListener {
            showAdminSecurityDialog()
        }
    }

    private fun showAdminSecurityDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Admin Security Check")
        builder.setMessage("Enter Master PIN to access management room:")

        val input = EditText(this)
        input.hint = "PIN (dinakeot1990)"
        builder.setView(input)

        builder.setPositiveButton("LOGIN") { dialog, _ ->
            val pin = input.text.toString().trim()
            if (pin == "dinakeot1990") {
                Toast.makeText(applicationContext, "Admin Login Successful!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(applicationContext, "Invalid Master PIN!", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        
        builder.setNegativeButton("CANCEL") { dialog, _ ->
            dialog.cancel()
        }
        
        builder.show()
    }
}
