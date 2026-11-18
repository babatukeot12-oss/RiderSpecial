package com.riderspecial.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    companion object {
        private const val PICK_IMAGES_REQUEST = 101
        private const val PICK_VIDEO_REQUEST = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderAIStudioHomeUI()
    }

    private fun renderAIStudioHomeUI() {
        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#040814"))
            isFillViewport = true
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(25, 30, 25, 30)
        }

        // Top Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        val crownTv = TextView(this).apply {
            text = "🎬"
            textSize = 28f
            gravity = Gravity.CENTER
        }
        val titleTv = TextView(this).apply {
            text = "AI Original Studio Pro"
            textSize = 22f
            setTextColor(Color.parseColor("#00FF66"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val subTitle = TextView(this).apply {
            text = "100% Free • Ad-Supported • Real-Life AI Studio"
            textSize = 11f
            setTextColor(Color.parseColor("#00CCFF"))
            gravity = Gravity.CENTER
        }
        header.addView(crownTv)
        header.addView(titleTv)
        header.addView(subTitle)

        // Ad Banner Notice
        val adNotice = TextView(this).apply {
            text = "📢 Free Access Powered by Smart Ads (Server Cost)"
            textSize = 10f
            setTextColor(Color.parseColor("#FFD166"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 15)
        }

        // Studio Mode Selection Buttons (Photo vs Video)
        val studioRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            setPadding(0, 0, 0, 15)
        }
        val btnPhotoStudio = Button(this).apply {
            text = "📷 PHOTO STUDIO"
            textSize = 12f
            setBackgroundColor(Color.parseColor("#1D3557"))
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, 120, 1f).apply { rightMargin = 6 }
            setOnClickListener { showPhotoStudioDialog() }
        }
        val btnVideoStudio = Button(this).apply {
            text = "🎥 VIDEO STUDIO"
            textSize = 12f
            setBackgroundColor(Color.parseColor("#7209B7"))
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, 120, 1f).apply { leftMargin = 6 }
            setOnClickListener { showVideoStudioDialog() }
        }
        studioRow.addView(btnPhotoStudio)
        studioRow.addView(btnVideoStudio)

        // Advanced Features Card (Face Swap & Auto-Dubbing)
        val advCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B132B"))
            setPadding(20, 20, 20, 20)
        }
        val advTitle = TextView(this).apply {
            text = "⚡ Advanced AI Power Tools"
            textSize = 14f
            setTextColor(Color.parseColor("#00FF66"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 10)
        }

        val btnFaceSwap = Button(this).apply {
            text = "🔄 Video Face Swap (Multi-Person)"
            textSize = 11f
            setBackgroundColor(Color.parseColor("#1B263B"))
            setTextColor(Color.WHITE)
            setPadding(10, 15, 10, 15)
            setOnClickListener { showFeatureDialog("Video Face Swap", "Upload any video to swap faces.", PICK_VIDEO_REQUEST) }
        }

        val s1 = TextView(this).apply { text = ""; setPadding(0, 8, 0, 0) }

        val btnAutoDub = Button(this).apply {
            text = "🗣️ English-to-Hindi Voice & Lip-Sync"
            textSize = 11f
            setBackgroundColor(Color.parseColor("#1B263B"))
            setTextColor(Color.WHITE)
            setPadding(10, 15, 10, 15)
            setOnClickListener { showFeatureDialog("Voice & Lip-Sync Dubbing", "Upload English video for Hindi dubbing.", PICK_VIDEO_REQUEST) }
        }

        advCard.addView(advTitle)
        advCard.addView(btnFaceSwap)
        advCard.addView(s1)
        advCard.addView(btnAutoDub)

        val s2 = TextView(this).apply { text = ""; setPadding(0, 15, 0, 0) }

        // Hindi Voice & Text Command Input Card
        val promptCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B132B"))
            setPadding(20, 20, 20, 20)
        }
        val pTitle = TextView(this).apply {
            text = "🎤 हिंदी वॉइस & टेक्स्ट कमांड बॉक्स"
            textSize = 14f
            setTextColor(Color.parseColor("#FFD166"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 10)
        }
        val inputCommand = EditText(this).apply {
            hint = "यहाँ अपना कमांड हिंदी में बोलें या टाइप करें..."
            setHintTextColor(Color.parseColor("#8D99AE"))
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1B263B"))
            setPadding(15, 15, 15, 15)
            textSize = 13f
        }
        val s3 = TextView(this).apply { text = ""; setPadding(0, 10, 0, 0) }
        val btnGenerateAll = Button(this).apply {
            text = "✨ GENERATE AI MASTERPIECE (FREE)"
            setBackgroundColor(Color.parseColor("#06D6A0"))
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(15, 20, 15, 20)
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Ad playing for server cost... Generating AI result!", Toast.LENGTH_LONG).show()
            }
        }

        promptCard.addView(pTitle)
        promptCard.addView(inputCommand)
        promptCard.addView(s3)
        promptCard.addView(btnGenerateAll)

        mainLayout.addView(header)
        mainLayout.addView(adNotice)
        mainLayout.addView(studioRow)
        mainLayout.addView(TextView(this).apply { text = ""; setPadding(0, 10, 0, 0) })
        mainLayout.addView(advCard)
        mainLayout.addView(s2)
        mainLayout.addView(promptCard)

        rootScroll.addView(mainLayout)
        setContentView(rootScroll)
    }

    private fun showPhotoStudioDialog() {
        AlertDialog.Builder(this)
            .setTitle("📷 Photo Studio (1 to 5 Images)")
            .setMessage("Upload up to 4-5 original photos or leave blank. Enter your Hindi prompt to generate real-life AI photos.")
            .setPositiveButton("OPEN UPLOADER") { _, _ -> openGallery("image/*", PICK_IMAGES_REQUEST) }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun showVideoStudioDialog() {
        AlertDialog.Builder(this)
            .setTitle("🎥 Long Video Studio")
            .setMessage("Stitch 4-5 photos or create from text prompt. Generates long, original 4K videos with Hindi voiceover.")
            .setPositiveButton("OPEN CREATOR") { _, _ -> openGallery("image/*", PICK_IMAGES_REQUEST) }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun showFeatureDialog(title: String, desc: String, requestCode: Int) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("$desc\n\n(Supported by rewarded ad to keep service free).")
            .setPositiveButton("START NOW") { _, _ -> openGallery("video/*", requestCode) }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun openGallery(type: String, requestCode: Int) {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, type)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        try {
            startActivityForResult(Intent.createChooser(intent, "Select Media"), requestCode)
        } catch (e: Exception) {
            val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                this.type = type
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            startActivityForResult(Intent.createChooser(fallbackIntent, "Select Media"), requestCode)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            Toast.makeText(this, "Media successfully selected! Processing AI command...", Toast.LENGTH_LONG).show()
        }
    }
}
