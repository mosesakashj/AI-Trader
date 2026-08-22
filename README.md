# EdgeTrader

Pure on-device algorithmic trading engine for Android. Runs an autonomous rule-based trading bot that monitors live price feeds, evaluates technical indicator strategies, and automatically places, manages, and closes trades.

## Features

- **6 Strategy Types**: Pullback, Breakout, Mean Reversion, Momentum, Range Trading, Scalping
- **14-State FSM**: Robust state machine with auto-recovery and safe mode
- **Multi-Asset**: Gold (XAUUSD), Bitcoin, Ethereum, Solana, and 9 Forex pairs
- **4 AI Providers**: NVIDIA Nemotron, Google Gemini, Anthropic Claude, OpenAI GPT-4o
- **Paper/Demo/Live Trading**: Full simulation with realistic slippage, or live Exness MT5 via REST gateway
- **Risk Management**: Equity-based position sizing, daily loss limits, break-even automation, trailing stops
- **Backtesting**: Walk-forward validation, parameter optimization, Monte Carlo simulation
- **Notifications**: Android push + Telegram bot alerts
- **Security**: AES-256-GCM encryption via Android KeyStore

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin (JVM 11) |
| Platform | Android (minSdk 24, targetSdk 36) |
| UI | Jetpack Compose + Material3 |
| Database | Room 2.7.0 |
| DI | Hilt 2.51.1 |
| Networking | OkHttp 4.10 + Retrofit 2.12 + Moshi 1.15 |
| Build | Gradle 9.3.1 + KSP |

## Setup

### Prerequisites

- Android Studio Ladybug (2024.2+) or later
- JDK 17
- Android SDK 36

### Build

```bash
# Clone the repository
git clone https://github.com/your-org/AI-Trader.git
cd AI-Trader

# Copy environment file
cp .env.example .env

# Add your Gemini API key to .env (optional)
echo "GEMINI_API_KEY=your_key_here" > .env

# Build debug APK
./gradlew assembleDebug
```

### Secrets

API keys are stored securely via the Secrets Gradle Plugin. Copy `.env.example` to `.env` and add your keys:

```
GEMINI_API_KEY=your_gemini_key
```

Broker credentials and Telegram tokens are encrypted with AES-256-GCM via Android KeyStore at runtime.

## Architecture

```
com.example/
├── ai/                  # LLM provider integrations
├── broker/              # Market data & broker adapters (Paper/Demo/Live)
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

See `.opencode/knowledge/ARCHITECTURE.md` for detailed component documentation.

## Testing

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Run lint
./gradlew lintDebug
```

## CI/CD

GitHub Actions pipeline runs on push to `main`/`develop` and on PRs:
- Build debug APK
- Run unit tests
- Run lint checks
- Upload artifacts

See `.github/workflows/ci.yml`.

## License

Private - All rights reserved.
