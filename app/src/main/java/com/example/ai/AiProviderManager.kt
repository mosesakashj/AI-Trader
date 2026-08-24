package com.example.ai

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

data class RateLimitInfo(
    val providerId: String,
    var requestCount: Int = 0,
    var tokenCount: Long = 0,
    val dailyLimit: Int = 1000,
    val tokenLimit: Long = 100_000,
    val windowStart: Long = System.currentTimeMillis()
)

data class CacheEntry(
    val response: AiAnalysisResponse,
    val timestamp: Long,
    val ttlMs: Long = 300_000L
) {
    fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttlMs
}

class AiProviderManager(
    private val providers: List<AiProvider>
) {
    private val rateLimits = ConcurrentHashMap<String, RateLimitInfo>()
    private val responseCache = ConcurrentHashMap<String, CacheEntry>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _activeProviderIndex = MutableStateFlow(0)
    val activeProviderIndex: StateFlow<Int> = _activeProviderIndex.asStateFlow()

    private val _failoverCount = MutableStateFlow(0)
    val failoverCount: StateFlow<Int> = _failoverCount.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        providers.forEach { provider ->
            rateLimits[provider.config.id] = RateLimitInfo(providerId = provider.config.id)
        }
    }

    suspend fun analyzeWithFailover(request: AiAnalysisRequest): Result<AiAnalysisResponse> {
        val cacheKey = buildCacheKey(request)
        responseCache[cacheKey]?.let { cached ->
            if (!cached.isExpired()) {
                Timber.d("Cache hit for analysis request")
                return Result.success(cached.response)
            }
            responseCache.remove(cacheKey)
        }

        var lastException: Exception? = null
        val startIndex = _activeProviderIndex.value

        for (i in providers.indices) {
            val index = (startIndex + i) % providers.size
            val provider = providers[index]

            if (!provider.config.enabled) continue

            if (isRateLimited(provider.config.id)) {
                Timber.w("Provider ${provider.config.name} is rate limited, skipping")
                continue
            }

            try {
                Timber.d("Attempting analysis with ${provider.config.name}")
                val result = provider.analyze(request)

                if (result.isSuccess) {
                    _activeProviderIndex.value = index
                    _lastError.value = null

                    recordUsage(provider.config.id, estimateTokens(request))

                    responseCache[cacheKey] = CacheEntry(
                        response = result.getOrNull()!!,
                        timestamp = System.currentTimeMillis()
                    )

                    return result
                } else {
                    lastException = result.exceptionOrNull() as? Exception
                    Timber.w("Provider ${provider.config.name} failed: ${lastException?.message}")
                }
            } catch (e: Exception) {
                lastException = e
                Timber.w("Provider ${provider.config.name} exception: ${e.message}")
            }
        }

        _lastError.value = lastException?.message ?: "All providers failed"
        _failoverCount.value++
        return Result.failure(lastException ?: IllegalStateException("No providers available"))
    }

    suspend fun chatWithFailover(request: AiChatRequest): Result<AiChatResponse> {
        var lastException: Exception? = null
        val startIndex = _activeProviderIndex.value

        for (i in providers.indices) {
            val index = (startIndex + i) % providers.size
            val provider = providers[index]

            if (!provider.config.enabled) continue
            if (isRateLimited(provider.config.id)) continue

            try {
                val result = provider.chat(request)
                if (result.isSuccess) {
                    _activeProviderIndex.value = index
                    recordUsage(provider.config.id, request.messages.sumOf { it.content.length } / 4)
                    return result
                }
                lastException = result.exceptionOrNull() as? Exception
            } catch (e: Exception) {
                lastException = e
            }
        }

        return Result.failure(lastException ?: IllegalStateException("No providers available"))
    }

    private fun isRateLimited(providerId: String): Boolean {
        val info = rateLimits[providerId] ?: return false
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L

        if (now - info.windowStart > dayMs) {
            info.requestCount = 0
            info.tokenCount = 0
            info.windowStart = now
            return false
        }

        return info.requestCount >= info.dailyLimit || info.tokenCount >= info.tokenLimit
    }

    private fun recordUsage(providerId: String, tokens: Int) {
        val info = rateLimits[providerId] ?: return
        info.requestCount++
        info.tokenCount += tokens
    }

    private fun estimateTokens(request: AiAnalysisRequest): Int {
        return (request.candleData.length + request.indicatorData.length + 500) / 4
    }

    private fun buildCacheKey(request: AiAnalysisRequest): String {
        return "${request.symbol}_${request.timeframe}_${request.candleData.hashCode()}"
    }

    fun clearCache() {
        responseCache.clear()
    }

    fun getUsageStats(): Map<String, RateLimitInfo> = rateLimits.toMap()

    fun setActiveProvider(index: Int) {
        if (index in providers.indices) {
            _activeProviderIndex.value = index
        }
    }

    fun getProviders(): List<AiProvider> = providers
}
