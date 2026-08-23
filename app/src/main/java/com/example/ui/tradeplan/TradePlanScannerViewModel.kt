package com.example.ui.tradeplan

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.EdgeTraderApp
import com.example.domain.model.*
import com.example.domain.strategy.TradingStrategy
import com.example.trading.TradingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TradePlanScannerViewModel(
    private val engine: TradingEngine = EdgeTraderApp.instance.tradingEngine
) : ViewModel() {

    private val _tradePlans = MutableStateFlow<List<TradePlan>>(emptyList())
    val tradePlans: StateFlow<List<TradePlan>> = _tradePlans.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _filterDirection = MutableStateFlow<TradeDirection?>(null)
    val filterDirection: StateFlow<TradeDirection?> = _filterDirection.asStateFlow()

    private val _filterStrategy = MutableStateFlow<StrategyType?>(null)
    val filterStrategy: StateFlow<StrategyType?> = _filterStrategy.asStateFlow()

    private val _executionResult = MutableStateFlow<ExecutionState>(ExecutionState.Idle)
    val executionResult: StateFlow<ExecutionState> = _executionResult.asStateFlow()

    val activeQuotes = engine.activeQuotes

    private val _filteredPlans = MutableStateFlow<List<TradePlan>>(emptyList())
    val filteredPlans: StateFlow<List<TradePlan>> = _filteredPlans.asStateFlow()

    fun scanAllPairs() {
        if (_isScanning.value) return
        _isScanning.value = true
        _tradePlans.value = emptyList()

        viewModelScope.launch {
            val allPlans = mutableListOf<TradePlan>()

            withContext(Dispatchers.Default) {
                val symbols = SymbolCatalog.ALL_SYMBOLS.filter { it.enabled }
                val strategyTypes = StrategyType.entries

                for (symbolConfig in symbols) {
                    for (strategyType in strategyTypes) {
                        try {
                            val strategyConfig = buildStrategyConfig(strategyType)
                            val plan = engine.analyzeSymbolForTradePlan(
                                symbol = symbolConfig.symbol,
                                strategyType = strategyType,
                                strategyConfig = strategyConfig
                            )
                            if (plan != null) {
                                allPlans.add(plan)
                            }
                        } catch (_: Exception) {
                            // Skip failed analysis
                        }
                    }
                }
            }

            _tradePlans.value = allPlans.sortedByDescending { it.signal.rrRatio }
            applyFilters()
            _isScanning.value = false
        }
    }

    fun setFilterDirection(direction: TradeDirection?) {
        _filterDirection.value = direction
        applyFilters()
    }

    fun setFilterStrategy(strategy: StrategyType?) {
        _filterStrategy.value = strategy
        applyFilters()
    }

    fun executeTrade(plan: TradePlan) {
        _executionResult.value = ExecutionState.Executing
        viewModelScope.launch {
            try {
                val result = engine.executeManualTrade(plan)
                if (result.success) {
                    _executionResult.value = ExecutionState.Success(
                        "${plan.signal.symbol} ${plan.signal.direction} order filled @ ${result.executedPrice}"
                    )
                } else {
                    _executionResult.value = ExecutionState.Error(
                        result.errorMessage ?: "Order execution failed"
                    )
                }
            } catch (e: Exception) {
                _executionResult.value = ExecutionState.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    fun clearExecutionState() {
        _executionResult.value = ExecutionState.Idle
    }

    private fun applyFilters() {
        val plans = _tradePlans.value.filter { plan ->
            val dirMatch = _filterDirection.value == null || plan.signal.direction == _filterDirection.value
            val stratMatch = _filterStrategy.value == null || plan.strategyType == _filterStrategy.value
            dirMatch && stratMatch
        }
        _filteredPlans.value = plans
    }

    private suspend fun buildStrategyConfig(strategyType: StrategyType): StrategyConfig {
        val config = runCatching {
            engine.repository.getOrCreateConfig()
        }.getOrNull()

        val tradeMode = try {
            com.example.domain.model.TradeMode.valueOf(config?.tradeMode ?: "BALANCED")
        } catch (_: Exception) {
            com.example.domain.model.TradeMode.BALANCED
        }

        return StrategyConfig(
            strategyType = strategyType,
            tradeMode = tradeMode,
            emaFastPeriod = config?.emaFastPeriod ?: 20,
            emaSlowPeriod = config?.emaSlowPeriod ?: 50,
            adxPeriod = config?.adxPeriod ?: 14,
            adxThreshold = config?.adxThreshold ?: 25.0,
            atrPeriod = config?.atrPeriod ?: 14,
            atrSlMultiplier = config?.atrSlMultiplier ?: 1.5,
            riskRewardRatio = config?.riskRewardRatio ?: 2.0,
            maxCandleExtensionAtr = config?.maxCandleExtensionAtr ?: 2.0,
            breakoutLookbackPeriod = config?.breakoutLookbackPeriod ?: 20,
            breakoutVolumeMultiplier = config?.breakoutVolumeMultiplier ?: 1.5,
            breakoutConfirmCandles = config?.breakoutConfirmCandles ?: 2,
            rsiPeriod = config?.rsiPeriod ?: 14,
            rsiOverbought = config?.rsiOverbought ?: 70.0,
            rsiOversold = config?.rsiOversold ?: 30.0,
            bbPeriod = config?.bbPeriod ?: 20,
            bbStdDev = config?.bbStdDev ?: 2.0,
            macdFastPeriod = config?.macdFastPeriod ?: 12,
            macdSlowPeriod = config?.macdSlowPeriod ?: 26,
            macdSignalPeriod = config?.macdSignalPeriod ?: 9,
            momentumAdxThreshold = config?.momentumAdxThreshold ?: 30.0,
            rangeLookbackPeriod = config?.rangeLookbackPeriod ?: 50,
            rangeMinTouches = config?.rangeMinTouches ?: 2,
            rangeAdxMax = config?.rangeAdxMax ?: 20.0,
            scalpMinRr = config?.scalpMinRr ?: 1.5,
            scalpMaxHoldMinutes = config?.scalpMaxHoldMinutes ?: 30,
            breakEvenEnabled = config?.breakEvenEnabled ?: true,
            breakEvenTriggerR = config?.breakEvenTriggerR ?: 0.8,
            breakEvenBufferPips = config?.breakEvenBufferPips ?: 1.5,
            trailingStopEnabled = config?.trailingStopEnabled ?: true,
            trailingStopTriggerR = config?.trailingStopTriggerR ?: 1.2,
            trailingStopDistanceAtr = config?.trailingStopDistanceAtr ?: 1.0,
            earlyExitOnTrendReversal = config?.earlyExitOnTrendReversal ?: true,
            adaptiveTpEnabled = config?.adaptiveTpEnabled ?: true,
            adaptiveSlEnabled = config?.adaptiveSlEnabled ?: true,
            adaptiveBeEnabled = config?.adaptiveBeEnabled ?: true
        )
    }
}

sealed class ExecutionState {
    data object Idle : ExecutionState()
    data object Executing : ExecutionState()
    data class Success(val message: String) : ExecutionState()
    data class Error(val message: String) : ExecutionState()
}
