package com.example.ui.markets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.domain.indicators.IndicatorCalculator
import com.example.domain.model.Candle
import com.example.domain.model.Timeframe
import com.example.ui.components.InteractiveCandleChart
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MarketsScreen() {
    val engine = EdgeTraderApp.instance.tradingEngine
    val quotes by engine.activeQuotes.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedSymbol by remember { mutableStateOf("XAUUSD") }
    var selectedTimeframe by remember { mutableStateOf(Timeframe.M15) }
    var candlesList by remember { mutableStateOf<List<Candle>>(emptyList()) }
    var isLoadingCandles by remember { mutableStateOf(false) }

    val quote = quotes[selectedSymbol]

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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Symbol Selector Tabs (Gold / Bitcoin)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf("XAUUSD" to "Gold Spot", "BTCUSD" to "Bitcoin").forEach { (symbol, name) ->
                    val isSelected = selectedSymbol == symbol
                    Button(
                        onClick = { selectedSymbol = symbol },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) PrimaryBlueContainer else SurfaceDark
                        ),
                        border = BorderStroke(1.dp, if (isSelected) PrimaryBlue else CardBorderDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("market_tab_$symbol")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(symbol, fontWeight = FontWeight.Bold, color = if (isSelected) PrimaryBlue else TextSecondary)
                            Text(name, style = MaterialTheme.typography.labelSmall, color = if (isSelected) OnPrimaryBlueContainer else TextMuted)
                        }
                    }
                }
            }
        }

        // 2. Live Price Hero Summary Card
        item {
            val session = engine.getMarketSession(selectedSymbol)
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
                            text = if (session.isOpen) "Live Exchange Feed" else (session.timeRemainingString ?: "Weekend Close"),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = selectedSymbol,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = if (selectedSymbol == "XAUUSD") GoldHero else CyanLight
                            )
                            Text(
                                text = "Tick Size: ${if (selectedSymbol == "XAUUSD") "$0.01" else "$0.10"} | Timeframe: ${selectedTimeframe.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = quote?.ask?.let { "$%.2f".format(it) } ?: activeCandles.lastOrNull()?.close?.let { "$%.2f".format(it) } ?: "--",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = if (session.isOpen) "Spread: ${quote?.spread?.let { "$%.2f".format(it) } ?: "--"}" else "Friday Official Close",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (session.isOpen) EmeraldGain else GoldHero
                            )
                        }
                    }

                    if (!session.isOpen) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = SurfaceVariantDark,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CardBorderDark)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "ℹ️ Saturday/Sunday Weekend Session:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = session.details,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Real Interactive Financial Candlestick Chart
        item {
            InteractiveCandleChart(
                candles = activeCandles,
                currentQuote = quote,
                timeframe = selectedTimeframe,
                onTimeframeSelected = { tf ->
                    selectedTimeframe = tf
                },
                onRefresh = {
                    scope.launch {
                        isLoadingCandles = true
                        candlesList = engine.fetchHistoricalCandles(selectedSymbol, selectedTimeframe, 60)
                        isLoadingCandles = false
                    }
                },
                isLoading = isLoadingCandles
            )
        }

        // 4. Technical Indicators Panel
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Technical Indicator Telemetry", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Surface(
                            color = SurfaceVariantDark,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, CardBorderDark)
                        ) {
                            Text(
                                text = selectedTimeframe.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = PrimaryBlue,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark), modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("EMA (Fast 20)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Text(indicators?.emaFast?.let { "%.2f".format(it) } ?: "--", fontWeight = FontWeight.Bold, color = CyanLight, fontFamily = FontFamily.Monospace)
                            }
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark), modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("EMA (Slow 50)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Text(indicators?.emaSlow?.let { "%.2f".format(it) } ?: "--", fontWeight = FontWeight.Bold, color = GoldHero, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark), modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("ADX (14 Trend)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                val adx = indicators?.adx ?: 0.0
                                Text(
                                    text = if (indicators != null) "%.1f".format(adx) else "--",
                                    fontWeight = FontWeight.Bold,
                                    color = if (adx >= 25.0) EmeraldGain else StatusWarning,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(if (adx >= 25.0) "Trending (>=25)" else "Choppy (<25)", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark), modifier = Modifier.weight(1f)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("ATR (14 Volatility)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Text(indicators?.atr?.let { "%.2f".format(it) } ?: "--", fontWeight = FontWeight.Bold, color = TextPrimary, fontFamily = FontFamily.Monospace)
                                Text("Stop Multiplier: 1.5x", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }
                        }
                    }
                }
            }
        }

        // 5. Broker Specifications
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Symbol Contract Specifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("• Minimum Lot: 0.01 | Max Lot: 10.00 | Lot Step: 0.01", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("• Contract Size: ${if (selectedSymbol == "XAUUSD") "100 oz" else "1.00 BTC"}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("• Maximum Spread Threshold: ${if (selectedSymbol == "XAUUSD") "$0.60" else "$15.00"}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("• Minimum Stop Distance: ${if (selectedSymbol == "XAUUSD") "$0.50" else "$25.00"}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
    }
}
