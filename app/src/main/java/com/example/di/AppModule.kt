package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.ai.AiManager
import com.example.auth.AuthManager
import com.example.broker.AccountManager
import com.example.broker.DemoBrokerAdapter
import com.example.broker.LiveBrokerAdapter
import com.example.broker.PaperBrokerAdapter
import com.example.broker.RealTimeMarketDataProvider
import com.example.data.firestore.FirestoreRepository
import com.example.data.local.*
import com.example.data.repository.IRepository
import com.example.data.repository.TradingRepository
import com.example.domain.model.TradingMode
import com.example.domain.risk.AdvancedRiskManager
import com.example.notifications.AndroidNotifier
import com.example.notifications.AppNotificationManager
import com.example.notifications.TelegramNotifier
import com.example.security.SecureStorage
import com.example.trading.TradingEngine
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

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSecureStorage(@ApplicationContext context: Context): SecureStorage {
        return SecureStorage(context)
    }

    @Provides
    @Singleton
    fun provideAndroidNotifier(@ApplicationContext context: Context): AndroidNotifier {
        return AndroidNotifier(context)
    }

    @Provides
    @Singleton
    fun provideTelegramNotifier(
        secureStorage: SecureStorage,
        repository: IRepository
    ): TelegramNotifier {
        return TelegramNotifier(secureStorage, repository)
    }

    @Provides
    @Singleton
    fun provideAppNotificationManager(
        androidNotifier: AndroidNotifier,
        telegramNotifier: TelegramNotifier
    ): AppNotificationManager {
        return AppNotificationManager(androidNotifier, telegramNotifier)
    }

    @Provides
    @Singleton
    fun provideRealTimeMarketDataProvider(secureStorage: SecureStorage): RealTimeMarketDataProvider {
        return RealTimeMarketDataProvider(secureStorage)
    }

    @Provides
    @Singleton
    fun provideFirestoreRepository(@ApplicationContext context: Context): FirestoreRepository {
        return FirestoreRepository(context)
    }

    @Provides
    @Singleton
    fun provideAuthManager(@ApplicationContext context: Context): AuthManager {
        return AuthManager(context)
    }

    @Provides
    @Singleton
    fun provideAccountManager(
        firestoreRepository: FirestoreRepository,
        secureStorage: SecureStorage
    ): AccountManager {
        return AccountManager(firestoreRepository, secureStorage)
    }

    @Provides
    @Singleton
    fun provideAiManager(secureStorage: SecureStorage): AiManager {
        return AiManager(secureStorage)
    }

    @Provides
    @Singleton
    fun provideAdvancedRiskManager(): AdvancedRiskManager {
        return AdvancedRiskManager()
    }

    @Provides
    @Singleton
    fun provideTradingEngine(
        repository: IRepository,
        notificationManager: AppNotificationManager,
        marketDataProvider: RealTimeMarketDataProvider,
        accountManager: AccountManager
    ): TradingEngine {
        return TradingEngine(
            repository = repository,
            notificationManager = notificationManager,
            brokerFactory = { mode ->
                when (mode) {
                    TradingMode.PAPER -> PaperBrokerAdapter()
                    TradingMode.DEMO -> DemoBrokerAdapter(secureStorage = null)
                    TradingMode.LIVE -> LiveBrokerAdapter(secureStorage = null)
                }
            },
            marketDataProvider = marketDataProvider,
            accountManager = accountManager
        )
    }
}
