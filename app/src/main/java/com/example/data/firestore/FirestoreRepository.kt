package com.example.data.firestore

import android.content.Context
import android.util.Log
import com.example.data.entities.BotConfigEntity
import com.example.data.entities.HeartbeatEntity
import com.example.data.entities.SignalEntity
import com.example.data.entities.SystemEventEntity
import com.example.data.entities.WatchlistItemEntity
import com.example.domain.model.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FirestoreRepository(private val context: Context) {

    private val db: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "Firestore instance not available: ${e.message}")
            null
        }
    }

    private val listeners = mutableListOf<ListenerRegistration>()

    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId.asStateFlow()

    // Local in-memory caches for resilient fallback
    private var localConfig = BotConfigEntity()
    private val _localConfigFlow = MutableStateFlow<BotConfigEntity?>(localConfig)

    private val localTrades = ConcurrentHashMap<String, Trade>()
    private val _localTradesFlow = MutableStateFlow<List<Trade>>(emptyList())

    private val localPositions = ConcurrentHashMap<String, Position>()
    private val _localPositionsFlow = MutableStateFlow<List<Position>>(emptyList())

    private val localSignals = mutableListOf<SignalEntity>()
    private val _localSignalsFlow = MutableStateFlow<List<SignalEntity>>(emptyList())

    private val localLogs = mutableListOf<SystemEventEntity>()
    private val _localLogsFlow = MutableStateFlow<List<SystemEventEntity>>(emptyList())

    private val localHeartbeats = ConcurrentHashMap<String, HeartbeatEntity>()
    private val _localHeartbeatsFlow = MutableStateFlow<List<HeartbeatEntity>>(emptyList())

    private val localWatchlist = ConcurrentHashMap<String, WatchlistItemEntity>()
    private val _localWatchlistFlow = MutableStateFlow<List<WatchlistItemEntity>>(emptyList())

    fun setUserId(uid: String?) {
        _userId.value = uid
    }

    private fun userCollection(collection: String) =
        db?.collection("users")?.document(userId.value ?: LOCAL_ID)?.collection(collection)

    private fun requireUserId(): String = userId.value ?: LOCAL_ID

    // ─── Bot Config ────────────────────────────────────────────────────────

    val configFlow: Flow<BotConfigEntity?> = callbackFlow {
        trySend(localConfig)
        val col = userCollection("config")
        if (col != null) {
            val docRef = col.document("primary_config")
            val registration = docRef.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(localConfig)
                    return@addSnapshotListener
                }
                val data = snapshot.data
                if (data != null) {
                    val cfg = FirestoreMapper.mapToBotConfig(data)
                    localConfig = cfg
                    trySend(cfg)
                } else {
                    trySend(localConfig)
                }
            }
            listeners.add(registration)
            awaitClose { registration.remove() }
        } else {
            val job = CoroutineScope(Dispatchers.Default).launch {
                _localConfigFlow.collect { trySend(it) }
            }
            awaitClose { job.cancel() }
        }
    }

    suspend fun getOrCreateConfig(): BotConfigEntity {
        return try {
            val col = userCollection("config")
            if (col != null) {
                val snapshot = col.document("primary_config").get().await()
                if (snapshot.exists() && snapshot.data != null) {
                    val cfg = FirestoreMapper.mapToBotConfig(snapshot.data!!)
                    localConfig = cfg
                    _localConfigFlow.value = cfg
                    cfg
                } else {
                    val default = BotConfigEntity()
                    localConfig = default
                    _localConfigFlow.value = default
                    col.document("primary_config").set(FirestoreMapper.botConfigToMap(default)).await()
                    default
                }
            } else {
                localConfig
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get config from Firestore, using local: ${e.message}")
            localConfig
        }
    }

    suspend fun updateConfig(config: BotConfigEntity) {
        val updated = config.copy(updatedAt = System.currentTimeMillis())
        localConfig = updated
        _localConfigFlow.value = updated
        try {
            userCollection("config")?.document("primary_config")
                ?.set(FirestoreMapper.botConfigToMap(updated))?.await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update config in Firestore: ${e.message}")
        }
    }

    // ─── Trades ────────────────────────────────────────────────────────────

    val allTradesFlow: Flow<List<Trade>> = callbackFlow {
        trySend(localTrades.values.sortedByDescending { it.openedAt })
        val col = userCollection("trades")
        if (col != null) {
            val registration = col.orderBy("openedAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        trySend(localTrades.values.sortedByDescending { it.openedAt })
                        return@addSnapshotListener
                    }
                    val trades = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { FirestoreMapper.mapToTrade(it) }
                    }
                    trades.forEach { localTrades[it.id] = it }
                    trySend(trades)
                }
            listeners.add(registration)
            awaitClose { registration.remove() }
        } else {
            val job = CoroutineScope(Dispatchers.Default).launch {
                _localTradesFlow.collect { trySend(it) }
            }
            awaitClose { job.cancel() }
        }
    }

    suspend fun getAllTrades(): List<Trade> {
        return try {
            val col = userCollection("trades")
            if (col != null) {
                val snapshot = col.orderBy("openedAt", Query.Direction.DESCENDING).get().await()
                val trades = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { FirestoreMapper.mapToTrade(it) }
                }
                trades.forEach { localTrades[it.id] = it }
                trades
            } else {
                localTrades.values.sortedByDescending { it.openedAt }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get trades from Firestore: ${e.message}")
            localTrades.values.sortedByDescending { it.openedAt }
        }
    }

    suspend fun recordTrade(trade: Trade) {
        localTrades[trade.id] = trade
        _localTradesFlow.value = localTrades.values.sortedByDescending { it.openedAt }
        try {
            userCollection("trades")?.document(trade.id)
                ?.set(FirestoreMapper.tradeToMap(trade))?.await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record trade to Firestore: ${e.message}")
        }
    }

    suspend fun updateTrade(trade: Trade) {
        localTrades[trade.id] = trade
        _localTradesFlow.value = localTrades.values.sortedByDescending { it.openedAt }
        try {
            userCollection("trades")?.document(trade.id)
                ?.set(FirestoreMapper.tradeToMap(trade))?.await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update trade in Firestore: ${e.message}")
        }
    }

    // ─── Positions ─────────────────────────────────────────────────────────

    val openPositionsFlow: Flow<List<Position>> = callbackFlow {
        trySend(localPositions.values.sortedByDescending { it.openedAt })
        val col = userCollection("positions")
        if (col != null) {
            val registration = col.orderBy("openedAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        trySend(localPositions.values.sortedByDescending { it.openedAt })
                        return@addSnapshotListener
                    }
                    val positions = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { FirestoreMapper.mapToPosition(it) }
                    }
                    localPositions.clear()
                    positions.forEach { localPositions[it.id] = it }
                    trySend(positions)
                }
            listeners.add(registration)
            awaitClose { registration.remove() }
        } else {
            val job = CoroutineScope(Dispatchers.Default).launch {
                _localPositionsFlow.collect { trySend(it) }
            }
            awaitClose { job.cancel() }
        }
    }

    suspend fun getOpenPositions(): List<Position> {
        return try {
            val col = userCollection("positions")
            if (col != null) {
                val snapshot = col.orderBy("openedAt", Query.Direction.DESCENDING).get().await()
                val positions = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { FirestoreMapper.mapToPosition(it) }
                }
                localPositions.clear()
                positions.forEach { localPositions[it.id] = it }
                positions
            } else {
                localPositions.values.sortedByDescending { it.openedAt }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get positions from Firestore: ${e.message}")
            localPositions.values.sortedByDescending { it.openedAt }
        }
    }

    suspend fun recordPosition(position: Position) {
        localPositions[position.id] = position
        _localPositionsFlow.value = localPositions.values.sortedByDescending { it.openedAt }
        try {
            userCollection("positions")?.document(position.id)
                ?.set(FirestoreMapper.positionToMap(position))?.await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record position in Firestore: ${e.message}")
        }
    }

    suspend fun removePosition(id: String) {
        localPositions.remove(id)
        _localPositionsFlow.value = localPositions.values.sortedByDescending { it.openedAt }
        try {
            userCollection("positions")?.document(id)?.delete()?.await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove position from Firestore: ${e.message}")
        }
    }

    // ─── Signals ───────────────────────────────────────────────────────────

    val recentSignalsFlow: Flow<List<SignalEntity>> = callbackFlow {
        trySend(synchronized(localSignals) { localSignals.toList() })
        val col = userCollection("signals")
        if (col != null) {
            val registration = col.orderBy("timestamp", Query.Direction.DESCENDING).limit(100)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        trySend(synchronized(localSignals) { localSignals.toList() })
                        return@addSnapshotListener
                    }
                    val signals = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { FirestoreMapper.mapToSignal(it) }
                    }
                    synchronized(localSignals) {
                        localSignals.clear()
                        localSignals.addAll(signals)
                    }
                    trySend(signals)
                }
            listeners.add(registration)
            awaitClose { registration.remove() }
        } else {
            val job = CoroutineScope(Dispatchers.Default).launch {
                _localSignalsFlow.collect { trySend(it) }
            }
            awaitClose { job.cancel() }
        }
    }

    suspend fun recordSignal(signal: Signal) {
        val entity = SignalEntity(
            id = signal.id,
            symbol = signal.symbol,
            direction = signal.direction.name,
            price = signal.price,
            stopLoss = signal.stopLoss,
            takeProfit = signal.takeProfit,
            rrRatio = signal.rrRatio,
            candleTime = signal.candleTime,
            timestamp = signal.timestamp,
            decision = signal.explanation.decision,
            reason = signal.explanation.reason,
            emaFast = signal.explanation.emaFast,
            emaSlow = signal.explanation.emaSlow,
            adx = signal.explanation.adx,
            atr = signal.explanation.atr,
            strategyVersion = signal.strategyVersion
        )
        synchronized(localSignals) {
            localSignals.add(0, entity)
            if (localSignals.size > 100) localSignals.removeAt(localSignals.lastIndex)
            _localSignalsFlow.value = localSignals.toList()
        }
        try {
            userCollection("signals")?.document(signal.id)
                ?.set(FirestoreMapper.signalToMap(entity))?.await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record signal in Firestore: ${e.message}")
        }
    }

    // ─── System Events / Logs ──────────────────────────────────────────────

    val systemLogsFlow: Flow<List<SystemEventEntity>> = callbackFlow {
        trySend(synchronized(localLogs) { localLogs.toList() })
        val col = userCollection("logs")
        if (col != null) {
            val registration = col.orderBy("timestamp", Query.Direction.DESCENDING).limit(200)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        trySend(synchronized(localLogs) { localLogs.toList() })
                        return@addSnapshotListener
                    }
                    val events = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { FirestoreMapper.mapToSystemEvent(it) }
                    }
                    synchronized(localLogs) {
                        localLogs.clear()
                        localLogs.addAll(events)
                    }
                    trySend(events)
                }
            listeners.add(registration)
            awaitClose { registration.remove() }
        } else {
            val job = CoroutineScope(Dispatchers.Default).launch {
                _localLogsFlow.collect { trySend(it) }
            }
            awaitClose { job.cancel() }
        }
    }

    suspend fun logEvent(
        level: LogLevel,
        component: String,
        event: String,
        message: String,
        symbol: String? = null,
        correlationId: String = UUID.randomUUID().toString().take(8)
    ) {
        val sanitizedMessage = sanitizeLog(message)
        val entity = SystemEventEntity(
            id = System.currentTimeMillis(),
            timestamp = System.currentTimeMillis(),
            level = level.name,
            component = component,
            event = event,
            correlationId = correlationId,
            symbol = symbol,
            message = sanitizedMessage
        )
        synchronized(localLogs) {
            localLogs.add(0, entity)
            if (localLogs.size > 200) localLogs.removeAt(localLogs.lastIndex)
            _localLogsFlow.value = localLogs.toList()
        }
        val docId = "${entity.timestamp}_${correlationId}"
        try {
            userCollection("logs")?.document(docId)
                ?.set(FirestoreMapper.systemEventToMap(entity.copy(id = 0)))?.await()
            trimLogs()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log event in Firestore: ${e.message}")
        }
    }

    private suspend fun trimLogs() {
        try {
            val col = userCollection("logs") ?: return
            val snapshot = col.orderBy("timestamp", Query.Direction.DESCENDING).limit(600).get().await()
            if (snapshot.documents.size > 500) {
                val toDelete = snapshot.documents.drop(500)
                val batch = db?.batch() ?: return
                toDelete.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to trim logs in Firestore: ${e.message}")
        }
    }

    // ─── Heartbeats ────────────────────────────────────────────────────────

    val heartbeatsFlow: Flow<List<HeartbeatEntity>> = callbackFlow {
        trySend(localHeartbeats.values.toList())
        val col = userCollection("heartbeats")
        if (col != null) {
            val registration = col.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(localHeartbeats.values.toList())
                    return@addSnapshotListener
                }
                val heartbeats = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { FirestoreMapper.mapToHeartbeat(it) }
                }
                heartbeats.forEach { localHeartbeats[it.component] = it }
                trySend(heartbeats)
            }
            listeners.add(registration)
            awaitClose { registration.remove() }
        } else {
            val job = CoroutineScope(Dispatchers.Default).launch {
                _localHeartbeatsFlow.collect { trySend(it) }
            }
            awaitClose { job.cancel() }
        }
    }

    suspend fun updateHeartbeat(component: String, status: String, details: String = "") {
        val heartbeat = HeartbeatEntity(
            component = component,
            timestamp = System.currentTimeMillis(),
            status = status,
            details = details
        )
        localHeartbeats[component] = heartbeat
        _localHeartbeatsFlow.value = localHeartbeats.values.toList()
        try {
            userCollection("heartbeats")?.document(component)
                ?.set(FirestoreMapper.heartbeatToMap(heartbeat))?.await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update heartbeat in Firestore: ${e.message}")
        }
    }

    // ─── Watchlist ──────────────────────────────────────────────────────────

    val watchlistFlow: Flow<List<WatchlistItemEntity>> = callbackFlow {
        trySend(localWatchlist.values.toList())
        val col = userCollection("watchlist")
        if (col != null) {
            val registration = col.addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(localWatchlist.values.toList())
                    return@addSnapshotListener
                }
                val items = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { map ->
                        WatchlistItemEntity(
                            symbol = map["symbol"] as? String ?: "",
                            displayName = map["displayName"] as? String ?: "",
                            assetType = map["assetType"] as? String ?: "FOREX",
                            addedAt = (map["addedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                            isMonitoring = map["isMonitoring"] as? Boolean ?: true,
                            alertOnSignal = map["alertOnSignal"] as? Boolean ?: true,
                            alertOnSessionOpen = map["alertOnSessionOpen"] as? Boolean ?: false,
                            notes = map["notes"] as? String ?: ""
                        )
                    }
                }
                localWatchlist.clear()
                items.forEach { localWatchlist[it.symbol] = it }
                trySend(items)
            }
            listeners.add(registration)
            awaitClose { registration.remove() }
        } else {
            val job = CoroutineScope(Dispatchers.Default).launch {
                _localWatchlistFlow.collect { trySend(it) }
            }
            awaitClose { job.cancel() }
        }
    }

    suspend fun getWatchlist(): List<WatchlistItemEntity> {
        return try {
            val col = userCollection("watchlist")
            if (col != null) {
                val snapshot = col.get().await()
                val items = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { map ->
                        WatchlistItemEntity(
                            symbol = map["symbol"] as? String ?: "",
                            displayName = map["displayName"] as? String ?: "",
                            assetType = map["assetType"] as? String ?: "FOREX",
                            addedAt = (map["addedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                            isMonitoring = map["isMonitoring"] as? Boolean ?: true,
                            alertOnSignal = map["alertOnSignal"] as? Boolean ?: true,
                            alertOnSessionOpen = map["alertOnSessionOpen"] as? Boolean ?: false,
                            notes = map["notes"] as? String ?: ""
                        )
                    }
                }
                localWatchlist.clear()
                items.forEach { localWatchlist[it.symbol] = it }
                items
            } else {
                localWatchlist.values.toList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get watchlist from Firestore: ${e.message}")
            localWatchlist.values.toList()
        }
    }

    suspend fun addToWatchlist(item: WatchlistItemEntity) {
        localWatchlist[item.symbol] = item
        _localWatchlistFlow.value = localWatchlist.values.toList()
        val data = mapOf(
            "symbol" to item.symbol,
            "displayName" to item.displayName,
            "assetType" to item.assetType,
            "addedAt" to item.addedAt,
            "isMonitoring" to item.isMonitoring,
            "alertOnSignal" to item.alertOnSignal,
            "alertOnSessionOpen" to item.alertOnSessionOpen,
            "notes" to item.notes
        )
        try {
            userCollection("watchlist")?.document(item.symbol)?.set(data)?.await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add to watchlist in Firestore: ${e.message}")
        }
    }

    suspend fun removeFromWatchlist(symbol: String) {
        localWatchlist.remove(symbol)
        _localWatchlistFlow.value = localWatchlist.values.toList()
        try {
            userCollection("watchlist")?.document(symbol)?.delete()?.await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove from watchlist in Firestore: ${e.message}")
        }
    }

    suspend fun getWatchlistItem(symbol: String): WatchlistItemEntity? {
        localWatchlist[symbol]?.let { return it }
        return try {
            val col = userCollection("watchlist") ?: return null
            val snapshot = col.document(symbol).get().await()
            snapshot.data?.let { map ->
                WatchlistItemEntity(
                    symbol = map["symbol"] as? String ?: "",
                    displayName = map["displayName"] as? String ?: "",
                    assetType = map["assetType"] as? String ?: "FOREX",
                    addedAt = (map["addedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    isMonitoring = map["isMonitoring"] as? Boolean ?: true,
                    alertOnSignal = map["alertOnSignal"] as? Boolean ?: true,
                    alertOnSessionOpen = map["alertOnSessionOpen"] as? Boolean ?: false,
                    notes = map["notes"] as? String ?: ""
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get watchlist item: ${e.message}")
            null
        }
    }

    suspend fun watchlistCount(): Int {
        return try {
            val col = userCollection("watchlist")
            if (col != null) col.get().await().size() else localWatchlist.size
        } catch (e: Exception) {
            localWatchlist.size
        }
    }

    // ─── History Clear ─────────────────────────────────────────────────────

    suspend fun clearHistory() {
        localTrades.clear()
        _localTradesFlow.value = emptyList()
        localPositions.clear()
        _localPositionsFlow.value = emptyList()
        synchronized(localLogs) {
            localLogs.clear()
            _localLogsFlow.value = emptyList()
        }
        try {
            val currentDb = db ?: return
            val batch = currentDb.batch()
            userCollection("trades")?.get()?.await()?.documents?.forEach { batch.delete(it.reference) }
            userCollection("positions")?.get()?.await()?.documents?.forEach { batch.delete(it.reference) }
            userCollection("logs")?.get()?.await()?.documents?.forEach { batch.delete(it.reference) }
            batch.commit().await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear history from Firestore: ${e.message}")
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private fun sanitizeLog(msg: String): String {
        return msg.replace(Regex("token=[^&\\s]+", RegexOption.IGNORE_CASE), "token=[REDACTED]")
            .replace(Regex("password=[^&\\s]+", RegexOption.IGNORE_CASE), "password=[REDACTED]")
            .replace(Regex("key=[^&\\s]+", RegexOption.IGNORE_CASE), "key=[REDACTED]")
    }

    fun removeAllListeners() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    companion object {
        private const val TAG = "FirestoreRepository"
        private const val LOCAL_ID = "_local_device"
    }
}
