package com.example

import android.app.Application
import com.example.auth.AuthManager
import com.example.data.firestore.FirestoreRepository
import com.example.notifications.AppNotificationManager
import com.example.security.SecureStorage
import com.example.trading.TradingEngine
import com.example.ai.AiManager
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class EdgeTraderApp : Application() {

    @Inject lateinit var secureStorage: SecureStorage
    @Inject lateinit var notificationManager: AppNotificationManager
    @Inject lateinit var tradingEngine: TradingEngine
    @Inject lateinit var aiManager: AiManager

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
