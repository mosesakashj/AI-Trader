package com.example.data.repository

import com.example.data.entities.BotConfigEntity
import com.example.data.local.*
import com.example.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class TradingRepository(
    private val tradeDao: TradeDao,
    private val positionDao: PositionDao,
    private val signalDao: SignalDao,
    private val systemEventDao: SystemEventDao,
    private val stateTransitionDao: StateTransitionDao,
    private val configDao: ConfigDao
) : IRepository {

    private var cachedBotConfig: BotConfigEntity? = null

    // ─── Config ───────────────────────────────────────────────────────────

    suspend fun getOrCreateConfig(): RoomConfig {
        return configDao.getConfig() ?: RoomConfig(
            id = "primary_config",
            configJson = "{}",
            updatedAt = System.currentTimeMillis()
        ).also { configDao.insertConfig(it) }
    }

    suspend fun updateConfig(config: RoomConfig) {
        configDao.updateConfig(config.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun getOrCreateBotConfig(): BotConfigEntity {
        cachedBotConfig?.let { return it }
        val roomConfig = getOrCreateConfig()
        return if (roomConfig.configJson.isNotBlank() && roomConfig.configJson != "{}") {
            parseBotConfigFromJson(roomConfig.configJson)
        } else {
            BotConfigEntity().also { cachedBotConfig = it }
        }
    }

    override suspend fun updateBotConfig(config: BotConfigEntity) {
        cachedBotConfig = config
        val json = botConfigToJson(config)
        val roomConfig = getOrCreateConfig()
        updateConfig(roomConfig.copy(configJson = json))
    }

    private fun parseBotConfigFromJson(json: String): BotConfigEntity {
        return try {
            val obj = JSONObject(json)
            BotConfigEntity(
                id = obj.optString("id", "primary_config"),
                mode = obj.optString("mode", TradingMode.PAPER.name),
                tradeMode = obj.optString("tradeMode", "BALANCED"),
                isBotEnabled = obj.optBoolean("isBotEnabled", false),
                emergencyStop = obj.optBoolean("emergencyStop", false),
                safeMode = obj.optBoolean("safeMode", false),
                safeModeReason = obj.optString("safeModeReason", ""),
                defaultRiskPercent = obj.optDouble("defaultRiskPercent", 0.25),
                maxDailyLossPercent = obj.optDouble("maxDailyLossPercent", 1.0),
                maxConsecutiveLosses = obj.optInt("maxConsecutiveLosses", 3),
                maxOpenPositions = obj.optInt("maxOpenPositions", 1),
                strategyType = obj.optString("strategyType", StrategyType.PULLBACK.name),
                emaFastPeriod = obj.optInt("emaFastPeriod", 20),
                emaSlowPeriod = obj.optInt("emaSlowPeriod", 50),
                adxPeriod = obj.optInt("adxPeriod", 14),
                adxThreshold = obj.optDouble("adxThreshold", 25.0),
                atrPeriod = obj.optInt("atrPeriod", 14),
                atrSlMultiplier = obj.optDouble("atrSlMultiplier", 1.5),
                riskRewardRatio = obj.optDouble("riskRewardRatio", 2.0),
                maxCandleExtensionAtr = obj.optDouble("maxCandleExtensionAtr", 2.0),
                sessionStartHour = obj.optInt("sessionStartHour", 0),
                sessionEndHour = obj.optInt("sessionEndHour", 24),
                timezone = obj.optString("timezone", "UTC"),
                breakoutLookbackPeriod = obj.optInt("breakoutLookbackPeriod", 20),
                breakoutVolumeMultiplier = obj.optDouble("breakoutVolumeMultiplier", 1.5),
                breakoutConfirmCandles = obj.optInt("breakoutConfirmCandles", 2),
                rsiPeriod = obj.optInt("rsiPeriod", 14),
                rsiOverbought = obj.optDouble("rsiOverbought", 70.0),
                rsiOversold = obj.optDouble("rsiOversold", 30.0),
                bbPeriod = obj.optInt("bbPeriod", 20),
                bbStdDev = obj.optDouble("bbStdDev", 2.0),
                macdFastPeriod = obj.optInt("macdFastPeriod", 12),
                macdSlowPeriod = obj.optInt("macdSlowPeriod", 26),
                macdSignalPeriod = obj.optInt("macdSignalPeriod", 9),
                momentumAdxThreshold = obj.optDouble("momentumAdxThreshold", 30.0),
                rangeLookbackPeriod = obj.optInt("rangeLookbackPeriod", 50),
                rangeMinTouches = obj.optInt("rangeMinTouches", 2),
                rangeAdxMax = obj.optDouble("rangeAdxMax", 20.0),
                scalpMinRr = obj.optDouble("scalpMinRr", 1.5),
                scalpMaxHoldMinutes = obj.optInt("scalpMaxHoldMinutes", 30),
                breakEvenEnabled = obj.optBoolean("breakEvenEnabled", true),
                breakEvenTriggerR = obj.optDouble("breakEvenTriggerR", 0.8),
                breakEvenBufferPips = obj.optDouble("breakEvenBufferPips", 1.5),
                trailingStopEnabled = obj.optBoolean("trailingStopEnabled", true),
                trailingStopTriggerR = obj.optDouble("trailingStopTriggerR", 1.2),
                trailingStopDistanceAtr = obj.optDouble("trailingStopDistanceAtr", 1.0),
                earlyExitOnTrendReversal = obj.optBoolean("earlyExitOnTrendReversal", true),
                adaptiveTpEnabled = obj.optBoolean("adaptiveTpEnabled", true),
                adaptiveSlEnabled = obj.optBoolean("adaptiveSlEnabled", true),
                adaptiveBeEnabled = obj.optBoolean("adaptiveBeEnabled", true),
                xauusdEnabled = obj.optBoolean("xauusdEnabled", true),
                btcusdEnabled = obj.optBoolean("btcusdEnabled", true),
                eurusdEnabled = obj.optBoolean("eurusdEnabled", false),
                gbpusdEnabled = obj.optBoolean("gbpusdEnabled", false),
                usdjpyEnabled = obj.optBoolean("usdjpyEnabled", false),
                audusdEnabled = obj.optBoolean("audusdEnabled", false),
                usdcadEnabled = obj.optBoolean("usdcadEnabled", false),
                usdchfEnabled = obj.optBoolean("usdchfEnabled", false),
                nzdusdEnabled = obj.optBoolean("nzdusdEnabled", false),
                eurgbpEnabled = obj.optBoolean("eurgbpEnabled", false),
                eurjpyEnabled = obj.optBoolean("eurjpyEnabled", false),
                gbpjpyEnabled = obj.optBoolean("gbpjpyEnabled", false),
                ethusdEnabled = obj.optBoolean("ethusdEnabled", false),
                solusdEnabled = obj.optBoolean("solusdEnabled", false),
                usoilEnabled = obj.optBoolean("usoilEnabled", false),
                telegramEnabled = obj.optBoolean("telegramEnabled", false),
                telegramChatId = obj.optString("telegramChatId", ""),
                strategyVersion = obj.optString("strategyVersion", "2.0.0"),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            BotConfigEntity()
        }
    }

    private fun botConfigToJson(config: BotConfigEntity): String {
        return JSONObject().apply {
            put("id", config.id)
            put("mode", config.mode)
            put("tradeMode", config.tradeMode)
            put("isBotEnabled", config.isBotEnabled)
            put("emergencyStop", config.emergencyStop)
            put("safeMode", config.safeMode)
            put("safeModeReason", config.safeModeReason)
            put("defaultRiskPercent", config.defaultRiskPercent)
            put("maxDailyLossPercent", config.maxDailyLossPercent)
            put("maxConsecutiveLosses", config.maxConsecutiveLosses)
            put("maxOpenPositions", config.maxOpenPositions)
            put("strategyType", config.strategyType)
            put("emaFastPeriod", config.emaFastPeriod)
            put("emaSlowPeriod", config.emaSlowPeriod)
            put("adxPeriod", config.adxPeriod)
            put("adxThreshold", config.adxThreshold)
            put("atrPeriod", config.atrPeriod)
            put("atrSlMultiplier", config.atrSlMultiplier)
            put("riskRewardRatio", config.riskRewardRatio)
            put("maxCandleExtensionAtr", config.maxCandleExtensionAtr)
            put("sessionStartHour", config.sessionStartHour)
            put("sessionEndHour", config.sessionEndHour)
            put("timezone", config.timezone)
            put("breakoutLookbackPeriod", config.breakoutLookbackPeriod)
            put("breakoutVolumeMultiplier", config.breakoutVolumeMultiplier)
            put("breakoutConfirmCandles", config.breakoutConfirmCandles)
            put("rsiPeriod", config.rsiPeriod)
            put("rsiOverbought", config.rsiOverbought)
            put("rsiOversold", config.rsiOversold)
            put("bbPeriod", config.bbPeriod)
            put("bbStdDev", config.bbStdDev)
            put("macdFastPeriod", config.macdFastPeriod)
            put("macdSlowPeriod", config.macdSlowPeriod)
            put("macdSignalPeriod", config.macdSignalPeriod)
            put("momentumAdxThreshold", config.momentumAdxThreshold)
            put("rangeLookbackPeriod", config.rangeLookbackPeriod)
            put("rangeMinTouches", config.rangeMinTouches)
            put("rangeAdxMax", config.rangeAdxMax)
            put("scalpMinRr", config.scalpMinRr)
            put("scalpMaxHoldMinutes", config.scalpMaxHoldMinutes)
            put("breakEvenEnabled", config.breakEvenEnabled)
            put("breakEvenTriggerR", config.breakEvenTriggerR)
            put("breakEvenBufferPips", config.breakEvenBufferPips)
            put("trailingStopEnabled", config.trailingStopEnabled)
            put("trailingStopTriggerR", config.trailingStopTriggerR)
            put("trailingStopDistanceAtr", config.trailingStopDistanceAtr)
            put("earlyExitOnTrendReversal", config.earlyExitOnTrendReversal)
            put("adaptiveTpEnabled", config.adaptiveTpEnabled)
            put("adaptiveSlEnabled", config.adaptiveSlEnabled)
            put("adaptiveBeEnabled", config.adaptiveBeEnabled)
            put("xauusdEnabled", config.xauusdEnabled)
            put("btcusdEnabled", config.btcusdEnabled)
            put("eurusdEnabled", config.eurusdEnabled)
            put("gbpusdEnabled", config.gbpusdEnabled)
            put("usdjpyEnabled", config.usdjpyEnabled)
            put("audusdEnabled", config.audusdEnabled)
            put("usdcadEnabled", config.usdcadEnabled)
            put("usdchfEnabled", config.usdchfEnabled)
            put("nzdusdEnabled", config.nzdusdEnabled)
            put("eurgbpEnabled", config.eurgbpEnabled)
            put("eurjpyEnabled", config.eurjpyEnabled)
            put("gbpjpyEnabled", config.gbpjpyEnabled)
            put("ethusdEnabled", config.ethusdEnabled)
            put("solusdEnabled", config.solusdEnabled)
            put("usoilEnabled", config.usoilEnabled)
            put("telegramEnabled", config.telegramEnabled)
            put("telegramChatId", config.telegramChatId)
            put("strategyVersion", config.strategyVersion)
            put("updatedAt", config.updatedAt)
        }.toString()
    }

    // ─── Trades ───────────────────────────────────────────────────────────

    override val allTradesFlow: Flow<List<RoomTrade>> = tradeDao.getAllTradesFlow()

    override suspend fun getAllTrades(): List<RoomTrade> = tradeDao.getAllTrades()

    override suspend fun recordTrade(trade: RoomTrade) = tradeDao.insertTrade(trade)

    override suspend fun updateTrade(trade: RoomTrade) = tradeDao.updateTrade(trade)

    override suspend fun getTradeById(id: String): RoomTrade? = tradeDao.getTradeById(id)

    override suspend fun getTradesCountForDay(dayStart: Long, dayEnd: Long): Int =
        tradeDao.getTradesCountForDay(dayStart, dayEnd)

    // ─── Positions ────────────────────────────────────────────────────────

    override val openPositionsFlow: Flow<List<RoomPosition>> = positionDao.getOpenPositionsFlow()

    override suspend fun getOpenPositions(): List<RoomPosition> = positionDao.getOpenPositions()

    override suspend fun recordPosition(position: RoomPosition) = positionDao.insertPosition(position)

    override suspend fun updatePosition(position: RoomPosition) = positionDao.updatePosition(position)

    override suspend fun removePosition(id: String) = positionDao.deletePosition(id)

    override suspend fun getPositionsBySymbol(symbol: String): List<RoomPosition> =
        positionDao.getPositionsBySymbol(symbol)

    // ─── Signals ──────────────────────────────────────────────────────────

    override val recentSignalsFlow: Flow<List<RoomSignal>> = signalDao.getRecentSignalsFlow()

    override suspend fun recordSignal(signal: RoomSignal) = signalDao.insertSignal(signal)

    // ─── System Events ────────────────────────────────────────────────────

    override val systemLogsFlow: Flow<List<RoomSystemEvent>> = systemEventDao.getSystemLogsFlow()

    override suspend fun logEvent(
        level: LogLevel,
        component: String,
        event: String,
        message: String,
        symbol: String?,
        correlationId: String
    ) {
        val entity = RoomSystemEvent(
            timestamp = System.currentTimeMillis(),
            level = level.name,
            component = component,
            event = event,
            correlationId = correlationId,
            symbol = symbol,
            message = sanitizeLog(message)
        )
        systemEventDao.insertEvent(entity)
        systemEventDao.trimOldEvents()
    }

    // ─── State Transitions ────────────────────────────────────────────────

    override suspend fun recordStateTransition(
        fromState: StateMachineState,
        toState: StateMachineState,
        reason: String,
        durationMs: Long
    ) {
        stateTransitionDao.insertTransition(
            RoomStateTransition(
                timestamp = System.currentTimeMillis(),
                fromState = fromState.name,
                toState = toState.name,
                reason = reason,
                durationMs = durationMs
            )
        )
    }

    override suspend fun getStateTransitions(limit: Int): List<RoomStateTransition> =
        stateTransitionDao.getTransitions(limit)

    override fun getStateTransitionsFlow(limit: Int): Flow<List<RoomStateTransition>> =
        stateTransitionDao.getTransitionsFlow(limit)

    // ─── Helpers ──────────────────────────────────────────────────────────

    private fun sanitizeLog(msg: String): String {
        return msg.replace(Regex("token=[^&\\s]+", RegexOption.IGNORE_CASE), "token=[REDACTED]")
            .replace(Regex("password=[^&\\s]+", RegexOption.IGNORE_CASE), "password=[REDACTED]")
            .replace(Regex("key=[^&\\s]+", RegexOption.IGNORE_CASE), "key=[REDACTED]")
    }

    override suspend fun clearHistory() {
        tradeDao.deleteAllTrades()
        positionDao.deleteAllPositions()
        systemEventDao.deleteAllEvents()
    }
}
