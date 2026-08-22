# Domain Models Reference

## Enums (`Enums.kt`)

### TradingMode
- `PAPER` - Simulated trading
- `DEMO` - Demo account
- `LIVE` - Real money

### TradeDirection
- `BUY` - Long position
- `SELL` - Short position

### TradeStatus
- `OPEN` - Active trade
- `CLOSED` - Completed
- `CANCELLED` - Cancelled before fill
- `REJECTED` - Broker rejected

### CloseReason
- `TAKE_PROFIT` - Hit TP
- `STOP_LOSS` - Hit SL
- `MANUAL` - User closed
- `EMERGENCY_STOP` - Emergency
- `SAFE_MODE` - Safe mode triggered
- `EXPIRED` - Time expiry

### StateMachineState
- `STOPPED`, `STARTING`, `CONNECTING`, `SYNCING`, `READY`, `ANALYZING`, `SIGNAL_FOUND`, `VALIDATING`, `EXECUTING`, `POSITION_OPEN`, `PAUSED`, `SAFE_MODE`, `ERROR`, `STOPPING`

### ConnectionState
- `ONLINE`, `DEGRADED`, `OFFLINE`, `RECONNECTING`

### LogLevel
- `DEBUG`, `INFO`, `WARN`, `ERROR`, `CRITICAL`

### Timeframe
- `M1` (1m), `M5` (5m), `M15` (15m), `M30` (30m), `H1` (1h), `H4` (4h), `D1` (1d)

### AssetType
- `COMMODITY`, `CRYPTO`, `FOREX`, `INDEX`

---

## Data Classes (`DomainModels.kt`)

### Candle
```kotlin
data class Candle(
    val symbol: String,
    val timeframe: Timeframe,
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double = 0.0,
    val isClosed: Boolean = true
)
```

### Quote
```kotlin
data class Quote(
    val symbol: String,
    val bid: Double,
    val ask: Double,
    val timestamp: Long = System.currentTimeMillis()
) {
    val spread: Double get() = ask - bid
}
```

### Signal
```kotlin
data class Signal(
    val id: String,
    val symbol: String,
    val direction: TradeDirection,
    val price: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val rrRatio: Double,
    val candleTime: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val explanation: SignalExplanation,
    val strategyVersion: String = "1.0.0"
)
```

### SignalExplanation
```kotlin
data class SignalExplanation(
    val symbol: String,
    val direction: TradeDirection,
    val emaFast: Double,
    val emaSlow: Double,
    val adx: Double,
    val atr: Double,
    val trendCheck: Boolean,
    val adxCheck: Boolean,
    val pullbackCheck: Boolean,
    val candleCheck: Boolean,
    val spreadCheck: Boolean,
    val riskCheck: Boolean,
    val sessionCheck: Boolean,
    val decision: String,
    val reason: String
) {
    val isAllPassed: Boolean get() = trendCheck && adxCheck && pullbackCheck && candleCheck && spreadCheck && riskCheck && sessionCheck
}
```

### Trade
```kotlin
data class Trade(
    val id: String,
    val brokerOrderId: String = "",
    val brokerPositionId: String = "",
    val symbol: String,
    val direction: TradeDirection,
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
    val status: TradeStatus = TradeStatus.OPEN,
    val closeReason: CloseReason? = null,
    val strategyVersion: String = "1.0.0",
    val mode: TradingMode = TradingMode.PAPER,
    val slippage: Double = 0.0
)
```

### Position
```kotlin
data class Position(
    val id: String,
    val symbol: String,
    val direction: TradeDirection,
    val volume: Double,
    val entryPrice: Double,
    val currentPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val unrealizedProfit: Double,
    val unrealizedR: Double,
    val openedAt: Long,
    val mode: TradingMode = TradingMode.PAPER
)
```

