package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.EdgeTraderApp
import com.example.domain.model.TradingMode
import com.example.notifications.TelegramNotifier
import com.example.ui.components.LiveModeDisclaimerDialog
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onNavigateToSecurity: () -> Unit
) {
    val repository = EdgeTraderApp.instance.repository
    val secureStorage = EdgeTraderApp.instance.secureStorage
    val config by repository.configFlow.collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    var selectedMode by remember(config) {
        mutableStateOf(runCatching { TradingMode.valueOf(config?.mode ?: "PAPER") }.getOrDefault(TradingMode.PAPER))
    }

    var showLiveDisclaimer by remember { mutableStateOf(false) }

    var telegramToken by remember { mutableStateOf(secureStorage.getTelegramToken()) }
    var telegramChatId by remember { mutableStateOf(secureStorage.getTelegramChatId()) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTestingTelegram by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mode Selector Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth().testTag("mode_selector_card")
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Execution Environment Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Select which execution environment the engine connects to:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TradingMode.values().forEach { mode ->
                            val isSelected = selectedMode == mode
                            Button(
                                onClick = {
                                    if (mode == TradingMode.LIVE) {
                                        showLiveDisclaimer = true
                                    } else {
                                        selectedMode = mode
                                        coroutineScope.launch {
                                            val curr = repository.getOrCreateConfig()
                                            repository.updateConfig(curr.copy(mode = mode.name))
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) SurfaceVariantDark else SurfaceDark
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) {
                                        if (mode == TradingMode.LIVE) CrimsonLoss else CyanLight
                                    } else CardBorderDark
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(44.dp).testTag("mode_btn_${mode.name}")
                            ) {
                                Text(
                                    text = mode.name,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) (if (mode == TradingMode.LIVE) CrimsonLoss else CyanLight) else TextSecondary
                                )
                            }
                        }
                    }

                    if (selectedMode == TradingMode.LIVE) {
                        Surface(
                            color = Color(0xFF381419),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CrimsonLoss)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonLoss, modifier = Modifier.size(18.dp))
                                Text(
                                    "LIVE MODE: Native Android MT5 execution blocked by safety adapter stub. Bridge required.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Telegram Alerts Setup Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth().testTag("telegram_config_card")
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Telegram Instant Alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Receive real-time push messages for signals, filled orders, closed trades, and emergency stops directly on Telegram.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                    OutlinedTextField(
                        value = telegramToken,
                        onValueChange = { telegramToken = it },
                        label = { Text("Bot API Token") },
                        visualTransformation = PasswordVisualTransformation(),
                        placeholder = { Text("123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("telegram_token_input")
                    )

                    OutlinedTextField(
                        value = telegramChatId,
                        onValueChange = { telegramChatId = it },
                        label = { Text("Telegram Chat / Channel ID") },
                        placeholder = { Text("@my_channel or -100123456789") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("telegram_chat_id_input")
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                secureStorage.saveTelegramToken(telegramToken)
                                secureStorage.saveTelegramChatId(telegramChatId)
                                testResult = "Settings saved to Android KeyStore!"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanLight),
                            modifier = Modifier.weight(1f).testTag("save_telegram_btn")
                        ) {
                            Text("Save Credentials", fontWeight = FontWeight.Bold, color = BackgroundDark)
                        }

                        OutlinedButton(
                            onClick = {
                                isTestingTelegram = true
                                secureStorage.saveTelegramToken(telegramToken)
                                secureStorage.saveTelegramChatId(telegramChatId)
                                val notifier = TelegramNotifier(secureStorage, repository)
                                notifier.sendTelegramMessage("<b>🔔 EdgeTrader Test Notification</b>\nTelegram integration verified successfully!") { success, msg ->
                                    isTestingTelegram = false
                                    testResult = if (success) "✅ Notification sent successfully!" else "❌ Error: $msg"
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("test_telegram_btn")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Push", color = CyanLight)
                        }
                    }

                    if (testResult != null) {
                        Text(testResult!!, style = MaterialTheme.typography.bodySmall, color = if (testResult!!.startsWith("✅")) EmeraldGain else CrimsonLoss)
                    }
                }
            }
        }

        // Battery Optimization Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = StatusWarning)
                        Text("24/7 Mobile Execution Guidelines", fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Text("1. Exclude EdgeTrader from Android Battery Optimization in Settings > Apps > Special App Access.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("2. Allow Unrestricted Mobile Background Data for continuous quote feeds.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("3. Keep device plugged into power for extended overnight sessions.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }

        // Documentation Link
        item {
            OutlinedButton(
                onClick = onNavigateToSecurity,
                border = BorderStroke(1.dp, CyanLight),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("view_docs_btn")
            ) {
                Text("View Architecture & Security Documentation", color = CyanLight, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showLiveDisclaimer) {
        LiveModeDisclaimerDialog(
            onConfirm = {
                selectedMode = TradingMode.LIVE
                coroutineScope.launch {
                    val curr = repository.getOrCreateConfig()
                    repository.updateConfig(curr.copy(mode = TradingMode.LIVE.name))
                }
                showLiveDisclaimer = false
            },
            onDismiss = { showLiveDisclaimer = false }
        )
    }
}
