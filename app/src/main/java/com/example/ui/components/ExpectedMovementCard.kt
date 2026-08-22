package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.api.ExpectedMove
import com.example.domain.model.SymbolCatalog
import com.example.ui.theme.*

@Composable
fun ExpectedMovementCard(
    expectedMove: ExpectedMove,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorderDark),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ShowChart, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                    Text(
                        text = "Expected Move (${expectedMove.symbol})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                VolatilityBadge(percentile = expectedMove.volatilityPercentile, label = expectedMove.volatilityLabel)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Price and ATR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Current Price", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        SymbolCatalog.formatPrice(expectedMove.symbol, expectedMove.currentPrice),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("ATR (14)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        SymbolCatalog.formatPrice(expectedMove.symbol, expectedMove.atr14),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = CyanLight
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Expected Range Visualization
            ExpectedRangeBar(expectedMove)

            Spacer(modifier = Modifier.height(12.dp))

            // Daily and Weekly Range
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Daily Range",
                    value = SymbolCatalog.formatPrice(expectedMove.symbol, expectedMove.expectedDailyRange),
                    subtitle = "$\u00B11 ATR",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Weekly Range",
                    value = SymbolCatalog.formatPrice(expectedMove.symbol, expectedMove.expectedWeeklyRange),
                    subtitle = "$\u00B11 ATR\u221A5",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Upper/Lower Band
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Upper Band", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        SymbolCatalog.formatPrice(expectedMove.symbol, expectedMove.upperBand),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = EmeraldGain
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Price", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        SymbolCatalog.formatPrice(expectedMove.symbol, expectedMove.currentPrice),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = PrimaryBlue
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Lower Band", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        SymbolCatalog.formatPrice(expectedMove.symbol, expectedMove.lowerBand),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = CrimsonLoss
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpectedRangeBar(expectedMove: ExpectedMove) {
    val range = expectedMove.upperBand - expectedMove.lowerBand
    val pricePosition = if (range > 0) {
        ((expectedMove.currentPrice - expectedMove.lowerBand) / range).coerceIn(0f, 1f)
    } else 0.5f

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
    ) {
        val barHeight = 8.dp.toPx()
        val y = (size.height - barHeight) / 2

        drawRoundRect(
            color = SurfaceVariantDark,
            topLeft = Offset(0f, y),
            size = Size(size.width, barHeight),
            cornerRadius = CornerRadius(4.dp.toPx())
        )

        val lowerThird = size.width / 3f
        val upperThird = size.width * 2f / 3f

        drawRoundRect(
            color = CrimsonLoss.copy(alpha = 0.25f),
            topLeft = Offset(0f, y),
            size = Size(lowerThird, barHeight),
            cornerRadius = CornerRadius(4.dp.toPx())
        )

        drawRoundRect(
            color = Color(0xFF0061A4).copy(alpha = 0.2f),
            topLeft = Offset(lowerThird, y),
            size = Size(upperThird - lowerThird, barHeight),
            cornerRadius = CornerRadius(0f)
        )

        drawRoundRect(
            color = EmeraldGain.copy(alpha = 0.25f),
            topLeft = Offset(upperThird, y),
            size = Size(size.width - upperThird, barHeight),
            cornerRadius = CornerRadius(4.dp.toPx())
        )

        val markerX = pricePosition * size.width
        drawCircle(
            color = PrimaryBlue,
            radius = 6.dp.toPx(),
            center = Offset(markerX, size.height / 2)
        )
        drawCircle(
            color = Color.White,
            radius = 3.dp.toPx(),
            center = Offset(markerX, size.height / 2)
        )

        val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        drawLine(
            color = PrimaryBlue.copy(alpha = 0.3f),
            start = Offset(markerX, 0f),
            end = Offset(markerX, y - 2.dp.toPx()),
            pathEffect = dashedEffect
        )
        drawLine(
            color = PrimaryBlue.copy(alpha = 0.3f),
            start = Offset(markerX, y + barHeight + 2.dp.toPx()),
            end = Offset(markerX, size.height),
            pathEffect = dashedEffect
        )
    }
}

@Composable
private fun VolatilityBadge(percentile: Double, label: String) {
    val color = when {
        percentile >= 90 -> CrimsonLoss
        percentile >= 75 -> GoldHero
        percentile >= 50 -> PrimaryBlue
        percentile >= 25 -> EmeraldGain
        else -> TextMuted
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                Icons.Default.Speed,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