### SymbolConfig
```kotlin
data class SymbolConfig(
    val symbol: String,
    val displayName: String,
    val brokerSymbol: String,
    val assetType: AssetType,
    val digits: Int,
    val contractSize: Double,
    val minLot: Double,
    val maxLot: Double,
    val lotStep: Double,
    val tickSize: Double,
    val tickValue: Double,
    val minimumStopDistance: Double,
    val spreadLimit: Double,
    val minimumAtr: Double = 0.0,
    val maximumAtr: Double = 1000.0,
    val enabled: Boolean = true
)
```

### StrategyConfig
```kotlin
data class StrategyConfig(
    val strategyVersion: String = "1.0.0",
    val emaFastPeriod: Int = 20,
    val emaSlowPeriod: Int = 50,
    val adxPeriod: Int = 14,
    val adxThreshold: Double = 25.0,
    val atrPeriod: Int = 14,
    val atrSlMultiplier: Double = 1.5,
    val riskRewardRatio: Double = 2.0,
    val maxCandleExtensionAtr: Double = 2.0,
    val sessionStartHour: Int = 0,
    val sessionEndHour: Int = 24,
    val timezone: String = "UTC"
)
```

### RiskConfig
```kotlin
data class RiskConfig(
    val defaultRiskPercent: Double = 0.25,
    val maxRiskPercent: Double = 1.0,
    val maxDailyLossPercent: Double = 1.0,
    val maxOpenPositions: Int = 1,
    val maxConsecutiveLosses: Int = 3,
    val maxTradesPerDay: Int = 10,
    val maxSpreadPips: Double = 30.0,
    val maxSlippagePips: Double = 10.0,
    val cooldownAfterLossMinutes: Int = 30,
    val cooldownAfterTradeMinutes: Int = 5,
    val emergencyStopActive: Boolean = false,
    val safeModeActive: Boolean = false,
    val safeModeReason: String = ""
)
```

### AccountInfo
```kotlin
data class AccountInfo(
    val balance: Double,
    val equity: Double,
    val freeMargin: Double,
    val margin: Double = 0.0,
    val leverage: Int = 100,
    val currency: String = "USD",
    val mode: TradingMode = TradingMode.PAPER,
    val serverTime: Long = System.currentTimeMillis()
)
```

### OrderRequest / OrderValidation / OrderResult
```kotlin
data class OrderRequest(
    val clientOrderId: String,
    val symbol: String,
    val direction: TradeDirection,
    val volume: Double,
    val requestedPrice: Double,
    val stopLoss: Double,
    val takeProfit: Double,
    val maxSlippage: Double,
    val mode: TradingMode
)

data class OrderValidation(
    val isValid: Boolean,
    val reason: String = "",
    val estimatedMargin: Double = 0.0,
    val theoreticalRisk: Double = 0.0
)

data class OrderResult(
    val success: Boolean,
    val orderId: String = "",
    val positionId: String = "",
    val executedPrice: Double = 0.0,
    val executedVolume: Double = 0.0,
    val slippage: Double = 0.0,
    val errorMessage: String = ""
)
```

---

## Backtest Models (`BacktestingEngine.kt`)

### BacktestResult
```kotlin
data class BacktestResult(
    val symbol: String,
    val timeframe: Timeframe,
    val candleCount: Int,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRate: Double,
    val totalProfitLoss: Double,
    val profitFactor: Double,
    val maxDrawdownAmount: Double,
    val maxDrawdownPercent: Double,
    val averageR: Double,
    val expectancy: Double,
    val maxConsecutiveLosses: Int,
    val averageWin: Double,
    val averageLoss: Double,
    val trades: List<Trade>,
    val equityCurve: List<Pair<Long, Double>>
)
```

### WalkForwardResult
```kotlin
data class WalkForwardResult(
    val inSampleResult: BacktestResult,
    val validationResult: BacktestResult,
    val outOfSampleResult: BacktestResult,
    val robustnessScore: Double
)
```

---

## Market Session (`MarketScheduleUtils.kt`)

### MarketSessionInfo
```kotlin
data class MarketSessionInfo(
    val symbol: String,
    val isOpen: Boolean,
    val sessionName: String,
    val statusLabel: String,
    val details: String,
    val nextOpenMillis: Long? = null,
    val timeRemainingString: String? = null
)
```