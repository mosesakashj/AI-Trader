package com.example.domain.backtest

import com.example.domain.model.*
import timber.log.Timber
import kotlin.math.*

data class CostModel(
    val spreadPips: Double = 1.5,
    val commissionPerLot: Double = 7.0,
    val swapPerNight: Double = -3.5,
    val slippagePips: Double = 0.5,
    val variableSpreadEnabled: Boolean = true
)

data class RollingWalkForwardResult(
    val windowResults: List<WalkForwardWindow>,
    val averageRobustnessScore: Double,
    val minRobustnessScore: Double,
    val maxRobustnessScore: Double,
    val parameterStability: Double,
    val recommendedParameters: StrategyConfig
)

data class WalkForwardWindow(
    val windowIndex: Int,
    val inSampleStart: Int,
    val inSampleEnd: Int,
    val outOfSampleStart: Int,
    val outOfSampleEnd: Int,
    val optimizedParams: StrategyConfig,
    val inSampleMetrics: BacktestResult,
    val outOfSampleMetrics: BacktestResult,
    val robustnessScore: Double
)

data class BayesianOptimizationResult(
    val bestParams: StrategyConfig,
    val bestScore: Double,
    val iterations: List<OptimizationIteration>,
    val convergencePlot: List<Pair<Int, Double>>
)

data class OptimizationIteration(
    val iteration: Int,
    val params: StrategyConfig,
    val score: Double,
    val explored: Boolean
)

class EnhancedBacktestingEngine {

    private val costModel = CostModel()

    fun calculateSlippage(symbol: String, atr: Double, volume: Double): Double {
        val baseSlippage = costModel.slippagePips
        val volatilityFactor = (atr / 10.0).coerceIn(0.5, 3.0)
        val volumeFactor = (volume / 0.1).coerceIn(1.0, 2.0)
        return baseSlippage * volatilityFactor * volumeFactor
    }

    fun calculateSpread(symbol: String, timeframe: Timeframe, hourOfDay: Int): Double {
        if (!costModel.variableSpreadEnabled) return costModel.spreadPips

        val baseSpread = costModel.spreadPips
        val sessionFactor = when {
            hourOfDay in 7..10 -> 1.2
            hourOfDay in 13..16 -> 1.0
            hourOfDay in 21..23 -> 1.5
            else -> 2.0
        }

        val timeframeFactor = when (timeframe) {
            Timeframe.M1 -> 1.5
            Timeframe.M5 -> 1.3
            Timeframe.M15 -> 1.1
            Timeframe.M30 -> 1.0
            Timeframe.H1 -> 1.0
            Timeframe.H4 -> 0.9
            Timeframe.D1 -> 0.8
        }

        return baseSpread * sessionFactor * timeframeFactor
    }

    fun calculateSwap(symbol: String, direction: TradeDirection, daysHeld: Int): Double {
        val swapPerDay = when {
            symbol.contains("JPY") -> if (direction == TradeDirection.BUY) -2.5 else 1.2
            symbol.contains("USD") -> if (direction == TradeDirection.BUY) -3.5 else 1.8
            symbol == "XAUUSD" -> if (direction == TradeDirection.BUY) -45.0 else 12.0
            else -> costModel.swapPerNight
        }
        return swapPerDay * daysHeld
    }

