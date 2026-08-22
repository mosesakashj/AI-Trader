# AI-Trader Architecture Overview

## Project Structure
```
app/src/main/java/com/example/
├── ai/                  # LLM provider integrations
├── broker/              # Market data & broker adapters
├── data/                # Room database, DAOs, entities, repositories
├── di/                  # Hilt dependency injection modules
├── domain/              # Strategy logic, indicators, risk management, backtesting
├── notifications/       # Android + Telegram notification system
├── security/            # AES-256-GCM encrypted key storage
├── service/             # Foreground service, boot receiver
├── trading/             # Core engine, state machine, position reconciler
├── ui/                  # Jetpack Compose screens
└── watchdog/            # Auto-recovery watchdog
```

## Core Components

### 1. Trading Engine (`TradingEngine.kt`)
- Main orchestration class managing the trading lifecycle
- State machine with states: STOPPED, STARTING, CONNECTING, SYNCING, READY, ANALYZING, SIGNAL_FOUND, VALIDATING, EXECUTING, POSITION_OPEN, PAUSED, SAFE_MODE, ERROR, STOPPING
- Handles quote processing, position management, strategy evaluation
- Integrates with broker adapters via factory pattern

### 2. Strategy (`TradingStrategy.kt`)
- Conservative trend-pullback strategy
- Uses EMA(20/50), ADX(14), ATR(14) indicators
- Entry criteria: Trend alignment + ADX > 25 + Pullback to EMA band + Candle confirmation + Spread check + Risk check + Session check
- Risk:Reward = 1:2 default
- ATR-based stop loss with minimum distance enforcement

### 3. Risk Manager (`RiskManager.kt`)
- Position sizing based on account equity and risk %
- Daily loss limit enforcement
- Consecutive loss limit
- Max open positions limit
- Spread and slippage limits

### 4. Market Data
- `PaperMarketDataProvider`: Simulated tick data for XAUUSD and BTCUSD
- `RealTimeMarketDataProvider`: Live data via gateway
- Market schedule handling (Gold weekend close, BTC 24/7)

### 5. Broker Adapters
- Paper: Simulated execution
- Demo: Demo account simulation
- Live: Real Exness MT5 via gateway bridge

### 6. Data Layer
- Room database with entities: BotConfig, Trade, Position, Signal, Candle, SystemEvent, Heartbeat
- Repository pattern with Flow-based reactive streams

### 7. UI Screens (Jetpack Compose)
- Dashboard: Overview with account, positions, signals
- Markets: Live quotes and session info
- Strategy: Signal verification matrix + parameter tuner
- Positions: Open positions with P&L
- Backtest: Standard + Walk-forward validation
- Settings: Mode, broker, telegram, battery optimization
- History, Health, Logs, Risk, Security

## Dependency Injection (Hilt)

The app uses Hilt for dependency injection. Key modules:

- `DatabaseModule` - Provides Room database and DAOs
- `AppModule` - Provides SecureStorage, Repository, NotificationManager, TradingEngine, AiManager

Entry points:
- `@HiltAndroidApp` on `EdgeTraderApp` (Application class)
- `@AndroidEntryPoint` on `MainActivity`, `TradingForegroundService`, `BootReceiver`
- `@HiltViewModel` on `DashboardViewModel`

The `EdgeTraderApp.instance` singleton is maintained for backward compatibility with screens that haven't been migrated to constructor-injected ViewModels yet.

## Current Symbol Support
- XAUUSD (Gold Spot) - Commodity
- BTCUSD (Bitcoin Spot) - Crypto

## Key Configuration (BotConfigEntity)
- Trading mode (PAPER/DEMO/LIVE)
- Strategy parameters (EMA, ADX, ATR, RR)
- Risk parameters (daily loss, consecutive losses, max positions)
- Symbol enable flags (xauusdEnabled, btcusdEnabled)
- Telegram settings