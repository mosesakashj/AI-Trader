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

class ClaudeProvider(
    private val secureStorage: SecureStorage = SecureStorage(com.example.EdgeTraderApp.instance)
) : AiProvider {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val helper = NvidiaLlmProvider(secureStorage)

    override val config: AiProviderConfig
        get() = AiProviderConfig(
            id = AiProviderType.CLAUDE,
            name = "Anthropic Claude 3.5 Sonnet",
            apiEndpoint = "https://api.anthropic.com/v1/messages",
            apiKey = secureStorage.getClaudeApiKey(),
            model = "claude-3-5-sonnet-20241022",
            enabled = secureStorage.getClaudeApiKey().isNotBlank(),
            temperature = 0.3,
            maxTokens = 4096,
            timeoutSeconds = 60
        )

    override suspend fun analyze(request: AiAnalysisRequest): Result<AiAnalysisResponse> {
        return withContext(Dispatchers.IO) {
            try {
                if (!config.enabled) {
                    return@withContext Result.failure(IllegalStateException("Claude API key not configured"))
                }

                val prompt = helper.buildAnalysisPrompt(request)
                val chatRequest = AiChatRequest(
                    messages = listOf(
                        AiChatMessage("user", "${helper.getSystemPrompt()}\n\n$prompt")
                    ),
                    temperature = config.temperature,
                    maxTokens = config.maxTokens
                )

                val chatResponse = chat(chatRequest).getOrThrow()
                helper.parseAnalysisResponse(chatResponse.content, request)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun chat(request: AiChatRequest): Result<AiChatResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val messagesArray = JSONArray()
                var systemPrompt = ""

                request.messages.forEach { msg ->
                    if (msg.role == "system") {
                        systemPrompt = msg.content
                    } else {
                        val obj = JSONObject()
                        obj.put("role", if (msg.role == "assistant") "assistant" else "user")
                        obj.put("content", msg.content)
                        messagesArray.put(obj)
                    }
                }

                val jsonBody = JSONObject().apply {
                    put("model", config.model)
                    put("max_tokens", request.maxTokens)
                    put("temperature", request.temperature)
                    if (systemPrompt.isNotBlank()) {
                        put("system", systemPrompt)
                    }
                    put("messages", messagesArray)
                }

                val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url(config.apiEndpoint)
                    .addHeader("x-api-key", config.apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(httpRequest).execute()
                val responseBodyStr = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        RuntimeException("Claude API error: ${response.code} - $responseBodyStr")
                    )
                }

                val respObj = JSONObject(responseBodyStr)
                val contentArray = respObj.optJSONArray("content")
                val text = contentArray?.optJSONObject(0)?.optString("text", "") ?: ""
                val usage = respObj.optJSONObject("usage")
                val inTokens = usage?.optInt("input_tokens", 0) ?: 0
                val outTokens = usage?.optInt("output_tokens", 0) ?: 0

                Result.success(AiChatResponse(
                    content = text,
                    provider = config.name,
                    tokensUsed = inTokens + outTokens
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
                    return@withContext Result.failure(IllegalStateException("Claude API key not configured"))
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
}
