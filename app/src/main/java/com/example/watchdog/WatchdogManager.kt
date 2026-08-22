package com.example.watchdog

import com.example.data.repositories.TradingRepository
import com.example.domain.model.LogLevel
import com.example.domain.model.StateMachineState
import com.example.notifications.AppNotificationManager
import com.example.trading.StateMachine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class WatchdogManager(
    private val repository: TradingRepository,
    private val stateMachine: StateMachine,
    private val notificationManager: AppNotificationManager,
    private val onRecoveryRequested: suspend () -> Unit
) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var watchdogJob: Job? = null

    private val lastEngineHeartbeat = AtomicLong(System.currentTimeMillis())
    private val lastMarketDataHeartbeat = AtomicLong(System.currentTimeMillis())
    private val restartCount = AtomicInteger(0)

    private val _isWatchdogHealthy = MutableStateFlow(true)
    val isWatchdogHealthy: StateFlow<Boolean> = _isWatchdogHealthy.asStateFlow()

    private val heartbeatIntervalMillis = 10_000L // 10s check
    private val timeoutThresholdMillis = 60_000L  // 60s timeout

    fun start() {
        if (watchdogJob?.isActive == true) return

        lastEngineHeartbeat.set(System.currentTimeMillis())
        lastMarketDataHeartbeat.set(System.currentTimeMillis())

        watchdogJob = scope.launch {
            repository.logEvent(
                level = LogLevel.INFO,
                component = "WatchdogManager",
                event = "WATCHDOG_STARTED",
                message = "Watchdog monitor active (Interval: ${heartbeatIntervalMillis / 1000}s, Timeout: ${timeoutThresholdMillis / 1000}s)"
            )

            while (isActive) {
                delay(heartbeatIntervalMillis)
                checkHealth()
            }
        }
    }

    fun stop() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    fun recordEngineHeartbeat() {
        lastEngineHeartbeat.set(System.currentTimeMillis())
        scope.launch {
            repository.updateHeartbeat("TradingEngine", "HEALTHY", "Heartbeat received")
        }
    }

    fun recordMarketDataHeartbeat() {
        lastMarketDataHeartbeat.set(System.currentTimeMillis())
        scope.launch {
            repository.updateHeartbeat("MarketData", "HEALTHY", "Tick received")
        }
    }

    private suspend fun checkHealth() {
        val now = System.currentTimeMillis()
        val engineDiff = now - lastEngineHeartbeat.get()
        val marketDiff = now - lastMarketDataHeartbeat.get()

        val isEngineActive = stateMachine.currentState.value !in listOf(
            StateMachineState.STOPPED,
            StateMachineState.SAFE_MODE,
            StateMachineState.ERROR
        )

        if (isEngineActive && engineDiff > timeoutThresholdMillis) {
            _isWatchdogHealthy.value = false
            val restarts = restartCount.incrementAndGet()

            repository.logEvent(
                level = LogLevel.CRITICAL,
                component = "WatchdogManager",
                event = "HEARTBEAT_TIMEOUT",
                message = "Trading engine heartbeat stalled ($engineDiff ms). Triggering automatic recovery (Attempt: $restarts)"
            )

            notificationManager.notifyBotRestarted("Trading Engine heartbeat timed out", restarts)

            if (restarts > 3) {
                // Too many crashes: force SAFE MODE
                stateMachine.forceState(
                    StateMachineState.SAFE_MODE,
                    "Repeated engine stalls ($restarts attempts). Manual inspection required."
                )
                notificationManager.notifySafeMode("Exceeded max automated recovery restarts (Count: $restarts)")
            } else {
                // Attempt safe automated restart
                try {
                    onRecoveryRequested()
                    lastEngineHeartbeat.set(System.currentTimeMillis())
                    _isWatchdogHealthy.value = true
                } catch (e: Exception) {
                    repository.logEvent(
                        level = LogLevel.ERROR,
                        component = "WatchdogManager",
                        event = "RECOVERY_FAILED",
                        message = "Automated recovery execution failed: ${e.localizedMessage}"
                    )
                }
            }
        } else {
            _isWatchdogHealthy.value = true
        }
    }

    fun getRestartCount(): Int = restartCount.get()
    fun getLastEngineHeartbeat(): Long = lastEngineHeartbeat.get()
    fun getLastMarketDataHeartbeat(): Long = lastMarketDataHeartbeat.get()
}
