package com.example.data.firestore

import com.example.data.entities.BotConfigEntity
import com.example.data.entities.HeartbeatEntity
import com.example.data.entities.SignalEntity
import com.example.data.entities.SystemEventEntity
import com.example.domain.model.*

object FirestoreMapper {

    fun botConfigToMap(config: BotConfigEntity): Map<String, Any> = mapOf(
        "id" to config.id,
        "mode" to config.mode,
        "isBotEnabled" to config.isBotEnabled,
        "emergencyStop" to config.emergencyStop,
        "safeMode" to config.safeMode,
        "safeModeReason" to config.safeModeReason,
        "defaultRiskPercent" to config.defaultRiskPercent,
        "maxDailyLossPercent" to config.maxDailyLossPercent,
        "maxConsecutiveLosses" to config.maxConsecutiveLosses,
        "maxOpenPositions" to config.maxOpenPositions,
        "strategyType" to config.strategyType,
        "emaFastPeriod" to config.emaFastPeriod,
        "emaSlowPeriod" to config.emaSlowPeriod,
        "adxPeriod" to config.adxPeriod,
        "adxThreshold" to config.adxThreshold,
        "atrPeriod" to config.atrPeriod,
        "atrSlMultiplier" to config.atrSlMultiplier,
        "riskRewardRatio" to config.riskRewardRatio,
        "maxCandleExtensionAtr" to config.maxCandleExtensionAtr,
        "sessionStartHour" to config.sessionStartHour,
        "sessionEndHour" to config.sessionEndHour,
        "timezone" to config.timezone,
        "breakoutLookbackPeriod" to config.breakoutLookbackPeriod,
        "breakoutVolumeMultiplier" to config.breakoutVolumeMultiplier,
        "breakoutConfirmCandles" to config.breakoutConfirmCandles,
        "rsiPeriod" to config.rsiPeriod,
        "rsiOverbought" to config.rsiOverbought,
        "rsiOversold" to config.rsiOversold,
        "bbPeriod" to config.bbPeriod,
        "bbStdDev" to config.bbStdDev,
        "macdFastPeriod" to config.macdFastPeriod,
        "macdSlowPeriod" to config.macdSlowPeriod,
        "macdSignalPeriod" to config.macdSignalPeriod,
        "momentumAdxThreshold" to config.momentumAdxThreshold,
        "rangeLookbackPeriod" to config.rangeLookbackPeriod,
        "rangeMinTouches" to config.rangeMinTouches,
        "rangeAdxMax" to config.rangeAdxMax,
        "scalpTimeframe" to config.scalpTimeframe,
        "scalpMinRr" to config.scalpMinRr,
        "scalpMaxHoldMinutes" to config.scalpMaxHoldMinutes,
        "xauusdEnabled" to config.xauusdEnabled,
        "btcusdEnabled" to config.btcusdEnabled,
        "eurusdEnabled" to config.eurusdEnabled,
        "gbpusdEnabled" to config.gbpusdEnabled,
        "usdjpyEnabled" to config.usdjpyEnabled,
        "audusdEnabled" to config.audusdEnabled,
        "usdcadEnabled" to config.usdcadEnabled,
        "usdchfEnabled" to config.usdchfEnabled,
        "nzdusdEnabled" to config.nzdusdEnabled,
        "eurgbpEnabled" to config.eurgbpEnabled,
        "eurjpyEnabled" to config.eurjpyEnabled,
        "gbpjpyEnabled" to config.gbpjpyEnabled,
        "ethusdEnabled" to config.ethusdEnabled,
        "solusdEnabled" to config.solusdEnabled,
        "usoilEnabled" to config.usoilEnabled,
        "telegramEnabled" to config.telegramEnabled,
        "telegramChatId" to config.telegramChatId,
        "strategyVersion" to config.strategyVersion,
        "updatedAt" to config.updatedAt
    )

