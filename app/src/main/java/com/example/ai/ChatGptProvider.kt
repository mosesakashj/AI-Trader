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

class ChatGptProvider(
    private val secureStorage: SecureStorage = SecureStorage(com.example.EdgeTraderApp.instance)
) : AiProvider {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val helper = NvidiaLlmProvider(secureStorage)

    override val config: AiProviderConfig
        get() = AiProviderConfig(
            id = AiProviderType.CHATGPT,
            name = "OpenAI GPT-4o",
            apiEndpoint = "https://api.openai.com/v1/chat/completions",
            apiKey = secureStorage.getChatGptApiKey(),
            model = "gpt-4o",
            enabled = secureStorage.getChatGptApiKey().isNotBlank(),
            temperature = 0.3,
            maxTokens = 4096,
            timeoutSeconds = 60
        )

    override suspend fun analyze(request: AiAnalysisRequest): Result<AiAnalysisResponse> {
        return withContext(Dispatchers.IO) {
            try {
                if (!config.enabled) {
                    return@withContext Result.failure(IllegalStateException("ChatGPT API key not configured"))
                }

                val prompt = helper.buildAnalysisPrompt(request)
                val chatRequest = AiChatRequest(
                    messages = listOf(
                        AiChatMessage("system", helper.getSystemPrompt()),
                        AiChatMessage("user", prompt)
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
                        RuntimeException("ChatGPT API error: ${response.code} - $responseBodyStr")
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
                    return@withContext Result.failure(IllegalStateException("ChatGPT API key not configured"))
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
