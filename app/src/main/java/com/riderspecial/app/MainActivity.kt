package com.riderspecial.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Captain Smart Pro - Direct UI Handler
        Toast.makeText(this, "Captain Smart Pro: 0.00s Auto-Accept Active!", Toast.LENGTH_LONG).show()
        
        showAdminSecurityDialog()
    }

    private fun showAdminSecurityDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Admin Security Check")
        builder.setMessage("Enter Master PIN (dinakeot1990) to access management room:")

        val input = android.widget.EditText(this)
        input.hint = "Enter PIN"
        builder.setView(input)

        builder.setPositiveButton("LOGIN") { dialog, _ ->
            val pin = input.text.toString().trim()
            if (pin == "dinakeot1990") {
                Toast.makeText(this, "Admin Login Successful!", Toast.LENGTH_LONG).show()
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