    fun mapToBotConfig(map: Map<String, Any>): BotConfigEntity = BotConfigEntity(
        id = map["id"] as? String ?: "primary_config",
        mode = map["mode"] as? String ?: TradingMode.PAPER.name,
        isBotEnabled = map["isBotEnabled"] as? Boolean ?: false,
        emergencyStop = map["emergencyStop"] as? Boolean ?: false,
        safeMode = map["safeMode"] as? Boolean ?: false,
        safeModeReason = map["safeModeReason"] as? String ?: "",
        defaultRiskPercent = (map["defaultRiskPercent"] as? Number)?.toDouble() ?: 0.25,
        maxDailyLossPercent = (map["maxDailyLossPercent"] as? Number)?.toDouble() ?: 1.0,
        maxConsecutiveLosses = (map["maxConsecutiveLosses"] as? Number)?.toInt() ?: 3,
        maxOpenPositions = (map["maxOpenPositions"] as? Number)?.toInt() ?: 1,
        strategyType = map["strategyType"] as? String ?: StrategyType.PULLBACK.name,
        emaFastPeriod = (map["emaFastPeriod"] as? Number)?.toInt() ?: 20,
        emaSlowPeriod = (map["emaSlowPeriod"] as? Number)?.toInt() ?: 50,
        adxPeriod = (map["adxPeriod"] as? Number)?.toInt() ?: 14,
        adxThreshold = (map["adxThreshold"] as? Number)?.toDouble() ?: 25.0,
        atrPeriod = (map["atrPeriod"] as? Number)?.toInt() ?: 14,
        atrSlMultiplier = (map["atrSlMultiplier"] as? Number)?.toDouble() ?: 1.5,
        riskRewardRatio = (map["riskRewardRatio"] as? Number)?.toDouble() ?: 2.0,
        maxCandleExtensionAtr = (map["maxCandleExtensionAtr"] as? Number)?.toDouble() ?: 2.0,
        sessionStartHour = (map["sessionStartHour"] as? Number)?.toInt() ?: 0,
        sessionEndHour = (map["sessionEndHour"] as? Number)?.toInt() ?: 24,
        timezone = map["timezone"] as? String ?: "UTC",
        breakoutLookbackPeriod = (map["breakoutLookbackPeriod"] as? Number)?.toInt() ?: 20,
        breakoutVolumeMultiplier = (map["breakoutVolumeMultiplier"] as? Number)?.toDouble() ?: 1.5,
        breakoutConfirmCandles = (map["breakoutConfirmCandles"] as? Number)?.toInt() ?: 2,
        rsiPeriod = (map["rsiPeriod"] as? Number)?.toInt() ?: 14,
        rsiOverbought = (map["rsiOverbought"] as? Number)?.toDouble() ?: 70.0,
        rsiOversold = (map["rsiOversold"] as? Number)?.toDouble() ?: 30.0,
        bbPeriod = (map["bbPeriod"] as? Number)?.toInt() ?: 20,
        bbStdDev = (map["bbStdDev"] as? Number)?.toDouble() ?: 2.0,
        macdFastPeriod = (map["macdFastPeriod"] as? Number)?.toInt() ?: 12,
        macdSlowPeriod = (map["macdSlowPeriod"] as? Number)?.toInt() ?: 26,
        macdSignalPeriod = (map["macdSignalPeriod"] as? Number)?.toInt() ?: 9,
        momentumAdxThreshold = (map["momentumAdxThreshold"] as? Number)?.toDouble() ?: 30.0,
        rangeLookbackPeriod = (map["rangeLookbackPeriod"] as? Number)?.toInt() ?: 50,
        rangeMinTouches = (map["rangeMinTouches"] as? Number)?.toInt() ?: 2,
        rangeAdxMax = (map["rangeAdxMax"] as? Number)?.toDouble() ?: 20.0,
        scalpTimeframe = map["scalpTimeframe"] as? String ?: Timeframe.M5.name,
        scalpMinRr = (map["scalpMinRr"] as? Number)?.toDouble() ?: 1.5,
        scalpMaxHoldMinutes = (map["scalpMaxHoldMinutes"] as? Number)?.toInt() ?: 30,
        xauusdEnabled = map["xauusdEnabled"] as? Boolean ?: true,
        btcusdEnabled = map["btcusdEnabled"] as? Boolean ?: true,
        eurusdEnabled = map["eurusdEnabled"] as? Boolean ?: false,
        gbpusdEnabled = map["gbpusdEnabled"] as? Boolean ?: false,
        usdjpyEnabled = map["usdjpyEnabled"] as? Boolean ?: false,
        audusdEnabled = map["audusdEnabled"] as? Boolean ?: false,
        usdcadEnabled = map["usdcadEnabled"] as? Boolean ?: false,
        usdchfEnabled = map["usdchfEnabled"] as? Boolean ?: false,
        nzdusdEnabled = map["nzdusdEnabled"] as? Boolean ?: false,
        eurgbpEnabled = map["eurgbpEnabled"] as? Boolean ?: false,
        eurjpyEnabled = map["eurjpyEnabled"] as? Boolean ?: false,
        gbpjpyEnabled = map["gbpjpyEnabled"] as? Boolean ?: false,
        ethusdEnabled = map["ethusdEnabled"] as? Boolean ?: false,
        solusdEnabled = map["solusdEnabled"] as? Boolean ?: false,
        usoilEnabled = map["usoilEnabled"] as? Boolean ?: false,
        telegramEnabled = map["telegramEnabled"] as? Boolean ?: false,
        telegramChatId = map["telegramChatId"] as? String ?: "",
        strategyVersion = map["strategyVersion"] as? String ?: "2.0.0",
        updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )

