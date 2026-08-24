package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {
    @Query("SELECT * FROM room_trades ORDER BY openedAt DESC")
    fun getAllTradesFlow(): Flow<List<RoomTrade>>

    @Query("SELECT * FROM room_trades ORDER BY openedAt DESC")
    suspend fun getAllTrades(): List<RoomTrade>

    @Query("SELECT * FROM room_trades WHERE status = 'OPEN'")
    suspend fun getOpenTrades(): List<RoomTrade>

    @Query("SELECT * FROM room_trades WHERE id = :id")
    suspend fun getTradeById(id: String): RoomTrade?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: RoomTrade)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrades(trades: List<RoomTrade>)

    @Update
    suspend fun updateTrade(trade: RoomTrade)

    @Delete
    suspend fun deleteTrade(trade: RoomTrade)

    @Query("DELETE FROM room_trades")
    suspend fun deleteAllTrades()

    @Query("SELECT COUNT(*) FROM room_trades")
    suspend fun getTradeCount(): Int

    @Query("SELECT COUNT(*) FROM room_trades WHERE closedAt >= :dayStart AND closedAt < :dayEnd")
    suspend fun getTradesCountForDay(dayStart: Long, dayEnd: Long): Int
}

@Dao
interface PositionDao {
    @Query("SELECT * FROM room_positions ORDER BY openedAt DESC")
    fun getOpenPositionsFlow(): Flow<List<RoomPosition>>

    @Query("SELECT * FROM room_positions ORDER BY openedAt DESC")
    suspend fun getOpenPositions(): List<RoomPosition>

    @Query("SELECT * FROM room_positions WHERE id = :id")
    suspend fun getPositionById(id: String): RoomPosition?

    @Query("SELECT * FROM room_positions WHERE symbol = :symbol")
    suspend fun getPositionsBySymbol(symbol: String): List<RoomPosition>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosition(position: RoomPosition)

    @Update
    suspend fun updatePosition(position: RoomPosition)

    @Query("DELETE FROM room_positions WHERE id = :id")
    suspend fun deletePosition(id: String)

    @Query("DELETE FROM room_positions")
    suspend fun deleteAllPositions()

    @Query("SELECT COUNT(*) FROM room_positions")
    suspend fun getOpenPositionCount(): Int
}

@Dao
interface SignalDao {
    @Query("SELECT * FROM room_signals ORDER BY timestamp DESC LIMIT 100")
    fun getRecentSignalsFlow(): Flow<List<RoomSignal>>

    @Query("SELECT * FROM room_signals ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSignals(limit: Int = 100): List<RoomSignal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignal(signal: RoomSignal)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignals(signals: List<RoomSignal>)

    @Query("DELETE FROM room_signals")
    suspend fun deleteAllSignals()
}

@Dao
interface SystemEventDao {
    @Query("SELECT * FROM room_system_events ORDER BY timestamp DESC LIMIT 200")
    fun getSystemLogsFlow(): Flow<List<RoomSystemEvent>>

    @Query("SELECT * FROM room_system_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getSystemLogs(limit: Int = 200): List<RoomSystemEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: RoomSystemEvent)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<RoomSystemEvent>)

    @Query("DELETE FROM room_system_events")
    suspend fun deleteAllEvents()

    @Query("DELETE FROM room_system_events WHERE id NOT IN (SELECT id FROM room_system_events ORDER BY timestamp DESC LIMIT 500)")
    suspend fun trimOldEvents()
}

@Dao
interface StateTransitionDao {
    @Query("SELECT * FROM room_state_transitions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getTransitions(limit: Int = 100): List<RoomStateTransition>

    @Query("SELECT * FROM room_state_transitions ORDER BY timestamp DESC LIMIT :limit")
    fun getTransitionsFlow(limit: Int = 100): Flow<List<RoomStateTransition>>

    @Insert
    suspend fun insertTransition(transition: RoomStateTransition)

    @Query("DELETE FROM room_state_transitions")
    suspend fun deleteAllTransitions()
}

@Dao
interface ConfigDao {
    @Query("SELECT * FROM room_config WHERE id = :id")
    suspend fun getConfig(id: String = "primary_config"): RoomConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: RoomConfig)

    @Update
    suspend fun updateConfig(config: RoomConfig)
}
