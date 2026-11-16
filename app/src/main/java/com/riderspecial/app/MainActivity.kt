package com.riderspecial.app

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as GColor
import android.graphics.Paint
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.OutputStream
import java.util.*

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF22C55E),
                    secondary = Color(0xFF38BDF8),
                    background = Color(0xFF020617),
                    surface = Color(0xFF0F172A),
                    onPrimary = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                CaptainSmartSolidApp(tts)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("en", "IN")
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

// Data class for Ride History
data class RideRecord(val company: String, val amount: String, val distance: String, val time: String)

// Function to save Trip Card Screenshot to Gallery
fun saveTripCardToGallery(context: Context, company: String, amount: String, distance: String) {
    try {
        val width = 1080
        val height = 720
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(GColor.parseColor("#052E16"))

        val paint = Paint().apply {
            color = GColor.WHITE
            isAntiAlias = true
        }

        paint.textSize = 55f
        paint.isFakeBoldText = true
        canvas.drawText("🎉 RIDE ACCEPTED ($company)", 60f, 100f, paint)

        paint.textSize = 45f
        paint.color = GColor.parseColor("#22C55E")
        canvas.drawText("Fare Amount: $amount", 60f, 250f, paint)
        canvas.drawText("Distance to Go: $distance", 60f, 380f, paint)

        paint.textSize = 35f
        paint.color = GColor.parseColor("#FACC15")
        canvas.drawText("⚡ Auto-Accepted by Captain Smart Pro", 60f, 550f, paint)

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Trip_${company}_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CaptainSmartPro")
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            val stream: OutputStream? = context.contentResolver.openOutputStream(it)
            stream?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
        }
        Toast.makeText(context, "📸 Screenshot Saved to Gallery!", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@SuppressLint("HardwareIds")
@Composable
fun CaptainSmartSolidApp(tts: TextToSpeech?) {
    val context = LocalContext.current
    
    // States
    var isActivated by remember { mutableStateOf(false) } // Activate / Deactivate Toggle
    var autoAcceptOn by remember { mutableStateOf(true) }
    var onlyRapidoOla by remember { mutableStateOf(true) }
    var playSound by remember { mutableStateOf(true) }
    var screenshotSave by remember { mutableStateOf(true) }

    var maxPickup by remember { mutableStateOf("1.0") }
    var minPrice by remember { mutableStateOf("20") }
    
    // Dynamic Stats (Start from 0)
    var totalEarnings by remember { mutableStateOf(0) }
    var totalRidesCount by remember { mutableStateOf(0) }
    var totalDistance by remember { mutableStateOf(0.0) }

    var lastRide by remember { mutableStateOf<RideRecord?>(null) }
    val rideHistoryList = remember { mutableStateListOf<RideRecord>() }

    var selectedTab by remember { mutableStateOf(0) } // 0: Home, 1: Analytics, 2: History, 3: Help, 4: About

    // Dialog states for popups
    var showStatsDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showOrderSplitDialog by remember { mutableStateOf(false) }
    var showProDialog by remember { mutableStateOf(false) }

    // Function to accept a ride (Strictly on demand / manual trigger or actual event)
    fun triggerRideAccept(company: String, amountStr: String, distStr: String) {
        val cleanAmount = amountStr.replace("₹", "").toIntOrNull() ?: 100
        val cleanDist = distStr.replace(" Km", "").toDoubleOrNull() ?: 1.0

        totalEarnings += cleanAmount
        totalRidesCount += 1
        totalDistance += cleanDist

        val newRecord = RideRecord(company, "₹$cleanAmount", "$cleanDist Km", "Just now")
        lastRide = newRecord
        rideHistoryList.add(0, newRecord)

        if (playSound) {
            val speech = "Ride Accepted! $company. Fare $cleanAmount rupees."
            tts?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, null)
        }

        if (screenshotSave) {
            saveTripCardToGallery(context, company, "₹$cleanAmount", "$cleanDist Km")
        }
        Toast.makeText(context, "🎉 Ride Captured Successfully!", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            Surface(color = Color(0xFF050B14), shadowElevation = 8.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("👑", fontSize = 24.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Captain Smart Pro", color = Color(0xFF22C55E), fontWeight = FontWeight.Black, fontSize = 18.sp)
                            Text("Auto Clicker • Ride Catcher • Order Split", color = Color.LightGray, fontSize = 10.sp)
                        }
                    }
                    // Interactive Activate / Deactivate Toggle Button
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { 
                                isActivated = !isActivated
                                Toast.makeText(context, if (isActivated) "⚡ Auto-Clicker ACTIVATED (Standby)" else "⏹ Auto-Clicker DEACTIVATED", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isActivated) Color(0xFF22C55E) else Color.Red),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(if (isActivated) "● ACTIVE" else "○ DEACTIVATED", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF050B14)) {
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, label = { Text("Home", color = Color.White, fontSize = 11.sp) }, icon = { Text("🏠", fontSize = 18.sp) })
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, label = { Text("Analytics", color = Color.White, fontSize = 11.sp) }, icon = { Text("📊", fontSize = 18.sp) })
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, label = { Text("History", color = Color.White, fontSize = 11.sp) }, icon = { Text("🕒", fontSize = 18.sp) })
                NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, label = { Text("Help", color = Color.White, fontSize = 11.sp) }, icon = { Text("❓", fontSize = 18.sp) })
                NavigationBarItem(selected = selectedTab == 4, onClick = { selectedTab = 4 }, label = { Text("About", color = Color.White, fontSize = 11.sp) }, icon = { Text("ℹ️", fontSize = 18.sp) })
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0A1128), Color(0xFF020617))))) {
            when (selectedTab) {
                0 -> {
                    // HOME SCREEN
                    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        
                        // Radar & Status Header
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B132B)),
                                border = BorderStroke(1.5.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                            ) {
                                Row(
                                    Modifier.padding(14.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("🟡 Rapido / Ola / Uber", color = Color(0xFFFACC15), fontWeight = FontWeight.Black, fontSize = 18.sp)
                                        Text("📍 Dibrugarh Fleet Dispatcher", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(4.dp))
                                        Text(if (isActivated) "0.00s Instant Catcher (Standby/Ready)" else "Paused (Deactivated)", color = if (isActivated) Color(0xFF22C55E) else Color.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Card(
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = if (autoAcceptOn) Color(0xFF16A34A) else Color.DarkGray)
                                    ) {
                                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("⚡", fontSize = 16.sp)
                                            Text("Auto Accept", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text(if (autoAcceptOn) "ON" else "OFF", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
                            }
                        }

                        // Test & Order Split Buttons
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        val comps = listOf("Rapido", "Ola", "Uber")
                                        val frs = listOf("125", "210", "320", "150")
                                        val dsts = listOf("1.1 Km", "2.5 Km", "0.9 Km")
                                        triggerRideAccept(comps.random(), frs.random(), dsts.random())
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8))
                                ) {
                                    Text("📸 Test Ride Accept & Save", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { showOrderSplitDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF581C87)),
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFFC084FC))
                                ) {
                                    Text("🔀 Order Split Form", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Last Accepted Ride Card (Only shows when triggered)
                        if (lastRide != null) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF052E16)),
                                    border = BorderStroke(2.dp, Color(0xFF22C55E))
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("✅", fontSize = 16.sp)
                                                Spacer(Modifier.width(6.dp))
                                                Text("Ride Accepted (${lastRide!!.company})", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }
                                            Button(
                                                onClick = {
                                                    saveTripCardToGallery(context, lastRide!!.company, lastRide!!.amount, lastRide!!.distance)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("📷 View Screenshot", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        
                                        Spacer(Modifier.height(12.dp))
                                        
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Fare Amount", color = Color.LightGray, fontSize = 11.sp)
                                                Text(lastRide!!.amount, color = Color(0xFFFACC15), fontWeight = FontWeight.Black, fontSize = 26.sp)
                                            }
                                            Divider(color = Color(0xFF22C55E).copy(alpha = 0.3f), modifier = Modifier.height(40.dp).width(1.dp))
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Distance to Go", color = Color.LightGray, fontSize = 11.sp)
                                                Text(lastRide!!.distance, color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                                            }
                                        }

                                        Spacer(Modifier.height(12.dp))

                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFFACC15)
                                        ) {
                                            Row(
                                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("🔊 Audio Alert Spoken: ${lastRide!!.amount} | ${lastRide!!.distance}", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text("▶ ılıll|l|ll|l|", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Quick Navigation Grid (Live Stats, History, Notifications, Settings)
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Card(
                                    onClick = { showStatsDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                                ) {
                                    Column(Modifier.padding(vertical = 12.dp, horizontal = 2.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("📊 Live Stats", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Card(
                                    onClick = { selectedTab = 2 },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                                ) {
                                    Column(Modifier.padding(vertical = 12.dp, horizontal = 2.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("🕒 History", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Card(
                                    onClick = { showNotificationsDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                                ) {
                                    Column(Modifier.padding(vertical = 12.dp, horizontal = 2.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("🔔 Notify", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Card(
                                    onClick = { showSettingsDialog = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                                ) {
                                    Column(Modifier.padding(vertical = 12.dp, horizontal = 2.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("⚙️ Settings", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Auto-Clicker Rules & Filters Card
                        item {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B132B)),
                                border = BorderStroke(1.5.dp, Color(0xFF22C55E).copy(alpha = 0.5f))
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("⚙️ Auto-Clicker Rules & Filters", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Surface(
                                            onClick = { showProDialog = true },
                                            shape = RoundedCornerShape(8.dp), 
                                            color = Color(0xFFFACC15)
                                        ) {
                                            Text(" Pro 👑 ", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(4.dp))
                                        }
                                    }

                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedTextField(
                                            value = maxPickup,
                                            onValueChange = { maxPickup = it },
                                            label = { Text("Max Pickup (Km)", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        OutlinedTextField(
                                            value = minPrice,
                                            onValueChange = { minPrice = it },
                                            label = { Text("Min Price (₹)", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }

                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("✔ Only Ola/Rapido", color = Color.White, fontSize = 12.sp)
                                        Switch(checked = onlyRapidoOla, onCheckedChange = { onlyRapidoOla = it })
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("🔊 Play Sound / TTS", color = Color.White, fontSize = 12.sp)
                                        Switch(checked = playSound, onCheckedChange = { playSound = it })
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("⚡ Auto Accept", color = Color.White, fontSize = 12.sp)
                                        Switch(checked = autoAcceptOn, onCheckedChange = { autoAcceptOn = it })
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("📸 Screenshot Save to Gallery", color = Color.White, fontSize = 12.sp)
                                        Switch(checked = screenshotSave, onCheckedChange = { screenshotSave = it })
                                    }

                                    Button(
                                        onClick = { Toast.makeText(context, "✅ Settings Saved Successfully!", Toast.LENGTH_SHORT).show() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Save Settings", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Live Earnings Dashboard (Starts from 0)
                        item {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF050B14)),
                                border = BorderStroke(1.5.dp, Color(0xFF22C55E).copy(alpha = 0.6f))
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("📈 Live Earnings Dashboard", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Today ▾", color = Color.LightGray, fontSize = 12.sp)
                                    }
                                    
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)), modifier = Modifier.weight(1f).padding(4.dp)) {
                                            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Total Earnings", color = Color.LightGray, fontSize = 9.sp)
                                                Text("₹$totalEarnings", color = Color(0xFF22C55E), fontWeight = FontWeight.Black, fontSize = 16.sp)
                                            }
                                        }
                                        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A8A)), modifier = Modifier.weight(1f).padding(4.dp)) {
                                            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Total Rides", color = Color.LightGray, fontSize = 9.sp)
                                                Text("$totalRidesCount", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                            }
                                        }
                                        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF581C87)), modifier = Modifier.weight(1f).padding(4.dp)) {
                                            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("Total Distance", color = Color.LightGray, fontSize = 9.sp)
                                                Text(String.format("%.1f Km", totalDistance), color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // ANALYTICS TAB
                    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("📊 Fleet Analytics & Performance", color = Color(0xFF38BDF8), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Total Revenue Generated: ₹$totalEarnings", color = Color(0xFF22C55E), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Total Trips Completed: $totalRidesCount", color = Color.White, fontSize = 14.sp)
                                Text("Average Earnings Per Trip: ₹${if (totalRidesCount > 0) totalEarnings / totalRidesCount else 0}", color = Color.White, fontSize = 14.sp)
                                Text("Efficiency Rating: 4.9 ⭐", color = Color(0xFFFACC15), fontSize = 14.sp)
                            }
                        }
                    }
                }
                2 -> {
                    // HISTORY TAB
                    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🕒 Completed Ride History", color = Color(0xFF38BDF8), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        if (rideHistoryList.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No rides captured yet. Use 'Test Ride Accept' on Home.", color = Color.Gray, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(rideHistoryList) { ride ->
                                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
                                        Row(Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Column {
                                                Text("🚀 ${ride.company}", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text("Distance: ${ride.distance}", color = Color.LightGray, fontSize = 11.sp)
                                            }
                                            Text(ride.amount, color = Color(0xFFFACC15), fontWeight = FontWeight.Black, fontSize = 18.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // HELP TAB
                    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("❓ Support & Help Center", color = Color(0xFF38BDF8), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("How to use Captain Smart Pro:", fontWeight = FontWeight.Bold, color = Color.White)
                                Text("1. Tap the Activate button on top to ready the 0.00s radar.", color = Color.LightGray, fontSize = 13.sp)
                                Text("2. Set your Max Pickup and Min Price filters.", color = Color.LightGray, fontSize = 13.sp)
                                Text("3. App stays completely peaceful until a ride is accepted or tested!", color = Color.LightGray, fontSize = 13.sp)
                            }
                        }
                    }
                }
                4 -> {
                    // ABOUT TAB
                    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("ℹ️ About Captain Smart Pro", color = Color(0xFF38BDF8), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("👑 Captain Smart Pro v2.5", fontWeight = FontWeight.Bold, color = Color(0xFF22C55E), fontSize = 16.sp)
                                Text("Dibrugarh Fleet & Auto-Clicker Edition", color = Color.White, fontSize = 13.sp)
                                Text("Designed for Rapido, Ola, and Uber Captains.", color = Color.LightGray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog Modals
    if (showStatsDialog) {
        AlertDialog(
            onDismissRequest = { showStatsDialog = false },
            title = { Text("📊 Live Fleet Stats") },
            text = { Text("Total Earnings: ₹$totalEarnings\nTotal Rides: $totalRidesCount\nTotal Distance: ${String.format("%.1f", totalDistance)} Km\nStatus: ${if(isActivated) "Active" else "Deactivated"}") },
            confirmButton = { TextButton(onClick = { showStatsDialog = false }) { Text("Close") } }
        )
    }

    if (showNotificationsDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationsDialog = false },
            title = { Text("🔔 Notifications (3)") },
            text = { Text("1. Dibrugarh Zone surge pricing active (+₹20).\n2. 30-Day Subscription verified successfully.\n3. New Rapido dispatch protocol updated.") },
            confirmButton = { TextButton(onClick = { showNotificationsDialog = false }) { Text("Close") } }
        )
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("⚙️ App Settings") },
            text = { Text("Audio Speech TTS: Enabled\nGallery Auto-Save: Enabled\nRegion: Dibrugarh, Assam\nVersion: v2.5 Pro") },
            confirmButton = { TextButton(onClick = { showSettingsDialog = false }) { Text("Close") } }
        )
    }

    if (showOrderSplitDialog) {
        AlertDialog(
            onDismissRequest = { showOrderSplitDialog = false },
            title = { Text("🔀 Order Split Form (Auto Split)") },
            text = { Text("Order split allows dividing multi-destination rides into profitable legs. Currently configured for Dibrugarh routes.") },
            confirmButton = { TextButton(onClick = { showOrderSplitDialog = false }) { Text("Got it") } }
        )
    }

    if (showProDialog) {
        AlertDialog(
            onDismissRequest = { showProDialog = false },
            title = { Text("👑 Captain Smart Pro Subscription") },
            text = { Text("You are on the Diamond Partner Fleet Plan. All 0.00s auto-clicker features, audio alerts, and gallery screenshot saving are fully unlocked!") },
            confirmButton = { TextButton(onClick = { showProDialog = false }) { Text("Awesome") } }
        )
    }
}
