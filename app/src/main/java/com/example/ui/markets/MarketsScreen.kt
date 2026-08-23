package com.example.ui.markets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.EdgeTraderApp
import com.example.ai.AiMarketIntelligence
import com.example.ai.AiInstitutionalTradePlan
import com.example.domain.indicators.IndicatorCalculator
import com.example.domain.model.AssetType
import com.example.domain.model.Candle
import com.example.domain.model.SymbolCatalog
import com.example.domain.model.Timeframe
import com.example.data.api.MarketInsightsRepository
import com.example.ui.components.ExpectedMovementCard
import com.example.ui.components.InteractiveCandleChart
import com.example.ui.theme.*

@Composable
fun MarketsScreen() {
    val engine = EdgeTraderApp.instance.tradingEngine
    val aiManager = EdgeTraderApp.instance.aiManager
    val quotes by engine.activeQuotes.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var selectedCategory by remember { mutableStateOf("ALL") }
    var selectedSymbol by remember { mutableStateOf("XAUUSD") }
    var selectedTimeframe by remember { mutableStateOf(Timeframe.M15) }
    var candlesList by remember { mutableStateOf<List<Candle>>(emptyList()) }
    var isLoadingCandles by remember { mutableStateOf(false) }

    var aiMarketIntel by remember { mutableStateOf<AiMarketIntelligence?>(null) }
    var isAnalyzingMarket by remember { mutableStateOf(false) }

    var aiTradePlan by remember { mutableStateOf<AiInstitutionalTradePlan?>(null) }
    var isGeneratingPlan by remember { mutableStateOf(false) }
    var planDeployMessage by remember { mutableStateOf<String?>(null) }
    var isDeployingPlan by remember { mutableStateOf(false) }

    val quote = quotes[selectedSymbol]

    val filteredSymbols = remember(selectedCategory) {
        when (selectedCategory) {
            "CRYPTO" -> SymbolCatalog.getByAssetType(AssetType.CRYPTO)
            "FOREX" -> SymbolCatalog.getByAssetType(AssetType.FOREX)
            "COMMODITIES" -> SymbolCatalog.getByAssetType(AssetType.COMMODITY)
            else -> SymbolCatalog.ALL_SYMBOLS
        }
    }

    // Fetch real live candles whenever symbol or timeframe changes
    LaunchedEffect(selectedSymbol, selectedTimeframe) {
        isLoadingCandles = true
        candlesList = engine.fetchHistoricalCandles(selectedSymbol, selectedTimeframe, 60)
        isLoadingCandles = false
    }

    // Dynamic live tick update on the active candle
    val activeCandles = remember(candlesList, quote) {
        if (candlesList.isEmpty() || quote == null) {
            candlesList
        } else {
            val list = candlesList.toMutableList()
            val last = list.last()
            val updatedLast = last.copy(
                close = quote.ask,
                high = maxOf(last.high, quote.ask),
                low = minOf(last.low, quote.ask)
            )
            list[list.lastIndex] = updatedLast
            list
        }
    }

    val indicators = remember(activeCandles) {
        if (activeCandles.size >= 30) {
            IndicatorCalculator.computeLatest(activeCandles)
        } else null
    }

    val insightsRepo = remember { MarketInsightsRepository() }

    val expectedMove = remember(activeCandles, selectedSymbol) {
        insightsRepo.computeExpectedMoves(selectedSymbol, quote, activeCandles)
    }

    val (supports, resistances) = remember(activeCandles) {
        insightsRepo.computeSupportResistance(activeCandles)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Symbol Filter Chips and Ticker Selector
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Category Filter
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("ALL" to "All Pairs (${SymbolCatalog.ALL_SYMBOLS.size})", "CRYPTO" to "Crypto (${SymbolCatalog.getByAssetType(AssetType.CRYPTO).size})", "FOREX" to "Forex (${SymbolCatalog.getByAssetType(AssetType.FOREX).size})", "COMMODITIES" to "Commodities (${SymbolCatalog.getByAssetType(AssetType.COMMODITY).size})").forEach { (cat, title) ->
                        val isSelected = selectedCategory == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = cat },
                            label = { Text(title) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryBlueContainer,
                                selectedLabelColor = PrimaryBlue,
                                containerColor = SurfaceDark,
                                labelColor = TextSecondary
                            ),
                            border = BorderStroke(1.dp, if (isSelected) PrimaryBlue else CardBorderDark),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                // Symbols Horizontal Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredSymbols.forEach { cfg ->
                        val isSelected = selectedSymbol == cfg.symbol
                        Button(
                            onClick = { selectedSymbol = cfg.symbol },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) PrimaryBlueContainer else SurfaceDark
                            ),
                            border = BorderStroke(1.dp, if (isSelected) PrimaryBlue else CardBorderDark),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(48.dp).testTag("market_tab_${cfg.symbol}")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(cfg.symbol, fontWeight = FontWeight.Bold, color = if (isSelected) PrimaryBlue else TextPrimary)
                                Text(cfg.displayName, style = MaterialTheme.typography.labelSmall, color = if (isSelected) OnPrimaryBlueContainer else TextMuted)
                            }
                        }
                    }
                }
            }
        }

        // 2. Live Price Hero Summary Card
        item {
            val session = engine.getMarketSession(selectedSymbol)
            val symConfig = SymbolCatalog.get(selectedSymbol)
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth().testTag("market_hero_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Session Status Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = if (session.isOpen) EmeraldContainer else GoldContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = if (session.isOpen) EmeraldGain else GoldHero,
                                    modifier = Modifier.size(8.dp)
                                ) {}
                                Text(
                                    text = session.statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (session.isOpen) EmeraldDark else GoldHero
                                )
                            }
                        }

                        Text(
                            text = "${symConfig.assetType.name} • ${selectedTimeframe.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Price Layout
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "${symConfig.displayName} (${selectedSymbol})",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                            val askVal = quote?.ask ?: SymbolCatalog.getInitialQuote(selectedSymbol).ask
                            Text(
                                text = SymbolCatalog.formatPrice(selectedSymbol, askVal),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            val bidVal = quote?.bid ?: SymbolCatalog.getInitialQuote(selectedSymbol).bid
                            val spreadVal = quote?.spread ?: (symConfig.spreadLimit / 2.0)
                            Text(
                                text = "Bid: ${SymbolCatalog.formatPrice(selectedSymbol, bidVal)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Spread: ${SymbolCatalog.formatPrice(selectedSymbol, spreadVal)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }

        // 3. Timeframe Bar
        item {
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
                            containerColor = SurfaceDark,
                            labelColor = TextSecondary
                        ),
                        border = BorderStroke(1.dp, if (isSelected) PrimaryBlue else CardBorderDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("timeframe_${tf.label}")
                    )
                }
            }
        }

        // 4. Interactive Candlestick Chart View
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth().testTag("candlestick_chart_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Real-Time Candlestick & EMA Band",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(shape = RoundedCornerShape(100.dp), color = CyanLight, modifier = Modifier.size(8.dp)) {}
                                Text("EMA 20", style = MaterialTheme.typography.labelSmall, color = CyanLight)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(shape = RoundedCornerShape(100.dp), color = GoldHero, modifier = Modifier.size(8.dp)) {}
                                Text("EMA 50", style = MaterialTheme.typography.labelSmall, color = GoldHero)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isLoadingCandles) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(260.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = PrimaryBlue)
                        }
                    } else if (activeCandles.isNotEmpty()) {
                        InteractiveCandleChart(
                            candles = activeCandles,
                            modifier = Modifier.fillMaxWidth().height(260.dp)
                        )
                    }
                }
            }
        }

        // AI Market Intelligence & Liquidity Radar Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CyanLight.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().testTag("ai_market_intelligence_card")
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Science, contentDescription = null, tint = CyanLight, modifier = Modifier.size(20.dp))
                            Text("AI Institutional Market Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }

                        if (aiMarketIntel != null) {
                            Surface(
                                color = EmeraldContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "Confluence: ${aiMarketIntel!!.confluenceScore}%",
                                    fontWeight = FontWeight.Black,
                                    color = EmeraldGain,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    if (aiMarketIntel == null && !isAnalyzingMarket) {
                        Text(
                            "Synthesizes multi-timeframe order flow, liquidity pools, order blocks, and session dynamics on $selectedSymbol ($selectedTimeframe).",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Button(
                            onClick = {
                                isAnalyzingMarket = true
                                coroutineScope.launch {
                                    val res = aiManager.generateMarketIntelligence(selectedSymbol, selectedTimeframe.label, quote, activeCandles)
                                    aiMarketIntel = res.getOrNull()
                                    isAnalyzingMarket = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("run_ai_market_analysis_btn")
                        ) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RUN AI MARKET STRUCTURE ANALYSIS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else if (isAnalyzingMarket) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = CyanLight, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Mapping Liquidity Pools & Order Blocks...", style = MaterialTheme.typography.bodySmall, color = CyanLight)
                        }
                    } else if (aiMarketIntel != null) {
                        val intel = aiMarketIntel!!

                        Surface(
                            color = SurfaceVariantDark,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Regime: ${intel.marketRegime}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = GoldHero)
                                    Text("Bias: ${intel.bias}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = if (intel.bias.contains("BULLISH")) EmeraldGain else CrimsonLoss)
                                }
                                Text(intel.institutionalSummary, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            }
                        }

                        // Order Block & Liquidity
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Key Order Block Zone:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = CyanLight)
                            Surface(color = SurfaceVariantDark, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(intel.orderBlockZone, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, color = TextSecondary, fontFamily = FontFamily.Monospace)
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Resting Liquidity Pools:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextMuted)
                            intel.liquidityPools.forEach { pool ->
                                Text("• $pool", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }

                        Surface(
                            color = PrimaryBlueContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Actionable Tactical Guidance:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                                Text(intel.actionableGuidance, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                isAnalyzingMarket = true
                                coroutineScope.launch {
                                    val res = aiManager.generateMarketIntelligence(selectedSymbol, selectedTimeframe.label, quote, activeCandles)
                                    aiMarketIntel = res.getOrNull()
                                    isAnalyzingMarket = false
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Refresh Market Intelligence", fontSize = 12.sp, color = CyanLight)
                        }
                    }
                }
            }
        }

        // AI Institutional Trade Plan Generator & 1-Tap Deployment Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, GoldHero.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().testTag("ai_trade_plan_card")
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = GoldHero, modifier = Modifier.size(20.dp))
                            Text("Institutional AI Trade Plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }

                        if (aiTradePlan != null) {
                            Surface(
                                color = if (aiTradePlan!!.direction == com.example.domain.model.TradeDirection.BUY) EmeraldContainer else CrimsonContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "${aiTradePlan!!.direction.name} | ${aiTradePlan!!.riskRewardRatio}R",
                                    fontWeight = FontWeight.Black,
                                    color = if (aiTradePlan!!.direction == com.example.domain.model.TradeDirection.BUY) EmeraldGain else CrimsonLoss,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    if (planDeployMessage != null) {
                        Surface(
                            color = EmeraldContainer.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.TrendingUp, contentDescription = null, tint = EmeraldGain, modifier = Modifier.size(18.dp))
                                Text(planDeployMessage!!, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }
                    }

                    if (aiTradePlan == null && !isGeneratingPlan) {
                        Text(
                            "AI analyzes current order flow to build an asymmetric Trade Plan with exact Entry, Protective Invalidation SL, TP1, TP2 Runner, and automatic lot sizing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Button(
                            onClick = {
                                isGeneratingPlan = true
                                planDeployMessage = null
                                coroutineScope.launch {
                                    val res = aiManager.generateInstitutionalTradePlan(
                                        symbol = selectedSymbol,
                                        timeframe = selectedTimeframe.label,
                                        quote = quote,
                                        candles = activeCandles
                                    )
                                    aiTradePlan = res.getOrNull()
                                    isGeneratingPlan = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldHero),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("build_ai_trade_plan_btn")
                        ) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("GENERATE AI TRADE PLAN FOR $selectedSymbol", fontWeight = FontWeight.Black, color = Color.Black, fontSize = 12.sp)
                        }
                    } else if (isGeneratingPlan) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = GoldHero, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Calculating Optimal R:R Brackets & Risk Sizing...", style = MaterialTheme.typography.bodySmall, color = GoldHero)
                        }
                    } else if (aiTradePlan != null) {
                        val plan = aiTradePlan!!

                        // Key Price Matrix
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(color = SurfaceVariantDark, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("ENTRY", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                    Text(SymbolCatalog.formatPrice(selectedSymbol, plan.entryPrice), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                }
                            }
                            Surface(color = SurfaceVariantDark, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("STOP LOSS", fontSize = 10.sp, color = CrimsonLoss, fontWeight = FontWeight.Bold)
                                    Text(SymbolCatalog.formatPrice(selectedSymbol, plan.stopLoss), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CrimsonLoss)
                                }
                            }
                            Surface(color = SurfaceVariantDark, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("TAKE PROFIT", fontSize = 10.sp, color = EmeraldGain, fontWeight = FontWeight.Bold)
                                    Text(SymbolCatalog.formatPrice(selectedSymbol, plan.takeProfit1), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EmeraldGain)
                                }
                            }
                        }

                        // Sizing & BE Trigger
                        Surface(color = SurfaceVariantDark, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Suggested Volume: ${plan.recommendedLotSize} lots ($${"%.2f".format(plan.maxRiskAmountUsd)} risk)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("BE Trigger: ${SymbolCatalog.formatPrice(selectedSymbol, plan.breakEvenTrigger)}", style = MaterialTheme.typography.labelSmall, color = CyanLight, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Execution Phases
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Execution Strategy:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextMuted)
                            plan.executionPhases.forEach { phase ->
                                Text("• $phase", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }

                        // Invalidation rule
                        Surface(color = CrimsonContainer.copy(alpha = 0.3f), shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Text("Invalidation: ${plan.invalidationCondition}", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall, color = CrimsonLoss)
                        }

                        // 1-Tap Deploy Button
                        Button(
                            onClick = {
                                isDeployingPlan = true
                                coroutineScope.launch {
                                    val execRes = engine.executeAiTradePlan(plan)
                                    if (execRes.isSuccess) {
                                        planDeployMessage = execRes.getOrNull()
                                    } else {
                                        planDeployMessage = "Execution notice: ${execRes.exceptionOrNull()?.message}"
                                    }
                                    isDeployingPlan = false
                                }
                            },
                            enabled = !isDeployingPlan,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGain),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("deploy_ai_trade_plan_btn")
                        ) {
                            if (isDeployingPlan) {
                                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("DEPLOY AI TRADE PLAN TO BOT ENGINE", fontWeight = FontWeight.Black, color = Color.Black, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // 5. Technical Indicators Readout
        if (indicators != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.fillMaxWidth().testTag("indicator_readout_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Algorithmic Indicators (${selectedSymbol})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("ADX (14)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    Text(
                                        "%.1f".format(indicators.adx),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (indicators.adx >= 25.0) EmeraldGain else TextSecondary
                                    )
                                    Text(if (indicators.adx >= 25.0) "Strong Trend" else "Ranging", style = MaterialTheme.typography.labelSmall, color = if (indicators.adx >= 25.0) EmeraldGain else TextMuted)
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("ATR (14)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    Text(
                                        SymbolCatalog.formatPrice(selectedSymbol, indicators.atr),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text("Volatility Filter", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("EMA Bias", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    val isBullish = indicators.emaFast > indicators.emaSlow
                                    Text(
                                        if (isBullish) "BULLISH" else "BEARISH",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBullish) EmeraldGain else CrimsonLoss
                                    )
                                    Text("20 > 50 EMA", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Expected Movement Card
        if (expectedMove != null) {
            item {
                ExpectedMovementCard(expectedMove = expectedMove!!)
            }
        }

        // 7. Support & Resistance Levels
        if (supports.isNotEmpty() || resistances.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.fillMaxWidth().testTag("support_resistance_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Support & Resistance Levels",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        val currentPrice = quote?.ask ?: SymbolCatalog.getInitialQuote(selectedSymbol).ask

                        // Current Price Marker
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Current Price", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(
                                    SymbolCatalog.formatPrice(selectedSymbol, currentPrice),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Resistance Levels (from highest to lowest)
                        if (resistances.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Resistance", style = MaterialTheme.typography.labelSmall, color = CrimsonLoss, fontWeight = FontWeight.Bold)
                                resistances.sortedDescending().forEach { level ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(100.dp),
                                                color = CrimsonLoss.copy(alpha = 0.15f),
                                                modifier = Modifier.size(10.dp)
                                            ) {}
                                            Text(
                                                SymbolCatalog.formatPrice(selectedSymbol, level),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = CrimsonLoss,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Support Levels (from highest to lowest)
                        if (supports.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Support", style = MaterialTheme.typography.labelSmall, color = EmeraldGain, fontWeight = FontWeight.Bold)
                                supports.sortedDescending().forEach { level ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(100.dp),
                                                color = EmeraldGain.copy(alpha = 0.15f),
                                                modifier = Modifier.size(10.dp)
                                            ) {}
                                            Text(
                                                SymbolCatalog.formatPrice(selectedSymbol, level),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = EmeraldGain,
                                                fontFamily = FontFamily.Monospace
                                            )
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
}
