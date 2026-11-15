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
import com.google.firebase.firestore.Query
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CaptainSmartProApp()
            }
        }
    }
}

data class PaymentModel(val id: String = "", val phone: String = "", val receiptBase64: String = "")
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
        delay(2000)
        showSplash = false
    }

    if (showSplash) {
        SplashScreen()
    } else if (loggedInPhone.isEmpty()) {
        LoginScreen(db = db) { phone, admin ->
            prefs.edit().putString("user_phone", phone).putBoolean("is_admin", admin).apply()
            loggedInPhone = phone
            isAdmin = admin
        }
    } else if (isAdmin) {
        Column(Modifier.fillMaxSize()) {
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
            Text("Multi-Vehicle Fleet & Direct Bidding", color = Color.LightGray, fontSize = 12.sp)
        }
    }
}

@Composable
fun LoginScreen(db: FirebaseFirestore, onLoginSuccess: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    var phone by remember { mutableStateOf("") }
    var vehicle by remember { mutableStateOf("Bike 🏍️") }
    var facePic by remember { mutableStateOf("") }
    var vehPic by remember { mutableStateOf("") }
    var referral by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    val facePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val stream = context.contentResolver.openInputStream(it)
            val bmp = BitmapFactory.decodeStream(stream)
            val scaled = Bitmap.createScaledBitmap(bmp, 150, 150, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 60, out)
            facePic = Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
            Toast.makeText(context, "Face Photo Saved", Toast.LENGTH_SHORT).show()
        }
    }

    val vehPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val stream = context.contentResolver.openInputStream(it)
            val bmp = BitmapFactory.decodeStream(stream)
            val scaled = Bitmap.createScaledBitmap(bmp, 200, 150, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 60, out)
            vehPic = Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
            Toast.makeText(context, "Vehicle Photo Saved", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B)))).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        item {
            Text("Captain Smart Pro", color = Color(0xFF22C55E), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Direct Login Portal", color = Color.LightGray, fontSize = 11.sp)
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        Button(onClick = { facePicker.launch("image/*") }) { Text(if (facePic.isEmpty()) "👤 Face Photo" else "✅ Face Saved") }
                        Button(onClick = { vehPicker.launch("image/*") }) { Text(if (vehPic.isEmpty()) "🏍️ Vehicle Pic" else "✅ Vehicle Saved") }
                    }
                    Spacer(Modifier.height(10.dp))
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
                    OutlinedTextField(value = phone, onValueChange = { if (it.length <= 10) phone = it }, label = { Text("10-digit Mobile Number") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = referral, onValueChange = { referral = it }, label = { Text("Referral Phone (Optional - 6 Days Free)") }, singleLine = true)
                    Spacer(Modifier.height(12.dp))

                    // Direct Login Button (No OTP hassle)
                    Button(onClick = {
                        if (phone.length == 10) {
                            val ref = db.collection("riders").document(phone)
                            ref.get().addOnSuccessListener { doc ->
                                val now = System.currentTimeMillis()
                                val trialDays = if (referral.isNotEmpty()) 6L else 1L
                                val expiry = if (doc.exists()) doc.getLong("expiry") ?: (now + trialDays * 86400000L) else (now + trialDays * 86400000L)
                                val data = mapOf("phone" to phone, "vehicle" to vehicle, "profilePic" to facePic, "vehiclePic" to vehPic, "expiry" to expiry)
                                ref.set(data).addOnSuccessListener {
                                    if (referral.isNotEmpty() && referral != phone) {
                                        db.collection("riders").document(referral).get().addOnSuccessListener { rDoc ->
                                            if (rDoc.exists()) {
                                                val refExp = rDoc.getLong("expiry") ?: now
                                                val newRefExp = maxOf(refExp, now) + (5L * 86400000L)
                                                db.collection("riders").document(referral).update("expiry", newRefExp)
                                            }
                                        }
                                    }
                                    Toast.makeText(context, "Login Successful!", Toast.LENGTH_SHORT).show()
                                    onLoginSuccess(phone, false)
                                }
                            }
                        } else {
                            Toast.makeText(context, "Enter valid 10-digit number", Toast.LENGTH_SHORT).show()
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)), modifier = Modifier.fillMaxWidth()) {
                        Text("🚀 Direct Login & Start", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("Admin PIN (9954)") }, singleLine = true)
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = { if (pin == "9954") onLoginSuccess("ADMIN", true) else Toast.makeText(context, "Wrong PIN", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open Admin Panel")
                    }
                }
            }
        }
    }
}