    fun runRollingWalkForward(
        candles: List<Candle>,
        symbolConfig: SymbolConfig,
        strategyConfig: StrategyConfig,
        windowSize: Int = 200,
        stepSize: Int = 50,
        inSampleRatio: Double = 0.6,
        validationRatio: Double = 0.2
    ): RollingWalkForwardResult {
        val windows = mutableListOf<WalkForwardWindow>()
        var windowIndex = 0

        var start = 0
        while (start + windowSize <= candles.size) {
            val windowCandles = candles.subList(start, start + windowSize)
            val inSampleSize = (windowSize * inSampleRatio).toInt()
            val validationSize = (windowSize * validationRatio).toInt()

            val inSampleCandles = windowCandles.take(inSampleSize)
            val validationCandles = windowCandles.drop(inSampleSize).take(validationSize)
            val outOfSampleCandles = windowCandles.drop(inSampleSize + validationSize)

            if (outOfSampleCandles.isEmpty()) {
                start += stepSize
                continue
            }

            val optimizedParams = optimizeOnWindow(inSampleCandles, symbolConfig, strategyConfig)

            val isResult = runBacktestOnCandles(inSampleCandles, symbolConfig, optimizedParams)
            val oosResult = runBacktestOnCandles(outOfSampleCandles, symbolConfig, optimizedParams)

            val robustness = if (isResult.profitFactor > 0) {
                min(1.0, oosResult.profitFactor / isResult.profitFactor) * 100.0
            } else 0.0

            windows.add(
                WalkForwardWindow(
                    windowIndex = windowIndex++,
                    inSampleStart = start,
                    inSampleEnd = start + inSampleSize,
                    outOfSampleStart = start + inSampleSize + validationSize,
                    outOfSampleEnd = start + windowSize,
                    optimizedParams = optimizedParams,
                    inSampleMetrics = isResult,
                    outOfSampleMetrics = oosResult,
                    robustnessScore = robustness
                )
            )

            start += stepSize
        }

        val robustnessScores = windows.map { it.robustnessScore }
        val avgRobustness = if (robustnessScores.isNotEmpty()) robustnessScores.average() else 0.0
        val minRobustness = robustnessScores.minOrNull() ?: 0.0
        val maxRobustness = robustnessScores.maxOrNull() ?: 0.0

        val paramStability = calculateParameterStability(windows)
        val recommendedParams = selectBestParameters(windows)

        return RollingWalkForwardResult(
            windowResults = windows,
            averageRobustnessScore = avgRobustness,
            minRobustnessScore = minRobustness,
            maxRobustnessScore = maxRobustness,
            parameterStability = paramStability,
            recommendedParameters = recommendedParams
        )
    }

    private fun optimizeOnWindow(
        candles: List<Candle>,
        symbolConfig: SymbolConfig,
        baseConfig: StrategyConfig
    ): StrategyConfig {
        val emaFastRange = listOf(10, 15, 20, 25, 30)
        val emaSlowRange = listOf(40, 50, 60, 80)
        val adxRange = listOf(20, 25, 30)
        val rrRange = listOf(1.5, 2.0, 2.5)

        var bestScore = Double.NEGATIVE_INFINITY
        var bestConfig = baseConfig

        for (fast in emaFastRange) {
            for (slow in emaSlowRange) {
                for (adx in adxRange) {
                    for (rr in rrRange) {
                        if (fast >= slow) continue

                        val testConfig = baseConfig.copy(
                            emaFastPeriod = fast,
                            emaSlowPeriod = slow,
                            adxThreshold = adx.toDouble(),
                            riskRewardRatio = rr
                        )

                        val result = runBacktestOnCandles(candles, symbolConfig, testConfig)
                        val score = calculateOptimizationScore(result)

                        if (score > bestScore) {
                            bestScore = score
                            bestConfig = testConfig
                        }
                    }
                }
            }
        }

        return bestConfig
    }

