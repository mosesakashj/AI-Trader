package com.example.domain.model

enum class TradingMode {
    PAPER,
    DEMO,
    LIVE
}

enum class TradeDirection {
    BUY,
    SELL
}

enum class TradeStatus {
    OPEN,
    CLOSED,
    CANCELLED,
    REJECTED
}

enum class CloseReason {
    TAKE_PROFIT,
    STOP_LOSS,
    BREAK_EVEN,
    TRAILING_STOP,
    TREND_REVERSAL,
    MANUAL,
    EMERGENCY_STOP,
    SAFE_MODE,
    EXPIRED
}

enum class StateMachineState {
    STOPPED,
    STARTING,
    CONNECTING,
    SYNCING,
    READY,
    ANALYZING,
    SIGNAL_FOUND,
    VALIDATING,
    EXECUTING,
    POSITION_OPEN,
    PAUSED,
    SAFE_MODE,
    ERROR,
    STOPPING
}

enum class ConnectionState {
    ONLINE,
    DEGRADED,
    OFFLINE,
    RECONNECTING
}

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    CRITICAL
}

enum class Timeframe(val label: String, val minutes: Int) {
    M1("1m", 1),
    M5("5m", 5),
    M15("15m", 15),
    M30("30m", 30),
    H1("1h", 60),
    H4("4h", 240),
    D1("1d", 1440)
}

enum class AssetType {
    COMMODITY,
    CRYPTO,
    FOREX,
    INDEX
}

enum class TradeMode(val displayName: String, val description: String) {
    CONSERVATIVE("Conservative", "Lower risk, wider stops, earlier break-even, more selective entries"),
    BALANCED("Balanced", "Default risk/reward profile with moderate parameters"),
    AGGRESSIVE("Aggressive", "Higher risk, tighter stops, later break-even, more trades")
}
