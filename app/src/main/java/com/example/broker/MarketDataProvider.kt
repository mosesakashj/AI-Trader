package com.example.broker

import com.example.domain.model.Candle
import com.example.domain.model.Quote
import com.example.domain.model.SymbolCatalog
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
        SymbolCatalog.ALL_SYMBOLS.forEach { add(it.symbol) }
    }

    private val currentPrices = ConcurrentHashMap<String, Double>().apply {
        SymbolCatalog.ALL_SYMBOLS.forEach { sym ->
            put(sym.symbol, SymbolCatalog.getInitialQuote(sym.symbol).ask)
        }
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
                val symConfig = SymbolCatalog.get(symbol)
                val spread = symConfig.spreadLimit * 0.5
                val volatility = when (symbol) {
                    "BTCUSD" -> 18.0
                    "ETHUSD" -> 1.5
                    "SOLUSD" -> 0.15
                    "XAUUSD" -> 0.45
                    "USOIL" -> 0.08
                    "USDJPY" -> 0.04
                    "EURUSD" -> 0.00015
                    "GBPUSD" -> 0.00020
                    else -> 0.1
                }

                // Geometric random walk with slight sine drift for realistic trend cycles
                val drift = sin(step * 0.05) * (volatility * 0.4)
                val noise = (Random.nextDouble() - 0.48) * volatility
                val newMid = (basePrice + drift + noise).coerceAtLeast(symConfig.tickSize * 2)
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
        val symConfig = SymbolCatalog.get(symbol)
        val candles = mutableListOf<Candle>()
        var price = currentPrices[symbol] ?: SymbolCatalog.getInitialQuote(symbol).ask
        val volatility = when (symbol) {
            "BTCUSD" -> 85.0
            "ETHUSD" -> 6.5
            "SOLUSD" -> 0.8
            "XAUUSD" -> 1.8
            "USOIL" -> 0.35
            "USDJPY" -> 0.15
            "EURUSD" -> 0.0006
            "GBPUSD" -> 0.0008
            else -> 0.5
        }
        val intervalMillis = timeframe.minutes * 60 * 1000L
        val now = System.currentTimeMillis()
        val startTime = now - (count * intervalMillis)

        for (i in 0 until count) {
            val openTime = startTime + (i * intervalMillis)
            val open = price
            // Drift + trend components
            val change = (Random.nextDouble() - 0.48) * volatility * 2.0
            val close = open + change
            val high = maxOf(open, close) + Random.nextDouble(symConfig.tickSize, volatility)
            val low = minOf(open, close) - Random.nextDouble(symConfig.tickSize, volatility)
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
