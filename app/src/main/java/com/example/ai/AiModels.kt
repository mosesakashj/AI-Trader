package com.example.ai

import kotlinx.serialization.Serializable

@Serializable
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

@Serializable
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

@Serializable
data class CandleSnapshot(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

@Serializable
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

@Serializable
data class KeyLevels(
    val support: List<Double>,
    val resistance: List<Double>,
    val pivotPoint: Double,
    val fibonacciLevels: Map<String, Double>
)

@Serializable
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

@Serializable
data class AiChatMessage(
    val role: String, // "system", "user", "assistant"
    val content: String
)

@Serializable
data class AiChatRequest(
    val messages: List<AiChatMessage>,
    val temperature: Double = 0.3,
    val maxTokens: Int = 2048
)

@Serializable
data class AiChatResponse(
    val content: String,
    val provider: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tokensUsed: Int
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