package com.example.domain.model

object SymbolCatalog {

    val ALL_SYMBOLS = listOf(
        SymbolConfig(
            symbol = "XAUUSD",
            displayName = "Gold (Spot)",
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
            spreadLimit = 0.60,
            minimumAtr = 0.5,
            maximumAtr = 25.0,
            enabled = true
        ),
        SymbolConfig(
            symbol = "BTCUSD",
            displayName = "Bitcoin (Spot)",
            brokerSymbol = "BTCUSD",
            assetType = AssetType.CRYPTO,
            digits = 2,
            contractSize = 1.0,
            minLot = 0.01,
            maxLot = 5.0,
            lotStep = 0.01,
            tickSize = 0.10,
            tickValue = 0.10,
            minimumStopDistance = 25.0,
            spreadLimit = 15.0,
            minimumAtr = 20.0,
            maximumAtr = 3000.0,
            enabled = true
        ),
        SymbolConfig(
            symbol = "ETHUSD",
            displayName = "Ethereum (Spot)",
            brokerSymbol = "ETHUSD",
            assetType = AssetType.CRYPTO,
            digits = 2,
            contractSize = 1.0,
            minLot = 0.01,
            maxLot = 20.0,
            lotStep = 0.01,
            tickSize = 0.05,
            tickValue = 0.05,
            minimumStopDistance = 2.0,
            spreadLimit = 1.50,
            minimumAtr = 1.5,
            maximumAtr = 250.0,
            enabled = true
        ),
        SymbolConfig(
            symbol = "SOLUSD",
            displayName = "Solana (Spot)",
            brokerSymbol = "SOLUSD",
            assetType = AssetType.CRYPTO,
            digits = 2,
            contractSize = 1.0,
            minLot = 0.1,
            maxLot = 100.0,
            lotStep = 0.1,
            tickSize = 0.01,
            tickValue = 0.01,
            minimumStopDistance = 0.40,
            spreadLimit = 0.30,
            minimumAtr = 0.2,
            maximumAtr = 20.0,
            enabled = true
        ),
        SymbolConfig(
            symbol = "EURUSD",
            displayName = "EUR / USD",
            brokerSymbol = "EURUSD",
            assetType = AssetType.FOREX,
            digits = 5,
            contractSize = 100000.0,
            minLot = 0.01,
            maxLot = 50.0,
            lotStep = 0.01,
            tickSize = 0.00001,
            tickValue = 1.0,
            minimumStopDistance = 0.00050,
            spreadLimit = 0.00025,
            minimumAtr = 0.0003,
            maximumAtr = 0.0050,
            enabled = true
        ),
        SymbolConfig(
            symbol = "GBPUSD",
            displayName = "GBP / USD",
            brokerSymbol = "GBPUSD",
            assetType = AssetType.FOREX,
            digits = 5,
            contractSize = 100000.0,
            minLot = 0.01,
            maxLot = 50.0,
            lotStep = 0.01,
            tickSize = 0.00001,
            tickValue = 1.0,
            minimumStopDistance = 0.00060,
            spreadLimit = 0.00030,
            minimumAtr = 0.0004,
            maximumAtr = 0.0060,
            enabled = true
        ),
        SymbolConfig(
            symbol = "USDJPY",
            displayName = "USD / JPY",
            brokerSymbol = "USDJPY",
            assetType = AssetType.FOREX,
            digits = 3,
            contractSize = 100000.0,
            minLot = 0.01,
            maxLot = 50.0,
            lotStep = 0.01,
            tickSize = 0.001,
            tickValue = 0.65,
            minimumStopDistance = 0.050,
            spreadLimit = 0.030,
            minimumAtr = 0.04,
            maximumAtr = 0.90,
            enabled = true
        ),
        SymbolConfig(
            symbol = "USOIL",
            displayName = "Crude Oil (WTI)",
            brokerSymbol = "USOIL",
            assetType = AssetType.COMMODITY,
            digits = 2,
            contractSize = 1000.0,
            minLot = 0.01,
            maxLot = 20.0,
            lotStep = 0.01,
            tickSize = 0.01,
            tickValue = 10.0,
            minimumStopDistance = 0.20,
            spreadLimit = 0.08,
            minimumAtr = 0.15,
            maximumAtr = 4.0,
            enabled = true
        ),
        SymbolConfig(
            symbol = "AUDUSD",
            displayName = "AUD / USD",
            brokerSymbol = "AUDUSD",
            assetType = AssetType.FOREX,
            digits = 5,
            contractSize = 100000.0,
            minLot = 0.01,
            maxLot = 50.0,
            lotStep = 0.01,
            tickSize = 0.00001,
            tickValue = 1.0,
            minimumStopDistance = 0.00050,
            spreadLimit = 0.00025,
            minimumAtr = 0.0003,
            maximumAtr = 0.0050,
            enabled = true
        ),
        SymbolConfig(
            symbol = "USDCAD",
            displayName = "USD / CAD",
            brokerSymbol = "USDCAD",
            assetType = AssetType.FOREX,
            digits = 5,
            contractSize = 100000.0,
            minLot = 0.01,
            maxLot = 50.0,
            lotStep = 0.01,
            tickSize = 0.00001,
            tickValue = 1.0,
            minimumStopDistance = 0.00050,
            spreadLimit = 0.00030,
            minimumAtr = 0.0003,
            maximumAtr = 0.0050,
            enabled = true
        ),
        SymbolConfig(
            symbol = "USDCHF",
            displayName = "USD / CHF",
            brokerSymbol = "USDCHF",
            assetType = AssetType.FOREX,
            digits = 5,
            contractSize = 100000.0,
            minLot = 0.01,
            maxLot = 50.0,
            lotStep = 0.01,
            tickSize = 0.00001,
            tickValue = 1.0,
            minimumStopDistance = 0.00050,
            spreadLimit = 0.00030,
            minimumAtr = 0.0003,
            maximumAtr = 0.0050,
            enabled = true
        ),
        SymbolConfig(
            symbol = "NZDUSD",
            displayName = "NZD / USD",
            brokerSymbol = "NZDUSD",
            assetType = AssetType.FOREX,
            digits = 5,
            contractSize = 100000.0,
            minLot = 0.01,
            maxLot = 50.0,
            lotStep = 0.01,
            tickSize = 0.00001,
            tickValue = 1.0,
            minimumStopDistance = 0.00060,
            spreadLimit = 0.00035,
            minimumAtr = 0.0004,
            maximumAtr = 0.0060,
            enabled = true
        ),
        SymbolConfig(
            symbol = "EURGBP",
            displayName = "EUR / GBP",
            brokerSymbol = "EURGBP",
            assetType = AssetType.FOREX,
            digits = 5,
            contractSize = 100000.0,
            minLot = 0.01,
            maxLot = 50.0,
            lotStep = 0.01,
            tickSize = 0.00001,
            tickValue = 1.0,
            minimumStopDistance = 0.00040,
            spreadLimit = 0.00025,
            minimumAtr = 0.0002,
            maximumAtr = 0.0040,
            enabled = true
        ),
        SymbolConfig(
            symbol = "EURJPY",
            displayName = "EUR / JPY",
            brokerSymbol = "EURJPY",
            assetType = AssetType.FOREX,
            digits = 3,
            contractSize = 100000.0,
            minLot = 0.01,
            maxLot = 50.0,
            lotStep = 0.01,
            tickSize = 0.001,
            tickValue = 0.65,
            minimumStopDistance = 0.050,
            spreadLimit = 0.040,
            minimumAtr = 0.04,
            maximumAtr = 1.0,
            enabled = true
        ),
        SymbolConfig(
            symbol = "GBPJPY",
            displayName = "GBP / JPY",
            brokerSymbol = "GBPJPY",
            assetType = AssetType.FOREX,
            digits = 3,
            contractSize = 100000.0,
            minLot = 0.01,
            maxLot = 50.0,
            lotStep = 0.01,
            tickSize = 0.001,
            tickValue = 0.65,
            minimumStopDistance = 0.060,
            spreadLimit = 0.050,
            minimumAtr = 0.05,
            maximumAtr = 1.5,
            enabled = true
        )
    )

