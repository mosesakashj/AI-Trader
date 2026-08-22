package com.example.ui.positions

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.EdgeTraderApp
import com.example.domain.model.Position
import com.example.domain.model.TradeDirection
import com.example.ui.components.CloseAllPositionsDialog
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@Composable
private fun PortfolioOverviewCard(
    totalPnL: Double,
    positionCount: Int,
    longCount: Int,
    shortCount: Int,
    bestPosition: Position?,
    worstPosition: Position?,
    modifier: Modifier = Modifier
) {
    val isProfit = totalPnL >= 0

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, CardBorderDark)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Total Unrealized P/L",
                fontSize = 13.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isProfit) EmeraldContainer else CrimsonContainer
            ) {
                Text(
                    text = "${if (isProfit) "+" else ""}%.2f".format(totalPnL),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isProfit) EmeraldGain else CrimsonLoss,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OverviewStat(label = "Positions", value = "$positionCount", color = TextPrimary)
                OverviewStat(label = "Long", value = "$longCount", color = EmeraldGain)
                OverviewStat(label = "Short", value = "$shortCount", color = CrimsonLoss)
            }

            if (bestPosition != null || worstPosition != null) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = CardBorderDark)
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (bestPosition != null && positionCount > 1) {
                    PositionBanner(
                        label = "Best",
                        symbol = bestPosition.symbol,
                        pnl = bestPosition.unrealizedProfit,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (worstPosition != null && positionCount > 1) {
                    PositionBanner(
                        label = "Worst",
                        symbol = worstPosition.symbol,
                        pnl = worstPosition.unrealizedProfit,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewStat(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = TextMuted
        )
    }
}

@Composable
private fun PositionBanner(
    label: String,
    symbol: String,
    pnl: Double,
    modifier: Modifier = Modifier
) {
    val isPositive = pnl >= 0

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (isPositive) EmeraldContainer.copy(alpha = 0.5f) else CrimsonContainer.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = TextMuted,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = symbol,
                fontSize = 12.sp,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${if (isPositive) "+" else ""}%.2f".format(pnl),
                fontSize = 11.sp,
                color = if (isPositive) EmeraldGain else CrimsonLoss,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun QuickStatsRow(
    positions: List<Position>,
    modifier: Modifier = Modifier
) {
    val avgR = if (positions.isNotEmpty()) {
        positions.map { it.unrealizedR }.average()
    } else 0.0

    val totalVolume = positions.sumOf { it.volume }

    val riskExposure = positions.sumOf { pos ->
        val riskPerUnit = abs(pos.entryPrice - pos.stopLoss)
        riskPerUnit * pos.volume
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickStatCard(
            label = "Avg R",
            value = "%.2f".format(avgR),
            subtitle = if (avgR >= 0) "Winning" else "Losing",
            valueColor = if (avgR >= 0) EmeraldGain else CrimsonLoss,
            modifier = Modifier.weight(1f)
        )
        QuickStatCard(
            label = "Risk Exposure",
            value = "%.0f".format(riskExposure),
            subtitle = "At risk",
            valueColor = GoldHero,
            modifier = Modifier.weight(1f)
        )
        QuickStatCard(
            label = "Total Volume",
            value = "%.2f".format(totalVolume),
            subtitle = "${positions.size} pos",
            valueColor = PrimaryBlue,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickStatCard(
    label: String,
    value: String,
    subtitle: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, CardBorderDark)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = TextMuted,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun EmptyPositionsCard(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorderDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = CircleShape,
                    color = SurfaceVariantDark
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = "No positions",
                            tint = TextMuted,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "No Open Positions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Your active trades will appear here once you open a position.",
                    fontSize = 13.sp,
                    color = TextMuted,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun ExpandablePositionCard(
    position: Position,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLong = position.direction == TradeDirection.LONG
    val isProfit = position.unrealizedProfit >= 0
    val durationMs = System.currentTimeMillis() - position.openedAt

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .testTag("position_card_${position.symbol}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, if (isProfit) EmeraldDark.copy(alpha = 0.4f) else CrimsonDark.copy(alpha = 0.4f))
    ) {
        Column {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DirectionBadge(isLong = isLong)

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = position.symbol,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Vol: ${"%.2f".format(position.volume)}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = formatDuration(durationMs),
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${if (isProfit) "+" else ""}%.2f".format(position.unrealizedProfit),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isProfit) EmeraldGain else CrimsonLoss,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val pct = if (position.entryPrice != 0.0) {
                                ((position.currentPrice - position.entryPrice) / position.entryPrice * 100.0) * (if (isLong) 1 else -1)
                            } else 0.0
                            Text(
                                text = "${if (pct >= 0) "+" else ""}%.2f%%".format(pct),
                                fontSize = 11.sp,
                                color = if (isProfit) EmeraldGain else CrimsonLoss,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (position.unrealizedR >= 0) EmeraldContainer else CrimsonContainer
                            ) {
                                Text(
                                    text = "${"%.1f".format(position.unrealizedR)}R",
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (position.unrealizedR >= 0) EmeraldGain else CrimsonLoss,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                SLTPProgressBar(
                    currentPrice = position.currentPrice,
                    stopLoss = position.stopLoss,
                    takeProfit = position.takeProfit,
                    isLong = isLong
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PriceLabel(
                        label = "Current",
                        price = position.currentPrice,
                        color = TextPrimary
                    )
                    val distToSL = abs(position.currentPrice - position.stopLoss)
                    val distToTP = abs(position.takeProfit - position.currentPrice)
                    PriceLabel(
                        label = "SL",
                        price = distToSL,
                        color = CrimsonLoss,
                        prefix = formatDistance(distToSL)
                    )
                    PriceLabel(
                        label = "TP",
                        price = distToTP,
                        color = EmeraldGain,
                        prefix = formatDistance(distToTP)
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceVariantDark.copy(alpha = 0.3f))
                        .padding(14.dp)
                ) {
                    HorizontalDivider(color = CardBorderDark, modifier = Modifier.padding(bottom = 12.dp))

                    val breakeven = position.entryPrice
                    val mfe = abs(position.currentPrice - position.entryPrice) * position.volume
                    val rrAchieved = if (abs(position.entryPrice - position.stopLoss) != 0.0) {
                        abs(position.currentPrice - position.entryPrice) / abs(position.entryPrice - position.stopLoss)
                    } else 0.0

                    DetailRow(label = "Breakeven", value = "%.5f".format(breakeven))
                    DetailRow(label = "MFE", value = "%.2f".format(mfe))
                    DetailRow(label = "Daily P&L", value = "%.2f".format(position.unrealizedProfit))
                    DetailRow(label = "R/R Achieved", value = "%.2f".format(rrAchieved))

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (position.unrealizedR >= 1.0f) {
                            RiskBadge(
                                text = "Break-even locked @ 1R",
                                color = EmeraldGain,
                                bgColor = EmeraldContainer
                            )
                        }
                        if (position.unrealizedR >= 1.5f) {
                            RiskBadge(
                                text = "Trailing active @ 1.5R",
                                color = GoldHero,
                                bgColor = GoldContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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
                val progressToTP = if (totalRange > 0) ((abs(pos.currentPrice - pos.entryPrice)) / totalRange).toFloat().coerceIn(0f, 1f) else 0f
                val slHitPct = if (totalRange > 0) (distanceToSL / totalRange * 100).toFloat().coerceIn(0f, 100f) else 0f
                val tpHitPct = if (totalRange > 0) (distanceToTP / totalRange * 100).toFloat().coerceIn(0f, 100f) else 0f

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
                        val isBeSecured = (pos.direction == TradeDirection.BUY && pos.stopLoss >= (pos.entryPrice - 0.0001)) ||
                                (pos.direction == TradeDirection.SELL && pos.stopLoss <= (pos.entryPrice + 0.0001))
                        val isTrailing = (pos.direction == TradeDirection.BUY && pos.stopLoss > (pos.entryPrice + 0.001)) ||
                                (pos.direction == TradeDirection.SELL && pos.stopLoss < (pos.entryPrice - 0.001))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isTrailing) {
                                Surface(color = CyanContainer, shape = RoundedCornerShape(4.dp)) {
                                    Text("🎯 Trailing Active (+${"%.2f".format(pos.unrealizedR)}R)", style = MaterialTheme.typography.labelSmall, color = CyanLight, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            } else if (isBeSecured) {
                                Surface(color = EmeraldContainer, shape = RoundedCornerShape(4.dp)) {
                                    Text("🛡️ Break-Even Secured (Risk Free)", style = MaterialTheme.typography.labelSmall, color = EmeraldDark, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            } else if (pos.unrealizedR >= 0.6) {
                                Surface(color = GoldContainer, shape = RoundedCornerShape(4.dp)) {
                                    Text("⚡ BE Approaching (${"%.2f".format(pos.unrealizedR)}R)", style = MaterialTheme.typography.labelSmall, color = GoldHero, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
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
                            val progressFromSL = (if (pos.direction == TradeDirection.BUY) {
                                if (pos.takeProfit != pos.stopLoss) (pos.currentPrice - pos.stopLoss) / (pos.takeProfit - pos.stopLoss) else 0.5
                            } else {
                                if (pos.stopLoss != pos.takeProfit) (pos.stopLoss - pos.currentPrice) / (pos.stopLoss - pos.takeProfit) else 0.5
                            }).toFloat().coerceIn(0f, 1f)
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SurfaceVariantDark)
                            ) {
                                // Progress from SL to current
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progressFromSL)
                                        .fillMaxHeight()
                                        .background(if (isProfit) EmeraldGain.copy(alpha = 0.6f) else CrimsonLoss.copy(alpha = 0.6f))
                                        .clip(RoundedCornerShape(4.dp))
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
}

@Composable
private fun DirectionBadge(
    isLong: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = if (isLong) EmeraldContainer else CrimsonContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isLong) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                contentDescription = null,
                tint = if (isLong) EmeraldGain else CrimsonLoss,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isLong) "LONG" else "SHORT",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isLong) EmeraldGain else CrimsonLoss
            )
        }
    }
}

@Composable
private fun SLTPProgressBar(
    currentPrice: Double,
    stopLoss: Double,
    takeProfit: Double,
    isLong: Boolean,
    modifier: Modifier = Modifier
) {
    val range = takeProfit - stopLoss
    val progress = if (range != 0.0) {
        ((currentPrice - stopLoss) / range).coerceIn(0.0, 1.0).toFloat()
    } else 0.5f

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(SurfaceVariantDark)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = progress)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (isLong) {
                            EmeraldGain
                        } else {
                            CrimsonLoss
                        }
                    )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SL %.5f".format(stopLoss),
                fontSize = 9.sp,
                color = CrimsonLoss,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "%.5f".format(currentPrice),
                fontSize = 9.sp,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "TP %.5f".format(takeProfit),
                fontSize = 9.sp,
                color = EmeraldGain,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun PriceLabel(
    label: String,
    price: Double,
    color: Color,
    prefix: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            color = TextMuted
        )
        if (prefix != null) {
            Text(
                text = prefix,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
                fontFamily = FontFamily.Monospace
            )
        } else {
            Text(
                text = "%.5f".format(price),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextMuted
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun RiskBadge(
    text: String,
    color: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

private fun formatDuration(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

private fun formatDistance(value: Double): String {
    return when {
        value >= 1.0 -> "%.2f".format(value)
        value >= 0.001 -> "%.4f".format(value)
        else -> "%.6f".format(value)
    }
}
