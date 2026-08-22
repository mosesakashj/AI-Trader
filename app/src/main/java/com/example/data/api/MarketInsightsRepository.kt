package com.example.data.api

import com.example.domain.model.Candle
import com.example.domain.model.Quote
import com.example.domain.model.SymbolCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MarketInsightsRepository {

    private val apiService = NewsApiService()

    private val _news = MutableStateFlow<List<NewsArticle>>(emptyList())
    val news: StateFlow<List<NewsArticle>> = _news.asStateFlow()

    private val _trending = MutableStateFlow<List<TrendingSymbol>>(emptyList())
    val trending: StateFlow<List<TrendingSymbol>> = _trending.asStateFlow()

    private val _economicEvents = MutableStateFlow<List<EconomicEvent>>(emptyList())
    val economicEvents: StateFlow<List<EconomicEvent>> = _economicEvents.asStateFlow()

    private val _sentiment = MutableStateFlow(MarketSentiment())
    val sentiment: StateFlow<MarketSentiment> = _sentiment.asStateFlow()

    private val _expectedMoves = MutableStateFlow<Map<String, ExpectedMove>>(emptyMap())
    val expectedMoves: StateFlow<Map<String, ExpectedMove>> = _expectedMoves.asStateFlow()

    suspend fun refreshAll(activeSymbols: List<String> = emptyList()) {
        try {
            _news.value = apiService.getMarketNews(activeSymbols)
            _trending.value = apiService.getTrendingSymbols()
            _economicEvents.value = apiService.getEconomicCalendar()
            _sentiment.value = apiService.getMarketSentiment()
        } catch (_: Exception) {
            // Use cached/default data
        }
    }

    fun computeExpectedMoves(
        symbol: String,
        quote: Quote?,
        candles: List<Candle>
    ): ExpectedMove {
        val price = quote?.ask ?: SymbolCatalog.getInitialQuote(symbol).ask
        val config = SymbolCatalog.get(symbol)

        val recentCandles = candles.takeLast(14)
        if (recentCandles.size < 14) {
            return ExpectedMove(
                symbol = symbol,
                currentPrice = price,
                atr14 = 0.0,
                expectedDailyRange = 0.0,
                expectedWeeklyRange = 0.0,
                upperBand = price,
                lowerBand = price,
                volatilityPercentile = 50.0,
                volatilityLabel = "Insufficient Data"
            )
        }

        val trValues = recentCandles.map { candle ->
            val hl = candle.high - candle.low
            val hc = kotlin.math.abs(candle.high - candle.close)
            val lc = kotlin.math.abs(candle.low - candle.close)
            maxOf(hl, hc, lc)
        }
        val atr14 = trValues.average()

        val dailyRange = atr14
        val weeklyRange = atr14 * kotlin.math.sqrt(5.0)
        val upperBand = price + atr14 * 1.5
        val lowerBand = price - atr14 * 1.5

        val allRanges = trValues.sorted()
        val rank = allRanges.indexOfFirst { it >= atr14 }
        val percentile = if (allRanges.isNotEmpty()) (rank.toDouble() / allRanges.size * 100) else 50.0

        val volatilityLabel = when {
            percentile >= 90 -> "Extremely High"
            percentile >= 75 -> "High"
            percentile >= 50 -> "Normal"
            percentile >= 25 -> "Low"
            else -> "Extremely Low"
        }

        return ExpectedMove(
            symbol = symbol,
            currentPrice = price,
            atr14 = atr14,
            expectedDailyRange = dailyRange,
            expectedWeeklyRange = weeklyRange,
            upperBand = upperBand,
            lowerBand = lowerBand,
            volatilityPercentile = percentile,
            volatilityLabel = volatilityLabel
        )
    }

    fun computeSupportResistance(candles: List<Candle>): Pair<List<Double>, List<Double>> {
        if (candles.size < 20) return Pair(emptyList(), emptyList())

        val recentCandles = candles.takeLast(50)
        val highs = recentCandles.map { it.high }.sorted()
        val lows = recentCandles.map { it.low }.sorted()

        val currentPrice = recentCandles.last().close
        val resistance = highs.filter { it > currentPrice }.distinct().take(3)
        val support = lows.filter { it < currentPrice }.distinct().takeLast(3).reversed()

        return Pair(support, resistance)
    }
}
