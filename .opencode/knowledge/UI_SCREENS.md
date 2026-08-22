# UI Screens Reference

## Navigation (`Navigation.kt`)

### Screen Definitions
```kotlin
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Markets : Screen("markets", "Markets", Icons.AutoMirrored.Filled.ShowChart)
    object Strategy : Screen("strategy", "Strategy", Icons.Default.Psychology)
    object Positions : Screen("positions", "Positions", Icons.Default.AccountBalanceWallet)
    object History : Screen("history", "History", Icons.Default.History)
    object Backtest : Screen("backtest", "Backtest", Icons.Default.Science)
    object Risk : Screen("risk", "Risk", Icons.Default.Shield)
    object Health : Screen("health", "System Health", Icons.Default.MonitorHeart)
    object Logs : Screen("logs", "Logs", Icons.Default.ReceiptLong)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Security : Screen("security", "Docs & Safety", Icons.Default.Security)
}
```

### Navigation Items
```kotlin
val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Markets,
    Screen.Positions,
    Screen.Backtest,
    Screen.Settings
)

val drawerNavItems = listOf(
    Screen.Dashboard,
    Screen.Markets,
    Screen.Strategy,
    Screen.Positions,
    Screen.History,
    Screen.Backtest,
    Screen.Risk,
    Screen.Health,
    Screen.Logs,
    Screen.Settings,
    Screen.Security
)
```

---

## Screen Files

### PositionsScreen.kt (`ui/positions/PositionsScreen.kt`)
**Current Features:**
- Summary header: Open positions count, Unrealized P/L with R-multiple
- Emergency "CLOSE ALL" button (red)
- Position cards with:
  - Direction badge (BUY/SELL)
  - Symbol, volume
  - Unrealized P/L with color coding
  - Entry price, Stop Loss, Take Profit
  - Individual "Close Position" button per position
- Empty state when no positions
- Close all confirmation dialog

**State:**
- `openPositions` from repository Flow
- `totalUnrealizedPnl`, `totalUnrealizedR` computed
- `showCloseAllDialog` for confirmation

### SettingsScreen.kt (`ui/settings/SettingsScreen.kt`)
**Current Features:**
- **Execution Environment Mode**: PAPER/DEMO/LIVE selector with Live disclaimer
- **Exness/MT5 Broker Connection**:
  - Server preset chips (Exness-MT5Real, Exness-MT5Trial, etc.)
  - Server name input
  - Account ID input
  - Password input (masked)
  - Gateway URL input
  - API Key input (masked)
  - Save & Test buttons
- **Market Data Feed & Session Engine**:
  - Gold (XAUUSD) session status
  - Bitcoin (BTCUSD) 24/7 status
  - Weekend market rule explanation
- **Telegram Alerts Setup**:
  - Bot token input (masked)
  - Chat ID input
  - Save & Test push
- **Battery Optimization Guidelines**
- **Documentation Link**

**State:**
- `selectedMode` (TradingMode)
- Broker fields: `brokerServer`, `brokerAccountId`, `brokerPassword`, `brokerGatewayUrl`, `brokerApiKey`
- Telegram: `telegramToken`, `telegramChatId`
- Various save/test result states

### BacktestScreen.kt (`ui/backtest/BacktestScreen.kt`)
**Current Features:**
- **Mode Selector**: Standard Backtest / Walk-Forward Splits tabs
- **Configuration Card**:
  - Symbol toggle: XAUUSD / BTCUSD
  - Timeframe toggle: M15 / M5
  - Candle count input
  - Risk % input
  - Run button
- **Standard Backtest Results**:
  - Equity curve sparkline
  - Metrics: Win Rate, Profit Factor, Net P/L, Max Drawdown, Avg R-Multiple, Expectancy, Max Consec. Loss
- **Walk-Forward Results**:
  - Robustness score
  - In-Sample (60%) metrics
  - Out-of-Sample (20%) metrics

**State:**
- `selectedSymbol`, `selectedTimeframe`, `candleCount`, `riskPercent`
- `testMode` (0=Standard, 1=WalkForward)
- `isRunning`, `backtestResult`, `walkForwardResult`
- Uses `PaperMarketDataProvider` and `BacktestingEngine`

### StrategyScreen.kt (`ui/strategy/StrategyScreen.kt`)
**Current Features:**
- Strategy summary card
- **Signal Verification Matrix**: 7-factor breakdown with pass/fail chips
  1. EMA Trend Filter
  2. ADX Momentum
  3. Pullback to EMA Band
  4. Closed Candle Confirmation
  5. Broker Spread Within Limit
  6. Account Risk & Position Capacity
  7. Allowed Trading Session Window
- **Parameter Tuner**:
  - Fast EMA Period
  - Slow EMA Period
  - ADX Minimum Threshold
  - ATR Stop-Loss Multiplier
  - Risk-to-Reward Ratio
  - Save button

### DashboardScreen.kt (`ui/dashboard/DashboardScreen.kt`)
**Current Features:**
- Account summary cards
- Active positions summary
- Latest signal display
- Quick actions

### MarketsScreen.kt (`ui/markets/MarketsScreen.kt`)
**Current Features:**
- Live quote cards for symbols
- Market session status
- Spread display

### HistoryScreen.kt, HealthScreen.kt, LogsScreen.kt, RiskScreen.kt
- Standard list/detail views

---

## Reusable Components (`ui/components/`)

### CommonComponents.kt
- `MetricCard` - Title, value, subtitle, color
- `FactorChip` - Label with pass/fail indicator
- `SparklineChart` - Simple line chart
- `SectionHeader` - Styled section title

### InteractiveCandleChart.kt
- Candlestick chart with touch interaction

### EmergencyDialogs.kt
- `CloseAllPositionsDialog` - Confirmation dialog
- `LiveModeDisclaimerDialog` - Live mode warning

---

## Theme (`ui/theme/`)
- `Color.kt` - Dark theme colors (BackgroundDark, SurfaceDark, PrimaryBlue, EmeraldGain, CrimsonLoss, GoldHero, CyanLight, TextPrimary/Secondary/Muted, CardBorderDark, etc.)
- `Type.kt` - Typography
- `Theme.kt` - Material3 theme setup