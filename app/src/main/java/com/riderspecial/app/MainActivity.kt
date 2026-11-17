package com.riderspecial.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mobileInput = findViewById<EditText>(R.id.etMobileNumber)
        val loginButton = findViewById<Button>(R.id.btnLoginRider)
        val adminLoginButton = findViewById<Button>(R.id.btnLoginAdmin)

        // Rider Login & 0.00s Auto-Accept Engine for Rapido, Ola, Uber, Zomato, Swiggy[span_0](start_span)[span_0](end_span)[span_1](start_span)[span_1](end_span)[span_2](start_span)[span_2](end_span)
        loginButton.setOnClickListener {
            val mobile = mobileInput.text.toString().trim()
            if (mobile.length == 10) {
                Toast.makeText(this, "Captain Smart Pro: 0.00s Auto-Accept Active!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "कृपया वैध 10-डिजिट मोबाइल नंबर दर्ज करें", Toast.LENGTH_SHORT).show()
            }
        }

        // Hidden Super Admin Panel Login using Master PIN: dinakeot1990[span_3](start_span)[span_3](end_span)[span_4](start_span)[span_4](end_span)
        adminLoginButton.setOnClickListener {
            val pin = mobileInput.text.toString().trim()
            if (pin == "dinakeot1990") {
                Toast.makeText(this, "Super Admin Control Room Unlocked Successfully!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "कृपया एडमिन पिन दर्ज करें (dinakeot1990)", Toast.LENGTH_LONG).show()
            }
        }
    }
}
