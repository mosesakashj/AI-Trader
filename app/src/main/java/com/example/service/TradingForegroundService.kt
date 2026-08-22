package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.notifications.AndroidNotifier
import com.example.trading.TradingEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class TradingForegroundService : Service() {

    @Inject lateinit var tradingEngine: TradingEngine

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var updateJob: Job? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_TRADING"
        const val ACTION_STOP = "ACTION_STOP_TRADING"
        const val ACTION_EMERGENCY_STOP = "ACTION_EMERGENCY_STOP"

        fun startService(context: Context) {
            val intent = Intent(context, TradingForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TradingForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "EdgeTrader::TradingEngineWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(24 * 60 * 60 * 1000L) // 24h
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification("Initializing engine..."))
                startTradingLoop()
            }
            ACTION_STOP -> {
                stopTradingLoop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_EMERGENCY_STOP -> {
                serviceScope.launch {
                    tradingEngine.triggerEmergencyStop("Foreground Notification Emergency Stop")
                }
            }
        }
        return START_STICKY
    }

    private fun startTradingLoop() {
        tradingEngine.start()

        updateJob?.cancel()
        updateJob = serviceScope.launch {
            val engine = tradingEngine
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

            while (isActive) {
                val state = engine.stateMachine.currentState.value
                val quotes = engine.activeQuotes.value
                val xau = quotes["XAUUSD"]?.ask?.let { "XAU: $it" } ?: "XAU: WAIT"
                val btc = quotes["BTCUSD"]?.ask?.let { "BTC: $it" } ?: "BTC: WAIT"
                val lastHeartbeat = timeFormat.format(Date(engine.watchdogManager.getLastEngineHeartbeat()))

                val statusText = "[$state] $xau | $btc | Heartbeat: $lastHeartbeat"
                val notif = buildNotification(statusText)

                val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notifManager.notify(NOTIFICATION_ID, notif)

                delay(5000) // Update notification status every 5 seconds
            }
        }
    }

    private fun stopTradingLoop() {
        updateJob?.cancel()
        tradingEngine.stop("Service stopped")
    }

    private fun buildNotification(contentText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val emergencyIntent = Intent(this, TradingForegroundService::class.java).apply {
            action = ACTION_EMERGENCY_STOP
        }
        val emergencyPendingIntent = PendingIntent.getService(
            this,
            1,
            emergencyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AndroidNotifier.CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("EdgeTrader Engine Active")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_delete, "EMERGENCY STOP", emergencyPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
        serviceScope.cancel()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
