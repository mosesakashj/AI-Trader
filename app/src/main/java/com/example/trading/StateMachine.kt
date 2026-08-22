package com.example.trading

import com.example.domain.model.StateMachineState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StateMachine(initialState: StateMachineState = StateMachineState.STOPPED) {

    private val _currentState = MutableStateFlow(initialState)
    val currentState: StateFlow<StateMachineState> = _currentState.asStateFlow()

    private val _stateReason = MutableStateFlow("System initialized")
    val stateReason: StateFlow<String> = _stateReason.asStateFlow()

    @Synchronized
    fun transitionTo(newState: StateMachineState, reason: String = ""): Boolean {
        val curr = _currentState.value

        // Enforce valid state machine transitions
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
            _currentState.value = newState
            _stateReason.value = reason
            return true
        }
        return false
    }

    fun forceState(newState: StateMachineState, reason: String) {
        _currentState.value = newState
        _stateReason.value = reason
    }
}
