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

class GeminiProvider(
    private val secureStorage: SecureStorage = SecureStorage(com.example.EdgeTraderApp.instance)
) : AiProvider {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val helper = NvidiaLlmProvider(secureStorage)

    override val config: AiProviderConfig
        get() = AiProviderConfig(
            id = AiProviderType.GEMINI,
            name = "Google Gemini Flash",
            apiEndpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            apiKey = secureStorage.getGeminiApiKey(),
            model = "gemini-2.5-flash",
            enabled = secureStorage.getGeminiApiKey().isNotBlank(),
            temperature = 0.3,
            maxTokens = 2048,
            timeoutSeconds = 30
        )

    override suspend fun analyze(request: AiAnalysisRequest): Result<AiAnalysisResponse> {
        return withContext(Dispatchers.IO) {
            try {
                if (!config.enabled) {
                    return@withContext Result.failure(IllegalStateException("Gemini API key not configured"))
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
                val contentsArray = JSONArray()
                request.messages.forEach { msg ->
                    val contentObj = JSONObject()
                    contentObj.put("role", if (msg.role == "user") "user" else "model")
                    val partsArray = JSONArray()
                    partsArray.put(JSONObject().put("text", msg.content))
                    contentObj.put("parts", partsArray)
                    contentsArray.put(contentObj)
                }

                val genConfig = JSONObject().apply {
                    put("temperature", request.temperature)
                    put("maxOutputTokens", request.maxTokens)
                    put("topP", 0.95)
                    put("topK", 40)
                }

                val rootObj = JSONObject().apply {
                    put("contents", contentsArray)
                    put("generationConfig", genConfig)
                }

                val requestBody = rootObj.toString().toRequestBody("application/json".toMediaType())
                val url = "${config.apiEndpoint}?key=${config.apiKey}"
                val httpRequest = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(httpRequest).execute()
                val responseBodyStr = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        RuntimeException("Gemini API error: ${response.code} - $responseBodyStr")
                    )
                }

                val respObj = JSONObject(responseBodyStr)
                val candidates = respObj.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val contentObj = firstCandidate?.optJSONObject("content")
                val parts = contentObj?.optJSONArray("parts")
                val text = parts?.optJSONObject(0)?.optString("text", "") ?: ""
                val usageMetadata = respObj.optJSONObject("usageMetadata")
                val tokensUsed = usageMetadata?.optInt("totalTokenCount", 0) ?: 0

                Result.success(AiChatResponse(
                    content = text,
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
                    return@withContext Result.failure(IllegalStateException("Gemini API key not configured"))
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
