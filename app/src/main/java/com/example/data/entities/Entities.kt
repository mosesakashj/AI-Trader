package com.example.data.entities

import com.example.domain.model.*

data class BotConfigEntity(
    val id: String = "primary_config",
    val mode: String = TradingMode.PAPER.name,
    val tradeMode: String = "BALANCED",
    val isBotEnabled: Boolean = false,
    val emergencyStop: Boolean = false,
    val safeMode: Boolean = false,
    val safeModeReason: String = "",
    val defaultRiskPercent: Double = 0.25,
    val maxDailyLossPercent: Double = 1.0,
    val maxConsecutiveLosses: Int = 3,
    val maxOpenPositions: Int = 1,
    val strategyType: String = StrategyType.PULLBACK.name,
    val emaFastPeriod: Int = 20,
    val emaSlowPeriod: Int = 50,
    val adxPeriod: Int = 14,
    val adxThreshold: Double = 25.0,
    val atrPeriod: Int = 14,
    val atrSlMultiplier: Double = 1.5,
    val riskRewardRatio: Double = 2.0,
    val maxCandleExtensionAtr: Double = 2.0,
    val sessionStartHour: Int = 0,
    val sessionEndHour: Int = 24,
    val timezone: String = "UTC",
    
    // Breakout parameters
    val breakoutLookbackPeriod: Int = 20,
    val breakoutVolumeMultiplier: Double = 1.5,
    val breakoutConfirmCandles: Int = 2,
    
    // Mean Reversion parameters
    val rsiPeriod: Int = 14,
    val rsiOverbought: Double = 70.0,
    val rsiOversold: Double = 30.0,
    val bbPeriod: Int = 20,
    val bbStdDev: Double = 2.0,
    
    // Momentum parameters
    val macdFastPeriod: Int = 12,
    val macdSlowPeriod: Int = 26,
    val macdSignalPeriod: Int = 9,
    val momentumAdxThreshold: Double = 30.0,
    
    // Range Trading parameters
    val rangeLookbackPeriod: Int = 50,
    val rangeMinTouches: Int = 2,
    val rangeAdxMax: Double = 20.0,
    
    // Scalping parameters
    val scalpTimeframe: String = Timeframe.M5.name,
    val scalpMinRr: Double = 1.5,
    val scalpMaxHoldMinutes: Int = 30,

    // Break-Even & Dynamic Position Management
    val breakEvenEnabled: Boolean = true,
    val breakEvenTriggerR: Double = 0.8,
    val breakEvenBufferPips: Double = 1.5,
    val trailingStopEnabled: Boolean = true,
    val trailingStopTriggerR: Double = 1.2,
    val trailingStopDistanceAtr: Double = 1.0,
    val earlyExitOnTrendReversal: Boolean = true,

    // Adaptive TP/SL/BE
    val adaptiveTpEnabled: Boolean = true,
    val adaptiveSlEnabled: Boolean = true,
    val adaptiveBeEnabled: Boolean = true,
    
    // Per-symbol timeframes (null = use strategy default M15)
    val xauusdTimeframe: String = "",
    val btcusdTimeframe: String = "",
    val ethusdTimeframe: String = "",
    val solusdTimeframe: String = "",
    val usoilTimeframe: String = "",
    val eurusdTimeframe: String = "",
    val gbpusdTimeframe: String = "",
    val usdjpyTimeframe: String = "",
    val audusdTimeframe: String = "",
    val usdcadTimeframe: String = "",
    val usdchfTimeframe: String = "",
    val nzdusdTimeframe: String = "",
    val eurgbpTimeframe: String = "",
    val eurjpyTimeframe: String = "",
    val gbpjpyTimeframe: String = "",
    
    val xauusdEnabled: Boolean = true,
    val btcusdEnabled: Boolean = true,
    val eurusdEnabled: Boolean = false,
    val gbpusdEnabled: Boolean = false,
    val usdjpyEnabled: Boolean = false,
    val audusdEnabled: Boolean = false,
    val usdcadEnabled: Boolean = false,
    val usdchfEnabled: Boolean = false,
    val nzdusdEnabled: Boolean = false,
    val eurgbpEnabled: Boolean = false,
    val eurjpyEnabled: Boolean = false,
    val gbpjpyEnabled: Boolean = false,
    val ethusdEnabled: Boolean = false,
    val solusdEnabled: Boolean = false,
    val usoilEnabled: Boolean = false,
    val telegramEnabled: Boolean = false,
    val telegramChatId: String = "",
    val strategyVersion: String = "2.0.0",
    val updatedAt: Long = System.currentTimeMillis()
)

data class TradeEntity(
    val id: String,
    val brokerOrderId: String,
    val brokerPositionId: String,
    val symbol: String,
    val direction: String,
    val volume: Double,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val riskAmount: Double,
    val riskPercent: Double,
    val rr: Double,
    val openedAt: Long,
    val closedAt: Long?,
    val closePrice: Double?,
    val profit: Double,
    val profitR: Double,
    val status: String,
    val closeReason: String?,
    val strategyVersion: String,
    val mode: String,
    val slippage: Double
)

data class PositionEntity(
    val id: String,
    val symbol: String,
    val direction: String,
    val volume: Double,
    val entryPrice: Double,
    val currentPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val unrealizedProfit: Double,
    val unrealizedR: Double,
    val openedAt: Long,
    val mode: String
)

data class SignalEntity(
    val id: String,
    val symbol: String,
    val direction: String,
    val price: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val rrRatio: Double,
    val candleTime: Long,
    val timestamp: Long,
    val decision: String,
    val reason: String,
    val emaFast: Double,
    val emaSlow: Double,
    val adx: Double,
    val atr: Double,
    val strategyVersion: String
)

data class CandleEntity(
    val symbol: String,
    val timeframe: String,
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val isClosed: Boolean
)

data class SystemEventEntity(
    val id: Long = 0,
    val timestamp: Long,
    val level: String,
    val component: String,
    val event: String,
    val correlationId: String,
    val symbol: String?,
    val message: String
)

data class HeartbeatEntity(
    val component: String,
    val timestamp: Long,
    val status: String,
    val details: String = ""
)

data class WatchlistItemEntity(
    val symbol: String,
    val displayName: String,
    val assetType: String,
    val addedAt: Long = System.currentTimeMillis(),
    val isMonitoring: Boolean = true,
    val alertOnSignal: Boolean = true,
    val alertOnSessionOpen: Boolean = false,
    val notes: String = ""
)


