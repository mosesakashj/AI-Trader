package com.example.domain.backtest

import com.example.domain.model.*
import com.example.domain.risk.RiskManager
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
    val maxDrawdownAmount: Double,
    val maxDrawdownPercent: Double,
    val averageR: Double,
    val expectancy: Double,
    val maxConsecutiveLosses: Int,
    val averageWin: Double,
    val averageLoss: Double,
    val trades: List<Trade>,
    val equityCurve: List<Pair<Long, Double>>
)

data class WalkForwardResult(
    val inSampleResult: BacktestResult,
    val validationResult: BacktestResult,
    val outOfSampleResult: BacktestResult,
    val robustnessScore: Double
)

class BacktestingEngine(
    private val strategy: TradingStrategy = TradingStrategy(),
    private val riskManager: RiskManager = RiskManager()
) {

    /**
     * Executes backtest strictly step-by-step on historical candles.
     */
    fun runBacktest(
        candles: List<Candle>,
        symbolConfig: SymbolConfig,
        initialBalance: Double = 10000.0,
        riskPercent: Double = 0.25
    ): BacktestResult {
        if (candles.size < 60) {
            return emptyResult(symbolConfig.symbol, candles.firstOrNull()?.timeframe ?: Timeframe.M15)
        }

        var balance = initialBalance
        var peakBalance = initialBalance
        var maxDrawdownAmount = 0.0
        var maxDrawdownPercent = 0.0

        val trades = mutableListOf<Trade>()
        val equityCurve = mutableListOf<Pair<Long, Double>>()
        equityCurve.add(candles.first().openTime to balance)

        var openTrade: Trade? = null
        var lastProcessedCandleTime = 0L

        // Meta values
        val tickSize = symbolConfig.tickSize
        val tickValue = symbolConfig.tickValue

        for (i in 50 until candles.size) {
            val currentCandle = candles[i]
            val pastCandles = candles.subList(0, i) // Only closed historical candles available up to i-1

            // 1. Manage open position if any
            if (openTrade != null) {
                val t = openTrade!!
                var isClosed = false
                var exitPrice = 0.0
                var reason = CloseReason.EXPIRED

                if (t.direction == TradeDirection.BUY) {
                    if (currentCandle.low <= t.stopLoss) {
                        isClosed = true
                        exitPrice = t.stopLoss
                        reason = CloseReason.STOP_LOSS
                    } else if (currentCandle.high >= t.takeProfit) {
                        isClosed = true
                        exitPrice = t.takeProfit
                        reason = CloseReason.TAKE_PROFIT
                    }
                } else {
                    if (currentCandle.high >= t.stopLoss) {
                        isClosed = true
                        exitPrice = t.stopLoss
                        reason = CloseReason.STOP_LOSS
                    } else if (currentCandle.low <= t.takeProfit) {
                        isClosed = true
                        exitPrice = t.takeProfit
                        reason = CloseReason.TAKE_PROFIT
                    }
                }

                if (isClosed) {
                    val priceDiff = if (t.direction == TradeDirection.BUY) exitPrice - t.entryPrice else t.entryPrice - exitPrice
                    val ticks = priceDiff / tickSize
                    val profit = ticks * tickValue * t.volume
                    val riskDist = abs(t.entryPrice - t.stopLoss)
                    val profitR = if (riskDist > 0) priceDiff / riskDist else 0.0

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

                val signal = strategy.evaluate(
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

        // Close any trailing position at final candle close
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

        // Calculate performance metrics
        val totalTrades = trades.size
        val winningTrades = trades.count { it.profit > 0 }
        val losingTrades = trades.count { it.profit <= 0 }
        val winRate = if (totalTrades > 0) (winningTrades.toDouble() / totalTrades) * 100.0 else 0.0

        val grossProfit = trades.filter { it.profit > 0 }.sumOf { it.profit }
        val grossLoss = abs(trades.filter { it.profit < 0 }.sumOf { it.profit })
        val profitFactor = if (grossLoss > 0) grossProfit / grossLoss else if (grossProfit > 0) 9.99 else 0.0

        val totalPnl = balance - initialBalance
        val avgR = if (totalTrades > 0) trades.sumOf { it.profitR } / totalTrades else 0.0
        val expectancy = if (totalTrades > 0) totalPnl / totalTrades else 0.0

        val wins = trades.filter { it.profit > 0 }
        val losses = trades.filter { it.profit < 0 }
        val avgWin = if (wins.isNotEmpty()) wins.sumOf { it.profit } / wins.size else 0.0
        val avgLoss = if (losses.isNotEmpty()) losses.sumOf { it.profit } / losses.size else 0.0

        var maxConsecLoss = 0
        var currentConsecLoss = 0
        trades.forEach { t ->
            if (t.profit <= 0) {
                currentConsecLoss++
                maxConsecLoss = max(maxConsecLoss, currentConsecLoss)
            } else {
                currentConsecLoss = 0
            }
        }

        return BacktestResult(
            symbol = symbolConfig.symbol,
            timeframe = candles.first().timeframe,
            candleCount = candles.size,
            totalTrades = totalTrades,
            winningTrades = winningTrades,
            losingTrades = losingTrades,
            winRate = winRate,
            totalProfitLoss = totalPnl,
            profitFactor = profitFactor,
            maxDrawdownAmount = maxDrawdownAmount,
            maxDrawdownPercent = maxDrawdownPercent,
            averageR = avgR,
            expectancy = expectancy,
            maxConsecutiveLosses = maxConsecLoss,
            averageWin = avgWin,
            averageLoss = avgLoss,
            trades = trades,
            equityCurve = equityCurve
        )
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

        // Robustness score based on consistency across splits
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
        maxDrawdownAmount = 0.0,
        maxDrawdownPercent = 0.0,
        averageR = 0.0,
        expectancy = 0.0,
        maxConsecutiveLosses = 0,
        averageWin = 0.0,
        averageLoss = 0.0,
        trades = emptyList(),
        equityCurve = emptyList()
    )
}
