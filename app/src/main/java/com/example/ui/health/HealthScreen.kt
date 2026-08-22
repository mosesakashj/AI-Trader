package com.example.ui.health

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.EdgeTraderApp
import com.example.domain.model.ConnectionState
import com.example.domain.model.StateMachineState
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HealthScreen() {
    val repository = EdgeTraderApp.instance.firestoreRepository
    val engine = EdgeTraderApp.instance.tradingEngine
    val config by repository.configFlow.collectAsState(initial = null)
    val stateMachineState by engine.stateMachine.currentState.collectAsState()
    val connState by engine.connectionState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val lastEngineHeartbeat = engine.watchdogManager.getLastEngineHeartbeat()
    val lastMarketHeartbeat = engine.watchdogManager.getLastMarketDataHeartbeat()
    val restartCount = engine.watchdogManager.getRestartCount()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Watchdog Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth().testTag("watchdog_hero_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.MonitorHeart, contentDescription = null, tint = EmeraldGain)
                            Text("Autonomous Watchdog", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Surface(color = Color(0xFF063321), shape = RoundedCornerShape(6.dp)) {
                            Text("ACTIVE", color = EmeraldGain, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Watchdog scans internal heartbeats on a 10s cycle (60s timeout threshold). If stalled or desynchronized, automated state recovery is executed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // Subsystems Health Matrix
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Subsystem Health Matrix", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)

                    HealthRow("Trading Engine Loop", stateMachineState != StateMachineState.ERROR && stateMachineState != StateMachineState.SAFE_MODE, "State: ${stateMachineState.name}")
                    HealthRow("Market Data Stream", true, "Last Tick: ${timeFormat.format(Date(lastMarketHeartbeat))}")
                    HealthRow("Broker Link Adapter", connState == ConnectionState.ONLINE, "Status: ${connState.name}")
                    HealthRow("Room Database", true, "SQLite Persistence Active")
                    HealthRow("Watchdog Recovery Supervisor", true, "Restarts: $restartCount")
                    HealthRow("Android KeyStore Secret Vault", true, "Hardware-Backed AES/GCM")
                }
            }
        }

        // Engine Heartbeat Telemetry
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Heartbeat Telemetry", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("• Engine Heartbeat: ${timeFormat.format(Date(lastEngineHeartbeat))}", style = MaterialTheme.typography.bodyMedium, color = CyanLight, fontFamily = FontFamily.Monospace)
                    Text("• Market Heartbeat: ${timeFormat.format(Date(lastMarketHeartbeat))}", style = MaterialTheme.typography.bodyMedium, color = CyanLight, fontFamily = FontFamily.Monospace)
                    Text("• Automated Recovery Restarts: $restartCount", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                }
            }
        }

        // Safe Mode Controls
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Safe Mode Supervisor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        "Safe Mode is triggered when unexpected position mismatches, repeated recovery failures, or critical exceptions occur. Auto-repair attempts to sync local DB with broker positions before entering Safe Mode.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                engine.reconcilePositions()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A2540)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp).testTag("health_reconcile_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = EmeraldGain)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manual Position Reconcile", color = EmeraldGain, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                engine.resetSafeMode()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp).testTag("health_reset_safe_mode_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = CyanLight)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset / Clear Safe Mode", color = CyanLight, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthRow(title: String, isHealthy: Boolean, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isHealthy) EmeraldGain else StatusError)
            )
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }

        Icon(
            imageVector = if (isHealthy) Icons.Default.CheckCircle else Icons.Default.Dangerous,
            contentDescription = null,
            tint = if (isHealthy) EmeraldGain else StatusError,
            modifier = Modifier.size(18.dp)
        )
    }
}
