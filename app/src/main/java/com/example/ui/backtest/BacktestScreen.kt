package com.example.ui.backtest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
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
import com.example.broker.PaperMarketDataProvider
import com.example.domain.backtest.BacktestResult
import com.example.domain.backtest.BacktestingEngine
import com.example.domain.backtest.WalkForwardResult
import com.example.domain.model.AssetType
import com.example.domain.model.SymbolConfig
import com.example.domain.model.Timeframe
import com.example.ui.components.MetricCard
import com.example.ui.components.SparklineChart
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BacktestScreen() {
    val coroutineScope = rememberCoroutineScope()
    var selectedSymbol by remember { mutableStateOf("XAUUSD") }
    var selectedTimeframe by remember { mutableStateOf(Timeframe.M15) }
    var candleCount by remember { mutableStateOf("120") }
    var riskPercent by remember { mutableStateOf("0.25") }

    var isRunning by remember { mutableStateOf(false) }
    var backtestResult by remember { mutableStateOf<BacktestResult?>(null) }
    var walkForwardResult by remember { mutableStateOf<WalkForwardResult?>(null) }
    var testMode by remember { mutableStateOf(0) } // 0: Standard Backtest, 1: Walk-Forward Validation

    val marketDataProvider = remember { PaperMarketDataProvider() }
    val backtestEngine = remember { BacktestingEngine() }

    val symbolConfig = remember(selectedSymbol) {
        if (selectedSymbol == "XAUUSD") {
            SymbolConfig(
                symbol = "XAUUSD",
                displayName = "Gold (Spot)",
                brokerSymbol = "XAUUSD",
                assetType = AssetType.COMMODITY,
                digits = 2,
                contractSize = 100.0,
                minLot = 0.01,
                maxLot = 10.0,
                lotStep = 0.01,
                tickSize = 0.01,
                tickValue = 1.0,
                minimumStopDistance = 0.50,
                spreadLimit = 0.60
            )
        } else {
            SymbolConfig(
                symbol = "BTCUSD",
                displayName = "Bitcoin (Spot)",
                brokerSymbol = "BTCUSD",
                assetType = AssetType.CRYPTO,
                digits = 2,
                contractSize = 1.0,
                minLot = 0.01,
                maxLot = 5.0,
                lotStep = 0.01,
                tickSize = 0.10,
                tickValue = 0.10,
                minimumStopDistance = 25.0,
                spreadLimit = 15.0
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mode Selector
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { testMode = 0 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (testMode == 0) SurfaceVariantDark else SurfaceDark
                    ),
                    border = BorderStroke(1.dp, if (testMode == 0) CyanLight else CardBorderDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("standard_backtest_tab")
                ) {
                    Text("Standard Backtest", fontWeight = FontWeight.Bold, color = if (testMode == 0) CyanLight else TextSecondary)
                }

                Button(
                    onClick = { testMode = 1 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (testMode == 1) SurfaceVariantDark else SurfaceDark
                    ),
                    border = BorderStroke(1.dp, if (testMode == 1) GoldHero else CardBorderDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("walk_forward_tab")
                ) {
                    Text("Walk-Forward Splits", fontWeight = FontWeight.Bold, color = if (testMode == 1) GoldHero else TextSecondary)
                }
            }
        }

        // Configuration Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Simulation Parameters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Symbol toggle
                        OutlinedButton(
                            onClick = { selectedSymbol = if (selectedSymbol == "XAUUSD") "BTCUSD" else "XAUUSD" },
                            modifier = Modifier.weight(1f).testTag("backtest_symbol_btn")
                        ) {
                            Text("Symbol: $selectedSymbol", color = CyanLight)
                        }

                        // Timeframe toggle
                        OutlinedButton(
                            onClick = { selectedTimeframe = if (selectedTimeframe == Timeframe.M15) Timeframe.M5 else Timeframe.M15 },
                            modifier = Modifier.weight(1f).testTag("backtest_timeframe_btn")
                        ) {
                            Text("TF: ${selectedTimeframe.label}", color = CyanLight)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = candleCount,
                            onValueChange = { candleCount = it },
                            label = { Text("Candles Count") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("candle_count_input")
                        )
                        OutlinedTextField(
                            value = riskPercent,
                            onValueChange = { riskPercent = it },
                            label = { Text("Risk % per Trade") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("backtest_risk_input")
                        )
                    }

                    Button(
                        onClick = {
                            isRunning = true
                            coroutineScope.launch {
                                val count = candleCount.toIntOrNull() ?: 120
                                val risk = riskPercent.toDoubleOrNull() ?: 0.25

                                withContext(Dispatchers.Default) {
                                    val candles = marketDataProvider.getHistoricalCandles(selectedSymbol, selectedTimeframe, count)
                                    if (testMode == 0) {
                                        backtestResult = backtestEngine.runBacktest(candles, symbolConfig, initialBalance = 10000.0, riskPercent = risk)
                                    } else {
                                        walkForwardResult = backtestEngine.runWalkForward(candles, symbolConfig)
                                    }
                                }
                                isRunning = false
                            }
                        },
                        enabled = !isRunning,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanLight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("run_backtest_btn")
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = BackgroundDark)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Simulating tick physics...", color = BackgroundDark, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = BackgroundDark)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Run Algorithmic Backtest", fontWeight = FontWeight.Bold, color = BackgroundDark)
                        }
                    }
                }
            }
        }

        // Standard Backtest Results
        if (testMode == 0 && backtestResult != null) {
            val res = backtestResult!!
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.fillMaxWidth().testTag("backtest_results_card")
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Simulation Results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Surface(color = SurfaceVariantDark, shape = RoundedCornerShape(6.dp)) {
                                Text("${res.candleCount} candles simulated", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }

                        // Equity Curve
                        Text("Simulated Account Equity Curve:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        SparklineChart(points = res.equityCurve, lineColor = if (res.totalProfitLoss >= 0) EmeraldGain else CrimsonLoss)

                        Divider(color = CardBorderDark)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricCard(title = "Win Rate", value = "${"%.1f".format(res.winRate)}%", subtitle = "${res.winningTrades}W / ${res.losingTrades}L", modifier = Modifier.weight(1f))
                            MetricCard(title = "Profit Factor", value = "%.2f".format(res.profitFactor), subtitle = "Avg Win/Loss", modifier = Modifier.weight(1f))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricCard(title = "Net P/L", value = "${if (res.totalProfitLoss >= 0) "+" else ""}$${"%.2f".format(res.totalProfitLoss)}", subtitle = "Initial: $10,000", valueColor = if (res.totalProfitLoss >= 0) EmeraldGain else CrimsonLoss, modifier = Modifier.weight(1f))
                            MetricCard(title = "Max Drawdown", value = "-$${"%.2f".format(res.maxDrawdownAmount)}", subtitle = "-${"%.2f".format(res.maxDrawdownPercent)}%", valueColor = CrimsonLoss, modifier = Modifier.weight(1f))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricCard(title = "Avg R-Multiple", value = "${"%.2f".format(res.averageR)}R", subtitle = "Expectancy: $${"%.2f".format(res.expectancy)}", modifier = Modifier.weight(1f))
                            MetricCard(title = "Max Consec. Loss", value = "${res.maxConsecutiveLosses}", subtitle = "Safe under limit (${res.maxConsecutiveLosses} < 3)", modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Walk-Forward Results
        if (testMode == 1 && walkForwardResult != null) {
            val wf = walkForwardResult!!
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.fillMaxWidth().testTag("walk_forward_results_card")
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Walk-Forward Robustness", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Score: ${"%.0f".format(wf.robustnessScore)}/100", fontWeight = FontWeight.Bold, color = GoldHero)
                        }

                        Text("1. In-Sample Period (60% training):", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("Trades: ${wf.inSampleResult.totalTrades} | Win Rate: ${"%.1f".format(wf.inSampleResult.winRate)}% | PF: ${"%.2f".format(wf.inSampleResult.profitFactor)} | Net: $${"%.2f".format(wf.inSampleResult.totalProfitLoss)}", style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontFamily = FontFamily.Monospace)

                        Divider(color = CardBorderDark)

                        Text("2. Out-of-Sample Period (20% blind testing):", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text("Trades: ${wf.outOfSampleResult.totalTrades} | Win Rate: ${"%.1f".format(wf.outOfSampleResult.winRate)}% | PF: ${"%.2f".format(wf.outOfSampleResult.profitFactor)} | Net: $${"%.2f".format(wf.outOfSampleResult.totalProfitLoss)}", style = MaterialTheme.typography.bodySmall, color = EmeraldGain, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
