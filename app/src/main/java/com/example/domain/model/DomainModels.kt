package com.example.domain.model

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
    val strategyVersion: String = "1.0.0",
    val emaFastPeriod: Int = 20,
    val emaSlowPeriod: Int = 50,
    val adxPeriod: Int = 14,
    val adxThreshold: Double = 25.0,
    val atrPeriod: Int = 14,
    val atrSlMultiplier: Double = 1.5,
    val riskRewardRatio: Double = 2.0,
    val maxCandleExtensionAtr: Double = 2.0, // Max distance from EMA20 in ATR units
    val sessionStartHour: Int = 0,
    val sessionEndHour: Int = 24,
    val timezone: String = "UTC"
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
