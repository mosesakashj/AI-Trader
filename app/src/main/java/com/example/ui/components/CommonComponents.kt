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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
        TradingMode.PAPER -> PrimaryBlueContainer to PrimaryBlue
        TradingMode.DEMO -> GoldContainer to GoldHero
        TradingMode.LIVE -> CrimsonContainer to CrimsonLoss
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.3f)),
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
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
        color = if (passed) EmeraldContainer else CrimsonContainer,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (passed) EmeraldGain.copy(alpha = 0.3f) else CrimsonLoss.copy(alpha = 0.3f)),
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
                color = if (passed) EmeraldDark else CrimsonDark,
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

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: RoundedCornerShape = RoundedCornerShape(12.dp)
) {
    val shimmerColors = listOf(
        SurfaceVariantDark,
        SurfaceDark,
        SurfaceVariantDark
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    Box(
        modifier = modifier
            .clip(cornerRadius)
            .background(brush)
    )
}

@Composable
fun ShimmerCard(
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorderDark),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(20.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShimmerBox(modifier = Modifier.weight(1f).height(48.dp))
                ShimmerBox(modifier = Modifier.weight(1f).height(48.dp))
            }
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(14.dp))
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color = CyanLight,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = CyanLight, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun StreakIndicator(
    streak: Int,
    modifier: Modifier = Modifier
) {
    val isWinStreak = streak > 0
    val absStreak = kotlin.math.abs(streak)
    val color = if (isWinStreak) EmeraldGain else CrimsonLoss
    val label = if (isWinStreak) "Win" else "Loss"

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Surface(
                color = color,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "$absStreak",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Text(
                text = "${label}${if (absStreak > 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun AnimatedEntry(
    visible: Boolean = true,
    delay: Int = 0,
    content: @Composable () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = delay),
        label = "entry_alpha"
    )
    Box(modifier = Modifier.alpha(alpha)) {
        content()
    }
}

private fun Modifier.alpha(alpha: Float): Modifier {
    return this.then(
        Modifier.graphicsLayer(alpha = alpha)
    )
}
