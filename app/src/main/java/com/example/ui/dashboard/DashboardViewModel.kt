package com.example.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.*
import com.example.data.repository.TradingRepository
import com.example.domain.model.*
import com.example.domain.risk.AdvancedRiskManager
import com.example.domain.risk.PortfolioRiskMetrics
import com.example.trading.TradingEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: TradingRepository,
    private val engine: TradingEngine,
    private val riskManager: AdvancedRiskManager
) : ViewModel() {

    val config: StateFlow<RoomConfig?> = flow {
        emit(repository.getOrCreateConfig())
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    val stateMachineState: StateFlow<StateMachineState> = engine.stateMachine.currentState
    val stateReason: StateFlow<String> = engine.stateMachine.stateReason
    val stateHistory: StateFlow<List<StateTransitionRecord>> = engine.stateMachine.stateHistory
    val connectionState: StateFlow<ConnectionState> = engine.connectionState
    val accountInfo: StateFlow<AccountInfo> = engine.currentAccount
    val activeQuotes: StateFlow<Map<String, Quote>> = engine.activeQuotes
    val latestSignal: StateFlow<Signal?> = engine.latestSignal
    val dailyPnl: StateFlow<Double> = engine.dailyProfitLoss
    val circuitBreakerCount: StateFlow<Int> = engine.stateMachine.circuitBreakerCount
    val isCircuitOpen: StateFlow<Boolean> = engine.stateMachine.isCircuitOpen

    val openPositions: StateFlow<List<RoomPosition>> = repository.openPositionsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val recentTrades: StateFlow<List<RoomTrade>> = repository.allTradesFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val stateTransitions: StateFlow<List<RoomStateTransition>> = repository.getStateTransitionsFlow().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private val _portfolioRisk = MutableStateFlow<PortfolioRiskMetrics?>(null)
    val portfolioRisk: StateFlow<PortfolioRiskMetrics?> = _portfolioRisk.asStateFlow()

    private val _sizingMethod = MutableStateFlow(SizingMethod.FIXED_FRACTIONAL)
    val sizingMethod: StateFlow<SizingMethod> = _sizingMethod.asStateFlow()

    init {
        viewModelScope.launch {
            launch {
                openPositions.collect { positions ->
                    updatePortfolioRisk(positions)
                }
            }
            launch {
                activeQuotes.collect { quotes ->
                    for ((symbol, quote) in quotes) {
                        riskManager.updatePriceHistory(symbol, quote.bid)
                    }
                }
            }
        }
    }

    private suspend fun updatePortfolioRisk(positions: List<RoomPosition>) {
        val account = accountInfo.value
        val quotes = activeQuotes.value
        val domainPositions = positions.map { pos ->
            Position(
                id = pos.id,
                symbol = pos.symbol,
                direction = TradeDirection.valueOf(pos.direction),
                volume = pos.volume,
                entryPrice = pos.entryPrice,
                currentPrice = pos.currentPrice,
                stopLoss = pos.stopLoss,
                takeProfit = pos.takeProfit,
                unrealizedProfit = pos.unrealizedProfit,
                unrealizedR = pos.unrealizedR,
                openedAt = pos.openedAt,
                mode = TradingMode.valueOf(pos.mode)
            )
        }
        val metrics = riskManager.calculatePortfolioRisk(domainPositions, account, quotes)
        _portfolioRisk.value = metrics

        if (riskManager.shouldForceCloseAll(account)) {
            engine.closeAllPositions("Max drawdown breach - auto close")
        }
    }

    fun toggleTradingBot(enable: Boolean) {
        viewModelScope.launch {
            if (enable) {
                engine.start()
            } else {
                engine.stop("User stopped bot")
            }
        }
    }

    fun triggerEmergencyStop() {
        viewModelScope.launch {
            engine.triggerEmergencyStop("User Emergency Stop triggered from Dashboard")
        }
    }

    fun closeAllPositions() {
        viewModelScope.launch {
            engine.closeAllPositions("Manual liquidation from Dashboard")
        }
    }

    fun closeSinglePosition(positionId: String) {
        viewModelScope.launch {
            engine.closeSinglePosition(positionId, CloseReason.MANUAL)
        }
    }

    fun resetSafeMode() {
        viewModelScope.launch {
            engine.resetSafeMode()
        }
    }

    fun resetCircuitBreaker() {
        engine.stateMachine.resetCircuitBreaker()
    }

    fun setSizingMethod(method: SizingMethod) {
        _sizingMethod.value = method
    }
}