    private fun runBacktestOnCandles(
        candles: List<Candle>,
        symbolConfig: SymbolConfig,
        config: StrategyConfig
    ): BacktestResult {
        var equity = 10000.0
        var peakEquity = equity
        var maxDrawdown = 0.0
        val trades = mutableListOf<Trade>()
        var position: Position? = null

        for (i in 35 until candles.size) {
            val window = candles.subList(0, i + 1)

            if (position != null) {
                val currentPrice = window.last().close
                val priceDiff = if (position.direction == TradeDirection.BUY) {
                    currentPrice - position.entryPrice
                } else {
                    position.entryPrice - currentPrice
                }

                val slippage = calculateSlippage(symbolConfig.symbol, 10.0, position.volume)
                val spread = calculateSpread(symbolConfig.symbol, config.strategyType.let { Timeframe.M15 }, 12) * symbolConfig.tickSize
                val totalCost = (spread + slippage * symbolConfig.tickSize) * position.volume

                if (position.direction == TradeDirection.BUY && currentPrice >= position.takeProfit) {
                    val profit = (position.takeProfit - position.entryPrice) * position.volume * symbolConfig.contractSize - totalCost
                    equity += profit
                    trades.add(createClosedTrade(position, currentPrice, profit, CloseReason.TAKE_PROFIT))
                    position = null
                } else if (position.direction == TradeDirection.SELL && currentPrice <= position.takeProfit) {
                    val profit = (position.entryPrice - position.takeProfit) * position.volume * symbolConfig.contractSize - totalCost
                    equity += profit
                    trades.add(createClosedTrade(position, currentPrice, profit, CloseReason.TAKE_PROFIT))
                    position = null
                } else if (position.direction == TradeDirection.BUY && currentPrice <= position.stopLoss) {
                    val loss = (position.stopLoss - position.entryPrice) * position.volume * symbolConfig.contractSize - totalCost
                    equity += loss
                    trades.add(createClosedTrade(position, currentPrice, loss, CloseReason.STOP_LOSS))
                    position = null
                } else if (position.direction == TradeDirection.SELL && currentPrice >= position.stopLoss) {
                    val loss = (position.entryPrice - position.stopLoss) * position.volume * symbolConfig.contractSize - totalCost
                    equity += loss
                    trades.add(createClosedTrade(position, currentPrice, loss, CloseReason.STOP_LOSS))
                    position = null
                }

                if (equity > peakEquity) peakEquity = equity
                val dd = ((peakEquity - equity) / peakEquity * 100.0)
                if (dd > maxDrawdown) maxDrawdown = dd
            }

            if (position == null && i + 1 < candles.size) {
                val signal = generateSimpleSignal(window, config, symbolConfig)
                if (signal != null) {
                    val volume = (equity * 0.0025) / (abs(signal.price - signal.stopLoss) * symbolConfig.contractSize)
                    val normalizedVol = (volume * 100).toInt() / 100.0
                    if (normalizedVol >= symbolConfig.minLot) {
                        position = Position(
                            id = "bt_${i}",
                            symbol = symbolConfig.symbol,
                            direction = signal.direction,
                            volume = normalizedVol,
                            entryPrice = signal.price,
                            currentPrice = signal.price,
                            stopLoss = signal.stopLoss,
                            takeProfit = signal.takeProfit,
                            unrealizedProfit = 0.0,
                            unrealizedR = 0.0,
                            openedAt = System.currentTimeMillis()
                        )
                    }
                }
            }
        }

        val wins = trades.filter { it.profit > 0 }
        val losses = trades.filter { it.profit <= 0 }
        val winRate = if (trades.isNotEmpty()) wins.size.toDouble() / trades.size * 100.0 else 0.0
        val totalProfit = trades.sumOf { it.profit }
        val grossProfit = wins.sumOf { it.profit }
        val grossLoss = abs(losses.sumOf { it.profit })
        val profitFactor = if (grossLoss > 0) grossProfit / grossLoss else if (grossProfit > 0) Double.MAX_VALUE else 0.0

        return BacktestResult(
            symbol = symbolConfig.symbol,
            timeframe = Timeframe.M15,
            candleCount = candles.size,
            totalTrades = trades.size,
            winningTrades = wins.size,
            losingTrades = losses.size,
            winRate = winRate,
            totalProfitLoss = totalProfit,
            profitFactor = profitFactor,
            sharpeRatio = calculateSharpe(trades),
            maxDrawdownAmount = peakEquity - (peakEquity * (1 - maxDrawdown / 100.0)),
            maxDrawdownPercent = maxDrawdown,
            averageR = calculateAverageR(trades),
            expectancy = if (trades.isNotEmpty()) totalProfit / trades.size else 0.0,
            maxConsecutiveLosses = calculateMaxConsecutiveLosses(trades),
            recoveryFactor = if (maxDrawdown > 0) totalProfit / (peakEquity * maxDrawdown / 100.0) else 0.0,
            trades = trades,
            equityCurve = buildEquityCurve(trades, 10000.0)
        )
    }

    private fun generateSimpleSignal(candles: List<Candle>, config: StrategyConfig, symbolConfig: SymbolConfig): Signal? {
        if (candles.size < 50) return null
        val last = candles.last()
        val closes = candles.map { it.close }
        val ema20 = calculateEMA(closes, 20)
        val ema50 = calculateEMA(closes, 50)

        if (ema20 > ema50 && last.close > ema20) {
            val atr = calculateATR(candles, 14)
            return Signal(
                id = "bt_sig_${System.currentTimeMillis()}",
                symbol = symbolConfig.symbol,
                direction = TradeDirection.BUY,
                price = last.close,
                stopLoss = last.close - atr * config.atrSlMultiplier,
                takeProfit = last.close + atr * config.riskRewardRatio,
                rrRatio = config.riskRewardRatio,
                candleTime = last.openTime,
                explanation = SignalExplanation(
                    symbol = symbolConfig.symbol,
                    direction = TradeDirection.BUY,
                    emaFast = ema20,
                    emaSlow = ema50,
                    adx = 25.0,
                    atr = atr,
                    trendCheck = true,
                    adxCheck = true,
                    pullbackCheck = true,
                    candleCheck = true,
                    spreadCheck = true,
                    riskCheck = true,
                    sessionCheck = true,
                    decision = "PULLBACK",
                    reason = "EMA cross + price above"
                )
            )
        }
        return null
    }

