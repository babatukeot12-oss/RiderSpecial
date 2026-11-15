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

data class PaymentModel(
    val id: String = "",
    val phone: String = "",
    val utr: String = "",
    val receiptBase64: String = ""
)

object AppStrings {
    val texts = mapOf(
        "EN" to mapOf(
            "title" to "Captain Smart Pro",
            "subtitle" to "Multi-Vehicle • 0.00s Auto-Accept",
            "login_title" to "Rider & Driver Login",
            "phone_hint" to "10-digit Mobile Number",
            "send_otp" to "Send OTP",
            "otp_hint" to "Enter 4-digit OTP",
            "verify_btn" to "Verify & Start Working",
            "select_vehicle" to "Select Your Vehicle Type:",
            "admin_btn" to "Admin Control Panel",
            "auto_accept_tab" to "Auto-Accept",
            "recharge_tab" to "₹49 Plan",
            "chat_tab" to "Live Support"
        ),
        "HI" to mapOf(
            "title" to "कैप्टन स्मार्ट प्रो",
            "subtitle" to "मल्टी-वेहिकल • 0.00s ऑटो-एक्सेप्ट",
            "login_title" to "राइडर और ड्राइवर लॉगिन",
            "phone_hint" to "10 अंकों का मोबाइल नंबर",
            "send_otp" to "OTP भेजें",
            "otp_hint" to "4-अंकों का OTP दर्ज करें",
            "verify_btn" to "सत्यापित करें और काम शुरू करें",
            "select_vehicle" to "अपनी गाड़ी चुनें:",
            "admin_btn" to "एडमिन कंट्रोल पैनल",
            "auto_accept_tab" to "ऑटो-एक्सेप्ट",
            "recharge_tab" to "₹49 प्लान",
            "chat_tab" to "लाइव सपोर्ट"
        ),
        "AS" to mapOf(
            "title" to "কেপ্টেইন স্মাৰ্ট প্ৰ'",
            "subtitle" to "মাল্টি-ভেনিকেল • 0.00s অটো-গ্ৰহণ",
            "login_title" to "ৰাইডাৰ আৰু ড্ৰাইভাৰ লগইন",
            "phone_hint" to "১০ টা সংখ্যাৰ মোবাইল নম্বৰ",
            "send_otp" to "OTP পঠিয়াওক",
            "otp_hint" to "৪ টা সংখ্যাৰ OTP দিয়ক",
            "verify_btn" to "যাচাই কৰক আৰু কাম আৰম্ভ কৰক",
            "select_vehicle" to "আপোনাৰ বাহন বাছক:",
            "admin_btn" to "এডমিন কণ্ট্ৰোল পেনেল",
            "auto_accept_tab" to "অটো-এক্সেপ্ট",
            "recharge_tab" to "₹49 পৰিকল্পনা",
            "chat_tab" to "লাইভ সহায়"
        )
    )
    fun get(lang: String, key: String): String = texts[lang]?.get(key) ?: texts["EN"]?.get(key) ?: key
}

@SuppressLint("HardwareIds")
@Composable
fun CaptainSmartApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("CaptainSmartPrefs", Context.MODE_PRIVATE) }
    val db = remember { FirebaseFirestore.getInstance() }
    val rtdb = remember { FirebaseDatabase.getInstance().reference }

    var currentLang by remember { mutableStateOf(prefs.getString("app_lang", "HI") ?: "HI") }
    val deviceId = remember { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device" }

    var loggedInPhone by remember { mutableStateOf(prefs.getString("user_phone", "") ?: "") }
    var isAdmin by remember { mutableStateOf(prefs.getBoolean("is_admin", false)) }

    if (loggedInPhone.isEmpty()) {
        LoginScreen(
            deviceId = deviceId, db = db, currentLang = currentLang,
            onLangChange = { lang -> currentLang = lang; prefs.edit().putString("app_lang", lang).apply() },
            onLoginSuccess = { phone, admin ->
                prefs.edit().putString("user_phone", phone).putBoolean("is_admin", admin).apply()
                loggedInPhone = phone; isAdmin = admin
            }
        )
    } else if (isAdmin) {
        AdminDashboard(
            db = db, rtdb = rtdb, currentLang = currentLang,
            onLangChange = { lang -> currentLang = lang; prefs.edit().putString("app_lang", lang).apply() },
            onLogout = { prefs.edit().remove("user_phone").remove("is_admin").apply(); loggedInPhone = ""; isAdmin = false }
        )
    } else {
        RiderDashboard(
            phone = loggedInPhone, deviceId = deviceId, prefs = prefs, db = db, rtdb = rtdb, currentLang = currentLang,
            onLangChange = { lang -> currentLang = lang; prefs.edit().putString("app_lang", lang).apply() },
            onLogout = { prefs.edit().remove("user_phone").remove("is_admin").apply(); loggedInPhone = "" }
        )
    }
}

