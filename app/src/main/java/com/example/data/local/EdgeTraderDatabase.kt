package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RoomTrade::class,
        RoomPosition::class,
        RoomSignal::class,
        RoomSystemEvent::class,
        RoomStateTransition::class,
        RoomConfig::class
    ],
    version = 1,
    exportSchema = false
)
abstract class EdgeTraderDatabase : RoomDatabase() {
    abstract fun tradeDao(): TradeDao
    abstract fun positionDao(): PositionDao
    abstract fun signalDao(): SignalDao
    abstract fun systemEventDao(): SystemEventDao
    abstract fun stateTransitionDao(): StateTransitionDao
    abstract fun configDao(): ConfigDao

    companion object {
        const val DATABASE_NAME = "edgetrader.db"

        @Volatile
        private var INSTANCE: EdgeTraderDatabase? = null

        fun getDatabase(context: Context): EdgeTraderDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EdgeTraderDatabase::class.java,
                    DATABASE_NAME
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
