package com.example.domain.strategy

import com.example.domain.model.*
import timber.log.Timber
import kotlin.math.abs

data class PartialCloseConfig(
    val enabled: Boolean = true,
    val tp1Percentage: Double = 0.5,
    val tp1TriggerR: Double = 1.0,
    val moveSlToBreakeven: Boolean = true,
    val tp2Percentage: Double = 0.5
)

data class PyramidingConfig(
    val enabled: Boolean = false,
    val maxAddOns: Int = 2,
    val addOnTriggerR: Double = 0.5,
    val addOnRiskPercent: Double = 0.125,
    val minAdxForPyramid: Double = 30.0
)

data class CorrelationFilterConfig(
    val enabled: Boolean = true,
    val maxCorrelation: Double = 0.8,
    val correlationLookback: Int = 50
)

class PositionManager(
    private val partialCloseConfig: PartialCloseConfig = PartialCloseConfig(),
    private val pyramidingConfig: PyramidingConfig = PyramidingConfig(),
    private val correlationFilterConfig: CorrelationFilterConfig = CorrelationFilterConfig()
) {

    private val openPositionAdds = mutableMapOf<String, Int>()

    fun checkPartialClose(
        position: Position,
        currentQuote: Quote,
        unrealizedR: Double
    ): PartialCloseDecision {
        if (!partialCloseConfig.enabled) {
            return PartialCloseDecision(shouldClose = false)
        }

        if (unrealizedR >= partialCloseConfig.tp1TriggerR) {
            val closeVolume = position.volume * partialCloseConfig.tp1Percentage
            val normalizedClose = (closeVolume * 100).toInt() / 100.0

            if (normalizedClose >= 0.01) {
                val newSl = if (partialCloseConfig.moveSlToBreakeven) {
                    position.entryPrice
                } else {
                    position.stopLoss
                }

                return PartialCloseDecision(
                    shouldClose = true,
                    closeVolume = normalizedClose,
                    newStopLoss = newSl,
                    reason = "TP1 reached at +${"%.2f".format(unrealizedR)}R, closing ${"%.0f".format(partialCloseConfig.tp1Percentage * 100)}%"
                )
            }
        }

        return PartialCloseDecision(shouldClose = false)
    }

    fun checkPyramiding(
        position: Position,
        currentQuote: Quote,
        unrealizedR: Double,
        adx: Double
    ): PyramidingDecision {
        if (!pyramidingConfig.enabled) {
            return PyramidingDecision(shouldAdd = false)
        }

        val currentAdds = openPositionAdds[position.id] ?: 0
        if (currentAdds >= pyramidingConfig.maxAddOns) {
            return PyramidingDecision(shouldAdd = false, reason = "Max add-ons reached ($currentAdds/${pyramidingConfig.maxAddOns})")
        }

        if (adx < pyramidingConfig.minAdxForPyramid) {
            return PyramidingDecision(shouldAdd = false, reason = "ADX too low for pyramiding (${"%.1f".format(adx)} < ${pyramidingConfig.minAdxForPyramid})")
        }

        if (unrealizedR >= pyramidingConfig.addOnTriggerR) {
            openPositionAdds[position.id] = currentAdds + 1
            return PyramidingDecision(
                shouldAdd = true,
                addVolumePercent = pyramidingConfig.addOnRiskPercent,
                reason = "Pyramid add #${currentAdds + 1} at +${"%.2f".format(unrealizedR)}R"
            )
        }

        return PyramidingDecision(shouldAdd = false)
    }

    fun checkCorrelationFilter(
        newSymbol: String,
        openPositions: List<Position>,
        getCorrelation: (String, String) -> Double
    ): CorrelationFilterResult {
        if (!correlationFilterConfig.enabled || openPositions.isEmpty()) {
            return CorrelationFilterResult(isAllowed = true)
        }

        val correlatedPositions = openPositions.filter { pos ->
            val correlation = getCorrelation(newSymbol, pos.symbol)
            abs(correlation) > correlationFilterConfig.maxCorrelation
        }

        if (correlatedPositions.isNotEmpty()) {
            val maxCorr = correlatedPositions.maxOfOrNull {
                abs(getCorrelation(newSymbol, it.symbol))
            } ?: 0.0
            return CorrelationFilterResult(
                isAllowed = false,
                reason = "High correlation (${"%.2f".format(maxCorr)}) with ${correlatedPositions.joinToString { it.symbol }}",
                correlatedSymbols = correlatedPositions.map { it.symbol }
            )
        }

        return CorrelationFilterResult(isAllowed = true)
    }

    fun resetPositionAdds(positionId: String) {
        openPositionAdds.remove(positionId)
    }

    fun getPositionAddCount(positionId: String): Int = openPositionAdds[positionId] ?: 0
}

data class PartialCloseDecision(
    val shouldClose: Boolean,
    val closeVolume: Double = 0.0,
    val newStopLoss: Double = 0.0,
    val reason: String = ""
)

data class PyramidingDecision(
    val shouldAdd: Boolean,
    val addVolumePercent: Double = 0.0,
    val reason: String = ""
)

data class CorrelationFilterResult(
    val isAllowed: Boolean,
    val reason: String = "",
    val correlatedSymbols: List<String> = emptyList()
)
