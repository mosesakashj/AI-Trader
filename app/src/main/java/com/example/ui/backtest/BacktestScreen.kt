package com.example.ui.backtest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Tune
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
import com.example.domain.backtest.*
import com.example.domain.model.*
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
    var candleCount by remember { mutableStateOf("200") }
    var riskPercent by remember { mutableStateOf("0.25") }
    var enableAutoBreakEven by remember { mutableStateOf(true) }
    var enableAutoTrailing by remember { mutableStateOf(true) }

    var isRunning by remember { mutableStateOf(false) }
    var backtestResult by remember { mutableStateOf<BacktestResult?>(null) }
    var portfolioResult by remember { mutableStateOf<PortfolioBacktestResult?>(null) }
    var optimizationResults by remember { mutableStateOf<List<OptimizationResult>?>(null) }
    var walkForwardResult by remember { mutableStateOf<WalkForwardResult?>(null) }
    var monteCarloResult by remember { mutableStateOf<MonteCarloResult?>(null) }

    // 0: Single Asset, 1: Portfolio (All Pairs), 2: Parameter Optimizer, 3: Walk-Forward, 4: Monte Carlo
    var testMode by remember { mutableStateOf(0) }

    val marketDataProvider = remember { EdgeTraderApp.instance.tradingEngine }
    val backtestEngine = remember { BacktestingEngine() }

    val symbolConfig = remember(selectedSymbol) {
        SymbolCatalog.get(selectedSymbol)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mode Selector Scrollable Tabs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    0 to "Single Asset",
                    1 to "Portfolio Mode",
                    2 to "Optimizer Grid",
                    3 to "Walk-Forward",
                    4 to "Monte Carlo"
                ).forEach { (mode, title) ->
                    val isSelected = testMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = { testMode = mode },
                        label = { Text(title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlueContainer,
                            selectedLabelColor = PrimaryBlue,
                            containerColor = SurfaceDark,
                            labelColor = TextSecondary
                        ),
                        border = BorderStroke(1.dp, if (isSelected) PrimaryBlue else CardBorderDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("backtest_mode_tab_$mode")
                    )
                }
            }
        }

        // Configuration Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth().testTag("backtest_config_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        when (testMode) {
                            0 -> "Single Asset Backtest Engine"
                            1 -> "Portfolio Multi-Asset Simulation"
                            2 -> "Quantitative Parameter Optimizer"
                            else -> "Walk-Forward Validation Matrix"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    // Symbol Selector (for Single Asset, Optimizer, Walk-Forward)
                    if (testMode != 1) {
                        Text("Select Watchlist Pair", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SymbolCatalog.ALL_SYMBOLS.forEach { config ->
                                val isSelected = selectedSymbol == config.symbol
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedSymbol = config.symbol },
                                    label = { Text(config.symbol) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryBlueContainer,
                                        selectedLabelColor = PrimaryBlue,
                                        containerColor = SurfaceVariantDark,
                                        labelColor = TextSecondary
                                    ),
                                    border = BorderStroke(1.dp, if (isSelected) PrimaryBlue else CardBorderDark),
                                    modifier = Modifier.testTag("chip_${config.symbol}")
                                )
                            }
                        }
                    } else {
                        // Portfolio mode description
                        Text(
                            "Simulates multi-pair automated trading across 8 symbols concurrently (Gold, Bitcoin, Ethereum, Solana, EUR/USD, GBP/USD, USD/JPY, Crude Oil).",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyanLight
                        )
                    }

                    // Timeframe Selector
                    Text("Timeframe Interval", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(Timeframe.M5, Timeframe.M15, Timeframe.H1, Timeframe.H4, Timeframe.D1).forEach { tf ->
                            val isSelected = selectedTimeframe == tf
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedTimeframe = tf },
                                label = { Text(tf.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryBlueContainer,
                                    selectedLabelColor = PrimaryBlue,
                                    containerColor = SurfaceVariantDark,
                                    labelColor = TextSecondary
                                ),
                                border = BorderStroke(1.dp, if (isSelected) PrimaryBlue else CardBorderDark),
                                modifier = Modifier.weight(1f).testTag("chip_tf_${tf.label}")
                            )
                        }
                    }

                    // Risk and Candle count
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = candleCount,
                            onValueChange = { candleCount = it },
                            label = { Text("Bars Count") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("candle_count_input")
                        )

                        OutlinedTextField(
                            value = riskPercent,
                            onValueChange = { riskPercent = it },
                            label = { Text("Risk % / Trade") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("risk_percent_input")
                        )
                    }

                    // Auto Position Management Toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Checkbox(
                                checked = enableAutoBreakEven,
                                onCheckedChange = { enableAutoBreakEven = it },
                                colors = CheckboxDefaults.colors(checkedColor = EmeraldGain)
                            )
                            Text("Auto Break-Even (+1R)", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Checkbox(
                                checked = enableAutoTrailing,
                                onCheckedChange = { enableAutoTrailing = it },
                                colors = CheckboxDefaults.colors(checkedColor = EmeraldGain)
                            )
                            Text("Auto Trailing (+1.5R)", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        }
                    }

                    // Action Button
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isRunning = true
                                val count = candleCount.toIntOrNull() ?: 200
                                val risk = riskPercent.toDoubleOrNull() ?: 0.25

                                withContext(Dispatchers.Default) {
                                    when (testMode) {
                                        0 -> {
                                            // Single Asset Backtest
                                            val candles = marketDataProvider.fetchHistoricalCandles(selectedSymbol, selectedTimeframe, count)
                                            backtestResult = backtestEngine.runBacktest(
                                                candles = candles,
                                                symbolConfig = symbolConfig,
                                                riskPercent = risk,
                                                enableAutoBreakEven = enableAutoBreakEven,
                                                enableAutoTrailing = enableAutoTrailing
                                            )
                                        }
                                        1 -> {
                                            // Portfolio All Pairs
                                            val map = mutableMapOf<String, List<Candle>>()
                                            SymbolCatalog.ALL_SYMBOLS.forEach { cfg ->
                                                map[cfg.symbol] = marketDataProvider.fetchHistoricalCandles(cfg.symbol, selectedTimeframe, count)
                                            }
                                            portfolioResult = backtestEngine.runPortfolioBacktest(
                                                candlesBySymbol = map,
                                                configs = SymbolCatalog.ALL_SYMBOLS,
                                                riskPercent = risk
                                            )
                                        }
                                        2 -> {
                                            // Parameter Optimization Grid
                                            val candles = marketDataProvider.fetchHistoricalCandles(selectedSymbol, selectedTimeframe, count)
                                            optimizationResults = backtestEngine.runParameterOptimization(candles, symbolConfig)
                                        }
                                        3 -> {
                                            // Walk-Forward
                                            val candles = marketDataProvider.fetchHistoricalCandles(selectedSymbol, selectedTimeframe, count)
                                            walkForwardResult = backtestEngine.runWalkForward(candles, symbolConfig)
                                        }
                                    }
                                }
                                isRunning = false
                            }
                        },
                        enabled = !isRunning,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("run_simulation_button")
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Simulating Ticks & Engine Rules...")
                        } else {
                            Icon(
                                when (testMode) {
                                    1 -> Icons.Default.AutoGraph
                                    2 -> Icons.Default.Tune
                                    3 -> Icons.Default.Science
                                    else -> Icons.Default.PlayArrow
                                },
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                when (testMode) {
                                    1 -> "Run Multi-Pair Portfolio Backtest"
                                    2 -> "Run Parameter Grid Optimizer"
                                    3 -> "Execute Walk-Forward Splits"
                                    else -> "Execute Algorithmic Backtest"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Mode 0: Single Asset Results
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
                            Column {
                                Text("${res.symbol} Performance Matrix", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("${res.candleCount} bars (${res.timeframe.label}) simulated", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                            Surface(
                                color = if (res.totalProfitLoss >= 0) EmeraldContainer else CrimsonContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "${if (res.totalProfitLoss >= 0) "+" else ""}$${"%.2f".format(res.totalProfitLoss)}",
                                    color = if (res.totalProfitLoss >= 0) EmeraldGain else CrimsonLoss,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // Equity Curve
                        if (res.equityCurve.size >= 2) {
                            Text("Simulated Account Equity Curve", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            SparklineChart(
                                dataPoints = res.equityCurve.map { it.second },
                                lineColor = if (res.totalProfitLoss >= 0) EmeraldGain else CrimsonLoss,
                                isPositive = res.totalProfitLoss >= 0,
                                modifier = Modifier.fillMaxWidth().height(120.dp)
                            )
                        }

                        // Quantitative KPI Grid
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("Sharpe Ratio", "%.2f".format(res.sharpeRatio), isPositive = res.sharpeRatio >= 1.0, modifier = Modifier.weight(1f))
                            MetricCard("Profit Factor", "%.2f".format(res.profitFactor), isPositive = res.profitFactor >= 1.5, modifier = Modifier.weight(1f))
                            MetricCard("Recovery Factor", "%.2f".format(res.recoveryFactor), isPositive = res.recoveryFactor >= 1.0, modifier = Modifier.weight(1f))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("Win Rate", "${"%.1f".format(res.winRate)}%", isPositive = res.winRate >= 50.0, modifier = Modifier.weight(1f))
                            MetricCard("Max Drawdown", "${"%.2f".format(res.maxDrawdownPercent)}%", isPositive = res.maxDrawdownPercent <= 5.0, modifier = Modifier.weight(1f))
                            MetricCard("Avg R-Multiple", "${"%.2f".format(res.averageR)}R", isPositive = res.averageR > 0, modifier = Modifier.weight(1f))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("Total Executions", "${res.totalTrades} (${res.winningTrades}W / ${res.losingTrades}L)", modifier = Modifier.weight(1f))
                            MetricCard("Expectancy", "$${"%.2f".format(res.expectancy)}", isPositive = res.expectancy > 0, modifier = Modifier.weight(1f))
                            MetricCard("Consec Losses", "${res.maxConsecutiveLosses}", isPositive = res.maxConsecutiveLosses <= 3, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Simulated Trade Log Items
            if (res.trades.isNotEmpty()) {
                item {
                    Text("Executed Simulated Trades (${res.trades.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                }

                items(res.trades.take(15)) { trade ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardBorderDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(
                                    color = if (trade.direction == TradeDirection.BUY) EmeraldContainer else CrimsonContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        trade.direction.name,
                                        color = if (trade.direction == TradeDirection.BUY) EmeraldGain else CrimsonLoss,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Column {
                                    Text("${trade.symbol} • ${"%.2f".format(trade.volume)} lots", fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(
                                        "In: ${trade.entryPrice} -> Out: ${trade.closePrice ?: "-"} (${trade.closeReason?.name ?: "OPEN"})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${if (trade.profit >= 0) "+" else ""}$${"%.2f".format(trade.profit)}",
                                    fontWeight = FontWeight.Bold,
                                    color = if (trade.profit >= 0) EmeraldGain else CrimsonLoss
                                )
                                Text("${"%.2f".format(trade.profitR)}R", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }

        // Mode 1: Portfolio Multi-Asset Results
        if (testMode == 1 && portfolioResult != null) {
            val port = portfolioResult!!
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.fillMaxWidth().testTag("portfolio_results_card")
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Portfolio Combined Performance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("${port.symbols.size} Pairs Simulated (${port.totalTrades} total trades)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                            Surface(
                                color = if (port.totalProfitLoss >= 0) EmeraldContainer else CrimsonContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "${if (port.totalProfitLoss >= 0) "+" else ""}$${"%.2f".format(port.totalProfitLoss)}",
                                    color = if (port.totalProfitLoss >= 0) EmeraldGain else CrimsonLoss,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // Portfolio Equity Curve
                        if (port.equityCurve.size >= 2) {
                            SparklineChart(
                                dataPoints = port.equityCurve.map { it.second },
                                lineColor = if (port.totalProfitLoss >= 0) EmeraldGain else CrimsonLoss,
                                isPositive = port.totalProfitLoss >= 0,
                                modifier = Modifier.fillMaxWidth().height(120.dp)
                            )
                        }

                        // Portfolio Metrics
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("Portfolio Sharpe", "%.2f".format(port.sharpeRatio), isPositive = port.sharpeRatio >= 1.0, modifier = Modifier.weight(1f))
                            MetricCard("Profit Factor", "%.2f".format(port.profitFactor), isPositive = port.profitFactor >= 1.5, modifier = Modifier.weight(1f))
                            MetricCard("Win Rate", "${"%.1f".format(port.winRate)}%", isPositive = port.winRate >= 50.0, modifier = Modifier.weight(1f))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCard("Max Drawdown", "${"%.2f".format(port.maxDrawdownPercent)}%", isPositive = port.maxDrawdownPercent <= 8.0, modifier = Modifier.weight(1f))
                            MetricCard("Recovery Factor", "%.2f".format(port.recoveryFactor), isPositive = port.recoveryFactor >= 1.0, modifier = Modifier.weight(1f))
                            MetricCard("Total Trades", "${port.totalTrades}", modifier = Modifier.weight(1f))
                        }

                        Divider(color = CardBorderDark)

                        Text("Performance Breakdown by Asset", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)

                        port.tradesBySymbol.forEach { (symbol, trades) ->
                            val pnl = trades.sumOf { it.profit }
                            val winCount = trades.count { it.profit > 0 }
                            val rate = if (trades.isNotEmpty()) (winCount.toDouble() / trades.size) * 100.0 else 0.0

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(symbol, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text("(${trades.size} trades, ${"%.0f".format(rate)}% win)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                                Text(
                                    "${if (pnl >= 0) "+" else ""}$${"%.2f".format(pnl)}",
                                    fontWeight = FontWeight.Bold,
                                    color = if (pnl >= 0) EmeraldGain else CrimsonLoss
                                )
                            }
                        }
                    }
                }
            }
        }

        // Mode 2: Parameter Optimizer Results
        if (testMode == 2 && optimizationResults != null) {
            val list = optimizationResults!!
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.fillMaxWidth().testTag("optimizer_results_card")
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Parameter Optimization Leaderboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Ranked by Sharpe Ratio & Profit Factor for $selectedSymbol", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                        list.take(8).forEachIndexed { idx, opt ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, if (idx == 0) GoldHero else CardBorderDark),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Surface(
                                                color = if (idx == 0) GoldContainer else SurfaceDark,
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text("#${idx + 1}", fontWeight = FontWeight.Bold, color = if (idx == 0) GoldHero else TextSecondary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                            }
                                            Text("EMA ${opt.fastEma}/${opt.slowEma} • ATR ${opt.atrMultiplier}x • RR 1:${opt.rrRatio}", fontWeight = FontWeight.Bold, color = TextPrimary)
                                        }
                                        Text(
                                            "${if (opt.totalProfitLoss >= 0) "+" else ""}$${"%.2f".format(opt.totalProfitLoss)}",
                                            fontWeight = FontWeight.Bold,
                                            color = if (opt.totalProfitLoss >= 0) EmeraldGain else CrimsonLoss
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Sharpe: ${"%.2f".format(opt.sharpeRatio)}", style = MaterialTheme.typography.labelSmall, color = CyanLight)
                                        Text("PF: ${"%.2f".format(opt.profitFactor)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Text("Win: ${"%.1f".format(opt.winRate)}%", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Text("DD: ${"%.1f".format(opt.maxDrawdownPercent)}%", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Text("${opt.totalTrades} trades", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Mode 3: Walk-Forward Results
        if (testMode == 3 && walkForwardResult != null) {
            val wf = walkForwardResult!!
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.fillMaxWidth().testTag("walk_forward_results_card")
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Walk-Forward Cross-Validation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("Out-of-Sample Generalization Metric", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                            Surface(
                                color = if (wf.robustnessScore >= 70.0) EmeraldContainer else GoldContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "Score: ${"%.1f".format(wf.robustnessScore)}%",
                                    color = if (wf.robustnessScore >= 70.0) EmeraldGain else GoldHero,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // In-Sample vs Validation vs Out-of-Sample
                        listOf(
                            Triple("In-Sample (60% Training)", wf.inSampleResult, CyanLight),
                            Triple("Validation (20% Tuning)", wf.validationResult, GoldHero),
                            Triple("Out-of-Sample (20% Live Test)", wf.outOfSampleResult, EmeraldGain)
                        ).forEach { (label, res, color) ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CardBorderDark),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(label, fontWeight = FontWeight.Bold, color = color)
                                        Text(
                                            "${if (res.totalProfitLoss >= 0) "+" else ""}$${"%.2f".format(res.totalProfitLoss)}",
                                            fontWeight = FontWeight.Bold,
                                            color = if (res.totalProfitLoss >= 0) EmeraldGain else CrimsonLoss
                                        )
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Trades: ${res.totalTrades}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Text("PF: ${"%.2f".format(res.profitFactor)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Text("Win: ${"%.1f".format(res.winRate)}%", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Text("DD: ${"%.1f".format(res.maxDrawdownPercent)}%", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
