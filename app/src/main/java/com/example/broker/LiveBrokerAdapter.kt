package com.example.broker

import com.example.domain.model.*

/**
 * LiveBrokerAdapter explicitly enforces the safety architecture constraint:
 * MetaQuotes MT5 on Android is a sandboxed client and does NOT support Expert Advisor
 * scripts or direct client-side automated execution sockets without an external broker WebAPI or desktop bridge.
 *
 * This adapter remains in a safe, unprovisioned state preventing live financial harm.
 */
class LiveBrokerAdapter : BrokerAdapter {

    override val mode: TradingMode = TradingMode.LIVE

    override suspend fun connect(): Boolean {
        // Direct Android-to-Exness automated execution is not officially supported without a bridge.
        return false
    }

    override suspend fun disconnect() {}

    override suspend fun isConnected(): Boolean = false

    override suspend fun getAccount(): AccountInfo {
        return AccountInfo(
            balance = 0.0,
            equity = 0.0,
            freeMargin = 0.0,
            mode = TradingMode.LIVE
        )
    }

    override suspend fun getQuote(symbol: String): Quote {
        return Quote(symbol, 0.0, 0.0)
    }

    override suspend fun getPositions(): List<Position> = emptyList()

    override suspend fun validateOrder(order: OrderRequest): OrderValidation {
        return OrderValidation(
            isValid = false,
            reason = "Direct Android MT5 Live Execution is unavailable. See BROKER_INTEGRATION.md for supported architecture."
        )
    }

    override suspend fun placeOrder(order: OrderRequest): OrderResult {
        return OrderResult(
            success = false,
            errorMessage = "Live order execution blocked: Native Android MT5 EA API does not exist. Use Paper Trading mode."
        )
    }

    override suspend fun closePosition(positionId: String, reason: CloseReason): OrderResult {
        return OrderResult(success = false, errorMessage = "Live adapter unavailable")
    }

    override suspend fun reconcile(): BrokerState {
        return BrokerState.Error("Live adapter unavailable on Android-only architecture")
    }

    override suspend fun onTick(quote: Quote): List<Trade> = emptyList()
}
