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

fun formatTradeDuration(millis: Long): String {
    val totalMinutes = millis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

fun formatDistance(distance: Double, symbol: String): String {
    val pipMultiplier = if (symbol.contains("JPY")) 100.0 else 10000.0
    val pips = distance * pipMultiplier
    return "${if (pips >= 0) "+" else ""}${"%.1f".format(pips)} pips"
}

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

    val expectancy = if (totalTrades > 0) totalPnl / totalTrades else 0.0
    val bestTrade = closedTrades.maxByOrNull { it.profit }
    val worstTrade = closedTrades.minByOrNull { it.profit }

    val buyTrades = closedTrades.filter { it.direction == TradeDirection.BUY }
    val sellTrades = closedTrades.filter { it.direction == TradeDirection.SELL }
    val buyWinCount = buyTrades.count { it.profit > 0 }
    val sellWinCount = sellTrades.count { it.profit > 0 }
    val buyWinRate = if (buyTrades.isNotEmpty()) (buyWinCount.toDouble() / buyTrades.size) * 100.0 else 0.0
    val sellWinRate = if (sellTrades.isNotEmpty()) (sellWinCount.toDouble() / sellTrades.size) * 100.0 else 0.0
    val buyTotalPnl = buyTrades.sumOf { it.profit }
    val sellTotalPnl = sellTrades.sumOf { it.profit }

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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Expectancy",
                        value = "${if (expectancy >= 0) "+" else ""}$${"%.2f".format(expectancy)}",
                        subtitle = "Avg per trade",
                        valueColor = if (expectancy >= 0) EmeraldGain else CrimsonLoss,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Best Trade",
                        value = "$${"%.2f".format(bestTrade?.profit ?: 0.0)}",
                        subtitle = bestTrade?.symbol ?: "--",
                        valueColor = EmeraldGain,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Worst Trade",
                        value = "$${"%.2f".format(worstTrade?.profit ?: 0.0)}",
                        subtitle = worstTrade?.symbol ?: "--",
                        valueColor = CrimsonLoss,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Performance by Direction
        if (totalTrades > 0) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Performance by Direction", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Surface(
                                    color = EmeraldContainer,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "BUY",
                                        color = EmeraldDark,
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("${"%.1f".format(buyWinRate)}% win", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${if (buyTotalPnl >= 0) "+" else ""}$${"%.2f".format(buyTotalPnl)}",
                                    color = if (buyTotalPnl >= 0) EmeraldGain else CrimsonLoss,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Surface(
                                    color = CrimsonContainer,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "SELL",
                                        color = CrimsonDark,
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("${"%.1f".format(sellWinRate)}% win", color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${if (sellTotalPnl >= 0) "+" else ""}$${"%.2f".format(sellTotalPnl)}",
                                    color = if (sellTotalPnl >= 0) EmeraldGain else CrimsonLoss,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
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
                val durationMillis = (trade.closedAt ?: System.currentTimeMillis()) - trade.openedAt
                val priceDistance = (trade.closePrice ?: trade.entryPrice) - trade.entryPrice
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
                                    color = if (trade.direction == TradeDirection.BUY) EmeraldContainer else CrimsonContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = trade.direction.name,
                                        color = if (trade.direction == TradeDirection.BUY) EmeraldDark else CrimsonDark,
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

                        HorizontalDivider(color = CardBorderDark, modifier = Modifier.padding(vertical = 10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Entry / Exit", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text("${trade.entryPrice} → ${trade.closePrice ?: trade.entryPrice}", style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontFamily = FontFamily.Monospace)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Move", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(formatDistance(priceDistance, trade.symbol), style = MaterialTheme.typography.bodySmall, color = if (isWin) EmeraldGain else CrimsonLoss, fontFamily = FontFamily.Monospace)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Duration", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(formatTradeDuration(durationMillis), style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontFamily = FontFamily.Monospace)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Reason", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(trade.closeReason?.name ?: "MANUAL", style = MaterialTheme.typography.bodySmall, color = if (isWin) EmeraldGain else CrimsonLoss)
                            }
                        }
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
