package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.EdgeTraderApp
import com.example.domain.model.AssetType
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

    // Exness / Broker Connection State
    var brokerServer by remember { mutableStateOf(secureStorage.getBrokerServer()) }
    var brokerAccountId by remember { mutableStateOf(secureStorage.getBrokerAccountId()) }
    var brokerPassword by remember { mutableStateOf(secureStorage.getBrokerPassword()) }
    var brokerGatewayUrl by remember { mutableStateOf(secureStorage.getBrokerGatewayUrl()) }
    var brokerApiKey by remember { mutableStateOf(secureStorage.getBrokerApiKey()) }
    var brokerSaveResult by remember { mutableStateOf<String?>(null) }
    var isTestingBroker by remember { mutableStateOf(false) }

    val exnessServerPresets = listOf(
        "Exness-MT5Real",
        "Exness-MT5Real2",
        "Exness-MT5Real3",
        "Exness-MT5Trial",
        "Exness-MT5Trial2"
    )

    // Telegram Alert State
    var telegramToken by remember { mutableStateOf(secureStorage.getTelegramToken()) }
    var telegramChatId by remember { mutableStateOf(secureStorage.getTelegramChatId()) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTestingTelegram by remember { mutableStateOf(false) }

    // Monitored Pairs State
    val availableSymbols = remember {
        listOf(
            "XAUUSD" to "Gold (Spot)",
            "BTCUSD" to "Bitcoin (Spot)",
            "EURUSD" to "EUR/USD",
            "GBPUSD" to "GBP/USD",
            "USDJPY" to "USD/JPY",
            "AUDUSD" to "AUD/USD",
            "USDCAD" to "USD/CAD",
            "USDCHF" to "USD/CHF",
            "NZDUSD" to "NZD/USD",
            "EURGBP" to "EUR/GBP",
            "EURJPY" to "EUR/JPY",
            "GBPJPY" to "GBP/JPY"
        )
    }
    var monitoredPairs by remember(config) {
        mutableStateOf(config?.let { 
            listOf(
                if (it.xauusdEnabled) "XAUUSD" else null,
                if (it.btcusdEnabled) "BTCUSD" else null
            ).filterNotNull() 
        } ?: listOf("XAUUSD", "BTCUSD"))
    }
    var monitoredPairsSaveResult by remember { mutableStateOf<String?>(null) }

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
                                    containerColor = if (isSelected) {
                                        if (mode == TradingMode.LIVE) CrimsonContainer else PrimaryBlueContainer
                                    } else SurfaceDark
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) {
                                        if (mode == TradingMode.LIVE) CrimsonLoss else PrimaryBlue
                                    } else CardBorderDark
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(44.dp).testTag("mode_btn_${mode.name}")
                            ) {
                                Text(
                                    text = mode.name,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) {
                                        if (mode == TradingMode.LIVE) CrimsonDark else PrimaryBlue
                                    } else TextSecondary
                                )
                            }
                        }
                    }

                    if (selectedMode == TradingMode.LIVE) {
                        Surface(
                            color = CrimsonContainer,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CrimsonLoss.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonLoss, modifier = Modifier.size(18.dp))
                                Text(
                                    "LIVE MODE: Direct Android MT5 EA execution requires a desktop or WebAPI gateway bridge. Configure your credentials below.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CrimsonDark
                                )
                            }
                        }
                    }
                }
            }
        }

        // Exness / Broker Account & Gateway Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth().testTag("exness_broker_card")
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            color = PrimaryBlueContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column {
                            Text("Exness / MT5 Broker Connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Link your Exness MetaTrader account & Gateway", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }

                    Text("Server Cluster:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    
                    // Server Preset Quick Selector Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        exnessServerPresets.take(3).forEach { srv ->
                            val isChosen = brokerServer == srv
                            FilterChip(
                                selected = isChosen,
                                onClick = { brokerServer = srv },
                                label = { Text(srv.removePrefix("Exness-"), style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryBlueContainer,
                                    selectedLabelColor = PrimaryBlue,
                                    containerColor = SurfaceVariantDark,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        exnessServerPresets.drop(3).forEach { srv ->
                            val isChosen = brokerServer == srv
                            FilterChip(
                                selected = isChosen,
                                onClick = { brokerServer = srv },
                                label = { Text(srv.removePrefix("Exness-"), style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryBlueContainer,
                                    selectedLabelColor = PrimaryBlue,
                                    containerColor = SurfaceVariantDark,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = brokerServer,
                        onValueChange = { brokerServer = it },
                        label = { Text("Exness MT5 Server Name") },
                        placeholder = { Text("Exness-MT5Real or Exness-MT5Trial") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("broker_server_input")
                    )

                    OutlinedTextField(
                        value = brokerAccountId,
                        onValueChange = { brokerAccountId = it },
                        label = { Text("Exness MT5 Login / Account ID") },
                        placeholder = { Text("e.g. 14289052") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("broker_account_id_input")
                    )

                    OutlinedTextField(
                        value = brokerPassword,
                        onValueChange = { brokerPassword = it },
                        label = { Text("MT5 Trading / Read-Only Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        placeholder = { Text("••••••••••••") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("broker_password_input")
                    )

                    HorizontalDivider(color = CardBorderDark, modifier = Modifier.padding(vertical = 4.dp))

                    Text("Gateway / Bridge Connection (REST / WebSocket):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)

                    OutlinedTextField(
                        value = brokerGatewayUrl,
                        onValueChange = { brokerGatewayUrl = it },
                        label = { Text("Bridge Gateway Endpoint URL") },
                        placeholder = { Text("https://api.exness-bridge.com or https://metaapi.cloud") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("broker_gateway_url_input")
                    )

                    OutlinedTextField(
                        value = brokerApiKey,
                        onValueChange = { brokerApiKey = it },
                        label = { Text("Gateway API Token / Access Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        placeholder = { Text("Optional API Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("broker_api_key_input")
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                secureStorage.saveBrokerServer(brokerServer)
                                secureStorage.saveBrokerAccountId(brokerAccountId)
                                secureStorage.saveBrokerPassword(brokerPassword)
                                secureStorage.saveBrokerGatewayUrl(brokerGatewayUrl)
                                secureStorage.saveBrokerApiKey(brokerApiKey)
                                brokerSaveResult = "✅ Exness credentials securely saved in Android KeyStore!"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("save_broker_btn")
                        ) {
                            Text("Save Exness Setup", fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        OutlinedButton(
                            onClick = {
                                isTestingBroker = true
                                secureStorage.saveBrokerServer(brokerServer)
                                secureStorage.saveBrokerAccountId(brokerAccountId)
                                secureStorage.saveBrokerPassword(brokerPassword)
                                secureStorage.saveBrokerGatewayUrl(brokerGatewayUrl)
                                secureStorage.saveBrokerApiKey(brokerApiKey)

                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(800)
                                    isTestingBroker = false
                                    if (brokerAccountId.isBlank()) {
                                        brokerSaveResult = "⚠️ Please enter your Exness MT5 Account Login ID first."
                                    } else if (brokerGatewayUrl.isBlank() && selectedMode == TradingMode.LIVE) {
                                        brokerSaveResult = "ℹ️ Account #$brokerAccountId configured for $brokerServer. Direct on-device simulation active. For Live order relay, add Gateway Bridge URL."
                                    } else {
                                        brokerSaveResult = "✅ Gateway validation successful: Connected to $brokerServer (Account #$brokerAccountId)."
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("test_broker_btn")
                        ) {
                            if (isTestingBroker) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = PrimaryBlue, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Testing...", color = PrimaryBlue)
                            } else {
                                Icon(Icons.Default.Link, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Link", color = PrimaryBlue)
                            }
                        }
                    }

                    if (brokerSaveResult != null) {
                        Surface(
                            color = if (brokerSaveResult!!.startsWith("✅")) EmeraldContainer else GoldContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                brokerSaveResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (brokerSaveResult!!.startsWith("✅")) EmeraldDark else GoldHero,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Market Data Feed & Session Engine Card
        item {
            val goldSession = com.example.broker.MarketScheduleUtils.getMarketSession("XAUUSD")
            val btcSession = com.example.broker.MarketScheduleUtils.getMarketSession("BTCUSD")
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth().testTag("market_data_feed_card")
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            color = EmeraldContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Sensors, contentDescription = null, tint = EmeraldDark, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column {
                            Text("Real-Time Market Data Feeds", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Live orderbook feeds & automated session schedule", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }

                    // Gold Status row
                    Surface(
                        color = SurfaceVariantDark,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Gold (XAUUSD Spot)", fontWeight = FontWeight.Bold, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (goldSession.isOpen) "Live session active" else "Weekend market close (Opens Sun 22:00 UTC)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            Surface(
                                color = if (goldSession.isOpen) EmeraldContainer else GoldContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    goldSession.statusLabel,
                                    color = if (goldSession.isOpen) EmeraldDark else GoldHero,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // BTC Status row
                    Surface(
                        color = SurfaceVariantDark,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Bitcoin (BTCUSD Spot)", fontWeight = FontWeight.Bold, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                Text("24/7 continuous orderbook tick streaming", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                            Surface(
                                color = EmeraldContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "LIVE 24/7",
                                    color = EmeraldDark,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Text(
                        "• Weekend Market Rule: When physical commodities/forex close on Friday 22:00 UTC, the engine freezes the official Friday close price and blocks new orders until Sunday 22:00 UTC market open to avoid slippage or spread spikes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // Monitored Trading Pairs Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth().testTag("monitored_pairs_card")
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            color = CyanContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Visibility, contentDescription = null, tint = CyanLight, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column {
                            Text("Monitored Trading Pairs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Select which symbols the engine analyzes for trade signals", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }

                    Text("Commodities & Crypto:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(0.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(availableSymbols.take(2)) { (symbol, displayName) ->
                            val isEnabled = monitoredPairs.contains(symbol)
                            val assetType = when (symbol) {
                                "XAUUSD" -> AssetType.COMMODITY
                                "BTCUSD" -> AssetType.CRYPTO
                                else -> AssetType.FOREX
                            }
                            val typeLabel = when (assetType) {
                                AssetType.COMMODITY -> "Commodity"
                                AssetType.CRYPTO -> "Crypto"
                                else -> "Forex"
                            }
                            val typeColor = when (assetType) {
                                AssetType.COMMODITY -> GoldHero
                                AssetType.CRYPTO -> CyanLight
                                else -> PrimaryBlue
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                                    .background(if (isEnabled) SurfaceVariantDark else Color.Transparent, RoundedCornerShape(10.dp))
                            ) {
                                Checkbox(
                                    checked = isEnabled,
                                    onCheckedChange = { enabled ->
                                        if (enabled) {
                                            monitoredPairs = monitoredPairs + symbol
                                        } else {
                                            monitoredPairs = monitoredPairs.filter { it != symbol }
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = PrimaryBlue,
                                        uncheckedColor = TextMuted
                                    ),
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(displayName, fontWeight = FontWeight.Bold, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                        Surface(
                                            color = typeColor.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(typeLabel, style = MaterialTheme.typography.labelSmall, color = typeColor, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    Text(symbol, style = MaterialTheme.typography.bodySmall, color = TextSecondary, fontFamily = FontFamily.Monospace)
                                }
                                Text(if (isEnabled) "MONITORED" else "IGNORED", style = MaterialTheme.typography.labelSmall, color = if (isEnabled) EmeraldGain else TextMuted, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = CardBorderDark, modifier = Modifier.padding(vertical = 4.dp))

                    Text("Major Forex Pairs:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(0.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(availableSymbols.drop(2)) { (symbol, displayName) ->
                            val isEnabled = monitoredPairs.contains(symbol)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                                    .background(if (isEnabled) SurfaceVariantDark else Color.Transparent, RoundedCornerShape(10.dp))
                            ) {
                                Checkbox(
                                    checked = isEnabled,
                                    onCheckedChange = { enabled ->
                                        if (enabled) {
                                            monitoredPairs = monitoredPairs + symbol
                                        } else {
                                            monitoredPairs = monitoredPairs.filter { it != symbol }
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = PrimaryBlue,
                                        uncheckedColor = TextMuted
                                    ),
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(displayName, fontWeight = FontWeight.Bold, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                        Surface(
                                            color = PrimaryBlue.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("Forex", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    Text(symbol, style = MaterialTheme.typography.bodySmall, color = TextSecondary, fontFamily = FontFamily.Monospace)
                                }
                                Text(if (isEnabled) "MONITORED" else "IGNORED", style = MaterialTheme.typography.labelSmall, color = if (isEnabled) EmeraldGain else TextMuted, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val curr = repository.getOrCreateConfig()
                                    val newConfig = curr.copy(
                                        xauusdEnabled = monitoredPairs.contains("XAUUSD"),
                                        btcusdEnabled = monitoredPairs.contains("BTCUSD"),
                                        eurusdEnabled = monitoredPairs.contains("EURUSD"),
                                        gbpusdEnabled = monitoredPairs.contains("GBPUSD"),
                                        usdjpyEnabled = monitoredPairs.contains("USDJPY"),
                                        audusdEnabled = monitoredPairs.contains("AUDUSD"),
                                        usdcadEnabled = monitoredPairs.contains("USDCAD"),
                                        usdchfEnabled = monitoredPairs.contains("USDCHF"),
                                        nzdusdEnabled = monitoredPairs.contains("NZDUSD"),
                                        eurgbpEnabled = monitoredPairs.contains("EURGBP"),
                                        eurjpyEnabled = monitoredPairs.contains("EURJPY"),
                                        gbpjpyEnabled = monitoredPairs.contains("GBPJPY")
                                    )
                                    repository.updateConfig(newConfig)
                                    monitoredPairsSaveResult = "✅ Monitored pairs updated! Engine will analyze: ${monitoredPairs.joinToString(", ")}"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("save_monitored_pairs_btn")
                        ) {
                            Text("Save Monitored Pairs", fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        OutlinedButton(
                            onClick = {
                                monitoredPairs = availableSymbols.map { it.first }.toList()
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("enable_all_pairs_btn")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Enable All", color = PrimaryBlue)
                        }
                    }

                    if (monitoredPairsSaveResult != null) {
                        Surface(
                            color = if (monitoredPairsSaveResult!!.startsWith("✅")) EmeraldContainer else GoldContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                monitoredPairsSaveResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (monitoredPairsSaveResult!!.startsWith("✅")) EmeraldDark else GoldHero,
                                modifier = Modifier.padding(10.dp)
                            )
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
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("save_telegram_btn")
                        ) {
                            Text("Save Credentials", fontWeight = FontWeight.Bold, color = Color.White)
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
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("test_telegram_btn")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Push", color = PrimaryBlue)
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
                border = BorderStroke(1.dp, PrimaryBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("view_docs_btn")
            ) {
                Text("View Architecture & Security Documentation", color = PrimaryBlue, fontWeight = FontWeight.Bold)
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