    private fun calculateEMA(data: List<Double>, period: Int): Double {
        if (data.size < period) return data.average()
        val multiplier = 2.0 / (period + 1)
        var ema = data.take(period).average()
        for (i in period until data.size) {
            ema = (data[i] - ema) * multiplier + ema
        }
        return ema
    }

    private fun calculateATR(candles: List<Candle>, period: Int): Double {
        if (candles.size < period + 1) return candles.last().high - candles.last().low
        val trs = candles.takeLast(period + 1).windowed(2).map { (prev, curr) ->
            maxOf(curr.high - curr.low, abs(curr.high - prev.close), abs(curr.low - prev.close))
        }
        return trs.average()
    }

    private fun calculateOptimizationScore(result: BacktestResult): Double {
        if (result.totalTrades < 5) return Double.NEGATIVE_INFINITY
        return (result.sharpeRatio * 0.4) +
            (result.profitFactor * 0.3) +
            (result.winRate / 100.0 * 0.2) +
            (1.0 - result.maxDrawdownPercent / 100.0) * 0.1
    }

    private fun calculateSharpe(trades: List<Trade>): Double {
        if (trades.size < 2) return 0.0
        val returns = trades.map { it.profit / 10000.0 }
        val meanReturn = returns.average()
        val stdDev = sqrt(returns.map { (it - meanReturn).pow(2) }.average())
        return if (stdDev > 0) meanReturn / stdDev * sqrt(252.0) else 0.0
    }

    private fun calculateAverageR(trades: List<Trade>): Double {
        if (trades.isEmpty()) return 0.0
        return trades.map { it.profitR }.average()
    }

    private fun calculateMaxConsecutiveLosses(trades: List<Trade>): Int {
        var maxConsecutive = 0
        var currentConsecutive = 0
        for (trade in trades) {
            if (trade.profit <= 0) {
                currentConsecutive++
                if (currentConsecutive > maxConsecutive) maxConsecutive = currentConsecutive
            } else {
                currentConsecutive = 0
            }
        }
        return maxConsecutive
    }

    private fun buildEquityCurve(trades: List<Trade>, initialEquity: Double): List<Pair<Long, Double>> {
        val curve = mutableListOf<Pair<Long, Double>>()
        var equity = initialEquity
        curve.add(0L to equity)
        for (trade in trades) {
            equity += trade.profit
            curve.add(trade.closedAt ?: trade.openedAt to equity)
        }
        return curve
    }

    private fun createClosedTrade(position: Position, closePrice: Double, profit: Double, reason: CloseReason): Trade {
        val rr = if (position.direction == TradeDirection.BUY) {
            (closePrice - position.entryPrice) / abs(position.entryPrice - position.stopLoss)
        } else {
            (position.entryPrice - closePrice) / abs(position.entryPrice - position.stopLoss)
        }
        return Trade(
            id = position.id,
            symbol = position.symbol,
            direction = position.direction,
            volume = position.volume,
            entryPrice = position.entryPrice,
            stopLoss = position.stopLoss,
            takeProfit = position.takeProfit,
            riskAmount = abs(position.entryPrice - position.stopLoss) * position.volume,
            riskPercent = 0.25,
            rr = rr,
            openedAt = position.openedAt,
            closedAt = System.currentTimeMillis(),
            closePrice = closePrice,
            profit = profit,
            profitR = rr,
            status = TradeStatus.CLOSED,
            closeReason = reason
        )
    }

    private fun calculateParameterStability(windows: List<WalkForwardWindow>): Double {
        if (windows.size < 2) return 1.0
        val emaFastValues = windows.map { it.optimizedParams.emaFastPeriod }
        val emaSlowValues = windows.map { it.optimizedParams.emaSlowPeriod }
        val adxValues = windows.map { it.optimizedParams.adxThreshold }

        val emaFastVariance = emaFastValues.variance()
        val emaSlowVariance = emaSlowValues.variance()
        val adxVariance = adxValues.variance()

        val avgVariance = (emaFastVariance + emaSlowVariance + adxVariance) / 3.0
        return (1.0 - avgVariance / 100.0).coerceIn(0.0, 1.0)
    }

    private fun selectBestParameters(windows: List<WalkForwardWindow>): StrategyConfig {
        if (windows.isEmpty()) return StrategyConfig()
        return windows.maxByOrNull { it.robustnessScore }?.optimizedParams ?: StrategyConfig()
    }

    private fun List<Double>.variance(): Double {
        if (size < 2) return 0.0
        val mean = average()
        return map { (it - mean).pow(2) }.average()
    }

    private fun Double.pow(n: Int): Double = kotlin.math.pow(n.toDouble())
}
