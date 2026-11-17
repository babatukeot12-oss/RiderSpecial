package com.riderspecial.app

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val currentContext = this
        Toast.makeText(currentContext, "Captain Smart Pro: Auto-Accept Active!", Toast.LENGTH_LONG).show()

        showAdminSecurityDialog()
    }

    private fun showAdminSecurityDialog() {
        val currentContext = this
        val builder = AlertDialog.Builder(currentContext)
        builder.setTitle("Admin Security Check")
        builder.setMessage("Enter Master PIN (dinakeot1990) to access management room:")

        val input = EditText(currentContext)
        input.hint = "Enter PIN"
        builder.setView(input)

        builder.setPositiveButton("LOGIN") { dialog, _ ->
            val pin = input.text.toString().trim()
            if (pin == "dinakeot1990") {
                Toast.makeText(currentContext, "Admin Login Successful!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(currentContext, "Invalid Master PIN!", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        
        builder.setNegativeButton("CANCEL") { dialog, _ ->
            dialog.cancel()
        }
        
        builder.show()
    }
}
