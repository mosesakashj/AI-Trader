package com.example.domain.strategy

import com.example.domain.indicators.IndicatorCalculator
import com.example.domain.indicators.IndicatorValues
import com.example.domain.model.*
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class TradingStrategy(
    val strategyConfig: StrategyConfig = StrategyConfig(),
    private val newsFilter: NewsFilter = NoNewsFilter()
) {

    fun evaluate(
        candles: List<Candle>,
        symbolConfig: SymbolConfig,
        currentQuote: Quote,
        riskConfig: RiskConfig,
        hasOpenPosition: Boolean,
        lastProcessedCandleTime: Long,
        isConnectionHealthy: Boolean,
        dailyLossReached: Boolean,
        consecutiveLossesReached: Boolean,
        marginSufficient: Boolean
    ): Signal? {
        val minRequired = maxOf(
            strategyConfig.emaSlowPeriod,
            strategyConfig.bbPeriod,
            strategyConfig.rangeLookbackPeriod,
            strategyConfig.breakoutLookbackPeriod,
            strategyConfig.adxPeriod * 2
        ) + 10
        if (candles.size < minRequired) return null

        val closedCandles = candles.filter { it.isClosed }
        if (closedCandles.size < 2) return null

        val lastClosedCandle = closedCandles.last()
        if (lastClosedCandle.openTime == lastProcessedCandleTime) return null

        val prevClosedCandle = closedCandles[closedCandles.size - 2]

        if (!newsFilter.isNewsFreeWindow(lastClosedCandle.openTime, symbolConfig.symbol)) return null

        val indicators = IndicatorCalculator.computeLatest(
            candles = closedCandles,
            fastEmaPeriod = strategyConfig.emaFastPeriod,
            slowEmaPeriod = strategyConfig.emaSlowPeriod,
            adxPeriod = strategyConfig.adxPeriod,
            atrPeriod = strategyConfig.atrPeriod,
            rsiPeriod = strategyConfig.rsiPeriod,
            macdFast = strategyConfig.macdFastPeriod,
            macdSlow = strategyConfig.macdSlowPeriod,
            macdSignal = strategyConfig.macdSignalPeriod,
            bbPeriod = strategyConfig.bbPeriod,
            bbStdDev = strategyConfig.bbStdDev,
            stochK = 14,
            stochD = 3
        ) ?: return null

        val prevIndicators = if (closedCandles.size >= 3) {
            IndicatorCalculator.computeLatest(
                candles = closedCandles.dropLast(1),
                fastEmaPeriod = strategyConfig.emaFastPeriod,
                slowEmaPeriod = strategyConfig.emaSlowPeriod,
                adxPeriod = strategyConfig.adxPeriod,
                atrPeriod = strategyConfig.atrPeriod,
                rsiPeriod = strategyConfig.rsiPeriod,
                macdFast = strategyConfig.macdFastPeriod,
                macdSlow = strategyConfig.macdSlowPeriod,
                macdSignal = strategyConfig.macdSignalPeriod,
                bbPeriod = strategyConfig.bbPeriod,
                bbStdDev = strategyConfig.bbStdDev,
                stochK = 14,
                stochD = 3
            )
        } else null

        val atr = indicators.atr
        val sessionCheck = isSessionAllowed(lastClosedCandle.openTime)
        val spreadCheck = currentQuote.spread <= symbolConfig.spreadLimit
        val atrCheck = atr >= symbolConfig.minimumAtr && atr <= symbolConfig.maximumAtr
        val riskCheck = !dailyLossReached && !consecutiveLossesReached && marginSufficient && !hasOpenPosition &&
            !riskConfig.emergencyStopActive && !riskConfig.safeModeActive && isConnectionHealthy

        if (!sessionCheck || !spreadCheck || !atrCheck || !riskCheck) return null

        return when (strategyConfig.strategyType) {
            StrategyType.PULLBACK -> evaluatePullback(
                closedCandles, lastClosedCandle, prevClosedCandle, indicators,
                symbolConfig, currentQuote, riskCheck, sessionCheck, spreadCheck, atr
            )
            StrategyType.BREAKOUT -> evaluateBreakout(
                closedCandles, lastClosedCandle, prevClosedCandle, indicators,
                symbolConfig, currentQuote, riskCheck, sessionCheck, spreadCheck, atr
            )
            StrategyType.MEAN_REVERSION -> evaluateMeanReversion(
                closedCandles, lastClosedCandle, prevClosedCandle, indicators,
                symbolConfig, currentQuote, riskCheck, sessionCheck, spreadCheck, atr
            )
            StrategyType.MOMENTUM -> evaluateMomentum(
                closedCandles, lastClosedCandle, prevClosedCandle, indicators, prevIndicators,
                symbolConfig, currentQuote, riskCheck, sessionCheck, spreadCheck, atr
            )
            StrategyType.RANGE_TRADING -> evaluateRangeTrading(
                closedCandles, lastClosedCandle, prevClosedCandle, indicators,
                symbolConfig, currentQuote, riskCheck, sessionCheck, spreadCheck, atr
            )
            StrategyType.SCALPING -> evaluateScalping(
                closedCandles, lastClosedCandle, prevClosedCandle, indicators, prevIndicators,
                symbolConfig, currentQuote, riskCheck, sessionCheck, spreadCheck, atr
            )
        }
    }

    private fun evaluatePullback(
        closedCandles: List<Candle>,
        lastClosedCandle: Candle,
        prevClosedCandle: Candle,
        indicators: IndicatorValues,
        symbolConfig: SymbolConfig,
        currentQuote: Quote,
        riskCheck: Boolean,
        sessionCheck: Boolean,
        spreadCheck: Boolean,
        atr: Double
    ): Signal? {
        val emaFast = indicators.emaFast
        val emaSlow = indicators.emaSlow
        val adx = indicators.adx

        if (adx < strategyConfig.adxThreshold) return null

        val emaBandUpper = emaFast + atr * 0.5
        val emaBandLower = emaFast - atr * 0.5

        val isBullishTrend = emaFast > emaSlow
        val isBearishTrend = emaFast < emaSlow

        val maxExtension = lastClosedCandle.close + atr * strategyConfig.maxCandleExtensionAtr
        val minExtension = lastClosedCandle.close - atr * strategyConfig.maxCandleExtensionAtr

        val isBullishCandle = lastClosedCandle.close > lastClosedCandle.open
        val isBearishCandle = lastClosedCandle.close < lastClosedCandle.open

        val adaptive = AdaptiveCalculator.computeAll(
            atr = atr, adx = adx, adxThreshold = strategyConfig.adxThreshold,
            baseSlMultiplier = strategyConfig.atrSlMultiplier, baseRiskReward = strategyConfig.riskRewardRatio,
            baseBeTriggerR = strategyConfig.breakEvenTriggerR, baseBeBufferPips = strategyConfig.breakEvenBufferPips,
            baseTrailingDistanceAtr = strategyConfig.trailingStopDistanceAtr,
            minimumStopDistance = symbolConfig.minimumStopDistance, tickSize = symbolConfig.tickSize,
            adaptiveSlEnabled = strategyConfig.adaptiveSlEnabled, adaptiveTpEnabled = strategyConfig.adaptiveTpEnabled,
            adaptiveBeEnabled = strategyConfig.adaptiveBeEnabled
        )

        if (isBullishTrend) {
            val pullbackToBand = lastClosedCandle.low <= emaBandUpper
            val notExtended = lastClosedCandle.low >= minExtension

            if (pullbackToBand && notExtended && isBullishCandle) {
                val entryPrice = currentQuote.ask
                val stopLoss = entryPrice - adaptive.slDistance
                val takeProfit = entryPrice + adaptive.tpDistance
                val riskReward = if (adaptive.slDistance > 0) adaptive.tpDistance / adaptive.slDistance else 0.0

                return Signal(
                    id = java.util.UUID.randomUUID().toString(),
                    symbol = symbolConfig.symbol,
                    direction = TradeDirection.BUY,
                    price = entryPrice,
                    stopLoss = stopLoss,
                    takeProfit = takeProfit,
                    rrRatio = riskReward,
                    candleTime = lastClosedCandle.openTime,
                    timestamp = System.currentTimeMillis(),
                    explanation = SignalExplanation(
                        symbol = symbolConfig.symbol,
                        direction = TradeDirection.BUY,
                        emaFast = emaFast, emaSlow = emaSlow, adx = adx, atr = atr,
                        trendCheck = true, adxCheck = true, pullbackCheck = true, candleCheck = true,
                        spreadCheck = spreadCheck, riskCheck = riskCheck, sessionCheck = sessionCheck,
                        decision = "BUY",
                        reason = "Bullish trend (EMA Fast > EMA Slow), ADX >= ${strategyConfig.adxThreshold}, pullback to EMA band, bullish candle [Adaptive SL: ${"%.1f".format(adaptive.slDistance)}, TP: ${"%.1f".format(adaptive.tpDistance)}, BE@${"%.2f".format(adaptive.beTriggerR)}R]"
                    )
                )
            }
        }

        if (isBearishTrend) {
            val pullbackToBand = lastClosedCandle.high >= emaBandLower
            val notExtended = lastClosedCandle.high <= maxExtension

            if (pullbackToBand && notExtended && isBearishCandle) {
                val entryPrice = currentQuote.bid
                val stopLoss = entryPrice + adaptive.slDistance
                val takeProfit = entryPrice - adaptive.tpDistance
                val riskReward = if (adaptive.slDistance > 0) adaptive.tpDistance / adaptive.slDistance else 0.0

                return Signal(
                    id = java.util.UUID.randomUUID().toString(),
                    symbol = symbolConfig.symbol,
                    direction = TradeDirection.SELL,
                    price = entryPrice,
                    stopLoss = stopLoss,
                    takeProfit = takeProfit,
                    rrRatio = riskReward,
                    candleTime = lastClosedCandle.openTime,
                    timestamp = System.currentTimeMillis(),
                    explanation = SignalExplanation(
                        symbol = symbolConfig.symbol,
                        direction = TradeDirection.SELL,
                        emaFast = emaFast, emaSlow = emaSlow, adx = adx, atr = atr,
                        trendCheck = true, adxCheck = true, pullbackCheck = true, candleCheck = true,
                        spreadCheck = spreadCheck, riskCheck = riskCheck, sessionCheck = sessionCheck,
                        decision = "SELL",
                        reason = "Bearish trend (EMA Fast < EMA Slow), ADX >= ${strategyConfig.adxThreshold}, pullback to EMA band, bearish candle [Adaptive SL: ${"%.1f".format(adaptive.slDistance)}, TP: ${"%.1f".format(adaptive.tpDistance)}, BE@${"%.2f".format(adaptive.beTriggerR)}R]"
                    )
                )
            }
        }

        return null
    }

    private fun evaluateBreakout(
        closedCandles: List<Candle>,
        lastClosedCandle: Candle,
        prevClosedCandle: Candle,
        indicators: IndicatorValues,
        symbolConfig: SymbolConfig,
        currentQuote: Quote,
        riskCheck: Boolean,
        sessionCheck: Boolean,
        spreadCheck: Boolean,
        atr: Double
    ): Signal? {
        val emaFast = indicators.emaFast
        val emaSlow = indicators.emaSlow
        val adx = indicators.adx

        if (adx < strategyConfig.adxThreshold) return null

        val lookback = min(strategyConfig.breakoutLookbackPeriod, closedCandles.size - 1)
        val lookbackCandles = closedCandles.dropLast(1).takeLast(lookback)
        if (lookbackCandles.size < strategyConfig.breakoutLookbackPeriod) return null

        val highestHigh = lookbackCandles.maxOf { it.high }
        val lowestLow = lookbackCandles.minOf { it.low }

        val isBullishCandle = lastClosedCandle.close > lastClosedCandle.open
        val isBearishCandle = lastClosedCandle.close < lastClosedCandle.open

        val bullishBreakout = lastClosedCandle.close > highestHigh && prevClosedCandle.close <= highestHigh
        val bearishBreakout = lastClosedCandle.close < lowestLow && prevClosedCandle.close >= lowestLow

        val adaptive = AdaptiveCalculator.computeAll(
            atr = atr, adx = adx, adxThreshold = strategyConfig.adxThreshold,
            baseSlMultiplier = strategyConfig.atrSlMultiplier, baseRiskReward = strategyConfig.riskRewardRatio,
            baseBeTriggerR = strategyConfig.breakEvenTriggerR, baseBeBufferPips = strategyConfig.breakEvenBufferPips,
            baseTrailingDistanceAtr = strategyConfig.trailingStopDistanceAtr,
            minimumStopDistance = symbolConfig.minimumStopDistance, tickSize = symbolConfig.tickSize,
            adaptiveSlEnabled = strategyConfig.adaptiveSlEnabled, adaptiveTpEnabled = strategyConfig.adaptiveTpEnabled,
            adaptiveBeEnabled = strategyConfig.adaptiveBeEnabled
        )

        if (bullishBreakout && isBullishCandle) {
            val entryPrice = currentQuote.ask
            val stopLoss = entryPrice - adaptive.slDistance
            val takeProfit = entryPrice + adaptive.tpDistance
            val riskReward = if (adaptive.slDistance > 0) adaptive.tpDistance / adaptive.slDistance else 0.0

            return Signal(
                id = java.util.UUID.randomUUID().toString(),
                symbol = symbolConfig.symbol,
                direction = TradeDirection.BUY,
                price = entryPrice,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                rrRatio = riskReward,
                candleTime = lastClosedCandle.openTime,
                timestamp = System.currentTimeMillis(),
                explanation = SignalExplanation(
                    symbol = symbolConfig.symbol,
                    direction = TradeDirection.BUY,
                    emaFast = emaFast, emaSlow = emaSlow, adx = adx, atr = atr,
                    trendCheck = true, adxCheck = true, pullbackCheck = false, candleCheck = true,
                    spreadCheck = spreadCheck, riskCheck = riskCheck, sessionCheck = sessionCheck,
                    decision = "BUY",
                    reason = "Bullish breakout above $highestHigh, ADX >= ${strategyConfig.adxThreshold}, bullish candle confirmation [Adaptive SL: ${"%.1f".format(adaptive.slDistance)}, TP: ${"%.1f".format(adaptive.tpDistance)}]"
                )
            )
        }

        if (bearishBreakout && isBearishCandle) {
            val entryPrice = currentQuote.bid
            val stopLoss = entryPrice + adaptive.slDistance
            val takeProfit = entryPrice - adaptive.tpDistance
            val riskReward = if (adaptive.slDistance > 0) adaptive.tpDistance / adaptive.slDistance else 0.0

            return Signal(
                id = java.util.UUID.randomUUID().toString(),
                symbol = symbolConfig.symbol,
                direction = TradeDirection.SELL,
                price = entryPrice,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                rrRatio = riskReward,
                candleTime = lastClosedCandle.openTime,
                timestamp = System.currentTimeMillis(),
                explanation = SignalExplanation(
                    symbol = symbolConfig.symbol,
                    direction = TradeDirection.SELL,
                    emaFast = emaFast, emaSlow = emaSlow, adx = adx, atr = atr,
                    trendCheck = true, adxCheck = true, pullbackCheck = false, candleCheck = true,
                    spreadCheck = spreadCheck, riskCheck = riskCheck, sessionCheck = sessionCheck,
                    decision = "SELL",
                    reason = "Bearish breakout below $lowestLow, ADX >= ${strategyConfig.adxThreshold}, bearish candle confirmation [Adaptive SL: ${"%.1f".format(adaptive.slDistance)}, TP: ${"%.1f".format(adaptive.tpDistance)}]"
                )
            )
        }

        return null
    }

    private fun evaluateMeanReversion(
        closedCandles: List<Candle>,
        lastClosedCandle: Candle,
        prevClosedCandle: Candle,
        indicators: IndicatorValues,
        symbolConfig: SymbolConfig,
        currentQuote: Quote,
        riskCheck: Boolean,
        sessionCheck: Boolean,
        spreadCheck: Boolean,
        atr: Double
    ): Signal? {
        val emaFast = indicators.emaFast
        val emaSlow = indicators.emaSlow
        val adx = indicators.adx
        val rsi = indicators.rsi
        val bbUpper = indicators.bbUpper
        val bbMiddle = indicators.bbMiddle
        val bbLower = indicators.bbLower

        if (adx >= 25.0) return null

        val isBullishCandle = lastClosedCandle.close > lastClosedCandle.open
        val isBearishCandle = lastClosedCandle.close < lastClosedCandle.open

        val oversoldCondition = rsi < strategyConfig.rsiOversold && lastClosedCandle.close < bbLower
        val overboughtCondition = rsi > strategyConfig.rsiOverbought && lastClosedCandle.close > bbUpper

        val adaptive = AdaptiveCalculator.computeAll(
            atr = atr, adx = adx, adxThreshold = strategyConfig.adxThreshold,
            baseSlMultiplier = 1.5, baseRiskReward = 1.0,
            baseBeTriggerR = strategyConfig.breakEvenTriggerR, baseBeBufferPips = strategyConfig.breakEvenBufferPips,
            baseTrailingDistanceAtr = strategyConfig.trailingStopDistanceAtr,
            minimumStopDistance = symbolConfig.minimumStopDistance, tickSize = symbolConfig.tickSize,
            adaptiveSlEnabled = strategyConfig.adaptiveSlEnabled, adaptiveTpEnabled = false,
            adaptiveBeEnabled = strategyConfig.adaptiveBeEnabled
        )

        if (oversoldCondition && isBullishCandle) {
            val entryPrice = currentQuote.ask
            val stopLoss = entryPrice - adaptive.slDistance
            val takeProfit = bbMiddle
            val tpDistance = abs(takeProfit - entryPrice)
            val riskReward = if (adaptive.slDistance > 0) tpDistance / adaptive.slDistance else 0.0

            return Signal(
                id = java.util.UUID.randomUUID().toString(),
                symbol = symbolConfig.symbol,
                direction = TradeDirection.BUY,
                price = entryPrice,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                rrRatio = riskReward,
                candleTime = lastClosedCandle.openTime,
                timestamp = System.currentTimeMillis(),
                explanation = SignalExplanation(
                    symbol = symbolConfig.symbol,
                    direction = TradeDirection.BUY,
                    emaFast = emaFast, emaSlow = emaSlow, adx = adx, atr = atr,
                    trendCheck = false, adxCheck = adx < 25.0, pullbackCheck = false, candleCheck = true,
                    spreadCheck = spreadCheck, riskCheck = riskCheck, sessionCheck = sessionCheck,
                    decision = "BUY",
                    reason = "RSI($rsi) < ${strategyConfig.rsiOversold}, Close < BB Lower($bbLower), ADX($adx) < 25, bullish reversal candle [Adaptive SL: ${"%.1f".format(adaptive.slDistance)}]"
                )
            )
        }

        if (overboughtCondition && isBearishCandle) {
            val entryPrice = currentQuote.bid
            val stopLoss = entryPrice + adaptive.slDistance
            val takeProfit = bbMiddle
            val tpDistance = abs(entryPrice - takeProfit)
            val riskReward = if (adaptive.slDistance > 0) tpDistance / adaptive.slDistance else 0.0

            return Signal(
                id = java.util.UUID.randomUUID().toString(),
                symbol = symbolConfig.symbol,
                direction = TradeDirection.SELL,
                price = entryPrice,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                rrRatio = riskReward,
                candleTime = lastClosedCandle.openTime,
                timestamp = System.currentTimeMillis(),
                explanation = SignalExplanation(
                    symbol = symbolConfig.symbol,
                    direction = TradeDirection.SELL,
                    emaFast = emaFast, emaSlow = emaSlow, adx = adx, atr = atr,
                    trendCheck = false, adxCheck = adx < 25.0, pullbackCheck = false, candleCheck = true,
                    spreadCheck = spreadCheck, riskCheck = riskCheck, sessionCheck = sessionCheck,
                    decision = "SELL",
                    reason = "RSI($rsi) > ${strategyConfig.rsiOverbought}, Close > BB Upper($bbUpper), ADX($adx) < 25, bearish reversal candle [Adaptive SL: ${"%.1f".format(adaptive.slDistance)}]"
                )
            )
        }

        return null
    }

    private fun evaluateMomentum(
        closedCandles: List<Candle>,
        lastClosedCandle: Candle,
        prevClosedCandle: Candle,
        indicators: IndicatorValues,
        prevIndicators: IndicatorValues?,
        symbolConfig: SymbolConfig,
        currentQuote: Quote,
        riskCheck: Boolean,
        sessionCheck: Boolean,
        spreadCheck: Boolean,
        atr: Double
    ): Signal? {
        val emaFast = indicators.emaFast
        val emaSlow = indicators.emaSlow
        val adx = indicators.adx
        val macdHistogram = indicators.macdHistogram

        if (adx < strategyConfig.momentumAdxThreshold) return null

        val prevMacdHistogram = prevIndicators?.macdHistogram

        val isBullishCandle = lastClosedCandle.close > lastClosedCandle.open
        val isBearishCandle = lastClosedCandle.close < lastClosedCandle.open

        val bullishMomentum = macdHistogram > 0 && prevMacdHistogram != null && macdHistogram > prevMacdHistogram
        val bearishMomentum = macdHistogram < 0 && prevMacdHistogram != null && macdHistogram < prevMacdHistogram

        val bullishTrend = emaFast > emaSlow && lastClosedCandle.close > emaFast
        val bearishTrend = emaFast < emaSlow && lastClosedCandle.close < emaFast

        val adaptive = AdaptiveCalculator.computeAll(
            atr = atr, adx = adx, adxThreshold = strategyConfig.adxThreshold,
            baseSlMultiplier = strategyConfig.atrSlMultiplier, baseRiskReward = strategyConfig.riskRewardRatio,
            baseBeTriggerR = strategyConfig.breakEvenTriggerR, baseBeBufferPips = strategyConfig.breakEvenBufferPips,
            baseTrailingDistanceAtr = strategyConfig.trailingStopDistanceAtr,
            minimumStopDistance = symbolConfig.minimumStopDistance, tickSize = symbolConfig.tickSize,
            adaptiveSlEnabled = strategyConfig.adaptiveSlEnabled, adaptiveTpEnabled = strategyConfig.adaptiveTpEnabled,
            adaptiveBeEnabled = strategyConfig.adaptiveBeEnabled
        )

        if (bullishMomentum && bullishTrend && isBullishCandle) {
            val entryPrice = currentQuote.ask
            val stopLoss = entryPrice - adaptive.slDistance
            val takeProfit = entryPrice + adaptive.tpDistance
            val riskReward = if (adaptive.slDistance > 0) adaptive.tpDistance / adaptive.slDistance else 0.0

            return Signal(
                id = java.util.UUID.randomUUID().toString(),
                symbol = symbolConfig.symbol,
                direction = TradeDirection.BUY,
                price = entryPrice,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                rrRatio = riskReward,
                candleTime = lastClosedCandle.openTime,
                timestamp = System.currentTimeMillis(),
                explanation = SignalExplanation(
                    symbol = symbolConfig.symbol,
                    direction = TradeDirection.BUY,
                    emaFast = emaFast, emaSlow = emaSlow, adx = adx, atr = atr,
                    trendCheck = true, adxCheck = true, pullbackCheck = false, candleCheck = true,
                    spreadCheck = spreadCheck, riskCheck = riskCheck, sessionCheck = sessionCheck,
                    decision = "BUY",
                    reason = "MACD histogram positive($macdHistogram) and increasing, ADX($adx) >= ${strategyConfig.momentumAdxThreshold}, bullish trend, bullish candle [Adaptive SL: ${"%.1f".format(adaptive.slDistance)}, TP: ${"%.1f".format(adaptive.tpDistance)}]"
                )
            )
        }

        if (bearishMomentum && bearishTrend && isBearishCandle) {
            val entryPrice = currentQuote.bid
            val stopLoss = entryPrice + adaptive.slDistance
            val takeProfit = entryPrice - adaptive.tpDistance
            val riskReward = if (adaptive.slDistance > 0) adaptive.tpDistance / adaptive.slDistance else 0.0

            return Signal(
                id = java.util.UUID.randomUUID().toString(),
                symbol = symbolConfig.symbol,
                direction = TradeDirection.SELL,
                price = entryPrice,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                rrRatio = riskReward,
                candleTime = lastClosedCandle.openTime,
                timestamp = System.currentTimeMillis(),
                explanation = SignalExplanation(
                    symbol = symbolConfig.symbol,
                    direction = TradeDirection.SELL,
                    emaFast = emaFast, emaSlow = emaSlow, adx = adx, atr = atr,
                    trendCheck = true, adxCheck = true, pullbackCheck = false, candleCheck = true,
                    spreadCheck = spreadCheck, riskCheck = riskCheck, sessionCheck = sessionCheck,
                    decision = "SELL",
                    reason = "MACD histogram negative($macdHistogram) and decreasing, ADX($adx) >= ${strategyConfig.momentumAdxThreshold}, bearish trend, bearish candle [Adaptive SL: ${"%.1f".format(adaptive.slDistance)}, TP: ${"%.1f".format(adaptive.tpDistance)}]"
                )
            )
        }

        return null
    }

    private fun evaluateRangeTrading(
        closedCandles: List<Candle>,
        lastClosedCandle: Candle,
        prevClosedCandle: Candle,
        indicators: IndicatorValues,
        symbolConfig: SymbolConfig,
        currentQuote: Quote,
        riskCheck: Boolean,
        sessionCheck: Boolean,
        spreadCheck: Boolean,
        atr: Double
    ): Signal? {
        val emaFast = indicators.emaFast
        val emaSlow = indicators.emaSlow
        val adx = indicators.adx
        val stochasticK = indicators.stochK

        if (adx >= strategyConfig.rangeAdxMax) return null

        val lookback = min(strategyConfig.rangeLookbackPeriod, closedCandles.size - 1)
        val lookbackCandles = closedCandles.dropLast(1).takeLast(lookback)
        if (lookbackCandles.size < strategyConfig.rangeLookbackPeriod) return null

        val rangeHigh = lookbackCandles.maxOf { it.high }
        val rangeLow = lookbackCandles.minOf { it.low }
        val rangeSize = rangeHigh - rangeLow

        if (rangeSize >= atr * 3) return null

        val nearRangeLow = lastClosedCandle.low <= rangeLow + atr * 0.3
        val nearRangeHigh = lastClosedCandle.high >= rangeHigh - atr * 0.3

        val isBullishCandle = lastClosedCandle.close > lastClosedCandle.open
        val isBearishCandle = lastClosedCandle.close < lastClosedCandle.open

        val adaptive = AdaptiveCalculator.computeAll(
            atr = atr, adx = adx, adxThreshold = strategyConfig.adxThreshold,
            baseSlMultiplier = 1.0, baseRiskReward = strategyConfig.riskRewardRatio,
            baseBeTriggerR = strategyConfig.breakEvenTriggerR, baseBeBufferPips = strategyConfig.breakEvenBufferPips,
            baseTrailingDistanceAtr = strategyConfig.trailingStopDistanceAtr,
            minimumStopDistance = symbolConfig.minimumStopDistance, tickSize = symbolConfig.tickSize,
            adaptiveSlEnabled = strategyConfig.adaptiveSlEnabled, adaptiveTpEnabled = false,
            adaptiveBeEnabled = strategyConfig.adaptiveBeEnabled
        )

        if (nearRangeLow && stochasticK < 30 && isBullishCandle) {
            val entryPrice = currentQuote.ask
            val stopLoss = entryPrice - adaptive.slDistance
            val rangeMidpoint = (rangeHigh + rangeLow) / 2.0
            val takeProfit = min(rangeMidpoint, entryPrice + adaptive.slDistance * strategyConfig.riskRewardRatio)
            val tpDistance = abs(takeProfit - entryPrice)
            val riskReward = if (adaptive.slDistance > 0) tpDistance / adaptive.slDistance else 0.0

            return Signal(
                id = java.util.UUID.randomUUID().toString(),
                symbol = symbolConfig.symbol,
                direction = TradeDirection.BUY,
                price = entryPrice,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                rrRatio = riskReward,
                candleTime = lastClosedCandle.openTime,
                timestamp = System.currentTimeMillis(),
                explanation = SignalExplanation(
                    symbol = symbolConfig.symbol,
                    direction = TradeDirection.BUY,
                    emaFast = emaFast, emaSlow = emaSlow, adx = adx, atr = atr,
                    trendCheck = false, adxCheck = adx < strategyConfig.rangeAdxMax, pullbackCheck = false, candleCheck = true,
                    spreadCheck = spreadCheck, riskCheck = riskCheck, sessionCheck = sessionCheck,
                    decision = "BUY",
                    reason = "Range detected ($rangeLow-$$rangeHigh), ADX($adx) < ${strategyConfig.rangeAdxMax}, near range low, stochastic($stochasticK) < 30, bullish candle [Adaptive SL: ${"%.1f".format(adaptive.slDistance)}]"
                )
            )
        }

        if (nearRangeHigh && stochasticK > 70 && isBearishCandle) {
            val entryPrice = currentQuote.bid
            val stopLoss = entryPrice + adaptive.slDistance
            val rangeMidpoint = (rangeHigh + rangeLow) / 2.0
            val takeProfit = max(rangeMidpoint, entryPrice - adaptive.slDistance * strategyConfig.riskRewardRatio)
            val tpDistance = abs(entryPrice - takeProfit)
            val riskReward = if (adaptive.slDistance > 0) tpDistance / adaptive.slDistance else 0.0

            return Signal(
                id = java.util.UUID.randomUUID().toString(),
                symbol = symbolConfig.symbol,
                direction = TradeDirection.SELL,
                price = entryPrice,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                rrRatio = riskReward,
                candleTime = lastClosedCandle.openTime,
                timestamp = System.currentTimeMillis(),
                explanation = SignalExplanation(
                    symbol = symbolConfig.symbol,
                    direction = TradeDirection.SELL,
                    emaFast = emaFast, emaSlow = emaSlow, adx = adx, atr = atr,
                    trendCheck = false, adxCheck = adx < strategyConfig.rangeAdxMax, pullbackCheck = false, candleCheck = true,
                    spreadCheck = spreadCheck, riskCheck = riskCheck, sessionCheck = sessionCheck,
                    decision = "SELL",
                    reason = "Range detected ($rangeLow-$$rangeHigh), ADX($adx) < ${strategyConfig.rangeAdxMax}, near range high, stochastic($stochasticK) > 70, bearish candle [Adaptive SL: ${"%.1f".format(adaptive.slDistance)}]"
                )
            )
        }

        return null
    }

    private fun evaluateScalping(
        closedCandles: List<Candle>,
        lastClosedCandle: Candle,
        prevClosedCandle: Candle,
        indicators: IndicatorValues,
        prevIndicators: IndicatorValues?,
        symbolConfig: SymbolConfig,
        currentQuote: Quote,
        riskCheck: Boolean,
        sessionCheck: Boolean,
        spreadCheck: Boolean,
        atr: Double
    ): Signal? {
        val emaFast = indicators.emaFast
        val emaSlow = indicators.emaSlow
        val stochasticK = indicators.stochK
        val stochasticD = indicators.stochD

        val prevStochasticK = prevIndicators?.stochK
        val prevStochasticD = prevIndicators?.stochD

        if (prevStochasticK == null || prevStochasticD == null) return null

        val bodySize = abs(lastClosedCandle.close - lastClosedCandle.open)
        val isStrongBullish = lastClosedCandle.close > lastClosedCandle.open && bodySize > atr * 0.5
        val isStrongBearish = lastClosedCandle.close < lastClosedCandle.open && bodySize > atr * 0.5

        val bullishCross = stochasticK > stochasticD && prevStochasticK <= prevStochasticD && prevStochasticD < 50
        val bearishCross = stochasticK < stochasticD && prevStochasticK >= prevStochasticD && prevStochasticD > 50

        if (isStrongBullish && bullishCross && lastClosedCandle.close > emaFast && emaFast > emaSlow) {
            val entryPrice = currentQuote.ask
            val slDistance = max(atr * 0.8, symbolConfig.minimumStopDistance)
            val stopLoss = entryPrice - slDistance
            val takeProfit = entryPrice + slDistance * strategyConfig.scalpMinRr
            val riskReward = (takeProfit - entryPrice) / slDistance

            return Signal(
                id = UUID.randomUUID().toString(),
                symbol = symbolConfig.symbol,
                direction = TradeDirection.BUY,
                price = entryPrice,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                rrRatio = riskReward,
                candleTime = lastClosedCandle.openTime,
                timestamp = System.currentTimeMillis(),
                explanation = SignalExplanation(
                    symbol = symbolConfig.symbol,
                    direction = TradeDirection.BUY,
                    emaFast = emaFast,
                    emaSlow = emaSlow,
                    adx = indicators.adx,
                    atr = atr,
                    trendCheck = true,
                    adxCheck = false,
                    pullbackCheck = false,
                    candleCheck = true,
                    spreadCheck = spreadCheck,
                    riskCheck = riskCheck,
                    sessionCheck = sessionCheck,
                    decision = "BUY",
                    reason = "Strong bullish candle (body $bodySize > ATR*0.5 ${atr * 0.5}), stochastic K($stochasticK) crosses above D($stochasticD), price > EMA Fast, bullish trend"
                )
            )
        }

        if (isStrongBearish && bearishCross && lastClosedCandle.close < emaFast && emaFast < emaSlow) {
            val entryPrice = currentQuote.bid
            val slDistance = max(atr * 0.8, symbolConfig.minimumStopDistance)
            val stopLoss = entryPrice + slDistance
            val takeProfit = entryPrice - slDistance * strategyConfig.scalpMinRr
            val riskReward = (entryPrice - takeProfit) / slDistance

            return Signal(
                id = UUID.randomUUID().toString(),
                symbol = symbolConfig.symbol,
                direction = TradeDirection.SELL,
                price = entryPrice,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                rrRatio = riskReward,
                candleTime = lastClosedCandle.openTime,
                timestamp = System.currentTimeMillis(),
                explanation = SignalExplanation(
                    symbol = symbolConfig.symbol,
                    direction = TradeDirection.SELL,
                    emaFast = emaFast,
                    emaSlow = emaSlow,
                    adx = indicators.adx,
                    atr = atr,
                    trendCheck = true,
                    adxCheck = false,
                    pullbackCheck = false,
                    candleCheck = true,
                    spreadCheck = spreadCheck,
                    riskCheck = riskCheck,
                    sessionCheck = sessionCheck,
                    decision = "SELL",
                    reason = "Strong bearish candle (body $bodySize > ATR*0.5 ${atr * 0.5}), stochastic K($stochasticK) crosses below D($stochasticD), price < EMA Fast, bearish trend"
                )
            )
        }

        return null
    }

    data class EarlyExitDecision(
        val shouldExit: Boolean,
        val reason: String
    )

    fun checkTrendReversalExit(
        position: Position,
        currentQuote: Quote,
        candles: List<Candle>,
        symbolConfig: SymbolConfig
    ): EarlyExitDecision {
        if (!strategyConfig.earlyExitOnTrendReversal) {
            return EarlyExitDecision(false, "")
        }
        val closedCandles = candles.filter { it.isClosed }
        if (closedCandles.size < 20) return EarlyExitDecision(false, "")

        val lastCandle = closedCandles.last()
        val prevCandle = closedCandles[closedCandles.size - 2]
        val indicators = IndicatorCalculator.computeLatest(
            candles = closedCandles,
            fastEmaPeriod = strategyConfig.emaFastPeriod,
            slowEmaPeriod = strategyConfig.emaSlowPeriod,
            adxPeriod = strategyConfig.adxPeriod,
            atrPeriod = strategyConfig.atrPeriod,
            rsiPeriod = strategyConfig.rsiPeriod
        ) ?: return EarlyExitDecision(false, "")

        val emaFast = indicators.emaFast
        val emaSlow = indicators.emaSlow
        val rsi = indicators.rsi
        val atr = indicators.atr

        val markPrice = if (position.direction == TradeDirection.BUY) currentQuote.bid else currentQuote.ask
        val priceDiff = if (position.direction == TradeDirection.BUY) markPrice - position.entryPrice else position.entryPrice - markPrice
        val riskDist = abs(position.entryPrice - position.stopLoss)
        val unrealizedR = if (riskDist > 0) priceDiff / riskDist else 0.0

        if (position.direction == TradeDirection.BUY) {
            val emaFlippedBearish = emaFast < emaSlow && lastCandle.close < emaFast
            val bearishEngulfing = lastCandle.close < prevCandle.low && lastCandle.open > prevCandle.close && lastCandle.close < emaFast
            val rsiExhaustion = rsi < 45.0 && prevCandle.close > lastCandle.close && (lastCandle.open - lastCandle.close) > atr * 0.75

            if (emaFlippedBearish) {
                return EarlyExitDecision(true, "Trend Reversal: EMA Fast (${SymbolCatalog.formatPrice(position.symbol, emaFast)}) crossed below EMA Slow. Protecting at ${unrealizedR.formatR()}R.")
            }
            if (unrealizedR > 0.3 && bearishEngulfing) {
                return EarlyExitDecision(true, "Bearish Engulfing candle formed against Long. Protecting gains at ${unrealizedR.formatR()}R.")
            }
            if (unrealizedR > 0.5 && rsiExhaustion) {
                return EarlyExitDecision(true, "Momentum Exhaustion: Sharp RSI drop against Long position.")
            }
        } else {
            val emaFlippedBullish = emaFast > emaSlow && lastCandle.close > emaFast
            val bullishEngulfing = lastCandle.close > prevCandle.high && lastCandle.open < prevCandle.close && lastCandle.close > emaFast
            val rsiBounce = rsi > 55.0 && lastCandle.close > prevCandle.close && (lastCandle.close - lastCandle.open) > atr * 0.75

            if (emaFlippedBullish) {
                return EarlyExitDecision(true, "Trend Reversal: EMA Fast (${SymbolCatalog.formatPrice(position.symbol, emaFast)}) crossed above EMA Slow. Protecting at ${unrealizedR.formatR()}R.")
            }
            if (unrealizedR > 0.3 && bullishEngulfing) {
                return EarlyExitDecision(true, "Bullish Engulfing candle formed against Short. Protecting gains at ${unrealizedR.formatR()}R.")
            }
            if (unrealizedR > 0.5 && rsiBounce) {
                return EarlyExitDecision(true, "Momentum Exhaustion: Sharp RSI bounce against Short position.")
            }
        }

        return EarlyExitDecision(false, "")
    }

    private fun Double.formatR(): String = "%.2f".format(Locale.US, this)

    private fun isSessionAllowed(openTime: Long): Boolean {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(strategyConfig.timezone))
        calendar.timeInMillis = openTime
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        return hour >= strategyConfig.sessionStartHour && hour < strategyConfig.sessionEndHour
    }
}

interface NewsFilter {
    fun isNewsFreeWindow(timestamp: Long, symbol: String): Boolean
}

class NoNewsFilter : NewsFilter {
    override fun isNewsFreeWindow(timestamp: Long, symbol: String): Boolean = true
}
