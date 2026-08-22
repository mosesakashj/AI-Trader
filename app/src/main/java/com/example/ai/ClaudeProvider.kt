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

class ClaudeProvider(
    private val secureStorage: SecureStorage,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : AiProvider {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(60))
        .build()

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
                val messages = request.messages.filter { it.role != "system" }.map { msg ->
                    ClaudeMessage(role = msg.role, content = msg.content)
                }

                val systemPrompt = request.messages.firstOrNull { it.role == "system" }?.content
                    ?: getSystemPrompt()

                val requestBody = json.encodeToString(
                    ClaudeRequest(
                        model = config.model,
                        max_tokens = request.maxTokens,
                        temperature = request.temperature,
                        system = systemPrompt,
                        messages = messages
                    )
                )

                val httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(config.apiEndpoint))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds.toLong()))
                    .header("x-api-key", config.apiKey)
                    .header("Content-Type", "application/json")
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build()

                val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())

                if (response.statusCode() != 200) {
                    return@withContext Result.failure(
                        RuntimeException("Claude API error: ${response.statusCode()} - ${response.body()}")
                    )
                }

                val claudeResponse = json.decodeFromString<ClaudeResponse>(response.body())
                val content = claudeResponse.content.firstOrNull()?.text ?: ""
                val tokensUsed = (claudeResponse.usage?.input_tokens ?: 0) + (claudeResponse.usage?.output_tokens ?: 0)

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

    private fun getSystemPrompt(): String = NvidiaLlmProvider().getSystemPrompt()

    private fun buildAnalysisPrompt(request: AiAnalysisRequest): String = NvidiaLlmProvider().buildAnalysisPrompt(request)

    private fun parseAnalysisResponse(content: String, request: AiAnalysisRequest): Result<AiAnalysisResponse> {
        return NvidiaLlmProvider().parseAnalysisResponse(content, request)
    }

    @Serializable
    private data class ClaudeRequest(
        val model: String,
        val max_tokens: Int,
        val temperature: Double,
        val system: String,
        val messages: List<ClaudeMessage>
    )

    @Serializable
    private data class ClaudeMessage(
        val role: String,
        val content: String
    )

    @Serializable
    private data class ClaudeResponse(
        val id: String,
        val type: String,
        val role: String,
        val content: List<ClaudeContent>,
        val model: String,
        val stop_reason: String?,
        val stop_sequence: String?,
        val usage: ClaudeUsage?
    )

    @Serializable
    private data class ClaudeContent(
        val type: String,
        val text: String
    )

    @Serializable
    private data class ClaudeUsage(
        val input_tokens: Int,
        val output_tokens: Int
    )
}