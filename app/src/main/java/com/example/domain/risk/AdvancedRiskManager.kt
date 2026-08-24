package com.example.domain.risk

import com.example.domain.model.*
import timber.log.Timber
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.*

data class CorrelationPair(
    val symbol1: String,
    val symbol2: String,
    val correlation: Double,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class PortfolioRiskMetrics(
    val totalExposure: Double,
    val portfolioVaR: Double,
    val portfolioCVaR: Double,
    val maxDrawdownPercent: Double,
    val currentDrawdownPercent: Double,
    val marginUtilization: Double,
    val correlationRisk: Double,
    val riskScore: Double,
    val alerts: List<String>
)

data class KellyResult(
    val kellyFraction: Double,
    val halfKellyFraction: Double,
    val recommendedSize: Double,
    val winRate: Double,
    val avgWin: Double,
    val avgLoss: Double
)

class AdvancedRiskManager(
    private val riskConfig: RiskConfig = RiskConfig()
) {

    private val priceHistory = mutableMapOf<String, MutableList<Double>>()
    private val correlationMatrix = mutableMapOf<String, MutableMap<String, Double>>()
    private val tradeHistory = mutableListOf<Trade>()
    private var peakEquity = 0.0
    private var dailyTradeCount = 0
    private var lastTradeDay = 0L

    fun calculatePositionSize(
        equity: Double,
        riskPercent: Double,
        entryPrice: Double,
        stopLossPrice: Double,
        symbolConfig: SymbolConfig,
        method: SizingMethod = SizingMethod.FIXED_FRACTIONAL
    ): Double {
        if (equity <= 0.0 || entryPrice <= 0.0 || stopLossPrice <= 0.0) return 0.0

        val priceRisk = abs(entryPrice - stopLossPrice)
        if (priceRisk <= 0.0) return 0.0

        val effectiveRiskPercent = min(riskPercent, riskConfig.maxRiskPercent)

        return when (method) {
            SizingMethod.FIXED_FRACTIONAL -> {
                calculateFixedFractional(equity, effectiveRiskPercent, priceRisk, symbolConfig)
            }
            SizingMethod.KELLY -> {
                calculateKellySize(equity, effectiveRiskPercent, priceRisk, symbolConfig)
            }
            SizingMethod.ATR_NORMALIZED -> {
                calculateAtrNormalizedSize(equity, effectiveRiskPercent, priceRisk, symbolConfig)
            }
            SizingMethod.EQUITY_CURVE -> {
                calculateEquityCurveAdjusted(equity, effectiveRiskPercent, priceRisk, symbolConfig)
            }
        }
    }

    private fun calculateFixedFractional(
        equity: Double,
        riskPercent: Double,
        priceRisk: Double,
        symbolConfig: SymbolConfig
    ): Double {
        val dollarRisk = equity * (riskPercent / 100.0)
        val ticksAtRisk = priceRisk / symbolConfig.tickSize
        val costPerLot = ticksAtRisk * symbolConfig.tickValue
        if (costPerLot <= 0.0) return symbolConfig.minLot
        val rawLots = dollarRisk / costPerLot
        return normalizeLots(rawLots, symbolConfig)
    }

    private fun calculateKellySize(
        equity: Double,
        riskPercent: Double,
        priceRisk: Double,
        symbolConfig: SymbolConfig
    ): Double {
        val kelly = calculateKellyCriterion()
        if (kelly.kellyFraction <= 0) {
            return calculateFixedFractional(equity, riskPercent * 0.5, priceRisk, symbolConfig)
        }

        val adjustedRisk = riskPercent * kelly.halfKellyFraction
        return calculateFixedFractional(equity, adjustedRisk, priceRisk, symbolConfig)
    }

    private fun calculateAtrNormalizedSize(
        equity: Double,
        riskPercent: Double,
        priceRisk: Double,
        symbolConfig: SymbolConfig
    ): Double {
        val baseSize = calculateFixedFractional(equity, riskPercent, priceRisk, symbolConfig)
        val recentPrices = priceHistory[symbolConfig.symbol]?.takeLast(20) ?: return baseSize
        if (recentPrices.size < 10) return baseSize

        val returns = recentPrices.windowed(2).map { (prev, curr) -> abs(curr - prev) / prev }
        val currentVol = returns.lastOrNull() ?: 0.001
        val avgVol = returns.average().coerceAtLeast(0.001)

        val volAdjustment = (avgVol / currentVol).coerceIn(0.5, 1.5)
        return normalizeLots(baseSize * volAdjustment, symbolConfig)
    }

    private fun calculateEquityCurveAdjusted(
        equity: Double,
        riskPercent: Double,
        priceRisk: Double,
        symbolConfig: SymbolConfig
    ): Double {
        val recentTrades = tradeHistory.takeLast(20)
        if (recentTrades.size < 5) {
            return calculateFixedFractional(equity, riskPercent, priceRisk, symbolConfig)
        }

        val wins = recentTrades.count { it.profit > 0 }
        val winRate = wins.toDouble() / recentTrades.size

        val last5 = recentTrades.takeLast(5)
        val last5Wins = last5.count { it.profit > 0 }
        val recentMomentum = last5Wins.toDouble() / 5.0

        val curveMultiplier = when {
            recentMomentum >= 0.8 -> 1.25
            recentMomentum >= 0.6 -> 1.0
            recentMomentum >= 0.4 -> 0.75
            else -> 0.5
        }

        val adjustedRisk = riskPercent * curveMultiplier
        return calculateFixedFractional(equity, adjustedRisk, priceRisk, symbolConfig)
    }

    fun calculateKellyCriterion(): KellyResult {
        val recentTrades = tradeHistory.takeLast(100)
        if (recentTrades.size < 20) {
            return KellyResult(
                kellyFraction = 0.0,
                halfKellyFraction = 0.0,
                recommendedSize = 0.0,
                winRate = 0.5,
                avgWin = 0.0,
                avgLoss = 0.0
            )
        }

        val wins = recentTrades.filter { it.profit > 0 }
        val losses = recentTrades.filter { it.profit <= 0 }

        val winRate = wins.size.toDouble() / recentTrades.size
        val avgWin = if (wins.isNotEmpty()) wins.map { it.profit }.average() else 0.0
        val avgLoss = if (losses.isNotEmpty()) abs(losses.map { it.profit }.average()) else 1.0

        val b = if (avgLoss > 0) avgWin / avgLoss else 1.0
        val p = winRate
        val q = 1.0 - p

        val kellyFraction = ((b * p - q) / b).coerceAtLeast(0.0)
        val halfKelly = kellyFraction / 2.0

        return KellyResult(
            kellyFraction = kellyFraction,
            halfKellyFraction = halfKelly,
            recommendedSize = halfKelly,
            winRate = winRate,
            avgWin = avgWin,
            avgLoss = avgLoss
        )
    }

    fun validateTrade(
        signal: Signal,
        account: AccountInfo,
        symbolConfig: SymbolConfig,
        todayLossPercent: Double,
        consecutiveLosses: Int,
        openPositionsCount: Int,
        openPositions: List<Position> = emptyList(),
        lastTradeClosedTime: Long = 0L,
        lastTradeWasLoss: Boolean = false,
        currentTime: Long = System.currentTimeMillis()
    ): OrderValidation {
        if (riskConfig.emergencyStopActive) {
            return OrderValidation(isValid = false, reason = "Emergency Stop is ACTIVE")
        }

        if (riskConfig.safeModeActive) {
            return OrderValidation(isValid = false, reason = "Safe Mode is ACTIVE: ${riskConfig.safeModeReason}")
        }

        if (openPositionsCount >= riskConfig.maxOpenPositions) {
            return OrderValidation(isValid = false, reason = "Max open positions limit reached ($openPositionsCount >= ${riskConfig.maxOpenPositions})")
        }

        if (todayLossPercent >= riskConfig.maxDailyLossPercent) {
            return OrderValidation(isValid = false, reason = "Daily loss limit reached (${"%.2f".format(todayLossPercent)}% >= ${riskConfig.maxDailyLossPercent}%)")
        }

        if (consecutiveLosses >= riskConfig.maxConsecutiveLosses) {
            return OrderValidation(isValid = false, reason = "Max consecutive losses reached ($consecutiveLosses >= ${riskConfig.maxConsecutiveLosses})")
        }

        val today = currentTime / (24 * 60 * 60 * 1000)
        if (today == lastTradeDay && dailyTradeCount >= riskConfig.maxTradesPerDay) {
            return OrderValidation(isValid = false, reason = "Max daily trades reached ($dailyTradeCount >= ${riskConfig.maxTradesPerDay})")
        }

        if (lastTradeClosedTime > 0) {
            val minutesSinceLastTrade = (currentTime - lastTradeClosedTime) / (60 * 1000)
            if (lastTradeWasLoss && minutesSinceLastTrade < riskConfig.cooldownAfterLossMinutes) {
                return OrderValidation(isValid = false, reason = "Loss cooldown active: $minutesSinceLastTrade/${riskConfig.cooldownAfterLossMinutes} mins")
            }
            if (minutesSinceLastTrade < riskConfig.cooldownAfterTradeMinutes) {
                return OrderValidation(isValid = false, reason = "Trade cooldown active: $minutesSinceLastTrade/${riskConfig.cooldownAfterTradeMinutes} mins")
            }
        }

        val correlationCheck = checkCorrelationRisk(signal.symbol, openPositions)
        if (!correlationCheck.isValid) return correlationCheck

        val volume = calculatePositionSize(
            equity = account.equity,
            riskPercent = riskConfig.defaultRiskPercent,
            entryPrice = signal.price,
            stopLossPrice = signal.stopLoss,
            symbolConfig = symbolConfig
        )

        if (volume < symbolConfig.minLot) {
            return OrderValidation(isValid = false, reason = "Calculated volume $volume below minLot ${symbolConfig.minLot}")
        }

        val estimatedMargin = (volume * symbolConfig.contractSize * signal.price) / account.leverage
        if (estimatedMargin > account.freeMargin * 0.8) {
            return OrderValidation(isValid = false, reason = "Insufficient margin (Required: $$estimatedMargin, Available: $${account.freeMargin})")
        }

        val priceRisk = abs(signal.price - signal.stopLoss)
        if (priceRisk < symbolConfig.minimumStopDistance) {
            return OrderValidation(isValid = false, reason = "Stop distance $priceRisk below minimum ${symbolConfig.minimumStopDistance}")
        }

        val theoreticalRisk = (priceRisk / symbolConfig.tickSize) * symbolConfig.tickValue * volume

        return OrderValidation(
            isValid = true,
            reason = "Validation passed",
            estimatedMargin = estimatedMargin,
            theoreticalRisk = theoreticalRisk
        )
    }

    private fun checkCorrelationRisk(newSymbol: String, openPositions: List<Position>): OrderValidation {
        if (openPositions.isEmpty()) return OrderValidation(isValid = true)

        for (pos in openPositions) {
            val correlation = getCorrelation(newSymbol, pos.symbol)
            if (abs(correlation) > 0.8) {
                return OrderValidation(
                    isValid = false,
                    reason = "High correlation (${"%.2f".format(correlation)}) with open ${pos.symbol} position"
                )
            }
        }

        return OrderValidation(isValid = true)
    }

    fun getCorrelation(symbol1: String, symbol2: String): Double {
        if (symbol1 == symbol2) return 1.0
        return correlationMatrix[symbol1]?.get(symbol2)
            ?: correlationMatrix[symbol2]?.get(symbol1)
            ?: calculateCorrelationFromHistory(symbol1, symbol2)
    }

    private fun calculateCorrelationFromHistory(symbol1: String, symbol2: String): Double {
        val prices1 = priceHistory[symbol1]?.takeLast(50) ?: return 0.0
        val prices2 = priceHistory[symbol2]?.takeLast(50) ?: return 0.0

        if (prices1.size < 20 || prices2.size < 20) return 0.0

        val minSize = min(prices1.size, prices2.size)
        val p1 = prices1.takeLast(minSize)
        val p2 = prices2.takeLast(minSize)

        val returns1 = p1.windowed(2).map { (a, b) -> (b - a) / a }
        val returns2 = p2.windowed(2).map { (a, b) -> (b - a) / a }

        if (returns1.size < 10 || returns2.size < 10) return 0.0

        val mean1 = returns1.average()
        val mean2 = returns2.average()

        var covariance = 0.0
        var variance1 = 0.0
        var variance2 = 0.0

        for (i in returns1.indices) {
            val d1 = returns1[i] - mean1
            val d2 = returns2[i] - mean2
            covariance += d1 * d2
            variance1 += d1 * d1
            variance2 += d2 * d2
        }

        val denominator = sqrt(variance1 * variance2)
        return if (denominator > 0) (covariance / denominator).coerceIn(-1.0, 1.0) else 0.0
    }

    fun updatePriceHistory(symbol: String, price: Double) {
        priceHistory.getOrPut(symbol) { mutableListOf() }.add(price)
        if (priceHistory[symbol]!!.size > 200) {
            priceHistory[symbol]!!.removeAt(0)
        }
    }

    fun calculatePortfolioRisk(
        openPositions: List<Position>,
        account: AccountInfo,
        quotes: Map<String, Quote>
    ): PortfolioRiskMetrics {
        var totalExposure = 0.0
        var totalUnrealized = 0.0
        val marginUsed = account.balance - account.freeMargin
        val marginUtilization = if (account.balance > 0) marginUsed / account.balance else 0.0

        for (pos in openPositions) {
            val quote = quotes[pos.symbol]
            if (quote != null) {
                val markPrice = if (pos.direction == TradeDirection.BUY) quote.bid else quote.ask
                val priceDiff = if (pos.direction == TradeDirection.BUY) markPrice - pos.entryPrice else pos.entryPrice - markPrice
                totalExposure += abs(pos.volume * priceDiff)
                totalUnrealized += pos.unrealizedProfit
            }
        }

        val currentDrawdown = if (peakEquity > 0) {
            ((peakEquity - account.equity) / peakEquity * 100.0).coerceAtLeast(0.0)
        } else 0.0

        if (account.equity > peakEquity) peakEquity = account.equity

        val portfolioVaR = calculatePortfolioVaR(openPositions, account)
        val portfolioCVaR = portfolioVaR * 1.5

        val correlationRisk = calculateCorrelationRisk(openPositions)
        val maxDrawdown = currentDrawdown

        val riskScore = calculateRiskScore(
            marginUtilization, currentDrawdown, correlationRisk, openPositions.size
        )

        val alerts = mutableListOf<String>()
        if (marginUtilization > 0.7) alerts.add("High margin utilization: ${"%.1f".format(marginUtilization * 100)}%")
        if (currentDrawdown > 3.0) alerts.add("Elevated drawdown: ${"%.1f".format(currentDrawdown)}%")
        if (correlationRisk > 0.6) alerts.add("High portfolio correlation: ${"%.2f".format(correlationRisk)}")
        if (riskScore > 70) alerts.add("High overall risk score: ${"%.0f".format(riskScore)}")

        return PortfolioRiskMetrics(
            totalExposure = totalExposure,
            portfolioVaR = portfolioVaR,
            portfolioCVaR = portfolioCVaR,
            maxDrawdownPercent = maxDrawdown,
            currentDrawdownPercent = currentDrawdown,
            marginUtilization = marginUtilization,
            correlationRisk = correlationRisk,
            riskScore = riskScore,
            alerts = alerts
        )
    }

    private fun calculatePortfolioVaR(positions: List<Position>, account: AccountInfo): Double {
        if (positions.isEmpty()) return 0.0

        val confidence = 1.96
        var portfolioVariance = 0.0

        for (pos in positions) {
            val returns = priceHistory[pos.symbol]?.takeLast(50) ?: continue
            if (returns.size < 20) continue

            val dailyReturns = returns.windowed(2).map { (a, b) -> abs(b - a) / a }
            val volatility = dailyReturns.standardDeviation()
            val positionValue = pos.volume * pos.entryPrice

            portfolioVariance += (positionValue * volatility * confidence).pow(2)
        }

        return sqrt(portfolioVariance)
    }

    private fun calculateCorrelationRisk(positions: List<Position>): Double {
        if (positions.size < 2) return 0.0

        var totalCorrelation = 0.0
        var pairCount = 0

        for (i in positions.indices) {
            for (j in i + 1 until positions.size) {
                val corr = getCorrelation(positions[i].symbol, positions[j].symbol)
                totalCorrelation += abs(corr)
                pairCount++
            }
        }

        return if (pairCount > 0) totalCorrelation / pairCount else 0.0
    }

    private fun calculateRiskScore(
        marginUtilization: Double,
        drawdown: Double,
        correlationRisk: Double,
        positionCount: Int
    ): Double {
        var score = 0.0
        score += marginUtilization * 30
        score += (drawdown / 10.0) * 25
        score += correlationRisk * 25
        score += (positionCount.toDouble() / riskConfig.maxOpenPositions) * 20
        return score.coerceIn(0.0, 100.0)
    }

    fun shouldForceCloseAll(account: AccountInfo, maxDrawdownPercent: Double = 5.0): Boolean {
        if (peakEquity <= 0) return false
        val currentDD = ((peakEquity - account.equity) / peakEquity * 100.0)
        return currentDD >= maxDrawdownPercent
    }

    fun recordTrade(trade: Trade) {
        tradeHistory.add(trade)
        if (tradeHistory.size > 500) tradeHistory.removeAt(0)

        val today = trade.openedAt / (24 * 60 * 60 * 1000)
        if (today != lastTradeDay) {
            dailyTradeCount = 0
            lastTradeDay = today
        }
        dailyTradeCount++
    }

    private fun normalizeLots(rawLots: Double, symbolConfig: SymbolConfig): Double {
        val lotStep = if (symbolConfig.lotStep > 0) symbolConfig.lotStep else 0.01
        val steps = floor(rawLots / lotStep)
        var normalizedLots = steps * lotStep
        val decimals = if (lotStep >= 1.0) 0 else if (lotStep >= 0.1) 1 else 2
        normalizedLots = BigDecimal(normalizedLots).setScale(decimals, RoundingMode.DOWN).toDouble()
        return normalizedLots.coerceIn(symbolConfig.minLot, symbolConfig.maxLot)
    }

    private fun List<Double>.standardDeviation(): Double {
        if (size < 2) return 0.0
        val mean = average()
        val variance = map { val diff = it - mean; diff * diff }.average()
        return sqrt(variance)
    }
}

enum class SizingMethod {
    FIXED_FRACTIONAL,
    KELLY,
    ATR_NORMALIZED,
    EQUITY_CURVE
}
