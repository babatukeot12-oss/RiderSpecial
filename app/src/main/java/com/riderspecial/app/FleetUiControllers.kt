package com.riderspecial.app

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import java.io.OutputStream

class VehicleSelectorController(
    rootView: View,
    private val onRiderProfileReady: (name: String, phone: String, vehicle: VehicleType) -> Unit
) {
    private val etName: EditText? = rootView.findViewById(R.id.etRiderName)
    private val etPhone: EditText? = rootView.findViewById(R.id.etRiderPhone)
    private val tvStatus: TextView? = rootView.findViewById(R.id.tvSelectedVehicleStatus)
    private val btnSave: Button? = rootView.findViewById(R.id.btnCreateRiderId)

    private val optionMap: Map<VehicleType, View?> = mapOf(
        VehicleType.BIKE to rootView.findViewById(R.id.btnVehicleBike),
        VehicleType.SCOOTY to rootView.findViewById(R.id.btnVehicleScooty),
        VehicleType.EV_SCOOTY to rootView.findViewById(R.id.btnVehicleEvScooty),
        VehicleType.AUTO to rootView.findViewById(R.id.btnVehicleAuto),
        VehicleType.ERIKSHA to rootView.findViewById(R.id.btnVehicleEriksha),
        VehicleType.TAXI to rootView.findViewById(R.id.btnVehicleTaxi),
        VehicleType.CAR to rootView.findViewById(R.id.btnVehicleCar)
    )

    private var selectedVehicle: VehicleType? = null

    init {
        optionMap.forEach { (type, view) ->
            view?.setOnClickListener {
                selectVehicle(type)
            }
        }

        btnSave?.setOnClickListener {
            val name = etName?.text?.toString()?.trim().orEmpty()
            val phone = etPhone?.text?.toString()?.trim().orEmpty()
            val chosenVehicle = selectedVehicle

            if (name.isEmpty()) {
                etName?.error = "Enter name"
                Toast.makeText(rootView.context, "Please enter rider name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (phone.length < 10) {
                etPhone?.error = "Enter valid mobile number"
                Toast.makeText(rootView.context, "Please enter valid mobile number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (chosenVehicle == null) {
                Toast.makeText(
                    rootView.context,
                    "Please tap a vehicle icon (Bike, Scooty, EV Scooty, Auto, E-Rickshaw, Taxi, or Car)",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            onRiderProfileReady(name, phone, chosenVehicle)
        }
    }

    fun selectVehicle(type: VehicleType) {
        selectedVehicle = type
        optionMap.forEach { (vehicleType, view) ->
            view?.alpha = if (vehicleType == type) 1.0f else 0.55f
            view?.scaleX = if (vehicleType == type) 1.04f else 1.0f
            view?.scaleY = if (vehicleType == type) 1.04f else 1.0f
        }
        tvStatus?.text = "Selected Vehicle: ${type.label}"
    }
}

object CrashFreePhotoDialog {

    fun show(
        context: Context,
        riderName: String?,
        riderPhone: String?,
        bitmap: Bitmap?
    ) {
        try {
            val inflater = LayoutInflater.from(context)
            val dialogView = inflater.inflate(R.layout.dialog_rider_photo_safe, null, false)

            val tvTitle: TextView? = dialogView.findViewById(R.id.tvPhotoTitle)
            val ivPhoto: ImageView? = dialogView.findViewById(R.id.ivRiderPhotoPreview)
            val btnDownload: Button? = dialogView.findViewById(R.id.btnDownloadPhoto)
            val btnClose: Button? = dialogView.findViewById(R.id.btnClosePhoto)

            val safeName = riderName?.trim().takeUnless { it.isNullOrEmpty() } ?: "Rider"
            val safePhone = riderPhone?.trim().takeUnless { it.isNullOrEmpty() } ?: ""
            tvTitle?.text = if (safePhone.isNotEmpty()) {
                "Photo from $safeName ($safePhone)"
            } else {
                "Photo from $safeName"
            }

            if (bitmap != null && !bitmap.isRecycled) {
                ivPhoto?.setImageBitmap(bitmap)
            } else {
                ivPhoto?.setImageResource(android.R.drawable.ic_menu_gallery)
            }

            val dialog = AlertDialog.Builder(context)
                .setView(dialogView)
                .setCancelable(true)
                .create()

            btnDownload?.setOnClickListener {
                if (bitmap == null || bitmap.isRecycled) {
                    Toast.makeText(context, "No photo available to download", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val saved = saveBitmapToGallery(context, bitmap, "RiderPhoto_${System.currentTimeMillis()}.jpg")
                Toast.makeText(
                    context,
                    if (saved) "Photo saved to gallery" else "Could not save photo",
                    Toast.LENGTH_SHORT
                ).show()
            }

            btnClose?.setOnClickListener {
                try {
                    if (dialog.isShowing) {
                        dialog.dismiss()
                    }
                } catch (_: Exception) {
                }
            }

            dialog.show()
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to open photo right now", Toast.LENGTH_SHORT).show()
        }
    }

    fun decodeSafeBitmap(bytes: ByteArray?): Bitmap? {
        if (bytes == null || bytes.isEmpty()) return null
        return try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = 2
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (_: Exception) {
            null
        }
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String): Boolean {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CaptainSmartPro")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return false

            val outputStream: OutputStream? = resolver.openOutputStream(uri)
            outputStream.use { stream ->
                if (stream == null) return false
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)) return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}

fun bindAdminRiderCard(
    cardView: View,
    rider: RiderProfileData
) {
    val ivVehicle: ImageView? = cardView.findViewById(R.id.ivAdminRiderVehicleIcon)
    val tvNamePhone: TextView? = cardView.findViewById(R.id.tvAdminRiderNamePhone)
    val tvVehicleLabel: TextView? = cardView.findViewById(R.id.tvAdminRiderVehicleLabel)
    val tvStatusLoc: TextView? = cardView.findViewById(R.id.tvAdminRiderStatusAndLoc)
    val btnMap: Button? = cardView.findViewById(R.id.btnOpenGoogleMap)
    val btnCall: Button? = cardView.findViewById(R.id.btnQuickCall)
    val btnWhatsApp: Button? = cardView.findViewById(R.id.btnQuickWhatsApp)

    val safeName = rider.name.trim().ifEmpty { "Unknown Rider" }
    val safePhone = rider.phone.trim().ifEmpty { "No Number" }
    val vehicleLabel = VehicleType.safeLabel(rider.vehicleTypeKey)
    val vehicleIcon = VehicleType.safeIcon(rider.vehicleTypeKey)
    val onlineText = if (rider.isOnline) "ONLINE" else "OFFLINE"
    val locText = RiderActionHelper.locationFreshnessText(
        lat = rider.latitude,
        lng = rider.longitude,
        isSharing = rider.isSharingLocation,
        updatedAt = rider.lastLocationUpdatedAt
    )

    ivVehicle?.setImageResource(vehicleIcon)
    tvNamePhone?.text = "$safeName ($safePhone)"
    tvVehicleLabel?.text = "Vehicle: $vehicleLabel"
    tvStatusLoc?.text = "Status: $onlineText • $locText"

    btnMap?.setOnClickListener {
        RiderActionHelper.openRiderInGoogleMap(cardView.context, rider)
    }

    btnCall?.setOnClickListener {
        RiderActionHelper.callRider(cardView.context, rider.phone)
    }

    btnWhatsApp?.setOnClickListener {
        RiderActionHelper.openWhatsAppRider(cardView.context, rider.phone)
    }
}
