package com.example.trading

import com.example.broker.*
import com.example.data.entities.BotConfigEntity
import com.example.data.firestore.FirestoreRepository
import com.example.domain.indicators.IndicatorCalculator
import com.example.domain.model.*
import com.example.domain.model.toClosedTrade
import com.example.domain.risk.RiskManager
import com.example.domain.strategy.*
import com.example.notifications.AppNotificationManager
import com.example.watchdog.WatchdogManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class TradingEngine(
    val repository: FirestoreRepository,
    private val notificationManager: AppNotificationManager,
    private val brokerFactory: (TradingMode) -> BrokerAdapter,
    val marketDataProvider: MarketDataProvider = PaperMarketDataProvider(),
    val accountManager: AccountManager? = null
) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var engineJob: Job? = null

    val stateMachine = StateMachine()

    private val _connectionState = MutableStateFlow(ConnectionState.OFFLINE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _currentAccount = MutableStateFlow(
        AccountInfo(balance = 10000.0, equity = 10000.0, freeMargin = 10000.0)
    )
    val currentAccount: StateFlow<AccountInfo> = _currentAccount.asStateFlow()

    private val _activeQuotes = MutableStateFlow<Map<String, Quote>>(
        SymbolCatalog.ALL_SYMBOLS.associate { it.symbol to SymbolCatalog.getInitialQuote(it.symbol) }
    )
    val activeQuotes: StateFlow<Map<String, Quote>> = _activeQuotes.asStateFlow()

    private val _latestSignal = MutableStateFlow<Signal?>(null)
    val latestSignal: StateFlow<Signal?> = _latestSignal.asStateFlow()

    private val _dailyProfitLoss = MutableStateFlow(0.0)
    val dailyProfitLoss: StateFlow<Double> = _dailyProfitLoss.asStateFlow()

    private val _consecutiveLosses = MutableStateFlow(0)
    val consecutiveLosses: StateFlow<Int> = _consecutiveLosses.asStateFlow()

    private val _marketStructurePlans = MutableStateFlow<Map<String, MarketStructurePlan>>(emptyMap())
    val marketStructurePlans: StateFlow<Map<String, MarketStructurePlan>> = _marketStructurePlans.asStateFlow()

    private var quoteStreamJob: Job? = null
    private var reconciliationJob: Job? = null

    private var activeBroker: BrokerAdapter = brokerFactory(TradingMode.PAPER)
    private var positionReconciler = PositionReconciler(repository, activeBroker)

    private val reconciliationIntervalMillis = 300_000L // 5 minutes

    private val symbolConfigs = ConcurrentHashMap<String, SymbolConfig>().apply {
        SymbolCatalog.ALL_SYMBOLS.forEach { put(it.symbol, it) }
    }

    private val candlesMap = ConcurrentHashMap<String, MutableList<Candle>>()
    private val lastProcessedCandleTimes = ConcurrentHashMap<String, Long>()

    val watchdogManager = WatchdogManager(
        repository = repository,
        stateMachine = stateMachine,
        notificationManager = notificationManager,
        onRecoveryRequested = { recoverEngine() }
    )

    private var strategy = TradingStrategy()
    private var riskManager = RiskManager()

    private val shadowStrategies = ConcurrentHashMap<StrategyType, TradingStrategy>()
    private val _shadowSignals = MutableStateFlow<Map<StrategyType, List<StrategySignalRecord>>>(emptyMap())
    val shadowSignals: StateFlow<Map<StrategyType, List<StrategySignalRecord>>> = _shadowSignals.asStateFlow()
    private val shadowSignalsAccumulator = ConcurrentHashMap<StrategyType, MutableList<StrategySignalRecord>>()
    private val lastShadowProcessedTimes = ConcurrentHashMap<String, ConcurrentHashMap<StrategyType, Long>>()

    suspend fun initialize() {
        val config = repository.getOrCreateConfig()
        val mode = runCatching { TradingMode.valueOf(config.mode) }.getOrDefault(TradingMode.PAPER)
        activeBroker = brokerFactory(mode)
        positionReconciler = PositionReconciler(repository, activeBroker)

        updateConfigurationsFromEntity(config)

        // Determine enabled symbols from config
        val enabledSymbols = mutableListOf<String>()
        if (config.xauusdEnabled) enabledSymbols.add("XAUUSD")
        if (config.btcusdEnabled) enabledSymbols.add("BTCUSD")
        if (config.eurusdEnabled) enabledSymbols.add("EURUSD")
        if (config.gbpusdEnabled) enabledSymbols.add("GBPUSD")
        if (config.usdjpyEnabled) enabledSymbols.add("USDJPY")
        if (config.audusdEnabled) enabledSymbols.add("AUDUSD")
        if (config.usdcadEnabled) enabledSymbols.add("USDCAD")
        if (config.usdchfEnabled) enabledSymbols.add("USDCHF")
        if (config.nzdusdEnabled) enabledSymbols.add("NZDUSD")
        if (config.eurgbpEnabled) enabledSymbols.add("EURGBP")
        if (config.eurjpyEnabled) enabledSymbols.add("EURJPY")
        if (config.gbpjpyEnabled) enabledSymbols.add("GBPJPY")
        if (config.ethusdEnabled) enabledSymbols.add("ETHUSD")
        if (config.solusdEnabled) enabledSymbols.add("SOLUSD")
        if (config.usoilEnabled) enabledSymbols.add("USOIL")

        // Seed historical candles for enabled symbols only
        enabledSymbols.forEach { symbol ->
            val symConfig = symbolConfigs[symbol]
            val timeframe = symConfig?.preferredTimeframe ?: Timeframe.M15
            val historical = marketDataProvider.getHistoricalCandles(symbol, timeframe, 80)
            candlesMap[symbol] = historical.toMutableList()
            // Subscribe to market data for this symbol
            marketDataProvider.subscribe(symbol)
        }

        startQuoteCollection()

        if (config.isBotEnabled && !config.emergencyStop && !config.safeMode) {
            start()
        }
    }

    private fun startQuoteCollection() {
        if (quoteStreamJob?.isActive == true) return
        quoteStreamJob = scope.launch {
            try {
                marketDataProvider.quotes().collect { quote ->
                    processQuote(quote)
                }
            } catch (e: Exception) {
                // If stream is interrupted, restart after brief pause
                delay(3000)
                startQuoteCollection()
            }
        }
    }

    fun start() {
        if (engineJob?.isActive == true) return

        stateMachine.transitionTo(StateMachineState.STARTING, "Starting trading loop")
        watchdogManager.start()
        startQuoteCollection()

        engineJob = scope.launch {
            try {
                val config = repository.getOrCreateConfig()
                val mode = runCatching { TradingMode.valueOf(config.mode) }.getOrDefault(TradingMode.PAPER)

                // Broker connection
                stateMachine.transitionTo(StateMachineState.CONNECTING, "Connecting to broker ($mode)")
                val connected = activeBroker.connect()
                if (!connected && mode != TradingMode.PAPER) {
                    _connectionState.value = ConnectionState.OFFLINE
                    stateMachine.transitionTo(
                        StateMachineState.PAUSED,
                        "Broker connection unavailable for mode $mode"
                    )
                    repository.logEvent(
                        LogLevel.WARN,
                        "TradingEngine",
                        "BROKER_OFFLINE",
                        "Could not connect to broker in $mode mode"
                    )
                    return@launch
                }

                _connectionState.value = ConnectionState.ONLINE

                // Position reconciliation with auto-repair
                stateMachine.transitionTo(StateMachineState.SYNCING, "Reconciling positions")
                val reconciliation = positionReconciler.reconcileAndRepair()
                when (reconciliation) {
                    is ReconciliationResult.Repaired -> {
                        repository.logEvent(
                            LogLevel.INFO,
                            "TradingEngine",
                            "STARTUP_RECONCILIATION_REPAIRED",
                            "Auto-repaired position mismatch on startup: ${reconciliation.message}"
                        )
                    }
                    is ReconciliationResult.Discrepancy -> {
                        // Only go to SAFE MODE if auto-repair failed or was not possible
                        stateMachine.forceState(
                            StateMachineState.SAFE_MODE,
                            "Position mismatch detected on startup (auto-repair failed): ${reconciliation.message}"
                        )
                        notificationManager.notifySafeMode(reconciliation.message)
                        return@launch
                    }
                    is ReconciliationResult.Error -> {
                        stateMachine.forceState(
                            StateMachineState.ERROR,
                            "Reconciliation error on startup: ${reconciliation.error}"
                        )
                        return@launch
                    }
                    else -> { /* InSync - proceed normally */ }
                }

                val openPositions = repository.getOpenPositions()
                val targetState = if (openPositions.isNotEmpty()) StateMachineState.POSITION_OPEN else StateMachineState.READY
                stateMachine.transitionTo(targetState, "Engine initialized & ready")

                notificationManager.notifyBotStarted(
                    mode = mode.name,
                    symbols = symbolConfigs.keys.toList(),
                    risk = config.defaultRiskPercent
                )

                // Start periodic position reconciliation
                startPeriodicReconciliation()

                // Keep engine job alive while bot is active
                while (isActive) {
                    delay(1000)
                }

            } catch (e: CancellationException) {
                stateMachine.transitionTo(StateMachineState.STOPPED, "Trading loop cancelled")
            } catch (e: Exception) {
                stateMachine.forceState(StateMachineState.ERROR, "Unhandled engine crash: ${e.localizedMessage}")
                notificationManager.notifyCriticalError("TradingEngine", e.localizedMessage ?: "Unknown error")
                repository.logEvent(LogLevel.CRITICAL, "TradingEngine", "ENGINE_EXCEPTION", "Crash: ${e.localizedMessage}")
            }
        }
    }

    private fun startPeriodicReconciliation() {
        if (reconciliationJob?.isActive == true) return
        reconciliationJob = scope.launch {
            while (isActive) {
                delay(reconciliationIntervalMillis)
                if (!isActive) break
                try {
                    val reconciliation = positionReconciler.reconcileAndRepair()
                    when (reconciliation) {
                        is ReconciliationResult.Repaired -> {
                            repository.logEvent(
                                LogLevel.INFO,
                                "TradingEngine",
                                "PERIODIC_RECONCILIATION_REPAIRED",
                                "Periodic reconciliation auto-repaired: ${reconciliation.message}"
                            )
                            notificationManager.notifyBotRestarted("Periodic reconciliation auto-repaired position mismatch", 0)
                        }
                        is ReconciliationResult.Discrepancy -> {
                            repository.logEvent(
                                LogLevel.CRITICAL,
                                "TradingEngine",
                                "PERIODIC_RECONCILIATION_MISMATCH",
                                "Periodic reconciliation found unrepairable mismatch: ${reconciliation.message}"
                            )
                            // Only go to SAFE MODE if we have positions and can't repair
                            val openPositions = repository.getOpenPositions()
                            if (openPositions.isNotEmpty()) {
                                stateMachine.forceState(
                                    StateMachineState.SAFE_MODE,
                                    "Periodic reconciliation found unrepairable position mismatch: ${reconciliation.message}"
                                )
                                notificationManager.notifySafeMode("Periodic reconciliation: ${reconciliation.message}")
                                break
                            }
                        }
                        is ReconciliationResult.Error -> {
                            repository.logEvent(
                                LogLevel.ERROR,
                                "TradingEngine",
                                "PERIODIC_RECONCILIATION_ERROR",
                                "Periodic reconciliation error: ${reconciliation.error}"
                            )
                        }
                        else -> { /* InSync - log periodically */
                            repository.logEvent(
                                LogLevel.DEBUG,
                                "TradingEngine",
                                "PERIODIC_RECONCILIATION_OK",
                                "Periodic reconciliation: All positions in sync"
                            )
                        }
                    }
                } catch (e: Exception) {
                    repository.logEvent(
                        LogLevel.ERROR,
                        "TradingEngine",
                        "PERIODIC_RECONCILIATION_EXCEPTION",
                        "Periodic reconciliation failed: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    private fun stopPeriodicReconciliation() {
        reconciliationJob?.cancel()
        reconciliationJob = null
    }

    fun stop(reason: String = "User requested stop") {
        stateMachine.transitionTo(StateMachineState.STOPPING, reason)
        engineJob?.cancel()
        engineJob = null
        watchdogManager.stop()
        stopPeriodicReconciliation()
        _connectionState.value = ConnectionState.OFFLINE
        stateMachine.transitionTo(StateMachineState.STOPPED, reason)

        scope.launch {
            repository.logEvent(LogLevel.INFO, "TradingEngine", "ENGINE_STOPPED", reason)
            notificationManager.notifyBotStopped(reason)
        }
    }

    private suspend fun processQuote(quote: Quote) {
        watchdogManager.recordEngineHeartbeat()
        watchdogManager.recordMarketDataHeartbeat()

        // 1. Update quotes state
        val updatedMap = _activeQuotes.value.toMutableMap()
        updatedMap[quote.symbol] = quote
        _activeQuotes.value = updatedMap

        // 2. Auto-manage open positions (Break-Even, Trailing Stop, Trend Reversal Early Exit)
        autoManagePositions(quote)

        // 3. Process active positions with quote (SL/TP/BE/Trailing execution check)
        val closedTrades = activeBroker.onTick(quote)
        closedTrades.forEach { trade ->
            repository.recordTrade(trade)
            repository.removePosition(trade.id)

            val plans = _marketStructurePlans.value.toMutableMap()
            plans.remove(trade.id)
            _marketStructurePlans.value = plans

            // Update daily stats
            _dailyProfitLoss.value += trade.profit
            if (trade.profit <= 0) {
                _consecutiveLosses.value += 1
            } else {
                _consecutiveLosses.value = 0
            }

            notificationManager.notifyTradeClosed(trade)
            repository.logEvent(
                LogLevel.INFO,
                "TradingEngine",
                "TRADE_CLOSED",
                "Closed ${trade.symbol} ${trade.direction} [${trade.closeReason ?: "EXIT"}]: P/L $${"%.2f".format(trade.profit)} (${"%.2f".format(trade.profitR)}R)",
                trade.symbol
            )

            // Update state machine
            val remaining = repository.getOpenPositions()
            if (remaining.isEmpty() && stateMachine.currentState.value == StateMachineState.POSITION_OPEN) {
                stateMachine.transitionTo(StateMachineState.READY, "All positions closed")
            }
        }

        // 4. Update account info
        val account = activeBroker.getAccount()
        _currentAccount.value = account

        // 5. Update rolling candles
        updateRollingCandle(quote)

        // 6. Evaluate strategy if in valid state
        if (stateMachine.currentState.value in listOf(StateMachineState.READY, StateMachineState.ANALYZING, StateMachineState.POSITION_OPEN)) {
            val openPositions = repository.getOpenPositions()
            val config = repository.getOrCreateConfig()
            if (openPositions.size < config.maxOpenPositions) {
                evaluateSymbol(quote.symbol, quote)
            }
        }
    }

    private suspend fun autoManagePositions(quote: Quote) {
        val openPositions = repository.getOpenPositions().filter { it.symbol == quote.symbol }
        if (openPositions.isEmpty()) return

        val config = repository.getOrCreateConfig()
        val symbolConfig = symbolConfigs[quote.symbol] ?: return
        val candles = candlesMap[quote.symbol] ?: emptyList()
        val atr = if (candles.size >= 14) IndicatorCalculator.computeLatest(candles)?.atr ?: (symbolConfig.tickSize * 20.0) else (symbolConfig.tickSize * 20.0)
        val adx = if (candles.size >= 14) IndicatorCalculator.computeLatest(candles)?.adx ?: 25.0 else 25.0

        val tradeMode = runCatching { TradeMode.valueOf(config.tradeMode) }.getOrDefault(TradeMode.BALANCED)
        val modePreset = TradeModePresets.getPreset(tradeMode)

        openPositions.forEach { pos ->
            val markPrice = if (pos.direction == TradeDirection.BUY) quote.bid else quote.ask
            val priceDiff = if (pos.direction == TradeDirection.BUY) markPrice - pos.entryPrice else pos.entryPrice - markPrice
            val meta = SymbolCatalog.getMeta(pos.symbol)
            val ticks = priceDiff / meta.first
            val unrealized = ticks * meta.second * pos.volume
            val riskDist = abs(pos.entryPrice - pos.stopLoss)
            val unrealizedR = if (riskDist > 0) priceDiff / riskDist else 0.0

            // Compute adaptive parameters
            val beTriggerR = AdaptiveCalculator.adaptiveBeTriggerR(
                baseTriggerR = config.breakEvenTriggerR,
                adx = adx,
                adxThreshold = config.adxThreshold,
                enabled = config.adaptiveBeEnabled
            )
            val beBufferPips = AdaptiveCalculator.adaptiveBeBufferPips(
                baseBufferPips = config.breakEvenBufferPips,
                atr = atr,
                tickSize = symbolConfig.tickSize,
                adx = adx,
                adxThreshold = config.adxThreshold,
                enabled = config.adaptiveBeEnabled
            )
            val trailingDistance = AdaptiveCalculator.adaptiveTrailingDistance(
                atr = atr,
                baseDistanceAtr = config.trailingStopDistanceAtr,
                adx = adx,
                adxThreshold = config.adxThreshold,
                tickSize = symbolConfig.tickSize,
                enabled = config.adaptiveBeEnabled
            )

            var updatedSL = pos.stopLoss
            var slChanged = false
            var slReason = ""

            // 1. Break-Even Protection: Trigger once profit reaches adaptive breakEvenTriggerR
            if (config.breakEvenEnabled && unrealizedR >= beTriggerR) {
                val bufferPipsDistance = (beBufferPips * symbolConfig.tickSize * 10.0).coerceAtLeast(symbolConfig.spreadLimit * 0.2)
                if (pos.direction == TradeDirection.BUY && pos.stopLoss < pos.entryPrice) {
                    updatedSL = pos.entryPrice + bufferPipsDistance
                    slChanged = true
                    slReason = "Break-Even secured at +${"%.2f".format(unrealizedR)}R (SL at +${"%.1f".format(beBufferPips)} pips buffer, BE@${"%.2f".format(beTriggerR)}R)"
                } else if (pos.direction == TradeDirection.SELL && pos.stopLoss > pos.entryPrice) {
                    updatedSL = pos.entryPrice - bufferPipsDistance
                    slChanged = true
                    slReason = "Break-Even secured at +${"%.2f".format(unrealizedR)}R (SL at -${"%.1f".format(beBufferPips)} pips buffer, BE@${"%.2f".format(beTriggerR)}R)"
                }
            }

            // 2. Dynamic Trailing Stop: Trailing price once profit reaches mode-based trailingStopTriggerR
            val trailingTriggerR = modePreset.trailingStopTriggerR
            if (config.trailingStopEnabled && unrealizedR >= trailingTriggerR) {
                if (pos.direction == TradeDirection.BUY) {
                    val candidateSL = quote.bid - trailingDistance
                    if (candidateSL > updatedSL) {
                        updatedSL = candidateSL
                        slChanged = true
                        slReason = "Trailing Stop ratcheted to ${SymbolCatalog.formatPrice(pos.symbol, updatedSL)} (+${"%.2f".format(unrealizedR)}R, trail dist: ${"%.1f".format(trailingDistance)})"
                    }
                } else {
                    val candidateSL = quote.ask + trailingDistance
                    if (candidateSL < updatedSL) {
                        updatedSL = candidateSL
                        slChanged = true
                        slReason = "Trailing Stop ratcheted to ${SymbolCatalog.formatPrice(pos.symbol, updatedSL)} (+${"%.2f".format(unrealizedR)}R, trail dist: ${"%.1f".format(trailingDistance)})"
                    }
                }
            }

            val updatedPos = pos.copy(
                currentPrice = markPrice,
                unrealizedProfit = unrealized,
                unrealizedR = unrealizedR,
                stopLoss = updatedSL
            )
            repository.recordPosition(updatedPos)

            // Market Structure & Continuous Trade Plan Monitor
            try {
                val plan = MarketStructureMonitor.analyzePositionStructure(
                    position = updatedPos,
                    currentQuote = quote,
                    candles = candles,
                    symbolConfig = symbolConfig
                )
                val currentPlans = _marketStructurePlans.value.toMutableMap()
                currentPlans[pos.id] = plan
                _marketStructurePlans.value = currentPlans
            } catch (_: Exception) {
                // Non-blocking fallback
            }

            if (slChanged) {
                // Synchronize SL with active broker adapter
                activeBroker.updatePositionSl(pos.id, updatedSL)
                repository.logEvent(
                    LogLevel.INFO,
                    "TradingEngine",
                    "POSITION_SL_UPDATED",
                    "Auto-Protect: $slReason for ${pos.symbol} (${pos.direction})",
                    pos.symbol
                )
            }

            // 3. Trend Reversal / Market Direction Flip Early Exit Protection
            if (config.earlyExitOnTrendReversal) {
                val exitDecision = strategy.checkTrendReversalExit(updatedPos, quote, candles, symbolConfig)
                if (exitDecision.shouldExit) {
                    repository.logEvent(
                        LogLevel.WARN,
                        "TradingEngine",
                        "EARLY_EXIT_TRIGGERED",
                        "Early Protective Exit on ${pos.symbol} (${pos.direction}): ${exitDecision.reason}",
                        pos.symbol
                    )
                    closeSinglePosition(pos.id, CloseReason.TREND_REVERSAL)
                }
            }
        }
    }

    suspend fun closeSinglePosition(positionId: String, reason: CloseReason = CloseReason.MANUAL): Boolean {
        val openPositions = repository.getOpenPositions()
        val pos = openPositions.find { it.id == positionId } ?: return false
        val res = activeBroker.closePosition(positionId, reason)

        if (!res.success) {
            val errorMsg = res.errorMessage ?: ""
            val isNotFound = errorMsg.contains("not found", ignoreCase = true)
            if (isNotFound) {
                repository.logEvent(
                    LogLevel.WARN,
                    "TradingEngine",
                    "POSITION_STALE_CLEANUP",
                    "Position $positionId not found in broker, cleaning up stale DB entry",
                    pos.symbol
                )
                repository.removePosition(positionId)
                val closedTrade = pos.toClosedTrade(
                    closePrice = pos.currentPrice,
                    reason = CloseReason.EXPIRED,
                    mode = activeBroker.mode
                )
                repository.recordTrade(closedTrade)
                notificationManager.notifyTradeClosed(closedTrade)
                val remaining = repository.getOpenPositions()
                if (remaining.isEmpty() && stateMachine.currentState.value == StateMachineState.POSITION_OPEN) {
                    stateMachine.transitionTo(StateMachineState.READY, "Stale position cleaned up")
                }
                return true
            }
            repository.logEvent(
                LogLevel.ERROR,
                "TradingEngine",
                "POSITION_CLOSE_FAILED",
                "Failed to close position on broker: $errorMsg",
                pos.symbol
            )
            return false
        }

        repository.removePosition(positionId)
        val closedTrade = pos.toClosedTrade(
            closePrice = res.executedPrice,
            reason = reason,
            mode = activeBroker.mode
        )
        repository.recordTrade(closedTrade)
        notificationManager.notifyTradeClosed(closedTrade)
        repository.logEvent(
            LogLevel.INFO,
            "TradingEngine",
            "POSITION_CLOSED_MANUAL",
            "Closed position on ${pos.symbol} at ${res.executedPrice}, P/L: $${"%.2f".format(pos.unrealizedProfit)}",
            pos.symbol
        )
        val remaining = repository.getOpenPositions()
        if (remaining.isEmpty() && stateMachine.currentState.value == StateMachineState.POSITION_OPEN) {
            stateMachine.transitionTo(StateMachineState.READY, "Position closed")
        }
        return true
    }

    fun isSymbolEnabled(symbol: String): Boolean {
        return symbolConfigs[symbol]?.enabled ?: false
    }

    fun setSymbolEnabled(symbol: String, enabled: Boolean) {
        symbolConfigs[symbol]?.let {
            symbolConfigs[symbol] = it.copy(enabled = enabled)
        }
    }

    private suspend fun evaluateSymbol(symbol: String, quote: Quote) {
        val symbolConfig = symbolConfigs[symbol] ?: return
        if (!symbolConfig.enabled) return

        // Check if market is open for trading (e.g. Gold weekend close)
        val sessionInfo = MarketScheduleUtils.getMarketSession(symbol)
        if (!sessionInfo.isOpen) {
            return
        }

        val candles = candlesMap[symbol] ?: return
        val lastProcessedTime = lastProcessedCandleTimes[symbol] ?: 0L

        val config = repository.getOrCreateConfig()
        val tradeMode = runCatching { TradeMode.valueOf(config.tradeMode) }.getOrDefault(TradeMode.BALANCED)
        val modePreset = TradeModePresets.getPreset(tradeMode)

        val riskConfig = RiskConfig(
            defaultRiskPercent = modePreset.riskPercent,
            maxDailyLossPercent = config.maxDailyLossPercent,
            maxConsecutiveLosses = modePreset.maxConsecutiveLosses,
            maxOpenPositions = config.maxOpenPositions,
            emergencyStopActive = config.emergencyStop,
            safeModeActive = config.safeMode,
            safeModeReason = config.safeModeReason
        )

        val openPositions = repository.getOpenPositions()
        val hasOpenPos = openPositions.any { it.symbol == symbol }

        val openingEquity = 10000.0 // Base daily equity
        val todayLossPercent = if (_dailyProfitLoss.value < 0) abs(_dailyProfitLoss.value / openingEquity) * 100.0 else 0.0
        val dailyLossReached = todayLossPercent >= config.maxDailyLossPercent
        val consecutiveLossReached = _consecutiveLosses.value >= config.maxConsecutiveLosses

        stateMachine.transitionTo(StateMachineState.ANALYZING, "Analyzing $symbol candle patterns")

        val signal = strategy.evaluate(
            candles = candles,
            symbolConfig = symbolConfig,
            currentQuote = quote,
            riskConfig = riskConfig,
            hasOpenPosition = hasOpenPos,
            lastProcessedCandleTime = lastProcessedTime,
            isConnectionHealthy = _connectionState.value == ConnectionState.ONLINE,
            dailyLossReached = dailyLossReached,
            consecutiveLossesReached = consecutiveLossReached,
            marginSufficient = _currentAccount.value.freeMargin > 200.0
        )

        if (signal != null) {
            _latestSignal.value = signal
            lastProcessedCandleTimes[symbol] = signal.candleTime
            repository.recordSignal(signal)
            notificationManager.notifySignal(signal)

            stateMachine.transitionTo(StateMachineState.SIGNAL_FOUND, "Signal: ${signal.symbol} ${signal.direction}")

            // Evaluate shadow strategies on same candle data
            evaluateShadowStrategies(symbol, candles, quote, lastProcessedTime, hasOpenPos, openPositions.size)

            // Validate order
            stateMachine.transitionTo(StateMachineState.VALIDATING, "Validating risk & margin")
            val validation = riskManager.validateTrade(
                signal = signal,
                account = _currentAccount.value,
                symbolConfig = symbolConfig,
                todayLossPercent = todayLossPercent,
                consecutiveLosses = _consecutiveLosses.value,
                openPositionsCount = openPositions.size
            )

            if (!validation.isValid) {
                repository.logEvent(
                    LogLevel.WARN,
                    "TradingEngine",
                    "TRADE_REJECTED",
                    "Risk rejected signal: ${validation.reason}",
                    symbol
                )
                stateMachine.transitionTo(StateMachineState.READY, "Signal rejected: ${validation.reason}")
                return
            }

            // Execute order
            stateMachine.transitionTo(StateMachineState.EXECUTING, "Sending ${signal.direction} order")
            val volume = riskManager.calculatePositionSize(
                equity = _currentAccount.value.equity,
                riskPercent = modePreset.riskPercent,
                entryPrice = signal.price,
                stopLossPrice = signal.stopLoss,
                symbolConfig = symbolConfig
            )

            val orderRequest = OrderRequest(
                clientOrderId = UUID.randomUUID().toString(),
                symbol = symbol,
                direction = signal.direction,
                volume = volume,
                requestedPrice = signal.price,
                stopLoss = signal.stopLoss,
                takeProfit = signal.takeProfit,
                maxSlippage = 1.0,
                mode = activeBroker.mode
            )

            val orderResult = activeBroker.placeOrder(orderRequest)
            if (orderResult.success) {
                val newPosition = Position(
                    id = orderResult.positionId,
                    symbol = symbol,
                    direction = signal.direction,
                    volume = volume,
                    entryPrice = orderResult.executedPrice,
                    currentPrice = orderResult.executedPrice,
                    stopLoss = signal.stopLoss,
                    takeProfit = signal.takeProfit,
                    unrealizedProfit = 0.0,
                    unrealizedR = 0.0,
                    openedAt = System.currentTimeMillis(),
                    mode = activeBroker.mode
                )

                repository.recordPosition(newPosition)
                stateMachine.transitionTo(StateMachineState.POSITION_OPEN, "Active position open on $symbol")

                val openTrade = Trade(
                    id = orderResult.positionId,
                    brokerOrderId = orderResult.orderId,
                    brokerPositionId = orderResult.positionId,
                    symbol = symbol,
                    direction = signal.direction,
                    volume = volume,
                    entryPrice = orderResult.executedPrice,
                    stopLoss = signal.stopLoss,
                    takeProfit = signal.takeProfit,
                    riskAmount = validation.theoreticalRisk,
                    riskPercent = modePreset.riskPercent,
                    rr = signal.rrRatio,
                    openedAt = System.currentTimeMillis(),
                    status = TradeStatus.OPEN,
                    strategyVersion = config.strategyVersion,
                    mode = activeBroker.mode,
                    slippage = orderResult.slippage
                )
                repository.recordTrade(openTrade)
                notificationManager.notifyTradeOpened(openTrade)

                repository.logEvent(
                    LogLevel.INFO,
                    "TradingEngine",
                    "ORDER_FILLED",
                    "Order executed: $symbol ${signal.direction} $volume lots @ ${orderResult.executedPrice}",
                    symbol
                )
            } else {
                repository.logEvent(
                    LogLevel.ERROR,
                    "TradingEngine",
                    "ORDER_EXECUTION_FAILED",
                    "Order execution failed: ${orderResult.errorMessage}",
                    symbol
                )
                stateMachine.transitionTo(StateMachineState.READY, "Execution failed: ${orderResult.errorMessage}")
            }
        } else {
            // No signal from active strategy, still evaluate shadows
            evaluateShadowStrategies(symbol, candles, quote, lastProcessedTime, hasOpenPos, openPositions.size)
        }
    }

    private suspend fun evaluateShadowStrategies(
        symbol: String,
        candles: List<Candle>,
        quote: Quote,
        lastProcessedTime: Long,
        hasOpenPosition: Boolean,
        openPositionsCount: Int
    ) {
        val config = repository.getOrCreateConfig()
        val tradeMode = runCatching { TradeMode.valueOf(config.tradeMode) }.getOrDefault(TradeMode.BALANCED)
        val modePreset = TradeModePresets.getPreset(tradeMode)
        val riskConfig = RiskConfig(
            defaultRiskPercent = modePreset.riskPercent,
            maxDailyLossPercent = config.maxDailyLossPercent,
            maxConsecutiveLosses = modePreset.maxConsecutiveLosses,
            maxOpenPositions = config.maxOpenPositions,
            emergencyStopActive = config.emergencyStop,
            safeModeActive = config.safeMode,
            safeModeReason = config.safeModeReason
        )
        val todayLossPercent = if (_dailyProfitLoss.value < 0) abs(_dailyProfitLoss.value / 10000.0) * 100.0 else 0.0

        val symbolLastProcessed = lastShadowProcessedTimes.getOrPut(symbol) { ConcurrentHashMap() }

        for ((type, shadowStrategy) in shadowStrategies) {
            if (type == strategy.strategyConfig.strategyType) continue

            val lastProcessed = symbolLastProcessed[type] ?: 0L

            val shadowSignal = shadowStrategy.evaluate(
                candles = candles,
                symbolConfig = symbolConfigs[symbol] ?: continue,
                currentQuote = quote,
                riskConfig = riskConfig,
                hasOpenPosition = hasOpenPosition,
                lastProcessedCandleTime = lastProcessed,
                isConnectionHealthy = _connectionState.value == ConnectionState.ONLINE,
                dailyLossReached = todayLossPercent >= config.maxDailyLossPercent,
                consecutiveLossesReached = _consecutiveLosses.value >= config.maxConsecutiveLosses,
                marginSufficient = _currentAccount.value.freeMargin > 200.0
            )

            if (shadowSignal != null) {
                symbolLastProcessed[type] = shadowSignal.candleTime

                val validation = riskManager.validateTrade(
                    signal = shadowSignal,
                    account = _currentAccount.value,
                    symbolConfig = symbolConfigs[symbol] ?: continue,
                    todayLossPercent = todayLossPercent,
                    consecutiveLosses = _consecutiveLosses.value,
                    openPositionsCount = openPositionsCount
                )

                val record = StrategySignalRecord(
                    strategyType = type,
                    symbol = shadowSignal.symbol,
                    direction = shadowSignal.direction,
                    price = shadowSignal.price,
                    stopLoss = shadowSignal.stopLoss,
                    takeProfit = shadowSignal.takeProfit,
                    rrRatio = shadowSignal.rrRatio,
                    candleTime = shadowSignal.candleTime,
                    wasExecuted = validation.isValid,
                    blockedReason = if (!validation.isValid) validation.reason else null,
                    explanation = shadowSignal.explanation
                )

                shadowSignalsAccumulator.getOrPut(type) { mutableListOf() }.add(record)
            }
        }

        val snapshot = shadowSignalsAccumulator.mapValues { it.value.toList() }
        _shadowSignals.value = snapshot
    }

    private fun updateRollingCandle(quote: Quote) {
        val list = candlesMap[quote.symbol] ?: return
        if (list.isEmpty()) return

        val last = list.last()
        val now = System.currentTimeMillis()
        val symConfig = symbolConfigs[quote.symbol]
        val timeframe = symConfig?.preferredTimeframe ?: Timeframe.M15
        val timeframeMillis = timeframe.minutes * 60 * 1000L

        if (now - last.openTime >= timeframeMillis) {
            // Close previous candle and open new candle
            list[list.size - 1] = last.copy(isClosed = true)
            list.add(
                Candle(
                    symbol = quote.symbol,
                    timeframe = timeframe,
                    openTime = now,
                    open = quote.ask,
                    high = quote.ask,
                    low = quote.bid,
                    close = (quote.ask + quote.bid) / 2.0,
                    volume = 1.0,
                    isClosed = false
                )
            )
            if (list.size > 120) list.removeAt(0)
        } else {
            // Update current forming candle
            val high = max(last.high, quote.ask)
            val low = minOf(last.low, quote.bid)
            val close = (quote.ask + quote.bid) / 2.0
            list[list.size - 1] = last.copy(high = high, low = low, close = close, volume = last.volume + 1.0)
        }
    }

    suspend fun analyzeSymbolForTradePlan(
        symbol: String,
        strategyType: StrategyType,
        strategyConfig: StrategyConfig
    ): TradePlan? {
        val symbolConfig = symbolConfigs[symbol] ?: return null
        if (!symbolConfig.enabled) return null

        val sessionInfo = MarketScheduleUtils.getMarketSession(symbol)
        val quote = _activeQuotes.value[symbol] ?: return null

        val candles = candlesMap[symbol]?.toList() ?: return null
        val closedCandles = candles.filter { it.isClosed }
        if (closedCandles.size < 30) return null

        val config = repository.getOrCreateConfig()

        val riskConfig = RiskConfig(
            defaultRiskPercent = config.defaultRiskPercent,
            maxDailyLossPercent = config.maxDailyLossPercent,
            maxConsecutiveLosses = config.maxConsecutiveLosses,
            maxOpenPositions = config.maxOpenPositions,
            emergencyStopActive = config.emergencyStop,
            safeModeActive = config.safeMode,
            safeModeReason = config.safeModeReason
        )

        val tempStrategy = TradingStrategy(
            strategyConfig = strategyConfig,
            newsFilter = NoNewsFilter()
        )

        val signal = tempStrategy.evaluate(
            candles = candles,
            symbolConfig = symbolConfig,
            currentQuote = quote,
            riskConfig = riskConfig,
            hasOpenPosition = false,
            lastProcessedCandleTime = 0L,
            isConnectionHealthy = _connectionState.value == ConnectionState.ONLINE,
            dailyLossReached = false,
            consecutiveLossesReached = false,
            marginSufficient = _currentAccount.value.freeMargin > 200.0
        ) ?: return null

        val indicators = IndicatorCalculator.computeLatest(
            candles = closedCandles,
            fastEmaPeriod = strategyConfig.emaFastPeriod,
            slowEmaPeriod = strategyConfig.emaSlowPeriod,
            adxPeriod = strategyConfig.adxPeriod,
            atrPeriod = strategyConfig.atrPeriod,
            rsiPeriod = strategyConfig.rsiPeriod,
            macdFast = strategyConfig.macdFastPeriod,
            macdSlow = strategyConfig.macdSlowPeriod,
            macdSignal = strategyConfig.macdSignalPeriod,
            bbPeriod = strategyConfig.bbPeriod,
            bbStdDev = strategyConfig.bbStdDev,
            stochK = 14,
            stochD = 3
        ) ?: return null

        val volume = riskManager.calculatePositionSize(
            equity = _currentAccount.value.equity,
            riskPercent = config.defaultRiskPercent,
            entryPrice = signal.price,
            stopLossPrice = signal.stopLoss,
            symbolConfig = symbolConfig
        )

        val priceRisk = kotlin.math.abs(signal.price - signal.stopLoss)
        val ticksAtRisk = priceRisk / symbolConfig.tickSize
        val theoreticalRisk = ticksAtRisk * symbolConfig.tickValue * volume

        val validation = riskManager.validateTrade(
            signal = signal,
            account = _currentAccount.value,
            symbolConfig = symbolConfig,
            todayLossPercent = 0.0,
            consecutiveLosses = 0,
            openPositionsCount = repository.getOpenPositions().size
        )

        return TradePlan(
            signal = signal,
            strategyType = strategyType,
            tradeMode = runCatching { TradeMode.valueOf(config.tradeMode) }.getOrDefault(TradeMode.BALANCED),
            symbolConfig = symbolConfig,
            currentQuote = quote,
            indicators = indicators,
            validation = validation,
            positionSize = volume,
            riskAmount = theoreticalRisk,
            marketSession = sessionInfo
        )
    }

    suspend fun executeManualTrade(plan: TradePlan): OrderResult {
        val config = repository.getOrCreateConfig()
        val orderRequest = OrderRequest(
            clientOrderId = UUID.randomUUID().toString(),
            symbol = plan.signal.symbol,
            direction = plan.signal.direction,
            volume = plan.positionSize,
            requestedPrice = plan.signal.price,
            stopLoss = plan.signal.stopLoss,
            takeProfit = plan.signal.takeProfit,
            maxSlippage = 1.0,
            mode = activeBroker.mode
        )
        return activeBroker.placeOrder(orderRequest)
    }

    suspend fun triggerEmergencyStop(reason: String = "User Emergency Stop triggered") {
        val config = repository.getOrCreateConfig()
        repository.updateConfig(config.copy(emergencyStop = true, isBotEnabled = false))
        stateMachine.forceState(StateMachineState.PAUSED, "EMERGENCY STOP: $reason")
        notificationManager.notifyEmergencyStop(reason)
        repository.logEvent(LogLevel.CRITICAL, "TradingEngine", "EMERGENCY_STOP", reason)
    }

    suspend fun closeAllPositions(reason: String = "Manual Close All requested") {
        val openPositions = repository.getOpenPositions()
        openPositions.forEach { pos ->
            val res = activeBroker.closePosition(pos.id, CloseReason.EMERGENCY_STOP)
            if (!res.success) {
                val errorMsg = res.errorMessage ?: ""
                val isNotFound = errorMsg.contains("not found", ignoreCase = true)
                if (isNotFound) {
                    repository.logEvent(
                        LogLevel.WARN,
                        "TradingEngine",
                        "POSITION_STALE_CLEANUP",
                        "Stale position ${pos.id} not found in broker, cleaning up",
                        pos.symbol
                    )
                }
            }
            repository.removePosition(pos.id)
            val closedTrade = pos.toClosedTrade(
                closePrice = res.executedPrice,
                reason = CloseReason.EMERGENCY_STOP,
                mode = activeBroker.mode
            )
            repository.recordTrade(closedTrade)
            notificationManager.notifyTradeClosed(closedTrade)
        }
        stateMachine.transitionTo(StateMachineState.READY, "All positions liquidated")
    }

    suspend fun resetSafeMode() {
        val config = repository.getOrCreateConfig()
        repository.updateConfig(config.copy(safeMode = false, safeModeReason = ""))
        stateMachine.forceState(StateMachineState.READY, "Safe Mode cleared by operator")
        repository.logEvent(LogLevel.INFO, "TradingEngine", "SAFE_MODE_CLEARED", "Operator cleared safe mode")
    }

    suspend fun reconcilePositions(): ReconciliationResult {
        return positionReconciler.reconcileAndRepair()
    }

    private suspend fun recoverEngine() {
        repository.logEvent(LogLevel.WARN, "TradingEngine", "AUTO_RECOVERY", "Executing automated engine recovery sequence")
        stop("Watchdog recovery restart")
        delay(1000)
        initialize()
        start()
    }

    suspend fun switchBrokerAccount(accountId: String) {
        if (accountManager == null) return

        val wasRunning = engineJob?.isActive == true
        if (wasRunning) {
            stop("Switching to account $accountId")
        }

        accountManager.switchToAccount(accountId)
        val config = repository.getOrCreateConfig()
        val mode = runCatching { TradingMode.valueOf(config.mode) }.getOrDefault(TradingMode.PAPER)
        activeBroker = brokerFactory(mode)
        positionReconciler = PositionReconciler(repository, activeBroker)

        repository.logEvent(
            LogLevel.INFO,
            "TradingEngine",
            "ACCOUNT_SWITCHED",
            "Switched to broker account: $accountId"
        )

        if (wasRunning) {
            delay(500)
            start()
        }
    }

    fun getCandles(symbol: String): List<Candle> = candlesMap[symbol]?.toList() ?: emptyList()

    suspend fun fetchHistoricalCandles(symbol: String, timeframe: Timeframe, count: Int = 80): List<Candle> {
        val effectiveTimeframe = symbolConfigs[symbol]?.preferredTimeframe ?: timeframe
        val candles = marketDataProvider.getHistoricalCandles(symbol, effectiveTimeframe, count)
        if (candles.isNotEmpty()) {
            candlesMap[symbol] = candles.toMutableList()
        }
        return candles
    }

    fun getMarketSession(symbol: String): MarketSessionInfo = MarketScheduleUtils.getMarketSession(symbol)

    private fun updateConfigurationsFromEntity(config: BotConfigEntity) {
        val tradeMode = runCatching { TradeMode.valueOf(config.tradeMode) }.getOrDefault(TradeMode.BALANCED)
        val modePreset = TradeModePresets.getPreset(tradeMode)

        strategy = TradingStrategy(
            StrategyConfig(
                strategyVersion = config.strategyVersion,
                strategyType = runCatching { StrategyType.valueOf(config.strategyType) }.getOrDefault(StrategyType.PULLBACK),
                tradeMode = tradeMode,
                emaFastPeriod = config.emaFastPeriod,
                emaSlowPeriod = config.emaSlowPeriod,
                adxPeriod = config.adxPeriod,
                adxThreshold = config.adxThreshold,
                atrPeriod = config.atrPeriod,
                atrSlMultiplier = config.atrSlMultiplier,
                riskRewardRatio = config.riskRewardRatio,
                maxCandleExtensionAtr = config.maxCandleExtensionAtr,
                breakoutLookbackPeriod = config.breakoutLookbackPeriod,
                breakoutVolumeMultiplier = config.breakoutVolumeMultiplier,
                breakoutConfirmCandles = config.breakoutConfirmCandles,
                rsiPeriod = config.rsiPeriod,
                rsiOverbought = config.rsiOverbought,
                rsiOversold = config.rsiOversold,
                bbPeriod = config.bbPeriod,
                bbStdDev = config.bbStdDev,
                macdFastPeriod = config.macdFastPeriod,
                macdSlowPeriod = config.macdSlowPeriod,
                macdSignalPeriod = config.macdSignalPeriod,
                momentumAdxThreshold = config.momentumAdxThreshold,
                rangeLookbackPeriod = config.rangeLookbackPeriod,
                rangeMinTouches = config.rangeMinTouches,
                rangeAdxMax = config.rangeAdxMax,
                scalpMinRr = config.scalpMinRr,
                scalpMaxHoldMinutes = config.scalpMaxHoldMinutes,
                breakEvenEnabled = config.breakEvenEnabled,
                breakEvenTriggerR = config.breakEvenTriggerR,
                breakEvenBufferPips = config.breakEvenBufferPips,
                trailingStopEnabled = config.trailingStopEnabled,
                trailingStopTriggerR = config.trailingStopTriggerR,
                trailingStopDistanceAtr = config.trailingStopDistanceAtr,
                earlyExitOnTrendReversal = config.earlyExitOnTrendReversal,
                adaptiveTpEnabled = config.adaptiveTpEnabled,
                adaptiveSlEnabled = config.adaptiveSlEnabled,
                adaptiveBeEnabled = config.adaptiveBeEnabled
            )
        )
        riskManager = RiskManager(
            RiskConfig(
                defaultRiskPercent = modePreset.riskPercent,
                maxDailyLossPercent = config.maxDailyLossPercent,
                maxConsecutiveLosses = modePreset.maxConsecutiveLosses,
                maxOpenPositions = config.maxOpenPositions,
                emergencyStopActive = config.emergencyStop,
                safeModeActive = config.safeMode,
                safeModeReason = config.safeModeReason
            )
        )

        // Apply per-symbol timeframes from config
        applySymbolTimeframes(config)

        StrategyType.entries.forEach { type ->
            val cfg = StrategyConfig(strategyType = type)
            shadowStrategies[type] = TradingStrategy(cfg)
            shadowSignalsAccumulator[type] = mutableListOf()
        }
    }

    suspend fun executeAiTradePlan(plan: com.example.ai.AiInstitutionalTradePlan): Result<String> {
        val symbolConfig = symbolConfigs[plan.symbol]
            ?: return Result.failure(IllegalStateException("Symbol ${plan.symbol} configuration not found"))

        val quote = _activeQuotes.value[plan.symbol]
            ?: return Result.failure(IllegalStateException("No active quote for ${plan.symbol}"))

        val openPositions = repository.getOpenPositions()
        val config = repository.getOrCreateConfig()

        if (openPositions.size >= config.maxOpenPositions) {
            return Result.failure(IllegalStateException("Maximum open positions (${config.maxOpenPositions}) reached"))
        }

        val orderRequest = OrderRequest(
            clientOrderId = UUID.randomUUID().toString(),
            symbol = plan.symbol,
            direction = plan.direction,
            volume = plan.recommendedLotSize,
            requestedPrice = plan.entryPrice,
            stopLoss = plan.stopLoss,
            takeProfit = plan.takeProfit1,
            maxSlippage = 2.0,
            mode = activeBroker.mode
        )

        val orderResult = activeBroker.placeOrder(orderRequest)
        if (!orderResult.success) {
            val err = orderResult.errorMessage ?: "Order execution failed"
            repository.logEvent(
                LogLevel.ERROR,
                "TradingEngine",
                "AI_TRADE_PLAN_FAILED",
                "Failed to execute AI Trade Plan for ${plan.symbol}: $err",
                plan.symbol
            )
            return Result.failure(RuntimeException(err))
        }

        val newPosition = Position(
            id = orderResult.positionId,
            symbol = plan.symbol,
            direction = plan.direction,
            volume = plan.recommendedLotSize,
            entryPrice = orderResult.executedPrice,
            currentPrice = orderResult.executedPrice,
            stopLoss = plan.stopLoss,
            takeProfit = plan.takeProfit1,
            unrealizedProfit = 0.0,
            unrealizedR = 0.0,
            openedAt = System.currentTimeMillis(),
            mode = activeBroker.mode
        )

        repository.recordPosition(newPosition)
        stateMachine.transitionTo(StateMachineState.POSITION_OPEN, "Active position open via AI Trade Plan on ${plan.symbol}")

        val openTrade = Trade(
            id = orderResult.positionId,
            brokerOrderId = orderResult.orderId,
            brokerPositionId = orderResult.positionId,
            symbol = plan.symbol,
            direction = plan.direction,
            volume = plan.recommendedLotSize,
            entryPrice = orderResult.executedPrice,
            stopLoss = plan.stopLoss,
            takeProfit = plan.takeProfit1,
            riskAmount = plan.maxRiskAmountUsd,
            riskPercent = 0.25,
            rr = plan.riskRewardRatio,
            openedAt = System.currentTimeMillis(),
            status = TradeStatus.OPEN,
            strategyVersion = "AI-Plan-${plan.strategyType.name}",
            mode = activeBroker.mode,
            slippage = orderResult.slippage
        )
        repository.recordTrade(openTrade)

        repository.logEvent(
            LogLevel.INFO,
            "TradingEngine",
            "AI_TRADE_PLAN_DEPLOYED",
            "AI Trade Plan deployed for ${plan.symbol} (${plan.direction}) at ${orderResult.executedPrice} (Vol: ${plan.recommendedLotSize}, SL: ${plan.stopLoss}, TP: ${plan.takeProfit1})",
            plan.symbol
        )

        return Result.success("AI Trade Plan executed successfully! Order #${orderResult.orderId} filled at ${orderResult.executedPrice}")
    }

    private fun applySymbolTimeframes(config: BotConfigEntity) {
        val timeframeOverrides = mapOf(
            "XAUUSD" to config.xauusdTimeframe,
            "BTCUSD" to config.btcusdTimeframe,
            "ETHUSD" to config.ethusdTimeframe,
            "SOLUSD" to config.solusdTimeframe,
            "USOIL" to config.usoilTimeframe,
            "EURUSD" to config.eurusdTimeframe,
            "GBPUSD" to config.gbpusdTimeframe,
            "USDJPY" to config.usdjpyTimeframe,
            "AUDUSD" to config.audusdTimeframe,
            "USDCAD" to config.usdcadTimeframe,
            "USDCHF" to config.usdchfTimeframe,
            "NZDUSD" to config.nzdusdTimeframe,
            "EURGBP" to config.eurgbpTimeframe,
            "EURJPY" to config.eurjpyTimeframe,
            "GBPJPY" to config.gbpjpyTimeframe
        )

        for ((symbol, tfString) in timeframeOverrides) {
            if (tfString.isNullOrBlank()) continue
            val tf = runCatching { Timeframe.valueOf(tfString) }.getOrNull() ?: continue
            symbolConfigs[symbol]?.let {
                symbolConfigs[symbol] = it.copy(preferredTimeframe = tf)
            }
        }
    }
}
