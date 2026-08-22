package com.example.trading

import com.example.broker.*
import com.example.data.entities.BotConfigEntity
import com.example.data.repositories.TradingRepository
import com.example.domain.model.*
import com.example.domain.risk.RiskManager
import com.example.domain.strategy.NewsFilter
import com.example.domain.strategy.NoNewsFilter
import com.example.domain.strategy.TradingStrategy
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

class TradingEngine(
    private val repository: TradingRepository,
    private val notificationManager: AppNotificationManager,
    private val brokerFactory: (TradingMode) -> BrokerAdapter,
    val marketDataProvider: MarketDataProvider = PaperMarketDataProvider()
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

    private val _activeQuotes = MutableStateFlow<Map<String, Quote>>(emptyMap())
    val activeQuotes: StateFlow<Map<String, Quote>> = _activeQuotes.asStateFlow()

    private val _latestSignal = MutableStateFlow<Signal?>(null)
    val latestSignal: StateFlow<Signal?> = _latestSignal.asStateFlow()

    private val _dailyProfitLoss = MutableStateFlow(0.0)
    val dailyProfitLoss: StateFlow<Double> = _dailyProfitLoss.asStateFlow()

    private val _consecutiveLosses = MutableStateFlow(0)
    val consecutiveLosses: StateFlow<Int> = _consecutiveLosses.asStateFlow()

    private var activeBroker: BrokerAdapter = brokerFactory(TradingMode.PAPER)
    private var positionReconciler = PositionReconciler(repository, activeBroker)

    private val symbolConfigs = ConcurrentHashMap<String, SymbolConfig>().apply {
        put(
            "XAUUSD",
            SymbolConfig(
                symbol = "XAUUSD",
                displayName = "Gold (Spot)",
                brokerSymbol = "XAUUSD",
                assetType = AssetType.COMMODITY,
                digits = 2,
                contractSize = 100.0,
                minLot = 0.01,
                maxLot = 10.0,
                lotStep = 0.01,
                tickSize = 0.01,
                tickValue = 1.0,
                minimumStopDistance = 0.50,
                spreadLimit = 0.60,
                minimumAtr = 0.5,
                maximumAtr = 25.0
            )
        )
        put(
            "BTCUSD",
            SymbolConfig(
                symbol = "BTCUSD",
                displayName = "Bitcoin (Spot)",
                brokerSymbol = "BTCUSD",
                assetType = AssetType.CRYPTO,
                digits = 2,
                contractSize = 1.0,
                minLot = 0.01,
                maxLot = 5.0,
                lotStep = 0.01,
                tickSize = 0.10,
                tickValue = 0.10,
                minimumStopDistance = 25.0,
                spreadLimit = 15.0,
                minimumAtr = 20.0,
                maximumAtr = 3000.0
            )
        )
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

    suspend fun initialize() {
        val config = repository.getOrCreateConfig()
        val mode = runCatching { TradingMode.valueOf(config.mode) }.getOrDefault(TradingMode.PAPER)
        activeBroker = brokerFactory(mode)
        positionReconciler = PositionReconciler(repository, activeBroker)

        updateConfigurationsFromEntity(config)

        // Seed historical candles for analysis
        symbolConfigs.keys.forEach { symbol ->
            val historical = marketDataProvider.getHistoricalCandles(symbol, Timeframe.M15, 80)
            candlesMap[symbol] = historical.toMutableList()
        }

        if (config.isBotEnabled && !config.emergencyStop && !config.safeMode) {
            start()
        }
    }

    fun start() {
        if (engineJob?.isActive == true) return

        stateMachine.transitionTo(StateMachineState.STARTING, "Starting trading loop")
        watchdogManager.start()

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

                // Position reconciliation
                stateMachine.transitionTo(StateMachineState.SYNCING, "Reconciling positions")
                val reconciliation = positionReconciler.reconcile()
                if (reconciliation is ReconciliationResult.Discrepancy) {
                    stateMachine.forceState(
                        StateMachineState.SAFE_MODE,
                        "Position mismatch detected on startup: ${reconciliation.message}"
                    )
                    notificationManager.notifySafeMode(reconciliation.message)
                    return@launch
                }

                val openPositions = repository.getOpenPositions()
                val targetState = if (openPositions.isNotEmpty()) StateMachineState.POSITION_OPEN else StateMachineState.READY
                stateMachine.transitionTo(targetState, "Engine initialized & ready")

                notificationManager.notifyBotStarted(
                    mode = mode.name,
                    symbols = symbolConfigs.keys.toList(),
                    risk = config.defaultRiskPercent
                )

                // Main tick collection & strategy loop
                marketDataProvider.quotes().collect { quote ->
                    processQuote(quote)
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

    fun stop(reason: String = "User requested stop") {
        stateMachine.transitionTo(StateMachineState.STOPPING, reason)
        engineJob?.cancel()
        engineJob = null
        watchdogManager.stop()
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

        // 2. Process active positions with quote (SL/TP check)
        val closedTrades = activeBroker.onTick(quote)
        closedTrades.forEach { trade ->
            repository.recordTrade(trade)
            repository.removePosition(trade.id)

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
                "Closed ${trade.symbol} ${trade.direction}: P/L $${"%.2f".format(trade.profit)} (${trade.profitR}R)",
                trade.symbol
            )

            // Update state machine
            val remaining = repository.getOpenPositions()
            if (remaining.isEmpty() && stateMachine.currentState.value == StateMachineState.POSITION_OPEN) {
                stateMachine.transitionTo(StateMachineState.READY, "All positions closed")
            }
        }

        // 3. Update account info
        val account = activeBroker.getAccount()
        _currentAccount.value = account

        // 4. Update rolling candles
        updateRollingCandle(quote)

        // 5. Evaluate strategy if in valid state
        if (stateMachine.currentState.value in listOf(StateMachineState.READY, StateMachineState.ANALYZING)) {
            evaluateSymbol(quote.symbol, quote)
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
        val riskConfig = RiskConfig(
            defaultRiskPercent = config.defaultRiskPercent,
            maxDailyLossPercent = config.maxDailyLossPercent,
            maxConsecutiveLosses = config.maxConsecutiveLosses,
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
                riskPercent = config.defaultRiskPercent,
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
                    riskPercent = config.defaultRiskPercent,
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
        }
    }

    private fun updateRollingCandle(quote: Quote) {
        val list = candlesMap[quote.symbol] ?: return
        if (list.isEmpty()) return

        val last = list.last()
        val now = System.currentTimeMillis()
        val timeframeMillis = 15 * 60 * 1000L // M15

        if (now - last.openTime >= timeframeMillis) {
            // Close previous candle and open new candle
            list[list.size - 1] = last.copy(isClosed = true)
            list.add(
                Candle(
                    symbol = quote.symbol,
                    timeframe = Timeframe.M15,
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
            if (res.success) {
                repository.removePosition(pos.id)
                val closedTrade = Trade(
                    id = pos.id,
                    symbol = pos.symbol,
                    direction = pos.direction,
                    volume = pos.volume,
                    entryPrice = pos.entryPrice,
                    stopLoss = pos.stopLoss,
                    takeProfit = pos.takeProfit,
                    riskAmount = 0.0,
                    riskPercent = 0.25,
                    rr = 2.0,
                    openedAt = pos.openedAt,
                    closedAt = System.currentTimeMillis(),
                    closePrice = res.executedPrice,
                    profit = pos.unrealizedProfit,
                    profitR = pos.unrealizedR,
                    status = TradeStatus.CLOSED,
                    closeReason = CloseReason.EMERGENCY_STOP,
                    mode = activeBroker.mode
                )
                repository.recordTrade(closedTrade)
                notificationManager.notifyTradeClosed(closedTrade)
            }
        }
        stateMachine.transitionTo(StateMachineState.READY, "All positions liquidated")
    }

    suspend fun resetSafeMode() {
        val config = repository.getOrCreateConfig()
        repository.updateConfig(config.copy(safeMode = false, safeModeReason = ""))
        stateMachine.forceState(StateMachineState.READY, "Safe Mode cleared by operator")
        repository.logEvent(LogLevel.INFO, "TradingEngine", "SAFE_MODE_CLEARED", "Operator cleared safe mode")
    }

    private suspend fun recoverEngine() {
        repository.logEvent(LogLevel.WARN, "TradingEngine", "AUTO_RECOVERY", "Executing automated engine recovery sequence")
        stop("Watchdog recovery restart")
        delay(1000)
        initialize()
        start()
    }

    fun getCandles(symbol: String): List<Candle> = candlesMap[symbol]?.toList() ?: emptyList()

    suspend fun fetchHistoricalCandles(symbol: String, timeframe: Timeframe, count: Int = 80): List<Candle> {
        val candles = marketDataProvider.getHistoricalCandles(symbol, timeframe, count)
        if (candles.isNotEmpty()) {
            candlesMap[symbol] = candles.toMutableList()
        }
        return candles
    }

    fun getMarketSession(symbol: String): MarketSessionInfo = MarketScheduleUtils.getMarketSession(symbol)

    private fun updateConfigurationsFromEntity(config: BotConfigEntity) {
        strategy = TradingStrategy(
            StrategyConfig(
                strategyVersion = config.strategyVersion,
                emaFastPeriod = config.emaFastPeriod,
                emaSlowPeriod = config.emaSlowPeriod,
                adxPeriod = config.adxPeriod,
                adxThreshold = config.adxThreshold,
                atrPeriod = config.atrPeriod,
                atrSlMultiplier = config.atrSlMultiplier,
                riskRewardRatio = config.riskRewardRatio
            )
        )
        riskManager = RiskManager(
            RiskConfig(
                defaultRiskPercent = config.defaultRiskPercent,
                maxDailyLossPercent = config.maxDailyLossPercent,
                maxConsecutiveLosses = config.maxConsecutiveLosses,
                maxOpenPositions = config.maxOpenPositions,
                emergencyStopActive = config.emergencyStop,
                safeModeActive = config.safeMode,
                safeModeReason = config.safeModeReason
            )
        )
    }
}
