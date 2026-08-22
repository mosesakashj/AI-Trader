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
    }

    private val currentPrices = ConcurrentHashMap<String, Double>().apply {
        put("XAUUSD", 2650.50)
        put("BTCUSD", 91250.00)
    }

    override suspend fun subscribe(symbol: String) {
        subscribedSymbols.add(symbol)
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
                val volatility = if (symbol == "XAUUSD") 0.45 else 18.0
                val spread = if (symbol == "XAUUSD") 0.25 else 4.5

                // Geometric random walk with slight sine drift for realistic trend cycles
                val drift = sin(step * 0.05) * (volatility * 0.4)
                val noise = (Random.nextDouble() - 0.48) * volatility
                val newMid = (basePrice + drift + noise).coerceAtLeast(10.0)
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
        var price = if (symbol == "XAUUSD") 2620.0 else 89500.0
        val volatility = if (symbol == "XAUUSD") 1.8 else 85.0
        val intervalMillis = timeframe.minutes * 60 * 1000L
        val now = System.currentTimeMillis()
        val startTime = now - (count * intervalMillis)

        for (i in 0 until count) {
            val openTime = startTime + (i * intervalMillis)
            val open = price
            // Drift + trend components
            val change = (Random.nextDouble() - 0.48) * volatility * 2.0
            val close = open + change
            val high = maxOf(open, close) + Random.nextDouble(0.1, volatility)
            val low = minOf(open, close) - Random.nextDouble(0.1, volatility)
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
