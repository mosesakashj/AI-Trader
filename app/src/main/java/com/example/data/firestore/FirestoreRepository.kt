package com.example.data.firestore

import android.content.Context
import android.util.Log
import com.example.data.entities.BotConfigEntity
import com.example.data.entities.HeartbeatEntity
import com.example.data.entities.SignalEntity
import com.example.data.entities.SystemEventEntity
import com.example.domain.model.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class FirestoreRepository(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()
    private val listeners = mutableListOf<ListenerRegistration>()

    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId.asStateFlow()

    fun setUserId(uid: String?) {
        _userId.value = uid
    }

    private fun userCollection(collection: String) =
        db.collection("users").document(userId.value ?: LOCAL_ID).collection(collection)

    private fun requireUserId(): String = userId.value ?: LOCAL_ID

    // ─── Bot Config ────────────────────────────────────────────────────────

    val configFlow: Flow<BotConfigEntity?> = callbackFlow {
        val docRef = userCollection("config").document("primary_config")
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(null)
                return@addSnapshotListener
            }
            val data = snapshot?.data
            if (data != null) {
                trySend(FirestoreMapper.mapToBotConfig(data))
            } else {
                trySend(null)
            }
        }
        listeners.add(registration)
        awaitClose { registration.remove() }
    }

    suspend fun getOrCreateConfig(): BotConfigEntity {
        val uid = requireUserId()
        val snapshot = userCollection("config").document("primary_config").get().await()
        return if (snapshot.exists() && snapshot.data != null) {
            FirestoreMapper.mapToBotConfig(snapshot.data!!)
        } else {
            val default = BotConfigEntity()
            userCollection("config").document("primary_config")
                .set(FirestoreMapper.botConfigToMap(default)).await()
            default
        }
    }

    suspend fun updateConfig(config: BotConfigEntity) {
        val updated = config.copy(updatedAt = System.currentTimeMillis())
        userCollection("config").document("primary_config")
            .set(FirestoreMapper.botConfigToMap(updated)).await()
    }

    // ─── Trades ────────────────────────────────────────────────────────────

    val allTradesFlow: Flow<List<Trade>> = callbackFlow {
        val registration = userCollection("trades")
            .orderBy("openedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val trades = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { FirestoreMapper.mapToTrade(it) }
                } ?: emptyList()
                trySend(trades)
            }
        listeners.add(registration)
        awaitClose { registration.remove() }
    }

    suspend fun getAllTrades(): List<Trade> {
        val snapshot = userCollection("trades")
            .orderBy("openedAt", Query.Direction.DESCENDING)
            .get().await()
        return snapshot.documents.mapNotNull { doc ->
            doc.data?.let { FirestoreMapper.mapToTrade(it) }
        }
    }

    suspend fun recordTrade(trade: Trade) {
        userCollection("trades").document(trade.id)
            .set(FirestoreMapper.tradeToMap(trade)).await()
    }

    suspend fun updateTrade(trade: Trade) {
        userCollection("trades").document(trade.id)
            .set(FirestoreMapper.tradeToMap(trade)).await()
    }

    // ─── Positions ─────────────────────────────────────────────────────────

    val openPositionsFlow: Flow<List<Position>> = callbackFlow {
        val registration = userCollection("positions")
            .orderBy("openedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val positions = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { FirestoreMapper.mapToPosition(it) }
                } ?: emptyList()
                trySend(positions)
            }
        listeners.add(registration)
        awaitClose { registration.remove() }
    }

    suspend fun getOpenPositions(): List<Position> {
        val snapshot = userCollection("positions")
            .orderBy("openedAt", Query.Direction.DESCENDING)
            .get().await()
        return snapshot.documents.mapNotNull { doc ->
            doc.data?.let { FirestoreMapper.mapToPosition(it) }
        }
    }

    suspend fun recordPosition(position: Position) {
        userCollection("positions").document(position.id)
            .set(FirestoreMapper.positionToMap(position)).await()
    }

    suspend fun removePosition(id: String) {
        userCollection("positions").document(id).delete().await()
    }

    // ─── Signals ───────────────────────────────────────────────────────────

    val recentSignalsFlow: Flow<List<SignalEntity>> = callbackFlow {
        val registration = userCollection("signals")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val signals = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { FirestoreMapper.mapToSignal(it) }
                } ?: emptyList()
                trySend(signals)
            }
        listeners.add(registration)
        awaitClose { registration.remove() }
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
        userCollection("signals").document(signal.id)
            .set(FirestoreMapper.signalToMap(entity)).await()
    }

    // ─── System Events / Logs ──────────────────────────────────────────────

    val systemLogsFlow: Flow<List<SystemEventEntity>> = callbackFlow {
        val registration = userCollection("logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(200)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val events = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { FirestoreMapper.mapToSystemEvent(it) }
                } ?: emptyList()
                trySend(events)
            }
        listeners.add(registration)
        awaitClose { registration.remove() }
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
            timestamp = System.currentTimeMillis(),
            level = level.name,
            component = component,
            event = event,
            correlationId = correlationId,
            symbol = symbol,
            message = sanitizedMessage
        )
        val docId = "${entity.timestamp}_${correlationId}"
        userCollection("logs").document(docId)
            .set(FirestoreMapper.systemEventToMap(entity.copy(id = 0))).await()
        trimLogs()
    }

    private suspend fun trimLogs() {
        val snapshot = userCollection("logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(600)
            .get().await()
        if (snapshot.documents.size > 500) {
            val toDelete = snapshot.documents.drop(500)
            val batch = db.batch()
            toDelete.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    // ─── Heartbeats ────────────────────────────────────────────────────────

    val heartbeatsFlow: Flow<List<HeartbeatEntity>> = callbackFlow {
        val registration = userCollection("heartbeats")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val heartbeats = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { FirestoreMapper.mapToHeartbeat(it) }
                } ?: emptyList()
                trySend(heartbeats)
            }
        listeners.add(registration)
        awaitClose { registration.remove() }
    }

    suspend fun updateHeartbeat(component: String, status: String, details: String = "") {
        val heartbeat = HeartbeatEntity(
            component = component,
            timestamp = System.currentTimeMillis(),
            status = status,
            details = details
        )
        userCollection("heartbeats").document(component)
            .set(FirestoreMapper.heartbeatToMap(heartbeat)).await()
    }

    // ─── Watchlist ──────────────────────────────────────────────────────────

    val watchlistFlow: Flow<List<WatchlistItemEntity>> = callbackFlow {
        val registration = userCollection("watchlist")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { doc ->
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
                } ?: emptyList()
                trySend(items)
            }
        listeners.add(registration)
        awaitClose { registration.remove() }
    }

    suspend fun getWatchlist(): List<WatchlistItemEntity> {
        val snapshot = userCollection("watchlist").get().await()
        return snapshot.documents.mapNotNull { doc ->
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
    }

    suspend fun addToWatchlist(item: WatchlistItemEntity) {
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
        userCollection("watchlist").document(item.symbol).set(data).await()
    }

    suspend fun removeFromWatchlist(symbol: String) {
        userCollection("watchlist").document(symbol).delete().await()
    }

    suspend fun getWatchlistItem(symbol: String): WatchlistItemEntity? {
        val snapshot = userCollection("watchlist").document(symbol).get().await()
        return snapshot.data?.let { map ->
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

    suspend fun watchlistCount(): Int {
        val snapshot = userCollection("watchlist").get().await()
        return snapshot.size()
    }

    // ─── History Clear ─────────────────────────────────────────────────────

    suspend fun clearHistory() {
        val batch = db.batch()
        userCollection("trades").get().await().documents.forEach { batch.delete(it.reference) }
        userCollection("positions").get().await().documents.forEach { batch.delete(it.reference) }
        userCollection("logs").get().await().documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
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

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
    return kotlinx.coroutines.tasks.await(this)
}