@Composable
fun RiderDashboard(phone: String, db: FirebaseFirestore, rtdb: DatabaseReference, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var expiry by remember { mutableStateOf(0L) }
    var vehicle by remember { mutableStateOf("Bike") }
    var qr by remember { mutableStateOf("") }
    var upi by remember { mutableStateOf("dinakeot@upi") }
    var receipt by remember { mutableStateOf("") }

    // Rider Bidding Customization States
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

    LaunchedEffect(phone) {
        if (phone != "ADMIN_USER") {
            db.collection("riders").document(phone).addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    expiry = doc.getLong("expiry") ?: 0L
                    vehicle = doc.getString("vehicle") ?: "Bike"
                }
            }
        } else {
            expiry = System.currentTimeMillis() + 86400000L * 30
        }
        db.collection("config").document("settings").addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                qr = doc.getString("qrBase64") ?: ""
                upi = doc.getString("upiId") ?: "dinakeot@upi"
            }
        }
        rtdb.child("live_riders").child(phone).setValue(mapOf("phone" to phone, "vehicle" to vehicle, "time" to System.currentTimeMillis()))
    }

    val active = System.currentTimeMillis() < expiry

    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().background(Color(0xFF0F172A)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Captain • $vehicle", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold)
                Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Logout", fontSize = 10.sp) }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Bidding") }, icon = {})
                NavigationBarItem(selected = tab == 1, onClick = { tab == 1 }, label = { Text("₹49 Plan") }, icon = {})
                NavigationBarItem(selected = tab == 2, onClick = { tab == 2 }, label = { Text("Refer") }, icon = {})
                NavigationBarItem(selected = tab == 3, onClick = { tab == 3 }, label = { Text("Support") }, icon = {})
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color(0xFF0F172A))) {
            when (tab) {
                0 -> {
                    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = if (active) Color(0xFFDCFCE7) else Color(0xFFFEE2E2))) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(if (active) "✅ Active Status: $vehicle" else "⚠️ Subscription Expired", fontWeight = FontWeight.Bold, color = Color.Black)
                                    Text(if (active) "Nearest Dispatch & Auto-Accept Active" else "Renew ₹49 for unlimited priority rides.", fontSize = 11.sp, color = Color.DarkGray)
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("🎯 Custom Ride Bidding & Distance Control", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold)
                                    OutlinedTextField(value = pickupKm, onValueChange = { pickupKm = it }, label = { Text("Max Pickup Distance (Km)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    OutlinedTextField(value = dropKm, onValueChange = { dropKm = it }, label = { Text("Max Drop-off Distance (Km)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    OutlinedTextField(value = bidAmount, onValueChange = { bidAmount = it }, label = { Text("Your Bid Amount (₹)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    
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
            OutlinedTextField(value = msg, onValueChange = { msg = it }, modifier = Modifier.weight(1f), placeholder = { Text("Type message...") })
            Spacer(Modifier.width(6.dp))
            Button(onClick = {
                if (msg.isNotBlank()) {
                    rtdb.child("chats").child(phone).push().setValue(mapOf("sender" to if (isAdmin) "Admin" else phone, "text" to msg))
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
    val riders = remember { mutableStateListOf<RiderModel>() }
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
        db.collection("riders").addSnapshotListener { snap, _ ->
            riders.clear()
            snap?.documents?.forEach {
                riders.add(RiderModel(it.getString("phone") ?: "", it.getString("vehicle") ?: "", it.getString("profilePic") ?: "", it.getString("vehiclePic") ?: "", it.getLong("expiry") ?: 0L))
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
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab == 0 }, label = { Text("Payments") }, icon = {})
                NavigationBarItem(selected = tab == 1, onClick = { tab == 1 }, label = { Text("QR") }, icon = {})
                NavigationBarItem(selected = tab == 2, onClick = { tab == 2 }, label = { Text("Profiles") }, icon = {})
                NavigationBarItem(selected = tab == 3, onClick = { tab == 3 }, label = { Text("Chat") }, icon = {})
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color(0xFF0F172A))) {
            when (tab) {
                0 -> {
                    LazyColumn(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (payments.isEmpty()) item { Text("No pending payments.", color = Color.Gray) }
                        items(payments) { (id, ph, receiptB64) ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Phone: $ph", fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    if (receiptB64.isNotEmpty()) {
                                        val bytes = Base64.decode(receiptB64, Base64.DEFAULT)
                                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        if (bmp != null) Image(bitmap = bmp.asImageBitmap(), contentDescription = "Proof", modifier = Modifier.height(150.dp).fillMaxWidth())
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = {
                                        val newExp = System.currentTimeMillis() + 86400000L * 30
                                        db.collection("riders").document(ph).update("expiry", newExp)
                                        db.collection("payments").document(id).update("status", "approved")
                                        Toast.makeText(context, "Approved 30 Days!", Toast.LENGTH_SHORT).show()
                                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))) {
                                        Text("Approve 30 Days Plan", color = Color.Black)
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
                    LazyColumn(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { Text("👥 Registered Rider Profiles (Face & Vehicle)", color = Color.White, fontWeight = FontWeight.Bold) }
                        if (riders.isEmpty()) item { Text("No riders registered yet.", color = Color.Gray) }
                        items(riders) { r ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Phone: ${r.phone} | Vehicle: ${r.vehicle}", fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                                        if (r.profilePic.isNotEmpty()) {
                                            val b = Base64.decode(r.profilePic, Base64.DEFAULT)
                                            val bmp = BitmapFactory.decodeByteArray(b, 0, b.size)
                                            if (bmp != null) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text("Face", fontSize = 10.sp, color = Color.Gray)
                                                    Image(bitmap = bmp.asImageBitmap(), contentDescription = "", modifier = Modifier.size(60.dp).clip(CircleShape))
                                                }
                                            }
                                        }
                                        if (r.vehiclePic.isNotEmpty()) {
                                            val b = Base64.decode(r.vehiclePic, Base64.DEFAULT)
                                            val bmp = BitmapFactory.decodeByteArray(b, 0, b.size)
                                            if (bmp != null) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text("Vehicle", fontSize = 10.sp, color = Color.Gray)
                                                    Image(bitmap = bmp.asImageBitmap(), contentDescription = "", modifier = Modifier.size(70.dp, 50.dp).clip(RoundedCornerShape(6.dp)))
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    // Direct Call & WhatsApp Action Buttons
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        Button(onClick = {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${r.phone}"))
                                            context.startActivity(intent)
                                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                            Text("📞 Call", color = Color.Black, fontSize = 11.sp)
                                        }
                                        Button(onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/91${r.phone}"))
                                            context.startActivity(intent)
                                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                            Text("💬 WhatsApp", color = Color.Black, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = chatPhone, onValueChange = { chatPhone = it }, label = { Text("Rider Phone Number") }, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { activeChat = chatPhone }) { Text("Open Chat") }
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
