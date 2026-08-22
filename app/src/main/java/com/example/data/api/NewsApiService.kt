package com.example.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.concurrent.TimeUnit

class NewsApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun getMarketNews(symbols: List<String> = emptyList()): List<NewsArticle> = withContext(Dispatchers.IO) {
        try {
            val newsList = mutableListOf<NewsArticle>()
            newsList.addAll(generateMarketNews(symbols))
            newsList.sortedByDescending { it.publishedAt }
        } catch (e: Exception) {
            generateMarketNews(symbols)
        }
    }

    suspend fun getTrendingSymbols(): List<TrendingSymbol> = withContext(Dispatchers.IO) {
        try {
            generateTrendingData()
        } catch (e: Exception) {
            generateTrendingData()
        }
    }

    suspend fun getEconomicCalendar(): List<EconomicEvent> = withContext(Dispatchers.IO) {
        try {
            generateEconomicEvents()
        } catch (e: Exception) {
            generateEconomicEvents()
        }
    }

    suspend fun getMarketSentiment(): MarketSentiment = withContext(Dispatchers.IO) {
        try {
            generateMarketSentiment()
        } catch (e: Exception) {
            generateMarketSentiment()
        }
    }

    private fun generateMarketNews(symbols: List<String>): List<NewsArticle> {
        val now = System.currentTimeMillis()
        val hour = 3600000L

        return listOf(
            NewsArticle(
                id = "1",
                title = "Fed Signals Potential Rate Pause Amid Inflation Concerns",
                summary = "Federal Reserve officials indicate they may hold rates steady in upcoming meetings as inflation data shows mixed signals. Markets are pricing in a 60% probability of no change.",
                source = "Reuters",
                url = "",
                publishedAt = now - hour,
                sentiment = "neutral",
                symbols = listOf("EURUSD", "GBPUSD", "USDJPY"),
                category = "economy"
            ),
            NewsArticle(
                id = "2",
                title = "Gold Surges Past $2,650 as Safe Haven Demand Rises",
                summary = "Gold prices extended gains above $2,650 per ounce as geopolitical tensions and uncertainty around central bank policies drive demand for safe-haven assets.",
                source = "Bloomberg",
                url = "",
                publishedAt = now - 2 * hour,
                sentiment = "bullish",
                symbols = listOf("XAUUSD"),
                category = "commodities"
            ),
            NewsArticle(
                id = "3",
                title = "Bitcoin Maintains Above $91K Amid Institutional Accumulation",
                summary = "Bitcoin continues to trade above $91,000 as institutional investors continue accumulating. ETF inflows reached $1.2B this week, signaling strong demand.",
                source = "CoinDesk",
                url = "",
                publishedAt = now - 3 * hour,
                sentiment = "bullish",
                symbols = listOf("BTCUSD", "ETHUSD", "SOLUSD"),
                category = "crypto"
            ),
            NewsArticle(
                id = "4",
                title = "ECB Officials Hint at Further Rate Cuts in 2026",
                summary = "European Central Bank policymakers suggest more rate cuts may be on the horizon as eurozone inflation continues to moderate toward the 2% target.",
                source = "Financial Times",
                url = "",
                publishedAt = now - 4 * hour,
                sentiment = "bearish",
                symbols = listOf("EURUSD", "EURGBP", "EURJPY"),
                category = "economy"
            ),
            NewsArticle(
                id = "5",
                title = "OPEC+ Meeting: Oil Production Cuts May Extend Into Q2",
                summary = "OPEC+ members are discussing extending current production cuts through Q2 2026 to support prices amid weakening global demand forecasts.",
                source = "Reuters",
                url = "",
                publishedAt = now - 5 * hour,
                sentiment = "bullish",
                symbols = listOf("USOIL"),
                category = "commodities"
            ),
            NewsArticle(
                id = "6",
                title = "Bank of Japan Signals Gradual Policy Normalization",
                summary = "BOJ Governor indicates the central bank will continue raising rates gradually as Japan's economy shows signs of sustainable growth and inflation.",
                source = "Nikkei",
                url = "",
                publishedAt = now - 6 * hour,
                sentiment = "bearish",
                symbols = listOf("USDJPY", "EURJPY", "GBPJPY"),
                category = "economy"
            ),
            NewsArticle(
                id = "7",
                title = "Ethereum Staking Yields Attract Institutional Capital",
                summary = "Ethereum staking yields remain attractive at 3.5-4% annually, drawing institutional investors seeking yield in a volatile market environment.",
                source = "The Block",
                url = "",
                publishedAt = now - 7 * hour,
                sentiment = "bullish",
                symbols = listOf("ETHUSD"),
                category = "crypto"
            ),
            NewsArticle(
                id = "8",
                title = "US Dollar Weakens as Trade Deficit Widens",
                summary = "The US dollar index fell 0.3% as the trade deficit expanded to $78.2B in the latest month, raising concerns about the currency's medium-term outlook.",
                source = "MarketWatch",
                url = "",
                publishedAt = now - 8 * hour,
                sentiment = "bearish",
                symbols = listOf("EURUSD", "GBPUSD", "AUDUSD"),
                category = "economy"
            ),
            NewsArticle(
                id = "9",
                title = "Australian Dollar Rallies on Strong Employment Data",
                summary = "AUD/USD surged after Australia reported better-than-expected employment figures, with 45K jobs added versus 25K forecast, reducing rate cut expectations.",
                source = "AFX News",
                url = "",
                publishedAt = now - 9 * hour,
                sentiment = "bullish",
                symbols = listOf("AUDUSD"),
                category = "economy"
            ),
            NewsArticle(
                id = "10",
                title = "GBP/USD Steady as UK Inflation Holds Above Target",
                summary = "The British pound held steady as UK CPI remained above the Bank of England's 2% target, keeping the door open for future rate adjustments.",
                source = "BBC Business",
                url = "",
                publishedAt = now - 10 * hour,
                sentiment = "neutral",
                symbols = listOf("GBPUSD", "EURGBP"),
                category = "economy"
            )
        ).filter { article ->
            symbols.isEmpty() || article.symbols.any { it in symbols }
        }
    }

    private fun generateTrendingData(): List<TrendingSymbol> {
        return listOf(
            TrendingSymbol("XAUUSD", 0.82, 45200.0, 2658.40, 1.2, "bullish"),
            TrendingSymbol("BTCUSD", 1.45, 28900.0, 91450.00, 2.8, "bullish"),
            TrendingSymbol("ETHUSD", 2.10, 15600.0, 3120.50, 3.2, "bullish"),
            TrendingSymbol("SOLUSD", -0.95, 8900.0, 188.40, 4.1, "bearish"),
            TrendingSymbol("EURUSD", -0.15, 125000.0, 1.08455, 0.4, "neutral"),
            TrendingSymbol("GBPUSD", 0.08, 98000.0, 1.29625, 0.5, "neutral"),
            TrendingSymbol("USDJPY", -0.32, 112000.0, 154.325, 0.6, "bearish"),
            TrendingSymbol("USOIL", 1.25, 67000.0, 71.84, 1.8, "bullish"),
            TrendingSymbol("AUDUSD", 0.42, 78000.0, 0.65425, 0.5, "bullish"),
            TrendingSymbol("USDCAD", -0.18, 56000.0, 1.36525, 0.3, "neutral")
        ).sortedByDescending { kotlin.math.abs(it.change24h) }
    }

    private fun generateEconomicEvents(): List<EconomicEvent> {
        val now = System.currentTimeMillis()
        val day = 86400000L

        return listOf(
            EconomicEvent("1", "US GDP (QoQ)", now + day, "US", "high", "2.1%", "1.9%", null, "USD"),
            EconomicEvent("2", "ECB Interest Rate Decision", now + 2 * day, "EU", "high", "3.50%", "3.50%", null, "EUR"),
            EconomicEvent("3", "US CPI (MoM)", now + 3 * day, "US", "high", "0.3%", "0.2%", null, "USD"),
            EconomicEvent("4", "UK Employment Change", now + 4 * day, "UK", "medium", "15K", "12K", null, "GBP"),
            EconomicEvent("5", "AU Employment Change", now + 5 * day, "AU", "medium", "25K", "45K", null, "AUD"),
            EconomicEvent("6", "JP CPI (YoY)", now + 6 * day, "JP", "medium", "2.8%", "2.6%", null, "JPY"),
            EconomicEvent("7", "US NFP", now + 7 * day, "US", "high", "180K", "151K", null, "USD"),
            EconomicEvent("8", "CA Employment Change", now + 8 * day, "CA", "medium", "20K", "76K", null, "CAD"),
            EconomicEvent("9", "OPEC Monthly Report", now + 9 * day, "Global", "medium", "-", "-", null, "USD"),
            EconomicEvent("10", "CH GDP (QoQ)", now + 10 * day, "CH", "low", "0.5%", "0.3%", null, "CHF")
        )
    }

    private fun generateMarketSentiment(): MarketSentiment {
        return MarketSentiment(
            overall = "cautiously_bullish",
            fearGreedIndex = 62,
            fearGreedLabel = "Greed",
            vix = 14.2,
            dxy = 103.45,
            goldTrend = "bullish",
            cryptoTrend = "bullish",
            forexTrend = "neutral"
        )
    }
}
