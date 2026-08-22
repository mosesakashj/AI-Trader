package com.example.ui.strategy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.EdgeTraderApp
import com.example.ui.components.FactorChip
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun StrategyScreen() {
    val repository = EdgeTraderApp.instance.repository
    val engine = EdgeTraderApp.instance.tradingEngine
    val config by repository.configFlow.collectAsState(initial = null)
    val latestSignal by engine.latestSignal.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var fastEma by remember(config) { mutableStateOf(config?.emaFastPeriod?.toString() ?: "20") }
    var slowEma by remember(config) { mutableStateOf(config?.emaSlowPeriod?.toString() ?: "50") }
    var adxMin by remember(config) { mutableStateOf(config?.adxThreshold?.toString() ?: "25.0") }
    var atrSlMultiplier by remember(config) { mutableStateOf(config?.atrSlMultiplier?.toString() ?: "1.5") }
    var rrRatio by remember(config) { mutableStateOf(config?.riskRewardRatio?.toString() ?: "2.0") }

    var saveStatus by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Strategy Architecture Summary
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth().testTag("strategy_summary_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Conservative Trend-Pullback", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CyanLight)
                        Surface(color = SurfaceVariantDark, shape = RoundedCornerShape(6.dp)) {
                            Text("v${config?.strategyVersion ?: "1.0.0"}", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Algorithmic rules require multi-factor alignment: Closed candle confirmation, EMA trend filter, ADX momentum boundary, ATR volatility bounds, and 1:2 Risk-to-Reward ratio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // Live Signal Explainability Breakdown
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Signal Verification Matrix", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)

                    val exp = latestSignal?.explanation
                    if (exp != null) {
                        Text("Decision: ${exp.decision} on ${exp.symbol} (${exp.direction})", fontWeight = FontWeight.Bold, color = if (exp.isAllPassed) EmeraldGain else GoldHero)
                        Text("Reason: ${exp.reason}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                        Divider(color = CardBorderDark, modifier = Modifier.padding(vertical = 4.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FactorChip("1. EMA Trend Filter (Fast > Slow)", exp.trendCheck, Modifier.fillMaxWidth())
                            FactorChip("2. ADX Momentum (>= ${config?.adxThreshold})", exp.adxCheck, Modifier.fillMaxWidth())
                            FactorChip("3. Pullback to EMA Band", exp.pullbackCheck, Modifier.fillMaxWidth())
                            FactorChip("4. Closed Candle Confirmation", exp.candleCheck, Modifier.fillMaxWidth())
                            FactorChip("5. Broker Spread Within Limit", exp.spreadCheck, Modifier.fillMaxWidth())
                            FactorChip("6. Account Risk & Position Capacity", exp.riskCheck, Modifier.fillMaxWidth())
                            FactorChip("7. Allowed Trading Session Window", exp.sessionCheck, Modifier.fillMaxWidth())
                        }
                    } else {
                        Text("No active signal yet. Engine evaluates closed M15 candles.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }
        }

        // Parameter Tuner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Strategy Parameter Tuner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)

                    OutlinedTextField(
                        value = fastEma,
                        onValueChange = { fastEma = it },
                        label = { Text("Fast EMA Period") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("fast_ema_input")
                    )

                    OutlinedTextField(
                        value = slowEma,
                        onValueChange = { slowEma = it },
                        label = { Text("Slow EMA Period") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("slow_ema_input")
                    )

                    OutlinedTextField(
                        value = adxMin,
                        onValueChange = { adxMin = it },
                        label = { Text("ADX Minimum Threshold") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("adx_min_input")
                    )

                    OutlinedTextField(
                        value = atrSlMultiplier,
                        onValueChange = { atrSlMultiplier = it },
                        label = { Text("ATR Stop-Loss Multiplier") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("atr_multiplier_input")
                    )

                    OutlinedTextField(
                        value = rrRatio,
                        onValueChange = { rrRatio = it },
                        label = { Text("Risk-to-Reward Ratio (Default: 2.0 = 1:2)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("rr_ratio_input")
                    )

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val current = repository.getOrCreateConfig()
                                repository.updateConfig(
                                    current.copy(
                                        emaFastPeriod = fastEma.toIntOrNull() ?: 20,
                                        emaSlowPeriod = slowEma.toIntOrNull() ?: 50,
                                        adxThreshold = adxMin.toDoubleOrNull() ?: 25.0,
                                        atrSlMultiplier = atrSlMultiplier.toDoubleOrNull() ?: 1.5,
                                        riskRewardRatio = rrRatio.toDoubleOrNull() ?: 2.0
                                    )
                                )
                                saveStatus = "Parameters updated successfully!"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanLight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_strategy_btn")
                    ) {
                        Text("Save & Apply Parameters", fontWeight = FontWeight.Bold, color = BackgroundDark)
                    }

                    if (saveStatus.isNotBlank()) {
                        Text(saveStatus, color = EmeraldGain, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
