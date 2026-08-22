package com.example.domain.indicators

import com.example.domain.model.Candle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class IndicatorValues(
    val emaFast: Double,
    val emaSlow: Double,
    val adx: Double,
    val plusDi: Double,
    val minusDi: Double,
    val atr: Double,
    val rsi: Double = 50.0,
    val macdLine: Double = 0.0,
    val macdSignal: Double = 0.0,
    val macdHistogram: Double = 0.0,
    val bbUpper: Double = 0.0,
    val bbMiddle: Double = 0.0,
    val bbLower: Double = 0.0,
    val bbWidth: Double = 0.0,
    val stochK: Double = 50.0,
    val stochD: Double = 50.0
)

object IndicatorCalculator {

    fun calculateEma(prices: List<Double>, period: Int): List<Double> {
        if (prices.isEmpty() || period <= 0) return emptyList()
        if (prices.size < period) {
            val avg = prices.average()
            return List(prices.size) { avg }
        }
        val result = mutableListOf<Double>()
        val multiplier = 2.0 / (period + 1.0)
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

    fun calculateAtr(candles: List<Candle>, period: Int = 14): List<Double> {
        if (candles.isEmpty() || period <= 0) return emptyList()
        if (candles.size < 2) return listOf(candles.first().high - candles.first().low)
        val trueRanges = mutableListOf<Double>()
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
        var currentAtr = trueRanges.take(period).average()
        for (i in 0 until period - 1) {
            atrValues.add(trueRanges[i])
        }
        atrValues.add(currentAtr)
        for (i in period until trueRanges.size) {
            currentAtr = ((currentAtr * (period - 1)) + trueRanges[i]) / period
            atrValues.add(currentAtr)
        }
        return atrValues
    }

    fun calculateAdx(candles: List<Candle>, period: Int = 14): List<Triple<Double, Double, Double>> {
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

    fun calculateSma(prices: List<Double>, period: Int): List<Double> {
        if (prices.isEmpty() || period <= 0) return emptyList()
        val result = mutableListOf<Double>()
        for (i in prices.indices) {
            if (i < period - 1) {
                result.add(prices.take(i + 1).average())
            } else {
                result.add(prices.subList(i - period + 1, i + 1).average())
            }
        }
        return result
    }

    fun calculateRsi(closes: List<Double>, period: Int = 14): List<Double> {
        if (closes.size < 2) return List(closes.size) { 50.0 }
        val gains = mutableListOf<Double>()
        val losses = mutableListOf<Double>()
        for (i in 1 until closes.size) {
            val change = closes[i] - closes[i - 1]
            if (change > 0) {
                gains.add(change)
                losses.add(0.0)
            } else {
                gains.add(0.0)
                losses.add(abs(change))
            }
        }
        if (gains.size < period) return List(closes.size) { 50.0 }
        val result = mutableListOf<Double>()
        for (i in 0 until period) {
            result.add(50.0)
        }
        var avgGain = gains.take(period).average()
        var avgLoss = losses.take(period).average()
        val rs = if (avgLoss > 0) avgGain / avgLoss else 100.0
        result.add(if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + rs))
        for (i in period until gains.size) {
            avgGain = (avgGain * (period - 1) + gains[i]) / period
            avgLoss = (avgLoss * (period - 1) + losses[i]) / period
            val currentRs = if (avgLoss > 0) avgGain / avgLoss else 100.0
            result.add(if (avgLoss == 0.0) 100.0 else 100.0 - 100.0 / (1.0 + currentRs))
        }
        return result
    }

    fun calculateMacd(
        closes: List<Double>,
        fastPeriod: Int = 12,
        slowPeriod: Int = 26,
        signalPeriod: Int = 9
    ): List<Triple<Double, Double, Double>> {
        if (closes.size < slowPeriod + signalPeriod) {
            return List(closes.size) { Triple(0.0, 0.0, 0.0) }
        }
        val fastEma = calculateEma(closes, fastPeriod)
        val slowEma = calculateEma(closes, slowPeriod)
        val macdLine = mutableListOf<Double>()
        for (i in closes.indices) {
            if (i < slowPeriod - 1) {
                macdLine.add(0.0)
            } else {
                macdLine.add(fastEma[i] - slowEma[i])
            }
        }
        val validMacd = macdLine.drop(slowPeriod - 1)
        val signalLine = calculateEma(validMacd, signalPeriod)
        val result = mutableListOf<Triple<Double, Double, Double>>()
        val signalOffset = slowPeriod - 1 + signalPeriod - 1
        for (i in closes.indices) {
            if (i < signalOffset) {
                result.add(Triple(macdLine[i], 0.0, macdLine[i]))
            } else {
                val sigIdx = i - (slowPeriod - 1)
                val signal = if (sigIdx < signalLine.size) signalLine[sigIdx] else 0.0
                val macd = macdLine[i]
                result.add(Triple(macd, signal, macd - signal))
            }
        }
        return result
    }

    fun calculateBollingerBands(
        closes: List<Double>,
        period: Int = 20,
        stdDevMultiplier: Double = 2.0
    ): List<Triple<Double, Double, Double>> {
        if (closes.isEmpty() || period <= 0) return emptyList()
        val sma = calculateSma(closes, period)
        val result = mutableListOf<Triple<Double, Double, Double>>()
        for (i in closes.indices) {
            if (i < period - 1) {
                result.add(Triple(closes[i], sma[i], closes[i]))
            } else {
                val window = closes.subList(i - period + 1, i + 1)
                val mean = sma[i]
                val variance = window.sumOf { (it - mean) * (it - mean) } / period
                val stdDev = sqrt(variance)
                val upper = mean + stdDevMultiplier * stdDev
                val lower = mean - stdDevMultiplier * stdDev
                result.add(Triple(upper, mean, lower))
            }
        }
        return result
    }

    fun calculateStochastic(
        candles: List<Candle>,
        kPeriod: Int = 14,
        dPeriod: Int = 3
    ): List<Pair<Double, Double>> {
        if (candles.isEmpty()) return emptyList()
        val result = mutableListOf<Pair<Double, Double>>()
        val kValues = mutableListOf<Double>()
        for (i in candles.indices) {
            if (i < kPeriod - 1) {
                result.add(Pair(50.0, 50.0))
                kValues.add(50.0)
            } else {
                val window = candles.subList(i - kPeriod + 1, i + 1)
                val highestHigh = window.maxOf { it.high }
                val lowestLow = window.minOf { it.low }
                val close = candles[i].close
                val k = if (highestHigh != lowestLow) {
                    ((close - lowestLow) / (highestHigh - lowestLow)) * 100.0
                } else {
                    50.0
                }
                kValues.add(k)
                if (kValues.size < dPeriod) {
                    result.add(Pair(k, 50.0))
                } else {
                    val d = kValues.takeLast(dPeriod).average()
                    result.add(Pair(k, d))
                }
            }
        }
        return result
    }

    fun findSupportResistance(
        candles: List<Candle>,
        lookback: Int = 50
    ): Pair<List<Double>, List<Double>> {
        val supports = mutableListOf<Double>()
        val resistances = mutableListOf<Double>()
        val startIdx = max(0, candles.size - lookback)
        for (i in startIdx until candles.size) {
            val isPivotLow = if (i > 0 && i < candles.size - 1) {
                candles[i].low < candles[i - 1].low && candles[i].low < candles[i + 1].low
            } else false
            val isPivotHigh = if (i > 0 && i < candles.size - 1) {
                candles[i].high > candles[i - 1].high && candles[i].high > candles[i + 1].high
            } else false
            if (isPivotLow) supports.add(candles[i].low)
            if (isPivotHigh) resistances.add(candles[i].high)
        }
        return Pair(supports.distinct().sorted(), resistances.distinct().sorted())
    }

    fun computeLatest(
        candles: List<Candle>,
        fastEmaPeriod: Int = 20,
        slowEmaPeriod: Int = 50,
        adxPeriod: Int = 14,
        atrPeriod: Int = 14,
        rsiPeriod: Int = 14,
        macdFast: Int = 12,
        macdSlow: Int = 26,
        macdSignal: Int = 9,
        bbPeriod: Int = 20,
        bbStdDev: Double = 2.0,
        stochK: Int = 14,
        stochD: Int = 3
    ): IndicatorValues? {
        if (candles.size < max(slowEmaPeriod, adxPeriod * 2)) return null
        val closes = candles.map { it.close }
        val fastEma = calculateEma(closes, fastEmaPeriod).lastOrNull() ?: return null
        val slowEma = calculateEma(closes, slowEmaPeriod).lastOrNull() ?: return null
        val atr = calculateAtr(candles, atrPeriod).lastOrNull() ?: return null
        val adxTriple = calculateAdx(candles, adxPeriod).lastOrNull() ?: Triple(25.0, 25.0, 25.0)
        val rsiVal = calculateRsi(closes, rsiPeriod).lastOrNull() ?: 50.0
        val macdTriple = calculateMacd(closes, macdFast, macdSlow, macdSignal).lastOrNull() ?: Triple(0.0, 0.0, 0.0)
        val bbTriple = calculateBollingerBands(closes, bbPeriod, bbStdDev).lastOrNull() ?: Triple(0.0, 0.0, 0.0)
        val stochPair = calculateStochastic(candles, stochK, stochD).lastOrNull() ?: Pair(50.0, 50.0)
        val bbWidth = if (bbTriple.second != 0.0) (bbTriple.first - bbTriple.third) / bbTriple.second else 0.0
        return IndicatorValues(
            emaFast = fastEma,
            emaSlow = slowEma,
            adx = adxTriple.first,
            plusDi = adxTriple.second,
            minusDi = adxTriple.third,
            atr = atr,
            rsi = rsiVal,
            macdLine = macdTriple.first,
            macdSignal = macdTriple.second,
            macdHistogram = macdTriple.third,
            bbUpper = bbTriple.first,
            bbMiddle = bbTriple.second,
            bbLower = bbTriple.third,
            bbWidth = bbWidth,
            stochK = stochPair.first,
            stochD = stochPair.second
        )
    }
}
