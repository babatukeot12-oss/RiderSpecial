package com.riderspecial.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF22C55E),
                    background = Color(0xFF0F172A),
                    surface = Color(0xFF1E293B),
                    onPrimary = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                CaptainSmartProApp()
            }
        }
    }
}

data class RiderModel(val phone: String = "", val vehicle: String = "", val profilePic: String = "", val vehiclePic: String = "", val expiry: Long = 0L)

@SuppressLint("HardwareIds")
@Composable
fun CaptainSmartProApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("CaptainSmartPrefs", Context.MODE_PRIVATE) }
    val db = remember { FirebaseFirestore.getInstance() }
    val rtdb = remember { FirebaseDatabase.getInstance().reference }

    var showSplash by remember { mutableStateOf(true) }
    var loggedInPhone by remember { mutableStateOf(prefs.getString("user_phone", "") ?: "") }
    var isAdmin by remember { mutableStateOf(prefs.getBoolean("is_admin", false)) }
    var adminViewMode by remember { mutableStateOf(prefs.getString("admin_view_mode", "RIDER") ?: "RIDER") }

    LaunchedEffect(Unit) {
        delay(1500)
        showSplash = false
    }

    if (showSplash) {
        SplashScreen()
    } else if (loggedInPhone.isEmpty()) {
        LoginScreen(prefs = prefs) { phone, admin ->
            loggedInPhone = phone
            isAdmin = admin
        }
    } else if (isAdmin) {
        Column(Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF1E293B)).padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { adminViewMode = "RIDER"; prefs.edit().putString("admin_view_mode", "RIDER").apply() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (adminViewMode == "RIDER") Color(0xFF22C55E) else Color(0xFF334155))) {
                    Text("🏍️ Rider View", color = if (adminViewMode == "RIDER") Color.Black else Color.White, fontWeight = FontWeight.Bold)
                }
                Button(onClick = { adminViewMode = "ADMIN"; prefs.edit().putString("admin_view_mode", "ADMIN").apply() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (adminViewMode == "ADMIN") Color(0xFF38BDF8) else Color(0xFF334155))) {
                    Text("🛠️ Admin Panel", color = if (adminViewMode == "ADMIN") Color.Black else Color.White, fontWeight = FontWeight.Bold)
                }
            }
            if (adminViewMode == "ADMIN") {
                AdminDashboard(db = db, rtdb = rtdb) {
                    prefs.edit().clear().apply()
                    loggedInPhone = ""
                    isAdmin = false
                }
            } else {
                RiderDashboard(phone = "ADMIN_USER", db = db, rtdb = rtdb) {
                    prefs.edit().clear().apply()
                    loggedInPhone = ""
                    isAdmin = false
                }
            }
        }
    } else {
        RiderDashboard(phone = loggedInPhone, db = db, rtdb = rtdb) {
            prefs.edit().clear().apply()
            loggedInPhone = ""
        }
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B)))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("👑 Captain Smart Pro", color = Color(0xFF22C55E), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Multi-Vehicle Fleet & Direct Bidding", color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
fun LoginScreen(prefs: android.content.SharedPreferences, onLoginSuccess: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    var phone by remember { mutableStateOf("") }
    var vehicle by remember { mutableStateOf("Bike 🏍️") }
    var pin by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B)))).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        item {
            Text("Captain Smart Pro", color = Color(0xFF22C55E), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Direct Instant Login", color = Color.White, fontSize = 11.sp)
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Select Vehicle Type:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("Bike 🏍️", "Auto 🛺", "Taxi 🚕", "EV ⚡").forEach { v ->
                            Button(onClick = { vehicle = v }, colors = ButtonDefaults.buttonColors(containerColor = if (vehicle == v) Color(0xFF22C55E) else Color.Gray), contentPadding = PaddingValues(4.dp)) {
                                Text(v, fontSize = 10.sp, color = Color.Black)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { if (it.length <= 10) phone = it },
                        label = { Text("10-digit Mobile Number", color = Color.White) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF22C55E), unfocusedBorderColor = Color.LightGray)
                    )
                    Spacer(Modifier.height(12.dp))

                    // Instant Local Login (Guaranteed to work without hanging)
                    Button(onClick = {
                        if (phone.length == 10) {
                            val expiry = System.currentTimeMillis() + 86400000L * 7 // 7 Days Trial
                            prefs.edit().putString("user_phone", phone).putBoolean("is_admin", false).putLong("sub_expiry", expiry).apply()
                            Toast.makeText(context, "Login Successful!", Toast.LENGTH_SHORT).show()
                            onLoginSuccess(phone, false)
                        } else {
                            Toast.makeText(context, "Enter valid 10-digit number", Toast.LENGTH_SHORT).show()
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)), modifier = Modifier.fillMaxWidth()) {
                        Text("🚀 Direct Login & Start", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text("Admin PIN (9954)", color = Color.White) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF38BDF8), unfocusedBorderColor = Color.LightGray)
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = {
                        if (pin == "9954") {
                            prefs.edit().putBoolean("is_admin", true).apply()
                            onLoginSuccess("ADMIN", true)
                        } else {
                            Toast.makeText(context, "Wrong PIN", Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))) {
                        Text("Open Admin Panel", color = Color.Black, fontWeight = FontWeight.Bold)
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
    var upi by remember { mutableStateOf("dinakeot@upi") }
    var receipt by remember { mutableStateOf("") }

    var pickupKm by remember { mutableStateOf("2.0") }
    var dropKm by remember { mutableStateOf("5.0") }
    var bidAmount by remember { mutableStateOf("150") }
    var autoAcceptEnabled by remember { mutableStateOf(true) }

    val context = LocalContext.current

    val payPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val stream = context.contentResolver.openInputStream(it)
            val bmp = BitmapFactory.decodeStream(stream)
            val scaled = Bitmap.createScaledBitmap(bmp, 300, 400, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 60, out)
            receipt = Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
            Toast.makeText(context, "Payment Screenshot Attached", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        db.collection("config").document("settings").get().addOnSuccessListener { doc ->
            if (doc != null && doc.exists()) {
                qr = doc.getString("qrBase64") ?: ""
                upi = doc.getString("upiId") ?: "dinakeot@upi"
            }
        }
    }

    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().background(Color(0xFF0F172A)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Captain • Active", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold)
                Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Logout", fontSize = 10.sp) }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF1E293B)) {
                NavigationBarItem(selected = tab == 0, onClick = { tab == 0 }, label = { Text("Bidding", color = Color.White) }, icon = {})
                NavigationBarItem(selected = tab == 1, onClick = { tab == 1 }, label = { Text("₹49 Plan", color = Color.White) }, icon = {})
                NavigationBarItem(selected = tab == 2, onClick = { tab == 2 }, label = { Text("Refer", color = Color.White) }, icon = {})
                NavigationBarItem(selected = tab == 3, onClick = { tab == 3 }, label = { Text("Support", color = Color.White) }, icon = {})
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color(0xFF0F172A))) {
            when (tab) {
                0 -> {
                    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7))) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("✅ Active Status: Online", fontWeight = FontWeight.Bold, color = Color.Black)
                                    Text("Nearest Dispatch & Auto-Accept Active", fontSize = 11.sp, color = Color.DarkGray)
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("🎯 Custom Ride Bidding & Distance Control", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold)
                                    OutlinedTextField(
                                        value = pickupKm,
                                        onValueChange = { pickupKm = it },
                                        label = { Text("Max Pickup Distance (Km)", color = Color.White) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF22C55E), unfocusedBorderColor = Color.LightGray)
                                    )
                                    OutlinedTextField(
                                        value = dropKm,
                                        onValueChange = { dropKm = it },
                                        label = { Text("Max Drop-off Distance (Km)", color = Color.White) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF22C55E), unfocusedBorderColor = Color.LightGray)
                                    )
                                    OutlinedTextField(
                                        value = bidAmount,
                                        onValueChange = { bidAmount = it },
                                        label = { Text("Your Bid Amount (₹)", color = Color.White) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF22C55E), unfocusedBorderColor = Color.LightGray)
                                    )
                                    
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("0.00s Auto-Accept Engine", color = Color.White, fontSize = 13.sp)
                                        Switch(checked = autoAcceptEnabled, onCheckedChange = { autoAcceptEnabled = it })
                                    }

                                    Button(onClick = {
                                        Toast.makeText(context, "Settings Saved! Pickup: ${pickupKm}Km, Bid: ₹$bidAmount", Toast.LENGTH_LONG).show()
                                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))) {
                                        Text("Save & Go Live", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    LazyColumn(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Text("30 Days Unlimited - ₹49", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        item {
                            if (qr.isNotEmpty()) {
                                val bytes = Base64.decode(qr, Base64.DEFAULT)
                                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bmp != null) Image(bitmap = bmp.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(160.dp))
                            } else {
                                Text("UPI ID: $upi", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        item {
                            Button(onClick = { payPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (receipt.isEmpty()) "Attach Payment Screenshot" else "✅ Screenshot Attached")
                            }
                        }
                        item {
                            Button(onClick = {
                                if (receipt.isNotEmpty()) {
                                    db.collection("payments").add(mapOf("phone" to phone, "receiptBase64" to receipt, "status" to "pending", "time" to System.currentTimeMillis()))
                                    receipt = ""
                                    Toast.makeText(context, "Screenshot sent to Admin!", Toast.LENGTH_SHORT).show()
                                }
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))) {
                                Text("Submit Payment Screenshot", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                2 -> {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("🎁 Refer & Earn Free Trial", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Refer friends! You get 5 Days Free per referral, and your friend gets 6 Days Free Trial.", color = Color.White, fontSize = 12.sp)
                                Button(onClick = {
                                    val intent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "Join Captain Smart Pro using my number $phone as Referral Code for 6 Days Free Trial!")
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share"))
                                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))) {
                                    Text("Share on WhatsApp", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                3 -> {
                    ChatScreen(phone = phone, isAdmin = false, rtdb = rtdb)
                }
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

    Column(Modifier.fillMaxSize().padding(8.dp)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(list) { (s, t) ->
                Text("$s: $t", color = Color.White, fontSize = 13.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = msg,
                onValueChange = { msg = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type message...", color = Color.LightGray) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF22C55E), unfocusedBorderColor = Color.LightGray)
            )
            Spacer(Modifier.width(6.dp))
            Button(onClick = {
                if (msg.isNotBlank()) {
                    rtdb.child("chats").child(phone).push().setValue(mapOf("sender" to if (isAdmin) "Admin" else phone, "text" tto msg))
                    msg = ""
                }
            }) { Text("Send") }
        }
    }
}

@Composable
fun AdminDashboard(db: FirebaseFirestore, rtdb: DatabaseReference, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val payments = remember { mutableStateListOf<Triple<String, String, String>>() }
    val context = LocalContext.current
    var chatPhone by remember { mutableStateOf("") }
    var activeChat by remember { mutableStateOf("") }

    val qrPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val stream = context.contentResolver.openInputStream(it)
            val bytes = stream?.readBytes()
            if (bytes != null) {
                val b64 = Base64.encodeToString(bytes, Base64.DEFAULT)
                db.collection("config").document("settings").set(mapOf("qrBase64" to b64), com.google.firebase.firestore.SetOptions.merge())
                Toast.makeText(context, "QR Updated Live!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        db.collection("payments").whereEqualTo("status", "pending").addSnapshotListener { snap, _ ->
            payments.clear()
            snap?.documents?.forEach {
                payments.add(Triple(it.id, it.getString("phone") ?: "", it.getString("receiptBase64") ?: ""))
            }
        }
    }

    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().background(Color(0xFF0F172A)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("SUPER ADMIN PANEL", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Logout", fontSize = 10.sp) }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF1E293B)) {
                NavigationBarItem(selected = tab == 0, onClick = { tab == 0 }, label = { Text("Payments", color = Color.White) }, icon = {})
                NavigationBarItem(selected = tab == 1, onClick = { tab == 1 }, label = { Text("QR", color = Color.White) }, icon = {})
                NavigationBarItem(selected = tab == 2, onClick = { tab == 2 }, label = { Text("Chat", color = Color.White) }, icon = {})
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color(0xFF0F172A))) {
            when (tab) {
                0 -> {
                    LazyColumn(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (payments.isEmpty()) item { Text("No pending payments.", color = Color.White) }
                        items(payments) { (id, ph, receiptB64) ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Phone: $ph", fontWeight = FontWeight.Bold, color = Color.Black)
                                    Spacer(Modifier.height(6.dp))
                                    if (receiptB64.isNotEmpty()) {
                                        val bytes = Base64.decode(receiptB64, Base64.DEFAULT)
                                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        if (bmp != null) Image(bitmap = bmp.asImageBitmap(), contentDescription = "Proof", modifier = Modifier.height(150.dp).fillMaxWidth())
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = {
                                        db.collection("payments").document(id).update("status", "approved")
                                        Toast.makeText(context, "Payment Approved!", Toast.LENGTH_SHORT).show()
                                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))) {
                                        Text("Approve Payment", color = Color.Black)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { qrPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("📷 Upload New QR Code") }
                    }
                }
                2 -> {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = chatPhone,
                                onValueChange = { chatPhone = it },
                                label = { Text("Rider Phone Number", color = Color.White) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF38BDF8), unfocusedBorderColor = Color.LightGray)
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { activeChat = chatPhone }) { Text("Open") }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (activeChat.isNotEmpty()) {
                            ChatScreen(phone = activeChat, isAdmin = true, rtdb = rtdb)
                        }
                    }
                }
            }
        }
    }
}
