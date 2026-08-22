package com.example

import com.example.domain.indicators.IndicatorCalculator
import com.example.domain.model.AssetType
import com.example.domain.model.Candle
import com.example.domain.model.SymbolConfig
import com.example.domain.model.Timeframe
import com.example.domain.risk.RiskManager
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {

  @Test
  fun testTechnicalIndicatorsCalculation() {
    val candles = mutableListOf<Candle>()
    var price = 2600.0
    for (i in 0 until 60) {
      val open = price
      val close = price + 0.50
      val high = close + 0.50
      val low = open - 0.50
      candles.add(
        Candle(
          symbol = "XAUUSD",
          timeframe = Timeframe.M15,
          openTime = i * 900000L,
          open = open,
          high = high,
          low = low,
          close = close,
          volume = 100.0,
          isClosed = true
        )
      )
      price = close
    }

    val indicators = IndicatorCalculator.computeLatest(candles)
    assertNotNull(indicators)
    assertTrue("Fast EMA must be > 0", indicators!!.emaFast > 0)
    assertTrue("Slow EMA must be > 0", indicators.emaSlow > 0)
    assertTrue("Fast EMA should be > Slow EMA in upward trend", indicators.emaFast > indicators.emaSlow)
    assertTrue("ADX must be non-negative", indicators.adx >= 0.0)
    assertTrue("ATR must be positive", indicators.atr > 0.0)
  }

  @Test
  fun testRiskPositionSizingFormula() {
    val riskManager = RiskManager()
    val symbolConfig = SymbolConfig(
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

    // Equity: $10,000, Risk: 0.25% ($25 risk budget)
    // Entry: 2650.00, SL: 2645.00 (Risk Distance = $5.00)
    // Contract size 100 -> 1 lot loss for $5 move is 5.00 / 0.01 * $1.00 = $500.
    // 0.05 lots -> $25 loss.
    val lotSize = riskManager.calculatePositionSize(
      equity = 10000.0,
      riskPercent = 0.25,
      entryPrice = 2650.00,
      stopLossPrice = 2645.00,
      symbolConfig = symbolConfig
    )

    assertEquals(0.05, lotSize, 0.01)
  }
}

