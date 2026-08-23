package com.example.domain.strategy

import com.example.domain.indicators.IndicatorCalculator
import com.example.domain.model.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class MarketStructurePlan(
    val positionId: String,
    val symbol: String,
    val direction: TradeDirection,
    val structurePhase: String,
    val trendHealth: String,
    val recentSwingHigh: Double,
    val recentSwingLow: Double,
    val nearestSupport: Double,
    val nearestResistance: Double,
    val invalidationLevel: Double,
    val continuousAction: ContinuousActionType,
    val actionAdvice: String,
    val target1: Double,
    val target2: Double,
    val recommendedStopLoss: Double,
    val fvgZone: String? = null,
    val bosDetected: Boolean = false,
    val chochDetected: Boolean = false,
    val confidence: Double = 0.88,
    val lastUpdated: Long = System.currentTimeMillis()
)

enum class ContinuousActionType(val label: String, val badgeColorType: String) {
    RIDE_RUNWAY("Strong Runway - Ride to TP", "EMERALD"),
    LOCK_BREAK_EVEN("Secure Break-Even", "CYAN"),
    TIGHTEN_SL_SWING("Ratchet SL to Swing", "PRIMARY"),
    APPROACHING_RESISTANCE("Impending Resistance - Take Partial", "AMBER"),
    STRUCTURE_WEAKENING("Structure Weakening - Tighten Stop", "AMBER"),
    CHOCH_REVERSAL_WARNING("Reversal Warning - Prepare Exit", "CRIMSON"),
    INVALIDATION_EXIT("Structural Invalidation - Close Position", "CRIMSON")
}

object MarketStructureMonitor {

