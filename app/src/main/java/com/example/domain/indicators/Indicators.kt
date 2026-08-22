package com.example.domain.indicators

import com.example.domain.model.Candle
import kotlin.math.abs
import kotlin.math.max

data class IndicatorValues(
    val emaFast: Double,
    val emaSlow: Double,
    val adx: Double,
    val plusDi: Double,
    val minusDi: Double,
    val atr: Double
)

object IndicatorCalculator {

    /**
     * Calculates Exponential Moving Average series from price list.
     */
    fun calculateEma(prices: List<Double>, period: Int): List<Double> {
        if (prices.isEmpty() || period <= 0) return emptyList()
        if (prices.size < period) {
            val avg = prices.average()
            return List(prices.size) { avg }
        }

        val result = mutableListOf<Double>()
        val multiplier = 2.0 / (period + 1.0)

        // First EMA value is SMA of initial 'period' elements
        var currentEma = prices.take(period).average()
        for (i in 0 until period - 1) {
            result.add(prices[i])
        }
        result.add(currentEma)

        for (i in period until prices.size) {
            currentEma = (prices[i] - currentEma) * multiplier + currentEma
            result.add(currentEma)
        }

        return result
    }

    /**
     * Calculates Average True Range (Wilder's Smoothing).
     */
    fun calculateAtr(candles: List<Candle>, period: Int = 14): List<Double> {
        if (candles.isEmpty() || period <= 0) return emptyList()
        if (candles.size < 2) return listOf(candles.first().high - candles.first().low)

        val trueRanges = mutableListOf<Double>()
        // First TR is just High - Low
        trueRanges.add(candles[0].high - candles[0].low)

        for (i in 1 until candles.size) {
            val high = candles[i].high
            val low = candles[i].low
            val prevClose = candles[i - 1].close
            val tr = max(high - low, max(abs(high - prevClose), abs(low - prevClose)))
            trueRanges.add(tr)
        }

        if (trueRanges.size < period) {
            val avg = trueRanges.average()
            return List(candles.size) { avg }
        }

        val atrValues = mutableListOf<Double>()
        // First ATR is the simple average of the first 'period' TRs
        var currentAtr = trueRanges.take(period).average()
        for (i in 0 until period - 1) {
            atrValues.add(trueRanges[i])
        }
        atrValues.add(currentAtr)

        // Wilder's smoothing: ATR = (Prior ATR * (period - 1) + Current TR) / period
        for (i in period until trueRanges.size) {
            currentAtr = ((currentAtr * (period - 1)) + trueRanges[i]) / period
            atrValues.add(currentAtr)
        }

        return atrValues
    }

    /**
     * Calculates ADX (Average Directional Index) with +DI and -DI.
     */
    fun calculateAdx(candles: List<Candle>, period: Int = 14): List<Triple<Double, Double, Double>> {
        // Triple(ADX, +DI, -DI)
        if (candles.size < period * 2) {
            return List(candles.size) { Triple(20.0, 20.0, 20.0) }
        }

        val trList = mutableListOf<Double>()
        val plusDmList = mutableListOf<Double>()
        val minusDmList = mutableListOf<Double>()

        trList.add(candles[0].high - candles[0].low)
        plusDmList.add(0.0)
        minusDmList.add(0.0)

        for (i in 1 until candles.size) {
            val curr = candles[i]
            val prev = candles[i - 1]

            val tr = max(curr.high - curr.low, max(abs(curr.high - prev.close), abs(curr.low - prev.close)))
            trList.add(tr)

            val upMove = curr.high - prev.high
            val downMove = prev.low - curr.low

            if (upMove > downMove && upMove > 0) {
                plusDmList.add(upMove)
            } else {
                plusDmList.add(0.0)
            }

            if (downMove > upMove && downMove > 0) {
                minusDmList.add(downMove)
            } else {
                minusDmList.add(0.0)
            }
        }

        // Wilder smooth TR, +DM, -DM
        var smoothTr = trList.take(period).sum()
        var smoothPlusDm = plusDmList.take(period).sum()
        var smoothMinusDm = minusDmList.take(period).sum()

        val dxList = mutableListOf<Double>()
        val plusDiList = mutableListOf<Double>()
        val minusDiList = mutableListOf<Double>()

        val firstPlusDi = if (smoothTr > 0) 100.0 * (smoothPlusDm / smoothTr) else 0.0
        val firstMinusDi = if (smoothTr > 0) 100.0 * (smoothMinusDm / smoothTr) else 0.0
        val diSum = firstPlusDi + firstMinusDi
        val firstDx = if (diSum > 0) 100.0 * (abs(firstPlusDi - firstMinusDi) / diSum) else 0.0

        plusDiList.add(firstPlusDi)
        minusDiList.add(firstMinusDi)
        dxList.add(firstDx)

        for (i in period until candles.size) {
            smoothTr = smoothTr - (smoothTr / period) + trList[i]
            smoothPlusDm = smoothPlusDm - (smoothPlusDm / period) + plusDmList[i]
            smoothMinusDm = smoothMinusDm - (smoothMinusDm / period) + minusDmList[i]

            val pDi = if (smoothTr > 0) 100.0 * (smoothPlusDm / smoothTr) else 0.0
            val mDi = if (smoothTr > 0) 100.0 * (smoothMinusDm / smoothTr) else 0.0
            val sumDi = pDi + mDi
            val dx = if (sumDi > 0) 100.0 * (abs(pDi - mDi) / sumDi) else 0.0

            plusDiList.add(pDi)
            minusDiList.add(mDi)
            dxList.add(dx)
        }

        if (dxList.size < period) {
            return List(candles.size) { Triple(dxList.lastOrNull() ?: 25.0, 25.0, 25.0) }
        }

        // First ADX is average of first period DX values
        var currentAdx = dxList.take(period).average()
        val result = mutableListOf<Triple<Double, Double, Double>>()

        val offset = candles.size - dxList.size
        for (i in 0 until offset + period - 1) {
            result.add(Triple(25.0, 25.0, 25.0))
        }
        result.add(Triple(currentAdx, plusDiList[period - 1], minusDiList[period - 1]))

        for (i in period until dxList.size) {
            currentAdx = ((currentAdx * (period - 1)) + dxList[i]) / period
            result.add(Triple(currentAdx, plusDiList[i], minusDiList[i]))
        }

        while (result.size < candles.size) {
            result.add(0, Triple(25.0, 25.0, 25.0))
        }

        return result
    }

    /**
     * Compute all indicator values for the most recent closed candle.
     */
    fun computeLatest(
        candles: List<Candle>,
        fastEmaPeriod: Int = 20,
        slowEmaPeriod: Int = 50,
        adxPeriod: Int = 14,
        atrPeriod: Int = 14
    ): IndicatorValues? {
        if (candles.size < max(slowEmaPeriod, adxPeriod * 2)) return null

        val closes = candles.map { it.close }
        val fastEma = calculateEma(closes, fastEmaPeriod).lastOrNull() ?: return null
        val slowEma = calculateEma(closes, slowEmaPeriod).lastOrNull() ?: return null
        val atr = calculateAtr(candles, atrPeriod).lastOrNull() ?: return null
        val adxTriple = calculateAdx(candles, adxPeriod).lastOrNull() ?: Triple(25.0, 25.0, 25.0)

        return IndicatorValues(
            emaFast = fastEma,
            emaSlow = slowEma,
            adx = adxTriple.first,
            plusDi = adxTriple.second,
            minusDi = adxTriple.third,
            atr = atr
        )
    }
}
