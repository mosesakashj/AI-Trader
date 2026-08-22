package com.example

import com.example.domain.model.*
import com.example.domain.risk.RiskManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RiskManagerTest {

    private lateinit var riskManager: RiskManager
    private val xauusdConfig = SymbolConfig(
        symbol = "XAUUSD",
        displayName = "Gold Spot",
        brokerSymbol = "XAUUSD",
        assetType = AssetType.COMMODITY,
        digits = 2,
        contractSize = 100.0,
        minLot = 0.01,
        maxLot = 10.0,
        lotStep = 0.01,
        tickSize = 0.01,
        tickValue = 1.0,
        minimumStopDistance = 0.50,
        spreadLimit = 0.60
    )

    @Before
    fun setup() {
        riskManager = RiskManager(RiskConfig())
    }

    @Test
    fun `position size returns 0 for zero equity`() {
        val lots = riskManager.calculatePositionSize(
            equity = 0.0, riskPercent = 0.25, entryPrice = 2650.0, stopLossPrice = 2645.0, symbolConfig = xauusdConfig
        )
        assertEquals(0.0, lots, 0.001)
    }

    @Test
    fun `position size returns 0 for zero risk distance`() {
        val lots = riskManager.calculatePositionSize(
            equity = 10000.0, riskPercent = 0.25, entryPrice = 2650.0, stopLossPrice = 2650.0, symbolConfig = xauusdConfig
        )
        assertEquals(0.0, lots, 0.001)
    }

    @Test
    fun `position size is clamped to minLot for very small accounts`() {
        val lots = riskManager.calculatePositionSize(
            equity = 100.0, riskPercent = 0.25, entryPrice = 2650.0, stopLossPrice = 2645.0, symbolConfig = xauusdConfig
        )
        assertTrue("Should be >= minLot", lots >= xauusdConfig.minLot)
    }

    @Test
    fun `position size is clamped to maxLot`() {
        val lots = riskManager.calculatePositionSize(
            equity = 10000000.0, riskPercent = 1.0, entryPrice = 2650.0, stopLossPrice = 2649.0, symbolConfig = xauusdConfig
        )
        assertTrue("Should be <= maxLot", lots <= xauusdConfig.maxLot)
    }

    @Test
    fun `validateTrade rejects when emergency stop active`() {
        val rm = RiskManager(RiskConfig(emergencyStopActive = true))
        val signal = makeSignal()
        val account = makeAccount()
        val result = rm.validateTrade(signal, account, xauusdConfig, 0.0, 0, 0)
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("Emergency Stop"))
    }

    @Test
    fun `validateTrade rejects when safe mode active`() {
        val rm = RiskManager(RiskConfig(safeModeActive = true, safeModeReason = "mismatch"))
        val signal = makeSignal()
        val account = makeAccount()
        val result = rm.validateTrade(signal, account, xauusdConfig, 0.0, 0, 0)
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("Safe Mode"))
    }

    @Test
    fun `validateTrade rejects when max positions reached`() {
        val rm = RiskManager(RiskConfig(maxOpenPositions = 1))
        val signal = makeSignal()
        val account = makeAccount()
        val result = rm.validateTrade(signal, account, xauusdConfig, 0.0, 0, 1)
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("Max open positions"))
    }

    @Test
    fun `validateTrade rejects when daily loss limit reached`() {
        val rm = RiskManager(RiskConfig(maxDailyLossPercent = 1.0))
        val signal = makeSignal()
        val account = makeAccount()
        val result = rm.validateTrade(signal, account, xauusdConfig, 1.5, 0, 0)
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("Daily loss limit"))
    }

    @Test
    fun `validateTrade rejects when consecutive losses reached`() {
        val rm = RiskManager(RiskConfig(maxConsecutiveLosses = 3))
        val signal = makeSignal()
        val account = makeAccount()
        val result = rm.validateTrade(signal, account, xauusdConfig, 0.0, 3, 0)
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("consecutive losses"))
    }

    @Test
    fun `validateTrade rejects during loss cooldown`() {
        val now = System.currentTimeMillis()
        val rm = RiskManager(RiskConfig(cooldownAfterLossMinutes = 30))
        val signal = makeSignal()
        val account = makeAccount()
        val result = rm.validateTrade(signal, account, xauusdConfig, 0.0, 0, 0, lastTradeClosedTime = now - 60000, lastTradeWasLoss = true, currentTime = now)
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("Loss cooldown"))
    }

    @Test
    fun `validateTrade rejects during trade cooldown`() {
        val now = System.currentTimeMillis()
        val rm = RiskManager(RiskConfig(cooldownAfterTradeMinutes = 5))
        val signal = makeSignal()
        val account = makeAccount()
        val result = rm.validateTrade(signal, account, xauusdConfig, 0.0, 0, 0, lastTradeClosedTime = now - 60000, lastTradeWasLoss = false, currentTime = now)
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("Trade cooldown"))
    }

    @Test
    fun `validateTrade passes with valid inputs`() {
        val rm = RiskManager(RiskConfig())
        val signal = makeSignal()
        val account = makeAccount()
        val result = rm.validateTrade(signal, account, xauusdConfig, 0.0, 0, 0)
        assertTrue(result.isValid)
        assertEquals("Validation passed", result.reason)
    }

    private fun makeSignal() = Signal(
        id = "sig-1",
        symbol = "XAUUSD",
        direction = TradeDirection.BUY,
        price = 2650.0,
        stopLoss = 2645.0,
        takeProfit = 2660.0,
        rrRatio = 2.0,
        candleTime = System.currentTimeMillis(),
        explanation = SignalExplanation(
            symbol = "XAUUSD",
            direction = TradeDirection.BUY,
            emaFast = 2648.0, emaSlow = 2640.0, adx = 30.0, atr = 5.0,
            trendCheck = true, adxCheck = true, pullbackCheck = true, candleCheck = true,
            spreadCheck = true, riskCheck = true, sessionCheck = true,
            decision = "BUY", reason = "test"
        )
    )

    private fun makeAccount() = AccountInfo(
        balance = 10000.0,
        equity = 10000.0,
        freeMargin = 10000.0,
        leverage = 100
    )
}
