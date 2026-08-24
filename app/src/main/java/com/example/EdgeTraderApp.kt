package com.example

import android.app.Application
import com.example.ai.AiManager
import com.example.ai.AiProviderManager
import com.example.auth.AuthManager
import com.example.broker.AccountManager
import com.example.broker.BrokerAdapter
import com.example.broker.DemoBrokerAdapter
import com.example.broker.LiveBrokerAdapter
import com.example.broker.PaperBrokerAdapter
import com.example.broker.RealTimeMarketDataProvider
import com.example.data.firestore.FirestoreRepository
import com.example.data.local.EdgeTraderDatabase
import com.example.data.repository.TradingRepository
import com.example.domain.model.TradingMode
import com.example.domain.risk.AdvancedRiskManager
import com.example.domain.risk.RiskManager
import com.example.notifications.AndroidNotifier
import com.example.notifications.AppNotificationManager
import com.example.notifications.TelegramNotifier
import com.example.security.SecureStorage
import com.example.trading.TradingEngine
import com.example.watchdog.WatchdogManager
import timber.log.Timber

class EdgeTraderApp : Application() {

    lateinit var database: EdgeTraderDatabase
        private set

    lateinit var tradingRepository: TradingRepository
        private set

    lateinit var firestoreRepository: FirestoreRepository
        private set

    lateinit var authManager: AuthManager
        private set

    lateinit var secureStorage: SecureStorage
        private set

    lateinit var androidNotifier: AndroidNotifier
        private set

    lateinit var telegramNotifier: TelegramNotifier
        private set

    lateinit var notificationManager: AppNotificationManager
        private set

    lateinit var accountManager: AccountManager
        private set

    lateinit var paperBrokerAdapter: PaperBrokerAdapter
        private set

    lateinit var demoBrokerAdapter: DemoBrokerAdapter
        private set

    lateinit var liveBrokerAdapter: LiveBrokerAdapter
        private set

    lateinit var realTimeMarketDataProvider: RealTimeMarketDataProvider
        private set

    lateinit var tradingEngine: TradingEngine
        private set

    lateinit var aiManager: AiManager
        private set

    lateinit var aiProviderManager: AiProviderManager
        private set

    lateinit var riskManager: AdvancedRiskManager
        private set

    lateinit var legacyRiskManager: RiskManager
        private set

    lateinit var watchdogManager: WatchdogManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        database = EdgeTraderDatabase.getDatabase(this)
        tradingRepository = TradingRepository(
            tradeDao = database.tradeDao(),
            positionDao = database.positionDao(),
            signalDao = database.signalDao(),
            systemEventDao = database.systemEventDao(),
            stateTransitionDao = database.stateTransitionDao(),
            configDao = database.configDao()
        )
        firestoreRepository = FirestoreRepository(this)
        authManager = AuthManager(this)
        secureStorage = SecureStorage(this)

        androidNotifier = AndroidNotifier(this)
        telegramNotifier = TelegramNotifier(secureStorage, tradingRepository)
        notificationManager = AppNotificationManager(androidNotifier, telegramNotifier)

        realTimeMarketDataProvider = RealTimeMarketDataProvider()
        accountManager = AccountManager(firestoreRepository, secureStorage)

        paperBrokerAdapter = PaperBrokerAdapter()
        demoBrokerAdapter = DemoBrokerAdapter(secureStorage)
        liveBrokerAdapter = LiveBrokerAdapter(secureStorage)

        val brokerFactory: (TradingMode) -> BrokerAdapter = { mode ->
            when (mode) {
                TradingMode.PAPER -> paperBrokerAdapter
                TradingMode.DEMO -> demoBrokerAdapter
                TradingMode.LIVE -> liveBrokerAdapter
            }
        }

        tradingEngine = TradingEngine(
            repository = tradingRepository,
            notificationManager = notificationManager,
            brokerFactory = brokerFactory,
            marketDataProvider = realTimeMarketDataProvider,
            accountManager = accountManager
        )

        aiManager = AiManager(secureStorage)
        aiProviderManager = AiProviderManager(aiManager.providers.value)
        riskManager = AdvancedRiskManager()
        legacyRiskManager = RiskManager()

        watchdogManager = WatchdogManager(
            repository = tradingRepository,
            stateMachine = tradingEngine.stateMachine,
            notificationManager = notificationManager,
            onRecoveryRequested = {
                tradingEngine.recoverState()
            }
        )
    }

    companion object {
        lateinit var instance: EdgeTraderApp
            private set
    }
}
