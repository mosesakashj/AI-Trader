package com.example.broker

import com.example.domain.model.Candle
import com.example.domain.model.Quote
import com.example.domain.model.Timeframe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

interface MarketDataProvider {
    suspend fun subscribe(symbol: String)
    suspend fun unsubscribe(symbol: String)
    fun quotes(): Flow<Quote>
    suspend fun getHistoricalCandles(symbol: String, timeframe: Timeframe, count: Int = 100): List<Candle>
}

class PaperMarketDataProvider : MarketDataProvider {

    private val subscribedSymbols = ConcurrentHashMap.newKeySet<String>().apply {
        add("XAUUSD")
        add("BTCUSD")
        add("EURUSD")
        add("GBPUSD")
        add("USDJPY")
        add("AUDUSD")
        add("USDCAD")
        add("USDCHF")
        add("NZDUSD")
        add("EURGBP")
        add("EURJPY")
        add("GBPJPY")
    }

    private val currentPrices = ConcurrentHashMap<String, Double>().apply {
        put("XAUUSD", 2650.50)
        put("BTCUSD", 91250.00)
        put("EURUSD", 1.0850)
        put("GBPUSD", 1.2750)
        put("USDJPY", 149.50)
        put("AUDUSD", 0.6550)
        put("USDCAD", 1.3650)
        put("USDCHF", 0.8850)
        put("NZDUSD", 0.6050)
        put("EURGBP", 0.8510)
        put("EURJPY", 162.20)
        put("GBPJPY", 190.80)
    }

    // Volatility per symbol (for 1.5s tick)
    private val volatilityMap = mapOf(
        "XAUUSD" to 0.45,
        "BTCUSD" to 18.0,
        "EURUSD" to 0.00015,
        "GBPUSD" to 0.00018,
        "USDJPY" to 0.015,
        "AUDUSD" to 0.00015,
        "USDCAD" to 0.00015,
        "USDCHF" to 0.00015,
        "NZDUSD" to 0.00018,
        "EURGBP" to 0.00012,
        "EURJPY" to 0.012,
        "GBPJPY" to 0.018
    )

    // Spread per symbol
    private val spreadMap = mapOf(
        "XAUUSD" to 0.25,
        "BTCUSD" to 4.5,
        "EURUSD" to 0.00012,
        "GBPUSD" to 0.00015,
        "USDJPY" to 0.012,
        "AUDUSD" to 0.00015,
        "USDCAD" to 0.00015,
        "USDCHF" to 0.00015,
        "NZDUSD" to 0.00018,
        "EURGBP" to 0.00018,
        "EURJPY" to 0.015,
        "GBPJPY" to 0.025
    )

    // Historical candle volatility
    private val historicalVolatilityMap = mapOf(
        "XAUUSD" to 1.8,
        "BTCUSD" to 85.0,
        "EURUSD" to 0.0006,
        "GBPUSD" to 0.0008,
        "USDJPY" to 0.06,
        "AUDUSD" to 0.0007,
        "USDCAD" to 0.0006,
        "USDCHF" to 0.0006,
        "NZDUSD" to 0.0008,
        "EURGBP" to 0.0005,
        "EURJPY" to 0.05,
        "GBPJPY" to 0.08
    )

    // Base prices for historical candles
    private val basePriceMap = mapOf(
        "XAUUSD" to 2620.0,
        "BTCUSD" to 89500.0,
        "EURUSD" to 1.0820,
        "GBPUSD" to 1.2720,
        "USDJPY" to 148.80,
        "AUDUSD" to 0.6520,
        "USDCAD" to 1.3620,
        "USDCHF" to 0.8820,
        "NZDUSD" to 0.6020,
        "EURGBP" to 0.8490,
        "EURJPY" to 161.50,
        "GBPJPY" to 189.50
    )

    override suspend fun subscribe(symbol: String) {
        subscribedSymbols.add(symbol)
        // Initialize price if not present
        if (!currentPrices.containsKey(symbol)) {
            currentPrices[symbol] = basePriceMap[symbol] ?: 100.0
        }
    }

    override suspend fun unsubscribe(symbol: String) {
        subscribedSymbols.remove(symbol)
    }

    override fun quotes(): Flow<Quote> = flow {
        var step = 0
        while (true) {
            step++
            subscribedSymbols.forEach { symbol ->
                val basePrice = currentPrices[symbol] ?: 100.0
                val volatility = volatilityMap[symbol] ?: 0.0001
                val spread = spreadMap[symbol] ?: 0.0001

                // Geometric random walk with slight sine drift for realistic trend cycles
                val drift = sin(step * 0.05) * (volatility * 0.4)
                val noise = (Random.nextDouble() - 0.48) * volatility
                val newMid = (basePrice + drift + noise).coerceAtLeast(0.00001)
                currentPrices[symbol] = newMid

                val bid = newMid - (spread / 2.0)
                val ask = newMid + (spread / 2.0)

                emit(Quote(symbol = symbol, bid = bid, ask = ask, timestamp = System.currentTimeMillis()))
            }
            delay(1500) // 1.5s tick interval
        }
    }

    override suspend fun getHistoricalCandles(
        symbol: String,
        timeframe: Timeframe,
        count: Int
    ): List<Candle> {
        val candles = mutableListOf<Candle>()
        var price = basePriceMap[symbol] ?: 100.0
        val volatility = historicalVolatilityMap[symbol] ?: 0.0006
        val intervalMillis = timeframe.minutes * 60 * 1000L
        val now = System.currentTimeMillis()
        val startTime = now - (count * intervalMillis)

        for (i in 0 until count) {
            val openTime = startTime + (i * intervalMillis)
            val open = price
            // Drift + trend components
            val change = (Random.nextDouble() - 0.48) * volatility * 2.0
            val close = open + change
            val high = maxOf(open, close) + Random.nextDouble(0.00001, volatility)
            val low = minOf(open, close) - Random.nextDouble(0.00001, volatility)
            val volume = Random.nextDouble(50.0, 500.0)

            candles.add(
                Candle(
                    symbol = symbol,
                    timeframe = timeframe,
                    openTime = openTime,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = volume,
                    isClosed = i < count - 1
                )
            )
            price = close
        }

        return candles
    }
}
