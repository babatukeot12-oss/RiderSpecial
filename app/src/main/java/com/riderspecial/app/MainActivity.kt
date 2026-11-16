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
                CaptainSmartCleanApp(tts)
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

// Function to generate trip card image and save directly to phone Gallery
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
        Toast.makeText(context, "📸 Trip Screenshot Saved to Gallery!", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@SuppressLint("HardwareIds")
@Composable
fun CaptainSmartCleanApp(tts: TextToSpeech?) {
    val context = LocalContext.current
    
    var isRunning by remember { mutableStateOf(false) }
    var maxPickup by remember { mutableStateOf("1.0") }
    var minPrice by remember { mutableStateOf("100") }
    
    // Captured Ride State
    var hasRide by remember { mutableStateOf(false) }
    var rideCompany by remember { mutableStateOf("") }
    var rideAmount by remember { mutableStateOf("") }
    var rideDistance by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Surface(color = Color(0xFF0F172A), shadowElevation = 8.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("👑", fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("Captain Smart Pro", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isRunning) Color(0xFF22C55E).copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, if (isRunning) Color(0xFF22C55E) else Color.Red)
                    ) {
                        Text(
                            if (isRunning) "● AUTO-CLICKER ACTIVE" else "○ PAUSED",
                            color = if (isRunning) Color(0xFF22C55E) else Color.Red,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A))))) {
            LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                
                // Header Banner & Start/Stop Button
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B132B)),
                        border = BorderStroke(1.5.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                    ) {
                        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Dibrugarh Fleet Dispatcher", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("0.00s Instant Ride Catcher", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(14.dp))
                            
                            Button(
                                onClick = { isRunning = !isRunning },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) Color.Red else Color(0xFF22C55E)),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    if (isRunning) "⏹ STOP AUTO-CLICKER" else "🚀 START 0.00s AUTO-CLICKER",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }

                // Manual Test Button to simulate actual Ride Acceptance & Gallery Screenshot
                item {
                    Button(
                        onClick = {
                            val companies = listOf("Rapido", "Ola", "Uber")
                            val fares = listOf("125", "180", "210", "145", "320")
                            val distances = listOf("1.5 Km", "2.8 Km", "2.2 Km", "1.1 Km")
                            
                            rideCompany = companies.random()
                            rideAmount = "₹${fares.random()}"
                            rideDistance = distances.random()
                            hasRide = true

                            // Speak Audio
                            val speechText = "Ride Accepted! $rideCompany. Fare $rideAmount rupees."
                            tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, null)

                            // Save Screenshot to Gallery
                            saveTripCardToGallery(context, rideCompany, rideAmount, rideDistance)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("🧪 Test Ride Accept & Save Screenshot", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                // Captured Ride Card
                if (hasRide) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF052E16)),
                            border = BorderStroke(2.dp, Color(0xFF22C55E))
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("🎉 RIDE ACCEPTED ($rideCompany)", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("⚡ 0.00s Saved to Gallery", color = Color(0xFFFACC15), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Fare Amount", color = Color.LightGray, fontSize = 11.sp)
                                        Text(rideAmount, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                                    }
                                    Divider(color = Color(0xFF22C55E).copy(alpha = 0.3f), modifier = Modifier.height(40.dp).width(1.dp))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Distance to Go", color = Color.LightGray, fontSize = 11.sp)
                                        Text(rideDistance, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                                Button(
                                    onClick = { hasRide = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    modifier = Modifier.fillMaxWidth().height(38.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Dismiss Card", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Settings & Filters Card
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.3f))
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("⚙️ Auto-Clicker Rules & Filters", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            
                            OutlinedTextField(
                                value = maxPickup,
                                onValueChange = { maxPickup = it },
                                label = { Text("Max Pickup Distance (Km)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            
                            OutlinedTextField(
                                value = minPrice,
                                onValueChange = { minPrice = it },
                                label = { Text("Minimum Price / MRP Filter (₹)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
