package com.example.domain.backtest

import com.example.domain.indicators.IndicatorCalculator
import com.example.domain.model.*
import com.example.domain.risk.RiskManager
import com.example.domain.strategy.StrategyConfig
import com.example.domain.strategy.TradingStrategy
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class BacktestResult(
    val symbol: String,
    val timeframe: Timeframe,
    val candleCount: Int,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRate: Double,
    val totalProfitLoss: Double,
    val profitFactor: Double,
    val sharpeRatio: Double,
    val recoveryFactor: Double,
    val maxDrawdownAmount: Double,
    val maxDrawdownPercent: Double,
    val averageR: Double,
    val expectancy: Double,
    val maxConsecutiveLosses: Int,
    val maxConsecutiveWins: Int,
    val averageWin: Double,
    val averageLoss: Double,
    val trades: List<Trade>,
    val equityCurve: List<Pair<Long, Double>>
)

data class PortfolioBacktestResult(
    val symbols: List<String>,
    val timeframe: Timeframe,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRate: Double,
    val totalProfitLoss: Double,
    val profitFactor: Double,
    val sharpeRatio: Double,
    val maxDrawdownPercent: Double,
    val recoveryFactor: Double,
    val tradesBySymbol: Map<String, List<Trade>>,
    val allTrades: List<Trade>,
    val equityCurve: List<Pair<Long, Double>>
)

data class OptimizationResult(
    val fastEma: Int,
    val slowEma: Int,
    val adxThreshold: Double,
    val atrMultiplier: Double,
    val rrRatio: Double,
    val totalTrades: Int,
    val winRate: Double,
    val totalProfitLoss: Double,
    val profitFactor: Double,
    val sharpeRatio: Double,
    val maxDrawdownPercent: Double
)

data class WalkForwardResult(
    val inSampleResult: BacktestResult,
    val validationResult: BacktestResult,
    val outOfSampleResult: BacktestResult,
    val robustnessScore: Double
)

