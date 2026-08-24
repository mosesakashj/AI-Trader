package com.example.data.repository

import com.example.data.entities.BotConfigEntity
import com.example.data.local.*
import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow

interface IRepository {
    suspend fun getOrCreateBotConfig(): BotConfigEntity
    suspend fun updateBotConfig(config: BotConfigEntity)

    val allTradesFlow: Flow<List<RoomTrade>>
    suspend fun getAllTrades(): List<RoomTrade>
    suspend fun recordTrade(trade: RoomTrade)
    suspend fun updateTrade(trade: RoomTrade)
    suspend fun getTradeById(id: String): RoomTrade?
    suspend fun getTradesCountForDay(dayStart: Long, dayEnd: Long): Int

    val openPositionsFlow: Flow<List<RoomPosition>>
    suspend fun getOpenPositions(): List<RoomPosition>
    suspend fun recordPosition(position: RoomPosition)
    suspend fun updatePosition(position: RoomPosition)
    suspend fun removePosition(id: String)
    suspend fun getPositionsBySymbol(symbol: String): List<RoomPosition>

    val recentSignalsFlow: Flow<List<RoomSignal>>
    suspend fun recordSignal(signal: RoomSignal)

    val systemLogsFlow: Flow<List<RoomSystemEvent>>
    suspend fun logEvent(
        level: LogLevel,
        component: String,
        event: String,
        message: String,
        symbol: String? = null,
        correlationId: String = java.util.UUID.randomUUID().toString().take(8)
    )

    suspend fun recordStateTransition(
        fromState: StateMachineState,
        toState: StateMachineState,
        reason: String,
        durationMs: Long = 0
    )

    suspend fun getStateTransitions(limit: Int = 100): List<RoomStateTransition>
    fun getStateTransitionsFlow(limit: Int = 100): Flow<List<RoomStateTransition>>

    suspend fun clearHistory()
}
