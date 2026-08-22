package com.example.domain.strategy

import com.example.domain.model.*
import kotlin.math.abs
import kotlin.math.max

object AdaptiveCalculator {

    data class AdaptiveResult(
        val slDistance: Double,
        val tpDistance: Double,
        val beTriggerR: Double,
        val beBufferPips: Double,
        val trailingDistance: Double,
        val adxStrengthLabel: String
    )

    enum class AdxStrength { STRONG, MODERATE, WEAK }

    fun classifyAdx(adx: Double, threshold: Double = 25.0): AdxStrength = when {
        adx >= threshold * 1.4 -> AdxStrength.STRONG
        adx >= threshold -> AdxStrength.MODERATE
        else -> AdxStrength.WEAK
    }

    fun adaptiveSlDistance(
        atr: Double,
        baseMultiplier: Double,
        minimumStopDistance: Double,
        adx: Double,
        adxThreshold: Double,
        enabled: Boolean
    ): Double {
        val baseDistance = atr * baseMultiplier
        if (!enabled) return max(baseDistance, minimumStopDistance)

        val adxStrength = classifyAdx(adx, adxThreshold)
        val adxFactor = when (adxStrength) {
            AdxStrength.STRONG -> 1.2
            AdxStrength.MODERATE -> 1.0
            AdxStrength.WEAK -> 0.8
        }
        return max(baseDistance * adxFactor, minimumStopDistance)
    }

    fun adaptiveTpDistance(
        slDistance: Double,
        baseRiskReward: Double,
        adx: Double,
        adxThreshold: Double,
        enabled: Boolean
    ): Double {
        if (!enabled) return slDistance * baseRiskReward

        val adxStrength = classifyAdx(adx, adxThreshold)
        val trendMultiplier = when (adxStrength) {
            AdxStrength.STRONG -> 1.3
            AdxStrength.MODERATE -> 1.0
            AdxStrength.WEAK -> 0.7
        }
        return slDistance * baseRiskReward * trendMultiplier
    }

    fun adaptiveBeTriggerR(
        baseTriggerR: Double,
        adx: Double,
        adxThreshold: Double,
        enabled: Boolean
    ): Double {
        if (!enabled) return baseTriggerR

        val adxStrength = classifyAdx(adx, adxThreshold)
        return when (adxStrength) {
            AdxStrength.STRONG -> baseTriggerR * 0.75
            AdxStrength.MODERATE -> baseTriggerR
            AdxStrength.WEAK -> baseTriggerR * 1.25
        }
    }

    fun adaptiveBeBufferPips(
        baseBufferPips: Double,
        atr: Double,
        tickSize: Double,
        adx: Double,
        adxThreshold: Double,
        enabled: Boolean
    ): Double {
        if (!enabled) return baseBufferPips

        val adxStrength = classifyAdx(adx, adxThreshold)
        val atrFactor = when (adxStrength) {
            AdxStrength.STRONG -> 0.8
            AdxStrength.MODERATE -> 1.0
            AdxStrength.WEAK -> 1.3
        }
        val minBuffer = (tickSize * 10.0)
        return max(baseBufferPips * atrFactor, minBuffer)
    }

    fun adaptiveTrailingDistance(
        atr: Double,
        baseDistanceAtr: Double,
        adx: Double,
        adxThreshold: Double,
        tickSize: Double,
        enabled: Boolean
    ): Double {
        val baseDistance = atr * baseDistanceAtr
        if (!enabled) return baseDistance

        val adxStrength = classifyAdx(adx, adxThreshold)
        val adxFactor = when (adxStrength) {
            AdxStrength.STRONG -> 0.8
            AdxStrength.MODERATE -> 1.0
            AdxStrength.WEAK -> 1.2
        }
        return max(baseDistance * adxFactor, tickSize * 25.0)
    }

    fun computeAll(
        atr: Double,
        adx: Double,
        adxThreshold: Double,
        baseSlMultiplier: Double,
        baseRiskReward: Double,
        baseBeTriggerR: Double,
        baseBeBufferPips: Double,
        baseTrailingDistanceAtr: Double,
        minimumStopDistance: Double,
        tickSize: Double,
        adaptiveSlEnabled: Boolean,
        adaptiveTpEnabled: Boolean,
        adaptiveBeEnabled: Boolean
    ): AdaptiveResult {
        val slDistance = adaptiveSlDistance(
            atr = atr,
            baseMultiplier = baseSlMultiplier,
            minimumStopDistance = minimumStopDistance,
            adx = adx,
            adxThreshold = adxThreshold,
            enabled = adaptiveSlEnabled
        )

        val tpDistance = adaptiveTpDistance(
            slDistance = slDistance,
            baseRiskReward = baseRiskReward,
            adx = adx,
            adxThreshold = adxThreshold,
            enabled = adaptiveTpEnabled
        )

        val beTriggerR = adaptiveBeTriggerR(
            baseTriggerR = baseBeTriggerR,
            adx = adx,
            adxThreshold = adxThreshold,
            enabled = adaptiveBeEnabled
        )

        val beBufferPips = adaptiveBeBufferPips(
            baseBufferPips = baseBeBufferPips,
            atr = atr,
            tickSize = tickSize,
            adx = adx,
            adxThreshold = adxThreshold,
            enabled = adaptiveBeEnabled
        )

        val trailingDistance = adaptiveTrailingDistance(
            atr = atr,
            baseDistanceAtr = baseTrailingDistanceAtr,
            adx = adx,
            adxThreshold = adxThreshold,
            tickSize = tickSize,
            enabled = adaptiveBeEnabled
        )

        val adxStrengthLabel = classifyAdx(adx, adxThreshold).name

        return AdaptiveResult(
            slDistance = slDistance,
            tpDistance = tpDistance,
            beTriggerR = beTriggerR,
            beBufferPips = beBufferPips,
            trailingDistance = trailingDistance,
            adxStrengthLabel = adxStrengthLabel
        )
    }
}
