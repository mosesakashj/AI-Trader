package com.example.ui.strategy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.EdgeTraderApp
import com.example.domain.model.StrategyType
import com.example.ui.components.FactorChip
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun StrategyScreen() {
    val repository = EdgeTraderApp.instance.firestoreRepository
    val engine = EdgeTraderApp.instance.tradingEngine
    val config by repository.configFlow.collectAsState(initial = null)
    val latestSignal by engine.latestSignal.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var strategyType by remember(config) {
        mutableStateOf(
            try {
                StrategyType.valueOf(config?.strategyType ?: StrategyType.PULLBACK.name)
            } catch (_: Exception) {
                StrategyType.PULLBACK
            }
        )
    }

    // PULLBACK parameters
    var fastEma by remember(config) { mutableStateOf(config?.emaFastPeriod?.toString() ?: "20") }
    var slowEma by remember(config) { mutableStateOf(config?.emaSlowPeriod?.toString() ?: "50") }

    // BREAKOUT parameters
    var breakoutLookback by remember(config) { mutableStateOf(config?.breakoutLookbackPeriod?.toString() ?: "20") }
    var breakoutVolumeMult by remember(config) { mutableStateOf(config?.breakoutVolumeMultiplier?.toString() ?: "1.5") }
    var breakoutConfirmCandles by remember(config) { mutableStateOf(config?.breakoutConfirmCandles?.toString() ?: "2") }

    // MEAN REVERSION parameters
    var rsiPeriod by remember(config) { mutableStateOf(config?.rsiPeriod?.toString() ?: "14") }
    var rsiOverbought by remember(config) { mutableStateOf(config?.rsiOverbought?.toString() ?: "70.0") }
    var rsiOversold by remember(config) { mutableStateOf(config?.rsiOversold?.toString() ?: "30.0") }
    var bbPeriod by remember(config) { mutableStateOf(config?.bbPeriod?.toString() ?: "20") }
    var bbStdDev by remember(config) { mutableStateOf(config?.bbStdDev?.toString() ?: "2.0") }

    // MOMENTUM parameters
    var macdFast by remember(config) { mutableStateOf(config?.macdFastPeriod?.toString() ?: "12") }
    var macdSlow by remember(config) { mutableStateOf(config?.macdSlowPeriod?.toString() ?: "26") }
    var macdSignal by remember(config) { mutableStateOf(config?.macdSignalPeriod?.toString() ?: "9") }
    var momentumAdxThreshold by remember(config) { mutableStateOf(config?.momentumAdxThreshold?.toString() ?: "30.0") }

    // RANGE TRADING parameters
    var rangeLookback by remember(config) { mutableStateOf(config?.rangeLookbackPeriod?.toString() ?: "50") }
    var rangeMinTouches by remember(config) { mutableStateOf(config?.rangeMinTouches?.toString() ?: "2") }
    var rangeAdxMax by remember(config) { mutableStateOf(config?.rangeAdxMax?.toString() ?: "20.0") }

    // SCALPING parameters
    var scalpMinRr by remember(config) { mutableStateOf(config?.scalpMinRr?.toString() ?: "1.5") }
    var scalpMaxHold by remember(config) { mutableStateOf(config?.scalpMaxHoldMinutes?.toString() ?: "30") }

    // Shared parameters
    var adxMin by remember(config) { mutableStateOf(config?.adxThreshold?.toString() ?: "25.0") }
    var atrSlMultiplier by remember(config) { mutableStateOf(config?.atrSlMultiplier?.toString() ?: "1.5") }
    var rrRatio by remember(config) { mutableStateOf(config?.riskRewardRatio?.toString() ?: "2.0") }

    var saveStatus by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Strategy Architecture Summary with type selector
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth().testTag("strategy_summary_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            strategyType.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = CyanLight
                        )
                        Surface(color = SurfaceVariantDark, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                "v${config?.strategyVersion ?: "1.0.0"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        strategyType.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StrategyType.entries.forEach { type ->
                            FilterChip(
                                selected = strategyType == type,
                                onClick = {
                                    strategyType = type
                                    coroutineScope.launch {
                                        val current = repository.getOrCreateConfig()
                                        repository.updateConfig(current.copy(strategyType = type.name))
                                    }
                                },
                                label = { Text(type.displayName, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyanLight.copy(alpha = 0.15f),
                                    selectedLabelColor = CyanLight,
                                    containerColor = SurfaceVariantDark,
                                    labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = CardBorderDark,
                                    selectedBorderColor = CyanLight,
                                    enabled = true,
                                    selected = strategyType == type
                                )
                            )
                        }
                    }
                }
            }
        }

        // Live Signal Explainability Breakdown
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Signal Verification Matrix", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)

                    val exp = latestSignal?.explanation
                    if (exp != null) {
                        Text("Decision: ${exp.decision} on ${exp.symbol} (${exp.direction})", fontWeight = FontWeight.Bold, color = if (exp.isAllPassed) EmeraldGain else GoldHero)
                        Text("Reason: ${exp.reason}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                        Divider(color = CardBorderDark, modifier = Modifier.padding(vertical = 4.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FactorChip("1. EMA Trend Filter (Fast > Slow)", exp.trendCheck, Modifier.fillMaxWidth())
                            FactorChip("2. ADX Momentum (>= ${config?.adxThreshold})", exp.adxCheck, Modifier.fillMaxWidth())
                            FactorChip("3. Pullback to EMA Band", exp.pullbackCheck, Modifier.fillMaxWidth())
                            FactorChip("4. Closed Candle Confirmation", exp.candleCheck, Modifier.fillMaxWidth())
                            FactorChip("5. Broker Spread Within Limit", exp.spreadCheck, Modifier.fillMaxWidth())
                            FactorChip("6. Account Risk & Position Capacity", exp.riskCheck, Modifier.fillMaxWidth())
                            FactorChip("7. Allowed Trading Session Window", exp.sessionCheck, Modifier.fillMaxWidth())
                        }
                    } else {
                        Text("No active signal yet. Engine evaluates closed M15 candles.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }
        }

        // Strategy-Specific Parameter Tuner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Strategy Parameter Tuner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)

                    when (strategyType) {
                        StrategyType.PULLBACK -> {
                            OutlinedTextField(
                                value = fastEma,
                                onValueChange = { fastEma = it },
                                label = { Text("Fast EMA Period") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("fast_ema_input")
                            )
                            OutlinedTextField(
                                value = slowEma,
                                onValueChange = { slowEma = it },
                                label = { Text("Slow EMA Period") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("slow_ema_input")
                            )
                            OutlinedTextField(
                                value = adxMin,
                                onValueChange = { adxMin = it },
                                label = { Text("ADX Minimum Threshold") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("adx_min_input")
                            )
                            OutlinedTextField(
                                value = atrSlMultiplier,
                                onValueChange = { atrSlMultiplier = it },
                                label = { Text("ATR Stop-Loss Multiplier") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("atr_multiplier_input")
                            )
                            OutlinedTextField(
                                value = rrRatio,
                                onValueChange = { rrRatio = it },
                                label = { Text("Risk-to-Reward Ratio (Default: 2.0 = 1:2)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("rr_ratio_input")
                            )
                        }

                        StrategyType.BREAKOUT -> {
                            OutlinedTextField(
                                value = breakoutLookback,
                                onValueChange = { breakoutLookback = it },
                                label = { Text("Lookback Period") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = breakoutVolumeMult,
                                onValueChange = { breakoutVolumeMult = it },
                                label = { Text("Volume Multiplier") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = breakoutConfirmCandles,
                                onValueChange = { breakoutConfirmCandles = it },
                                label = { Text("Confirm Candles") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = atrSlMultiplier,
                                onValueChange = { atrSlMultiplier = it },
                                label = { Text("ATR Stop-Loss Multiplier") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = rrRatio,
                                onValueChange = { rrRatio = it },
                                label = { Text("Risk-to-Reward Ratio (Default: 2.0 = 1:2)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        StrategyType.MEAN_REVERSION -> {
                            OutlinedTextField(
                                value = rsiPeriod,
                                onValueChange = { rsiPeriod = it },
                                label = { Text("RSI Period") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = rsiOverbought,
                                onValueChange = { rsiOverbought = it },
                                label = { Text("RSI Overbought") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = rsiOversold,
                                onValueChange = { rsiOversold = it },
                                label = { Text("RSI Oversold") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = bbPeriod,
                                onValueChange = { bbPeriod = it },
                                label = { Text("Bollinger Band Period") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = bbStdDev,
                                onValueChange = { bbStdDev = it },
                                label = { Text("Bollinger Band StdDev") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = rrRatio,
                                onValueChange = { rrRatio = it },
                                label = { Text("Risk-to-Reward Ratio (Default: 2.0 = 1:2)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        StrategyType.MOMENTUM -> {
                            OutlinedTextField(
                                value = macdFast,
                                onValueChange = { macdFast = it },
                                label = { Text("MACD Fast Period") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = macdSlow,
                                onValueChange = { macdSlow = it },
                                label = { Text("MACD Slow Period") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = macdSignal,
                                onValueChange = { macdSignal = it },
                                label = { Text("MACD Signal Period") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = momentumAdxThreshold,
                                onValueChange = { momentumAdxThreshold = it },
                                label = { Text("Momentum ADX Threshold") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = rrRatio,
                                onValueChange = { rrRatio = it },
                                label = { Text("Risk-to-Reward Ratio (Default: 2.0 = 1:2)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        StrategyType.RANGE_TRADING -> {
                            OutlinedTextField(
                                value = rangeLookback,
                                onValueChange = { rangeLookback = it },
                                label = { Text("Lookback Period") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = rangeMinTouches,
                                onValueChange = { rangeMinTouches = it },
                                label = { Text("Min Touches") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = rangeAdxMax,
                                onValueChange = { rangeAdxMax = it },
                                label = { Text("ADX Max Threshold") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = rrRatio,
                                onValueChange = { rrRatio = it },
                                label = { Text("Risk-to-Reward Ratio (Default: 2.0 = 1:2)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        StrategyType.SCALPING -> {
                            OutlinedTextField(
                                value = scalpMinRr,
                                onValueChange = { scalpMinRr = it },
                                label = { Text("Minimum R:R") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = scalpMaxHold,
                                onValueChange = { scalpMaxHold = it },
                                label = { Text("Max Hold Minutes") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = atrSlMultiplier,
                                onValueChange = { atrSlMultiplier = it },
                                label = { Text("ATR Stop-Loss Multiplier") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = rrRatio,
                                onValueChange = { rrRatio = it },
                                label = { Text("Risk-to-Reward Ratio (Default: 2.0 = 1:2)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val current = repository.getOrCreateConfig()
                                repository.updateConfig(
                                    when (strategyType) {
                                        StrategyType.PULLBACK -> current.copy(
                                            strategyType = strategyType.name,
                                            emaFastPeriod = fastEma.toIntOrNull() ?: 20,
                                            emaSlowPeriod = slowEma.toIntOrNull() ?: 50,
                                            adxThreshold = adxMin.toDoubleOrNull() ?: 25.0,
                                            atrSlMultiplier = atrSlMultiplier.toDoubleOrNull() ?: 1.5,
                                            riskRewardRatio = rrRatio.toDoubleOrNull() ?: 2.0
                                        )
                                        StrategyType.BREAKOUT -> current.copy(
                                            strategyType = strategyType.name,
                                            breakoutLookbackPeriod = breakoutLookback.toIntOrNull() ?: 20,
                                            breakoutVolumeMultiplier = breakoutVolumeMult.toDoubleOrNull() ?: 1.5,
                                            breakoutConfirmCandles = breakoutConfirmCandles.toIntOrNull() ?: 2,
                                            atrSlMultiplier = atrSlMultiplier.toDoubleOrNull() ?: 1.5,
                                            riskRewardRatio = rrRatio.toDoubleOrNull() ?: 2.0
                                        )
                                        StrategyType.MEAN_REVERSION -> current.copy(
                                            strategyType = strategyType.name,
                                            rsiPeriod = rsiPeriod.toIntOrNull() ?: 14,
                                            rsiOverbought = rsiOverbought.toDoubleOrNull() ?: 70.0,
                                            rsiOversold = rsiOversold.toDoubleOrNull() ?: 30.0,
                                            bbPeriod = bbPeriod.toIntOrNull() ?: 20,
                                            bbStdDev = bbStdDev.toDoubleOrNull() ?: 2.0,
                                            riskRewardRatio = rrRatio.toDoubleOrNull() ?: 2.0
                                        )
                                        StrategyType.MOMENTUM -> current.copy(
                                            strategyType = strategyType.name,
                                            macdFastPeriod = macdFast.toIntOrNull() ?: 12,
                                            macdSlowPeriod = macdSlow.toIntOrNull() ?: 26,
                                            macdSignalPeriod = macdSignal.toIntOrNull() ?: 9,
                                            momentumAdxThreshold = momentumAdxThreshold.toDoubleOrNull() ?: 30.0,
                                            riskRewardRatio = rrRatio.toDoubleOrNull() ?: 2.0
                                        )
                                        StrategyType.RANGE_TRADING -> current.copy(
                                            strategyType = strategyType.name,
                                            rangeLookbackPeriod = rangeLookback.toIntOrNull() ?: 50,
                                            rangeMinTouches = rangeMinTouches.toIntOrNull() ?: 2,
                                            rangeAdxMax = rangeAdxMax.toDoubleOrNull() ?: 20.0,
                                            riskRewardRatio = rrRatio.toDoubleOrNull() ?: 2.0
                                        )
                                        StrategyType.SCALPING -> current.copy(
                                            strategyType = strategyType.name,
                                            scalpMinRr = scalpMinRr.toDoubleOrNull() ?: 1.5,
                                            scalpMaxHoldMinutes = scalpMaxHold.toIntOrNull() ?: 30,
                                            atrSlMultiplier = atrSlMultiplier.toDoubleOrNull() ?: 1.5,
                                            riskRewardRatio = rrRatio.toDoubleOrNull() ?: 2.0
                                        )
                                    }
                                )
                                saveStatus = "Parameters updated successfully!"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanLight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_strategy_btn")
                    ) {
                        Text("Save & Apply Parameters", fontWeight = FontWeight.Bold, color = BackgroundDark)
                    }

                    if (saveStatus.isNotBlank()) {
                        Text(saveStatus, color = EmeraldGain, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