    private val map = ALL_SYMBOLS.associateBy { it.symbol }

    fun get(symbol: String): SymbolConfig {
        return map[symbol] ?: ALL_SYMBOLS.first()
    }

    fun getByAssetType(type: AssetType): List<SymbolConfig> {
        return ALL_SYMBOLS.filter { it.assetType == type }
    }

    fun getInitialQuote(symbol: String): Quote {
        return when (symbol) {
            "XAUUSD" -> Quote("XAUUSD", 2658.20, 2658.60, System.currentTimeMillis())
            "BTCUSD" -> Quote("BTCUSD", 91450.00, 91455.00, System.currentTimeMillis())
            "ETHUSD" -> Quote("ETHUSD", 3120.20, 3120.80, System.currentTimeMillis())
            "SOLUSD" -> Quote("SOLUSD", 188.35, 188.45, System.currentTimeMillis())
            "EURUSD" -> Quote("EURUSD", 1.08450, 1.08462, System.currentTimeMillis())
            "GBPUSD" -> Quote("GBPUSD", 1.29620, 1.29635, System.currentTimeMillis())
            "USDJPY" -> Quote("USDJPY", 154.320, 154.335, System.currentTimeMillis())
            "USOIL" -> Quote("USOIL", 71.82, 71.86, System.currentTimeMillis())
            "AUDUSD" -> Quote("AUDUSD", 0.65420, 0.65432, System.currentTimeMillis())
            "USDCAD" -> Quote("USDCAD", 1.36520, 1.36535, System.currentTimeMillis())
            "USDCHF" -> Quote("USDCHF", 0.88520, 0.88535, System.currentTimeMillis())
            "NZDUSD" -> Quote("NZDUSD", 0.60420, 0.60435, System.currentTimeMillis())
            "EURGBP" -> Quote("EURGBP", 0.83650, 0.83662, System.currentTimeMillis())
            "EURJPY" -> Quote("EURJPY", 162.320, 162.345, System.currentTimeMillis())
            "GBPJPY" -> Quote("GBPJPY", 190.820, 190.855, System.currentTimeMillis())
            else -> Quote(symbol, 100.0, 100.1, System.currentTimeMillis())
        }
    }

    fun getMeta(symbol: String): Triple<Double, Double, Double> {
        val config = get(symbol)
        return Triple(config.tickSize, config.tickValue, config.contractSize)
    }

    fun formatPrice(symbol: String, price: Double): String {
        val config = get(symbol)
        return "%.${config.digits}f".format(price)
    }
}
