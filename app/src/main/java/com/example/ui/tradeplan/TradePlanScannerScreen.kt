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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

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
                                Text("Trade Plan Scanner", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                                Text("Analyze all pairs across 6 strategies & 6 timeframes", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
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

            // Summary Stats
            if (tradePlans.isNotEmpty()) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryStatCard("Total Signals", "${tradePlans.size}", PrimaryBlue, Modifier.weight(1f))
                        SummaryStatCard("BUY", "${tradePlans.count { it.signal.direction == TradeDirection.BUY }}", EmeraldGain, Modifier.weight(1f))
                        SummaryStatCard("SELL", "${tradePlans.count { it.signal.direction == TradeDirection.SELL }}", CrimsonLoss, Modifier.weight(1f))
                    }
                }

                // Filter Chips
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
            if (tradePlans.isEmpty() && !isScanning) {
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
                                Text("No trade plans yet", color = TextSecondary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Tap SCAN ALL PAIRS to analyze all symbols", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

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
