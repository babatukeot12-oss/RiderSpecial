package com.riderspecial.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val upiId = "9954681428@naviaxis"
    private val planAmount = "49"
    private val trialDays = 3L
    private val subscriptionDays = 30L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ctx: Context = this
        val deviceId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown_device"
        val nowMillis = System.currentTimeMillis()

        checkOnlineSubscriptionState(ctx, deviceId, nowMillis) { trialStartMillis, activeUntilMillis ->
            val effectiveTrialStart = if (trialStartMillis > 0L) trialStartMillis else nowMillis
            val trialEndMillis = effectiveTrialStart + TimeUnit.DAYS.toMillis(trialDays)

            val hasActivePaidPlan = nowMillis < activeUntilMillis
            val isInFreeTrial = !hasActivePaidPlan && (nowMillis < trialEndMillis)

            renderAppScreen(
                ctx = ctx,
                deviceId = deviceId,
                nowMillis = nowMillis,
                trialEndMillis = trialEndMillis,
                activeUntilMillis = activeUntilMillis,
                hasActivePaidPlan = hasActivePaidPlan,
                isInFreeTrial = isInFreeTrial
            )
        }
    }

    private fun checkOnlineSubscriptionState(
        ctx: Context,
        deviceId: String,
        nowMillis: Long,
        onResult: (trialStartMillis: Long, activeUntilMillis: Long) -> Unit
    ) {
        val prefs = ctx.getSharedPreferences("captain_smart_pro_cache", Context.MODE_PRIVATE)
        var trialStart = prefs.getLong("trial_start_$deviceId", 0L)
        if (trialStart == 0L) {
            trialStart = nowMillis
            prefs.edit().putLong("trial_start_$deviceId", trialStart).apply()
        }
        val activeUntil = prefs.getLong("active_until_$deviceId", 0L)
        onResult(trialStart, activeUntil)
    }

    private fun renderAppScreen(
        ctx: Context,
        deviceId: String,
        nowMillis: Long,
        trialEndMillis: Long,
        activeUntilMillis: Long,
        hasActivePaidPlan: Boolean,
        isInFreeTrial: Boolean
    ) {
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }

        val titleView = TextView(ctx).apply {
            text = "Captain Smart Pro"
            textSize = 24f
        }

        val infoText = when {
            hasActivePaidPlan -> {
                val daysLeft = TimeUnit.MILLISECONDS.toDays(activeUntilMillis - nowMillis).coerceAtLeast(0L)
                "Status: ACTIVE ($subscriptionDays Days Plan | Days Left: $daysLeft)\nDevice ID: $deviceId"
            }
            isInFreeTrial -> {
                val hoursLeft = TimeUnit.MILLISECONDS.toHours(trialEndMillis - nowMillis).coerceAtLeast(0L)
                "Status: FREE TRIAL ($trialDays Days | Hours Left: $hoursLeft)\nDevice ID: $deviceId"
            }
            else -> {
                "Status: LOCKED ($trialDays-Day Trial Ended)\nPay ₹$planAmount for $subscriptionDays Days to: $upiId\nDevice ID: $deviceId"
            }
        }

        val statusView = TextView(ctx).apply {
            text = infoText
            textSize = 16f
            setPadding(0, 24, 0, 32)
        }

        val payButton = Button(ctx).apply {
            text = "Pay ₹$planAmount for $subscriptionDays Days ($upiId)"
            setOnClickListener {
                val uri = Uri.parse(
                    "upi://pay?pa=$upiId&pn=Captain%20Smart%20Pro&am=$planAmount&cu=INR&tn=Captain%20Smart%20Pro%2030%20Days"
                )
                val intent = Intent(Intent.ACTION_VIEW, uri)
                val chooser = Intent.createChooser(intent, "Choose UPI App")
                if (intent.resolveActivity(ctx.packageManager) != null) {
                    ctx.startActivity(chooser)
                } else {
                    Toast.makeText(
                        ctx,
                        "No UPI app found. Pay ₹$planAmount to $upiId",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        layout.addView(titleView)
        layout.addView(statusView)
        if (!hasActivePaidPlan && !isInFreeTrial) {
            layout.addView(payButton)
        }

        setContentView(layout)
    }
}
