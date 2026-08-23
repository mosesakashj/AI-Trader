package com.example.domain.model

import com.example.broker.MarketSessionInfo
import com.example.domain.indicators.IndicatorValues

data class TradePlan(
    val signal: Signal,
    val strategyType: StrategyType,
    val tradeMode: TradeMode = TradeMode.BALANCED,
    val symbolConfig: SymbolConfig,
    val currentQuote: Quote,
    val indicators: IndicatorValues,
    val validation: OrderValidation? = null,
    val positionSize: Double = 0.0,
    val riskAmount: Double = 0.0,
    val marketSession: MarketSessionInfo
)
