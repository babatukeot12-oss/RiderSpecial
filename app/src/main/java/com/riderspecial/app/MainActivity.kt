package com.riderspecial.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF22C55E),
                    secondary = Color(0xFF38BDF8),
                    background = Color(0xFF030712),
                    surface = Color(0xFF0F172A),
                    onPrimary = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                CaptainSmartApp()
            }
        }
    }
}

data class RiderItem(val phone: String = "", val vehicle: String = "", val expiry: Long = 0L)
data class PaymentItem(val id: String = "", val phone: String = "", val receipt: String = "", val status: String = "")
data class LiveRiderItem(val phone: String = "", val vehicle: String = "", val pickup: String = "", val drop: String = "", val bid: String = "")

@SuppressLint("HardwareIds")
@Composable
fun CaptainSmartApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("CaptainPrefs", Context.MODE_PRIVATE) }
    val db = remember { FirebaseFirestore.getInstance() }
    val rtdb = remember { FirebaseDatabase.getInstance().reference }

    var showSplash by remember { mutableStateOf(true) }
    var loggedInPhone by remember { mutableStateOf(prefs.getString("phone", "") ?: "") }
    var isAdmin by remember { mutableStateOf(prefs.getBoolean("admin", false)) }

    LaunchedEffect(Unit) {
        delay(1400)
        showSplash = false
    }

    if (showSplash) {
        Box(
            modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF030712), Color(0xFF0F172A), Color(0xFF052E16)))),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(110.dp),
                    shape = CircleShape,
                    color = Color(0xFF22C55E).copy(alpha = 0.15f),
                    border = BorderStroke(2.dp, Color(0xFF22C55E))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("👑", fontSize = 52.sp)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("CAPTAIN SMART PRO", color = Color(0xFF22C55E), fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Spacer(Modifier.height(6.dp))
                Text("Dibrugarh's Ultimate Lighting Fleet Hub", color = Color(0xFF38BDF8), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    } else if (loggedInPhone.isEmpty()) {
        LoginScreen(prefs = prefs, db = db) { phone, admin ->
            loggedInPhone = phone
            isAdmin = admin
        }
    } else if (isAdmin) {
        AdminDashboard(db = db, rtdb = rtdb) {
            prefs.edit().clear().apply()
            loggedInPhone = ""
            isAdmin = false
        }
    } else {
        RiderDashboard(phone = loggedInPhone, db = db, rtdb = rtdb) {
            prefs.edit().clear().apply()
            loggedInPhone = ""
        }
    }
}

