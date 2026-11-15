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
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CaptainSmartApp()
            }
        }
    }
}

data class PaymentModel(val id: String = "", val phone: String = "", val receiptBase64: String = "")
data class RiderModel(val phone: String = "", val vehicle: String = "", val profilePic: String = "", val vehiclePic: String = "", val expiry: Long = 0L)

@SuppressLint("HardwareIds")
@Composable
fun CaptainSmartApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("CaptainSmartPrefs", Context.MODE_PRIVATE) }
    val db = remember { FirebaseFirestore.getInstance() }
    val rtdb = remember { FirebaseDatabase.getInstance().reference }

    var loggedInPhone by remember { mutableStateOf(prefs.getString("user_phone", "") ?: "") }
    var isAdmin by remember { mutableStateOf(prefs.getBoolean("is_admin", false)) }
    var adminViewMode by remember { mutableStateOf(prefs.getString("admin_view_mode", "RIDER") ?: "RIDER") }

    if (loggedInPhone.isEmpty()) {
        LoginScreen(db = db) { phone, admin ->
            prefs.edit().putString("user_phone", phone).putBoolean("is_admin", admin).apply()
            loggedInPhone = phone
            isAdmin = admin
        }
    } else if (isAdmin) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF1E293B)).padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Button(onClick = { adminViewMode = "RIDER"; prefs.edit().putString("admin_view_mode", "RIDER").apply() }) {
                    Text("Rider View")
                }
                Button(onClick = { adminViewMode = "ADMIN"; prefs.edit().putString("admin_view_mode", "ADMIN").apply() }) {
                    Text("Admin Panel")
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
fun LoginScreen(db: FirebaseFirestore, onLoginSuccess: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var sentOtp by remember { mutableStateOf("") }
    var vehicle by remember { mutableStateOf("Bike 🏍️") }
    var facePic by remember { mutableStateOf("") }
    var vehPic by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    val facePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val stream = context.contentResolver.openInputStream(it)
            val bmp = BitmapFactory.decodeStream(stream)
            val scaled = Bitmap.createScaledBitmap(bmp, 150, 150, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 60, out)
            facePic = Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
            Toast.makeText(context, "Face Saved", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Vehicle Saved", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        item {
            Text("Captain Smart Pro", color = Color(0xFF22C55E), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        Button(onClick = { facePicker.launch("image/*") }) { Text(if (facePic.isEmpty()) "Face Photo" else "✅ Face") }
                        Button(onClick = { vehPicker.launch("image/*") }) { Text(if (vehPic.isEmpty()) "Vehicle Pic" else "✅ Vehicle") }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("Bike 🏍️", "Auto 🛺", "Taxi 🚕").forEach { v ->
                            Button(onClick = { vehicle = v }, colors = ButtonDefaults.buttonColors(containerColor = if (vehicle == v) Color(0xFF22C55E) else Color.Gray)) {
                                Text(v, fontSize = 11.sp, color = Color.Black)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = phone, onValueChange = { if (it.length <= 10) phone = it }, label = { Text("Mobile Number") }, singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    if (sentOtp.isEmpty()) {
                        Button(onClick = {
                            if (phone.length == 10) {
                                sentOtp = (1000..9999).random().toString()
                                Toast.makeText(context, "OTP: $sentOtp", Toast.LENGTH_LONG).show()
                            } else Toast.makeText(context, "Enter 10 digits", Toast.LENGTH_SHORT).show()
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))) {
                            Text("Send OTP", color = Color.Black)
                        }
                    } else {
                        OutlinedTextField(value = otp, onValueChange = { otp = it }, label = { Text("Enter OTP") }, singleLine = true)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = {
                            if (otp == sentOtp) {
                                val ref = db.collection("riders").document(phone)
                                ref.get().addOnSuccessListener { doc ->
                                    val expiry = if (doc.exists()) doc.getLong("expiry") ?: (System.currentTimeMillis() + 86400000L) else (System.currentTimeMillis() + 86400000L)
                                    val data = mapOf("phone" to phone, "vehicle" to vehicle, "profilePic" to facePic, "vehiclePic" to vehPic, "expiry" to expiry)
                                    ref.set(data).addOnSuccessListener { onLoginSuccess(phone, false) }
                                }
                            } else Toast.makeText(context, "Wrong OTP", Toast.LENGTH_SHORT).show()
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))) {
                            Text("Verify & Start", color = Color.Black)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("Admin PIN (9954)") }, singleLine = true)
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = { if (pin == "9954") onLoginSuccess("ADMIN", true) else Toast.makeText(context, "Wrong PIN", Toast.LENGTH_SHORT).show() }) {
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
    val context = LocalContext.current

    val payPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val stream = context.contentResolver.openInputStream(it)
            val bmp = BitmapFactory.decodeStream(stream)
            val scaled = Bitmap.createScaledBitmap(bmp, 300, 400, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 60, out)
            receipt = Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
            Toast.makeText(context, "Screenshot Attached", Toast.LENGTH_SHORT).show()
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
        rtdb.child("live_riders").child(phone).setValue(mapOf("phone" to phone, "time" to System.currentTimeMillis()))
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
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Status") }, icon = {})
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, label = { Text("₹49 Plan") }, icon = {})
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, label = { Text("Refer") }, icon = {})
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, label = { Text("Chat") }, icon = {})
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color(0xFF0F172A))) {
            when (tab) {
                0 -> {
                    Column(Modifier.padding(16.dp)) {
                        Card(colors = CardDefaults.cardColors(containerColor = if (active) Color(0xFFDCFCE7) else Color(0xFFFEE2E2))) {
                            Column(Modifier.padding(16.dp)) {
                                Text(if (active) "✅ Plan Active" else "⚠️ Plan Expired", fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    }
                }
                1 -> {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("₹49 for 30 Days", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(10.dp))
                        if (qr.isNotEmpty()) {
                            val bytes = Base64.decode(qr, Base64.DEFAULT)
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bmp != null) Image(bitmap = bmp.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(150.dp))
                        } else {
                            Text("UPI ID: $upi", color = Color.White)
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { payPicker.launch("image/*") }) { Text(if (receipt.isEmpty()) "Attach Payment Screenshot" else "✅ Attached") }
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = {
                            if (receipt.isNotEmpty()) {
                                db.collection("payments").add(mapOf("phone" to phone, "receipt" to receipt, "status" to "pending", "time" to System.currentTimeMillis()))
                                receipt = ""
                                Toast.makeText(context, "Sent to Admin!", Toast.LENGTH_SHORT).show()
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))) {
                            Text("Submit Payment", color = Color.Black)
                        }
                    }
                }
                2 -> {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Refer & Earn (5 Days Free)", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = {
                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Join Captain Smart Pro using my number $phone as Referral Code for 6 Days Trial!")
                            }
                            context.startActivity(Intent.createChooser(intent, "Share"))
                        }) { Text("Share on WhatsApp") }
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
                Text("$s: $t", color = Color.White)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(value = msg, onValueChange = { msg = it }, modifier = Modifier.weight(1f))
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
    val payments = remember { mutableStateListOf<Pair<String, String>>() }
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
            snap?.documents?.forEach { payments.add(it.id to (it.getString("phone") ?: "")) }
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
            Row(Modifier.fillMaxWidth().background(Color(0xFF0F172A)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Admin Panel", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Logout", fontSize = 10.sp) }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Payments") }, icon = {})
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, label = { Text("QR") }, icon = {})
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, label = { Text("Profiles") }, icon = {})
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, label = { Text("Chat") }, icon = {})
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color(0xFF0F172A))) {
            when (tab) {
                0 -> {
                    LazyColumn(Modifier.padding(16.dp)) {
                        items(payments) { (id, ph) ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(Modifier.padding(8.dp)) {
                                    Text("Phone: $ph")
                                    Button(onClick = {
                                        val newExp = System.currentTimeMillis() + 86400000L * 30
                                        db.collection("riders").document(ph).update("expiry", newExp)
                                        db.collection("payments").document(id).update("status", "approved")
                                        Toast.makeText(context, "Approved!", Toast.LENGTH_SHORT).show()
                                    }) { Text("Approve 30 Days") }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(onClick = { qrPicker.launch("image/*") }) { Text("Upload New QR Code") }
                    }
                }
                2 -> {
                    LazyColumn(Modifier.padding(16.dp)) {
                        items(riders) { r ->
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(Modifier.padding(8.dp)) {
                                    Text("Phone: ${r.phone} | Vehicle: ${r.vehicle}")
                                    Row {
                                        if (r.profilePic.isNotEmpty()) {
                                            val b = Base64.decode(r.profilePic, Base64.DEFAULT)
                                            val bmp = BitmapFactory.decodeByteArray(b, 0, b.size)
                                            if (bmp != null) Image(bitmap = bmp.asImageBitmap(), contentDescription = "", modifier = Modifier.size(50.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    Column(Modifier.padding(16.dp)) {
                        Row {
                            OutlinedTextField(value = chatPhone, onValueChange = { chatPhone = it }, label = { Text("Phone Number") }, modifier = Modifier.weight(1f))
                            Button(onClick = { activeChat = chatPhone }) { Text("Open") }
                        }
                        if (activeChat.isNotEmpty()) {
                            ChatScreen(phone = activeChat, isAdmin = true, rtdb = rtdb)
                        }
                    }
                }
            }
        }
    }
}
