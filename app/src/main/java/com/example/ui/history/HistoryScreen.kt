package com.example.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
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
import com.example.domain.model.TradeStatus
import com.example.ui.components.MetricCard
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@Composable
fun HistoryScreen() {
    val repository = EdgeTraderApp.instance.repository
    val trades by repository.allTradesFlow.collectAsState(initial = emptyList())
    val closedTrades = remember(trades) { trades.filter { it.status == TradeStatus.CLOSED } }

    val totalTrades = closedTrades.size
    val winTrades = closedTrades.count { it.profit > 0 }
    val winRate = if (totalTrades > 0) (winTrades.toDouble() / totalTrades) * 100.0 else 0.0
    val totalPnl = closedTrades.sumOf { it.profit }
    val grossProfit = closedTrades.filter { it.profit > 0 }.sumOf { it.profit }
    val grossLoss = abs(closedTrades.filter { it.profit < 0 }.sumOf { it.profit })
    val profitFactor = if (grossLoss > 0) grossProfit / grossLoss else if (grossProfit > 0) 9.99 else 0.0
    val avgR = if (totalTrades > 0) closedTrades.sumOf { it.profitR } / totalTrades else 0.0

    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quantitative Performance Summary Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Win Rate",
                        value = "${"%.1f".format(winRate)}%",
                        subtitle = "$winTrades wins / ${totalTrades - winTrades} losses",
                        valueColor = if (winRate >= 50.0) EmeraldGain else CrimsonLoss,
                        modifier = Modifier.weight(1f),
                        testTag = "win_rate_metric"
                    )
                    MetricCard(
                        title = "Profit Factor",
                        value = "%.2f".format(profitFactor),
                        subtitle = "Gross: $${"%.0f".format(grossProfit)} / -$${"%.0f".format(grossLoss)}",
                        valueColor = if (profitFactor >= 1.5) EmeraldGain else GoldHero,
                        modifier = Modifier.weight(1f),
                        testTag = "profit_factor_metric"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Realized Net P/L",
                        value = "${if (totalPnl >= 0) "+" else ""}$${"%.2f".format(totalPnl)}",
                        subtitle = "Total Closed: $totalTrades",
                        valueColor = if (totalPnl >= 0) EmeraldGain else CrimsonLoss,
                        modifier = Modifier.weight(1f),
                        testTag = "net_pnl_metric"
                    )
                    MetricCard(
                        title = "Avg R-Multiple",
                        value = "${if (avgR >= 0) "+" else ""}${"%.2f".format(avgR)}R",
                        subtitle = "Target: 2.0R",
                        valueColor = CyanLight,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Header with Count
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Trade Execution Log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("$totalTrades Trades", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }

        // Trades List
        if (closedTrades.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Inbox, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("No trade history", color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text("Executed trades will appear here with closed P/L and R metrics.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                    }
                }
            }
        } else {
            items(closedTrades, key = { it.id }) { trade ->
                val isWin = trade.profit >= 0
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.fillMaxWidth().testTag("trade_item_${trade.id}")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    color = if (trade.direction == TradeDirection.BUY) Color(0xFF063321) else Color(0xFF381419),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = trade.direction.name,
                                        color = if (trade.direction == TradeDirection.BUY) EmeraldGain else CrimsonLoss,
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Text(trade.symbol, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("${trade.volume} lot", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${if (isWin) "+" else ""}$${"%.2f".format(trade.profit)}",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isWin) EmeraldGain else CrimsonLoss,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${if (isWin) "+" else ""}${"%.2f".format(trade.profitR)}R",
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
                            Column {
                                Text("Entry / Exit", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text("${trade.entryPrice} → ${trade.closePrice ?: trade.entryPrice}", style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontFamily = FontFamily.Monospace)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Reason", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(trade.closeReason?.name ?: "MANUAL", style = MaterialTheme.typography.bodySmall, color = if (isWin) EmeraldGain else CrimsonLoss)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Opened: ${dateFormat.format(Date(trade.openedAt))} | Closed: ${trade.closedAt?.let { dateFormat.format(Date(it)) } ?: "--"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}
