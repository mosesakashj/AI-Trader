package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.broker.MarketScheduleUtils
import com.example.data.api.MarketInsightsRepository
import com.example.domain.model.*
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToMarkets: () -> Unit,
    onNavigateToPositions: () -> Unit,
    onNavigateToStrategy: () -> Unit
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val stateMachineState by viewModel.stateMachineState.collectAsStateWithLifecycle()
    val stateReason by viewModel.stateReason.collectAsStateWithLifecycle()
    val accountInfo by viewModel.accountInfo.collectAsStateWithLifecycle()
    val quotes by viewModel.activeQuotes.collectAsStateWithLifecycle()
    val latestSignal by viewModel.latestSignal.collectAsStateWithLifecycle()
    val dailyPnl by viewModel.dailyPnl.collectAsStateWithLifecycle()
    val openPositions by viewModel.openPositions.collectAsStateWithLifecycle()

    val insightsRepo = remember { MarketInsightsRepository() }
    val trending by insightsRepo.trending.collectAsState()
    val news by insightsRepo.news.collectAsState()
    val economicEvents by insightsRepo.economicEvents.collectAsState()
    val sentiment by insightsRepo.sentiment.collectAsState()
    LaunchedEffect(Unit) { insightsRepo.refreshAll() }

    val allTrades by viewModel.recentTrades.collectAsStateWithLifecycle()
    val perfStats = remember(allTrades) { computePerformanceStats(allTrades) }

    var showEmergencyDialog by remember { mutableStateOf(false) }
    var showCloseAllDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("ALL") }

    val mode = runCatching { TradingMode.valueOf(config?.mode ?: "PAPER") }.getOrDefault(TradingMode.PAPER)
    val isBotRunning = config?.isBotEnabled == true && config?.emergencyStop == false

    val filteredSymbols = remember(selectedCategory) {
        when (selectedCategory) {
            "CRYPTO" -> SymbolCatalog.getByAssetType(AssetType.CRYPTO)
            "FOREX" -> SymbolCatalog.getByAssetType(AssetType.FOREX)
            "COMMODITIES" -> SymbolCatalog.getByAssetType(AssetType.COMMODITY)
            else -> SymbolCatalog.ALL_SYMBOLS
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Status & Control Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth().testTag("dashboard_status_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPulseIndicator(state = stateMachineState)
                            ModeBadge(mode = mode)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isBotRunning) "ACTIVE" else "OFF",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isBotRunning) EmeraldGain else TextMuted,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Switch(
                                checked = isBotRunning,
                                onCheckedChange = { viewModel.toggleTradingBot(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = EmeraldGain,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = SurfaceVariantDark
                                ),
                                modifier = Modifier.testTag("engine_toggle_switch")
                            )
                        }
                    }

                    if (stateReason.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "State: $stateReason",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { showEmergencyDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonLoss),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("dashboard_emergency_stop_btn")
                    ) {
                        Icon(Icons.Default.Dangerous, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("EMERGENCY STOP (HALT ALL)", fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
            }
        }

        // Safe Mode Alert Banner if active
        if (config?.safeMode == true || stateMachineState == StateMachineState.SAFE_MODE) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CrimsonContainer),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CrimsonLoss.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("safe_mode_banner")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonLoss)
                            Text(
                                text = "SAFE MODE ACTIVE",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = CrimsonLoss
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = config?.safeModeReason ?: "Automated safety circuit tripped.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.resetSafeMode() },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonLoss),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("reset_safe_mode_btn")
                        ) {
                            Text("Reset Safe Mode", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // 2. Account & Financial Overview Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Account Capital & Risk",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard(
                        title = "Equity",
                        value = "$${"%.2f".format(accountInfo.equity)}",
                        subtitle = "Balance: $${"%.2f".format(accountInfo.balance)}",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Free Margin",
                        value = "$${"%.2f".format(accountInfo.freeMargin)}",
                        subtitle = "Margin: $${"%.2f".format(accountInfo.margin)}",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val pnl = dailyPnl
                    val isPositive = pnl >= 0
                    MetricCard(
                        title = "Today P/L",
                        value = "${if (isPositive) "+" else ""}$${"%.2f".format(pnl)}",
                        subtitle = if (isPositive) "Profitable session" else "Within daily risk limits",
                        valueColor = if (isPositive) EmeraldGain else CrimsonLoss,
                        isPositive = isPositive,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Active Risk",
                        value = "${config?.defaultRiskPercent ?: 0.25}%",
                        subtitle = "Max Daily Loss: ${config?.maxDailyLossPercent ?: 1.0}%",
                        valueColor = GoldHero,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. Multi-Pair Live Watchlist
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Multi-Pair Watchlist (${filteredSymbols.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    TextButton(onClick = onNavigateToMarkets) {
                        Text("Interactive Charts", color = CyanLight, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("ALL" to "All Pairs", "CRYPTO" to "Crypto (3)", "FOREX" to "Forex (3)", "COMMODITIES" to "Metals & Oil (2)").forEach { (cat, label) ->
                        val isSelected = selectedCategory == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = cat },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryBlueContainer,
                                selectedLabelColor = PrimaryBlue,
                                containerColor = SurfaceDark,
                                labelColor = TextSecondary
                            ),
                            border = BorderStroke(1.dp, if (isSelected) PrimaryBlue else CardBorderDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("filter_$cat")
                        )
                    }
                }
            }
        }

        // Watchlist Items
        items(filteredSymbols) { symConfig ->
            val quote = quotes[symConfig.symbol]
            val session = MarketScheduleUtils.getMarketSession(symConfig.symbol)
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToMarkets() }
                    .testTag("quote_card_${symConfig.symbol}")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        symConfig.symbol,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = when (symConfig.assetType) {
                                            AssetType.CRYPTO -> CyanLight
                                            AssetType.COMMODITY -> GoldHero
                                            AssetType.FOREX -> EmeraldGain
                                            else -> TextPrimary
                                        }
                                    )
                                    Surface(
                                        color = if (session.isOpen) EmeraldContainer else GoldContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            session.statusLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (session.isOpen) EmeraldDark else GoldHero,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${symConfig.displayName} • Spread: ${quote?.spread?.let { SymbolCatalog.formatPrice(symConfig.symbol, it) } ?: "--"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            val askVal = quote?.ask ?: SymbolCatalog.getInitialQuote(symConfig.symbol).ask
                            val bidVal = quote?.bid ?: SymbolCatalog.getInitialQuote(symConfig.symbol).bid
                            Text(
                                text = SymbolCatalog.formatPrice(symConfig.symbol, askVal),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Bid: ${SymbolCatalog.formatPrice(symConfig.symbol, bidVal)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // 4. Open Positions Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Auto-Managed Positions (${openPositions.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (openPositions.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showCloseAllDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonLoss),
                            border = BorderStroke(1.dp, CrimsonLoss),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("close_all_quick_btn")
                        ) {
                            Text("CLOSE ALL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (openPositions.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CardBorderDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Inbox, contentDescription = null, tint = TextMuted, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No open positions", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                                Text("Engine is scanning closed M15 candles across all 8 pairs", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                } else {
                    openPositions.forEach { pos ->
                        val isProfit = pos.unrealizedProfit >= 0
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, if (isProfit) EmeraldGain.copy(alpha = 0.5f) else CrimsonLoss.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().testTag("position_card_${pos.id}")
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
                                        Text(pos.symbol, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text("${pos.volume} lots", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${if (isProfit) "+" else ""}$${"%.2f".format(pos.unrealizedProfit)}",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isProfit) EmeraldGain else CrimsonLoss,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "${if (isProfit) "+" else ""}${"%.2f".format(pos.unrealizedR)}R",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (pos.unrealizedR >= 1.0) {
                                        Surface(color = EmeraldContainer, shape = RoundedCornerShape(4.dp)) {
                                            Text("Break-Even Secured", style = MaterialTheme.typography.labelSmall, color = EmeraldDark, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    if (pos.unrealizedR >= 1.5) {
                                        Surface(color = CyanContainer, shape = RoundedCornerShape(4.dp)) {
                                            Text("Trailing Active", style = MaterialTheme.typography.labelSmall, color = CyanLight, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                }

                                HorizontalDivider(color = CardBorderDark, modifier = Modifier.padding(vertical = 10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Entry: ${SymbolCatalog.formatPrice(pos.symbol, pos.entryPrice)}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                        Text("SL: ${SymbolCatalog.formatPrice(pos.symbol, pos.stopLoss)} • TP: ${SymbolCatalog.formatPrice(pos.symbol, pos.takeProfit)}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    }

                                    Button(
                                        onClick = { viewModel.closeSinglePosition(pos.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("close_pos_btn_${pos.id}")
                                    ) {
                                        Text("Close", color = CrimsonLoss, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Live Explainability Signal Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth().testTag("dashboard_signal_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Latest Strategy Audit",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        TextButton(onClick = onNavigateToStrategy) {
                            Text("Tuner", color = CyanLight, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    val sig = latestSignal
                    if (sig != null) {
                        val exp = sig.explanation
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Evaluated ${sig.symbol} (${sig.direction}): ${sig.decision}",
                            fontWeight = FontWeight.Bold,
                            color = if (sig.decision == SignalDecision.GO) EmeraldGain else GoldHero
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = sig.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        if (exp != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                FactorChip("EMA Trend Alignment", exp.trendCheck, Modifier.fillMaxWidth())
                                FactorChip("ADX Momentum (>= ${config?.adxThreshold})", exp.adxCheck, Modifier.fillMaxWidth())
                                FactorChip("Pullback to EMA Band", exp.pullbackCheck, Modifier.fillMaxWidth())
                                FactorChip("Closed Bar Confirmation", exp.candleCheck, Modifier.fillMaxWidth())
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Waiting for closed M15 candle formation...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        // 6. Market Sentiment
        item {
            MarketSentimentBar(sentiment = sentiment)
        }

        // 7. Trending Movers
        item {
            TrendingMoversCard(trending = trending)
        }

        // 8. News Feed
        item {
            NewsFeedCard(articles = news)
        }

        // 9. Economic Calendar
        item {
            EconomicCalendarCard(events = economicEvents)
        }

        // 10. Performance Dashboard
        item {
            PerformanceDashboard(stats = perfStats)
        }
    }

    // Emergency Confirmation Dialog
    if (showEmergencyDialog) {
        AlertDialog(
            onDismissRequest = { showEmergencyDialog = false },
            title = { Text("Activate Emergency Stop?", fontWeight = FontWeight.Bold, color = CrimsonLoss) },
            text = { Text("This will immediately disable automated trading and protect open balance.", color = TextPrimary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.triggerEmergencyStop()
                        showEmergencyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonLoss)
                ) {
                    Text("YES, EMERGENCY STOP", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEmergencyDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // Close All Confirmation Dialog
    if (showCloseAllDialog) {
        AlertDialog(
            onDismissRequest = { showCloseAllDialog = false },
            title = { Text("Close All Positions?", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text("This will instantly send market orders to close all open positions across all pairs.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.closeAllPositions()
                        showCloseAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonLoss)
                ) {
                    Text("Close All", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCloseAllDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }
}
