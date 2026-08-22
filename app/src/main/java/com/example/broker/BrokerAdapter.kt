package com.example.broker

import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow

sealed class BrokerState {
    data class Connected(val account: AccountInfo, val positions: List<Position>) : BrokerState()
    data class Disconnected(val reason: String) : BrokerState()
    data class Error(val message: String) : BrokerState()
}

interface BrokerAdapter {
    val mode: TradingMode
    suspend fun connect(): Boolean
    suspend fun disconnect()
    suspend fun isConnected(): Boolean
    suspend fun getAccount(): AccountInfo
    suspend fun getQuote(symbol: String): Quote
    suspend fun getPositions(): List<Position>
    suspend fun validateOrder(order: OrderRequest): OrderValidation
    suspend fun placeOrder(order: OrderRequest): OrderResult
    suspend fun closePosition(positionId: String, reason: CloseReason): OrderResult
    suspend fun updatePositionSl(positionId: String, newStopLoss: Double, newTakeProfit: Double = 0.0): Boolean
    suspend fun reconcile(): BrokerState
    suspend fun onTick(quote: Quote): List<Trade> // Checks and executes SL/TP on active positions
}
