package com.example

import android.app.Application
import com.example.data.database.EdgeTraderDatabase
import com.example.data.repositories.TradingRepository
import com.example.notifications.AppNotificationManager
import com.example.security.SecureStorage
import com.example.trading.TradingEngine
import com.example.ai.AiManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class EdgeTraderApp : Application() {

    @Inject lateinit var database: EdgeTraderDatabase
    @Inject lateinit var repository: TradingRepository
    @Inject lateinit var secureStorage: SecureStorage
    @Inject lateinit var notificationManager: AppNotificationManager
    @Inject lateinit var tradingEngine: TradingEngine
    @Inject lateinit var aiManager: AiManager

    override fun onCreate() {
        super.onCreate()
        instance = this

        CoroutineScope(Dispatchers.Default).launch {
            tradingEngine.initialize()
        }
    }

    companion object {
        lateinit var instance: EdgeTraderApp
            private set
    }
}
