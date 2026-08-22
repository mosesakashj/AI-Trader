package com.example.domain.risk

import com.example.domain.model.*
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class RiskManager(
    private val riskConfig: RiskConfig = RiskConfig()
) {

    /**
     * Calculates mathematically sound position size (lots) based on equity risk percentage.
     */
    fun calculatePositionSize(
        equity: Double,
        riskPercent: Double,
        entryPrice: Double,
        stopLossPrice: Double,
        symbolConfig: SymbolConfig
    ): Double {
        if (equity <= 0.0 || entryPrice <= 0.0 || stopLossPrice <= 0.0) return 0.0

        val priceRisk = abs(entryPrice - stopLossPrice)
        if (priceRisk <= 0.0) return 0.0

        // Cap risk percent to safety bounds
        val effectiveRiskPercent = min(riskPercent, riskConfig.maxRiskPercent)
        val dollarRisk = equity * (effectiveRiskPercent / 100.0)

        // Risk in ticks
        val ticksAtRisk = priceRisk / symbolConfig.tickSize
        // Value of 1 standard lot for priceRisk distance:
        // Value per tick for 1 lot = symbolConfig.tickValue
        val costPerLot = ticksAtRisk * symbolConfig.tickValue

        if (costPerLot <= 0.0) return symbolConfig.minLot

        val rawLots = dollarRisk / costPerLot

        // Normalize lots according to minLot, maxLot, and lotStep
        val lotStep = if (symbolConfig.lotStep > 0) symbolConfig.lotStep else 0.01
        val steps = floor(rawLots / lotStep)
        var normalizedLots = steps * lotStep

        // Precision rounding based on lotStep
        val decimals = if (lotStep >= 1.0) 0 else if (lotStep >= 0.1) 1 else 2
        normalizedLots = BigDecimal(normalizedLots).setScale(decimals, RoundingMode.DOWN).toDouble()

        return normalizedLots.coerceIn(symbolConfig.minLot, symbolConfig.maxLot)
    }

    /**
     * Comprehensive pre-trade safety validation before sending order.
     */
    fun validateTrade(
        signal: Signal,
        account: AccountInfo,
        symbolConfig: SymbolConfig,
        todayLossPercent: Double,
        consecutiveLosses: Int,
        openPositionsCount: Int,
        lastTradeClosedTime: Long = 0L,
        lastTradeWasLoss: Boolean = false,
        currentTime: Long = System.currentTimeMillis()
    ): OrderValidation {
        // 1. Emergency stop active
        if (riskConfig.emergencyStopActive) {
            return OrderValidation(isValid = false, reason = "Emergency Stop is ACTIVE across the application")
        }

        // 2. Safe mode active
        if (riskConfig.safeModeActive) {
            return OrderValidation(isValid = false, reason = "Safe Mode is ACTIVE: ${riskConfig.safeModeReason}")
        }

        // 3. Max open positions check
        if (openPositionsCount >= riskConfig.maxOpenPositions) {
            return OrderValidation(isValid = false, reason = "Max open positions limit reached ($openPositionsCount >= ${riskConfig.maxOpenPositions})")
        }

        // 4. Daily loss limit check
        if (todayLossPercent >= riskConfig.maxDailyLossPercent) {
            return OrderValidation(isValid = false, reason = "Daily loss limit reached (${todayLossPercent.format(2)}% >= ${riskConfig.maxDailyLossPercent}%)")
        }

        // 5. Consecutive loss limit check
        if (consecutiveLosses >= riskConfig.maxConsecutiveLosses) {
            return OrderValidation(isValid = false, reason = "Max consecutive losses reached ($consecutiveLosses >= ${riskConfig.maxConsecutiveLosses})")
        }

        // 6. Cooldown checks
        if (lastTradeClosedTime > 0) {
            val minutesSinceLastTrade = (currentTime - lastTradeClosedTime) / (60 * 1000)
            if (lastTradeWasLoss && minutesSinceLastTrade < riskConfig.cooldownAfterLossMinutes) {
                return OrderValidation(
                    isValid = false,
                    reason = "Loss cooldown active: $minutesSinceLastTrade/${riskConfig.cooldownAfterLossMinutes} mins passed"
                )
            }
            if (minutesSinceLastTrade < riskConfig.cooldownAfterTradeMinutes) {
                return OrderValidation(
                    isValid = false,
                    reason = "Trade cooldown active: $minutesSinceLastTrade/${riskConfig.cooldownAfterTradeMinutes} mins passed"
                )
            }
        }

        // 7. Calculate volume & risk
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

        // 8. Calculate margin requirements: Margin = (Volume * ContractSize * Price) / Leverage
        val estimatedMargin = (volume * symbolConfig.contractSize * signal.price) / account.leverage
        if (estimatedMargin > account.freeMargin * 0.8) {
            return OrderValidation(
                isValid = false,
                reason = "Insufficient free margin (Required: $$estimatedMargin, Available: $${account.freeMargin})"
            )
        }

        val priceRisk = abs(signal.price - signal.stopLoss)
        val ticksAtRisk = priceRisk / symbolConfig.tickSize
        val theoreticalRisk = ticksAtRisk * symbolConfig.tickValue * (volume / 1.0)

        // 9. Stop distance validation
        if (priceRisk < symbolConfig.minimumStopDistance) {
            return OrderValidation(
                isValid = false,
                reason = "Stop distance $priceRisk below broker minimum distance ${symbolConfig.minimumStopDistance}"
            )
        }

        return OrderValidation(
            isValid = true,
            reason = "Validation passed",
            estimatedMargin = estimatedMargin,
            theoreticalRisk = theoreticalRisk
        )
    }

    private fun Double.format(digits: Int): String = "%.${digits}f".format(this)
}
