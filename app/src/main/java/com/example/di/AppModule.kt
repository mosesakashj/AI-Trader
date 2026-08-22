package com.example.di

import android.content.Context
import com.example.ai.AiManager
import com.example.broker.DemoBrokerAdapter
import com.example.broker.LiveBrokerAdapter
import com.example.broker.PaperBrokerAdapter
import com.example.broker.RealTimeMarketDataProvider
import com.example.data.database.EdgeTraderDatabase
import com.example.data.repositories.TradingRepository
import com.example.domain.model.TradingMode
import com.example.notifications.AndroidNotifier
import com.example.notifications.AppNotificationManager
import com.example.notifications.TelegramNotifier
import com.example.security.SecureStorage
import com.example.trading.TradingEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
    fun provideTradingRepository(database: EdgeTraderDatabase): TradingRepository {
        return TradingRepository(database)
    }

    @Provides
    @Singleton
    fun provideAndroidNotifier(@ApplicationContext context: Context): AndroidNotifier {
        return AndroidNotifier(context)
    }

    @Provides
    @Singleton
    fun provideTelegramNotifier(secureStorage: SecureStorage, repository: TradingRepository): TelegramNotifier {
        return TelegramNotifier(secureStorage, repository)
    }

    @Provides
    @Singleton
    fun provideAppNotificationManager(androidNotifier: AndroidNotifier, telegramNotifier: TelegramNotifier): AppNotificationManager {
        return AppNotificationManager(androidNotifier, telegramNotifier)
    }

    @Provides
    @Singleton
    fun provideRealTimeMarketDataProvider(secureStorage: SecureStorage): RealTimeMarketDataProvider {
        return RealTimeMarketDataProvider(secureStorage)
    }

    @Provides
    @Singleton
    fun provideTradingEngine(
        repository: TradingRepository,
        notificationManager: AppNotificationManager,
        secureStorage: SecureStorage,
        marketDataProvider: RealTimeMarketDataProvider
    ): TradingEngine {
        return TradingEngine(
            repository = repository,
            notificationManager = notificationManager,
            brokerFactory = { mode ->
                when (mode) {
                    TradingMode.PAPER -> PaperBrokerAdapter()
                    TradingMode.DEMO -> DemoBrokerAdapter()
                    TradingMode.LIVE -> LiveBrokerAdapter(secureStorage)
                }
            },
            marketDataProvider = marketDataProvider
        )
    }

    @Provides
    @Singleton
    fun provideAiManager(secureStorage: SecureStorage): AiManager {
        return AiManager(secureStorage)
    }
}
