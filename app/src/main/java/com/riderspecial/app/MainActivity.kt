package com.riderspecial.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Captain Smart Pro - Direct Action Engine
        Toast.makeText(this, "Captain Smart Pro: 0.00s Auto-Accept Active!", Toast.LENGTH_LONG).show()

        // Admin Security Check via Quick Prompt
        showAdminPrompt()
    }

    private fun showAdminPrompt() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Captain Smart Pro - Security")
        builder.setMessage("Enter Master PIN (dinakeot1990) to access Admin Control Room:")

        val input = android.widget.EditText(this)
        input.hint = "Enter PIN"
        builder.setView(input)

        builder.setPositiveButton("LOGIN") { dialog, _ ->
            val pin = input.text.toString().trim()
            if (pin == "dinakeot1990") {
                Toast.makeText(this, "Super Admin Control Room Unlocked!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Invalid Master PIN!", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        
        builder.setNegativeButton("CANCEL") { dialog, _ ->
            dialog.cancel()
        }
        
        builder.show()
    }
}
