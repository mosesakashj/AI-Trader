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
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.AttachMoney
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
import com.example.EdgeTraderApp
import com.example.domain.model.TradeDirection
import com.example.ui.components.CloseAllPositionsDialog
import com.example.ui.components.MetricCard
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

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

        // Avg R & Best/Worst Summary Row
        if (openPositions.isNotEmpty()) {
            item {
                val avgR = openPositions.map { it.unrealizedR }.average()
                val bestR = openPositions.maxOfOrNull { it.unrealizedR } ?: 0.0
                val worstR = openPositions.minOfOrNull { it.unrealizedR } ?: 0.0

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Avg R",
                        value = "${if (avgR >= 0) "+" else ""}${"%.2f".format(avgR)}R",
                        subtitle = "Across ${openPositions.size} positions",
                        valueColor = if (avgR >= 0) EmeraldGain else CrimsonLoss,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Best / Worst",
                        value = "${if (bestR >= 0) "+" else ""}${"%.2f".format(bestR)}R",
                        subtitle = "Worst: ${if (worstR >= 0) "+" else ""}${"%.2f".format(worstR)}R",
                        valueColor = if (bestR >= worstR) GoldHero else CrimsonLoss,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Portfolio Risk Metrics
        if (openPositions.isNotEmpty()) {
            item {
                val totalRisk = openPositions.sumOf { it.volume * abs(it.entryPrice - it.stopLoss) }
                val totalMargin = openPositions.sumOf { it.volume * it.entryPrice * 100 } // approximate
                val avgR = if (openPositions.isNotEmpty()) openPositions.sumOf { it.unrealizedR } / openPositions.size else 0.0
                val bestPosition = openPositions.maxByOrNull { it.unrealizedR }
                val worstPosition = openPositions.minByOrNull { it.unrealizedR }
                val totalVolume = openPositions.sumOf { it.volume }
                val longCount = openPositions.count { it.direction == TradeDirection.BUY }
                val shortCount = openPositions.count { it.direction == TradeDirection.SELL }

                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Portfolio Risk Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricCard(title = "Total Volume", value = "${"%.2f".format(totalVolume)} lots", subtitle = "L: $longCount / S: $shortCount", modifier = Modifier.weight(1f))
                            MetricCard(title = "Avg R-Multiple", value = "${"%.2f".format(avgR)}R", subtitle = "Per position", modifier = Modifier.weight(1f))
                        }
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricCard(title = "Best Position", value = bestPosition?.let { "${it.symbol} ${"%.2f".format(it.unrealizedR)}R" } ?: "—", subtitle = "Highest unrealized R", valueColor = EmeraldGain, modifier = Modifier.weight(1f))
                            MetricCard(title = "Worst Position", value = worstPosition?.let { "${it.symbol} ${"%.2f".format(it.unrealizedR)}R" } ?: "—", subtitle = "Lowest unrealized R", valueColor = CrimsonLoss, modifier = Modifier.weight(1f))
                        }
                        
                        HorizontalDivider(color = CardBorderDark)
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Risk Exposure", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text("$${"%.2f".format(totalRisk)}", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontFamily = FontFamily.Monospace)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Est. Margin Used", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text("$${"%.2f".format(totalMargin)}", style = MaterialTheme.typography.titleMedium, color = TextSecondary, fontFamily = FontFamily.Monospace)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Margin Level", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                val marginLevel = if (totalMargin > 0) (10000.0 / totalMargin) * 100 else 0.0
                                Text("${"%.0f".format(marginLevel)}%", style = MaterialTheme.typography.titleMedium, color = if (marginLevel > 200) EmeraldGain else if (marginLevel > 100) GoldHero else CrimsonLoss, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
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
                val timeHeld = formatDuration(System.currentTimeMillis() - pos.openedAt)
                val distanceToSL = if (pos.direction == TradeDirection.BUY) pos.currentPrice - pos.stopLoss else pos.stopLoss - pos.currentPrice
                val distanceToTP = if (pos.direction == TradeDirection.BUY) pos.takeProfit - pos.currentPrice else pos.currentPrice - pos.takeProfit
                val totalRange = abs(pos.takeProfit - pos.stopLoss)
                val progressToTP = if (totalRange > 0) ((abs(pos.currentPrice - pos.entryPrice)) / totalRange).coerceIn(0f, 1f) else 0f
                val slHitPct = if (totalRange > 0) (distanceToSL / totalRange * 100).coerceIn(0f, 100f) else 0f
                val tpHitPct = if (totalRange > 0) (distanceToTP / totalRange * 100).coerceIn(0f, 100f) else 0f

                // Enhanced analytics
                val spreadOffset = abs(pos.entryPrice - pos.stopLoss)
                val breakevenPrice = if (pos.direction == TradeDirection.BUY) pos.entryPrice + spreadOffset else pos.entryPrice - spreadOffset
                val pnlPct = if (pos.entryPrice > 0) (pos.unrealizedProfit / (pos.volume * pos.entryPrice) * 100) else 0.0
                val mfeR = pos.unrealizedR * 0.9
                val timeHeldMillis = System.currentTimeMillis() - pos.openedAt
                val timeHeldHours = timeHeldMillis / 3600000.0
                val annualizedR = if (timeHeldHours > 0 && pos.unrealizedR > 0) (pos.unrealizedR / timeHeldHours) * 8760.0 else 0.0
                val distanceFromEntry = if (pos.direction == TradeDirection.BUY) pos.currentPrice - pos.entryPrice else pos.entryPrice - pos.currentPrice
                val targetR = if (abs(pos.entryPrice - pos.stopLoss) > 0) abs(pos.takeProfit - pos.entryPrice) / abs(pos.entryPrice - pos.stopLoss) else 0.0
                val riskRewardAchieved = if (targetR > 0) pos.unrealizedR / targetR else 0.0
                val dailyPnlContrib = if (timeHeldHours > 0) pos.unrealizedProfit / (timeHeldHours / 24.0) else 0.0

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
                                Text("${pos.volume} lots", color = if (isProfit) EmeraldGain else CrimsonLoss, style = MaterialTheme.typography.bodyMedium)
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${if (isProfit) "+" else ""}$${"%.2f".format(pos.unrealizedProfit)}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isProfit) EmeraldGain else CrimsonLoss,
                                    fontFamily = FontFamily.Monospace
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "(${if (pnlPct >= 0) "+" else ""}${"%.2f".format(pnlPct)}%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (pnlPct >= 0) EmeraldGain else CrimsonLoss,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "${if (isProfit) "+" else ""}${"%.2f".format(pos.unrealizedR)}R",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
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

                        // Current Price, Time Held, Distance from Entry
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Current Price", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text("${pos.currentPrice}", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontFamily = FontFamily.Monospace)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Time Held", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Timer, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                                    Text(timeHeld, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontFamily = FontFamily.Monospace)
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Distance from Entry", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(
                                    formatDistance(distanceFromEntry, pos.symbol),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (distanceFromEntry >= 0) EmeraldGain else CrimsonLoss,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Enhanced Analytics Row 1: Breakeven, MFE, Annualized R
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Breakeven Price", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(
                                    "${"%.5f".format(breakevenPrice)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GoldHero,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("MFE (Best R)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(
                                    "${"%.2f".format(mfeR)}R",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EmeraldGain,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Annualized R", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(
                                    "${"%.1f".format(annualizedR)}R",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CyanLight,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Enhanced Analytics Row 2: Daily P&L, R/R Achieved
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Daily P&L", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(
                                    "$${"%.2f".format(dailyPnlContrib)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (dailyPnlContrib >= 0) EmeraldGain else CrimsonLoss,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("R/R Achieved", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(
                                    "${"%.0f".format(riskRewardAchieved * 100)}% of ${"%.1f".format(targetR)}R target",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (riskRewardAchieved >= 0.5) EmeraldGain else if (riskRewardAchieved >= 0.25) GoldHero else CrimsonLoss,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // SL/TP Progress Visualization
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("SL \u2192 TP Progress", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text("${"%.0f".format(slHitPct)}% to SL  |  ${"%.0f".format(tpHitPct)}% to TP", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontFamily = FontFamily.Monospace)
                            }
                            
                            // Progress bar from SL to TP
                            val progressFromSL = if (pos.direction == TradeDirection.BUY) {
                                (pos.currentPrice - pos.stopLoss) / (pos.takeProfit - pos.stopLoss)
                            } else {
                                (pos.stopLoss - pos.currentPrice) / (pos.stopLoss - pos.takeProfit)
                            }.coerceIn(0f, 1f)
                            
                            Box(modifier = Modifier.fillMaxWidth().height(8.dp)) {
                                // Background track
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SurfaceVariantDark)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                
                                // Progress from SL to current
                                Box(
                                    modifier = Modifier
                                        .width(progressFromSL)
                                        .fillMaxHeight()
                                        .background(if (isProfit) EmeraldGain.copy(alpha = 0.4f) else CrimsonLoss.copy(alpha = 0.4f))
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                
                                // Current price marker
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .fillMaxHeight()
                                        .offset(x = (progressFromSL * 100 - 1.5).dp, y = 0.dp)
                                        .background(if (isProfit) EmeraldGain else CrimsonLoss)
                                        .clip(RoundedCornerShape(1.5.dp))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Detailed SL/TP Info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Stop Loss", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${pos.stopLoss}", style = MaterialTheme.typography.bodyMedium, color = CrimsonLoss, fontFamily = FontFamily.Monospace)
                                    Text("(${formatDistance(distanceToSL, pos.symbol)} to SL)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                }
                            }
                            Column {
                                Text("Take Profit", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${pos.takeProfit}", style = MaterialTheme.typography.bodyMedium, color = EmeraldGain, fontFamily = FontFamily.Monospace)
                                    Text("(${formatDistance(distanceToTP, pos.symbol)} to TP)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                }
                            }
                            Column {
                                Text("Entry", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text("${pos.entryPrice}", style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontFamily = FontFamily.Monospace)
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

private fun formatDuration(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else if (minutes > 0) {
        "${minutes}m ${seconds}s"
    } else {
        "${seconds}s"
    }
}

private fun formatDistance(distance: Double, symbol: String): String {
    val digits = when (symbol) {
        "XAUUSD" -> 2
        "BTCUSD" -> 2
        "EURUSD", "GBPUSD", "AUDUSD", "NZDUSD", "USDCAD", "USDCHF", "USDJPY" -> 5
        else -> 5
    }
    val pips = distance * Math.pow(10.0, digits.toDouble())
    return if (symbol == "USDJPY") {
        "${"%.1f".format(pips)} pts"
    } else {
        "${"%.1f".format(pips)} pips"
    }
}
