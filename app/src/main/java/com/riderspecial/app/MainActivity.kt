package com.riderspecial.app

import android.app.Activity
import android.os.Bundle

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun verifyAdminPin(enteredPin: String): Boolean {
        return enteredPin == "3479"
    }
}
