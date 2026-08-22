package com.example.broker

import com.example.domain.model.*

/**
 * DemoBrokerAdapter facilitates demonstration & testing against mock demo endpoints
 * using identical risk rules as the core system.
 */
class DemoBrokerAdapter(
    private val paperDelegate: PaperBrokerAdapter = PaperBrokerAdapter(initialBalance = 10000.0)
) : BrokerAdapter {

    override val mode: TradingMode = TradingMode.DEMO

    override suspend fun connect(): Boolean = paperDelegate.connect()
    override suspend fun disconnect() = paperDelegate.disconnect()
    override suspend fun isConnected(): Boolean = paperDelegate.isConnected()
    override suspend fun getAccount(): AccountInfo = paperDelegate.getAccount().copy(mode = TradingMode.DEMO)
    override suspend fun getQuote(symbol: String): Quote = paperDelegate.getQuote(symbol)
    override suspend fun getPositions(): List<Position> = paperDelegate.getPositions().map { it.copy(mode = TradingMode.DEMO) }
    override suspend fun validateOrder(order: OrderRequest): OrderValidation = paperDelegate.validateOrder(order)
    override suspend fun placeOrder(order: OrderRequest): OrderResult = paperDelegate.placeOrder(order)
    override suspend fun closePosition(positionId: String, reason: CloseReason): OrderResult = paperDelegate.closePosition(positionId, reason)
    override suspend fun reconcile(): BrokerState = paperDelegate.reconcile()
    override suspend fun onTick(quote: Quote): List<Trade> = paperDelegate.onTick(quote).map { it.copy(mode = TradingMode.DEMO) }
}
