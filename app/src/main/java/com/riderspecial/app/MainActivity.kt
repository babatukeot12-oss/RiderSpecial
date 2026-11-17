package com.riderspecial.app

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Captain Smart Pro - Master Panel Launcher
        showAdminSecurityDialog()
    }

    private fun showAdminSecurityDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Captain Smart Pro")
        builder.setMessage("Enter Master PIN (dinakeot1990) to access Admin Control Room:")

        val input = android.widget.EditText(this)
        input.hint = "Enter PIN"
        builder.setView(input)

        builder.setPositiveButton("LOGIN") { dialog, _ ->
            val pin = input.text.toString().trim()
            if (pin == "dinakeot1990") {
                val alert = AlertDialog.Builder(this)
                alert.setTitle("Success")
                alert.setMessage("Admin Login Successful!")
                alert.setPositiveButton("OK") { d, _ -> d.dismiss() }
                alert.show()
            } else {
                val alert = AlertDialog.Builder(this)
                alert.setTitle("Error")
                alert.setMessage("Invalid Master PIN!")
                alert.setPositiveButton("OK") { d, _ -> d.dismiss() }
                alert.show()
            }
            dialog.dismiss()
        }
        
        builder.setNegativeButton("CANCEL") { dialog, _ ->
            dialog.cancel()
        }
        
        builder.show()
    }
}
