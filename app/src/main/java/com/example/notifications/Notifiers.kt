package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.data.firestore.FirestoreRepository
import com.example.domain.model.LogLevel
import com.example.domain.model.Signal
import com.example.domain.model.Trade
import com.example.security.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AndroidNotifier(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_SIGNALS = "edgetrader_signals"
        const val CHANNEL_TRADES = "edgetrader_trades"
        const val CHANNEL_ALERTS = "edgetrader_alerts"
        const val CHANNEL_SERVICE = "edgetrader_service_channel"
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val signalChannel = NotificationChannel(
                CHANNEL_SIGNALS,
                "Trading Signals",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority entry signal notifications"
            }

            val tradeChannel = NotificationChannel(
                CHANNEL_TRADES,
                "Trades & Positions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Trade executions, take-profit and stop-loss notifications"
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "System & Risk Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Emergency stop, safe mode, and watchdog alerts"
            }

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "Foreground Trading Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing status of on-device trading loop"
            }

            notificationManager.createNotificationChannels(
                listOf(signalChannel, tradeChannel, alertChannel, serviceChannel)
            )
        }
    }

    fun notifySignal(signal: Signal) {
        val notif = NotificationCompat.Builder(context, CHANNEL_SIGNALS)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("📊 Signal: ${signal.symbol} ${signal.direction}")
            .setContentText("Price: ${signal.price} | SL: ${signal.stopLoss} | TP: ${signal.takeProfit}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Signal ${signal.direction} on ${signal.symbol}\n" +
                            "Price: ${signal.price}\n" +
                            "Stop Loss: ${signal.stopLoss}\n" +
                            "Take Profit: ${signal.takeProfit}\n" +
                            "RR: 1:${signal.rrRatio}\n" +
                            "ADX: ${signal.explanation.adx} | ATR: ${signal.explanation.atr}"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(signal.id.hashCode(), notif)
    }

    fun notifyTradeOpened(trade: Trade) {
        val notif = NotificationCompat.Builder(context, CHANNEL_TRADES)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("🟢 Trade Opened: ${trade.symbol} ${trade.direction}")
            .setContentText("Volume: ${trade.volume} lots @ ${trade.entryPrice}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(trade.id.hashCode(), notif)
    }

    fun notifyTradeClosed(trade: Trade) {
        val isWin = trade.profit >= 0
        val icon = if (isWin) "🔵" else "🔴"
        val notif = NotificationCompat.Builder(context, CHANNEL_TRADES)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("$icon Trade Closed: ${trade.symbol} ${trade.direction}")
            .setContentText("Result: ${if (isWin) "+" else ""}${trade.profitR}R ($${trade.profit}) | Reason: ${trade.closeReason}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(trade.id.hashCode() + 1, notif)
    }

    fun notifySystemAlert(title: String, message: String) {
        val notif = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(title.hashCode(), notif)
    }
}

class TelegramNotifier(
    private val secureStorage: SecureStorage,
    private val repository: FirestoreRepository
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO)

    fun sendTelegramMessage(text: String, onResult: ((Boolean, String) -> Unit)? = null) {
        scope.launch {
            val token = secureStorage.getTelegramToken()
            val chatId = secureStorage.getTelegramChatId()

            if (token.isBlank() || chatId.isBlank()) {
                onResult?.invoke(false, "Telegram Bot Token or Chat ID not configured")
                return@launch
            }

            try {
                val url = "https://api.telegram.org/bot$token/sendMessage"
                val json = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", text)
                    put("parse_mode", "HTML")
                }

                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder().url(url).post(body).build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        repository.logEvent(
                            level = LogLevel.INFO,
                            component = "TelegramNotifier",
                            event = "MESSAGE_SENT",
                            message = "Telegram notification successfully dispatched"
                        )
                        onResult?.invoke(true, "Message sent successfully")
                    } else {
                        val err = "HTTP ${response.code}: ${response.message}"
                        repository.logEvent(
                            level = LogLevel.WARN,
                            component = "TelegramNotifier",
                            event = "SEND_FAILED",
                            message = "Failed to send telegram message: $err"
                        )
                        onResult?.invoke(false, err)
                    }
                }
            } catch (e: Exception) {
                val err = e.localizedMessage ?: "Network error"
                repository.logEvent(
                    level = LogLevel.ERROR,
                    component = "TelegramNotifier",
                    event = "EXCEPTION",
                    message = "Telegram dispatch exception: $err"
                )
                onResult?.invoke(false, err)
            }
        }
    }
}

