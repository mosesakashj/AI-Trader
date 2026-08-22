package com.example.di

import android.content.Context
import com.example.data.database.EdgeTraderDatabase
import com.example.data.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EdgeTraderDatabase {
        return EdgeTraderDatabase.getInstance(context)
    }

    @Provides
    fun provideBotConfigDao(db: EdgeTraderDatabase): BotConfigDao = db.botConfigDao()

    @Provides
    fun provideTradeDao(db: EdgeTraderDatabase): TradeDao = db.tradeDao()

    @Provides
    fun providePositionDao(db: EdgeTraderDatabase): PositionDao = db.positionDao()

    @Provides
    fun provideSignalDao(db: EdgeTraderDatabase): SignalDao = db.signalDao()

    @Provides
    fun provideCandleDao(db: EdgeTraderDatabase): CandleDao = db.candleDao()

    @Provides
    fun provideSystemEventDao(db: EdgeTraderDatabase): SystemEventDao = db.systemEventDao()

    @Provides
    fun provideHeartbeatDao(db: EdgeTraderDatabase): HeartbeatDao = db.heartbeatDao()
}
