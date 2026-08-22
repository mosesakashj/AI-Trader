package com.example.broker

import com.example.domain.model.*
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class PaperBrokerAdapter(
    initialBalance: Double = 10000.0,
    private val leverage: Int = 100
) : BrokerAdapter {

    override val mode: TradingMode = TradingMode.PAPER

    private var balance: Double = initialBalance
    private val openPositions = mutableMapOf<String, Position>()
    private var connected: Boolean = false
    private val currentQuotes = mutableMapOf<String, Quote>()

    private fun getSymbolMeta(symbol: String): Triple<Double, Double, Double> {
        return SymbolCatalog.getMeta(symbol)
    }

    override suspend fun connect(): Boolean {
        connected = true
        return true
    }

    override suspend fun disconnect() {
        connected = false
    }

    override suspend fun isConnected(): Boolean = connected

    override suspend fun getAccount(): AccountInfo {
        var totalUnrealized = 0.0
        var totalMargin = 0.0

        openPositions.values.forEach { pos ->
            totalUnrealized += pos.unrealizedProfit
            val meta = getSymbolMeta(pos.symbol)
            val margin = (pos.volume * meta.third * pos.entryPrice) / leverage
            totalMargin += margin
        }

        val equity = balance + totalUnrealized
        val freeMargin = max(0.0, equity - totalMargin)

        return AccountInfo(
            balance = balance,
            equity = equity,
            freeMargin = freeMargin,
            margin = totalMargin,
            leverage = leverage,
            currency = "USD",
            mode = TradingMode.PAPER,
            serverTime = System.currentTimeMillis()
        )
    }

    override suspend fun getQuote(symbol: String): Quote {
        return currentQuotes[symbol] ?: SymbolCatalog.getInitialQuote(symbol)
    }

    override suspend fun getPositions(): List<Position> {
        return openPositions.values.toList()
    }

    override suspend fun validateOrder(order: OrderRequest): OrderValidation {
        if (order.volume <= 0.0) {
            return OrderValidation(isValid = false, reason = "Volume must be positive")
        }
        val quote = getQuote(order.symbol)
        val currentPrice = if (order.direction == TradeDirection.BUY) quote.ask else quote.bid
        val slippage = abs(currentPrice - order.requestedPrice)

        if (slippage > order.maxSlippage) {
            return OrderValidation(
                isValid = false,
                reason = "Slippage $slippage exceeds maximum allowed ${order.maxSlippage}"
            )
        }

        val account = getAccount()
        val meta = getSymbolMeta(order.symbol)
        val estimatedMargin = (order.volume * meta.third * currentPrice) / leverage

        if (estimatedMargin > account.freeMargin) {
            return OrderValidation(
                isValid = false,
                reason = "Insufficient margin: Required $$estimatedMargin > Free Margin $${account.freeMargin}"
            )
        }

        val priceRisk = abs(order.requestedPrice - order.stopLoss)
        val ticksAtRisk = priceRisk / meta.first
        val theoreticalRisk = ticksAtRisk * meta.second * (order.volume / 1.0)

        return OrderValidation(
            isValid = true,
            reason = "Paper validation successful",
            estimatedMargin = estimatedMargin,
            theoreticalRisk = theoreticalRisk
        )
    }

    override suspend fun placeOrder(order: OrderRequest): OrderResult {
        val validation = validateOrder(order)
        if (!validation.isValid) {
            return OrderResult(success = false, errorMessage = validation.reason)
        }

        val quote = getQuote(order.symbol)
        // Simulate minor realistic execution slippage (0 to 0.5 ticks)
        val slippageTicks = Random.nextDouble(0.0, 0.5)
        val meta = getSymbolMeta(order.symbol)
        val slipAmount = slippageTicks * meta.first

        val executionPrice = if (order.direction == TradeDirection.BUY) {
            quote.ask + slipAmount
        } else {
            quote.bid - slipAmount
        }

        val positionId = "PAPER_POS_${UUID.randomUUID().toString().take(8)}"
        val position = Position(
            id = positionId,
            symbol = order.symbol,
            direction = order.direction,
            volume = order.volume,
            entryPrice = executionPrice,
            currentPrice = executionPrice,
            stopLoss = order.stopLoss,
            takeProfit = order.takeProfit,
            unrealizedProfit = 0.0,
            unrealizedR = 0.0,
            openedAt = System.currentTimeMillis(),
            mode = TradingMode.PAPER
        )

        openPositions[positionId] = position

        return OrderResult(
            success = true,
            orderId = "PAPER_ORD_${UUID.randomUUID().toString().take(8)}",
            positionId = positionId,
            executedPrice = executionPrice,
            executedVolume = order.volume,
            slippage = slipAmount
        )
    }

    override suspend fun closePosition(positionId: String, reason: CloseReason): OrderResult {
        val position = openPositions.remove(positionId)
        if (position == null) {
            return OrderResult(
                success = false,
                errorMessage = "Position $positionId not found"
            )
        }

        val quote = getQuote(position.symbol)
        val exitPrice = if (position.direction == TradeDirection.BUY) quote.bid else quote.ask
        val meta = getSymbolMeta(position.symbol)

        val priceDiff = if (position.direction == TradeDirection.BUY) exitPrice - position.entryPrice else position.entryPrice - exitPrice
        val ticks = priceDiff / meta.first
        val profit = ticks * meta.second * position.volume

        balance += profit

        return OrderResult(
            success = true,
            positionId = positionId,
            executedPrice = exitPrice,
            executedVolume = position.volume
        )
    }

    override suspend fun updatePositionSl(positionId: String, newStopLoss: Double, newTakeProfit: Double): Boolean {
        val position = openPositions[positionId] ?: return false
        val updated = position.copy(
            stopLoss = newStopLoss,
            takeProfit = if (newTakeProfit > 0.0) newTakeProfit else position.takeProfit
        )
        openPositions[positionId] = updated
        return true
    }

    override suspend fun reconcile(): BrokerState {
        return if (connected) {
            BrokerState.Connected(getAccount(), getPositions())
        } else {
            BrokerState.Disconnected("Paper broker not connected")
        }
    }

    override suspend fun onTick(quote: Quote): List<Trade> {
        currentQuotes[quote.symbol] = quote
        val closedTrades = mutableListOf<Trade>()
        val positionsToClose = mutableListOf<Pair<Position, CloseReason>>()

        val meta = getSymbolMeta(quote.symbol)

        openPositions.values.filter { it.symbol == quote.symbol }.forEach { pos ->
            val markPrice = if (pos.direction == TradeDirection.BUY) quote.bid else quote.ask
            val priceDiff = if (pos.direction == TradeDirection.BUY) markPrice - pos.entryPrice else pos.entryPrice - markPrice
            val ticks = priceDiff / meta.first
            val unrealized = ticks * meta.second * pos.volume

            val riskDistance = abs(pos.entryPrice - pos.stopLoss)
            val currentProfitDist = priceDiff
            val unrealizedR = if (riskDistance > 0) currentProfitDist / riskDistance else 0.0

            // Update in-memory position state
            openPositions[pos.id] = pos.copy(
                currentPrice = markPrice,
                unrealizedProfit = unrealized,
                unrealizedR = unrealizedR
            )

            // Check Stop Loss & Take Profit triggers with Break-Even and Trailing Stop awareness
            if (pos.direction == TradeDirection.BUY) {
                if (quote.bid <= pos.stopLoss) {
                    val isBreakEven = pos.stopLoss >= (pos.entryPrice - 0.0001)
                    val isTrailing = pos.stopLoss > (pos.entryPrice + (meta.first * 10.0))
                    val reason = when {
                        isTrailing -> CloseReason.TRAILING_STOP
                        isBreakEven -> CloseReason.BREAK_EVEN
                        else -> CloseReason.STOP_LOSS
                    }
                    positionsToClose.add(pos to reason)
                } else if (quote.bid >= pos.takeProfit) {
                    positionsToClose.add(pos to CloseReason.TAKE_PROFIT)
                }
            } else {
                if (quote.ask >= pos.stopLoss) {
                    val isBreakEven = pos.stopLoss <= (pos.entryPrice + 0.0001)
                    val isTrailing = pos.stopLoss < (pos.entryPrice - (meta.first * 10.0))
                    val reason = when {
                        isTrailing -> CloseReason.TRAILING_STOP
                        isBreakEven -> CloseReason.BREAK_EVEN
                        else -> CloseReason.STOP_LOSS
                    }
                    positionsToClose.add(pos to reason)
                } else if (quote.ask <= pos.takeProfit) {
                    positionsToClose.add(pos to CloseReason.TAKE_PROFIT)
                }
            }
        }

        positionsToClose.forEach { (pos, reason) ->
            val exitPrice = if (reason == CloseReason.TAKE_PROFIT) pos.takeProfit else pos.stopLoss
            val priceDiff = if (pos.direction == TradeDirection.BUY) exitPrice - pos.entryPrice else pos.entryPrice - exitPrice
            val ticks = priceDiff / meta.first
            val realizedProfit = ticks * meta.second * pos.volume
            val riskDistance = abs(pos.entryPrice - pos.stopLoss)
            val profitR = if (riskDistance > 0) priceDiff / riskDistance else 0.0

            balance += realizedProfit
            openPositions.remove(pos.id)

            closedTrades.add(
                Trade(
                    id = pos.id,
                    brokerOrderId = "PAPER_ORD_${pos.id.takeLast(6)}",
                    brokerPositionId = pos.id,
                    symbol = pos.symbol,
                    direction = pos.direction,
                    volume = pos.volume,
                    entryPrice = pos.entryPrice,
                    stopLoss = pos.stopLoss,
                    takeProfit = pos.takeProfit,
                    riskAmount = (riskDistance / meta.first) * meta.second * pos.volume,
                    riskPercent = 0.25,
                    rr = 2.0,
                    openedAt = pos.openedAt,
                    closedAt = System.currentTimeMillis(),
                    closePrice = exitPrice,
                    profit = realizedProfit,
                    profitR = profitR,
                    status = TradeStatus.CLOSED,
                    closeReason = reason,
                    strategyVersion = "1.0.0",
                    mode = TradingMode.PAPER,
                    slippage = 0.0
                )
            )
        }

        return closedTrades
    }
}
