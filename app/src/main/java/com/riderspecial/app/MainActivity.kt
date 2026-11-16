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
                CaptainSmartUltimateApp(tts)
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
fun CaptainSmartUltimateApp(tts: TextToSpeech?) {
    val context = LocalContext.current
    
    var autoAcceptOn by remember { mutableStateOf(true) }
    var onlyRapidoOla by remember { mutableStateOf(true) }
    var playSound by remember { mutableStateOf(true) }
    var screenshotSave by remember { mutableStateOf(true) }

    var maxPickup by remember { mutableStateOf("1.0") }
    var minPrice by remember { mutableStateOf("20") }
    
    var hasRide by remember { mutableStateOf(true) }
    var rideCompany by remember { mutableStateOf("Rapido") }
    var rideAmount by remember { mutableStateOf("₹320") }
    var rideDistance by remember { mutableStateOf("1.1 Km") }

    var selectedTab by remember { mutableStateOf(0) }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF22C55E).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFF22C55E))
                        ) {
                            Text("● ACTIVE", color = Color(0xFF22C55E), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("⚙️", fontSize = 20.sp)
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
            LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                
                // Top Rapido Radar & Auto Accept Toggle Bar
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
                                Text("🟡 Rapido", color = Color(0xFFFACC15), fontWeight = FontWeight.Black, fontSize = 22.sp)
                                Text("📍 Dibrugarh Fleet Dispatcher", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text("0.00s Instant Ride Catcher", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Black)
                            }
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF16A34A))
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("⚡", fontSize = 16.sp)
                                    Text("Auto Accept", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text("ON", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }

                // Action Buttons Row
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                hasRide = true
                                rideCompany = "Rapido"
                                rideAmount = "₹320"
                                rideDistance = "1.1 Km"
                                val speech = "Ride Accepted! Rapido. Fare 320 rupees."
                                tts?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, null)
                                if (screenshotSave) saveTripCardToGallery(context, rideCompany, rideAmount, rideDistance)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8))
                        ) {
                            Text("📸 Test Ride Accept & Save", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { Toast.makeText(context, "Order Split Active!", Toast.LENGTH_SHORT).show() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF581C87)),
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFC084FC))
                        ) {
                            Text("🔀 Order Split Form", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Ride Accepted Card (Matches Screenshot style)
                if (hasRide) {
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
                                        Text("Ride Accepted ($rideCompany)", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Button(
                                        onClick = {
                                            if (screenshotSave) saveTripCardToGallery(context, rideCompany, rideAmount, rideDistance)
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
                                        Text(rideAmount, color = Color(0xFFFACC15), fontWeight = FontWeight.Black, fontSize = 26.sp)
                                    }
                                    Divider(color = Color(0xFF22C55E).copy(alpha = 0.3f), modifier = Modifier.height(40.dp).width(1.dp))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Distance to Go", color = Color.LightGray, fontSize = 11.sp)
                                        Text(rideDistance, color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                // Audio Alert Bar with waveform simulation
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFACC15)
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🔊 Audio Alert: $rideAmount | $rideDistance", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                        listOf("📊 Live Stats", "🕒 History", "🔔 Notify (3)", "⚙️ Settings").forEachIndexed { index, title ->
                            Card(
                                onClick = { Toast.makeText(context, "$title Clicked", Toast.LENGTH_SHORT).show() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                            ) {
                                Column(
                                    Modifier.padding(vertical = 12.dp, horizontal = 2.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(title, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                        }
                    }
                }

                // Auto-Clicker Rules & Filters Card (Matches Screenshot)
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B132B)),
                        border = BorderStroke(1.5.dp, Color(0xFF22C55E).copy(alpha = 0.5f))
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("⚙️ Auto-Clicker Rules & Filters", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFACC15)) {
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

                            // Toggles Grid
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("✔ Only Ola/Rapido", color = Color.White, fontSize = 12.sp)
                                Switch(checked = onlyRapidoOla, onCheckedChange = { onlyRapidoOla = it })
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("🔊 Play Sound", color = Color.White, fontSize = 12.sp)
                                Switch(checked = playSound, onCheckedChange = { playSound = it })
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("⚡ Auto Accept", color = Color.White, fontSize = 12.sp)
                                Switch(checked = autoAcceptOn, onCheckedChange = { autoAcceptOn = it })
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("📸 Screenshot Save", color = Color.White, fontSize = 12.sp)
                                Switch(checked = screenshotSave, onCheckedChange = { screenshotSave = it })
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { Toast.makeText(context, "Settings Reset", Toast.LENGTH_SHORT).show() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Reset", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { Toast.makeText(context, "Test Mode Active", Toast.LENGTH_SHORT).show() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Test Mode", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { Toast.makeText(context, "Settings Saved Successfully!", Toast.LENGTH_SHORT).show() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                    modifier = Modifier.weight(1.2f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Save Settings", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Live Earnings Dashboard Card
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
                                        Text("₹1,250", color = Color(0xFF22C55E), fontWeight = FontWeight.Black, fontSize = 16.sp)
                                    }
                                }
                                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A8A)), modifier = Modifier.weight(1f).padding(4.dp)) {
                                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Total Rides", color = Color.LightGray, fontSize = 9.sp)
                                        Text("12", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                    }
                                }
                                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF581C87)), modifier = Modifier.weight(1f).padding(4.dp)) {
                                    Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Total Distance", color = Color.LightGray, fontSize = 9.sp)
                                        Text("14.8 Km", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
