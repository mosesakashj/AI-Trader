package com.example.ui.risk

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.EdgeTraderApp
import com.example.ui.components.EmergencyStopDialog
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun RiskScreen() {
    val repository = EdgeTraderApp.instance.repository
    val engine = EdgeTraderApp.instance.tradingEngine
    val config by repository.configFlow.collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    var riskSliderValue by remember(config) { mutableFloatStateOf(config?.defaultRiskPercent?.toFloat() ?: 0.25f) }
    var maxDailyLossInput by remember(config) { mutableStateOf(config?.maxDailyLossPercent?.toString() ?: "1.0") }
    var maxConsecLossInput by remember(config) { mutableStateOf(config?.maxConsecutiveLosses?.toString() ?: "3") }
    var maxPositionsInput by remember(config) { mutableStateOf(config?.maxOpenPositions?.toString() ?: "2") }

    var showEmergencyDialog by remember { mutableStateOf(false) }
    var saveStatus by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Risk Philosophy Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth().testTag("risk_philosophy_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = EmeraldGain)
                            Text("Mathematical Capital Preservation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "EdgeTrader enforces strict on-device pre-trade risk validation: Every position size is mathematically calculated from equity and ATR stop-distance. Orders are blocked if any safety parameter is breached.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // Emergency Stop Action
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF381419)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CrimsonLoss),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Emergency Kill-Switch", fontWeight = FontWeight.Bold, color = CrimsonLoss)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Halts all automated order execution immediately and engages local lockdown.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showEmergencyDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonLoss),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("risk_screen_emergency_stop_btn")
                    ) {
                        Icon(Icons.Default.Dangerous, contentDescription = null, tint = TextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("TRIGGER EMERGENCY STOP", fontWeight = FontWeight.Black, color = TextPrimary)
                    }
                }
            }
        }

        // Risk Parameters Controls
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Risk Budget & Position Sizing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)

                    // Risk Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Risk per Trade:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text("${"%.2f".format(riskSliderValue)}% of Equity", fontWeight = FontWeight.Bold, color = CyanLight)
                        }
                        Slider(
                            value = riskSliderValue,
                            onValueChange = { riskSliderValue = (it * 100).roundToInt() / 100f },
                            valueRange = 0.10f..1.00f,
                            steps = 17,
                            colors = SliderDefaults.colors(
                                thumbColor = CyanLight,
                                activeTrackColor = CyanLight,
                                inactiveTrackColor = SurfaceVariantDark
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("risk_percent_slider")
                        )
                        Text(
                            "Recommended: 0.25% (Conservative) to 0.50% (Standard)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }

                    Divider(color = CardBorderDark)

                    OutlinedTextField(
                        value = maxDailyLossInput,
                        onValueChange = { maxDailyLossInput = it },
                        label = { Text("Max Daily Loss Limit (% of Equity)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("max_daily_loss_input")
                    )

                    OutlinedTextField(
                        value = maxConsecLossInput,
                        onValueChange = { maxConsecLossInput = it },
                        label = { Text("Max Consecutive Losses Before Pause") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("max_consec_losses_input")
                    )

                    OutlinedTextField(
                        value = maxPositionsInput,
                        onValueChange = { maxPositionsInput = it },
                        label = { Text("Max Concurrent Open Positions") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("max_open_positions_input")
                    )

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val current = repository.getOrCreateConfig()
                                repository.updateConfig(
                                    current.copy(
                                        defaultRiskPercent = riskSliderValue.toDouble(),
                                        maxDailyLossPercent = maxDailyLossInput.toDoubleOrNull() ?: 1.0,
                                        maxConsecutiveLosses = maxConsecLossInput.toIntOrNull() ?: 3,
                                        maxOpenPositions = maxPositionsInput.toIntOrNull() ?: 2
                                    )
                                )
                                saveStatus = "Risk parameters successfully persisted!"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanLight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_risk_config_btn")
                    ) {
                        Text("Save Risk Configuration", fontWeight = FontWeight.Bold, color = BackgroundDark)
                    }

                    if (saveStatus.isNotBlank()) {
                        Text(saveStatus, color = EmeraldGain, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (showEmergencyDialog) {
        EmergencyStopDialog(
            onConfirm = {
                coroutineScope.launch {
                    engine.triggerEmergencyStop()
                    showEmergencyDialog = false
                }
            },
            onDismiss = { showEmergencyDialog = false }
        )
    }
}
