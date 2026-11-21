package com.riderspecial.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes

enum class VehicleType(
    val key: String,
    val label: String,
    @DrawableRes val iconRes: Int
) {
    BIKE("bike", "Bike", android.R.drawable.ic_menu_directions),
    SCOOTY("scooty", "Scooty", android.R.drawable.ic_menu_compass),
    EV_SCOOTY("ev_scooty", "EV Scooty", android.R.drawable.ic_menu_send),
    AUTO("auto", "Auto", android.R.drawable.ic_menu_mylocation),
    ERIKSHA("eriksha", "E-Rickshaw", android.R.drawable.ic_menu_mapmode),
    TAXI("taxi", "Taxi", android.R.drawable.ic_menu_agenda),
    CAR("car", "Car", android.R.drawable.ic_menu_manage);

    companion object {
        fun fromKey(rawKey: String?): VehicleType? {
            val normalized = rawKey?.trim()?.lowercase()?.replace(" ", "_")?.replace("-", "_")
            return entries.firstOrNull {
                it.key == normalized || it.label.lowercase() == rawKey?.trim()?.lowercase()
            }
        }

        fun safeLabel(rawKey: String?): String {
            return fromKey(rawKey)?.label ?: "Vehicle Not Set"
        }

        @DrawableRes
        fun safeIcon(rawKey: String?): Int {
            return fromKey(rawKey)?.iconRes ?: android.R.drawable.ic_menu_info_details
        }
    }
}

data class RiderProfileData(
    val name: String = "",
    val phone: String = "",
    val vehicleTypeKey: String? = null,
    val isOnline: Boolean = false,
    val isSharingLocation: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lastLocationUpdatedAt: Long = 0L,
    val photoUrl: String? = null
)

object RiderActionHelper {

    fun locationFreshnessText(lat: Double?, lng: Double?, isSharing: Boolean, updatedAt: Long): String {
        if (!isSharing || lat == null || lng == null || updatedAt <= 0L) {
            return "Loc: Not shared"
        }
        val diffMs = (System.currentTimeMillis() - updatedAt).coerceAtLeast(0L)
        val diffMinutes = diffMs / 60_000L
        return when {
            diffMinutes < 1L -> "Live Location (Just now)"
            diffMinutes < 10L -> "Location (${diffMinutes} min ago)"
            else -> "Stale Location (${diffMinutes} min ago)"
        }
    }

    fun openRiderInGoogleMap(context: Context, rider: RiderProfileData) {
        val lat = rider.latitude
        val lng = rider.longitude

        if (!rider.isSharingLocation || lat == null || lng == null || lat.isNaN() || lng.isNaN()) {
            Toast.makeText(
                context,
                "Rider location not available right now",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            val labelUri = Uri.encode("${rider.name.ifBlank { "Rider" }} (${VehicleType.safeLabel(rider.vehicleTypeKey)})")
            val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($labelUri)")
            val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                setPackage("com.google.android.apps.maps")
            }

            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
            } else {
                val browserUrl = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
                context.startActivity(Intent(Intent.ACTION_VIEW, browserUrl))
            }
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "Could not open Google Map right now",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun callRider(context: Context, phone: String?) {
        val cleanNumber = phone?.trim().orEmpty()
        if (cleanNumber.isEmpty()) {
            Toast.makeText(context, "Phone number not available", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber"))
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to start call", Toast.LENGTH_SHORT).show()
        }
    }

    fun openWhatsAppRider(context: Context, phone: String?) {
        val digitsOnly = phone?.filter { it.isDigit() }.orEmpty()
        if (digitsOnly.isEmpty()) {
            Toast.makeText(context, "WhatsApp number not available", Toast.LENGTH_SHORT).show()
            return
        }
        val formattedNumber = if (digitsOnly.length == 10) "91$digitsOnly" else digitsOnly
        try {
            val uri = Uri.parse("https://wa.me/$formattedNumber")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to open WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }
}
