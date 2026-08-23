package com.example.ai

data class AiProviderConfig(
    val id: String,
    val name: String,
    val apiEndpoint: String,
    val apiKey: String,
    val model: String,
    val enabled: Boolean = false,
    val temperature: Double = 0.3,
    val maxTokens: Int = 2048,
    val timeoutSeconds: Int = 30
)

data class AiAnalysisRequest(
    val symbol: String,
    val timeframe: String,
    val currentPrice: Double,
    val signalDirection: String?,
    val signalEntry: Double?,
    val signalStopLoss: Double?,
    val signalTakeProfit: Double?,
    val indicators: Map<String, Double>,
    val recentCandles: List<CandleSnapshot>,
    val accountEquity: Double,
    val openPositions: Int,
    val dailyPnL: Double,
    val riskPercent: Double
)

data class CandleSnapshot(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

data class AiAnalysisResponse(
    val provider: String,
    val timestamp: Long = System.currentTimeMillis(),
    val recommendation: AiRecommendation,
    val confidence: Double, // 0.0 to 1.0
    val reasoning: String,
    val keyLevels: KeyLevels,
    val riskAssessment: RiskAssessment,
    val alternativeScenarios: List<String>
)

enum class AiRecommendation {
    STRONG_BUY,
    BUY,
    NEUTRAL,
    SELL,
    STRONG_SELL,
    HOLD_POSITION,
    CLOSE_POSITION,
    REDUCE_RISK
}

data class KeyLevels(
    val support: List<Double>,
    val resistance: List<Double>,
    val pivotPoint: Double,
    val fibonacciLevels: Map<String, Double>
)

data class RiskAssessment(
    val riskLevel: RiskLevel,
    val maxRiskPercent: Double,
    val suggestedPositionSize: Double,
    val stopLossDistance: Double,
    val riskRewardRatio: Double,
    val warnings: List<String>
)

enum class RiskLevel {
    VERY_LOW,
    LOW,
    MODERATE,
    HIGH,
    VERY_HIGH
}

data class AiChatMessage(
    val role: String, // "system", "user", "assistant"
    val content: String
)

data class AiChatRequest(
    val messages: List<AiChatMessage>,
    val temperature: Double = 0.3,
    val maxTokens: Int = 2048
)

data class AiChatResponse(
    val content: String,
    val provider: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tokensUsed: Int
)

data class AiBacktestAudit(
    val grade: String, // "A+", "A", "B", "C", "D"
    val summary: String,
    val winRateAssessment: String,
    val drawdownRiskAssessment: String,
    val rMultipleEfficiency: String,
    val strengths: List<String>,
    val vulnerabilities: List<String>,
    val tacticalTweaks: List<String>,
    val overfittingRisk: String, // "LOW", "MODERATE", "ELEVATED"
    val timestamp: Long = System.currentTimeMillis()
)

data class AiStrategyAudit(
    val strategyName: String,
    val marketFitRating: String, // "OPTIMAL", "FAVORABLE", "CHOPPY / CAUTION"
    val executiveSummary: String,
    val parameterAnalysis: List<String>,
    val recommendedAdjustments: List<String>,
    val riskManagementPlan: String,
    val edgeScore: Int = 88, // 0-100
    val timestamp: Long = System.currentTimeMillis()
)

data class AiPositionAudit(
    val positionId: String,
    val symbol: String,
    val verdict: String, // "HOLD_TOWARD_TP", "TIGHTEN_SL", "SCALE_OUT_PARTIAL", "EXIT_STRUCTURAL_INVALIDATION"
    val confidence: Double,
    val marketStructureAnalysis: String,
    val structuralLevels: List<String>,
    val continuousPlanSteps: List<String>,
    val immediateAction: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class AiStrategyOptimization(
    val strategyName: String,
    val currentProfitFactor: Double,
    val targetProfitFactor: Double,
    val expectedProfitBoostPct: Double, // e.g. +38.5%
    val expectedWinRate: Double, // e.g. 68.5%
    val expectedSharpe: Double, // e.g. 2.1
    val detectedBottlenecks: List<String>,
    val parameterOptimizations: Map<String, String>, // e.g. "EMA Fast": "20 -> 14 (Faster trigger)"
    val appliedConfig: com.example.domain.model.StrategyConfig,
    val regimeAdaptations: List<String>,
    val fineTuningRationale: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class AiMarketIntelligence(
    val symbol: String,
    val timeframe: String,
    val currentPrice: Double,
    val bias: String, // "STRONG_BULLISH", "BULLISH_EXPANSION", "RANGE_ACCUMULATION", "BEARISH_SWEEP", "STRONG_BEARISH"
    val confluenceScore: Int, // 0 to 100
    val marketRegime: String, // "TREND_EXPANSION", "ORDER_BLOCK_RETEST", "RANGE_BOUND_LIQUIDITY_RUN", "VOLATILITY_COMPRESSION"
    val institutionalSummary: String,
    val keySupportZones: List<Double>,
    val keyResistanceZones: List<Double>,
    val liquidityPools: List<String>,
    val orderBlockZone: String,
    val catalystAndNewsRisk: String,
    val actionableGuidance: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class AiInstitutionalTradePlan(
    val id: String = java.util.UUID.randomUUID().toString(),
    val symbol: String,
    val timeframe: String = "M15",
    val direction: com.example.domain.model.TradeDirection,
    val strategyType: com.example.domain.model.StrategyType,
    val entryPrice: Double,
    val entryZoneLow: Double,
    val entryZoneHigh: Double,
    val stopLoss: Double,
    val takeProfit1: Double, // 50% scale out + Move to BE
    val takeProfit2: Double, // Runner target
    val breakEvenTrigger: Double,
    val riskRewardRatio: Double,
    val recommendedLotSize: Double,
    val maxRiskAmountUsd: Double,
    val invalidationCondition: String,
    val confluenceFactors: List<String>,
    val executionPhases: List<String>,
    val confidence: Double = 0.85,
    val status: String = "READY_TO_DEPLOY", // "READY_TO_DEPLOY", "DEPLOYED", "INVALIDATED"
    val timestamp: Long = System.currentTimeMillis()
)

sealed interface AiProvider {
    val config: AiProviderConfig
    suspend fun analyze(request: AiAnalysisRequest): Result<AiAnalysisResponse>
    suspend fun chat(request: AiChatRequest): Result<AiChatResponse>
    suspend fun testConnection(): Result<String>
}

object AiProviderType {
    const val NVIDIA = "nvidia"
    const val GEMINI = "gemini"
    const val CLAUDE = "claude"
    const val CHATGPT = "chatgpt"
    const val LOCAL = "local"
}