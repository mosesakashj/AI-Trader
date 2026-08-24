package com.example.domain.strategy

import com.example.domain.indicators.IndicatorCalculator
import com.example.domain.indicators.IndicatorValues
import com.example.domain.model.*
import timber.log.Timber
import kotlin.math.abs

data class MultiTimeframeResult(
    val higherTimeframe: Timeframe,
    val lowerTimeframe: Timeframe,
    val higherTrend: TrendDirection,
    val lowerTrend: TrendDirection,
    val alignment: Boolean,
    val higherIndicators: IndicatorValues?,
    val lowerIndicators: IndicatorValues?,
    val confluenceScore: Double
)

enum class TrendDirection { BULLISH, BEARISH, NEUTRAL }

class MultiTimeframeAnalyzer {

    fun analyze(
        higherCandles: List<Candle>,
        lowerCandles: List<Candle>,
        higherTimeframe: Timeframe,
        lowerTimeframe: Timeframe,
        emaFastPeriod: Int = 20,
        emaSlowPeriod: Int = 50,
        adxPeriod: Int = 14,
        adxThreshold: Double = 25.0
    ): MultiTimeframeResult? {
        val higherClosed = higherCandles.filter { it.isClosed }
        val lowerClosed = lowerCandles.filter { it.isClosed }

        if (higherClosed.size < emaSlowPeriod + 10 || lowerClosed.size < emaSlowPeriod + 10) {
            return null
        }

        val higherIndicators = IndicatorCalculator.computeLatest(
            candles = higherClosed,
            fastEmaPeriod = emaFastPeriod,
            slowEmaPeriod = emaSlowPeriod,
            adxPeriod = adxPeriod,
            atrPeriod = 14,
            rsiPeriod = 14,
            macdFast = 12,
            macdSlow = 26,
            macdSignal = 9,
            bbPeriod = 20,
            bbStdDev = 2.0,
            stochK = 14,
            stochD = 3
        ) ?: return null

        val lowerIndicators = IndicatorCalculator.computeLatest(
            candles = lowerClosed,
            fastEmaPeriod = emaFastPeriod,
            slowEmaPeriod = emaSlowPeriod,
            adxPeriod = adxPeriod,
            atrPeriod = 14,
            rsiPeriod = 14,
            macdFast = 12,
            macdSlow = 26,
            macdSignal = 9,
            bbPeriod = 20,
            bbStdDev = 2.0,
            stochK = 14,
            stochD = 3
        ) ?: return null

        val higherTrend = classifyTrend(higherIndicators)
        val lowerTrend = classifyTrend(lowerIndicators)

        val alignment = when {
            higherTrend == TrendDirection.BULLISH && lowerTrend == TrendDirection.BULLISH -> true
            higherTrend == TrendDirection.BEARISH && lowerTrend == TrendDirection.BEARISH -> true
            else -> false
        }

        val confluenceScore = calculateConfluenceScore(
            higherTrend, lowerTrend, higherIndicators, lowerIndicators, adxThreshold
        )

        return MultiTimeframeResult(
            higherTimeframe = higherTimeframe,
            lowerTimeframe = lowerTimeframe,
            higherTrend = higherTrend,
            lowerTrend = lowerTrend,
            alignment = alignment,
            higherIndicators = higherIndicators,
            lowerIndicators = lowerIndicators,
            confluenceScore = confluenceScore
        )
    }

    private fun classifyTrend(indicators: IndicatorValues): TrendDirection {
        val emaAbove = indicators.emaFast > indicators.emaSlow
        val adxStrong = indicators.adx > 25.0

        return when {
            emaAbove && adxStrong -> TrendDirection.BULLISH
            !emaAbove && adxStrong -> TrendDirection.BEARISH
            else -> TrendDirection.NEUTRAL
        }
    }

    private fun calculateConfluenceScore(
        higherTrend: TrendDirection,
        lowerTrend: TrendDirection,
        higherIndicators: IndicatorValues,
        lowerIndicators: IndicatorValues,
        adxThreshold: Double
    ): Double {
        var score = 0.0

        if (higherTrend == lowerTrend && higherTrend != TrendDirection.NEUTRAL) {
            score += 40.0
        }

        if (higherIndicators.adx > adxThreshold) {
            score += 20.0
            if (higherIndicators.adx > 35.0) score += 10.0
        }

        if (lowerIndicators.adx > adxThreshold) {
            score += 15.0
        }

        if (higherTrend == TrendDirection.BULLISH && higherIndicators.emaFast > higherIndicators.emaSlow) {
            score += 10.0
        } else if (higherTrend == TrendDirection.BEARISH && higherIndicators.emaFast < higherIndicators.emaSlow) {
            score += 10.0
        }

        return score.coerceIn(0.0, 100.0)
    }

    fun getHigherTimeframe(lower: Timeframe): Timeframe {
        return when (lower) {
            Timeframe.M1 -> Timeframe.M5
            Timeframe.M5 -> Timeframe.M15
            Timeframe.M15 -> Timeframe.H1
            Timeframe.M30 -> Timeframe.H4
            Timeframe.H1 -> Timeframe.H4
            Timeframe.H4 -> Timeframe.D1
            Timeframe.D1 -> Timeframe.D1
        }
    }
}
