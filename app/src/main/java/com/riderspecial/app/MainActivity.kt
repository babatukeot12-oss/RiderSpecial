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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@SuppressLint("HardwareIds")
@Composable
fun CaptainSmartApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("CaptainSmartPrefs", Context.MODE_PRIVATE) }
    val db = remember { FirebaseFirestore.getInstance() }
    val rtdb = remember { FirebaseDatabase.getInstance().reference }

    // हार्डवेयर Device ID लॉक (एक फ़ोन पर एक ही बार 24-घंटे का फ्री डेमो)
    val deviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    var loggedInPhone by remember { mutableStateOf(prefs.getString("user_phone", "") ?: "") }
    var isAdmin by remember { mutableStateOf(prefs.getBoolean("is_admin", false)) }

    if (loggedInPhone.isEmpty()) {
        LoginScreen(
            deviceId = deviceId,
            db = db,
            onLoginSuccess = { phone, admin ->
                prefs.edit().putString("user_phone", phone).putBoolean("is_admin", admin).apply()
                loggedInPhone = phone
                isAdmin = admin
            }
        )
    } else if (isAdmin) {
        AdminDashboard(
            db = db,
            rtdb = rtdb,
            onLogout = {
                prefs.edit().remove("user_phone").remove("is_admin").apply()
                loggedInPhone = ""
                isAdmin = false
            }
        )
    } else {
        RiderDashboard(
            phone = loggedInPhone,
            deviceId = deviceId,
            prefs = prefs,
            db = db,
            rtdb = rtdb,
            onLogout = {
                prefs.edit().remove("user_phone").remove("is_admin").apply()
                loggedInPhone = ""
            }
        )
    }
}