class BacktestingEngine(
    private var strategy: TradingStrategy = TradingStrategy(),
    private val riskManager: RiskManager = RiskManager()
) {

    /**
     * Executes backtest strictly step-by-step on historical candles with Auto Position Management (BE & Trailing).
     */
    fun runBacktest(
        candles: List<Candle>,
        symbolConfig: SymbolConfig,
        initialBalance: Double = 10000.0,
        riskPercent: Double = 0.25,
        customStrategy: TradingStrategy? = null,
        enableAutoBreakEven: Boolean = true,
        enableAutoTrailing: Boolean = true
    ): BacktestResult {
        if (candles.size < 40) {
            return emptyResult(symbolConfig.symbol, candles.firstOrNull()?.timeframe ?: Timeframe.M15)
        }

        val activeStrategy = customStrategy ?: strategy
        var balance = initialBalance
        var peakBalance = initialBalance
        var maxDrawdownAmount = 0.0
        var maxDrawdownPercent = 0.0

        val trades = mutableListOf<Trade>()
        val equityCurve = mutableListOf<Pair<Long, Double>>()
        equityCurve.add(candles.first().openTime to balance)

        var openTrade: Trade? = null
        var lastProcessedCandleTime = 0L

        val tickSize = symbolConfig.tickSize
        val tickValue = symbolConfig.tickValue

        for (i in 35 until candles.size) {
            val currentCandle = candles[i]
            val pastCandles = candles.subList(0, i)

            // 1. Manage open position if any
            if (openTrade != null) {
                var t = openTrade!!
                var isClosed = false
                var exitPrice = 0.0
                var reason = CloseReason.EXPIRED

                // Auto-Trailing and Auto-BreakEven calculation during candle evolution
                val ind = IndicatorCalculator.computeLatest(pastCandles)
                val atr = ind?.atr ?: (tickSize * 20.0)

                val markPrice = currentCandle.close
                val priceDiff = if (t.direction == TradeDirection.BUY) markPrice - t.entryPrice else t.entryPrice - markPrice
                val riskDist = abs(t.entryPrice - t.stopLoss)
                val unrealizedR = if (riskDist > 0) priceDiff / riskDist else 0.0

                // 1a. Auto Break-Even (+1.0R achieved)
                if (enableAutoBreakEven && unrealizedR >= 1.0) {
                    if (t.direction == TradeDirection.BUY && t.stopLoss < t.entryPrice) {
                        t = t.copy(stopLoss = t.entryPrice + (symbolConfig.spreadLimit * 0.2))
                    } else if (t.direction == TradeDirection.SELL && t.stopLoss > t.entryPrice) {
                        t = t.copy(stopLoss = t.entryPrice - (symbolConfig.spreadLimit * 0.2))
                    }
                }

                // 1b. Auto Trailing Stop (+1.5R achieved)
                if (enableAutoTrailing && unrealizedR >= 1.5) {
                    val trailDist = atr * 1.2
                    if (t.direction == TradeDirection.BUY) {
                        val candidate = currentCandle.high - trailDist
                        if (candidate > t.stopLoss) {
                            t = t.copy(stopLoss = candidate)
                        }
                    } else {
                        val candidate = currentCandle.low + trailDist
                        if (candidate < t.stopLoss) {
                            t = t.copy(stopLoss = candidate)
                        }
                    }
                }
                openTrade = t

                // Check candle SL/TP hits
                if (t.direction == TradeDirection.BUY) {
                    if (currentCandle.low <= t.stopLoss) {
                        isClosed = true
                        exitPrice = t.stopLoss
                        reason = if (t.stopLoss >= t.entryPrice) CloseReason.TAKE_PROFIT else CloseReason.STOP_LOSS
                    } else if (currentCandle.high >= t.takeProfit) {
                        isClosed = true
                        exitPrice = t.takeProfit
                        reason = CloseReason.TAKE_PROFIT
                    }
                } else {
                    if (currentCandle.high >= t.stopLoss) {
                        isClosed = true
                        exitPrice = t.stopLoss
                        reason = if (t.stopLoss <= t.entryPrice) CloseReason.TAKE_PROFIT else CloseReason.STOP_LOSS
                    } else if (currentCandle.low <= t.takeProfit) {
                        isClosed = true
                        exitPrice = t.takeProfit
                        reason = CloseReason.TAKE_PROFIT
                    }
                }

                if (isClosed) {
                    val finalDiff = if (t.direction == TradeDirection.BUY) exitPrice - t.entryPrice else t.entryPrice - exitPrice
                    val ticks = finalDiff / tickSize
                    val profit = ticks * tickValue * t.volume
                    val profitR = if (riskDist > 0) finalDiff / riskDist else 0.0

                    balance += profit
                    peakBalance = max(peakBalance, balance)
                    val dd = peakBalance - balance
                    val ddPct = if (peakBalance > 0) (dd / peakBalance) * 100.0 else 0.0
                    maxDrawdownAmount = max(maxDrawdownAmount, dd)
                    maxDrawdownPercent = max(maxDrawdownPercent, ddPct)

                    trades.add(
                        t.copy(
                            closedAt = currentCandle.openTime,
                            closePrice = exitPrice,
                            profit = profit,
                            profitR = profitR,
                            status = TradeStatus.CLOSED,
                            closeReason = reason
                        )
                    )
                    openTrade = null
                    equityCurve.add(currentCandle.openTime to balance)
                }
            }

            // 2. Generate new signal if flat
            if (openTrade == null) {
                val mockQuote = Quote(
                    symbol = symbolConfig.symbol,
                    bid = currentCandle.close - (symbolConfig.spreadLimit / 2.0),
                    ask = currentCandle.close + (symbolConfig.spreadLimit / 2.0),
                    timestamp = currentCandle.openTime
                )

                val signal = activeStrategy.evaluate(
                    candles = pastCandles,
                    symbolConfig = symbolConfig,
                    currentQuote = mockQuote,
                    riskConfig = RiskConfig(defaultRiskPercent = riskPercent),
                    hasOpenPosition = false,
                    lastProcessedCandleTime = lastProcessedCandleTime,
                    isConnectionHealthy = true,
                    dailyLossReached = false,
                    consecutiveLossesReached = false,
                    marginSufficient = true
                )

                if (signal != null) {
                    lastProcessedCandleTime = signal.candleTime
                    val volume = riskManager.calculatePositionSize(
                        equity = balance,
                        riskPercent = riskPercent,
                        entryPrice = signal.price,
                        stopLossPrice = signal.stopLoss,
                        symbolConfig = symbolConfig
                    )

                    if (volume >= symbolConfig.minLot) {
                        openTrade = Trade(
                            id = UUID.randomUUID().toString(),
                            symbol = symbolConfig.symbol,
                            direction = signal.direction,
                            volume = volume,
                            entryPrice = signal.price,
                            stopLoss = signal.stopLoss,
                            takeProfit = signal.takeProfit,
                            riskAmount = balance * (riskPercent / 100.0),
                            riskPercent = riskPercent,
                            rr = signal.rrRatio,
                            openedAt = currentCandle.openTime,
                            status = TradeStatus.OPEN
                        )
                    }
                }
            }
        }

        // Close any remaining position at end of dataset
        openTrade?.let { t ->
            val lastCandle = candles.last()
            val exitPrice = lastCandle.close
            val priceDiff = if (t.direction == TradeDirection.BUY) exitPrice - t.entryPrice else t.entryPrice - exitPrice
            val ticks = priceDiff / tickSize
            val profit = ticks * tickValue * t.volume
            val riskDist = abs(t.entryPrice - t.stopLoss)
            val profitR = if (riskDist > 0) priceDiff / riskDist else 0.0

            balance += profit
            trades.add(
                t.copy(
                    closedAt = lastCandle.openTime,
                    closePrice = exitPrice,
                    profit = profit,
                    profitR = profitR,
                    status = TradeStatus.CLOSED,
                    closeReason = CloseReason.EXPIRED
                )
            )
            equityCurve.add(lastCandle.openTime to balance)
        }

        return calculateMetrics(
            symbol = symbolConfig.symbol,
            timeframe = candles.first().timeframe,
            candleCount = candles.size,
            initialBalance = initialBalance,
            finalBalance = balance,
            maxDrawdownAmount = maxDrawdownAmount,
            maxDrawdownPercent = maxDrawdownPercent,
            trades = trades,
            equityCurve = equityCurve
        )
    }

    /**
     * Executes a combined Portfolio Backtest across multiple symbols simultaneously.
     */
    fun runPortfolioBacktest(
        candlesBySymbol: Map<String, List<Candle>>,
        configs: List<SymbolConfig>,
        initialBalance: Double = 10000.0,
        riskPercent: Double = 0.25
    ): PortfolioBacktestResult {
        val tradesBySymbol = mutableMapOf<String, List<Trade>>()
        val allTrades = mutableListOf<Trade>()

        var balance = initialBalance
        var peakBalance = initialBalance
        var maxDrawdownPct = 0.0
        val equityCurve = mutableListOf<Pair<Long, Double>>()
        equityCurve.add(System.currentTimeMillis() - 86400000L to balance)

        configs.forEach { config ->
            val candles = candlesBySymbol[config.symbol] ?: emptyList()
            if (candles.isNotEmpty()) {
                val res = runBacktest(candles, config, initialBalance, riskPercent)
                tradesBySymbol[config.symbol] = res.trades
                allTrades.addAll(res.trades)
            }
        }

        allTrades.sortBy { it.openedAt }
        allTrades.forEach { trade ->
            balance += trade.profit
            peakBalance = max(peakBalance, balance)
            val dd = peakBalance - balance
            val ddPct = if (peakBalance > 0) (dd / peakBalance) * 100.0 else 0.0
            maxDrawdownPct = max(maxDrawdownPct, ddPct)
            equityCurve.add((trade.closedAt ?: trade.openedAt) to balance)
        }

        val totalTrades = allTrades.size
        val winningTrades = allTrades.count { it.profit > 0 }
        val losingTrades = allTrades.count { it.profit <= 0 }
        val winRate = if (totalTrades > 0) (winningTrades.toDouble() / totalTrades) * 100.0 else 0.0
        val grossProfit = allTrades.filter { it.profit > 0 }.sumOf { it.profit }
        val grossLoss = abs(allTrades.filter { it.profit < 0 }.sumOf { it.profit })
        val profitFactor = if (grossLoss > 0) grossProfit / grossLoss else if (grossProfit > 0) 9.99 else 0.0
        val totalPnl = balance - initialBalance
        val recoveryFactor = if (maxDrawdownPct > 0) (totalPnl / (initialBalance * (maxDrawdownPct / 100.0))) else 0.0

        val returns = allTrades.map { it.profit / initialBalance }
        val avgReturn = if (returns.isNotEmpty()) returns.average() else 0.0
        val stdDev = if (returns.size > 1) {
            val variance = returns.map { (it - avgReturn) * (it - avgReturn) }.average()
            sqrt(variance)
        } else 0.0
        val sharpe = if (stdDev > 0) (avgReturn / stdDev) * sqrt(252.0) else 0.0

        return PortfolioBacktestResult(
            symbols = configs.map { it.symbol },
            timeframe = Timeframe.M15,
            totalTrades = totalTrades,
            winningTrades = winningTrades,
            losingTrades = losingTrades,
            winRate = winRate,
            totalProfitLoss = totalPnl,
            profitFactor = profitFactor,
            sharpeRatio = sharpe,
            maxDrawdownPercent = maxDrawdownPct,
            recoveryFactor = recoveryFactor,
            tradesBySymbol = tradesBySymbol,
            allTrades = allTrades,
            equityCurve = equityCurve
        )
    }

    /**
     * Automated Parameter Optimization Grid Search.
     */
    fun runParameterOptimization(
        candles: List<Candle>,
        symbolConfig: SymbolConfig
    ): List<OptimizationResult> {
        val results = mutableListOf<OptimizationResult>()
        val fastEmas = listOf(10, 20, 30)
        val slowEmas = listOf(30, 50, 100)
        val adxLevels = listOf(20.0, 25.0)
        val atrMultipliers = listOf(1.2, 1.5, 2.0)
        val rrRatios = listOf(1.5, 2.0, 2.5)

        for (fast in fastEmas) {
            for (slow in slowEmas) {
                if (fast >= slow) continue
                for (adx in adxLevels) {
                    for (atrMult in atrMultipliers) {
                        for (rr in rrRatios) {
                            val customStrat = TradingStrategy(
                                StrategyConfig(
                                    emaFastPeriod = fast,
                                    emaSlowPeriod = slow,
                                    adxThreshold = adx,
                                    atrSlMultiplier = atrMult,
                                    riskRewardRatio = rr
                                )
                            )
                            val res = runBacktest(candles, symbolConfig, customStrategy = customStrat)
                            if (res.totalTrades >= 3) {
                                results.add(
                                    OptimizationResult(
                                        fastEma = fast,
                                        slowEma = slow,
                                        adxThreshold = adx,
                                        atrMultiplier = atrMult,
                                        rrRatio = rr,
                                        totalTrades = res.totalTrades,
                                        winRate = res.winRate,
                                        totalProfitLoss = res.totalProfitLoss,
                                        profitFactor = res.profitFactor,
                                        sharpeRatio = res.sharpeRatio,
                                        maxDrawdownPercent = res.maxDrawdownPercent
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sort by best Sharpe Ratio and Profit Factor
        return results.sortedWith(compareByDescending<OptimizationResult> { it.sharpeRatio }.thenByDescending { it.profitFactor })
    }

    /**
     * Walk-forward testing (In-sample 60%, Validation 20%, Out-of-sample 20%).
     */
    fun runWalkForward(
        candles: List<Candle>,
        symbolConfig: SymbolConfig
    ): WalkForwardResult {
        val n = candles.size
        val inSampleEnd = (n * 0.6).toInt()
        val valEnd = (n * 0.8).toInt()

        val inSampleCandles = candles.subList(0, inSampleEnd)
        val valCandles = candles.subList(inSampleEnd, valEnd)
        val oosCandles = candles.subList(valEnd, n)

        val inSample = runBacktest(inSampleCandles, symbolConfig)
        val validation = runBacktest(valCandles, symbolConfig)
        val oos = runBacktest(oosCandles, symbolConfig)

        val robustness = if (inSample.profitFactor > 0 && oos.profitFactor > 0) {
            min(1.0, oos.profitFactor / inSample.profitFactor) * 100.0
        } else {
            0.0
        }

        return WalkForwardResult(
            inSampleResult = inSample,
            validationResult = validation,
            outOfSampleResult = oos,
            robustnessScore = robustness
        )
    }

    private fun calculateMetrics(
        symbol: String,
        timeframe: Timeframe,
        candleCount: Int,
        initialBalance: Double,
        finalBalance: Double,
        maxDrawdownAmount: Double,
        maxDrawdownPercent: Double,
        trades: List<Trade>,
        equityCurve: List<Pair<Long, Double>>
    ): BacktestResult {
        val totalTrades = trades.size
        val winningTrades = trades.count { it.profit > 0 }
        val losingTrades = trades.count { it.profit <= 0 }
        val winRate = if (totalTrades > 0) (winningTrades.toDouble() / totalTrades) * 100.0 else 0.0

        val grossProfit = trades.filter { it.profit > 0 }.sumOf { it.profit }
        val grossLoss = abs(trades.filter { it.profit < 0 }.sumOf { it.profit })
        val profitFactor = if (grossLoss > 0) grossProfit / grossLoss else if (grossProfit > 0) 9.99 else 0.0

        val totalPnl = finalBalance - initialBalance
        val avgR = if (totalTrades > 0) trades.sumOf { it.profitR } / totalTrades else 0.0
        val expectancy = if (totalTrades > 0) totalPnl / totalTrades else 0.0

        val recoveryFactor = if (maxDrawdownAmount > 0) totalPnl / maxDrawdownAmount else if (totalPnl > 0) 9.99 else 0.0

        val returns = trades.map { it.profit / initialBalance }
        val avgReturn = if (returns.isNotEmpty()) returns.average() else 0.0
        val stdDev = if (returns.size > 1) {
            val variance = returns.map { (it - avgReturn) * (it - avgReturn) }.average()
            sqrt(variance)
        } else 0.0
        val sharpe = if (stdDev > 0) (avgReturn / stdDev) * sqrt(252.0) else 0.0

        val wins = trades.filter { it.profit > 0 }
        val losses = trades.filter { it.profit < 0 }
        val avgWin = if (wins.isNotEmpty()) wins.sumOf { it.profit } / wins.size else 0.0
        val avgLoss = if (losses.isNotEmpty()) losses.sumOf { it.profit } / losses.size else 0.0

        var maxConsecLoss = 0
        var currentConsecLoss = 0
        var maxConsecWin = 0
        var currentConsecWin = 0

        trades.forEach { t ->
            if (t.profit <= 0) {
                currentConsecLoss++
                maxConsecLoss = max(maxConsecLoss, currentConsecLoss)
                currentConsecWin = 0
            } else {
                currentConsecWin++
                maxConsecWin = max(maxConsecWin, currentConsecWin)
                currentConsecLoss = 0
            }
        }

        return BacktestResult(
            symbol = symbol,
            timeframe = timeframe,
            candleCount = candleCount,
            totalTrades = totalTrades,
            winningTrades = winningTrades,
            losingTrades = losingTrades,
            winRate = winRate,
            totalProfitLoss = totalPnl,
            profitFactor = profitFactor,
            sharpeRatio = sharpe,
            recoveryFactor = recoveryFactor,
            maxDrawdownAmount = maxDrawdownAmount,
            maxDrawdownPercent = maxDrawdownPercent,
            averageR = avgR,
            expectancy = expectancy,
            maxConsecutiveLosses = maxConsecLoss,
            maxConsecutiveWins = maxConsecWin,
            averageWin = avgWin,
            averageLoss = avgLoss,
            trades = trades,
            equityCurve = equityCurve
        )
    }

    private fun emptyResult(symbol: String, timeframe: Timeframe) = BacktestResult(
        symbol = symbol,
        timeframe = timeframe,
        candleCount = 0,
        totalTrades = 0,
        winningTrades = 0,
        losingTrades = 0,
        winRate = 0.0,
        totalProfitLoss = 0.0,
        profitFactor = 0.0,
        sharpeRatio = 0.0,
        recoveryFactor = 0.0,
        maxDrawdownAmount = 0.0,
        maxDrawdownPercent = 0.0,
        averageR = 0.0,
        expectancy = 0.0,
        maxConsecutiveLosses = 0,
        maxConsecutiveWins = 0,
        averageWin = 0.0,
        averageLoss = 0.0,
        trades = emptyList(),
        equityCurve = emptyList()
    )
}
