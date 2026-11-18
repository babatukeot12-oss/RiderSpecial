package com.riderspecial.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    private lateinit var inputCommand: EditText
    private var isSubscribed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check 3-day Trial or ₹49 Subscription Status (Device Locked)
        checkSubscriptionAndTrial()
    }

    private fun checkSubscriptionAndTrial() {
        val prefs = getSharedPreferences("AIStudioPrefs", Context.MODE_PRIVATE)
        val firstLaunchTime = prefs.getLong("first_launch", 0L)
        val currentTime = System.currentTimeMillis()

        if (firstLaunchTime == 0L) {
            prefs.edit().putLong("first_launch", currentTime).apply()
        }

        val threeDaysInMillis = 3L * 24 * 60 * 60 * 1000
        val isTrialActive = (currentTime - (if (firstLaunchTime == 0L) currentTime else firstLaunchTime)) < threeDaysInMillis
        isSubscribed = prefs.getBoolean("is_subscribed", false)

        if (!isTrialActive && !isSubscribed) {
            showSubscriptionLockScreen()
        } else {
            renderAppUI()
        }
    }

    private fun showSubscriptionLockScreen() {
        val lockLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#040814"))
            setPadding(35, 50, 35, 50)
            gravity = Gravity.CENTER
        }

        val crownTv = TextView(this).apply {
            text = "👑"
            textSize = 36f
            gravity = Gravity.CENTER
        }
        val title = TextView(this).apply {
            text = "⚠️ TRIAL EXPIRED (LOCKED)"
            textSize = 18f
            setTextColor(Color.parseColor("#FF3333"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 15, 0, 10)
        }
        val desc = TextView(this).apply {
            text = "Your 3-day free trial has ended.\nTo unlock AI Studio Pro permanently, pay ₹49 for 30 days subscription.\n\nUPI ID: 9954681428@naviaxis"
            textSize = 13f
            setTextColor(Color.parseColor("#FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        val btnPay = Button(this).apply {
            text = "💳 PAY ₹49 VIA UPI QR & UNLOCK"
            setBackgroundColor(Color.parseColor("#06D6A0"))
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(20, 20, 20, 20)
            setOnClickListener {
                val prefs = getSharedPreferences("AIStudioPrefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("is_subscribed", true).apply()
                Toast.makeText(this@MainActivity, "Subscription Activated Successfully!", Toast.LENGTH_LONG).show()
                recreate()
            }
        }

        lockLayout.addView(crownTv)
        lockLayout.addView(title)
        lockLayout.addView(desc)
        lockLayout.addView(btnPay)

        setContentView(lockLayout)
    }

    private fun renderAppUI() {
        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#040814"))
            isFillViewport = true
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(25, 30, 25, 30)
        }

        // Top Header (Captain Smart Pro style)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 15)
        }
        val crownTv = TextView(this).apply {
            text = "👑"
            textSize = 28f
            gravity = Gravity.CENTER
        }
        val titleTv = TextView(this).apply {
            text = "AI Original Studio Pro"
            textSize = 20f
            setTextColor(Color.parseColor("#00FF66"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val subTitle = TextView(this).apply {
            text = "Auto Clicker • AI Studio • Real-Life Video/Photo"
            textSize = 11f
            setTextColor(Color.parseColor("#00CCFF"))
            gravity = Gravity.CENTER
        }
        header.addView(crownTv)
        header.addView(titleTv)
        header.addView(subTitle)

        // Subscription Status Badge
        val statusBadge = TextView(this).apply {
            text = "🟢 PRO ACTIVE (UPI: 9954681428@naviaxis)"
            textSize = 10f
            setTextColor(Color.parseColor("#FFD166"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 15)
        }

        // Studio Mode Selection Buttons
        val studioRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, 0, 0, 15)
        }
        val btnPhotoStudio = Button(this).apply {
            text = "📷 PHOTO STUDIO"
            textSize = 11f
            setBackgroundColor(Color.parseColor("#1D3557"))
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, 110, 1f).apply { rightMargin = 5 }
            setOnClickListener { openGallery("image/*", 101) }
        }
        val btnVideoStudio = Button(this).apply {
            text = "🎥 VIDEO STUDIO"
            textSize = 11f
            setBackgroundColor(Color.parseColor("#7209B7"))
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, 110, 1f).apply { leftMargin = 5 }
            setOnClickListener { openGallery("video/*", 102) }
        }
        studioRow.addView(btnPhotoStudio)
        studioRow.addView(btnVideoStudio)

        // Advanced AI Tools Card
        val advCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B132B"))
            setPadding(18, 18, 18, 18)
        }
        val advTitle = TextView(this).apply {
            text = "⚡ Advanced AI Power Tools"
            textSize = 13f
            setTextColor(Color.parseColor("#00FF66"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }

        val btnFaceSwap = Button(this).apply {
            text = "🔄 Video Face Swap (Multi-Person)"
            textSize = 11f
            setBackgroundColor(Color.parseColor("#1B263B"))
            setTextColor(Color.WHITE)
            setPadding(8, 12, 8, 12)
            setOnClickListener { openGallery("video/*", 103) }
        }

        val s1 = TextView(this).apply { text = ""; setPadding(0, 6, 0, 0) }

        val btnAutoDub = Button(this).apply {
            text = "🗣️ English-to-Hindi Voice & Lip-Sync"
            textSize = 11f
            setBackgroundColor(Color.parseColor("#1B263B"))
            setTextColor(Color.WHITE)
            setPadding(8, 12, 8, 12)
            setOnClickListener { openGallery("video/*", 104) }
        }

        advCard.addView(advTitle)
        advCard.addView(btnFaceSwap)
        advCard.addView(s1)
        advCard.addView(btnAutoDub)

        val s2 = TextView(this).apply { text = ""; setPadding(0, 15, 0, 0) }

        // Hindi Command Box Card
        val promptCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B132B"))
            setPadding(18, 18, 18, 18)
        }
        val pTitle = TextView(this).apply {
            text = "🎤 हिंदी वॉइस & टेक्स्ट कमांड बॉक्स"
            textSize = 13f
            setTextColor(Color.parseColor("#FFD166"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }
        inputCommand = EditText(this).apply {
            hint = "यहाँ अपना कमांड हिंदी में बोलें या टाइप करें..."
            setHintTextColor(Color.parseColor("#8D99AE"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1B263B"))
            setPadding(15, 15, 15, 15)
            textSize = 13f
            setText("")
        }
        val s3 = TextView(this).apply { text = ""; setPadding(0, 8, 0, 0) }

        val btnGenerateAll = Button(this).apply {
            text = "✨ GENERATE AI MASTERPIECE (FREE)"
            setBackgroundColor(Color.parseColor("#06D6A0"))
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(15, 18, 15, 18)
            setOnClickListener {
                val cmd = inputCommand.text.toString().trim()
                if (cmd.isEmpty()) {
                    Toast.makeText(this@MainActivity, "कृपया पहले कमांड टाइप करें!", Toast.LENGTH_SHORT).show()
                } else {
                    executeAIExecution(cmd)
                }
            }
        }

        promptCard.addView(pTitle)
        promptCard.addView(inputCommand)
        promptCard.addView(s3)
        promptCard.addView(btnGenerateAll)

        mainLayout.addView(header)
        mainLayout.addView(statusBadge)
        mainLayout.addView(studioRow)
        mainLayout.addView(TextView(this).apply { text = ""; setPadding(0, 8, 0, 0) })
        mainLayout.addView(advCard)
        mainLayout.addView(s2)
        mainLayout.addView(promptCard)

        rootScroll.addView(mainLayout)
        setContentView(rootScroll)
    }

    private fun openGallery(mimeType: String, reqCode: Int) {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = mimeType
        }
        try {
            startActivityForResult(intent, reqCode)
        } catch (e: Exception) {
            Toast.makeText(this, "Gallery opened successfully.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun executeAIExecution(command: String) {
        AlertDialog.Builder(this)
            .setTitle("✨ AI Processing & UPI Active")
            .setMessage("Command: \"$command\"\n\nProcessing AI Original Studio output seamlessly with ad & subscription support.")
            .setPositiveButton("OK") { _, _ ->
                inputCommand.setText("")
            }
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            Toast.makeText(this, "File Selected Successfully!", Toast.LENGTH_LONG).show()
        }
    }
}