class AppNotificationManager(
    private val androidNotifier: AndroidNotifier,
    private val telegramNotifier: TelegramNotifier
) {
    fun notifyBotStarted(mode: String, symbols: List<String>, risk: Double) {
        androidNotifier.notifySystemAlert("🟢 EdgeTrader Started", "Mode: $mode | Risk: $risk%")
        val msg = """
            <b>🟢 EdgeTrader Started</b>
            <b>Mode:</b> $mode
            <b>Symbols:</b> ${symbols.joinToString(", ")}
            <b>Risk:</b> $risk%
        """.trimIndent()
        telegramNotifier.sendTelegramMessage(msg)
    }

    fun notifyBotStopped(reason: String) {
        androidNotifier.notifySystemAlert("🔴 EdgeTrader Stopped", "Reason: $reason")
        val msg = """
            <b>🔴 EdgeTrader Stopped</b>
            <b>Reason:</b> $reason
        """.trimIndent()
        telegramNotifier.sendTelegramMessage(msg)
    }

    fun notifyBotRestarted(reason: String, restartCount: Int) {
        androidNotifier.notifySystemAlert("🔄 EdgeTrader Restarted", "Reason: $reason (Count: $restartCount)")
        val msg = """
            <b>🔄 EdgeTrader Restarted</b>
            <b>Reason:</b> $reason
            <b>Restart count:</b> $restartCount
        """.trimIndent()
        telegramNotifier.sendTelegramMessage(msg)
    }

    fun notifySignal(signal: Signal) {
        androidNotifier.notifySignal(signal)
        val msg = """
            <b>📊 SIGNAL DETECTED</b>
            <b>Symbol:</b> ${signal.symbol}
            <b>Direction:</b> ${signal.direction}
            <b>Price:</b> ${signal.price}
            <b>SL:</b> ${signal.stopLoss}
            <b>TP:</b> ${signal.takeProfit}
            <b>RR:</b> 1:${signal.rrRatio}
            <b>Status:</b> PENDING EXECUTION
        """.trimIndent()
        telegramNotifier.sendTelegramMessage(msg)
    }

    fun notifyTradeOpened(trade: Trade) {
        androidNotifier.notifyTradeOpened(trade)
        val msg = """
            <b>🟢 TRADE OPENED</b>
            <b>${trade.symbol} ${trade.direction}</b>
            <b>Volume:</b> ${trade.volume}
            <b>Entry:</b> ${trade.entryPrice}
            <b>SL:</b> ${trade.stopLoss}
            <b>TP:</b> ${trade.takeProfit}
            <b>Risk:</b> ${trade.riskPercent}%
        """.trimIndent()
        telegramNotifier.sendTelegramMessage(msg)
    }

    fun notifyTradeClosed(trade: Trade) {
        androidNotifier.notifyTradeClosed(trade)
        val isWin = trade.profit >= 0
        val icon = if (isWin) "🔵" else "🔴"
        val msg = """
            <b>$icon TRADE CLOSED</b>
            <b>${trade.symbol} ${trade.direction}</b>
            <b>Result:</b> ${if (isWin) "+" else ""}${trade.profitR}R
            <b>P/L:</b> $${"%.2f".format(trade.profit)}
            <b>Reason:</b> ${trade.closeReason}
        """.trimIndent()
        telegramNotifier.sendTelegramMessage(msg)
    }

    fun notifyEmergencyStop(reason: String) {
        androidNotifier.notifySystemAlert("🚨 EMERGENCY STOP ACTIVATED", reason)
        val msg = """
            <b>🚨 EMERGENCY STOP ACTIVATED</b>
            <b>Reason:</b> $reason
            <b>Action:</b> New trades blocked immediately
        """.trimIndent()
        telegramNotifier.sendTelegramMessage(msg)
    }

    fun notifySafeMode(reason: String) {
        androidNotifier.notifySystemAlert("⚠️ SAFE MODE ENTERED", reason)
        val msg = """
            <b>⚠️ SAFE MODE ENTERED</b>
            <b>Reason:</b> $reason
            <b>Action:</b> New trades halted pending manual review
        """.trimIndent()
        telegramNotifier.sendTelegramMessage(msg)
    }

    fun notifyCriticalError(component: String, reason: String) {
        androidNotifier.notifySystemAlert("🚨 CRITICAL TRADING ERROR", "$component: $reason")
        val msg = """
            <b>🚨 CRITICAL TRADING ERROR</b>
            <b>Component:</b> $component
            <b>Reason:</b> $reason
            <b>Action:</b> Trading halted
        """.trimIndent()
        telegramNotifier.sendTelegramMessage(msg)
    }
}
