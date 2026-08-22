package com.example.ai

import com.example.security.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NvidiaLlmProvider(
    private val secureStorage: SecureStorage = SecureStorage(com.example.EdgeTraderApp.instance)
) : AiProvider {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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
                parseAnalysisResponse(chatResponse.content, request)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun chat(request: AiChatRequest): Result<AiChatResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val messagesArray = JSONArray()
                request.messages.forEach { msg ->
                    val obj = JSONObject()
                    obj.put("role", msg.role)
                    obj.put("content", msg.content)
                    messagesArray.put(obj)
                }

                val jsonBody = JSONObject().apply {
                    put("model", config.model)
                    put("messages", messagesArray)
                    put("temperature", request.temperature)
                    put("max_tokens", request.maxTokens)
                    put("stream", false)
                }

                val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url(config.apiEndpoint)
                    .addHeader("Authorization", "Bearer ${config.apiKey}")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(httpRequest).execute()
                val responseBodyStr = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        RuntimeException("NVIDIA API error: ${response.code} - $responseBodyStr")
                    )
                }

                val respObj = JSONObject(responseBodyStr)
                val choices = respObj.optJSONArray("choices")
                val firstChoice = choices?.optJSONObject(0)
                val msgObj = firstChoice?.optJSONObject("message")
                val content = msgObj?.optString("content", "") ?: ""
                val usage = respObj.optJSONObject("usage")
                val tokensUsed = usage?.optInt("total_tokens", 0) ?: 0

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

    fun getSystemPrompt(): String {
        return """You are an expert quantitative trading analyst specializing in forex, commodities, and cryptocurrency markets. 
Your task is to analyze market data, technical indicators, and trading signals to provide actionable trading recommendations.

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

    fun buildAnalysisPrompt(request: AiAnalysisRequest): String {
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
        - Equity: $${request.accountEquity}
        - Open Positions: ${request.openPositions}
        - Daily P&L: $${request.dailyPnL}
        - Risk per Trade: ${request.riskPercent}%
        
        Please provide a comprehensive analysis following the response format specified in the system prompt.
        """.trimIndent()
    }

    fun parseAnalysisResponse(content: String, request: AiAnalysisRequest): Result<AiAnalysisResponse> {
        return try {
            val jsonStart = content.indexOf('{')
            val jsonEnd = content.lastIndexOf('}') + 1
            
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonStr = content.substring(jsonStart, jsonEnd)
                val obj = JSONObject(jsonStr)
                
                val recStr = obj.optString("recommendation", "NEUTRAL")
                val rec = runCatching { AiRecommendation.valueOf(recStr) }.getOrDefault(AiRecommendation.NEUTRAL)
                val conf = obj.optDouble("confidence", 0.5).coerceIn(0.0, 1.0)
                val reasoning = obj.optString("reasoning", "")
                
                val klObj = obj.optJSONObject("keyLevels")
                val supportList = mutableListOf<Double>()
                klObj?.optJSONArray("support")?.let { arr ->
                    for (i in 0 until arr.length()) supportList.add(arr.optDouble(i))
                }
                val resistanceList = mutableListOf<Double>()
                klObj?.optJSONArray("resistance")?.let { arr ->
                    for (i in 0 until arr.length()) resistanceList.add(arr.optDouble(i))
                }
                val pivot = klObj?.optDouble("pivotPoint", request.currentPrice) ?: request.currentPrice
                val fibMap = mutableMapOf<String, Double>()
                klObj?.optJSONObject("fibonacciLevels")?.let { fObj ->
                    fObj.keys().forEach { k -> fibMap[k] = fObj.optDouble(k) }
                }
                
                val raObj = obj.optJSONObject("riskAssessment")
                val rlStr = raObj?.optString("riskLevel", "MODERATE") ?: "MODERATE"
                val rl = runCatching { RiskLevel.valueOf(rlStr) }.getOrDefault(RiskLevel.MODERATE)
                val maxRisk = raObj?.optDouble("maxRiskPercent", request.riskPercent) ?: request.riskPercent
                val posSize = raObj?.optDouble("suggestedPositionSize", 0.1) ?: 0.1
                val slDist = raObj?.optDouble("stopLossDistance", 0.0) ?: 0.0
                val rr = raObj?.optDouble("riskRewardRatio", 2.0) ?: 2.0
                val warnings = mutableListOf<String>()
                raObj?.optJSONArray("warnings")?.let { arr ->
                    for (i in 0 until arr.length()) warnings.add(arr.optString(i))
                }
                
                val altScenarios = mutableListOf<String>()
                obj.optJSONArray("alternativeScenarios")?.let { arr ->
                    for (i in 0 until arr.length()) altScenarios.add(arr.optString(i))
                }
                
                Result.success(AiAnalysisResponse(
                    provider = config.name,
                    recommendation = rec,
                    confidence = conf,
                    reasoning = reasoning,
                    keyLevels = KeyLevels(supportList, resistanceList, pivot, fibMap),
                    riskAssessment = RiskAssessment(rl, maxRisk, posSize, slDist, rr, warnings),
                    alternativeScenarios = altScenarios
                ))
            } else {
                Result.success(createFallbackResponse(content, request))
            }
        } catch (e: Exception) {
            Result.success(createFallbackResponse(content, request))
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
            reasoning = "AI response parsed from text: $content",
            keyLevels = KeyLevels(emptyList(), emptyList(), request.currentPrice, emptyMap()),
            riskAssessment = RiskAssessment(
                riskLevel = RiskLevel.MODERATE,
                maxRiskPercent = request.riskPercent,
                suggestedPositionSize = 0.1,
                stopLossDistance = 0.0,
                riskRewardRatio = 2.0,
                warnings = listOf("AI response parsing formatted from text")
            ),
            alternativeScenarios = emptyList()
        )
    }
}
