package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Candle
import com.example.domain.model.StateMachineState
import com.example.domain.model.TradingMode
import com.example.ui.theme.*

@Composable
fun StatusPulseIndicator(
    state: StateMachineState,
    modifier: Modifier = Modifier
) {
    val baseColor = when (state) {
        StateMachineState.READY, StateMachineState.ANALYZING, StateMachineState.POSITION_OPEN -> EmeraldGain
        StateMachineState.STARTING, StateMachineState.CONNECTING, StateMachineState.SYNCING, StateMachineState.VALIDATING, StateMachineState.EXECUTING -> CyanLight
        StateMachineState.PAUSED, StateMachineState.STOPPING -> StatusWarning
        StateMachineState.SAFE_MODE, StateMachineState.ERROR -> StatusError
        StateMachineState.STOPPED -> TextMuted
        StateMachineState.SIGNAL_FOUND -> GoldHero
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(baseColor.copy(alpha = alpha))
        )
        Text(
            text = state.name,
            style = MaterialTheme.typography.labelSmall,
            color = baseColor,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun ModeBadge(
    mode: TradingMode,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = when (mode) {
        TradingMode.PAPER -> Color(0xFF1E293B) to CyanLight
        TradingMode.DEMO -> Color(0xFF2E2611) to GoldLight
        TradingMode.LIVE -> Color(0xFF3B1219) to CrimsonLoss
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Text(
            text = mode.name,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String? = null,
    valueColor: Color = TextPrimary,
    modifier: Modifier = Modifier,
    testTag: String = "metric_card"
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CardBorderDark),
        modifier = modifier.testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = valueColor,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun FactorChip(
    label: String,
    passed: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (passed) Color(0xFF063321) else Color(0xFF381419),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (passed) EmeraldGain.copy(alpha = 0.5f) else CrimsonLoss.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Dangerous,
                contentDescription = if (passed) "Passed" else "Failed",
                tint = if (passed) EmeraldGain else CrimsonLoss,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (passed) TextPrimary else TextSecondary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SparklineChart(
    points: List<Pair<Long, Double>>,
    modifier: Modifier = Modifier,
    lineColor: Color = CyanLight
) {
    if (points.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No equity data points yet", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        return
    }

    val minVal = points.minOf { it.second }
    val maxVal = points.maxOf { it.second }
    val range = if (maxVal > minVal) maxVal - minVal else 1.0

    Canvas(modifier = modifier.fillMaxWidth().height(140.dp)) {
        val width = size.width
        val height = size.height
        val stepX = width / (points.size - 1)

        val path = Path()
        val fillPath = Path()

        points.forEachIndexed { i, p ->
            val x = i * stepX
            val y = height - (((p.second - minVal) / range) * (height - 20) + 10).toFloat()

            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        fillPath.lineTo(width, height)
        fillPath.close()

        // Gradient fill
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.35f), Color.Transparent),
                startY = 0f,
                endY = height
            )
        )

        // Stroke line
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx())
        )
    }
}

@Composable
fun MiniCandleChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier
) {
    if (candles.isEmpty()) return

    val displayCandles = candles.takeLast(30)
    val high = displayCandles.maxOf { it.high }
    val low = displayCandles.minOf { it.low }
    val range = if (high > low) high - low else 1.0

    Canvas(modifier = modifier.fillMaxWidth().height(100.dp)) {
        val w = size.width
        val h = size.height
        val candleWidth = (w / displayCandles.size) * 0.7f
        val gap = (w / displayCandles.size) * 0.3f

        displayCandles.forEachIndexed { i, candle ->
            val x = i * (candleWidth + gap) + (candleWidth / 2f)
            val isBullish = candle.close >= candle.open
            val color = if (isBullish) EmeraldGain else CrimsonLoss

            val yHigh = h - (((candle.high - low) / range) * (h - 10) + 5).toFloat()
            val yLow = h - (((candle.low - low) / range) * (h - 10) + 5).toFloat()
            val yOpen = h - (((candle.open - low) / range) * (h - 10) + 5).toFloat()
            val yClose = h - (((candle.close - low) / range) * (h - 10) + 5).toFloat()

            // Wick
            drawLine(
                color = color,
                start = Offset(x, yHigh),
                end = Offset(x, yLow),
                strokeWidth = 1.5.dp.toPx()
            )

            // Body
            val top = minOf(yOpen, yClose)
            val bottom = maxOf(yOpen, yClose)
            val bodyHeight = maxOf(2f, bottom - top)

            drawRect(
                color = color,
                topLeft = Offset(x - (candleWidth / 2f), top),
                size = androidx.compose.ui.geometry.Size(candleWidth, bodyHeight)
            )
        }
    }
}
