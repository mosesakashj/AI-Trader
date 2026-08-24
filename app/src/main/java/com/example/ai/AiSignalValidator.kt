package com.example.ai

import com.example.domain.model.*
import timber.log.Timber

data class AiSignalValidation(
    val shouldExecute: Boolean,
    val confidence: Double,
    val reasoning: String,
    val suggestedAdjustments: Map<String, Any> = emptyMap()
)

class AiSignalValidator(
    private val providerManager: AiProviderManager
) {

    suspend fun validateSignal(
        signal: Signal,
        marketContext: Map<String, Any>,
        accountContext: Map<String, Any>
    ): AiSignalValidation {
        val prompt = buildValidationPrompt(signal, marketContext, accountContext)

        val request = AiChatRequest(
            messages = listOf(
                AiChatMessage("system", getValidationSystemPrompt()),
                AiChatMessage("user", prompt)
            ),
            temperature = 0.2,
            maxTokens = 800
        )

        return try {
            val result = providerManager.chatWithFailover(request)
            if (result.isSuccess) {
                parseValidationResponse(result.getOrNull()?.content ?: "")
            } else {
                AiSignalValidation(
                    shouldExecute = true,
                    confidence = 0.5,
                    reasoning = "AI validation unavailable, proceeding with default confidence"
                )
            }
        } catch (e: Exception) {
            Timber.w("AI signal validation failed: ${e.message}")
            AiSignalValidation(
                shouldExecute = true,
                confidence = 0.5,
                reasoning = "AI validation error: ${e.message}"
            )
        }
    }

    private fun buildValidationPrompt(
        signal: Signal,
        marketContext: Map<String, Any>,
        accountContext: Map<String, Any>
    ): String {
        return """
            Validate this trading signal for execution:

            SIGNAL DETAILS:
            - Symbol: ${signal.symbol}
            - Direction: ${signal.direction}
            - Entry: ${signal.price}
            - Stop Loss: ${signal.stopLoss}
            - Take Profit: ${signal.takeProfit}
            - R:R Ratio: ${signal.rrRatio}
            - Strategy: ${signal.explanation.decision}

            INDICATOR CHECKS:
            - Trend: ${signal.explanation.trendCheck}
            - ADX: ${signal.explanation.adxCheck} (${signal.explanation.adx})
            - Pullback: ${signal.explanation.pullbackCheck}
            - Candle: ${signal.explanation.candleCheck}
            - Spread: ${signal.explanation.spreadCheck}
            - Risk: ${signal.explanation.riskCheck}
            - Session: ${signal.explanation.sessionCheck}

            MARKET CONTEXT:
            ${marketContext.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }}

            ACCOUNT CONTEXT:
            ${accountContext.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }}

            Provide your assessment as JSON:
            {"shouldExecute": bool, "confidence": 0.0-1.0, "reasoning": "...", "adjustments": {}}
        """.trimIndent()
    }

    private fun getValidationSystemPrompt(): String {
        return """
            You are an expert trading signal validator. Analyze the signal against market conditions
            and account state. Consider:
            1. Technical alignment (trend, momentum, support/resistance)
            2. Risk-reward appropriateness
            3. Market volatility regime
            4. Account equity and margin state
            5. Potential news catalysts

            Be conservative: reject signals with confidence below 0.6.
            Always respond with valid JSON.
        """.trimIndent()
    }

    private fun parseValidationResponse(content: String): AiSignalValidation {
        return try {
            val jsonStr = content.substringAfter("{").substringBeforeLast("}")
            val shouldExecute = jsonStr.contains("\"shouldExecute\": true") ||
                jsonStr.contains("\"shouldExecute\":true")
            val confidenceMatch = Regex("\"confidence\":\\s*([0-9.]+)").find(jsonStr)
            val confidence = confidenceMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.5
            val reasoningMatch = Regex("\"reasoning\":\\s*\"([^\"]+)\"").find(jsonStr)
            val reasoning = reasoningMatch?.groupValues?.get(1) ?: "No reasoning provided"

            AiSignalValidation(
                shouldExecute = shouldExecute && confidence >= 0.6,
                confidence = confidence,
                reasoning = reasoning
            )
        } catch (e: Exception) {
            AiSignalValidation(
                shouldExecute = true,
                confidence = 0.5,
                reasoning = "Failed to parse AI response"
            )
        }
    }
}
