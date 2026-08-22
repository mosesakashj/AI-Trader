package com.example.ai

import com.example.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI
import java.time.Duration
import java.util.UUID

class NvidiaLlmProvider(
    private val secureStorage: SecureStorage,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : AiProvider {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    override val config: AiProviderConfig
        get() = AiProviderConfig(
            id = AiProviderType.NVIDIA,
            name = "NVIDIA Nemotron 3 Ultra",
            apiEndpoint = "https://integrate.api.nvidia.com/v1/chat/completions",
            apiKey = secureStorage.getNvidiaApiKey(),
            model = "nvidia/nemotron-3-ultra",
            enabled = secureStorage.getNvidiaApiKey().isNotBlank(),
            temperature = 0.3,
            maxTokens = 2048,
            timeoutSeconds = 30
        )

    override suspend fun analyze(request: AiAnalysisRequest): Result<AiAnalysisResponse> {
        return withContext(Dispatchers.IO) {
            try {
                if (!config.enabled) {
                    return@withContext Result.failure(IllegalStateException("NVIDIA API key not configured"))
                }

                val prompt = buildAnalysisPrompt(request)
                val chatRequest = AiChatRequest(
                    messages = listOf(
                        AiChatMessage("system", getSystemPrompt()),
                        AiChatMessage("user", prompt)
                    ),
                    temperature = config.temperature,
                    maxTokens = config.maxTokens
                )

                val chatResponse = chat(chatRequest).getOrThrow()
                
                // Parse the response into structured analysis
                parseAnalysisResponse(chatResponse.content, request)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun chat(request: AiChatRequest): Result<AiChatResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = json.encodeToString(
                    NvidiaChatRequest(
                        model = config.model,
                        messages = request.messages.map { NvidiaMessage(it.role, it.content) },
                        temperature = request.temperature,
                        max_tokens = request.maxTokens,
                        stream = false
                    )
                )

                val httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(config.apiEndpoint))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds.toLong()))
                    .header("Authorization", "Bearer ${config.apiKey}")
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build()

                val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())

                if (response.statusCode() != 200) {
                    return@withContext Result.failure(
                        RuntimeException("NVIDIA API error: ${response.statusCode()} - ${response.body()}")
                    )
                }

                val nvidiaResponse = json.decodeFromString<NvidiaChatResponse>(response.body())
                val content = nvidiaResponse.choices.firstOrNull()?.message?.content ?: ""
                val tokensUsed = nvidiaResponse.usage?.total_tokens ?: 0

                Result.success(AiChatResponse(
                    content = content,
                    provider = config.name,
                    tokensUsed = tokensUsed
                ))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun testConnection(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (!config.enabled) {
                    return@withContext Result.failure(IllegalStateException("NVIDIA API key not configured"))
                }

                val testRequest = AiChatRequest(
                    messages = listOf(AiChatMessage("user", "Reply with 'OK' if you receive this message.")),
                    temperature = 0.1,
                    maxTokens = 10
                )

                val response = chat(testRequest).getOrThrow()
                Result.success("Connected to ${config.name} - Response: ${response.content.take(50)}")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun getSystemPrompt(): String {
        return """You are an expert quantitative trading analyst specializing in forex, commodities, and cryptocurrency markets. 
Your task is to analyze market data, technical indicators, and trading signals to provide actionable trading recommendations.

You must respond in a structured format that can be parsed programmatically. Your analysis should include:
1. Clear recommendation (STRONG_BUY, BUY, NEUTRAL, SELL, STRONG_SELL, HOLD_POSITION, CLOSE_POSITION, REDUCE_RISK)
2. Confidence level (0.0 to 1.0)
3. Detailed reasoning
4. Key support/resistance levels
5. Risk assessment with position sizing suggestions
6. Alternative scenarios

Be conservative, risk-aware, and prioritize capital preservation. Consider:
- Trend alignment across timeframes
- Momentum and volatility (ATR, ADX)
- Risk-reward ratios
- Position sizing based on account equity
- Correlation with open positions
- Market session and liquidity conditions
- Spread and slippage impact

Format your response as JSON matching this schema:
{
  "recommendation": "BUY|SELL|NEUTRAL|...",
  "confidence": 0.75,
  "reasoning": "Detailed analysis...",
  "keyLevels": {
    "support": [1.0800, 1.0750],
    "resistance": [1.0900, 1.0950],
    "pivotPoint": 1.0850,
    "fibonacciLevels": {"0.382": 1.0820, "0.618": 1.0880}
  },
  "riskAssessment": {
    "riskLevel": "MODERATE",
    "maxRiskPercent": 0.25,
    "suggestedPositionSize": 0.15,
    "stopLossDistance": 0.0050,
    "riskRewardRatio": 2.0,
    "warnings": ["High spread during Asian session"]
  },
  "alternativeScenarios": ["If price breaks below 1.0800, bias shifts bearish"]
}"""
    }

    private fun buildAnalysisPrompt(request: AiAnalysisRequest): String {
        val candlesStr = request.recentCandles.takeLast(20).joinToString("\n") { c ->
            "  ${c.openTime}: O=${c.open} H=${c.high} L=${c.low} C=${c.close} V=${c.volume}"
        }
        
        val indicatorsStr = request.indicators.entries.joinToString("\n") { (k, v) -> "  $k: $v" }

        val signalInfo = if (request.signalDirection != null) {
            """
            ACTIVE SIGNAL:
            - Direction: ${request.signalDirection}
            - Entry: ${request.signalEntry}
            - Stop Loss: ${request.signalStopLoss}
            - Take Profit: ${request.signalTakeProfit}
            """
        } else {
            "NO ACTIVE SIGNAL - Analyzing for new setup"
        }

        return """
        MARKET ANALYSIS REQUEST
        ========================
        Symbol: ${request.symbol}
        Timeframe: ${request.timeframe}
        Current Price: ${request.currentPrice}
        
        $signalInfo
        
        TECHNICAL INDICATORS:
        $indicatorsStr
        
        RECENT CANDLES (last 20):
        $candlesStr
        
        ACCOUNT STATE:
        - Equity: \$${request.accountEquity}
        - Open Positions: ${request.openPositions}
        - Daily P&L: \$${request.dailyPnL}
        - Risk per Trade: ${request.riskPercent}%
        
        Please provide a comprehensive analysis following the response format specified in the system prompt.
        """.trimIndent()
    }

    private fun parseAnalysisResponse(content: String, request: AiAnalysisRequest): Result<AiAnalysisResponse> {
        return try {
            // Try to extract JSON from the response
            val jsonStart = content.indexOf('{')
            val jsonEnd = content.lastIndexOf('}') + 1
            
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonStr = content.substring(jsonStart, jsonEnd)
                val parsed = json.decodeFromString<AiAnalysisResponseJson>(jsonStr)
                
                Result.success(AiAnalysisResponse(
                    provider = config.name,
                    recommendation = AiRecommendation.valueOf(parsed.recommendation),
                    confidence = parsed.confidence.coerceIn(0.0, 1.0),
                    reasoning = parsed.reasoning,
                    keyLevels = KeyLevels(
                        support = parsed.keyLevels.support,
                        resistance = parsed.keyLevels.resistance,
                        pivotPoint = parsed.keyLevels.pivotPoint,
                        fibonacciLevels = parsed.keyLevels.fibonacciLevels
                    ),
                    riskAssessment = RiskAssessment(
                        riskLevel = RiskLevel.valueOf(parsed.riskAssessment.riskLevel),
                        maxRiskPercent = parsed.riskAssessment.maxRiskPercent,
                        suggestedPositionSize = parsed.riskAssessment.suggestedPositionSize,
                        stopLossDistance = parsed.riskAssessment.stopLossDistance,
                        riskRewardRatio = parsed.riskAssessment.riskRewardRatio,
                        warnings = parsed.riskAssessment.warnings
                    ),
                    alternativeScenarios = parsed.alternativeScenarios
                ))
            } else {
                // Fallback: create a basic response from text
                Result.success(createFallbackResponse(content, request))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createFallbackResponse(content: String, request: AiAnalysisRequest): AiAnalysisResponse {
        val lowerContent = content.lowercase()
        val recommendation = when {
            lowerContent.contains("strong buy") -> AiRecommendation.STRONG_BUY
            lowerContent.contains("buy") -> AiRecommendation.BUY
            lowerContent.contains("strong sell") -> AiRecommendation.STRONG_SELL
            lowerContent.contains("sell") -> AiRecommendation.SELL
            lowerContent.contains("close") -> AiRecommendation.CLOSE_POSITION
            lowerContent.contains("reduce") -> AiRecommendation.REDUCE_RISK
            lowerContent.contains("hold") -> AiRecommendation.HOLD_POSITION
            else -> AiRecommendation.NEUTRAL
        }

        return AiAnalysisResponse(
            provider = config.name,
            recommendation = recommendation,
            confidence = 0.5,
            reasoning = "AI response parsed from text (JSON parsing failed): $content",
            keyLevels = KeyLevels(emptyList(), emptyList(), request.currentPrice, emptyMap()),
            riskAssessment = RiskAssessment(
                riskLevel = RiskLevel.MODERATE,
                maxRiskPercent = request.riskPercent,
                suggestedPositionSize = 0.1,
                stopLossDistance = 0.0,
                riskRewardRatio = 2.0,
                warnings = listOf("AI response parsing failed - using defaults")
            ),
            alternativeScenarios = emptyList()
        )
    }

    @Serializable
    private data class NvidiaChatRequest(
        val model: String,
        val messages: List<NvidiaMessage>,
        val temperature: Double,
        val max_tokens: Int,
        val stream: Boolean
    )

    @Serializable
    private data class NvidiaMessage(
        val role: String,
        val content: String
    )

    @Serializable
    private data class NvidiaChatResponse(
        val id: String = UUID.randomUUID().toString(),
        val choices: List<NvidiaChoice>,
        val usage: NvidiaUsage?
    )

    @Serializable
    private data class NvidiaChoice(
        val message: NvidiaMessage?,
        val finish_reason: String?
    )

    @Serializable
    private data class NvidiaUsage(
        val prompt_tokens: Int,
        val completion_tokens: Int,
        val total_tokens: Int
    )

    @Serializable
    private data class AiAnalysisResponseJson(
        val recommendation: String,
        val confidence: Double,
        val reasoning: String,
        val keyLevels: KeyLevelsJson,
        val riskAssessment: RiskAssessmentJson,
        val alternativeScenarios: List<String>
    )

    @Serializable
    private data class KeyLevelsJson(
        val support: List<Double>,
        val resistance: List<Double>,
        val pivotPoint: Double,
        val fibonacciLevels: Map<String, Double>
    )

    @Serializable
    private data class RiskAssessmentJson(
        val riskLevel: String,
        val maxRiskPercent: Double,
        val suggestedPositionSize: Double,
        val stopLossDistance: Double,
        val riskRewardRatio: Double,
        val warnings: List<String>
    )
}