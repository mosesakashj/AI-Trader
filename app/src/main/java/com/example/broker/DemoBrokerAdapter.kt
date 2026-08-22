package com.example.broker

import com.example.domain.model.*
import com.example.security.SecureStorage

/**
 * DemoBrokerAdapter connects to Exness MT5 demo account via the same REST gateway
 * as LiveBrokerAdapter, but operates in DEMO mode.
 */
class DemoBrokerAdapter(
    secureStorage: SecureStorage? = null
) : BrokerAdapter {

    override val mode: TradingMode = TradingMode.DEMO

    private val liveDelegate = LiveBrokerAdapter(secureStorage)

    override suspend fun connect(): Boolean = liveDelegate.connect()
    override suspend fun disconnect() = liveDelegate.disconnect()
    override suspend fun isConnected(): Boolean = liveDelegate.isConnected()
    override suspend fun getAccount(): AccountInfo = liveDelegate.getAccount().copy(mode = TradingMode.DEMO)
    override suspend fun getQuote(symbol: String): Quote = liveDelegate.getQuote(symbol)
    override suspend fun getPositions(): List<Position> = liveDelegate.getPositions().map { it.copy(mode = TradingMode.DEMO) }
    override suspend fun validateOrder(order: OrderRequest): OrderValidation = liveDelegate.validateOrder(order)
    override suspend fun placeOrder(order: OrderRequest): OrderResult = liveDelegate.placeOrder(order)
    override suspend fun closePosition(positionId: String, reason: CloseReason): OrderResult = liveDelegate.closePosition(positionId, reason)
    override suspend fun updatePositionSl(positionId: String, newStopLoss: Double, newTakeProfit: Double): Boolean = liveDelegate.updatePositionSl(positionId, newStopLoss, newTakeProfit)
    override suspend fun reconcile(): BrokerState = liveDelegate.reconcile()
    override suspend fun onTick(quote: Quote): List<Trade> = liveDelegate.onTick(quote).map { it.copy(mode = TradingMode.DEMO) }
}
