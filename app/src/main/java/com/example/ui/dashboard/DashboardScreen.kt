package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.StateMachineState
import com.example.domain.model.TradeDirection
import com.example.domain.model.TradingMode
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToMarkets: () -> Unit,
    onNavigateToPositions: () -> Unit,
    onNavigateToStrategy: () -> Unit
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val stateMachineState by viewModel.stateMachineState.collectAsStateWithLifecycle()
    val stateReason by viewModel.stateReason.collectAsStateWithLifecycle()
    val accountInfo by viewModel.accountInfo.collectAsStateWithLifecycle()
    val quotes by viewModel.activeQuotes.collectAsStateWithLifecycle()
    val latestSignal by viewModel.latestSignal.collectAsStateWithLifecycle()
    val dailyPnl by viewModel.dailyPnl.collectAsStateWithLifecycle()
    val openPositions by viewModel.openPositions.collectAsStateWithLifecycle()

    var showEmergencyDialog by remember { mutableStateOf(false) }
    var showCloseAllDialog by remember { mutableStateOf(false) }

    val mode = runCatching { TradingMode.valueOf(config?.mode ?: "PAPER") }.getOrDefault(TradingMode.PAPER)
    val isBotRunning = config?.isBotEnabled == true && config?.emergencyStop == false

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Status & Control Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth().testTag("dashboard_status_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPulseIndicator(state = stateMachineState)
                            ModeBadge(mode = mode)
                        }

                        // Engine Toggle Switch
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isBotRunning) "ACTIVE" else "OFF",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isBotRunning) EmeraldGain else TextMuted,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Switch(
                                checked = isBotRunning,
                                onCheckedChange = { viewModel.toggleTradingBot(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = TextPrimary,
                                    checkedTrackColor = EmeraldDark,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = SurfaceVariantDark
                                ),
                                modifier = Modifier.testTag("engine_toggle_switch")
                            )
                        }
                    }

                    if (stateReason.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "State: $stateReason",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    // Emergency Stop Banner Button
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { showEmergencyDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonLoss),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("dashboard_emergency_stop_btn")
                    ) {
                        Icon(Icons.Default.Dangerous, contentDescription = null, tint = TextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("EMERGENCY STOP (HALT ALL)", fontWeight = FontWeight.Black, color = TextPrimary)
                    }
                }
            }
        }

        // Safe Mode Alert Banner if active
        if (config?.safeMode == true || stateMachineState == StateMachineState.SAFE_MODE) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF381419)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CrimsonLoss),
                    modifier = Modifier.fillMaxWidth().testTag("safe_mode_banner")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonLoss)
                            Text("SAFE MODE ACTIVE", fontWeight = FontWeight.Bold, color = CrimsonLoss)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = config?.safeModeReason.takeIf { !it.isNullOrBlank() } ?: "System discrepancy detected. New trades blocked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.resetSafeMode() },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark),
                            modifier = Modifier.fillMaxWidth().testTag("reset_safe_mode_btn")
                        ) {
                            Text("Acknowledge & Clear Safe Mode", color = CyanLight)
                        }
                    }
                }
            }
        }

        // 2. Financial Metrics Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Account Equity",
                        value = "$${"%.2f".format(accountInfo.equity)}",
                        subtitle = "Balance: $${"%.2f".format(accountInfo.balance)}",
                        valueColor = CyanLight,
                        modifier = Modifier.weight(1f),
                        testTag = "equity_metric_card"
                    )
                    MetricCard(
                        title = "Today's P/L",
                        value = "${if (dailyPnl >= 0) "+" else ""}$${"%.2f".format(dailyPnl)}",
                        subtitle = "${if (dailyPnl >= 0) "+" else ""}${"%.2f".format((dailyPnl / 10000.0) * 100.0)}%",
                        valueColor = if (dailyPnl >= 0) EmeraldGain else CrimsonLoss,
                        modifier = Modifier.weight(1f),
                        testTag = "daily_pnl_metric_card"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Free Margin",
                        value = "$${"%.2f".format(accountInfo.freeMargin)}",
                        subtitle = "Used: $${"%.2f".format(accountInfo.margin)}",
                        valueColor = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Active Risk",
                        value = "${config?.defaultRiskPercent ?: 0.25}%",
                        subtitle = "Max Daily Loss: ${config?.maxDailyLossPercent ?: 1.0}%",
                        valueColor = GoldHero,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. Live Markets Feed Cards
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live Tickers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    TextButton(onClick = onNavigateToMarkets) {
                        Text("View Charts", color = CyanLight, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // XAUUSD Card
                val xau = quotes["XAUUSD"]
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.fillMaxWidth().testTag("xauusd_quote_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("XAUUSD", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = GoldHero)
                                Surface(color = SurfaceVariantDark, shape = RoundedCornerShape(4.dp)) {
                                    Text("Gold Spot", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Spread: ${xau?.spread?.let { "%.2f".format(it) } ?: "--"} ($0.01 tick)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = xau?.ask?.let { "$%.2f".format(it) } ?: "2650.45",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Bid: ${xau?.bid?.let { "$%.2f".format(it) } ?: "2650.20"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // BTCUSD Card
                val btc = quotes["BTCUSD"]
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.fillMaxWidth().testTag("btcusd_quote_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("BTCUSD", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = CyanLight)
                                Surface(color = SurfaceVariantDark, shape = RoundedCornerShape(4.dp)) {
                                    Text("Crypto", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Spread: ${btc?.spread?.let { "$%.1f".format(it) } ?: "--"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = btc?.ask?.let { "$%.1f".format(it) } ?: "91250.0",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Bid: ${btc?.bid?.let { "$%.1f".format(it) } ?: "91245.5"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // 4. Open Positions Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Active Positions (${openPositions.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (openPositions.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showCloseAllDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonLoss),
                            border = BorderStroke(1.dp, CrimsonLoss),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("close_all_quick_btn")
                        ) {
                            Text("CLOSE ALL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (openPositions.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CardBorderDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Inbox, contentDescription = null, tint = TextMuted, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No open positions", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                                Text("Engine is scanning closed M15 candles", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                } else {
                    openPositions.forEach { pos ->
                        val isProfit = pos.unrealizedProfit >= 0
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, if (isProfit) EmeraldGain.copy(alpha = 0.5f) else CrimsonLoss.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().testTag("position_card_${pos.id}")
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Surface(
                                            color = if (pos.direction == TradeDirection.BUY) Color(0xFF063321) else Color(0xFF381419),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = pos.direction.name,
                                                color = if (pos.direction == TradeDirection.BUY) EmeraldGain else CrimsonLoss,
                                                fontWeight = FontWeight.Black,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        Text(pos.symbol, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text("${pos.volume} lots", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${if (isProfit) "+" else ""}$${"%.2f".format(pos.unrealizedProfit)}",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isProfit) EmeraldGain else CrimsonLoss,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "${if (isProfit) "+" else ""}${"%.2f".format(pos.unrealizedR)}R",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                Divider(color = CardBorderDark, modifier = Modifier.padding(vertical = 10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Entry: ${pos.entryPrice}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    Text("SL: ${pos.stopLoss}", style = MaterialTheme.typography.bodySmall, color = CrimsonLoss)
                                    Text("TP: ${pos.takeProfit}", style = MaterialTheme.typography.bodySmall, color = EmeraldGain)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Strategy & Signal Explainability Card
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Strategy Explainability",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    TextButton(onClick = onNavigateToStrategy) {
                        Text("Tuner", color = CyanLight, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val signal = latestSignal
                        if (signal != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Latest: ${signal.symbol} ${signal.direction} @ ${signal.price}",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Surface(
                                    color = if (signal.explanation.isAllPassed) Color(0xFF063321) else Color(0xFF2E2611),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = signal.explanation.decision,
                                        color = if (signal.explanation.isAllPassed) EmeraldGain else GoldHero,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Indicators: EMA20: ${"%.2f".format(signal.explanation.emaFast)} | EMA50: ${"%.2f".format(signal.explanation.emaSlow)} | ADX: ${"%.1f".format(signal.explanation.adx)} | ATR: ${"%.2f".format(signal.explanation.atr)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = CyanLight,
                                fontFamily = FontFamily.Monospace
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Text("14-Factor Verification Checklist:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FactorChip("Trend", signal.explanation.trendCheck)
                                FactorChip("ADX", signal.explanation.adxCheck)
                                FactorChip("Pullback", signal.explanation.pullbackCheck)
                                FactorChip("Candle", signal.explanation.candleCheck)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FactorChip("Spread", signal.explanation.spreadCheck)
                                FactorChip("Risk Limits", signal.explanation.riskCheck)
                                FactorChip("Session", signal.explanation.sessionCheck)
                            }
                        } else {
                            Text(
                                "Conservative EMA20/EMA50 + ADX(14) + ATR(14) strategy engine running. Awaiting completed candle setup.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEmergencyDialog) {
        EmergencyStopDialog(
            onConfirm = {
                viewModel.triggerEmergencyStop()
                showEmergencyDialog = false
            },
            onDismiss = { showEmergencyDialog = false }
        )
    }

    if (showCloseAllDialog) {
        CloseAllPositionsDialog(
            positionCount = openPositions.size,
            onConfirm = {
                viewModel.closeAllPositions()
                showCloseAllDialog = false
            },
            onDismiss = { showCloseAllDialog = false }
        )
    }
}
