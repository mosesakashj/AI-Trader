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

class GeminiProvider(
    private val secureStorage: SecureStorage,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : AiProvider {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    override val config: AiProviderConfig
        get() = AiProviderConfig(
            id = AiProviderType.GEMINI,
            name = "Google Gemini Pro",
            apiEndpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent",
            apiKey = secureStorage.getGeminiApiKey(),
            model = "gemini-1.5-pro",
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

                val prompt = buildAnalysisPrompt(request)
                val chatRequest = AiChatRequest(
                    messages = listOf(
                        AiChatMessage("user", "${getSystemPrompt()}\n\n$prompt")
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
                val contents = request.messages.map { msg ->
                    GeminiContent(
                        role = if (msg.role == "user") "user" else "model",
                        parts = listOf(GeminiPart(msg.content))
                    )
                }

                val requestBody = json.encodeToString(
                    GeminiRequest(
                        contents = contents,
                        generationConfig = GeminiGenerationConfig(
                            temperature = request.temperature,
                            maxOutputTokens = request.maxTokens,
                            topP = 0.95,
                            topK = 40
                        )
                    )
                )

                val httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("${config.apiEndpoint}?key=${config.apiKey}"))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds.toLong()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build()

                val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())

                if (response.statusCode() != 200) {
                    return@withContext Result.failure(
                        RuntimeException("Gemini API error: ${response.statusCode()} - ${response.body()}")
                    )
                }

                val geminiResponse = json.decodeFromString<GeminiResponse>(response.body())
                val content = geminiResponse.candidates.firstOrNull()?.content?.parts.firstOrNull()?.text ?: ""
                val tokensUsed = geminiResponse.usageMetadata?.totalTokenCount ?: 0

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

    private fun getSystemPrompt(): String = NvidiaLlmProvider().getSystemPrompt()

    private fun buildAnalysisPrompt(request: AiAnalysisRequest): String = NvidiaLlmProvider().buildAnalysisPrompt(request)

    private fun parseAnalysisResponse(content: String, request: AiAnalysisRequest): Result<AiAnalysisResponse> {
        return NvidiaLlmProvider().parseAnalysisResponse(content, request)
    }

    @Serializable
    private data class GeminiRequest(
        val contents: List<GeminiContent>,
        val generationConfig: GeminiGenerationConfig
    )

    @Serializable
    private data class GeminiContent(
        val role: String,
        val parts: List<GeminiPart>
    )

    @Serializable
    private data class GeminiPart(
        val text: String
    )

    @Serializable
    private data class GeminiGenerationConfig(
        val temperature: Double,
        val maxOutputTokens: Int,
        val topP: Double,
        val topK: Int
    )

    @Serializable
    private data class GeminiResponse(
        val candidates: List<GeminiCandidate>,
        val usageMetadata: GeminiUsageMetadata?
    )

    @Serializable
    private data class GeminiCandidate(
        val content: GeminiContent?,
        val finishReason: String?
    )

    @Serializable
    private data class GeminiUsageMetadata(
        val promptTokenCount: Int,
        val candidatesTokenCount: Int,
        val totalTokenCount: Int
    )
}