package com.example.domain.strategy

interface NewsFilter {
    fun isTradingAllowed(symbol: String, timestamp: Long): Boolean
}

class NoNewsFilter : NewsFilter {
    override fun isTradingAllowed(symbol: String, timestamp: Long): Boolean {
        // Extensible interface: V1 allows trading without external network news dependency
        return true
    }
}
