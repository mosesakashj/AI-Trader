package com.example.domain.strategy

import com.example.domain.indicators.IndicatorCalculator
import com.example.domain.model.*
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class TradingStrategy(
    private val strategyConfig: StrategyConfig = StrategyConfig(),
    private val newsFilter: NewsFilter = NoNewsFilter()
) {

    /**
     * Evaluates closed candle history and generates deterministic Signal or explanation.
     * Prevents look-ahead bias by strictly evaluating closed candles.
     */
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
        if (candles.size < max(strategyConfig.emaSlowPeriod + 10, strategyConfig.adxPeriod * 2)) {
            return null
        }

        // Get closed candles (ensure we ignore forming/unclosed candles)
        val closedCandles = candles.filter { it.isClosed }
        if (closedCandles.size < strategyConfig.emaSlowPeriod + 2) return null

        val lastClosedCandle = closedCandles.last()
        val prevClosedCandle = closedCandles[closedCandles.size - 2]

        // Duplicate candle prevention
        if (lastClosedCandle.openTime <= lastProcessedCandleTime) {
            return null
        }

        val indicators = IndicatorCalculator.computeLatest(
            candles = closedCandles,
            fastEmaPeriod = strategyConfig.emaFastPeriod,
            slowEmaPeriod = strategyConfig.emaSlowPeriod,
            adxPeriod = strategyConfig.adxPeriod,
            atrPeriod = strategyConfig.atrPeriod
        ) ?: return null

        val emaFast = indicators.emaFast
        val emaSlow = indicators.emaSlow
        val adx = indicators.adx
        val atr = indicators.atr

        // 1. Session check
        val sessionCheck = isSessionAllowed(lastClosedCandle.openTime)

        // 2. Spread check
        val currentSpreadPips = (currentQuote.spread / symbolConfig.tickSize) * (symbolConfig.tickSize / 0.0001)
        val spreadCheck = currentQuote.spread <= symbolConfig.spreadLimit

        // 3. Volatility check (ATR bounds)
        val atrCheck = atr >= symbolConfig.minimumAtr && atr <= symbolConfig.maximumAtr

        // 4. ADX trend strength check
        val adxCheck = adx >= strategyConfig.adxThreshold

        // 5. Risk & system health checks
        val riskCheck = !dailyLossReached && !consecutiveLossesReached && marginSufficient && !hasOpenPosition &&
                !riskConfig.emergencyStopActive && !riskConfig.safeModeActive && isConnectionHealthy

        // Check BUY candidate
        val isBullishTrend = emaFast > emaSlow
        val isBullishCandle = lastClosedCandle.close > lastClosedCandle.open
        val buyPullback = prevClosedCandle.low <= (emaFast + (atr * 0.5)) || lastClosedCandle.low <= (emaFast + (atr * 0.5))
        val buyNotExtended = abs(lastClosedCandle.close - emaFast) <= (atr * strategyConfig.maxCandleExtensionAtr)

        val buyExplanation = SignalExplanation(
            symbol = symbolConfig.symbol,
            direction = TradeDirection.BUY,
            emaFast = emaFast,
            emaSlow = emaSlow,
            adx = adx,
            atr = atr,
            trendCheck = isBullishTrend,
            adxCheck = adxCheck,
            pullbackCheck = buyPullback && buyNotExtended && atrCheck,
            candleCheck = isBullishCandle,
            spreadCheck = spreadCheck,
            riskCheck = riskCheck,
            sessionCheck = sessionCheck,
            decision = if (isBullishTrend && adxCheck && buyPullback && buyNotExtended && isBullishCandle && atrCheck && spreadCheck && riskCheck && sessionCheck) "BUY" else "REJECT",
            reason = when {
                !isBullishTrend -> "EMA20 not above EMA50"
                !adxCheck -> "ADX ($adx) below threshold ${strategyConfig.adxThreshold}"
                !atrCheck -> "ATR ($atr) outside volatility range"
                !buyPullback -> "No valid pullback to EMA band"
                !buyNotExtended -> "Price overextended from EMA20"
                !isBullishCandle -> "Closed candle not bullish"
                !spreadCheck -> "Spread exceeds limit"
                !sessionCheck -> "Outside allowed trading session"
                !riskCheck -> "Risk limit / position / emergency active"
                else -> "All entry criteria satisfied"
            }
        )

        if (buyExplanation.isAllPassed) {
            val entryPrice = currentQuote.ask
            val rawSlDistance = atr * strategyConfig.atrSlMultiplier
            val minStopDistance = symbolConfig.minimumStopDistance
            val slDistance = max(rawSlDistance, minStopDistance)
            val stopLoss = entryPrice - slDistance
            val riskDistance = entryPrice - stopLoss
            val takeProfit = entryPrice + (riskDistance * strategyConfig.riskRewardRatio)

            return Signal(
                id = UUID.randomUUID().toString(),
                symbol = symbolConfig.symbol,
                direction = TradeDirection.BUY,
                price = entryPrice,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                rrRatio = strategyConfig.riskRewardRatio,
                candleTime = lastClosedCandle.openTime,
                explanation = buyExplanation,
                strategyVersion = strategyConfig.strategyVersion
            )
        }

        // Check SELL candidate
        val isBearishTrend = emaFast < emaSlow
        val isBearishCandle = lastClosedCandle.close < lastClosedCandle.open
        val sellPullback = prevClosedCandle.high >= (emaFast - (atr * 0.5)) || lastClosedCandle.high >= (emaFast - (atr * 0.5))
        val sellNotExtended = abs(lastClosedCandle.close - emaFast) <= (atr * strategyConfig.maxCandleExtensionAtr)

        val sellExplanation = SignalExplanation(
            symbol = symbolConfig.symbol,
            direction = TradeDirection.SELL,
            emaFast = emaFast,
            emaSlow = emaSlow,
            adx = adx,
            atr = atr,
            trendCheck = isBearishTrend,
            adxCheck = adxCheck,
            pullbackCheck = sellPullback && sellNotExtended && atrCheck,
            candleCheck = isBearishCandle,
            spreadCheck = spreadCheck,
            riskCheck = riskCheck,
            sessionCheck = sessionCheck,
            decision = if (isBearishTrend && adxCheck && sellPullback && sellNotExtended && isBearishCandle && atrCheck && spreadCheck && riskCheck && sessionCheck) "SELL" else "REJECT",
            reason = when {
                !isBearishTrend -> "EMA20 not below EMA50"
                !adxCheck -> "ADX ($adx) below threshold ${strategyConfig.adxThreshold}"
                !atrCheck -> "ATR ($atr) outside volatility range"
                !sellPullback -> "No valid pullback to EMA band"
                !sellNotExtended -> "Price overextended from EMA20"
                !isBearishCandle -> "Closed candle not bearish"
                !spreadCheck -> "Spread exceeds limit"
                !sessionCheck -> "Outside allowed trading session"
                !riskCheck -> "Risk limit / position / emergency active"
                else -> "All entry criteria satisfied"
            }
        )

        if (sellExplanation.isAllPassed) {
            val entryPrice = currentQuote.bid
            val rawSlDistance = atr * strategyConfig.atrSlMultiplier
            val minStopDistance = symbolConfig.minimumStopDistance
            val slDistance = max(rawSlDistance, minStopDistance)
            val stopLoss = entryPrice + slDistance
            val riskDistance = stopLoss - entryPrice
            val takeProfit = entryPrice - (riskDistance * strategyConfig.riskRewardRatio)

            return Signal(
                id = UUID.randomUUID().toString(),
                symbol = symbolConfig.symbol,
                direction = TradeDirection.SELL,
                price = entryPrice,
                stopLoss = stopLoss,
                takeProfit = takeProfit,
                rrRatio = strategyConfig.riskRewardRatio,
                candleTime = lastClosedCandle.openTime,
                explanation = sellExplanation,
                strategyVersion = strategyConfig.strategyVersion
            )
        }

        return null
    }

    private fun isSessionAllowed(timestamp: Long): Boolean {
        if (strategyConfig.sessionStartHour == 0 && strategyConfig.sessionEndHour >= 24) {
            return true
        }
        val cal = Calendar.getInstance(TimeZone.getTimeZone(strategyConfig.timezone))
        cal.timeInMillis = timestamp
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour in strategyConfig.sessionStartHour until strategyConfig.sessionEndHour
    }
}
