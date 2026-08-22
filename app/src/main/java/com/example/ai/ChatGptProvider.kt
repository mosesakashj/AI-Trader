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

class ChatGptProvider(
    private val secureStorage: SecureStorage,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : AiProvider {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(60))
        .build()

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
                val requestBody = json.encodeToString(
                    ChatGptRequest(
                        model = config.model,
                        messages = request.messages.map { ChatGptMessage(it.role, it.content) },
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
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build()

                val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())

                if (response.statusCode() != 200) {
                    return@withContext Result.failure(
                        RuntimeException("ChatGPT API error: ${response.statusCode()} - ${response.body()}")
                    )
                }

                val chatGptResponse = json.decodeFromString<ChatGptResponse>(response.body())
                val content = chatGptResponse.choices.firstOrNull()?.message?.content ?: ""
                val tokensUsed = chatGptResponse.usage?.total_tokens ?: 0

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

    private fun getSystemPrompt(): String = NvidiaLlmProvider().getSystemPrompt()

    private fun buildAnalysisPrompt(request: AiAnalysisRequest): String = NvidiaLlmProvider().buildAnalysisPrompt(request)

    private fun parseAnalysisResponse(content: String, request: AiAnalysisRequest): Result<AiAnalysisResponse> {
        return NvidiaLlmProvider().parseAnalysisResponse(content, request)
    }

    @Serializable
    private data class ChatGptRequest(
        val model: String,
        val messages: List<ChatGptMessage>,
        val temperature: Double,
        val max_tokens: Int,
        val stream: Boolean
    )

    @Serializable
    private data class ChatGptMessage(
        val role: String,
        val content: String
    )

    @Serializable
    private data class ChatGptResponse(
        val id: String,
        val object: String,
        val created: Long,
        val model: String,
        val choices: List<ChatGptChoice>,
        val usage: ChatGptUsage?
    )

    @Serializable
    private data class ChatGptChoice(
        val index: Int,
        val message: ChatGptMessage?,
        val finish_reason: String?
    )

    @Serializable
    private data class ChatGptUsage(
        val prompt_tokens: Int,
        val completion_tokens: Int,
        val total_tokens: Int
    )
}