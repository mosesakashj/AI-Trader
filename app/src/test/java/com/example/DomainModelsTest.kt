package com.example

import com.example.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class DomainModelsTest {

    @Test
    fun `toClosedTrade creates correct Trade from Position`() {
        val position = Position(
            id = "pos-1",
            symbol = "XAUUSD",
            direction = TradeDirection.BUY,
            volume = 0.05,
            entryPrice = 2650.0,
            currentPrice = 2660.0,
            stopLoss = 2645.0,
            takeProfit = 2660.0,
            unrealizedProfit = 50.0,
            unrealizedR = 2.0,
            openedAt = 1000L,
            mode = TradingMode.PAPER
        )

        val trade = position.toClosedTrade(
            closePrice = 2660.0,
            reason = CloseReason.TAKE_PROFIT,
            mode = TradingMode.PAPER
        )

        assertEquals(position.id, trade.id)
        assertEquals(position.symbol, trade.symbol)
        assertEquals(position.direction, trade.direction)
        assertEquals(position.volume, trade.volume, 0.001)
        assertEquals(position.entryPrice, trade.entryPrice, 0.01)
        assertEquals(position.stopLoss, trade.stopLoss, 0.01)
        assertEquals(position.takeProfit, trade.takeProfit, 0.01)
        assertEquals(2660.0, trade.closePrice!!, 0.01)
        assertEquals(position.unrealizedProfit, trade.profit, 0.01)
        assertEquals(position.unrealizedR, trade.profitR, 0.01)
        assertEquals(TradeStatus.CLOSED, trade.status)
        assertEquals(CloseReason.TAKE_PROFIT, trade.closeReason)
        assertEquals(TradingMode.PAPER, trade.mode)
        assertEquals(0.0, trade.riskAmount, 0.001)
    }

    @Test
    fun `toClosedTrade with EMERGENCY_STOP reason`() {
        val position = Position(
            id = "pos-2", symbol = "BTCUSD", direction = TradeDirection.SELL,
            volume = 0.1, entryPrice = 65000.0, currentPrice = 64500.0,
            stopLoss = 65500.0, takeProfit = 64000.0,
            unrealizedProfit = 50.0, unrealizedR = 1.0,
            openedAt = 2000L, mode = TradingMode.LIVE
        )

        val trade = position.toClosedTrade(
            closePrice = 64500.0,
            reason = CloseReason.EMERGENCY_STOP,
            mode = TradingMode.LIVE
        )

        assertEquals(CloseReason.EMERGENCY_STOP, trade.closeReason)
        assertEquals(TradingMode.LIVE, trade.mode)
    }

    @Test
    fun `Quote spread calculation`() {
        val quote = Quote(symbol = "XAUUSD", bid = 2650.0, ask = 2650.30)
        assertEquals(0.30, quote.spread, 0.001)
    }

    @Test
    fun `SignalExplanation isAllPassed returns true when all checks pass`() {
        val explanation = SignalExplanation(
            symbol = "XAUUSD", direction = TradeDirection.BUY,
            emaFast = 2650.0, emaSlow = 2640.0, adx = 30.0, atr = 5.0,
            trendCheck = true, adxCheck = true, pullbackCheck = true, candleCheck = true,
            spreadCheck = true, riskCheck = true, sessionCheck = true,
            decision = "BUY", reason = "test"
        )
        assertTrue(explanation.isAllPassed)
    }

    @Test
    fun `SignalExplanation isAllPassed returns false when any check fails`() {
        val explanation = SignalExplanation(
            symbol = "XAUUSD", direction = TradeDirection.BUY,
            emaFast = 2650.0, emaSlow = 2640.0, adx = 30.0, atr = 5.0,
            trendCheck = true, adxCheck = false, pullbackCheck = true, candleCheck = true,
            spreadCheck = true, riskCheck = true, sessionCheck = true,
            decision = "NO_TRADE", reason = "adx failed"
        )
        assertFalse(explanation.isAllPassed)
    }

    @Test
    fun `OrderValidation default values`() {
        val validation = OrderValidation(isValid = true)
        assertTrue(validation.isValid)
        assertEquals("", validation.reason)
        assertEquals(0.0, validation.estimatedMargin, 0.001)
        assertEquals(0.0, validation.theoreticalRisk, 0.001)
    }
}