@Composable
fun LanguageSelector(currentLang: String, onLangChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("EN" to "English", "HI" to "हिंदी", "AS" to "অসমীয়া").forEach { (code, label) ->
            Button(
                onClick = { onLangChange(code) },
                colors = ButtonDefaults.buttonColors(containerColor = if (currentLang == code) Color(0xFF22C55E) else Color(0xFF334155)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(label, color = if (currentLang == code) Color.Black else Color.White, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun LoginScreen(deviceId: String, db: FirebaseFirestore, currentLang: String, onLangChange: (String) -> Unit, onLoginSuccess: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    var phone by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }
    var generatedOtp by remember { mutableStateOf("") }
    var selectedVehicle by remember { mutableStateOf("Bike") }
    var profilePicBase64 by remember { mutableStateOf("") }
    var adminPin by remember { mutableStateOf("") }
    var showAdminBox by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val stream = context.contentResolver.openInputStream(it)
            val bmp = BitmapFactory.decodeStream(stream)
            val scaled = Bitmap.createScaledBitmap(bmp, 200, 200, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
            profilePicBase64 = Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
            Toast.makeText(context, "Profile Photo Added!", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B)))).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        item {
            LanguageSelector(currentLang, onLangChange)
            Spacer(Modifier.height(14.dp))
            Text(AppStrings.get(currentLang, "title"), color = Color(0xFF22C55E), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(AppStrings.get(currentLang, "subtitle"), color = Color.LightGray, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(Color.Gray).border(2.dp, Color(0xFF22C55E), CircleShape), contentAlignment = Alignment.Center) {
                        if (profilePicBase64.isNotEmpty()) {
                            val bytes = Base64.decode(profilePicBase64, Base64.DEFAULT)
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bmp != null) Image(bitmap = bmp.asImageBitmap(), contentDescription = "Profile", modifier = Modifier.fillMaxSize())
                        } else {
                            Text("Foto", color = Color.White, fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = { imagePicker.launch("image/*") }) { Text("📸 Upload Profile Photo", color = Color(0xFF38BDF8), fontSize = 12.sp) }
                    Spacer(Modifier.height(10.dp))

                    Text(AppStrings.get(currentLang, "select_vehicle"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("Bike", "Scooty", "Auto", "E-Rickshaw/Go-Go").forEach { v ->
                            Button(
                                onClick = { selectedVehicle = v },
                                colors = ButtonDefaults.buttonColors(containerColor = if (selectedVehicle == v) Color(0xFF22C55E) else Color(0xFF334155)),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(v, color = if (selectedVehicle == v) Color.Black else Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { if (it.length <= 10) phone = it.filter { c -> c.isDigit() } },
                        label = { Text(AppStrings.get(currentLang, "phone_hint")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))

                    if (generatedOtp.isEmpty()) {
                        Button(
                            onClick = {
                                if (phone.length == 10) {
                                    generatedOtp = (1000..9999).random().toString()
                                    Toast.makeText(context, "OTP Code: $generatedOtp", Toast.LENGTH_LONG).show()
                                } else Toast.makeText(context, "Enter 10 digit number", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                        ) { Text(AppStrings.get(currentLang, "send_otp"), color = Color.Black, fontWeight = FontWeight.Bold) }
                    } else {
                        Text("OTP: $generatedOtp", color = Color(0xFFFACC15), fontSize = 14.sp)
                        OutlinedTextField(value = otpInput, onValueChange = { otpInput = it }, label = { Text(AppStrings.get(currentLang, "otp_hint")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                if (otpInput == generatedOtp) {
                                    val userRef = db.collection("riders").document(phone)
                                    userRef.get().addOnSuccessListener { doc ->
                                        val now = System.currentTimeMillis()
                                        val expiry = if (doc.exists()) doc.getLong("expiry") ?: (now + 24L * 3600000L) else (now + 24L * 3600000L)
                                        val data = mapOf(
                                            "phone" to phone, "vehicle" to selectedVehicle, "profilePic" to profilePicBase64,
                                            "deviceId" to deviceId, "expiry" to expiry, "plan" to "Active / Free Demo"
                                        )
                                        userRef.set(data).addOnSuccessListener { onLoginSuccess(phone, false) }
                                    }
                                } else Toast.makeText(context, "Wrong OTP", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                        ) { Text(AppStrings.get(currentLang, "verify_btn"), color = Color.Black, fontWeight = FontWeight.Bold) }
                    }

                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { showAdminBox = !showAdminBox }) { Text(AppStrings.get(currentLang, "admin_btn"), color = Color(0xFF38BDF8)) }
                    if (showAdminBox) {
                        OutlinedTextField(value = adminPin, onValueChange = { adminPin = it }, label = { Text("Admin PIN (9954)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = { if (adminPin == "9954") onLoginSuccess("ADMIN", true) else Toast.makeText(context, "Wrong PIN", Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Open Admin Panel")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RiderDashboard(
    phone: String, deviceId: String, prefs: android.content.SharedPreferences,
    db: FirebaseFirestore, rtdb: DatabaseReference, currentLang: String,
    onLangChange: (String) -> Unit, onLogout: () -> Unit
) {
    val context = LocalContext.current
    var tabIndex by remember { mutableStateOf(0) }
    var expiry by remember { mutableStateOf(prefs.getLong("sub_expiry", 0L)) }
    var vehicleType by remember { mutableStateOf("Bike") }
    var profilePic by remember { mutableStateOf("") }
    var announcement by remember { mutableStateOf("Welcome to Captain Smart Pro!") }
    var qrBase64 by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("dinakeot@upi") }
    var autoEnabled by remember { mutableStateOf(prefs.getBoolean("auto_accept_enabled", false)) }

    LaunchedEffect(phone) {
        db.collection("riders").document(phone).addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                expiry = doc.getLong("expiry") ?: 0L
                vehicleType = doc.getString("vehicle") ?: "Bike"
                profilePic = doc.getString("profilePic") ?: ""
                prefs.edit().putLong("sub_expiry", expiry).apply()
            }
        }
        db.collection("config").document("settings").addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                announcement = doc.getString("announcement") ?: announcement
                qrBase64 = doc.getString("qrBase64") ?: ""
                upiId = doc.getString("upiId") ?: upiId
            }
        }
    }

    val isSubscribed = System.currentTimeMillis() < expiry
    val remainingHours = if (isSubscribed) ((expiry - System.currentTimeMillis()) / 3600000L) else 0

    Scaffold(
        topBar = {
            Column(Modifier.background(Color(0xFF0F172A)).padding(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (profilePic.isNotEmpty()) {
                            val bytes = Base64.decode(profilePic, Base64.DEFAULT)
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bmp != null) Image(bitmap = bmp.asImageBitmap(), contentDescription = "Avatar", modifier = Modifier.size(36.dp).clip(CircleShape))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Captain Smart • $vehicleType", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("ID: $phone • ${if (isSubscribed) "${remainingHours}h left" else "Expired"}", color = Color.White, fontSize = 11.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LanguageSelector(currentLang, onLangChange)
                        TextButton(onClick = onLogout) { Text("Logout", color = Color(0xFFF87171), fontSize = 11.sp) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("📢 $announcement", color = Color(0xFFFACC15), fontSize = 12.sp)
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tabIndex == 0, onClick = { tabIndex = 0 }, label = { Text(AppStrings.get(currentLang, "auto_accept_tab")) }, icon = {})
                NavigationBarItem(selected = tabIndex == 1, onClick = { tabIndex = 1 }, label = { Text(AppStrings.get(currentLang, "recharge_tab")) }, icon = {})
                NavigationBarItem(selected = tabIndex == 2, onClick = { tabIndex = 2 }, label = { Text(AppStrings.get(currentLang, "chat_tab")) }, icon = {})
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tabIndex) {
                0 -> {
                    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = if (isSubscribed) Color(0xFFDCFCE7) else Color(0xFFFEE2E2))) {
                                Column(Modifier.padding(14.dp).fillMaxWidth()) {
                                    Text(if (isSubscribed) "✅ Plan Active ($vehicleType Mode)" else "⚠️ Subscription Expired", fontWeight = FontWeight.Bold, color = Color.Black)
                                    Text(if (isSubscribed) "Valid till: ${SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(expiry))}" else "Renew ₹49 for 30 days unlimited.", fontSize = 12.sp, color = Color.DarkGray)
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("0.00s Auto-Accept ($vehicleType)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Switch(checked = autoEnabled && isSubscribed, onCheckedChange = {
                                            if (!isSubscribed) tabIndex = 1 else { autoEnabled = it; prefs.edit().putBoolean("auto_accept_enabled", it).apply() }
                                        })
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
                                        Text("1. Accessibility Permission (Required)")
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> SubscriptionScreen(phone = phone, db = db, qrBase64 = qrBase64, upiId = upiId)
                2 -> ChatScreen(phone = phone, isAdminSender = false, rtdb = rtdb)
            }
        }
    }
}

@Composable
fun SubscriptionScreen(phone: String, db: FirebaseFirestore, qrBase64: String, upiId: String) {
    val context = LocalContext.current
    var utr by remember { mutableStateOf("") }
    var redeemCode by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("30 Days Unlimited - ₹49", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    if (qrBase64.isNotEmpty()) {
                        val bytes = Base64.decode(qrBase64, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) Image(bitmap = bmp.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(170.dp))
                    } else Text("UPI ID: $upiId", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Submit UTR / Ref Number", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = utr, onValueChange = { utr = it }, label = { Text("12-digit UTR") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        if (utr.isNotBlank()) {
                            val req = mapOf("phone" to phone, "utr" to utr, "status" to "pending", "createdAt" to System.currentTimeMillis())
                            db.collection("payments").add(req).addOnSuccessListener { utr = ""; Toast.makeText(context, "Submitted to Admin!", Toast.LENGTH_SHORT).show() }
                        }
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))) {
                        Text("Submit Payment", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Redeem 30-Day Code", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = redeemCode, onValueChange = { redeemCode = it.uppercase() }, label = { Text("Enter Code") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        if (redeemCode.isNotBlank()) {
                            db.collection("codes").document(redeemCode.trim()).get().addOnSuccessListener { doc ->
                                if (doc.exists() && doc.getBoolean("used") == false) {
                                    val newExp = System.currentTimeMillis() + 30L * 24L * 3600000L
                                    db.collection("codes").document(redeemCode.trim()).update(mapOf("used" to true, "usedBy" to phone))
                                    db.collection("riders").document(phone).update(mapOf("expiry" to newExp, "plan" to "₹49 / 30 Days"))
                                    redeemCode = ""
                                    Toast.makeText(context, "Activated for 30 Days!", Toast.LENGTH_LONG).show()
                                } else Toast.makeText(context, "Invalid Code", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Activate Code") }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(phone: String, isAdminSender: Boolean, rtdb: DatabaseReference) {
    var message by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<Pair<String, String>>() }

    LaunchedEffect(phone) {
        rtdb.child("chats").child(phone).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messages.clear()
                for (child in snapshot.children) {
                    messages.add((child.child("sender").getValue(String::class.java) ?: "") to (child.child("text").getValue(String::class.java) ?: ""))
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(messages) { (sender, text) ->
                val isMe = (isAdminSender && sender == "Admin") || (!isAdminSender && sender != "Admin")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
                    Surface(color = if (isMe) Color(0xFF22C55E) else Color(0xFFE2E8F0), shape = RoundedCornerShape(10.dp)) {
                        Column(Modifier.padding(8.dp)) {
                            Text(sender, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                            Text(text, color = Color.Black)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = message, onValueChange = { message = it }, placeholder = { Text("Type message...") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (message.isNotBlank()) {
                    rtdb.child("chats").child(phone).push().setValue(mapOf("sender" to if (isAdminSender) "Admin" else phone, "text" to message.trim(), "ts" to System.currentTimeMillis()))
                    message = ""
                }
            }) { Text("Send") }
        }
    }
}

@Composable
fun AdminDashboard(
    db: FirebaseFirestore, rtdb: DatabaseReference, currentLang: String,
    onLangChange: (String) -> Unit, onLogout: () -> Unit
) {
    val context = LocalContext.current
    var adminTab by remember { mutableStateOf(0) }
    val payments = remember { mutableStateListOf<PaymentModel>() }
    var generatedCode by remember { mutableStateOf("") }
    var newAnnouncement by remember { mutableStateOf("") }
    var newUpi by remember { mutableStateOf("dinakeot@upi") }
    var chatPhone by remember { mutableStateOf("") }
    var activeChatPhone by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        db.collection("payments").whereEqualTo("status", "pending").orderBy("createdAt", Query.Direction.DESCENDING).addSnapshotListener { snap, _ ->
            payments.clear()
            snap?.documents?.forEach { doc ->
                payments.add(PaymentModel(doc.id, doc.getString("phone") ?: "", doc.getString("utr") ?: "", ""))
            }
        }
    }

    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().background(Color(0xFF0F172A)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ADMIN PANEL", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LanguageSelector(currentLang, onLangChange)
                    TextButton(onClick = onLogout) { Text("Logout", color = Color(0xFFF87171), fontSize = 12.sp) }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = adminTab == 0, onClick = { adminTab = 0 }, label = { Text("Payments (${payments.size})") }, icon = {})
                NavigationBarItem(selected = adminTab == 1, onClick = { adminTab = 1 }, label = { Text("Broadcast & QR") }, icon = {})
                NavigationBarItem(selected = adminTab == 2, onClick = { adminTab = 2 }, label = { Text("Chat Support") }, icon = {})
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (adminTab) {
                0 -> {
                    LazyColumn(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (payments.isEmpty()) item { Text("No pending payments.", color = Color.Gray) }
                        items(payments) { item ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("Rider Phone: ${item.phone}", fontWeight = FontWeight.Bold)
                                    Text("UTR Number: ${item.utr}", fontSize = 13.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = {
                                        val newExp = System.currentTimeMillis() + 30L * 24L * 3600000L
                                        db.collection("riders").document(item.phone).update(mapOf("expiry" to newExp, "plan" to "₹49 / 30 Days"))
                                        db.collection("payments").document(item.id).update("status", "approved")
                                        Toast.makeText(context, "Approved 30 Days!", Toast.LENGTH_SHORT).show()
                                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))) {
                                        Text("Approve 30 Days", color = Color.Black)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("1. Update UPI ID", fontWeight = FontWeight.Bold)
                                    OutlinedTextField(value = newUpi, onValueChange = { newUpi = it }, label = { Text("UPI ID") }, modifier = Modifier.fillMaxWidth())
                                    Button(onClick = {
                                        db.collection("config").document("settings").set(mapOf("upiId" to newUpi), com.google.firebase.firestore.SetOptions.merge())
                                        Toast.makeText(context, "UPI Saved!", Toast.LENGTH_SHORT).show()
                                    }, modifier = Modifier.fillMaxWidth()) { Text("Save UPI ID") }
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("2. Generate 30-Day Voucher Code", fontWeight = FontWeight.Bold)
                                    Button(onClick = {
                                        val code = "CS-" + (100000..999999).random()
                                        db.collection("codes").document(code).set(mapOf("used" to false, "createdAt" to System.currentTimeMillis()))
                                        generatedCode = code
                                    }, modifier = Modifier.fillMaxWidth()) { Text("Generate Code") }
                                    if (generatedCode.isNotEmpty()) Text("Code: $generatedCode", fontWeight = FontWeight.Bold, color = Color(0xFF16A34A), fontSize = 18.sp)
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("3. Broadcast Live Notice to All Riders", fontWeight = FontWeight.Bold)
                                    OutlinedTextField(value = newAnnouncement, onValueChange = { newAnnouncement = it }, label = { Text("Notice Text") }, modifier = Modifier.fillMaxWidth())
                                    Button(onClick = {
                                        db.collection("config").document("settings").set(mapOf("announcement" to newAnnouncement), com.google.firebase.firestore.SetOptions.merge())
                                        Toast.makeText(context, "Broadcasted live!", Toast.LENGTH_SHORT).show()
                                    }, modifier = Modifier.fillMaxWidth()) { Text("Publish Notice") }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = chatPhone, onValueChange = { chatPhone = it.filter { c -> c.isDigit() } }, label = { Text("Rider Mobile Number") }, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { activeChatPhone = chatPhone }) { Text("Open Chat") }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (activeChatPhone.isNotEmpty()) ChatScreen(phone = activeChatPhone, isAdminSender = true, rtdb = rtdb)
                    }
                }
            }
        }
    }
}
