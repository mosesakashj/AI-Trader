package com.example.data.repository

import com.example.data.local.*
import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TradingRepository @Inject constructor(
    private val tradeDao: TradeDao,
    private val positionDao: PositionDao,
    private val signalDao: SignalDao,
    private val systemEventDao: SystemEventDao,
    private val stateTransitionDao: StateTransitionDao,
    private val configDao: ConfigDao
) {

    // ─── Config ───────────────────────────────────────────────────────────

    suspend fun getOrCreateConfig(): RoomConfig {
        return configDao.getConfig() ?: RoomConfig(
            id = "primary_config",
            configJson = "{}",
            updatedAt = System.currentTimeMillis()
        ).also { configDao.insertConfig(it) }
    }

    suspend fun updateConfig(config: RoomConfig) {
        configDao.updateConfig(config.copy(updatedAt = System.currentTimeMillis()))
    }

    // ─── Trades ───────────────────────────────────────────────────────────

    val allTradesFlow: Flow<List<RoomTrade>> = tradeDao.getAllTradesFlow()

    suspend fun getAllTrades(): List<RoomTrade> = tradeDao.getAllTrades()

    suspend fun recordTrade(trade: RoomTrade) = tradeDao.insertTrade(trade)

    suspend fun updateTrade(trade: RoomTrade) = tradeDao.updateTrade(trade)

    suspend fun getTradeById(id: String): RoomTrade? = tradeDao.getTradeById(id)

    suspend fun getTradesCountForDay(dayStart: Long, dayEnd: Long): Int =
        tradeDao.getTradesCountForDay(dayStart, dayEnd)

    // ─── Positions ────────────────────────────────────────────────────────

    val openPositionsFlow: Flow<List<RoomPosition>> = positionDao.getOpenPositionsFlow()

    suspend fun getOpenPositions(): List<RoomPosition> = positionDao.getOpenPositions()

    suspend fun recordPosition(position: RoomPosition) = positionDao.insertPosition(position)

    suspend fun updatePosition(position: RoomPosition) = positionDao.updatePosition(position)

    suspend fun removePosition(id: String) = positionDao.deletePosition(id)

    suspend fun getPositionsBySymbol(symbol: String): List<RoomPosition> =
        positionDao.getPositionsBySymbol(symbol)

    // ─── Signals ──────────────────────────────────────────────────────────

    val recentSignalsFlow: Flow<List<RoomSignal>> = signalDao.getRecentSignalsFlow()

    suspend fun recordSignal(signal: RoomSignal) = signalDao.insertSignal(signal)

    // ─── System Events ────────────────────────────────────────────────────

    val systemLogsFlow: Flow<List<RoomSystemEvent>> = systemEventDao.getSystemLogsFlow()

    suspend fun logEvent(
        level: LogLevel,
        component: String,
        event: String,
        message: String,
        symbol: String? = null,
        correlationId: String = java.util.UUID.randomUUID().toString().take(8)
    ) {
        val entity = RoomSystemEvent(
            timestamp = System.currentTimeMillis(),
            level = level.name,
            component = component,
            event = event,
            correlationId = correlationId,
            symbol = symbol,
            message = sanitizeLog(message)
        )
        systemEventDao.insertEvent(entity)
        systemEventDao.trimOldEvents()
    }

    // ─── State Transitions ────────────────────────────────────────────────

    suspend fun recordStateTransition(
        fromState: StateMachineState,
        toState: StateMachineState,
        reason: String,
        durationMs: Long = 0
    ) {
        stateTransitionDao.insertTransition(
            RoomStateTransition(
                timestamp = System.currentTimeMillis(),
                fromState = fromState.name,
                toState = toState.name,
                reason = reason,
                durationMs = durationMs
            )
        )
    }

    suspend fun getStateTransitions(limit: Int = 100): List<RoomStateTransition> =
        stateTransitionDao.getTransitions(limit)

    fun getStateTransitionsFlow(limit: Int = 100): Flow<List<RoomStateTransition>> =
        stateTransitionDao.getTransitionsFlow(limit)

    // ─── Helpers ──────────────────────────────────────────────────────────

    private fun sanitizeLog(msg: String): String {
        return msg.replace(Regex("token=[^&\\s]+", RegexOption.IGNORE_CASE), "token=[REDACTED]")
            .replace(Regex("password=[^&\\s]+", RegexOption.IGNORE_CASE), "password=[REDACTED]")
            .replace(Regex("key=[^&\\s]+", RegexOption.IGNORE_CASE), "key=[REDACTED]")
    }

    suspend fun clearHistory() {
        tradeDao.deleteAllTrades()
        positionDao.deleteAllPositions()
        systemEventDao.deleteAllEvents()
    }
}
