package com.example.broker

import java.util.Calendar
import java.util.TimeZone

data class MarketSessionInfo(
    val symbol: String,
    val isOpen: Boolean,
    val sessionName: String,
    val statusLabel: String,
    val details: String,
    val nextOpenMillis: Long? = null,
    val timeRemainingString: String? = null
)

object MarketScheduleUtils {

    /**
     * Determines whether the market for a given symbol is currently open for trading.
     * 
     * Forex & Metals (XAUUSD, EURUSD, GBPUSD, USDJPY, AUDUSD, USDCAD, USDCHF, NZDUSD, EURGBP, EURJPY, GBPJPY):
     * - Opens Sunday 22:00 UTC (5:00 PM EST)
     * - Closes Friday 22:00 UTC (5:00 PM EST)
     * - Weekends (Saturday all day, Sunday until 22:00 UTC): CLOSED
     * 
     * Crypto (BTCUSD, ETHUSD, SOLUSD):
     * - Open 24/7/365
     */
    fun getMarketSession(symbol: String, currentTimeMillis: Long = System.currentTimeMillis()): MarketSessionInfo {
        val isCrypto = symbol in listOf("BTCUSD", "ETHUSD", "SOLUSD")
        if (isCrypto) {
            return MarketSessionInfo(
                symbol = symbol,
                isOpen = true,
                sessionName = "Crypto 24/7",
                statusLabel = "LIVE 24/7",
                details = "Continuous real-time exchange orderbook streaming"
            )
        }

        // For XAUUSD and traditional Forex/Metals:
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = currentTimeMillis
        }

        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // Calendar.SUNDAY = 1, SATURDAY = 7
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        val isSaturday = (dayOfWeek == Calendar.SATURDAY)
        val isSundayBeforeOpen = (dayOfWeek == Calendar.SUNDAY && hour < 22)
        val isFridayAfterClose = (dayOfWeek == Calendar.FRIDAY && hour >= 22)

        val isWeekendClosed = isSaturday || isSundayBeforeOpen || isFridayAfterClose

        if (isWeekendClosed) {
            // Calculate next open time (Sunday 22:00 UTC)
            val nextOpenCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = currentTimeMillis
                set(Calendar.HOUR_OF_DAY, 22)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                while (get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY || timeInMillis <= currentTimeMillis) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val diffMillis = nextOpenCal.timeInMillis - currentTimeMillis
            val diffHours = diffMillis / (1000 * 60 * 60)
            val diffMins = (diffMillis / (1000 * 60)) % 60
            val remainingStr = "${diffHours}h ${diffMins}m until open"

            return MarketSessionInfo(
                symbol = symbol,
                isOpen = false,
                sessionName = "Weekend Market Close",
                statusLabel = "MARKET CLOSED",
                details = "Forex/Metals markets are closed on weekends. Showing real Friday closing spot price. Reopens Sunday 22:00 UTC.",
                nextOpenMillis = nextOpenCal.timeInMillis,
                timeRemainingString = remainingStr
            )
        }

        // Active weekday session detection
        val session = when (hour) {
            in 0..7 -> "Asian / Tokyo Session"
            in 8..12 -> "London / European Session"
            in 13..17 -> "London & New York Overlap"
            in 18..21 -> "New York Afternoon Session"
            else -> "Global Forex Session"
        }

        return MarketSessionInfo(
            symbol = symbol,
            isOpen = true,
            sessionName = session,
            statusLabel = "MARKET OPEN",
            details = "Active institutional trading session ($session). Real-time tick updates live."
        )
    }

    fun isMarketOpen(symbol: String, currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        return getMarketSession(symbol, currentTimeMillis).isOpen
    }
}
