package com.example

import com.example.domain.indicators.IndicatorCalculator
import com.example.domain.model.Candle
import com.example.domain.model.Timeframe
import org.junit.Assert.*
import org.junit.Test

class IndicatorCalculatorTest {

    @Test
    fun `EMA calculation produces correct length`() {
        val prices = (1..50).map { it.toDouble() }
        val ema = IndicatorCalculator.calculateEma(prices, 20)
        assertEquals(prices.size, ema.size)
    }

    @Test
    fun `EMA for empty list returns empty`() {
        val ema = IndicatorCalculator.calculateEma(emptyList(), 20)
        assertTrue(ema.isEmpty())
    }

    @Test
    fun `EMA for list shorter than period returns averages`() {
        val prices = listOf(10.0, 20.0, 30.0)
        val ema = IndicatorCalculator.calculateEma(prices, 20)
        assertEquals(3, ema.size)
        assertEquals(20.0, ema[0], 0.01)
    }

    @Test
    fun `ATR returns correct length`() {
        val candles = makeCandles(30)
        val atr = IndicatorCalculator.calculateAtr(candles, 14)
        assertEquals(candles.size, atr.size)
    }

    @Test
    fun `ATR is always positive for valid candles`() {
        val candles = makeCandles(30)
        val atr = IndicatorCalculator.calculateAtr(candles, 14)
        atr.drop(14).forEach { value ->
            assertTrue("ATR must be positive, was $value", value >= 0.0)
        }
    }

    @Test
    fun `ATR for empty list returns empty`() {
        val atr = IndicatorCalculator.calculateAtr(emptyList(), 14)
        assertTrue(atr.isEmpty())
    }

    @Test
    fun `RSI returns values between 0 and 100`() {
        val candles = makeCandles(30)
        val rsi = IndicatorCalculator.calculateRsi(candles, 14)
        rsi.drop(14).forEach { value ->
            assertTrue("RSI must be in [0,100], was $value", value in 0.0..100.0)
        }
    }

    @Test
    fun `RSI for empty list returns empty`() {
        val rsi = IndicatorCalculator.calculateRsi(emptyList(), 14)
        assertTrue(rsi.isEmpty())
    }

    @Test
    fun `computeLatest returns null for insufficient data`() {
        val candles = makeCandles(5)
        val result = IndicatorCalculator.computeLatest(candles)
        assertNull(result)
    }

    @Test
    fun `computeLatest returns values for sufficient data`() {
        val candles = makeCandles(60)
        val result = IndicatorCalculator.computeLatest(candles)
        assertNotNull(result)
        assertTrue(result!!.emaFast > 0)
        assertTrue(result.emaSlow > 0)
        assertTrue(result.adx >= 0)
        assertTrue(result.atr > 0)
    }

    @Test
    fun `computeLatest emaFast > emaSlow in uptrend`() {
        val candles = makeUptrendCandles(60, startPrice = 100.0)
        val result = IndicatorCalculator.computeLatest(candles)
        assertNotNull(result)
        assertTrue("Fast EMA ${result!!.emaFast} should be > Slow EMA ${result.emaSlow} in uptrend",
            result.emaFast > result.emaSlow)
    }

    @Test
    fun `computeLatest emaFast < emaSlow in downtrend`() {
        val candles = makeDowntrendCandles(60, startPrice = 200.0)
        val result = IndicatorCalculator.computeLatest(candles)
        assertNotNull(result)
        assertTrue("Fast EMA ${result!!.emaFast} should be < Slow EMA ${result.emaSlow} in downtrend",
            result.emaFast < result.emaSlow)
    }

    private fun makeCandles(count: Int, startPrice: Double = 100.0): List<Candle> {
        var price = startPrice
        return (0 until count).map { i ->
            val open = price
            val close = price + 0.50
            val high = close + 0.25
            val low = open - 0.25
            price = close
            Candle(
                symbol = "XAUUSD", timeframe = Timeframe.M15,
                openTime = i * 900000L,
                open = open, high = high, low = low, close = close,
                volume = 100.0, isClosed = true
            )
        }
    }

    private fun makeUptrendCandles(count: Int, startPrice: Double): List<Candle> {
        var price = startPrice
        return (0 until count).map { i ->
            val open = price
            val close = price + 1.0
            val high = close + 0.25
            val low = open - 0.25
            price = close
            Candle(
                symbol = "XAUUSD", timeframe = Timeframe.M15,
                openTime = i * 900000L,
                open = open, high = high, low = low, close = close,
                volume = 100.0, isClosed = true
            )
        }
    }

    private fun makeDowntrendCandles(count: Int, startPrice: Double): List<Candle> {
        var price = startPrice
        return (0 until count).map { i ->
            val open = price
            val close = price - 1.0
            val high = open + 0.25
            val low = close - 0.25
            price = close
            Candle(
                symbol = "XAUUSD", timeframe = Timeframe.M15,
                openTime = i * 900000L,
                open = open, high = high, low = low, close = close,
                volume = 100.0, isClosed = true
            )
        }
    }
}