    fun analyzePositionStructure(
        position: Position,
        currentQuote: Quote,
        candles: List<Candle>,
        symbolConfig: SymbolConfig
    ): MarketStructurePlan {
        val closedCandles = candles.filter { it.isClosed }
        val markPrice = if (position.direction == TradeDirection.BUY) currentQuote.bid else currentQuote.ask
        val tickSize = symbolConfig.tickSize
        val priceDiff = if (position.direction == TradeDirection.BUY) markPrice - position.entryPrice else position.entryPrice - markPrice
        val riskDist = abs(position.entryPrice - position.stopLoss)
        val unrealizedR = if (riskDist > 0) priceDiff / riskDist else 0.0

        if (closedCandles.size < 15) {
            // Basic fallback if history is small
            return MarketStructurePlan(
                positionId = position.id,
                symbol = position.symbol,
                direction = position.direction,
                structurePhase = "Evaluating Micro-Structure",
                trendHealth = "Initializing",
                recentSwingHigh = markPrice + (tickSize * 50),
                recentSwingLow = markPrice - (tickSize * 50),
                nearestSupport = position.stopLoss,
                nearestResistance = position.takeProfit,
                invalidationLevel = position.stopLoss,
                continuousAction = ContinuousActionType.RIDE_RUNWAY,
                actionAdvice = "Maintain current risk parameters as candle history builds.",
                target1 = position.takeProfit,
                target2 = position.takeProfit,
                recommendedStopLoss = position.stopLoss
            )
        }

        val lastCandle = closedCandles.last()
        val prevCandle = closedCandles[closedCandles.size - 2]
        val recentLookback = closedCandles.takeLast(min(30, closedCandles.size))

        // Find swing highs and swing lows
        val swingHighs = mutableListOf<Double>()
        val swingLows = mutableListOf<Double>()

        for (i in 2 until recentLookback.size - 2) {
            val c = recentLookback[i]
            val isHigh = c.high > recentLookback[i - 1].high && c.high > recentLookback[i - 2].high &&
                    c.high > recentLookback[i + 1].high && c.high > recentLookback[i + 2].high
            val isLow = c.low < recentLookback[i - 1].low && c.low < recentLookback[i - 2].low &&
                    c.low < recentLookback[i + 1].low && c.low < recentLookback[i + 2].low

            if (isHigh) swingHighs.add(c.high)
            if (isLow) swingLows.add(c.low)
        }

        val recentSwingHigh = swingHighs.lastOrNull() ?: recentLookback.maxOf { it.high }
        val recentSwingLow = swingLows.lastOrNull() ?: recentLookback.minOf { it.low }

        val indicators = IndicatorCalculator.computeLatest(recentLookback)
        val atr = indicators?.atr ?: (tickSize * 20.0)
        val emaFast = indicators?.emaFast ?: markPrice
        val emaSlow = indicators?.emaSlow ?: markPrice
        val rsi = indicators?.rsi ?: 50.0
        val adx = indicators?.adx ?: 25.0

        // FVG (Fair Value Gap) Check on last 3 candles
        var fvgZone: String? = null
        if (closedCandles.size >= 3) {
            val c1 = closedCandles[closedCandles.size - 3]
            val c3 = closedCandles.last()
            if (c3.low > c1.high) {
                fvgZone = "Bullish FVG: [${SymbolCatalog.formatPrice(position.symbol, c1.high)} - ${SymbolCatalog.formatPrice(position.symbol, c3.low)}]"
            } else if (c3.high < c1.low) {
                fvgZone = "Bearish FVG: [${SymbolCatalog.formatPrice(position.symbol, c3.high)} - ${SymbolCatalog.formatPrice(position.symbol, c1.low)}]"
            }
        }

        // BOS and CHoCH detection
        val bullishBos = lastCandle.close > recentSwingHigh
        val bearishBos = lastCandle.close < recentSwingLow
        val bullishChoch = position.direction == TradeDirection.SELL && lastCandle.close > recentSwingHigh
        val bearishChoch = position.direction == TradeDirection.BUY && lastCandle.close < recentSwingLow

        val isBullishStructure = emaFast >= emaSlow && lastCandle.close >= emaFast
        val isBearishStructure = emaFast <= emaSlow && lastCandle.close <= emaFast

        // Targets & Supports
        val nearestSupport = recentSwingLow.coerceAtMost(markPrice - (atr * 0.5))
        val nearestResistance = recentSwingHigh.coerceAtLeast(markPrice + (atr * 0.5))

        val target1: Double
        val target2: Double
        val invalidationLevel: Double
        val recommendedStopLoss: Double
        val structurePhase: String
        val trendHealth: String
        val continuousAction: ContinuousActionType
        val actionAdvice: String

        if (position.direction == TradeDirection.BUY) {
            invalidationLevel = nearestSupport - (tickSize * 10.0)
            target1 = nearestResistance
            target2 = nearestResistance + (atr * 1.5)

            val isApproachingResistance = markPrice >= nearestResistance - (atr * 0.25)
            val isStructureBroken = markPrice <= invalidationLevel || bearishChoch
            val isEmaFlipped = emaFast < emaSlow && lastCandle.close < emaFast

            if (isStructureBroken) {
                structurePhase = "CHoCH Bearish Invalidation"
                trendHealth = "Compromised / Reversal"
                continuousAction = ContinuousActionType.INVALIDATION_EXIT
                actionAdvice = "Price invalidated structural higher low at ${SymbolCatalog.formatPrice(position.symbol, invalidationLevel)}. Immediate capital protection advised."
                recommendedStopLoss = markPrice
            } else if (isEmaFlipped) {
                structurePhase = "Bearish Momentum Deceleration"
                trendHealth = "Weakening"
                continuousAction = ContinuousActionType.CHOCH_REVERSAL_WARNING
                actionAdvice = "EMA Fast crossed below EMA Slow. Tighten Stop Loss to +${"%.2f".format(unrealizedR)}R or secure Break-Even."
                recommendedStopLoss = max(position.stopLoss, position.entryPrice)
            } else if (isApproachingResistance && unrealizedR >= 1.0) {
                structurePhase = "Supply Zone / Resistance Test"
                trendHealth = "Strong but Extended (RSI ${rsi.toInt()})"
                continuousAction = ContinuousActionType.APPROACHING_RESISTANCE
                actionAdvice = "Approaching major resistance ${SymbolCatalog.formatPrice(position.symbol, nearestResistance)}. Secure 50% partials or trail SL behind swing low."
                recommendedStopLoss = max(position.stopLoss, markPrice - atr)
            } else if (unrealizedR >= 0.8 && position.stopLoss < position.entryPrice) {
                structurePhase = "Bullish Expansion (Unrealized +${"%.2f".format(unrealizedR)}R)"
                trendHealth = if (adx > 25) "Strong Trend" else "Moderate Drift"
                continuousAction = ContinuousActionType.LOCK_BREAK_EVEN
                actionAdvice = "Trade in profit (+${"%.2f".format(unrealizedR)}R). Ratchet Stop Loss to Entry + Buffer to guarantee risk-free hold."
                recommendedStopLoss = position.entryPrice + (tickSize * 15.0)
            } else if (unrealizedR >= 1.2) {
                structurePhase = "Bullish Trend Continuation (BOS Confirmed)"
                trendHealth = "Strong Impulse"
                continuousAction = ContinuousActionType.TIGHTEN_SL_SWING
                actionAdvice = "Trailing behind micro swing low ${SymbolCatalog.formatPrice(position.symbol, nearestSupport)}. Letting profits run."
                recommendedStopLoss = max(position.stopLoss, nearestSupport)
            } else {
                structurePhase = if (isBullishStructure) "Bullish Trend Building" else "Consolidation / Pullback"
                trendHealth = if (adx > 20) "Healthy" else "Low Momentum"
                continuousAction = ContinuousActionType.RIDE_RUNWAY
                actionAdvice = "Holding planned position toward Target 1 (${SymbolCatalog.formatPrice(position.symbol, target1)}). Invalidation intact at ${SymbolCatalog.formatPrice(position.symbol, invalidationLevel)}."
                recommendedStopLoss = position.stopLoss
            }
        } else {
            // SELL position
            invalidationLevel = nearestResistance + (tickSize * 10.0)
            target1 = nearestSupport
            target2 = nearestSupport - (atr * 1.5)

            val isApproachingSupport = markPrice <= nearestSupport + (atr * 0.25)
            val isStructureBroken = markPrice >= invalidationLevel || bullishChoch
            val isEmaFlipped = emaFast > emaSlow && lastCandle.close > emaFast

            if (isStructureBroken) {
                structurePhase = "CHoCH Bullish Invalidation"
                trendHealth = "Compromised / Reversal"
                continuousAction = ContinuousActionType.INVALIDATION_EXIT
                actionAdvice = "Price broke above structural lower high at ${SymbolCatalog.formatPrice(position.symbol, invalidationLevel)}. Immediate capital protection advised."
                recommendedStopLoss = markPrice
            } else if (isEmaFlipped) {
                structurePhase = "Bullish Momentum Bounce"
                trendHealth = "Weakening"
                continuousAction = ContinuousActionType.CHOCH_REVERSAL_WARNING
                actionAdvice = "EMA Fast crossed above EMA Slow against Short. Tighten Stop Loss to +${"%.2f".format(unrealizedR)}R or secure Break-Even."
                recommendedStopLoss = min(position.stopLoss, position.entryPrice)
            } else if (isApproachingSupport && unrealizedR >= 1.0) {
                structurePhase = "Demand Zone / Support Test"
                trendHealth = "Strong Bearish Extension (RSI ${rsi.toInt()})"
                continuousAction = ContinuousActionType.APPROACHING_RESISTANCE
                actionAdvice = "Approaching major support ${SymbolCatalog.formatPrice(position.symbol, nearestSupport)}. Secure 50% partials or trail SL behind swing high."
                recommendedStopLoss = min(position.stopLoss, markPrice + atr)
            } else if (unrealizedR >= 0.8 && position.stopLoss > position.entryPrice) {
                structurePhase = "Bearish Expansion (Unrealized +${"%.2f".format(unrealizedR)}R)"
                trendHealth = if (adx > 25) "Strong Trend" else "Moderate Drift"
                continuousAction = ContinuousActionType.LOCK_BREAK_EVEN
                actionAdvice = "Trade in profit (+${"%.2f".format(unrealizedR)}R). Ratchet Stop Loss to Entry - Buffer to guarantee risk-free hold."
                recommendedStopLoss = position.entryPrice - (tickSize * 15.0)
            } else if (unrealizedR >= 1.2) {
                structurePhase = "Bearish Trend Continuation (BOS Confirmed)"
                trendHealth = "Strong Impulse"
                continuousAction = ContinuousActionType.TIGHTEN_SL_SWING
                actionAdvice = "Trailing behind micro swing high ${SymbolCatalog.formatPrice(position.symbol, nearestResistance)}. Letting profits run."
                recommendedStopLoss = min(position.stopLoss, nearestResistance)
            } else {
                structurePhase = if (isBearishStructure) "Bearish Trend Building" else "Consolidation / Pullback"
                trendHealth = if (adx > 20) "Healthy" else "Low Momentum"
                continuousAction = ContinuousActionType.RIDE_RUNWAY
                actionAdvice = "Holding planned position toward Target 1 (${SymbolCatalog.formatPrice(position.symbol, target1)}). Invalidation intact at ${SymbolCatalog.formatPrice(position.symbol, invalidationLevel)}."
                recommendedStopLoss = position.stopLoss
            }
        }

        return MarketStructurePlan(
            positionId = position.id,
            symbol = position.symbol,
            direction = position.direction,
            structurePhase = structurePhase,
            trendHealth = trendHealth,
            recentSwingHigh = recentSwingHigh,
            recentSwingLow = recentSwingLow,
            nearestSupport = nearestSupport,
            nearestResistance = nearestResistance,
            invalidationLevel = invalidationLevel,
            continuousAction = continuousAction,
            actionAdvice = actionAdvice,
            target1 = target1,
            target2 = target2,
            recommendedStopLoss = recommendedStopLoss,
            fvgZone = fvgZone,
            bosDetected = bullishBos || bearishBos,
            chochDetected = bullishChoch || bearishChoch,
            confidence = (0.75 + (if (adx > 25) 0.1 else 0.0) + (if (unrealizedR > 0.5) 0.05 else 0.0)).coerceAtMost(0.96)
        )
    }
}
