package com.example.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.EdgeTraderApp
import com.example.data.entities.BotConfigEntity
import com.example.data.firestore.FirestoreRepository
import com.example.domain.model.*
import com.example.domain.risk.AdvancedRiskManager
import com.example.domain.risk.PortfolioRiskMetrics
import com.example.domain.risk.SizingMethod
import com.example.service.TradingForegroundService
import com.example.trading.TradingEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val context: Context = EdgeTraderApp.instance,
    private val repository: FirestoreRepository = EdgeTraderApp.instance.firestoreRepository,
    private val engine: TradingEngine = EdgeTraderApp.instance.tradingEngine
) : ViewModel() {

    private val riskManager = AdvancedRiskManager()

    val config: StateFlow<BotConfigEntity?> = repository.configFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    val stateMachineState: StateFlow<StateMachineState> = engine.stateMachine.currentState
    val stateReason: StateFlow<String> = engine.stateMachine.stateReason
    val connectionState: StateFlow<ConnectionState> = engine.connectionState
    val accountInfo: StateFlow<AccountInfo> = engine.currentAccount
    val activeQuotes: StateFlow<Map<String, Quote>> = engine.activeQuotes
    val latestSignal: StateFlow<Signal?> = engine.latestSignal
    val dailyPnl: StateFlow<Double> = engine.dailyProfitLoss
    val circuitBreakerCount: StateFlow<Int> = engine.stateMachine.circuitBreakerCount
    val isCircuitOpen: StateFlow<Boolean> = engine.stateMachine.isCircuitOpen

    val openPositions: StateFlow<List<Position>> = repository.openPositionsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val recentTrades: StateFlow<List<Trade>> = repository.allTradesFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val brokerAccounts: StateFlow<List<BrokerAccount>> = engine.accountManager?.accounts?.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    ) ?: MutableStateFlow(emptyList())

    val activeBrokerAccount: StateFlow<BrokerAccount?> = engine.accountManager?.activeAccount?.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    ) ?: MutableStateFlow(null)

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

    private fun updatePortfolioRisk(positions: List<Position>) {
        val account = accountInfo.value
        val quotes = activeQuotes.value
        val metrics = riskManager.calculatePortfolioRisk(positions, account, quotes)
        _portfolioRisk.value = metrics

        viewModelScope.launch {
            if (riskManager.shouldForceCloseAll(account)) {
                engine.closeAllPositions("Max drawdown breach - auto close")
            }
        }
    }

    fun switchBrokerAccount(accountId: String) {
        viewModelScope.launch {
            engine.switchBrokerAccount(accountId)
        }
    }

    fun toggleTradingBot(enable: Boolean) {
        viewModelScope.launch {
            val cfg = repository.getOrCreateConfig()
            repository.updateConfig(cfg.copy(isBotEnabled = enable, emergencyStop = false))
            if (enable) {
                TradingForegroundService.startService(context)
            } else {
                TradingForegroundService.stopService(context)
            }
        }
    }

    fun triggerEmergencyStop() {
        viewModelScope.launch {
            engine.triggerEmergencyStop("User Emergency Stop triggered from Dashboard")
            TradingForegroundService.stopService(context)
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
