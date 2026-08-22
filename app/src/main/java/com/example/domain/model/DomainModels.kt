package com.example.domain.model

enum class StrategyType(val displayName: String, val description: String) {
    PULLBACK("Trend Pullback", "EMA trend + pullback to EMA band with ADX confirmation"),
    BREAKOUT("Breakout", "Price breaks key levels with volume/momentum confirmation"),
    MEAN_REVERSION("Mean Reversion", "RSI/Bollinger extremes revert to mean in ranging markets"),
    MOMENTUM("Momentum", "Strong directional moves with MACD/ADX alignment"),
    RANGE_TRADING("Range Trading", "Support/Resistance bounces in defined ranges"),
    SCALPING("Scalping", "Quick 0.5-1R trades on micro-structure patterns")
}

data class Candle(
    val symbol: String,
    val timeframe: Timeframe,
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double = 0.0,
    val isClosed: Boolean = true
)

data class Quote(
    val symbol: String,
    val bid: Double,
    val ask: Double,
    val timestamp: Long = System.currentTimeMillis()
) {
    val spread: Double
        get() = ask - bid
}

data class Signal(
    val id: String,
    val symbol: String,
    val direction: TradeDirection,
    val price: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val rrRatio: Double,
    val candleTime: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val explanation: SignalExplanation,
    val strategyVersion: String = "1.0.0"
)

data class SignalExplanation(
    val symbol: String,
    val direction: TradeDirection,
    val emaFast: Double,
    val emaSlow: Double,
    val adx: Double,
    val atr: Double,
    val trendCheck: Boolean,
    val adxCheck: Boolean,
    val pullbackCheck: Boolean,
    val candleCheck: Boolean,
    val spreadCheck: Boolean,
    val riskCheck: Boolean,
    val sessionCheck: Boolean,
    val decision: String,
    val reason: String
) {
    val isAllPassed: Boolean
        get() = trendCheck && adxCheck && pullbackCheck && candleCheck && spreadCheck && riskCheck && sessionCheck
}

data class Trade(
    val id: String,
    val brokerOrderId: String = "",
    val brokerPositionId: String = "",
    val symbol: String,
    val direction: TradeDirection,
    val volume: Double,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val riskAmount: Double,
    val riskPercent: Double,
    val rr: Double,
    val openedAt: Long,
    val closedAt: Long? = null,
    val closePrice: Double? = null,
    val profit: Double = 0.0,
    val profitR: Double = 0.0,
    val status: TradeStatus = TradeStatus.OPEN,
    val closeReason: CloseReason? = null,
    val strategyVersion: String = "1.0.0",
    val mode: TradingMode = TradingMode.PAPER,
    val slippage: Double = 0.0
)

data class Position(
    val id: String,
    val symbol: String,
    val direction: TradeDirection,
    val volume: Double,
    val entryPrice: Double,
    val currentPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val unrealizedProfit: Double,
    val unrealizedR: Double,
    val openedAt: Long,
    val mode: TradingMode = TradingMode.PAPER
)

fun Position.toClosedTrade(
    closePrice: Double,
    reason: CloseReason,
    mode: TradingMode
): Trade = Trade(
    id = id,
    symbol = symbol,
    direction = direction,
    volume = volume,
    entryPrice = entryPrice,
    stopLoss = stopLoss,
    takeProfit = takeProfit,
    riskAmount = 0.0,
    riskPercent = 0.25,
    rr = 2.0,
    openedAt = openedAt,
    closedAt = System.currentTimeMillis(),
    closePrice = closePrice,
    profit = unrealizedProfit,
    profitR = unrealizedR,
    status = TradeStatus.CLOSED,
    closeReason = reason,
    mode = mode
)

data class SymbolConfig(
    val symbol: String,
    val displayName: String,
    val brokerSymbol: String,
    val assetType: AssetType,
    val digits: Int,
    val contractSize: Double,
    val minLot: Double,
    val maxLot: Double,
    val lotStep: Double,
    val tickSize: Double,
    val tickValue: Double,
    val minimumStopDistance: Double,
    val spreadLimit: Double,
    val minimumAtr: Double = 0.0,
    val maximumAtr: Double = 1000.0,
    val enabled: Boolean = true
)

data class StrategyConfig(
    val strategyVersion: String = "2.0.0",
    val strategyType: StrategyType = StrategyType.PULLBACK,
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
    val scalpTimeframe: Timeframe = Timeframe.M5,
    val scalpMinRr: Double = 1.5,
    val scalpMaxHoldMinutes: Int = 30,

    // Break-Even & Dynamic Position Management
    val breakEvenEnabled: Boolean = true,
    val breakEvenTriggerR: Double = 0.8,
    val breakEvenBufferPips: Double = 1.5,
    val trailingStopEnabled: Boolean = true,
    val trailingStopTriggerR: Double = 1.2,
    val trailingStopDistanceAtr: Double = 1.0,
    val earlyExitOnTrendReversal: Boolean = true
)

data class RiskConfig(
    val defaultRiskPercent: Double = 0.25, // 0.25%
    val maxRiskPercent: Double = 1.0,      // Max allowed without override
    val maxDailyLossPercent: Double = 1.0, // 1% daily loss halts new trades
    val maxOpenPositions: Int = 1,
    val maxConsecutiveLosses: Int = 3,
    val maxTradesPerDay: Int = 10,
    val maxSpreadPips: Double = 30.0,
    val maxSlippagePips: Double = 10.0,
    val cooldownAfterLossMinutes: Int = 30,
    val cooldownAfterTradeMinutes: Int = 5,
    val emergencyStopActive: Boolean = false,
    val safeModeActive: Boolean = false,
    val safeModeReason: String = ""
)

data class AccountInfo(
    val balance: Double,
    val equity: Double,
    val freeMargin: Double,
    val margin: Double = 0.0,
    val leverage: Int = 100,
    val currency: String = "USD",
    val mode: TradingMode = TradingMode.PAPER,
    val serverTime: Long = System.currentTimeMillis()
)

data class OrderRequest(
    val clientOrderId: String,
    val symbol: String,
    val direction: TradeDirection,
    val volume: Double,
    val requestedPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val maxSlippage: Double,
    val mode: TradingMode
)

data class OrderValidation(
    val isValid: Boolean,
    val reason: String = "",
    val estimatedMargin: Double = 0.0,
    val theoreticalRisk: Double = 0.0
)

data class OrderResult(
    val success: Boolean,
    val orderId: String = "",
    val positionId: String = "",
    val executedPrice: Double = 0.0,
    val executedVolume: Double = 0.0,
    val slippage: Double = 0.0,
    val errorMessage: String = ""
)
