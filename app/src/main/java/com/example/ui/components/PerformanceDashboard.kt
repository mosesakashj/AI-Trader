package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Trade
import com.example.domain.model.TradeStatus
import com.example.ui.theme.*

data class PerformanceStats(
    val totalTrades: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val winRate: Double = 0.0,
    val profitFactor: Double = 0.0,
    val avgWinR: Double = 0.0,
    val avgLossR: Double = 0.0,
    val expectancyR: Double = 0.0,
    val maxDrawdownR: Double = 0.0,
    val currentStreak: Int = 0,
    val longestWinStreak: Int = 0,
    val longestLossStreak: Int = 0,
    val totalPnL: Double = 0.0,
    val equityCurve: List<Pair<Long, Double>> = emptyList()
)

fun computePerformanceStats(trades: List<Trade>): PerformanceStats {
    val closed = trades.filter { it.status == TradeStatus.CLOSED }
    if (closed.isEmpty()) return PerformanceStats()

    val wins = closed.filter { (it.profit) > 0 }
    val losses = closed.filter { (it.profit) <= 0 }

    val totalTrades = closed.size
    val winCount = wins.size
    val lossCount = losses.size
    val winRate = if (totalTrades > 0) winCount.toDouble() / totalTrades else 0.0

    val totalWins = wins.sumOf { it.profit }
    val totalLosses = kotlin.math.abs(losses.sumOf { it.profit })
    val profitFactor = if (totalLosses > 0) totalWins / totalLosses else if (totalWins > 0) Double.MAX_VALUE else 0.0

    val avgWinR = if (winCount > 0) totalWins / winCount else 0.0
    val avgLossR = if (lossCount > 0) totalLosses / lossCount else 0.0

    val expectancyR = if (totalTrades > 0) {
        (winRate * avgWinR) - ((1 - winRate) * avgLossR)
    } else 0.0

val sorted = closed.sortedBy { it.closedAt }
    val equityCurve = mutableListOf<Pair<Long, Double>>()
    var running = 0.0
    var peak = 0.0
    var maxDrawdown = 0.0

    for (trade in sorted) {
        running += trade.profit
        val timestamp = trade.closedAt ?: trade.openedAt
        equityCurve.add(timestamp to running)
        if (running > peak) peak = running
        val drawdown = peak - running
        if (drawdown > maxDrawdown) maxDrawdown = drawdown
    }

    var currentStreak = 0
    var longestWinStreak = 0
    var longestLossStreak = 0
    var currentWinCount = 0
    var currentLossCount = 0

    for (trade in sorted) {
        val pnl = trade.profit
        if (pnl > 0) {
            currentWinCount++
            currentLossCount = 0
            if (currentWinCount > longestWinStreak) longestWinStreak = currentWinCount
        } else {
            currentLossCount++
            currentWinCount = 0
            if (currentLossCount > longestLossStreak) longestLossStreak = currentLossCount
        }
    }

    val lastPnl = sorted.lastOrNull()?.profit ?: 0.0
    currentStreak = if (lastPnl > 0) currentWinCount else -currentLossCount

    return PerformanceStats(
        totalTrades = totalTrades,
        wins = winCount,
        losses = lossCount,
        winRate = winRate,
        profitFactor = profitFactor,
        avgWinR = avgWinR,
        avgLossR = avgLossR,
        expectancyR = expectancyR,
        maxDrawdownR = maxDrawdown,
        currentStreak = currentStreak,
        longestWinStreak = longestWinStreak,
        longestLossStreak = longestLossStreak,
        totalPnL = running,
        equityCurve = equityCurve
    )
}

@Composable
fun PerformanceDashboard(stats: PerformanceStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "\uD83D\uDCCA", fontSize = 20.sp)
            Text(
                text = "Performance Dashboard",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Win Rate",
                value = "%.1f%%".format(stats.winRate * 100),
                color = if (stats.winRate >= 0.5) EmeraldGain else CrimsonLoss
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Profit Factor",
                value = if (stats.profitFactor == Double.MAX_VALUE) "∞" else "%.2f".format(stats.profitFactor),
                color = if (stats.profitFactor >= 1.5) EmeraldGain else if (stats.profitFactor >= 1.0) GoldHero else CrimsonLoss
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Expectancy",
                value = "%.2fR".format(stats.expectancyR),
                color = if (stats.expectancyR > 0) EmeraldGain else CrimsonLoss
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Max Drawdown",
                value = "-%.2fR".format(stats.maxDrawdownR),
                color = CrimsonLoss
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = CardDefaults.outlinedCardBorder().copy(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(CardBorderDark, CardBorderDark))
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Streaks",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StreakItem(
                        label = "Current",
                        value = if (stats.currentStreak > 0) "+${stats.currentStreak}" else stats.currentStreak.toString(),
                        color = if (stats.currentStreak > 0) EmeraldGain else if (stats.currentStreak < 0) CrimsonLoss else TextMuted
                    )
                    StreakItem(
                        label = "Best Win",
                        value = "+${stats.longestWinStreak}",
                        color = EmeraldGain
                    )
                    StreakItem(
                        label = "Worst Loss",
                        value = "-${stats.longestLossStreak}",
                        color = CrimsonLoss
                    )
                }
            }
        }

        if (stats.equityCurve.size >= 2) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = CardDefaults.outlinedCardBorder().copy(
                    width = 1.dp,
                    brush = Brush.linearGradient(listOf(CardBorderDark, CardBorderDark))
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Equity Curve",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    EquityCurveChart(
                        data = stats.equityCurve,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = CardDefaults.outlinedCardBorder().copy(
            width = 1.dp,
            brush = Brush.linearGradient(listOf(CardBorderDark, CardBorderDark))
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = color
            )
        }
    }
}

@Composable
private fun StreakItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}

@Composable
private fun EquityCurveChart(
    data: List<Pair<Long, Double>>,
    modifier: Modifier = Modifier
) {
    val isPositive = remember(data) { data.lastOrNull()?.second ?: 0.0 >= 0.0 }
    val lineColor = if (isPositive) EmeraldGain else CrimsonLoss

    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas

        val values = data.map { it.second }
        val minVal = values.min()
        val maxVal = values.max()
        val range = (maxVal - minVal).coerceAtLeast(0.01)

        val padding = 4.dp.toPx()
        val chartWidth = size.width - padding * 2
        val chartHeight = size.height - padding * 2

        val points = values.mapIndexed { index, value ->
            val x = padding + (index.toFloat() / (values.size - 1)) * chartWidth
            val normalizedY = ((value - minVal) / range).toFloat()
            val y = padding + chartHeight - (normalizedY * chartHeight)
            Offset(x, y)
        }

        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
        }

        val fillPath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.3f),
                    lineColor.copy(alpha = 0.0f)
                ),
                startY = 0f,
                endY = size.height
            )
        )

        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
