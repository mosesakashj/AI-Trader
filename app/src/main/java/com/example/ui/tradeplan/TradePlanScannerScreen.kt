package com.example.ui.tradeplan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.*
import com.example.ui.theme.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradePlanScannerScreen(viewModel: TradePlanScannerViewModel = viewModel()) {
    val tradePlans by viewModel.tradePlans.collectAsStateWithLifecycle()
    val filteredPlans by viewModel.filteredPlans.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val filterDirection by viewModel.filterDirection.collectAsStateWithLifecycle()
    val filterStrategy by viewModel.filterStrategy.collectAsStateWithLifecycle()
    val filterTimeframe by viewModel.filterTimeframe.collectAsStateWithLifecycle()
    val executionResult by viewModel.executionResult.collectAsStateWithLifecycle()
    val openPositions by viewModel.openPositions.collectAsState(initial = emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(executionResult) {
        when (val state = executionResult) {
            is ExecutionState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearExecutionState()
            }
            is ExecutionState.Error -> {
                snackbarHostState.showSnackbar("Error: ${state.message}")
                viewModel.clearExecutionState()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundDark
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with Scan Button
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.fillMaxWidth().testTag("scanner_header_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Trade Center", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                                Text("Active positions & scan across 6 strategies x 6 timeframes", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { viewModel.scanAllPairs() },
                            enabled = !isScanning,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("scan_all_btn")
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scanning...", color = Color.White, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SCAN ALL PAIRS", fontWeight = FontWeight.Black, color = Color.White)
                            }
                        }
                    }
                }
            }

            // Active Positions Section
            if (openPositions.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ACTIVE POSITIONS", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge, color = GoldHero)
                        Surface(color = GoldContainer, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                text = "${openPositions.size}",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                color = GoldHero
                            )
                        }
                    }
                }

                // Portfolio overview row
                item {
                    val totalPnL = openPositions.sumOf { it.unrealizedProfit }
                    val isProfit = totalPnL >= 0
                    val longCount = openPositions.count { it.direction == TradeDirection.BUY }
                    val shortCount = openPositions.count { it.direction == TradeDirection.SELL }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, CardBorderDark)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total P/L", fontSize = 10.sp, color = TextMuted)
                                Text(
                                    text = "${if (isProfit) "+" else ""}${"%.2f".format(totalPnL)}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isProfit) EmeraldGain else CrimsonLoss,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Positions", fontSize = 10.sp, color = TextMuted)
                                Text("${openPositions.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Long", fontSize = 10.sp, color = TextMuted)
                                Text("$longCount", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = EmeraldGain)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Short", fontSize = 10.sp, color = TextMuted)
                                Text("$shortCount", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CrimsonLoss)
                            }
                        }
                    }
                }

                // Individual position cards
                items(openPositions) { position ->
                    ActivePositionCard(
                        position = position,
                        onClose = { viewModel.closePosition(position) }
                    )
                }
            } else if (tradePlans.isEmpty() && !isScanning) {
                // Empty state - show only when no positions AND no scan results
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CardBorderDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Analytics, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No active positions or trade plans", color = TextSecondary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Tap SCAN ALL PAIRS to analyze all symbols", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Scan Results Section
            if (tradePlans.isNotEmpty()) {
                item {
                    HorizontalDivider(color = CardBorderDark)
                }

                item {
                    Text("SCAN RESULTS", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge, color = PrimaryBlue)
                }

                // Summary Stats
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryStatCard("Total", "${tradePlans.size}", PrimaryBlue, Modifier.weight(1f))
                        SummaryStatCard("BUY", "${tradePlans.count { it.signal.direction == TradeDirection.BUY }}", EmeraldGain, Modifier.weight(1f))
                        SummaryStatCard("SELL", "${tradePlans.count { it.signal.direction == TradeDirection.SELL }}", CrimsonLoss, Modifier.weight(1f))
                    }
                }

                // Direction Filter Chips
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = filterDirection == null,
                            onClick = { viewModel.setFilterDirection(null) },
                            label = { Text("ALL") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PrimaryBlueContainer, selectedLabelColor = PrimaryBlue, containerColor = SurfaceDark, labelColor = TextSecondary),
                            border = BorderStroke(1.dp, if (filterDirection == null) PrimaryBlue else CardBorderDark),
                            shape = RoundedCornerShape(10.dp)
                        )
                        FilterChip(
                            selected = filterDirection == TradeDirection.BUY,
                            onClick = { viewModel.setFilterDirection(if (filterDirection == TradeDirection.BUY) null else TradeDirection.BUY) },
                            label = { Text("BUY") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldContainer, selectedLabelColor = EmeraldGain, containerColor = SurfaceDark, labelColor = TextSecondary),
                            border = BorderStroke(1.dp, if (filterDirection == TradeDirection.BUY) EmeraldGain else CardBorderDark),
                            shape = RoundedCornerShape(10.dp)
                        )
                        FilterChip(
                            selected = filterDirection == TradeDirection.SELL,
                            onClick = { viewModel.setFilterDirection(if (filterDirection == TradeDirection.SELL) null else TradeDirection.SELL) },
                            label = { Text("SELL") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CrimsonContainer, selectedLabelColor = CrimsonLoss, containerColor = SurfaceDark, labelColor = TextSecondary),
                            border = BorderStroke(1.dp, if (filterDirection == TradeDirection.SELL) CrimsonLoss else CardBorderDark),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                // Strategy Filter Chips
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = filterStrategy == null,
                            onClick = { viewModel.setFilterStrategy(null) },
                            label = { Text("All Strategies", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PrimaryBlueContainer, selectedLabelColor = PrimaryBlue, containerColor = SurfaceDark, labelColor = TextSecondary),
                            border = BorderStroke(1.dp, if (filterStrategy == null) PrimaryBlue else CardBorderDark),
                            shape = RoundedCornerShape(8.dp)
                        )
                        StrategyType.entries.forEach { strategy ->
                            FilterChip(
                                selected = filterStrategy == strategy,
                                onClick = { viewModel.setFilterStrategy(if (filterStrategy == strategy) null else strategy) },
                                label = { Text(strategy.displayName, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PrimaryBlueContainer, selectedLabelColor = PrimaryBlue, containerColor = SurfaceDark, labelColor = TextSecondary),
                                border = BorderStroke(1.dp, if (filterStrategy == strategy) PrimaryBlue else CardBorderDark),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }

                // Timeframe Filter Chips
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = filterTimeframe == null,
                            onClick = { viewModel.setFilterTimeframe(null) },
                            label = { Text("All Timeframes", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PrimaryBlueContainer, selectedLabelColor = PrimaryBlue, containerColor = SurfaceDark, labelColor = TextSecondary),
                            border = BorderStroke(1.dp, if (filterTimeframe == null) PrimaryBlue else CardBorderDark),
                            shape = RoundedCornerShape(8.dp)
                        )
                        listOf(Timeframe.M1, Timeframe.M5, Timeframe.M15, Timeframe.M30, Timeframe.H1, Timeframe.H4).forEach { tf ->
                            FilterChip(
                                selected = filterTimeframe == tf,
                                onClick = { viewModel.setFilterTimeframe(if (filterTimeframe == tf) null else tf) },
                                label = { Text(tf.label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PrimaryBlueContainer, selectedLabelColor = PrimaryBlue, containerColor = SurfaceDark, labelColor = TextSecondary),
                                border = BorderStroke(1.dp, if (filterTimeframe == tf) PrimaryBlue else CardBorderDark),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }

            // Trade Plan Cards
            items(filteredPlans) { plan ->
                TradePlanCard(
                    plan = plan,
                    onExecute = { viewModel.executeTrade(it) }
                )
            }
        }
    }
}

@Composable
private fun ActivePositionCard(
    position: Position,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCloseDialog by remember { mutableStateOf(false) }
    val isLong = position.direction == TradeDirection.BUY
    val isProfit = position.unrealizedProfit >= 0
    val durationMs = System.currentTimeMillis() - position.openedAt

    if (showCloseDialog) {
        AlertDialog(
            onDismissRequest = { showCloseDialog = false },
            title = { Text("Close ${position.symbol}?", fontWeight = FontWeight.Bold, color = CrimsonLoss) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${if (isLong) "LONG" else "SHORT"} ${position.symbol} @ ${"%.5f".format(position.entryPrice)}", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Volume: ${"%.2f".format(position.volume)}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("P/L: ${if (isProfit) "+" else ""}${"%.2f".format(position.unrealizedProfit)}", color = if (isProfit) EmeraldGain else CrimsonLoss, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(onClick = { showCloseDialog = false; onClose() }, colors = ButtonDefaults.buttonColors(containerColor = CrimsonLoss)) {
                    Text("CLOSE POSITION", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCloseDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = SurfaceDark
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (isProfit) EmeraldDark.copy(alpha = 0.4f) else CrimsonDark.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Row 1: Direction + Symbol + Volume + Duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Direction badge
                Surface(
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

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = position.symbol, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Vol: ${"%.2f".format(position.volume)}", fontSize = 11.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.Timer, contentDescription = null, tint = TextMuted, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = formatDuration(durationMs), fontSize = 11.sp, color = TextMuted)
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (isProfit) "+" else ""}${"%.2f".format(position.unrealizedProfit)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isProfit) EmeraldGain else CrimsonLoss,
                        fontFamily = FontFamily.Monospace
                    )
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

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: SL/TP Progress Bar
            SLTPProgressBar(
                currentPrice = position.currentPrice,
                stopLoss = position.stopLoss,
                takeProfit = position.takeProfit,
                isLong = isLong
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Row 3: SL/TP distances + Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val distToSL = abs(position.currentPrice - position.stopLoss)
                    val distToTP = abs(position.takeProfit - position.currentPrice)
                    DistanceLabel("SL", distToSL, CrimsonLoss)
                    DistanceLabel("TP", distToTP, EmeraldGain)
                }

                Button(
                    onClick = { showCloseDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonLoss),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CLOSE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
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
                    .background(if (isLong) EmeraldGain else CrimsonLoss)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("SL ${"%.5f".format(stopLoss)}", fontSize = 9.sp, color = CrimsonLoss, fontFamily = FontFamily.Monospace)
            Text("${"%.5f".format(currentPrice)}", fontSize = 9.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
            Text("TP ${"%.5f".format(takeProfit)}", fontSize = 9.sp, color = EmeraldGain, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun DistanceLabel(label: String, distance: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 9.sp, color = TextMuted)
        Text(
            text = formatDistance(distance),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun SummaryStatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = color)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
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
