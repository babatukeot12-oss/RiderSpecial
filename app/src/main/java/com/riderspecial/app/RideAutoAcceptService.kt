package com.riderspecial.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.regex.Pattern

class RideAutoAcceptService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val prefs = getSharedPreferences("CaptainSmartPrefs", Context.MODE_PRIVATE)
        val isAutoEnabled = prefs.getBoolean("auto_accept_enabled", false)
        val expiryMillis = prefs.getLong("sub_expiry", 0L)

        // केवल सक्रिय सब्सक्रिप्शन या 24-घंटे डेमो में ही काम करेगा
        if (!isAutoEnabled || System.currentTimeMillis() > expiryMillis) return

        val rootNode = rootInActiveWindow ?: return
        val pkg = event?.packageName?.toString() ?: return

        val enabledApps = setOf(
            "com.rapido.rider" to prefs.getBoolean("app_rapido", true),
            "com.olacabs.oladriver" to prefs.getBoolean("app_ola", true),
            "com.ubercab.driver" to prefs.getBoolean("app_uber", true),
            "com.zomato.delivery" to prefs.getBoolean("app_zomato", true),
            "in.swiggy.deliveryapp" to prefs.getBoolean("app_swiggy", true)
        ).filter { it.second }.map { it.first }

        if (pkg !in enabledApps) return

        val allTexts = mutableListOf<String>()
        collectTexts(rootNode, allTexts)
        val joined = allTexts.joinToString(" ")

        // स्क्रीन से किराया (₹) और दूरी (km) निकालें
        val fare = extractNumber(joined, "(?:₹|Rs\\.?|INR)\\s*(\\d+(?:\\.\\d+)?)")
        val distance = extractNumber(joined, "(\\d+(?:\\.\\d+)?)\\s*(?:km|KM|Km)")

        val minFare = prefs.getFloat("min_fare", 30f)
        val maxDist = prefs.getFloat("max_dist", 10f)
        val minPerKm = prefs.getFloat("min_per_km", 8f)

        val fareOk = fare == null || fare >= minFare
        val distOk = distance == null || distance <= maxDist
        val rateOk = (fare == null || distance == null || distance == 0f) || ((fare / distance) >= minPerKm)

        if (fareOk && distOk && rateOk) {
            clickAcceptButton(rootNode)
        }
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.let { if (it.isNotBlank()) list.add(it) }
        node.contentDescription?.toString()?.let { if (it.isNotBlank()) list.add(it) }
        for (i in 0 until node.childCount) {
            collectTexts(node.getChild(i), list)
        }
    }

    private fun extractNumber(text: String, regex: String): Float? {
        val matcher = Pattern.compile(regex).matcher(text)
        return if (matcher.find()) matcher.group(1)?.toFloatOrNull() else null
    }

    private fun clickAcceptButton(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val label = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").lowercase()
        val keywords = listOf("accept", "accept order", "accept ride", "go online", "confirm", "swipe to accept")
        if (keywords.any { label == it || label.contains("accept") }) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
                parent = parent.parent
            }
        }
        for (i in 0 until node.childCount) {
            if (clickAcceptButton(node.getChild(i))) return true
        }
        return false
    }

    override fun onInterrupt() {}
}
