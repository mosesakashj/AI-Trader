package com.example.data.repositories

import com.example.data.database.EdgeTraderDatabase
import com.example.data.entities.*
import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class TradingRepository(private val db: EdgeTraderDatabase) {

    val configFlow: Flow<BotConfigEntity?> = db.botConfigDao().getConfigFlow()
    val allTradesFlow: Flow<List<Trade>> = db.tradeDao().getAllTradesFlow().map { entities ->
        entities.map { it.toDomain() }
    }
    val openPositionsFlow: Flow<List<Position>> = db.positionDao().getAllPositionsFlow().map { entities ->
        entities.map { it.toDomain() }
    }
    val recentSignalsFlow: Flow<List<SignalEntity>> = db.signalDao().getRecentSignalsFlow()
    val systemLogsFlow: Flow<List<SystemEventEntity>> = db.systemEventDao().getRecentEventsFlow()
    val heartbeatsFlow: Flow<List<HeartbeatEntity>> = db.heartbeatDao().getAllHeartbeatsFlow()

    suspend fun getOrCreateConfig(): BotConfigEntity {
        var cfg = db.botConfigDao().getConfig()
        if (cfg == null) {
            cfg = BotConfigEntity()
            db.botConfigDao().insertOrUpdate(cfg)
        }
        return cfg
    }

    suspend fun updateConfig(config: BotConfigEntity) {
        db.botConfigDao().insertOrUpdate(config.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun recordTrade(trade: Trade) {
        db.tradeDao().insert(trade.toEntity())
    }

    suspend fun updateTrade(trade: Trade) {
        db.tradeDao().update(trade.toEntity())
    }

    suspend fun recordPosition(position: Position) {
        db.positionDao().insert(position.toEntity())
    }

    suspend fun removePosition(id: String) {
        db.positionDao().delete(id)
    }

    suspend fun recordSignal(signal: Signal) {
        db.signalDao().insert(
            SignalEntity(
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
        )
    }

    suspend fun logEvent(
        level: LogLevel,
        component: String,
        event: String,
        message: String,
        symbol: String? = null,
        correlationId: String = UUID.randomUUID().toString().take(8)
    ) {
        // Redaction layer: ensure no passwords/tokens in message
        val sanitizedMessage = sanitizeLog(message)
        db.systemEventDao().insert(
            SystemEventEntity(
                timestamp = System.currentTimeMillis(),
                level = level.name,
                component = component,
                event = event,
                correlationId = correlationId,
                symbol = symbol,
                message = sanitizedMessage
            )
        )
        db.systemEventDao().trimLogs()
    }

    suspend fun updateHeartbeat(component: String, status: String, details: String = "") {
        db.heartbeatDao().updateHeartbeat(
            HeartbeatEntity(
                component = component,
                timestamp = System.currentTimeMillis(),
                status = status,
                details = details
            )
        )
    }

    suspend fun getOpenPositions(): List<Position> {
        return db.positionDao().getAllPositions().map { it.toDomain() }
    }

    suspend fun getAllTrades(): List<Trade> {
        return db.tradeDao().getAllTrades().map { it.toDomain() }
    }

    suspend fun clearHistory() {
        db.tradeDao().clearAll()
        db.positionDao().clearAll()
        db.systemEventDao().clearAll()
    }

    val watchlistFlow: Flow<List<WatchlistItemEntity>> = db.watchlistDao().getWatchlistFlow()

    suspend fun getWatchlist(): List<WatchlistItemEntity> = db.watchlistDao().getWatchlist()

    suspend fun addToWatchlist(item: WatchlistItemEntity) = db.watchlistDao().insert(item)

    suspend fun removeFromWatchlist(symbol: String) = db.watchlistDao().delete(symbol)

    suspend fun getWatchlistItem(symbol: String): WatchlistItemEntity? = db.watchlistDao().getItem(symbol)

    suspend fun watchlistCount(): Int = db.watchlistDao().count()

    private fun sanitizeLog(msg: String): String {
        return msg.replace(Regex("token=[^&\\s]+", RegexOption.IGNORE_CASE), "token=[REDACTED]")
            .replace(Regex("password=[^&\\s]+", RegexOption.IGNORE_CASE), "password=[REDACTED]")
            .replace(Regex("key=[^&\\s]+", RegexOption.IGNORE_CASE), "key=[REDACTED]")
    }

    private fun TradeEntity.toDomain(): Trade {
        return Trade(
            id = id,
            brokerOrderId = brokerOrderId,
            brokerPositionId = brokerPositionId,
            symbol = symbol,
            direction = TradeDirection.valueOf(direction),
            volume = volume,
            entryPrice = entryPrice,
            stopLoss = stopLoss,
            takeProfit = takeProfit,
            riskAmount = riskAmount,
            riskPercent = riskPercent,
            rr = rr,
            openedAt = openedAt,
            closedAt = closedAt,
            closePrice = closePrice,
            profit = profit,
            profitR = profitR,
            status = TradeStatus.valueOf(status),
            closeReason = closeReason?.let { runCatching { CloseReason.valueOf(it) }.getOrNull() },
            strategyVersion = strategyVersion,
            mode = runCatching { TradingMode.valueOf(mode) }.getOrDefault(TradingMode.PAPER),
            slippage = slippage
        )
    }

    private fun Trade.toEntity(): TradeEntity {
        return TradeEntity(
            id = id,
            brokerOrderId = brokerOrderId,
            brokerPositionId = brokerPositionId,
            symbol = symbol,
            direction = direction.name,
            volume = volume,
            entryPrice = entryPrice,
            stopLoss = stopLoss,
            takeProfit = takeProfit,
            riskAmount = riskAmount,
            riskPercent = riskPercent,
            rr = rr,
            openedAt = openedAt,
            closedAt = closedAt,
            closePrice = closePrice,
            profit = profit,
            profitR = profitR,
            status = status.name,
            closeReason = closeReason?.name,
            strategyVersion = strategyVersion,
            mode = mode.name,
            slippage = slippage
        )
    }

    private fun PositionEntity.toDomain(): Position {
        return Position(
            id = id,
            symbol = symbol,
            direction = TradeDirection.valueOf(direction),
            volume = volume,
            entryPrice = entryPrice,
            currentPrice = currentPrice,
            stopLoss = stopLoss,
            takeProfit = takeProfit,
            unrealizedProfit = unrealizedProfit,
            unrealizedR = unrealizedR,
            openedAt = openedAt,
            mode = runCatching { TradingMode.valueOf(mode) }.getOrDefault(TradingMode.PAPER)
        )
    }

    private fun Position.toEntity(): PositionEntity {
        return PositionEntity(
            id = id,
            symbol = symbol,
            direction = direction.name,
            volume = volume,
            entryPrice = entryPrice,
            currentPrice = currentPrice,
            stopLoss = stopLoss,
            takeProfit = takeProfit,
            unrealizedProfit = unrealizedProfit,
            unrealizedR = unrealizedR,
            openedAt = openedAt,
            mode = mode.name
        )
    }
}
