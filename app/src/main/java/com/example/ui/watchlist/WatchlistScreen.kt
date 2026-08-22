package com.example.ui.watchlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.domain.model.AssetType
import com.example.domain.model.SymbolCatalog
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    viewModel: WatchlistViewModel = hiltViewModel()
) {
    val uiItems by viewModel.uiItems.collectAsState()
    val availableSymbols by viewModel.availableSymbols.collectAsState()
    val showAddDialog by viewModel.showAddDialog.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Watchlist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("${uiItems.size} pairs tracked", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Button(
                    onClick = { viewModel.showAddDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Pair", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        if (uiItems.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.StarBorder, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                        Text("No pairs in watchlist", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
                        Text("Add pairs to monitor them for trading setups", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }
        }

        items(uiItems, key = { it.symbol }) { item ->
            WatchlistCard(
                item = item,
                onToggleMonitoring = { viewModel.toggleMonitoring(item.symbol, it) },
                onToggleAlert = { viewModel.toggleAlertOnSignal(item.symbol, it) },
                onRemove = { viewModel.removeFromWatchlist(item.symbol) }
            )
        }
    }

    if (showAddDialog) {
        AddWatchlistDialog(
            availableSymbols = availableSymbols,
            onAdd = { viewModel.addToWatchlist(it) },
            onDismiss = { viewModel.hideAddDialog() }
        )
    }
}

@Composable
private fun WatchlistCard(
    item: WatchlistUiItem,
    onToggleMonitoring: (Boolean) -> Unit,
    onToggleAlert: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (item.isMonitoring) PrimaryBlue.copy(alpha = 0.3f) else CardBorderDark),
        modifier = Modifier.fillMaxWidth().testTag("watchlist_card_${item.symbol}")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = when (item.assetType) {
                            AssetType.CRYPTO -> CyanContainer
                            AssetType.COMMODITY -> GoldContainer
                            AssetType.FOREX -> PrimaryBlueContainer
                            else -> SurfaceVariantDark
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                when (item.assetType) {
                                    AssetType.CRYPTO -> Icons.Default.CurrencyBitcoin
                                    AssetType.COMMODITY -> Icons.Default.Grain
                                    else -> Icons.Default.ShowChart
                                },
                                contentDescription = null,
                                tint = when (item.assetType) {
                                    AssetType.CRYPTO -> CyanLight
                                    AssetType.COMMODITY -> GoldHero
                                    else -> PrimaryBlue
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(item.symbol, fontWeight = FontWeight.Bold, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                        Text(item.displayName, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (item.sessionOpen) EmeraldGain else GoldHero,
                        modifier = Modifier.size(8.dp)
                    ) {}
                    Text(
                        if (item.sessionOpen) "OPEN" else "CLOSED",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.sessionOpen) EmeraldGain else GoldHero,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (item.currentPrice != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Price", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(
                            SymbolCatalog.formatPrice(item.symbol, item.currentPrice),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (item.spread != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Spread", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(
                                SymbolCatalog.formatPrice(item.symbol, item.spread),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            if (item.trendBias != null && item.adx != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiniIndicator("Trend", item.trendBias, if (item.trendBias == "BULLISH") EmeraldGain else CrimsonLoss)
                    MiniIndicator("ADX", "%.1f".format(item.adx), if (item.adx >= 25.0) EmeraldGain else TextSecondary)
                    if (item.atr != null) {
                        MiniIndicator("ATR", SymbolCatalog.formatPrice(item.symbol, item.atr), TextSecondary)
                    }
                }
            }

            HorizontalDivider(color = CardBorderDark)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Switch(
                            checked = item.isMonitoring,
                            onCheckedChange = onToggleMonitoring,
                            colors = SwitchDefaults.colors(checkedTrackColor = PrimaryBlue),
                            modifier = Modifier.testTag("monitor_toggle_${item.symbol}")
                        )
                        Text("Monitor", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Switch(
                            checked = item.alertOnSignal,
                            onCheckedChange = onToggleAlert,
                            colors = SwitchDefaults.colors(checkedTrackColor = PrimaryBlue),
                            modifier = Modifier.testTag("alert_toggle_${item.symbol}")
                        )
                        Text("Alert", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag("remove_${item.symbol}")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = CrimsonLoss, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun MiniIndicator(label: String, value: String, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.weight(1f)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun AddWatchlistDialog(
    availableSymbols: List<com.example.domain.model.SymbolConfig>,
    onAdd: (com.example.domain.model.SymbolConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredSymbols = remember(searchQuery, availableSymbols) {
        if (searchQuery.isBlank()) availableSymbols
        else availableSymbols.filter {
            it.symbol.contains(searchQuery, ignoreCase = true) ||
            it.displayName.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = { Text("Add to Watchlist", fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search pairs") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (filteredSymbols.isEmpty()) {
                    Text("No more pairs available", style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(16.dp))
                }

                LazyColumn(modifier = Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filteredSymbols) { cfg ->
                        Card(
                            onClick = {
                                onAdd(cfg)
                                onDismiss()
                            },
                            colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Surface(
                                        color = when (cfg.assetType) {
                                            AssetType.CRYPTO -> CyanContainer
                                            AssetType.COMMODITY -> GoldContainer
                                            else -> PrimaryBlueContainer
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                cfg.symbol.take(2),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = when (cfg.assetType) {
                                                    AssetType.CRYPTO -> CyanLight
                                                    AssetType.COMMODITY -> GoldHero
                                                    else -> PrimaryBlue
                                                }
                                            )
                                        }
                                    }
                                    Column {
                                        Text(cfg.symbol, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Text(cfg.displayName, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    }
                                }
                                Icon(Icons.Default.Add, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}
