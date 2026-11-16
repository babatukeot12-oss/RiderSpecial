package com.riderspecial.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Programmatic Layout to completely avoid XML missing/layout reference errors
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

        val subText = TextView(this).apply {
            text = "\nWelcome Rider!\n\nApp is running successfully.\nAdmin PIN: 3479"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        layout.addView(titleText)
        layout.addView(subText)
        setContentView(layout)
    }

    fun verifyAdminPin(enteredPin: String): Boolean {
        return enteredPin == "3479"
    }
}