@Composable
fun LoginScreen(
    deviceId: String,
    db: FirebaseFirestore,
    onLoginSuccess: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    var phone by remember { mutableStateOf("") }
    var otpInput by remember { mutableStateOf("") }
    var generatedOtp by remember { mutableStateOf("") }
    var adminPin by remember { mutableStateOf("") }
    var showAdminBox by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("CAPTAIN SMART", color = Color(0xFF22C55E), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Rider Special • 0.00s Auto-Accept", color = Color.LightGray, fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("मोबाइल नंबर से लॉगिन करें", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { if (it.length <= 10) phone = it.filter { c -> c.isDigit() } },
                    label = { Text("10 अंकों का मोबाइल नंबर") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))

                if (generatedOtp.isEmpty()) {
                    Button(
                        onClick = {
                            if (phone.length == 10) {
                                val code = (1000..9999).random().toString()
                                generatedOtp = code
                                Toast.makeText(context, "आपका OTP कोड है: $code", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "सही 10 अंकों का नंबर डालें", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                    ) {
                        Text("OTP भेजें", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("OTP कोड: $generatedOtp", color = Color(0xFFFACC15), fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = otpInput,
                        onValueChange = { otpInput = it },
                        label = { Text("4-अंकों का OTP दर्ज करें") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        enabled = !loading,
                        onClick = {
                            if (otpInput == generatedOtp) {
                                loading = true
                                val userRef = db.collection("riders").document(phone)
                                val deviceRef = db.collection("devices").document(deviceId)

                                userRef.get().addOnSuccessListener { userDoc ->
                                    if (userDoc.exists()) {
                                        loading = false
                                        onLoginSuccess(phone, false)
                                    } else {
                                        deviceRef.get().addOnSuccessListener { devDoc ->
                                            val now = System.currentTimeMillis()
                                            val expiry = if (devDoc.exists()) {
                                                0L
                                            } else {
                                                deviceRef.set(mapOf("firstPhone" to phone, "usedAt" to now))
                                                now + 24L * 60L * 60L * 1000L
                                            }
                                            val userData = hashMapOf(
                                                "phone" to phone,
                                                "deviceId" to deviceId,
                                                "expiry" to expiry,
                                                "plan" to if (expiry > now) "24h Free Demo" else "Expired",
                                                "createdAt" to now
                                            )
                                            userRef.set(userData).addOnSuccessListener {
                                                loading = false
                                                onLoginSuccess(phone, false)
                                            }
                                        }
                                    }
                                }
                            } else {
                                Toast.makeText(context, "गलत OTP!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                    ) {
                        Text(if (loading) "जाँच हो रही है..." else "सत्यापित करें और आगे बढ़ें", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { showAdminBox = !showAdminBox }) {
                    Text("Admin Login (एडमिन पैनल)", color = Color(0xFF38BDF8))
                }
                if (showAdminBox) {
                    OutlinedTextField(
                        value = adminPin,
                        onValueChange = { adminPin = it },
                        label = { Text("Admin PIN (9954)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (adminPin == "9954") {
                                onLoginSuccess("ADMIN", true)
                            } else {
                                Toast.makeText(context, "गलत Admin PIN", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Admin डैशबोर्ड खोलें")
                    }
                }
            }
        }
    }
}

@Composable
fun RiderDashboard(
    phone: String,
    deviceId: String,
    prefs: android.content.SharedPreferences,
    db: FirebaseFirestore,
    rtdb: DatabaseReference,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var tabIndex by remember { mutableIntStateOf(0) }
    var expiry by remember { mutableLongStateOf(prefs.getLong("sub_expiry", 0L)) }
    var planName by remember { mutableStateOf("लोड हो रहा है...") }
    var announcement by remember { mutableStateOf("Captain Smart में आपका स्वागत है!") }
    var qrBase64 by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("dinakeot@upi") }

    var autoEnabled by remember { mutableStateOf(prefs.getBoolean("auto_accept_enabled", false)) }
    var minFare by remember { mutableStateOf(prefs.getFloat("min_fare", 30f).toInt().toString()) }
    var maxDist by remember { mutableStateOf(prefs.getFloat("max_dist", 10f).toInt().toString()) }
    var minPerKm by remember { mutableStateOf(prefs.getFloat("min_per_km", 8f).toInt().toString()) }

    var appRapido by remember { mutableStateOf(prefs.getBoolean("app_rapido", true)) }
    var appOla by remember { mutableStateOf(prefs.getBoolean("app_ola", true)) }
    var appUber by remember { mutableStateOf(prefs.getBoolean("app_uber", true)) }
    var appZomato by remember { mutableStateOf(prefs.getBoolean("app_zomato", true)) }
    var appSwiggy by remember { mutableStateOf(prefs.getBoolean("app_swiggy", true)) }

    LaunchedEffect(phone) {
        db.collection("riders").document(phone).addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                val exp = doc.getLong("expiry") ?: 0L
                expiry = exp
                planName = doc.getString("plan") ?: "Active"
                prefs.edit().putLong("sub_expiry", exp).apply()
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
    val remainingHours = if (isSubscribed) ((expiry - System.currentTimeMillis()) / (1000 * 60 * 60)) else 0

    Scaffold(
        topBar = {
            Column(Modifier.background(Color(0xFF0F172A)).padding(14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Captain Smart", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("ID: $phone • ${if (isSubscribed) "$planName ($remainingHours घंटे शेष)" else "प्लान समाप्त"}", color = Color.White, fontSize = 12.sp)
                    }
                    TextButton(onClick = onLogout) {
                        Text("Logout", color = Color(0xFFF87171))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("📢 $announcement", color = Color(0xFFFACC15), fontSize = 12.sp)
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tabIndex == 0, onClick = { tabIndex = 0 }, label = { Text("Auto-Accept") }, icon = {})
                NavigationBarItem(selected = tabIndex == 1, onClick = { tabIndex = 1 }, label = { Text("₹49 रिचार्ज") }, icon = {})
                NavigationBarItem(selected = tabIndex == 2, onClick = { tabIndex = 2 }, label = { Text("लाइव चैट") }, icon = {})
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tabIndex) {
                0 -> {
                    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSubscribed) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                                )
                            ) {
                                Column(Modifier.padding(16.dp).fillMaxWidth()) {
                                    Text(
                                        if (isSubscribed) "✅ आपका प्लान सक्रिय है" else "⚠️ फ्री डेमो / सब्सक्रिप्शन समाप्त",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                    Text(
                                        if (isSubscribed) "वैधता: ${SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(expiry))}"
                                        else "Auto-Accept चालू रखने के लिए ₹49 (30 दिन) का प्लान लें।",
                                        fontSize = 13.sp,
                                        color = Color.DarkGray
                                    )
                                }
                            }
                        }

                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("0.00s Auto-Accept चालू करें", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Switch(
                                            checked = autoEnabled && isSubscribed,
                                            onCheckedChange = {
                                                if (!isSubscribed) {
                                                    Toast.makeText(context, "पहले ₹49 का प्लान सक्रिय करें!", Toast.LENGTH_SHORT).show()
                                                    tabIndex = 1
                                                } else {
                                                    autoEnabled = it
                                                    prefs.edit().putBoolean("auto_accept_enabled", it).apply()
                                                }
                                            }
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("1. Accessibility Permission दें (जरूरी)")
                                    }
                                }
                            }
                        }

                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("राइड और ऑर्डर फ़िल्टर", fontWeight = FontWeight.Bold)
                                    OutlinedTextField(
                                        value = minFare,
                                        onValueChange = { minFare = it.filter { c -> c.isDigit() } },
                                        label = { Text("न्यूनतम किराया (Min ₹)") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = maxDist,
                                        onValueChange = { maxDist = it.filter { c -> c.isDigit() } },
                                        label = { Text("अधिकतम दूरी (Max Km)") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = minPerKm,
                                        onValueChange = { minPerKm = it.filter { c -> c.isDigit() } },
                                        label = { Text("न्यूनतम रेट (₹ प्रति Km)") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = {
                                            prefs.edit()
                                                .putFloat("min_fare", minFare.toFloatOrNull() ?: 30f)
                                                .putFloat("max_dist", maxDist.toFloatOrNull() ?: 10f)
                                                .putFloat("min_per_km", minPerKm.toFloatOrNull() ?: 8f)
                                                .apply()
                                            Toast.makeText(context, "फ़िल्टर सेव हो गए!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("फ़िल्टर सेव करें")
                                    }
                                }
                            }
                        }

                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("किन ऐप्स पर ऑटो-एक्सेप्ट चले?", fontWeight = FontWeight.Bold)
                                    AppToggleRow("Rapido Captain", appRapido) { appRapido = it; prefs.edit().putBoolean("app_rapido", it).apply() }
                                    AppToggleRow("Ola Driver", appOla) { appOla = it; prefs.edit().putBoolean("app_ola", it).apply() }
                                    AppToggleRow("Uber Driver", appUber) { appUber = it; prefs.edit().putBoolean("app_uber", it).apply() }
                                    AppToggleRow("Zomato Delivery", appZomato) { appZomato = it; prefs.edit().putBoolean("app_zomato", it).apply() }
                                    AppToggleRow("Swiggy Delivery", appSwiggy) { appSwiggy = it; prefs.edit().putBoolean("app_swiggy", it).apply() }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    SubscriptionScreen(phone = phone, db = db, qrBase64 = qrBase64, upiId = upiId)
                }

                2 -> {
                    ChatScreen(phone = phone, isAdminSender = false, rtdb = rtdb)
                }
            }
        }
    }
}

@Composable
fun AppToggleRow(name: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SubscriptionScreen(phone: String, db: FirebaseFirestore, qrBase64: String, upiId: String) {
    val context = LocalContext.current
    var utr by remember { mutableStateOf("") }
    var receiptBase64 by remember { mutableStateOf("") }
    var redeemCode by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val stream = context.contentResolver.openInputStream(it)
            val bmp = BitmapFactory.decodeStream(stream)
            val scaled = Bitmap.createScaledBitmap(bmp, 450, 750, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 65, out)
            receiptBase64 = Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
            Toast.makeText(context, "रसीद फ़ोटो जुड़ गई!", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("30 दिन अनलिमिटेड प्लान - मात्र ₹49", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    if (qrBase64.isNotEmpty()) {
                        val bytes = Base64.decode(qrBase64, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            Image(bitmap = bmp.asImageBitmap(), contentDescription = "UPI QR", modifier = Modifier.size(190.dp))
                        }
                    } else {
                        Text("UPI ID: $upiId", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("₹49 पेमेंट करके नीचे स्क्रीनशॉट या UTR नंबर भेजें", color = Color.LightGray, fontSize = 12.sp)
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("पेमेंट रसीद भेजें (Admin अप्रूवल के लिए)", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = utr,
                        onValueChange = { utr = it },
                        label = { Text("12-अंकों का UTR / Ref नंबर") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (receiptBase64.isEmpty()) "पेमेंट स्क्रीनशॉट चुनें" else "✅ स्क्रीनशॉट चुना गया")
                    }
                    Button(
                        enabled = !submitting,
                        onClick = {
                            if (utr.isBlank() && receiptBase64.isEmpty()) {
                                Toast.makeText(context, "UTR नंबर या स्क्रीनशॉट दें", Toast.LENGTH_SHORT).show()
                            } else {
                                submitting = true
                                val req = hashMapOf(
                                    "phone" to phone,
                                    "utr" to utr,
                                    "receiptBase64" to receiptBase64,
                                    "status" to "pending",
                                    "createdAt" to System.currentTimeMillis()
                                )
                                db.collection("payments").add(req).addOnSuccessListener {
                                    submitting = false
                                    utr = ""
                                    receiptBase64 = ""
                                    Toast.makeText(context, "रिक्वेस्ट भेज दी गई! एडमिन जल्द अप्रूव करेंगे।", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                    ) {
                        Text("पेमेंट सबमिट करें", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("वाउचर / 30-दिन कोड रिडीम करें", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = redeemCode,
                        onValueChange = { redeemCode = it.uppercase() },
                        label = { Text("एडमिन से मिला कोड डालें") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (redeemCode.isNotBlank()) {
                                val codeRef = db.collection("codes").document(redeemCode.trim())
                                codeRef.get().addOnSuccessListener { doc ->
                                    if (doc.exists() && doc.getBoolean("used") == false) {
                                        val newExpiry = System.currentTimeMillis() + 30L * 24L * 60L * 60L * 1000L
                                        codeRef.update(mapOf("used" to true, "usedBy" to phone))
                                        db.collection("riders").document(phone)
                                            .update(mapOf("expiry" to newExpiry, "plan" to "₹49 / 30 Days"))
                                        redeemCode = ""
                                        Toast.makeText(context, "बधाई हो! 30 दिन का प्लान चालू हो गया!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "अमान्य या पहले से उपयोग किया गया कोड!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("कोड एक्टिवेट करें")
                    }
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
                    val sender = child.child("sender").getValue(String::class.java) ?: ""
                    val text = child.child("text").getValue(String::class.java) ?: ""
                    messages.add(sender to text)
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
                    Surface(
                        color = if (isMe) Color(0xFF22C55E) else Color(0xFFE2E8F0),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(sender, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                            Text(text, color = Color.Black)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                placeholder = { Text("मैसेज लिखें...") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (message.isNotBlank()) {
                    val payload = mapOf(
                        "sender" to if (isAdminSender) "Admin" else phone,
                        "text" to message.trim(),
                        "ts" to System.currentTimeMillis()
                    )
                    rtdb.child("chats").child(phone).push().setValue(payload)
                    message = ""
                }
            }) {
                Text("भेजें")
            }
        }
    }
}

@Composable
fun AdminDashboard(
    db: FirebaseFirestore,
    rtdb: DatabaseReference,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var adminTab by remember { mutableIntStateOf(0) }
    val payments = remember { mutableStateListOf>() }
    var generatedCode by remember { mutableStateOf("") }
    var newAnnouncement by remember { mutableStateOf("") }
    var newUpi by remember { mutableStateOf("dinakeot@upi") }
    var chatPhone by remember { mutableStateOf("") }
    var activeChatPhone by remember { mutableStateOf("") }

    val qrPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val stream = context.contentResolver.openInputStream(it)
            val bmp = BitmapFactory.decodeStream(stream)
            val scaled = Bitmap.createScaledBitmap(bmp, 400, 400, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
            db.collection("config").document("settings").set(mapOf("qrBase64" to b64), com.google.firebase.firestore.SetOptions.merge())
            Toast.makeText(context, "नया UPI QR कोड लाइव हो गया!", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        db.collection("payments")
            .whereEqualTo("status", "pending")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                payments.clear()
                snap?.documents?.forEach { doc ->
                    val map = doc.data?.toMutableMap() ?: mutableMapOf()
                    map["id"] = doc.id
                    payments.add(map)
                }
            }
    }

    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF0F172A)).padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ADMIN PANEL", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                TextButton(onClick = onLogout) { Text("Logout", color = Color(0xFFF87171)) }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = adminTab == 0, onClick = { adminTab = 0 }, label = { Text("पेमेंट्स (${payments.size})") }, icon = {})
                NavigationBarItem(selected = adminTab == 1, onClick = { adminTab = 1 }, label = { Text("QR व कोड") }, icon = {})
                NavigationBarItem(selected = adminTab == 2, onClick = { adminTab = 2 }, label = { Text("राइडर चैट") }, icon = {})
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (adminTab) {
                0 -> {
                    LazyColumn(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (payments.isEmpty()) {
                            item { Text("कोई पेंडिंग पेमेंट नहीं है।", color = Color.Gray) }
                        }
                        items(payments) { item ->
                            val docId = item["id"].toString()
                            val rPhone = item["phone"].toString()
                            val utr = item["utr"].toString()
                            val rImg = item["receiptBase64"]?.toString() ?: ""

                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("राइडर मोबाइल: $rPhone", fontWeight = FontWeight.Bold)
                                    Text("UTR: $utr", fontSize = 13.sp)
                                    if (rImg.isNotEmpty()) {
                                        val bytes = Base64.decode(rImg, Base64.DEFAULT)
                                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        if (bmp != null) {
                                            Spacer(Modifier.height(6.dp))
                                            Image(bitmap = bmp.asImageBitmap(), contentDescription = "Receipt", modifier = Modifier.height(180.dp).fillMaxWidth())
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = {
                                                val newExp = System.currentTimeMillis() + 30L * 24L * 60L * 60L * 1000L
                                                db.collection("riders").document(rPhone)
                                                    .update(mapOf("expiry" to newExp, "plan" to "₹49 / 30 Days"))
                                                db.collection("payments").document(docId).update("status", "approved")
                                                Toast.makeText(context, "$rPhone का 30-दिन प्लान चालू!", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                                        ) {
                                            Text("Approve (30 दिन)", color = Color.Black)
                                        }
                                        OutlinedButton(onClick = {
                                            db.collection("payments").document(docId).update("status", "rejected")
                                        }) {
                                            Text("Reject")
                                        }
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
                                    Text("1. UPI QR कोड और UPI ID बदलें", fontWeight = FontWeight.Bold)
                                    Button(onClick = { qrPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                                        Text("गैलरी से नया QR कोड अपलोड करें")
                                    }
                                    OutlinedTextField(
                                        value = newUpi,
                                        onValueChange = { newUpi = it },
                                        label = { Text("UPI ID") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(onClick = {
                                        db.collection("config").document("settings").set(mapOf("upiId" to newUpi), com.google.firebase.firestore.SetOptions.merge())
                                        Toast.makeText(context, "UPI ID अपडेट हो गई!", Toast.LENGTH_SHORT).show()
                                    }, modifier = Modifier.fillMaxWidth()) {
                                        Text("UPI ID सेव करें")
                                    }
                                }
                            }
                        }

                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("2. नया 30-दिन सब्सक्रिप्शन कोड बनाएं", fontWeight = FontWeight.Bold)
                                    Button(onClick = {
                                        val code = "CS-" + (100000..999999).random()
                                        db.collection("codes").document(code).set(mapOf("used" to false, "createdAt" to System.currentTimeMillis()))
                                        generatedCode = code
                                    }, modifier = Modifier.fillMaxWidth()) {
                                        Text("30-दिन का नया कोड जेनरेट करें")
                                    }
                                    if (generatedCode.isNotEmpty()) {
                                        Text("जेनरेट हुआ कोड: $generatedCode", fontWeight = FontWeight.Bold, color = Color(0xFF16A34A), fontSize = 18.sp)
                                    }
                                }
                            }
                        }

                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("3. सभी राइडर्स को लाइव सूचना भेजें", fontWeight = FontWeight.Bold)
                                    OutlinedTextField(
                                        value = newAnnouncement,
                                        onValueChange = { newAnnouncement = it },
                                        label = { Text("नोटिस / ऑफर लिखें") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(onClick = {
                                        db.collection("config").document("settings").set(mapOf("announcement" to newAnnouncement), com.google.firebase.firestore.SetOptions.merge())
                                        Toast.makeText(context, "सूचना लाइव हो गई!", Toast.LENGTH_SHORT).show()
                                    }, modifier = Modifier.fillMaxWidth()) {
                                        Text("सूचना पब्लिश करें")
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = chatPhone,
                                onValueChange = { chatPhone = it.filter { c -> c.isDigit() } },
                                label = { Text("राइडर का 10-अंक मोबाइल नंबर") },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { activeChatPhone = chatPhone }) { Text("चैट खोलें") }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (activeChatPhone.isNotEmpty()) {
                            ChatScreen(phone = activeChatPhone, isAdminSender = true, rtdb = rtdb)
                        }
                    }
                }
            }
        }
    }
}
