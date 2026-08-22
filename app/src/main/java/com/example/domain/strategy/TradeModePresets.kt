package com.example.domain.strategy

import com.example.domain.model.TradeMode

object TradeModePresets {

    data class ModeConfig(
        val riskPercent: Double,
        val atrSlMultiplier: Double,
        val riskRewardRatio: Double,
        val breakEvenTriggerR: Double,
        val breakEvenBufferPips: Double,
        val trailingStopTriggerR: Double,
        val trailingStopDistanceAtr: Double,
        val maxConsecutiveLosses: Int,
        val cooldownAfterLossMinutes: Int,
        val cooldownAfterTradeMinutes: Int,
        val maxSpreadPips: Double
    )

    private val presets = mapOf(
        TradeMode.CONSERVATIVE to ModeConfig(
            riskPercent = 0.15,
            atrSlMultiplier = 2.0,
            riskRewardRatio = 2.5,
            breakEvenTriggerR = 0.5,
            breakEvenBufferPips = 2.0,
            trailingStopTriggerR = 0.8,
            trailingStopDistanceAtr = 1.5,
            maxConsecutiveLosses = 2,
            cooldownAfterLossMinutes = 60,
            cooldownAfterTradeMinutes = 10,
            maxSpreadPips = 20.0
        ),
        TradeMode.BALANCED to ModeConfig(
            riskPercent = 0.25,
            atrSlMultiplier = 1.5,
            riskRewardRatio = 2.0,
            breakEvenTriggerR = 0.8,
            breakEvenBufferPips = 1.5,
            trailingStopTriggerR = 1.2,
            trailingStopDistanceAtr = 1.0,
            maxConsecutiveLosses = 3,
            cooldownAfterLossMinutes = 30,
            cooldownAfterTradeMinutes = 5,
            maxSpreadPips = 30.0
        ),
        TradeMode.AGGRESSIVE to ModeConfig(
            riskPercent = 0.50,
            atrSlMultiplier = 1.0,
            riskRewardRatio = 1.5,
            breakEvenTriggerR = 1.2,
            breakEvenBufferPips = 1.0,
            trailingStopTriggerR = 1.8,
            trailingStopDistanceAtr = 0.7,
            maxConsecutiveLosses = 5,
            cooldownAfterLossMinutes = 15,
            cooldownAfterTradeMinutes = 2,
            maxSpreadPips = 40.0
        )
    )

    fun getPreset(mode: TradeMode): ModeConfig = presets[mode] ?: presets[TradeMode.BALANCED]!!

    fun getRiskPercent(mode: TradeMode): Double = getPreset(mode).riskPercent

    fun getAtrSlMultiplier(mode: TradeMode): Double = getPreset(mode).atrSlMultiplier

    fun getRiskRewardRatio(mode: TradeMode): Double = getPreset(mode).riskRewardRatio

    fun getBreakEvenTriggerR(mode: TradeMode): Double = getPreset(mode).breakEvenTriggerR

    fun getTrailingStopTriggerR(mode: TradeMode): Double = getPreset(mode).trailingStopTriggerR
}
