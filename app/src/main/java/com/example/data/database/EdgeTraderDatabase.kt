package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.*
import com.example.data.entities.*

@Database(
    entities = [
        BotConfigEntity::class,
        TradeEntity::class,
        PositionEntity::class,
        SignalEntity::class,
        CandleEntity::class,
        SystemEventEntity::class,
        HeartbeatEntity::class,
        WatchlistItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class EdgeTraderDatabase : RoomDatabase() {
    abstract fun botConfigDao(): BotConfigDao
    abstract fun tradeDao(): TradeDao
    abstract fun positionDao(): PositionDao
    abstract fun signalDao(): SignalDao
    abstract fun candleDao(): CandleDao
    abstract fun systemEventDao(): SystemEventDao
    abstract fun heartbeatDao(): HeartbeatDao
    abstract fun watchlistDao(): WatchlistDao

    companion object {
        @Volatile
        private var INSTANCE: EdgeTraderDatabase? = null

        fun getInstance(context: Context): EdgeTraderDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EdgeTraderDatabase::class.java,
                    "edgetrader_local.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
