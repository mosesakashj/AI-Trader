package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.data.local.*
import com.example.data.repository.IRepository
import com.example.data.repository.TradingRepository
import dagger.Binds
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
        return Room.databaseBuilder(
            context,
            EdgeTraderDatabase::class.java,
            EdgeTraderDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideTradeDao(db: EdgeTraderDatabase): TradeDao = db.tradeDao()

    @Provides
    fun providePositionDao(db: EdgeTraderDatabase): PositionDao = db.positionDao()

    @Provides
    fun provideSignalDao(db: EdgeTraderDatabase): SignalDao = db.signalDao()

    @Provides
    fun provideSystemEventDao(db: EdgeTraderDatabase): SystemEventDao = db.systemEventDao()

    @Provides
    fun provideStateTransitionDao(db: EdgeTraderDatabase): StateTransitionDao = db.stateTransitionDao()

    @Provides
    fun provideConfigDao(db: EdgeTraderDatabase): ConfigDao = db.configDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindRepository(impl: TradingRepository): IRepository
}
