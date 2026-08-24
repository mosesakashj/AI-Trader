package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.example.domain.model.CloseReason
import com.example.domain.model.Position
import com.example.domain.model.Trade
import com.example.domain.model.TradeDirection
import com.example.domain.model.TradeStatus
import com.example.domain.model.TradingMode

@Entity(tableName = "room_trades")
data class RoomTrade(
    @PrimaryKey val id: String,
    val brokerOrderId: String = "",
    val brokerPositionId: String = "",
    val symbol: String,
    val direction: String,
    val volume: Double,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val riskAmount: Double,
    val riskPercent: Double,
    val rr: Double,
    val openedAt: Long,
    val closedAt: Long? = null,
    val closePrice: Double? = null,
    val profit: Double = 0.0,
    val profitR: Double = 0.0,
    val status: String,
    val closeReason: String? = null,
    val strategyVersion: String = "1.0.0",
    val mode: String,
    val slippage: Double = 0.0
)

@Entity(tableName = "room_positions")
data class RoomPosition(
    @PrimaryKey val id: String,
    val symbol: String,
    val direction: String,
    val volume: Double,
    val entryPrice: Double,
    val currentPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val unrealizedProfit: Double,
    val unrealizedR: Double,
    val openedAt: Long,
    val mode: String
)

@Entity(tableName = "room_signals")
data class RoomSignal(
    @PrimaryKey val id: String,
    val symbol: String,
    val direction: String,
    val price: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val rrRatio: Double,
    val candleTime: Long,
    val timestamp: Long,
    val decision: String,
    val reason: String,
    val emaFast: Double,
    val emaSlow: Double,
    val adx: Double,
    val atr: Double,
    val strategyVersion: String
)

@Entity(tableName = "room_system_events")
data class RoomSystemEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val level: String,
    val component: String,
    val event: String,
    val correlationId: String,
    val symbol: String? = null,
    val message: String
)

@Entity(tableName = "room_state_transitions")
data class RoomStateTransition(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val fromState: String,
    val toState: String,
    val reason: String,
    val durationMs: Long = 0
)

@Entity(tableName = "room_config")
data class RoomConfig(
    @PrimaryKey val id: String = "primary_config",
    val configJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

fun RoomTrade.toDomain(): Trade = Trade(
    id = id,
    brokerOrderId = brokerOrderId,
    brokerPositionId = brokerPositionId,
    symbol = symbol,
    direction = runCatching { TradeDirection.valueOf(direction) }.getOrDefault(TradeDirection.BUY),
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
    status = runCatching { TradeStatus.valueOf(status) }.getOrDefault(TradeStatus.CLOSED),
    closeReason = closeReason?.let { runCatching { CloseReason.valueOf(it) }.getOrNull() },
    strategyVersion = strategyVersion,
    mode = runCatching { TradingMode.valueOf(mode) }.getOrDefault(TradingMode.PAPER),
    slippage = slippage
)

fun RoomPosition.toDomain(): Position = Position(
    id = id,
    symbol = symbol,
    direction = runCatching { TradeDirection.valueOf(direction) }.getOrDefault(TradeDirection.BUY),
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
