package com.example.data.dao

import androidx.room.*
import com.example.data.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BotConfigDao {
    @Query("SELECT * FROM bot_config WHERE id = 'primary_config' LIMIT 1")
    fun getConfigFlow(): Flow<BotConfigEntity?>

    @Query("SELECT * FROM bot_config WHERE id = 'primary_config' LIMIT 1")
    suspend fun getConfig(): BotConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(config: BotConfigEntity)
}

@Dao
interface TradeDao {
    @Query("SELECT * FROM trades ORDER BY openedAt DESC")
    fun getAllTradesFlow(): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades ORDER BY openedAt DESC")
    suspend fun getAllTrades(): List<TradeEntity>

    @Query("SELECT * FROM trades WHERE status = 'OPEN'")
    suspend fun getOpenTrades(): List<TradeEntity>

    @Query("SELECT * FROM trades WHERE status = 'OPEN'")
    fun getOpenTradesFlow(): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE id = :id LIMIT 1")
    suspend fun getTradeById(id: String): TradeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trade: TradeEntity)

    @Update
    suspend fun update(trade: TradeEntity)

    @Query("DELETE FROM trades")
    suspend fun clearAll()
}

@Dao
interface PositionDao {
    @Query("SELECT * FROM positions ORDER BY openedAt DESC")
    fun getAllPositionsFlow(): Flow<List<PositionEntity>>

    @Query("SELECT * FROM positions ORDER BY openedAt DESC")
    suspend fun getAllPositions(): List<PositionEntity>

    @Query("SELECT * FROM positions WHERE id = :id LIMIT 1")
    suspend fun getPositionById(id: String): PositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(position: PositionEntity)

    @Query("DELETE FROM positions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM positions")
    suspend fun clearAll()
}

@Dao
interface SignalDao {
    @Query("SELECT * FROM signals ORDER BY timestamp DESC LIMIT 100")
    fun getRecentSignalsFlow(): Flow<List<SignalEntity>>

    @Query("SELECT * FROM signals ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSignal(): SignalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(signal: SignalEntity)
}

@Dao
interface CandleDao {
    @Query("SELECT * FROM candles WHERE symbol = :symbol AND timeframe = :timeframe ORDER BY openTime DESC LIMIT :limit")
    suspend fun getCandles(symbol: String, timeframe: String, limit: Int = 100): List<CandleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandles(candles: List<CandleEntity>)

    @Query("DELETE FROM candles WHERE symbol = :symbol AND timeframe = :timeframe")
    suspend fun deleteOldCandles(symbol: String, timeframe: String)
}

@Dao
interface SystemEventDao {
    @Query("SELECT * FROM system_events ORDER BY timestamp DESC LIMIT 200")
    fun getRecentEventsFlow(): Flow<List<SystemEventEntity>>

    @Insert
    suspend fun insert(event: SystemEventEntity)

    @Query("DELETE FROM system_events WHERE id NOT IN (SELECT id FROM system_events ORDER BY timestamp DESC LIMIT 500)")
    suspend fun trimLogs()

    @Query("DELETE FROM system_events")
    suspend fun clearAll()
}

@Dao
interface HeartbeatDao {
    @Query("SELECT * FROM heartbeats")
    fun getAllHeartbeatsFlow(): Flow<List<HeartbeatEntity>>

    @Query("SELECT * FROM heartbeats WHERE component = :component LIMIT 1")
    suspend fun getHeartbeat(component: String): HeartbeatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateHeartbeat(heartbeat: HeartbeatEntity)
}
