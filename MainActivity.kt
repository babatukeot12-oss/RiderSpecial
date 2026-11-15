package com.riderspecial.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Slate = Color(0xFF101820)
private val Panel = Color(0xFF1B2935)
private val Emerald = Color(0xFF19A66A)
private val Gold = Color(0xFFFFC857)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RiderSpecialApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiderSpecialApp() {
    var isAdmin by remember { mutableStateOf(false) }
    var riderTab by remember { mutableIntStateOf(0) }
    var adminTab by remember { mutableIntStateOf(0) }
    var demoRunning by remember { mutableStateOf(false) }
    var demoCount by remember { mutableIntStateOf(0) }
    var announcement by remember { mutableStateOf("Welcome to Rider Special") }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Emerald, secondary = Gold, background = Slate,
            surface = Panel, onPrimary = Color.White, onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Rider Special", fontWeight = FontWeight.Bold) },
                    actions = {
                        TextButton(onClick = { isAdmin = !isAdmin }) {
                            Text(if (isAdmin) "Rider Mode" else "Admin Panel", color = Gold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Slate, titleContentColor = Color.White
                    )
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Panel) {
                    val labels = if (isAdmin) listOf("Riders", "Payments", "Support", "Control")
                                 else listOf("Auto-Tap", "Proofs", "Plan", "Profile", "Chat")
                    val selected = if (isAdmin) adminTab else riderTab
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { if (isAdmin) adminTab = index else riderTab = index },
                            icon = { Icon(
                                when (index) {
                                    0 -> Icons.Default.Dashboard
                                    1 -> Icons.Default.Photo
                                    2 -> Icons.Default.CreditCard
                                    3 -> Icons.Default.Person
                                    else -> Icons.Default.Chat
                                }, contentDescription = label
                            ) },
                            label = { Text(label, fontSize = 10.sp) }
                        )
                    }
                }
            },
            containerColor = Slate
        ) { padding ->
            if (isAdmin) AdminContent(adminTab, announcement, { announcement = it }, Modifier.padding(padding))
            else RiderContent(
                riderTab, demoRunning, { demoRunning = it }, demoCount,
                { demoCount++ }, announcement, Modifier.padding(padding)
            )
        }
    }
}

@Composable
fun RiderContent(
    tab: Int, running: Boolean, setRunning: (Boolean) -> Unit,
    count: Int, addCount: () -> Unit, announcement: String, modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(14.dp)) {
                Text(announcement, color = Gold, fontWeight = FontWeight.SemiBold)
                Text("Rider workspace • Demo mode", color = Color.LightGray)
            }
        }
        when (tab) {
            0 -> {
                Text("Auto-Tap Demo", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Safe simulation only. This demo does not interact with Ola, Uber, Rapido, Zomato or Swiggy.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField("₹", {}, label = { Text("Minimum fare") }, modifier = Modifier.weight(1f))
                    OutlinedTextField("2.0", {}, label = { Text("Max pickup km") }, modifier = Modifier.weight(1f))
                }
                Button(onClick = { setRunning(!running) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (running) "Stop Demo" else "Start Demo")
                }
                Button(onClick = { addCount() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Simulate Incoming Ride")
                }
                Text(if (running) "Demo engine: ON" else "Demo engine: OFF", color = Emerald)
                Text("Simulated events: $count")
            }
            1 -> {
                Text("Proof Gallery", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (count == 0) Text("No demo proofs yet. Simulate an incoming ride first.")
                else repeat(count) { Text("Demo proof #${it + 1} • Test event • Not a real ride") }
            }
            2 -> {
                Text("Plan & QR", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Demo access: 24 hours (sample screen)")
                Text("Sample plan: ₹49 / 30 days")
                Text("Payment verification is not connected in this starter build.")
            }
            3 -> {
                Text("Rider Profile", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                listOf("Name: Not set", "Mobile: Not verified", "City: Not set", "Vehicle: Not set",
                    "Plan: Demo").forEach { Text(it) }
            }
            else -> {
                Text("Support Chat", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Chat UI placeholder. Cloud messaging must be connected for real-time support.")
            }
        }
    }
}

@Composable
fun AdminContent(tab: Int, announcement: String, setAnnouncement: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Admin Control Panel", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        when (tab) {
            0 -> {
                Text("Riders & Analytics", color = Gold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("Riders", "0"); Metric("Paid", "0"); Metric("Revenue", "₹0")
                }
                Text("Rider directory will appear after a secure backend is connected.")
            }
            1 -> {
                Text("Payment Verification", color = Gold)
                Text("No pending payment requests in local demo.")
            }
            2 -> {
                Text("Support Inbox", color = Gold)
                Text("Rider conversations require a cloud messaging backend.")
            }
            else -> {
                Text("App Control & Broadcast", color = Gold)
                OutlinedTextField(
                    value = announcement, onValueChange = setAnnouncement,
                    label = { Text("Announcement for rider screen") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Changes are local to this demo build.")
                Text("Plan price: ₹49 • Duration: 30 days • Trial: 24 hours")
            }
        }
    }
}

@Composable
fun Metric(label: String, value: String) {
    Card(modifier = Modifier.widthIn(min = 85.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Color.LightGray, fontSize = 11.sp)
        }
    }
}