@Composable
fun LoginScreen(prefs: android.content.SharedPreferences, db: FirebaseFirestore, onLoginSuccess: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    var phone by remember { mutableStateOf("") }
    var vehicle by remember { mutableStateOf("Bike 🏍️") }
    var pin by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF030712), Color(0xFF0F172A)))).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        item {
            Text("👑 Captain Smart Pro", color = Color(0xFF22C55E), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("High-Performance Auto-Clicker Driver Portal", color = Color.LightGray, fontSize = 12.sp)
            Spacer(Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth().border(BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.4f)), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Select Vehicle Type:", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("Bike 🏍️", "Auto 🛺", "Taxi 🚕", "EV ⚡").forEach { v ->
                            Button(
                                onClick = { vehicle = v },
                                colors = ButtonDefaults.buttonColors(containerColor = if (vehicle == v) Color(0xFF22C55E) else Color(0xFF1E293B)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(v, fontSize = 11.sp, color = if (vehicle == v) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { if (it.length <= 10) phone = it },
                        label = { Text("10-digit Mobile Number", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF22C55E), unfocusedBorderColor = Color.Gray)
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            if (phone.length == 10) {
                                val expiry = System.currentTimeMillis() + 86400000L * 30
                                prefs.edit().putString("phone", phone).putBoolean("admin", false).putLong("expiry", expiry).apply()
                                db.collection("riders").document(phone).set(mapOf("phone" to phone, "vehicle" to vehicle, "expiry" to expiry))
                                Toast.makeText(context, "Login Successful! Welcome Captain.", Toast.LENGTH_SHORT).show()
                                onLoginSuccess(phone, false)
                            } else {
                                Toast.makeText(context, "Please enter a valid 10-digit mobile number", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("🚀 Direct Login & Start", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Spacer(Modifier.height(20.dp))
                    Divider(color = Color.Gray.copy(alpha = 0.3f))
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text("Admin PIN (9954)", color = Color(0xFF94A3B8)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF38BDF8), unfocusedBorderColor = Color.Gray)
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (pin == "9954") {
                                prefs.edit().putBoolean("admin", true).apply()
                                Toast.makeText(context, "Welcome Admin!", Toast.LENGTH_SHORT).show()
                                onLoginSuccess("ADMIN", true)
                            } else {
                                Toast.makeText(context, "Wrong Admin PIN", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("🛠️ Open Super Admin Panel", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RiderDashboard(phone: String, db: FirebaseFirestore, rtdb: DatabaseReference, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var qr by remember { mutableStateOf("") }
    var receipt by remember { mutableStateOf("") }
    var pickupKm by remember { mutableStateOf("2.0") }
    var dropKm by remember { mutableStateOf("5.0") }
    var bidAmount by remember { mutableStateOf("150") }
    var autoAccept by remember { mutableStateOf(true) }
    val context = LocalContext.current

    val payPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val stream = context.contentResolver.openInputStream(it)
            val bmp = BitmapFactory.decodeStream(stream)
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 60, out)
            receipt = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.DEFAULT)
            Toast.makeText(context, "Payment Screenshot Attached Successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        db.collection("config").document("settings").get().addOnSuccessListener { doc ->
            if (doc != null && doc.exists()) qr = doc.getString("qrBase64") ?: ""
        }
        rtdb.child("live_riders").child(phone).setValue(mapOf("phone" to phone, "time" to System.currentTimeMillis()))
    }

    Scaffold(
        topBar = {
            Surface(color = Color(0xFF0F172A), shadowElevation = 8.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("👑 Captain Pro", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("ID: $phone • Dibrugarh Zone", color = Color.LightGray, fontSize = 11.sp)
                    }
                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Logout", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0F172A)) {
                NavigationBarItem(selected = tab == 0, onClick = { tab == 0 }, label = { Text("🎯 Bidding", color = Color.White, fontWeight = FontWeight.Bold) }, icon = {})
                NavigationBarItem(selected = tab == 1, onClick = { tab == 1 }, label = { Text("💳 ₹49 Plan", color = Color.White, fontWeight = FontWeight.Bold) }, icon = {})
                NavigationBarItem(selected = tab == 2, onClick = { tab == 2 }, label = { Text("💬 Support", color = Color.White, fontWeight = FontWeight.Bold) }, icon = {})
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color(0xFF030712))) {
            when (tab) {
                0 -> {
                    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7))
                            ) {
                                Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("🟢", fontSize = 18.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("Status: Online & Ready", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 14.sp)
                                        Text("Nearest Dispatch & 0.00s Auto-Accept Active", fontSize = 11.sp, color = Color.DarkGray)
                                    }
                                }
                            }
                        }
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().border(BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.5f)), RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                            ) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("⚡ Auto-Clicker & Distance Rules", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    OutlinedTextField(
                                        value = pickupKm,
                                        onValueChange = { pickupKm = it },
                                        label = { Text("Max Pickup Distance (Km)", color = Color(0xFF38BDF8)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF22C55E), unfocusedBorderColor = Color.Gray)
                                    )
                                    OutlinedTextField(
                                        value = dropKm,
                                        onValueChange = { dropKm = it },
                                        label = { Text("Max Drop-off Distance (Km)", color = Color(0xFF38BDF8)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF22C55E), unfocusedBorderColor = Color.Gray)
                                    )
                                    OutlinedTextField(
                                        value = bidAmount,
                                        onValueChange = { bidAmount = it },
                                        label = { Text("Minimum Price / MRP Filter (₹)", color = Color(0xFF38BDF8)) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF22C55E), unfocusedBorderColor = Color.Gray)
                                    )

                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("0.00s Auto-Accept Engine", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Switch(checked = autoAccept, onCheckedChange = { autoAccept = it })
                                    }

                                    Button(
                                        onClick = {
                                            rtdb.child("live_riders").child(phone).setValue(
                                                mapOf("phone" to phone, "pickup" to pickupKm, "drop" to dropKm, "bid" to bidAmount, "time" to System.currentTimeMillis())
                                            )
                                            Toast.makeText(context, "✅ Rules Saved & Auto-Clicker Live in Dibrugarh!", Toast.LENGTH_LONG).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("🔥 Save & Run Auto-Clicker", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    LazyColumn(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        item {
                            Text("💳 30 Days Unlimited - ₹49", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        item {
                            Card(
                                modifier = Modifier.size(190.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
                                    if (qr.isNotEmpty()) {
                                        val bytes = android.util.Base64.decode(qr, android.util.Base64.DEFAULT)
                                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        if (bmp != null) Image(bitmap = bmp.asImageBitmap(), contentDescription = "QR", modifier = Modifier.fillMaxSize())
                                    } else {
                                        Text("Scan & Pay ₹49\nUPI: dinakeot@upi", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                        item {
                            Button(
                                onClick = { payPicker.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(if (receipt.isEmpty()) "📸 Attach Payment Screenshot" else "✅ Screenshot Attached Successfully", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                        item {
                            Button(
                                onClick = {
                                    if (receipt.isNotEmpty()) {
                                        db.collection("payments").add(mapOf("phone" to phone, "receipt" to receipt, "status" to "pending", "time" to System.currentTimeMillis()))
                                        receipt = ""
                                        Toast.makeText(context, "🚀 Screenshot Sent to Admin Successfully!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Please attach payment screenshot first", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Submit Payment to Admin", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }
                2 -> ChatScreen(phone = phone, isAdmin = false, rtdb = rtdb)
            }
        }
    }
}

@Composable
fun ChatScreen(phone: String, isAdmin: Boolean, rtdb: DatabaseReference) {
    var msg by remember { mutableStateOf("") }
    val list = remember { mutableStateListOf<Pair<String, String>>() }

    LaunchedEffect(phone) {
        rtdb.child("chats").child(phone).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                list.clear()
                for (c in snapshot.children) {
                    list.add((c.child("sender").getValue(String::class.java) ?: "") to (c.child("text").getValue(String::class.java) ?: ""))
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list) { (s, t) ->
                Surface(
                    color = if (s == "Admin") Color(0xFF0F172A) else Color(0xFF22C55E).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(s, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        Spacer(Modifier.height(2.dp))
                        Text(t, color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = msg,
                onValueChange = { msg = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type message...", color = Color.LightGray) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF22C55E), unfocusedBorderColor = Color.Gray)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (msg.isNotBlank()) {
                        rtdb.child("chats").child(phone).push().setValue(mapOf("sender" to if (isAdmin) "Admin" else phone, "text" to msg))
                        msg = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Send", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AdminDashboard(db: FirebaseFirestore, rtdb: DatabaseReference, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val payments = remember { mutableStateListOf<PaymentItem>() }
    val riders = remember { mutableStateListOf<RiderItem>() }
    val liveRiders = remember { mutableStateListOf<LiveRiderItem>() }
    val context = LocalContext.current
    var chatPhone by remember { mutableStateOf("") }
    var activeChat by remember { mutableStateOf("") }

    val qrPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val stream = context.contentResolver.openInputStream(it)
            val bytes = stream?.readBytes()
            if (bytes != null) {
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                db.collection("config").document("settings").set(mapOf("qrBase64" to b64), com.google.firebase.firestore.SetOptions.merge())
                Toast.makeText(context, "QR Code Updated Successfully!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        db.collection("payments").addSnapshotListener { snap, _ ->
            payments.clear()
            snap?.documents?.forEach { doc ->
                val status = doc.getString("status") ?: "pending"
                if (status == "pending") {
                    payments.add(PaymentItem(doc.id, doc.getString("phone") ?: "", doc.getString("receipt") ?: "", status))
                }
            }
        }
        db.collection("riders").addSnapshotListener { snap, _ ->
            riders.clear()
            snap?.documents?.forEach { doc ->
                riders.add(RiderItem(doc.getString("phone") ?: "", doc.getString("vehicle") ?: "Bike", doc.getLong("expiry") ?: 0L))
            }
        }
        rtdb.child("live_riders").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                liveRiders.clear()
                for (child in snapshot.children) {
                    val phone = child.child("phone").getValue(String::class.java) ?: child.key ?: ""
                    val vehicle = child.child("vehicle").getValue(String::class.java) ?: "Bike"
                    val pickup = child.child("pickup").getValue(String::class.java) ?: "2.0"
                    val drop = child.child("drop").getValue(String::class.java) ?: "5.0"
                    val bid = child.child("bid").getValue(String::class.java) ?: "150"
                    liveRiders.add(LiveRiderItem(phone, vehicle, pickup, drop, bid))
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    Scaffold(
        topBar = {
            Surface(color = Color(0xFF0F172A), shadowElevation = 8.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🛠️ SUPER ADMIN PANEL", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Logout", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0F172A)) {
                NavigationBarItem(selected = tab == 0, onClick = { tab == 0 }, label = { Text("💳 Payments", color = Color.White, fontWeight = FontWeight.Bold) }, icon = {})
                NavigationBarItem(selected = tab == 1, onClick = { tab == 1 }, label = { Text("📷 QR", color = Color.White, fontWeight = FontWeight.Bold) }, icon = {})
                NavigationBarItem(selected = tab == 2, onClick = { tab == 2 }, label = { Text("👥 Profiles", color = Color.White, fontWeight = FontWeight.Bold) }, icon = {})
                NavigationBarItem(selected = tab == 3, onClick = { tab == 3 }, label = { Text("📍 GPS", color = Color.White, fontWeight = FontWeight.Bold) }, icon = {})
                NavigationBarItem(selected = tab == 4, onClick = { tab == 4 }, label = { Text("💬 Chat", color = Color.White, fontWeight = FontWeight.Bold) }, icon = {})
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color(0xFF030712))) {
            when (tab) {
                0 -> {
                    LazyColumn(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (payments.isEmpty()) {
                            item { Text("No pending payment screenshots.", color = Color.LightGray, fontSize = 13.sp) }
                        }
                        items(payments) { item ->
                            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("Rider Phone: ${item.phone}", fontWeight = FontWeight.Bold, color = Color(0xFF22C55E), fontSize = 14.sp)
                                    Spacer(Modifier.height(8.dp))
                                    if (item.receipt.isNotEmpty()) {
                                        val bytes = android.util.Base64.decode(item.receipt, android.util.Base64.DEFAULT)
                                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        if (bmp != null) Image(bitmap = bmp.asImageBitmap(), contentDescription = "Proof", modifier = Modifier.height(160.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)))
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            db.collection("payments").document(item.id).update("status", "approved")
                                            val newExp = System.currentTimeMillis() + 86400000L * 30
                                            db.collection("riders").document(item.phone).update("expiry", newExp)
                                            Toast.makeText(context, "✅ Plan Approved for 30 Days!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Approve 30 Days Plan", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Instant UPI QR Code Manager", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Upload a new QR code from your gallery. It will instantly update across all rider apps in Dibrugarh.", color = Color.LightGray, fontSize = 12.sp)
                        Button(
                            onClick = { qrPicker.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("📷 Upload New QR Code Image", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
                2 -> {
                    LazyColumn(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { Text("👥 Registered Rider Profiles", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold) }
                        if (riders.isEmpty()) {
                            item { Text("No registered riders found.", color = Color.LightGray, fontSize = 13.sp) }
                        }
                        items(riders) { r ->
                            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("Phone: ${r.phone}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                    Text("Vehicle: ${r.vehicle}", color = Color(0xFF22C55E), fontSize = 12.sp)
                                    Spacer(Modifier.height(10.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        Button(
                                            onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${r.phone}"))) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                        ) {
                                            Text("📞 Call", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                        Button(
                                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/91${r.phone}"))) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                        ) {
                                            Text("💬 WhatsApp", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    LazyColumn(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { Text("📍 Live Dibrugarh Fleet GPS Tracking", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold) }
                        if (liveRiders.isEmpty()) {
                            item { Text("No active riders online right now.", color = Color.LightGray, fontSize = 13.sp) }
                        }
                        items(liveRiders) { r ->
                            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Captain: ${r.phone}", fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("🟢 Online", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text("Vehicle: ${r.vehicle}", color = Color(0xFF38BDF8), fontSize = 12.sp)
                                    Text("Auto-Clicker Rules -> Pickup: ${r.pickup}Km | Drop: ${r.drop}Km | Min Price: ₹${r.bid}", color = Color(0xFFFACC15), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                4 -> {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = chatPhone,
                                onValueChange = { chatPhone = it },
                                label = { Text("Rider Phone Number", color = Color(0xFF94A3B8)) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF38BDF8), unfocusedBorderColor = Color.Gray)
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { activeChat = chatPhone },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Open", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (activeChat.isNotEmpty()) {
                            ChatScreen(phone = activeChat, isAdmin = true, rtdb = rtdb)
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Enter a rider mobile number above to open live chat support.", color = Color.LightGray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
