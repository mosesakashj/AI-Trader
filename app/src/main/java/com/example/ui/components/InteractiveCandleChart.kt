package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.indicators.IndicatorCalculator
import com.example.domain.model.Candle
import com.example.domain.model.Quote
import com.example.domain.model.Timeframe
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalTextApi::class)
@Composable
fun InteractiveCandleChart(
    candles: List<Candle>,
    currentQuote: Quote?,
    timeframe: Timeframe,
    onTimeframeSelected: (Timeframe) -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showEma by remember { mutableStateOf(true) }
    var showVolume by remember { mutableStateOf(true) }
    var selectedCandleIndex by remember { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorderDark),
        modifier = modifier.fillMaxWidth().testTag("interactive_candle_chart_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header Toolbar: Timeframe Pills & Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timeframe Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val timeframes = listOf(
                        Timeframe.M1,
                        Timeframe.M5,
                        Timeframe.M15,
                        Timeframe.M30,
                        Timeframe.H1,
                        Timeframe.H4,
                        Timeframe.D1
                    )
                    timeframes.forEach { tf ->
                        val isSelected = tf == timeframe
                        Surface(
                            onClick = { onTimeframeSelected(tf) },
                            color = if (isSelected) PrimaryBlueContainer else SurfaceVariantDark,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (isSelected) PrimaryBlue else CardBorderDark),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(
                                    text = tf.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) PrimaryBlue else TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Toggles & Refresh
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // EMA Toggle
                    IconButton(
                        onClick = { showEma = !showEma },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = "Toggle EMA",
                            tint = if (showEma) CyanLight else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Refresh Button
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(28.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp), color = PrimaryBlue)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Feed",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chart HUD Bar (OHLC Info on hover/tap)
            val activeCandle = selectedCandleIndex?.let { idx ->
                candles.getOrNull(idx)
            } ?: candles.lastOrNull()

            Surface(
                color = SurfaceVariantDark,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (activeCandle != null) {
                    val isBullish = activeCandle.close >= activeCandle.open
                    val changeVal = activeCandle.close - activeCandle.open
                    val changePct = if (activeCandle.open > 0) (changeVal / activeCandle.open) * 100.0 else 0.0
                    val timeFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
                    val dateStr = timeFormat.format(Date(activeCandle.openTime))

                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${if (changeVal >= 0) "+" else ""}${"%.2f".format(changeVal)} (${"%.2f".format(changePct)}%)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isBullish) EmeraldGain else CrimsonLoss,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "O: ${"%.2f".format(activeCandle.open)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "H: ${"%.2f".format(activeCandle.high)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldGain,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "L: ${"%.2f".format(activeCandle.low)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CrimsonLoss,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "C: ${"%.2f".format(activeCandle.close)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isBullish) EmeraldGain else CrimsonLoss,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "Vol: ${"%.0f".format(activeCandle.volume)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        Text("Connecting to live exchange orderbook...", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Candlestick + Indicators Canvas
            if (candles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            } else {
                val displayCandles = remember(candles) {
                    if (candles.size > 50) candles.takeLast(50) else candles
                }

                // Compute EMAs across the displayed candles
                val closes = remember(displayCandles) { displayCandles.map { it.close } }
                val ema20List = remember(closes) { IndicatorCalculator.calculateEma(closes, 20) }
                val ema50List = remember(closes) { IndicatorCalculator.calculateEma(closes, 50) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(SurfaceDark)
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(displayCandles) {
                                detectTapGestures(
                                    onPress = { offset ->
                                        val barWidth = size.width / displayCandles.size
                                        val idx = (offset.x / barWidth).toInt().coerceIn(0, displayCandles.size - 1)
                                        selectedCandleIndex = idx
                                    },
                                    onTap = { offset ->
                                        val barWidth = size.width / displayCandles.size
                                        val idx = (offset.x / barWidth).toInt().coerceIn(0, displayCandles.size - 1)
                                        selectedCandleIndex = idx
                                    }
                                )
                            }
                            .pointerInput(displayCandles) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val barWidth = size.width / displayCandles.size
                                        val idx = (offset.x / barWidth).toInt().coerceIn(0, displayCandles.size - 1)
                                        selectedCandleIndex = idx
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val barWidth = size.width / displayCandles.size
                                        val idx = (change.position.x / barWidth).toInt().coerceIn(0, displayCandles.size - 1)
                                        selectedCandleIndex = idx
                                    },
                                    onDragEnd = {
                                        // Keep selected or release
                                    }
                                )
                            }
                    ) {
                        val canvasW = size.width
                        val canvasH = size.height

                        val priceAreaHeight = if (showVolume) canvasH * 0.78f else canvasH - 24.dp.toPx()
                        val volumeAreaTop = priceAreaHeight + 8.dp.toPx()
                        val volumeAreaHeight = canvasH - volumeAreaTop - 20.dp.toPx()
                        val rightAxisMargin = 56.dp.toPx()
                        val chartWidth = canvasW - rightAxisMargin

                        val candleCount = displayCandles.size
                        if (candleCount == 0) return@Canvas

                        val maxPrice = displayCandles.maxOf { it.high }
                        val minPrice = displayCandles.minOf { it.low }
                        val priceRange = if (maxPrice > minPrice) maxPrice - minPrice else 1.0
                        val pricePadding = priceRange * 0.05
                        val topPrice = maxPrice + pricePadding
                        val bottomPrice = minPrice - pricePadding
                        val fullRange = topPrice - bottomPrice

                        val maxVolume = displayCandles.maxOfOrNull { it.volume }?.takeIf { it > 0 } ?: 1.0

                        fun priceToY(p: Double): Float {
                            return (priceAreaHeight - ((p - bottomPrice) / fullRange * priceAreaHeight)).toFloat()
                        }

                        // 1. Draw Horizontal Price Gridlines & Axis Labels
                        val gridCount = 4
                        val gridDottedEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

                        for (i in 0..gridCount) {
                            val ratio = i.toFloat() / gridCount
                            val y = priceAreaHeight * ratio
                            val priceLevel = topPrice - (ratio * fullRange)

                            // Dotted horizontal gridline
                            drawLine(
                                color = CardBorderDark.copy(alpha = 0.6f),
                                start = Offset(0f, y),
                                end = Offset(chartWidth, y),
                                strokeWidth = 1f,
                                pathEffect = gridDottedEffect
                            )

                            // Price label on right margin
                            val textLayout = textMeasurer.measure(
                                text = "%.2f".format(priceLevel),
                                style = TextStyle(
                                    color = TextMuted,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            drawText(
                                textLayoutResult = textLayout,
                                topLeft = Offset(chartWidth + 6.dp.toPx(), y - (textLayout.size.height / 2f))
                            )
                        }

                        // 2. Draw Volume Bars (at bottom)
                        if (showVolume) {
                            val barSlotWidth = chartWidth / candleCount
                            val barWidth = barSlotWidth * 0.7f

                            displayCandles.forEachIndexed { i, c ->
                                val x = i * barSlotWidth + (barSlotWidth - barWidth) / 2f
                                val isBullish = c.close >= c.open
                                val volHeight = ((c.volume / maxVolume) * volumeAreaHeight).toFloat().coerceAtLeast(2f)
                                val volY = canvasH - 20.dp.toPx() - volHeight

                                drawRect(
                                    color = if (isBullish) EmeraldGain.copy(alpha = 0.35f) else CrimsonLoss.copy(alpha = 0.35f),
                                    topLeft = Offset(x, volY),
                                    size = Size(barWidth, volHeight)
                                )
                            }
                        }

                        // 3. Draw Candlesticks (Wicks & Bodies)
                        val barSlotWidth = chartWidth / candleCount
                        val candleBodyWidth = max(2f, barSlotWidth * 0.65f)

                        displayCandles.forEachIndexed { i, c ->
                            val centerX = i * barSlotWidth + (barSlotWidth / 2f)
                            val isBullish = c.close >= c.open
                            val candleColor = if (isBullish) EmeraldGain else CrimsonLoss

                            val yHigh = priceToY(c.high)
                            val yLow = priceToY(c.low)
                            val yOpen = priceToY(c.open)
                            val yClose = priceToY(c.close)

                            // High/Low Wick
                            drawLine(
                                color = candleColor,
                                start = Offset(centerX, yHigh),
                                end = Offset(centerX, yLow),
                                strokeWidth = 1.5.dp.toPx()
                            )

                            // Real Candlestick Body
                            val topY = min(yOpen, yClose)
                            val bottomY = max(yOpen, yClose)
                            val bodyHeight = max(2f, bottomY - topY)

                            drawRect(
                                color = candleColor,
                                topLeft = Offset(centerX - (candleBodyWidth / 2f), topY),
                                size = Size(candleBodyWidth, bodyHeight)
                            )
                        }

                        // 4. Draw EMA Lines Overlays
                        if (showEma && displayCandles.size >= 10) {
                            // Fast EMA 20 (Cyan)
                            if (ema20List.isNotEmpty()) {
                                val emaPath = Path()
                                var started = false
                                val offsetIdx = displayCandles.size - ema20List.size
                                ema20List.forEachIndexed { idx, emaVal ->
                                    val candleIdx = idx + offsetIdx
                                    if (candleIdx in 0 until candleCount) {
                                        val x = candleIdx * barSlotWidth + (barSlotWidth / 2f)
                                        val y = priceToY(emaVal)
                                        if (!started) {
                                            emaPath.moveTo(x, y)
                                            started = true
                                        } else {
                                            emaPath.lineTo(x, y)
                                        }
                                    }
                                }
                                drawPath(
                                    path = emaPath,
                                    color = CyanLight,
                                    style = Stroke(width = 1.8.dp.toPx())
                                )
                            }

                            // Slow EMA 50 (Gold)
                            if (ema50List.isNotEmpty()) {
                                val ema50Path = Path()
                                var started50 = false
                                val offsetIdx50 = displayCandles.size - ema50List.size
                                ema50List.forEachIndexed { idx, emaVal ->
                                    val candleIdx = idx + offsetIdx50
                                    if (candleIdx in 0 until candleCount) {
                                        val x = candleIdx * barSlotWidth + (barSlotWidth / 2f)
                                        val y = priceToY(emaVal)
                                        if (!started50) {
                                            ema50Path.moveTo(x, y)
                                            started50 = true
                                        } else {
                                            ema50Path.lineTo(x, y)
                                        }
                                    }
                                }
                                drawPath(
                                    path = ema50Path,
                                    color = GoldHero,
                                    style = Stroke(width = 1.8.dp.toPx())
                                )
                            }
                        }

                        // 5. Live Current Price Marker Line & Badge
                        val latestPrice = currentQuote?.ask ?: displayCandles.lastOrNull()?.close
                        if (latestPrice != null && latestPrice in bottomPrice..topPrice) {
                            val liveY = priceToY(latestPrice)

                            // Dashed horizontal live line
                            drawLine(
                                color = CyanLight,
                                start = Offset(0f, liveY),
                                end = Offset(chartWidth, liveY),
                                strokeWidth = 1.2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
                            )

                            // Live price pill badge on right axis
                            val badgeWidth = rightAxisMargin - 4.dp.toPx()
                            val badgeHeight = 16.dp.toPx()
                            drawRect(
                                color = CyanLight,
                                topLeft = Offset(chartWidth + 2.dp.toPx(), liveY - (badgeHeight / 2f)),
                                size = Size(badgeWidth, badgeHeight)
                            )
                            val liveTextLayout = textMeasurer.measure(
                                text = "%.2f".format(latestPrice),
                                style = TextStyle(
                                    color = Color.Black,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black
                                )
                            )
                            drawText(
                                textLayoutResult = liveTextLayout,
                                topLeft = Offset(
                                    chartWidth + 4.dp.toPx(),
                                    liveY - (liveTextLayout.size.height / 2f)
                                )
                            )
                        }

                        // 6. Interactive Crosshair Overlay (when touch/drag active)
                        selectedCandleIndex?.let { selIdx ->
                            if (selIdx in 0 until candleCount) {
                                val selCandle = displayCandles[selIdx]
                                val crossX = selIdx * barSlotWidth + (barSlotWidth / 2f)
                                val crossY = priceToY(selCandle.close)

                                val crossEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)

                                // Vertical guideline
                                drawLine(
                                    color = TextSecondary,
                                    start = Offset(crossX, 0f),
                                    end = Offset(crossX, canvasH - 20.dp.toPx()),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = crossEffect
                                )

                                // Horizontal guideline
                                drawLine(
                                    color = TextSecondary,
                                    start = Offset(0f, crossY),
                                    end = Offset(chartWidth, crossY),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = crossEffect
                                )

                                // Cursor highlight dot
                                drawCircle(
                                    color = if (selCandle.close >= selCandle.open) EmeraldGain else CrimsonLoss,
                                    radius = 4.dp.toPx(),
                                    center = Offset(crossX, crossY)
                                )
                            }
                        }

                        // 7. Time Axis Labels (Bottom)
                        val timeStep = max(1, candleCount / 4)
                        val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
                        for (i in 0 until candleCount step timeStep) {
                            val c = displayCandles[i]
                            val x = i * barSlotWidth + (barSlotWidth / 2f)
                            val timeStr = timeFormat.format(Date(c.openTime))

                            val textLayout = textMeasurer.measure(
                                text = timeStr,
                                style = TextStyle(
                                    color = TextMuted,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            drawText(
                                textLayoutResult = textLayout,
                                topLeft = Offset(
                                    (x - textLayout.size.width / 2f).coerceIn(0f, chartWidth - textLayout.size.width),
                                    canvasH - 16.dp.toPx()
                                )
                            )
                        }
                    }
                }
            }

            // Legend Footer
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (showEma) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(color = CyanLight, shape = RoundedCornerShape(2.dp), modifier = Modifier.size(10.dp, 2.dp)) {}
                            Text("EMA 20", style = MaterialTheme.typography.labelSmall, color = CyanLight, fontSize = 10.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(color = GoldHero, shape = RoundedCornerShape(2.dp), modifier = Modifier.size(10.dp, 2.dp)) {}
                            Text("EMA 50", style = MaterialTheme.typography.labelSmall, color = GoldHero, fontSize = 10.sp)
                        }
                    }
                }

                Text(
                    text = "Tap & drag chart to inspect OHLC",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}