    fun tradeToMap(trade: Trade): Map<String, Any> = mapOf(
        "id" to trade.id,
        "brokerOrderId" to trade.brokerOrderId,
        "brokerPositionId" to trade.brokerPositionId,
        "symbol" to trade.symbol,
        "direction" to trade.direction.name,
        "volume" to trade.volume,
        "entryPrice" to trade.entryPrice,
        "stopLoss" to trade.stopLoss,
        "takeProfit" to trade.takeProfit,
        "riskAmount" to trade.riskAmount,
        "riskPercent" to trade.riskPercent,
        "rr" to trade.rr,
        "openedAt" to trade.openedAt,
        "closedAt" to trade.closedAt,
        "closePrice" to trade.closePrice,
        "profit" to trade.profit,
        "profitR" to trade.profitR,
        "status" to trade.status.name,
        "closeReason" to trade.closeReason?.name,
        "strategyVersion" to trade.strategyVersion,
        "mode" to trade.mode.name,
        "slippage" to trade.slippage
    )

    fun mapToTrade(map: Map<String, Any>): Trade = Trade(
        id = map["id"] as? String ?: "",
        brokerOrderId = map["brokerOrderId"] as? String ?: "",
        brokerPositionId = map["brokerPositionId"] as? String ?: "",
        symbol = map["symbol"] as? String ?: "",
        direction = runCatching { TradeDirection.valueOf(map["direction"] as? String ?: "BUY") }.getOrDefault(TradeDirection.BUY),
        volume = (map["volume"] as? Number)?.toDouble() ?: 0.0,
        entryPrice = (map["entryPrice"] as? Number)?.toDouble() ?: 0.0,
        stopLoss = (map["stopLoss"] as? Number)?.toDouble() ?: 0.0,
        takeProfit = (map["takeProfit"] as? Number)?.toDouble() ?: 0.0,
        riskAmount = (map["riskAmount"] as? Number)?.toDouble() ?: 0.0,
        riskPercent = (map["riskPercent"] as? Number)?.toDouble() ?: 0.0,
        rr = (map["rr"] as? Number)?.toDouble() ?: 0.0,
        openedAt = (map["openedAt"] as? Number)?.toLong() ?: 0L,
        closedAt = (map["closedAt"] as? Number)?.toLong(),
        closePrice = (map["closePrice"] as? Number)?.toDouble(),
        profit = (map["profit"] as? Number)?.toDouble() ?: 0.0,
        profitR = (map["profitR"] as? Number)?.toDouble() ?: 0.0,
        status = runCatching { TradeStatus.valueOf(map["status"] as? String ?: "OPEN") }.getOrDefault(TradeStatus.OPEN),
        closeReason = (map["closeReason"] as? String)?.let { runCatching { CloseReason.valueOf(it) }.getOrNull() },
        strategyVersion = map["strategyVersion"] as? String ?: "1.0.0",
        mode = runCatching { TradingMode.valueOf(map["mode"] as? String ?: "PAPER") }.getOrDefault(TradingMode.PAPER),
        slippage = (map["slippage"] as? Number)?.toDouble() ?: 0.0
    )

    fun positionToMap(position: Position): Map<String, Any> = mapOf(
        "id" to position.id,
        "symbol" to position.symbol,
        "direction" to position.direction.name,
        "volume" to position.volume,
        "entryPrice" to position.entryPrice,
        "currentPrice" to position.currentPrice,
        "stopLoss" to position.stopLoss,
        "takeProfit" to position.takeProfit,
        "unrealizedProfit" to position.unrealizedProfit,
        "unrealizedR" to position.unrealizedR,
        "openedAt" to position.openedAt,
        "mode" to position.mode.name
    )

