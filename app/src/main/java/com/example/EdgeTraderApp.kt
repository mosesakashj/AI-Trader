package com.example

import android.app.Application
import com.example.ai.AiManager
import com.example.auth.AuthManager
import com.example.broker.DemoBrokerAdapter
import com.example.broker.LiveBrokerAdapter
import com.example.broker.PaperBrokerAdapter
import com.example.broker.RealTimeMarketDataProvider
import com.example.data.firestore.FirestoreRepository
import com.example.domain.model.TradingMode
import com.example.notifications.AndroidNotifier
import com.example.notifications.AppNotificationManager
import com.example.notifications.TelegramNotifier
import com.example.security.SecureStorage
import com.example.trading.TradingEngine
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EdgeTraderApp : Application() {

    lateinit var secureStorage: SecureStorage
        private set
    lateinit var androidNotifier: AndroidNotifier
        private set
    lateinit var telegramNotifier: TelegramNotifier
        private set
    lateinit var notificationManager: AppNotificationManager
        private set
    lateinit var marketDataProvider: RealTimeMarketDataProvider
        private set
    lateinit var tradingEngine: TradingEngine
        private set
    lateinit var aiManager: AiManager
        private set
    lateinit var authManager: AuthManager
        private set
    lateinit var firestoreRepository: FirestoreRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        FirebaseApp.initializeApp(this)

        authManager = AuthManager(this)
        firestoreRepository = FirestoreRepository(this)
        secureStorage = SecureStorage(this)
        androidNotifier = AndroidNotifier(this)
        telegramNotifier = TelegramNotifier(secureStorage, firestoreRepository)
        notificationManager = AppNotificationManager(androidNotifier, telegramNotifier)
        marketDataProvider = RealTimeMarketDataProvider(secureStorage)
        tradingEngine = TradingEngine(
            repository = firestoreRepository,
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
        aiManager = AiManager(secureStorage)

        CoroutineScope(Dispatchers.Default).launch {
            authManager.authState.collect { state ->
                when (state) {
                    is com.example.auth.AuthState.SignedIn -> {
                        firestoreRepository.setUserId(state.userId)
                    }
                    is com.example.auth.AuthState.SignedOut -> {
                        firestoreRepository.setUserId(null)
                    }
                    else -> {}
                }
            }
        }

        CoroutineScope(Dispatchers.Default).launch {
            // Wait for auth state to resolve before initializing engine
            authManager.authState.first { it !is com.example.auth.AuthState.Loading }
            delay(200) // Allow Firestore userId to propagate
            tradingEngine.initialize()
        }
    }

    companion object {
        lateinit var instance: EdgeTraderApp
            private set
    }
}
