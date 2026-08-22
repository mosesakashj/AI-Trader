package com.example.ui.watchlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.EdgeTraderApp
import com.example.data.entities.WatchlistItemEntity
import com.example.data.firestore.FirestoreRepository
import com.example.domain.indicators.IndicatorCalculator
import com.example.domain.model.*
import com.example.trading.TradingEngine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class WatchlistUiItem(
    val symbol: String,
    val displayName: String,
    val assetType: AssetType,
    val isMonitoring: Boolean,
    val alertOnSignal: Boolean,
    val notes: String,
    val currentPrice: Double?,
    val spread: Double?,
    val sessionOpen: Boolean,
    val emaFast: Double?,
    val emaSlow: Double?,
    val adx: Double?,
    val atr: Double?,
    val trendBias: String?,
    val addedAt: Long
)

class WatchlistViewModel(
    private val context: Context = EdgeTraderApp.instance,
    private val repository: FirestoreRepository = EdgeTraderApp.instance.firestoreRepository,
    private val engine: TradingEngine = EdgeTraderApp.instance.tradingEngine
) : ViewModel() {

    private val _uiItems = MutableStateFlow<List<WatchlistUiItem>>(emptyList())
    val uiItems: StateFlow<List<WatchlistUiItem>> = _uiItems.asStateFlow()

    private val _availableSymbols = MutableStateFlow<List<SymbolConfig>>(emptyList())
    val availableSymbols: StateFlow<List<SymbolConfig>> = _availableSymbols.asStateFlow()

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog.asStateFlow()

    init {
        loadWatchlist()
        loadAvailableSymbols()
        startMonitoring()
    }

    private fun loadWatchlist() {
        viewModelScope.launch {
            repository.watchlistFlow.collect { entities ->
                val quotes = engine.activeQuotes.value
                val items = entities.map { entity ->
                    val quote = quotes[entity.symbol]
                    val session = engine.getMarketSession(entity.symbol)
                    val candles: List<Candle> = engine.getCandles(entity.symbol)
                    val indicators = if (candles.size >= 30) IndicatorCalculator.computeLatest(candles) else null

                    WatchlistUiItem(
                        symbol = entity.symbol,
                        displayName = entity.displayName,
                        assetType = runCatching { AssetType.valueOf(entity.assetType) }.getOrDefault(AssetType.FOREX),
                        isMonitoring = entity.isMonitoring,
                        alertOnSignal = entity.alertOnSignal,
                        notes = entity.notes,
                        currentPrice = quote?.ask,
                        spread = quote?.spread,
                        sessionOpen = session.isOpen,
                        emaFast = indicators?.emaFast,
                        emaSlow = indicators?.emaSlow,
                        adx = indicators?.adx,
                        atr = indicators?.atr,
                        trendBias = if (indicators != null) {
                            if (indicators.emaFast > indicators.emaSlow) "BULLISH" else "BEARISH"
                        } else null,
                        addedAt = entity.addedAt
                    )
                }
                _uiItems.value = items
            }
        }
    }

    private fun loadAvailableSymbols() {
        viewModelScope.launch {
            val watchlist = repository.getWatchlist().map { it.symbol }.toSet()
            _availableSymbols.value = SymbolCatalog.ALL_SYMBOLS.filter { it.symbol !in watchlist }
        }
    }

    fun addToWatchlist(symbolConfig: SymbolConfig) {
        viewModelScope.launch {
            repository.addToWatchlist(
                WatchlistItemEntity(
                    symbol = symbolConfig.symbol,
                    displayName = symbolConfig.displayName,
                    assetType = symbolConfig.assetType.name,
                    isMonitoring = true,
                    alertOnSignal = true
                )
            )
            loadAvailableSymbols()
        }
    }

    fun removeFromWatchlist(symbol: String) {
        viewModelScope.launch {
            repository.removeFromWatchlist(symbol)
            loadAvailableSymbols()
        }
    }

    fun toggleMonitoring(symbol: String, enabled: Boolean) {
        viewModelScope.launch {
            val item = repository.getWatchlistItem(symbol) ?: return@launch
            repository.addToWatchlist(item.copy(isMonitoring = enabled))
        }
    }

    fun toggleAlertOnSignal(symbol: String, enabled: Boolean) {
        viewModelScope.launch {
            val item = repository.getWatchlistItem(symbol) ?: return@launch
            repository.addToWatchlist(item.copy(alertOnSignal = enabled))
        }
    }

    fun showAddDialog() { _showAddDialog.value = true }
    fun hideAddDialog() { _showAddDialog.value = false }

    private fun startMonitoring() {
        viewModelScope.launch {
            engine.activeQuotes.collect { quotes ->
                val currentItems = _uiItems.value
                if (currentItems.isEmpty()) return@collect

                val updatedItems = currentItems.map { item ->
                    val quote = quotes[item.symbol]
                    if (quote != null) {
                        item.copy(
                            currentPrice = quote.ask,
                            spread = quote.spread
                        )
                    } else item
                }
                _uiItems.value = updatedItems
            }
        }
    }
}
