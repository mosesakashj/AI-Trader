package com.example

import android.app.Application
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EdgeTraderApp : Application() {

    lateinit var database: EdgeTraderDatabase
        private set

    lateinit var repository: TradingRepository
        private set

    lateinit var secureStorage: SecureStorage
        private set

    lateinit var notificationManager: AppNotificationManager
        private set

    lateinit var tradingEngine: TradingEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = EdgeTraderDatabase.getInstance(this)
        repository = TradingRepository(database)
        secureStorage = SecureStorage(this)

        val androidNotifier = AndroidNotifier(this)
        val telegramNotifier = TelegramNotifier(secureStorage, repository)
        notificationManager = AppNotificationManager(androidNotifier, telegramNotifier)

        val marketDataProvider = RealTimeMarketDataProvider(secureStorage)

        tradingEngine = TradingEngine(
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

        CoroutineScope(Dispatchers.Default).launch {
            tradingEngine.initialize()
        }
    }

    companion object {
        lateinit var instance: EdgeTraderApp
            private set
    }
}
