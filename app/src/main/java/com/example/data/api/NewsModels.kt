package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NewsArticle(
    @Json(name = "id") val id: String = "",
    @Json(name = "title") val title: String = "",
    @Json(name = "summary") val summary: String = "",
    @Json(name = "source") val source: String = "",
    @Json(name = "url") val url: String = "",
    @Json(name = "image") val image: String = "",
    @Json(name = "publishedAt") val publishedAt: Long = 0L,
    @Json(name = "sentiment") val sentiment: String = "neutral",
    @Json(name = "symbols") val symbols: List<String> = emptyList(),
    @Json(name = "category") val category: String = "general"
)

@JsonClass(generateAdapter = true)
data class NewsResponse(
    @Json(name = "data") val articles: List<NewsArticle> = emptyList(),
    @Json(name = "nextPage") val nextPage: String? = null
)

@JsonClass(generateAdapter = true)
data class TrendingSymbol(
    @Json(name = "symbol") val symbol: String = "",
    @Json(name = "change24h") val change24h: Double = 0.0,
    @Json(name = "volume") val volume: Double = 0.0,
    @Json(name = "price") val price: Double = 0.0,
    @Json(name = "volatility") val volatility: Double = 0.0,
    @Json(name = "sentiment") val sentiment: String = "neutral"
)

@JsonClass(generateAdapter = true)
data class EconomicEvent(
    @Json(name = "id") val id: String = "",
    @Json(name = "title") val title: String = "",
    @Json(name = "date") val date: Long = 0L,
    @Json(name = "country") val country: String = "",
    @Json(name = "impact") val impact: String = "medium",
    @Json(name = "forecast") val forecast: String = "",
    @Json(name = "previous") val previous: String = "",
    @Json(name = "actual") val actual: String? = null,
    @Json(name = "currency") val currency: String = ""
)

@JsonClass(generateAdapter = true)
data class MarketSentiment(
    @Json(name = "overall") val overall: String = "neutral",
    @Json(name = "fearGreedIndex") val fearGreedIndex: Int = 50,
    @Json(name = "fearGreedLabel") val fearGreedLabel: String = "Neutral",
    @Json(name = "vix") val vix: Double = 0.0,
    @Json(name = "dxy") val dxy: Double = 0.0,
    @Json(name = "goldTrend") val goldTrend: String = "neutral",
    @Json(name = "cryptoTrend") val cryptoTrend: String = "neutral",
    @Json(name = "forexTrend") val forexTrend: String = "neutral"
)

@JsonClass(generateAdapter = true)
data class ExpectedMove(
    @Json(name = "symbol") val symbol: String = "",
    @Json(name = "currentPrice") val currentPrice: Double = 0.0,
    @Json(name = "atr14") val atr14: Double = 0.0,
    @Json(name = "expectedDailyRange") val expectedDailyRange: Double = 0.0,
    @Json(name = "expectedWeeklyRange") val expectedWeeklyRange: Double = 0.0,
    @Json(name = "upperBand") val upperBand: Double = 0.0,
    @Json(name = "lowerBand") val lowerBand: Double = 0.0,
    @Json(name = "volatilityPercentile") val volatilityPercentile: Double = 0.0,
    @Json(name = "volatilityLabel") val volatilityLabel: String = "Normal"
)
