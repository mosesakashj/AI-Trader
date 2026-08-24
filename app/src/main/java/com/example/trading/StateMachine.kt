package com.example.trading

import com.example.domain.model.StateMachineState
import com.example.data.local.RoomStateTransition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedDeque

data class StateTransitionRecord(
    val fromState: StateMachineState,
    val toState: StateMachineState,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 0
)

class StateMachine(initialState: StateMachineState = StateMachineState.STOPPED) {

    private val _currentState = MutableStateFlow(initialState)
    val currentState: StateFlow<StateMachineState> = _currentState.asStateFlow()

    private val _stateReason = MutableStateFlow("System initialized")
    val stateReason: StateFlow<String> = _stateReason.asStateFlow()

    private val _stateHistory = MutableStateFlow<List<StateTransitionRecord>>(emptyList())
    val stateHistory: StateFlow<List<StateTransitionRecord>> = _stateHistory.asStateFlow()

    private val transitionHistory = ConcurrentLinkedDeque<StateTransitionRecord>()
    private var lastTransitionTime = System.currentTimeMillis()

    private val _circuitBreakerCount = MutableStateFlow(0)
    val circuitBreakerCount: StateFlow<Int> = _circuitBreakerCount.asStateFlow()

    private val _isCircuitOpen = MutableStateFlow(false)
    val isCircuitOpen: StateFlow<Boolean> = _isCircuitOpen.asStateFlow()

    private var consecutiveErrors = 0
    private val maxConsecutiveErrors = 3

    private val stateTimeouts = mapOf(
        StateMachineState.ANALYZING to 60_000L,
        StateMachineState.EXECUTING to 30_000L,
        StateMachineState.CONNECTING to 45_000L,
        StateMachineState.SYNCING to 30_000L
    )

    @Synchronized
    fun transitionTo(newState: StateMachineState, reason: String = ""): Boolean {
        val curr = _currentState.value
        val now = System.currentTimeMillis()

        if (_isCircuitOpen.value && newState != StateMachineState.SAFE_MODE && newState != StateMachineState.STOPPED) {
            Timber.w("Circuit breaker OPEN, blocking transition to $newState")
            return false
        }

        val isValid = when (curr) {
            StateMachineState.STOPPED -> newState in listOf(
                StateMachineState.STARTING,
                StateMachineState.ERROR,
                StateMachineState.SAFE_MODE
            )
            StateMachineState.STARTING -> newState in listOf(
                StateMachineState.CONNECTING,
                StateMachineState.STOPPED,
                StateMachineState.ERROR,
                StateMachineState.SAFE_MODE
            )
            StateMachineState.CONNECTING -> newState in listOf(
                StateMachineState.SYNCING,
                StateMachineState.PAUSED,
                StateMachineState.ERROR,
                StateMachineState.STOPPED,
                StateMachineState.SAFE_MODE
            )
            StateMachineState.SYNCING -> newState in listOf(
                StateMachineState.READY,
                StateMachineState.POSITION_OPEN,
                StateMachineState.SAFE_MODE,
                StateMachineState.ERROR,
                StateMachineState.STOPPED
            )
            StateMachineState.READY -> newState in listOf(
                StateMachineState.ANALYZING,
                StateMachineState.PAUSED,
                StateMachineState.SAFE_MODE,
                StateMachineState.STOPPING,
                StateMachineState.ERROR
            )
            StateMachineState.ANALYZING -> newState in listOf(
                StateMachineState.SIGNAL_FOUND,
                StateMachineState.READY,
                StateMachineState.PAUSED,
                StateMachineState.SAFE_MODE,
                StateMachineState.STOPPING,
                StateMachineState.ERROR
            )
            StateMachineState.SIGNAL_FOUND -> newState in listOf(
                StateMachineState.VALIDATING,
                StateMachineState.READY,
                StateMachineState.PAUSED,
                StateMachineState.SAFE_MODE,
                StateMachineState.STOPPING,
                StateMachineState.ERROR
            )
            StateMachineState.VALIDATING -> newState in listOf(
                StateMachineState.EXECUTING,
                StateMachineState.READY,
                StateMachineState.PAUSED,
                StateMachineState.SAFE_MODE,
                StateMachineState.STOPPING,
                StateMachineState.ERROR
            )
            StateMachineState.EXECUTING -> newState in listOf(
                StateMachineState.POSITION_OPEN,
                StateMachineState.READY,
                StateMachineState.ERROR,
                StateMachineState.SAFE_MODE
            )
            StateMachineState.POSITION_OPEN -> newState in listOf(
                StateMachineState.READY,
                StateMachineState.PAUSED,
                StateMachineState.SAFE_MODE,
                StateMachineState.STOPPING,
                StateMachineState.ERROR
            )
            StateMachineState.PAUSED -> newState in listOf(
                StateMachineState.CONNECTING,
                StateMachineState.READY,
                StateMachineState.STOPPED,
                StateMachineState.SAFE_MODE,
                StateMachineState.ERROR
            )
            StateMachineState.SAFE_MODE -> newState in listOf(
                StateMachineState.READY,
                StateMachineState.STOPPED,
                StateMachineState.ERROR
            )
            StateMachineState.ERROR -> newState in listOf(
                StateMachineState.STARTING,
                StateMachineState.STOPPED,
                StateMachineState.SAFE_MODE
            )
            StateMachineState.STOPPING -> newState in listOf(
                StateMachineState.STOPPED,
                StateMachineState.ERROR
            )
        }

        if (isValid || newState == StateMachineState.SAFE_MODE || newState == StateMachineState.ERROR) {
            val durationMs = now - lastTransitionTime
            val record = StateTransitionRecord(
                fromState = curr,
                toState = newState,
                reason = reason,
                timestamp = now,
                durationMs = durationMs
            )

            transitionHistory.addFirst(record)
            if (transitionHistory.size > 100) transitionHistory.removeLast()
            _stateHistory.value = transitionHistory.toList()

            _currentState.value = newState
            _stateReason.value = reason
            lastTransitionTime = now

            if (newState == StateMachineState.ERROR) {
                consecutiveErrors++
                _circuitBreakerCount.value = consecutiveErrors
                if (consecutiveErrors >= maxConsecutiveErrors) {
                    _isCircuitOpen.value = true
                    Timber.e("Circuit breaker OPEN after $consecutiveErrors consecutive errors")
                }
            } else if (newState == StateMachineState.READY || newState == StateMachineState.POSITION_OPEN) {
                consecutiveErrors = 0
                _circuitBreakerCount.value = 0
            }

            Timber.d("State: $curr -> $newState ($reason)")
            return true
        }
        return false
    }

    fun forceState(newState: StateMachineState, reason: String) {
        val curr = _currentState.value
        val record = StateTransitionRecord(
            fromState = curr,
            toState = newState,
            reason = "FORCE: $reason",
            timestamp = System.currentTimeMillis()
        )
        transitionHistory.addFirst(record)
        if (transitionHistory.size > 100) transitionHistory.removeLast()
        _stateHistory.value = transitionHistory.toList()

        _currentState.value = newState
        _stateReason.value = reason
        lastTransitionTime = System.currentTimeMillis()
        Timber.w("State FORCED: $curr -> $newState ($reason)")
    }

    fun getStateTimeout(state: StateMachineState): Long? = stateTimeouts[state]

    fun resetCircuitBreaker() {
        consecutiveErrors = 0
        _circuitBreakerCount.value = 0
        _isCircuitOpen.value = false
        Timber.i("Circuit breaker reset")
    }

    fun getTransitionHistory(): List<StateTransitionRecord> = transitionHistory.toList()
}
