package com.example.ui.positions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.EdgeTraderApp
import com.example.domain.model.TradeDirection
import com.example.ui.components.CloseAllPositionsDialog
import com.example.ui.components.MetricCard
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun PositionsScreen() {
    val repository = EdgeTraderApp.instance.repository
    val engine = EdgeTraderApp.instance.tradingEngine
    val openPositions by repository.openPositionsFlow.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    var showCloseAllDialog by remember { mutableStateOf(false) }

    val totalUnrealizedPnl = openPositions.sumOf { it.unrealizedProfit }
    val totalUnrealizedR = openPositions.sumOf { it.unrealizedR }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary Header Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Open Positions",
                    value = "${openPositions.size}",
                    subtitle = "Capacity: 2 max",
                    modifier = Modifier.weight(1f),
                    testTag = "open_positions_count_metric"
                )
                MetricCard(
                    title = "Unrealized P/L",
                    value = "${if (totalUnrealizedPnl >= 0) "+" else ""}$${"%.2f".format(totalUnrealizedPnl)}",
                    subtitle = "${if (totalUnrealizedR >= 0) "+" else ""}${"%.2f".format(totalUnrealizedR)}R",
                    valueColor = if (totalUnrealizedPnl >= 0) EmeraldGain else CrimsonLoss,
                    modifier = Modifier.weight(1f),
                    testTag = "unrealized_pnl_metric"
                )
            }
        }

        // Close All Emergency Action
        if (openPositions.isNotEmpty()) {
            item {
                Button(
                    onClick = { showCloseAllDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonLoss),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("positions_close_all_btn")
                ) {
                    Icon(Icons.Default.Dangerous, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CLOSE ALL ACTIVE POSITIONS", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Positions List
        if (openPositions.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Inbox, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No open positions", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("The engine is monitoring the market for valid trade setups.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                }
            }
        } else {
            items(openPositions, key = { it.id }) { pos ->
                val isProfit = pos.unrealizedProfit >= 0
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, if (isProfit) EmeraldGain.copy(alpha = 0.6f) else CrimsonLoss.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth().testTag("position_item_${pos.id}")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    color = if (pos.direction == TradeDirection.BUY) EmeraldContainer else CrimsonContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = pos.direction.name,
                                        color = if (pos.direction == TradeDirection.BUY) EmeraldDark else CrimsonDark,
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Text(pos.symbol, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text("${pos.volume} lots", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
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

                        Spacer(modifier = Modifier.height(8.dp))

                        // Auto Position Management Badges
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (pos.unrealizedR >= 1.0) {
                                Surface(color = EmeraldContainer, shape = RoundedCornerShape(4.dp)) {
                                    Text("🛡️ Break-Even Locked", style = MaterialTheme.typography.labelSmall, color = EmeraldDark, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            if (pos.unrealizedR >= 1.5) {
                                Surface(color = CyanContainer, shape = RoundedCornerShape(4.dp)) {
                                    Text("🎯 Trailing Active", style = MaterialTheme.typography.labelSmall, color = CyanLight, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }

                        HorizontalDivider(color = CardBorderDark, modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Entry Price", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(com.example.domain.model.SymbolCatalog.formatPrice(pos.symbol, pos.entryPrice), style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontFamily = FontFamily.Monospace)
                            }
                            Column {
                                Text("Stop Loss", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(com.example.domain.model.SymbolCatalog.formatPrice(pos.symbol, pos.stopLoss), style = MaterialTheme.typography.bodyMedium, color = CrimsonLoss, fontFamily = FontFamily.Monospace)
                            }
                            Column {
                                Text("Take Profit", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(com.example.domain.model.SymbolCatalog.formatPrice(pos.symbol, pos.takeProfit), style = MaterialTheme.typography.bodyMedium, color = EmeraldGain, fontFamily = FontFamily.Monospace)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    engine.closeSinglePosition(pos.id)
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonLoss),
                            border = BorderStroke(1.dp, CrimsonLoss),
                            modifier = Modifier.fillMaxWidth().testTag("close_pos_btn_${pos.id}")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Close Position Immediately", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showCloseAllDialog) {
        CloseAllPositionsDialog(
            positionCount = openPositions.size,
            onConfirm = {
                coroutineScope.launch {
                    engine.closeAllPositions()
                    showCloseAllDialog = false
                }
            },
            onDismiss = { showCloseAllDialog = false }
        )
    }
}
