package com.riderspecial.app

import android.annotation.SuppressLint
import android.os.Bundle
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
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                CaptainSmartPureApp()
            }
        }
    }
}

@SuppressLint("HardwareIds")
@Composable
fun CaptainSmartPureApp() {
    val context = LocalContext.current
    
    var isRunning by remember { mutableStateOf(false) }
    var maxPickup by remember { mutableStateOf("3.0") }
    var minPrice by remember { mutableStateOf("100") }
    
    // Simple Primitive States for Captured Ride (No Map/Generic Errors)
    var hasRide by remember { mutableStateOf(false) }
    var rideCompany by remember { mutableStateOf("") }
    var rideAmount by remember { mutableStateOf("") }
    var rideDistance by remember { mutableStateOf("") }

    // Simulated Auto-Clicker Engine catching rides in 0.00s
    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (isRunning) {
                delay(4000) // Simulating incoming ride radar scan
                val sampleFares = listOf("125", "180", "210", "95", "320")
                val sampleDistances = listOf("1.5 Km", "2.8 Km", "4.0 Km", "0.8 Km", "5.2 Km")
                val sampleCompanies = listOf("Rapido", "Ola", "Uber")
                
                val fare = sampleFares.random()
                val dist = sampleDistances.random()
                val company = sampleCompanies.random()

                if (fare.toInt() >= (minPrice.toIntOrNull() ?: 50)) {
                    rideCompany = company
                    rideAmount = "₹$fare"
                    rideDistance = dist
                    hasRide = true
                    Toast.makeText(context, "🔔 Ride Accept! $company (₹$fare)", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

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
                
                // Header Banner & Start Button
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

                // Captured Ride Card (Shows Amount and Distance instantly)
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
                                    Text("⚡ 0.00s", color = Color(0xFFFACC15), fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF22C55E),
                                    unfocusedBorderColor = Color.Gray
                                )
                            )
                            
                            OutlinedTextField(
                                value = minPrice,
                                onValueChange = { minPrice = it },
                                label = { Text("Minimum Price / MRP Filter (₹)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF22C55E),
                                    unfocusedBorderColor = Color.Gray
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
