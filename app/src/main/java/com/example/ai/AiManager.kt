package com.example.ai

import com.example.security.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AiManager(
    private val secureStorage: SecureStorage
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _providers = MutableStateFlow<List<AiProvider>>(emptyList())
    val providers: StateFlow<List<AiProvider>> = _providers

    private val _activeProviderId = MutableStateFlow<String?>(null)
    val activeProviderId: StateFlow<String?> = _activeProviderId

    private val _lastAnalysis = MutableStateFlow<AiAnalysisResponse?>(null)
    val lastAnalysis: StateFlow<AiAnalysisResponse?> = _lastAnalysis

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError

    init {
        initializeProviders()
    }

    private fun initializeProviders() {
        val nvidiaProvider = NvidiaLlmProvider(secureStorage)
        val geminiProvider = GeminiProvider(secureStorage)
        val claudeProvider = ClaudeProvider(secureStorage)
        val chatGptProvider = ChatGptProvider(secureStorage)

        val providerList = listOf(nvidiaProvider, geminiProvider, claudeProvider, chatGptProvider)
        _providers.value = providerList

        // Auto-select first enabled provider
        val firstEnabled = providerList.firstOrNull { it.config.enabled }
        if (firstEnabled != null) {
            _activeProviderId.value = firstEnabled.config.id
        }
    }

    fun setActiveProvider(providerId: String) {
        _activeProviderId.value = providerId
    }

    suspend fun analyzeMarket(request: AiAnalysisRequest): Result<AiAnalysisResponse> {
        val provider = _providers.value.firstOrNull { it.config.id == _activeProviderId.value }
            ?: return Result.failure(IllegalStateException("No active AI provider selected"))

        if (!provider.config.enabled) {
            return Result.failure(IllegalStateException("Selected provider (${provider.config.name}) is not configured"))
        }

        _isAnalyzing.value = true
        _analysisError.value = null

        return provider.analyze(request).also { result ->
            _isAnalyzing.value = false
            if (result.isSuccess) {
                _lastAnalysis.value = result.getOrNull()
            } else {
                _analysisError.value = result.exceptionOrNull()?.message ?: "Unknown error"
            }
        }
    }

    suspend fun chat(request: AiChatRequest): Result<AiChatResponse> {
        val provider = _providers.value.firstOrNull { it.config.id == _activeProviderId.value }
            ?: return Result.failure(IllegalStateException("No active AI provider selected"))

        if (!provider.config.enabled) {
            return Result.failure(IllegalStateException("Selected provider (${provider.config.name}) is not configured"))
        }

        return provider.chat(request)
    }

    suspend fun testProvider(providerId: String): Result<String> {
        val provider = _providers.value.firstOrNull { it.config.id == providerId }
            ?: return Result.failure(IllegalStateException("Provider not found: $providerId"))

        return provider.testConnection()
    }

    fun refreshProviders() {
        initializeProviders()
    }

    fun shutdown() {
        scope.cancel()
    }
}