    fun mapToPosition(map: Map<String, Any>): Position = Position(
        id = map["id"] as? String ?: "",
        symbol = map["symbol"] as? String ?: "",
        direction = runCatching { TradeDirection.valueOf(map["direction"] as? String ?: "BUY") }.getOrDefault(TradeDirection.BUY),
        volume = (map["volume"] as? Number)?.toDouble() ?: 0.0,
        entryPrice = (map["entryPrice"] as? Number)?.toDouble() ?: 0.0,
        currentPrice = (map["currentPrice"] as? Number)?.toDouble() ?: 0.0,
        stopLoss = (map["stopLoss"] as? Number)?.toDouble() ?: 0.0,
        takeProfit = (map["takeProfit"] as? Number)?.toDouble() ?: 0.0,
        unrealizedProfit = (map["unrealizedProfit"] as? Number)?.toDouble() ?: 0.0,
        unrealizedR = (map["unrealizedR"] as? Number)?.toDouble() ?: 0.0,
        openedAt = (map["openedAt"] as? Number)?.toLong() ?: 0L,
        mode = runCatching { TradingMode.valueOf(map["mode"] as? String ?: "PAPER") }.getOrDefault(TradingMode.PAPER)
    )

    fun signalToMap(signal: SignalEntity): Map<String, Any> = mapOf(
        "id" to signal.id,
        "symbol" to signal.symbol,
        "direction" to signal.direction,
        "price" to signal.price,
        "stopLoss" to signal.stopLoss,
        "takeProfit" to signal.takeProfit,
        "rrRatio" to signal.rrRatio,
        "candleTime" to signal.candleTime,
        "timestamp" to signal.timestamp,
        "decision" to signal.decision,
        "reason" to signal.reason,
        "emaFast" to signal.emaFast,
        "emaSlow" to signal.emaSlow,
        "adx" to signal.adx,
        "atr" to signal.atr,
        "strategyVersion" to signal.strategyVersion
    )

    fun mapToSignal(map: Map<String, Any>): SignalEntity = SignalEntity(
        id = map["id"] as? String ?: "",
        symbol = map["symbol"] as? String ?: "",
        direction = map["direction"] as? String ?: "BUY",
        price = (map["price"] as? Number)?.toDouble() ?: 0.0,
        stopLoss = (map["stopLoss"] as? Number)?.toDouble() ?: 0.0,
        takeProfit = (map["takeProfit"] as? Number)?.toDouble() ?: 0.0,
        rrRatio = (map["rrRatio"] as? Number)?.toDouble() ?: 0.0,
        candleTime = (map["candleTime"] as? Number)?.toLong() ?: 0L,
        timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
        decision = map["decision"] as? String ?: "",
        reason = map["reason"] as? String ?: "",
        emaFast = (map["emaFast"] as? Number)?.toDouble() ?: 0.0,
        emaSlow = (map["emaSlow"] as? Number)?.toDouble() ?: 0.0,
        adx = (map["adx"] as? Number)?.toDouble() ?: 0.0,
        atr = (map["atr"] as? Number)?.toDouble() ?: 0.0,
        strategyVersion = map["strategyVersion"] as? String ?: "1.0.0"
    )

    fun systemEventToMap(event: SystemEventEntity): Map<String, Any> = mapOf(
        "id" to event.id,
        "timestamp" to event.timestamp,
        "level" to event.level,
        "component" to event.component,
        "event" to event.event,
        "correlationId" to event.correlationId,
        "symbol" to (event.symbol ?: ""),
        "message" to event.message
    )

    fun mapToSystemEvent(map: Map<String, Any>): SystemEventEntity = SystemEventEntity(
        id = (map["id"] as? Number)?.toLong() ?: 0L,
        timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
        level = map["level"] as? String ?: "INFO",
        component = map["component"] as? String ?: "",
        event = map["event"] as? String ?: "",
        correlationId = map["correlationId"] as? String ?: "",
        symbol = map["symbol"] as? String,
        message = map["message"] as? String ?: ""
    )

    fun heartbeatToMap(heartbeat: HeartbeatEntity): Map<String, Any> = mapOf(
        "component" to heartbeat.component,
        "timestamp" to heartbeat.timestamp,
        "status" to heartbeat.status,
        "details" to heartbeat.details
    )

    fun mapToHeartbeat(map: Map<String, Any>): HeartbeatEntity = HeartbeatEntity(
        component = map["component"] as? String ?: "",
        timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
        status = map["status"] as? String ?: "UNKNOWN",
        details = map["details"] as? String ?: ""
    )
}
