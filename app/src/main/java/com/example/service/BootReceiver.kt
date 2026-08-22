package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.EdgeTraderApp
import com.example.domain.model.LogLevel
import com.example.domain.model.TradingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val repository = EdgeTraderApp.instance.repository
                    val config = repository.getOrCreateConfig()

                    repository.logEvent(
                        level = LogLevel.WARN,
                        component = "BootReceiver",
                        event = "DEVICE_BOOT_COMPLETED",
                        message = "Device reboot detected. Checking persisted bot state (Enabled: ${config.isBotEnabled}, Mode: ${config.mode})"
                    )

                    // Safety constraint: Never auto-start LIVE trading without explicit user re-confirmation
                    val isLive = config.mode == TradingMode.LIVE.name
                    if (config.isBotEnabled && !config.emergencyStop && !config.safeMode && !isLive) {
                        repository.logEvent(
                            level = LogLevel.INFO,
                            component = "BootReceiver",
                            event = "AUTO_START_SERVICE",
                            message = "Restoring on-device trading service post-boot in ${config.mode} mode"
                        )
                        TradingForegroundService.startService(context)
                    } else if (isLive) {
                        repository.logEvent(
                            level = LogLevel.WARN,
                            component = "BootReceiver",
                            event = "LIVE_RESUME_BLOCKED",
                            message = "Live trading resume blocked post-reboot. Operator manual confirmation required."
                        )
                    }
                } catch (e: Exception) {
                    // Log fail-safe
                }
            }
        }
    }
}
