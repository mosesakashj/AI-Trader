package com.example.ui.strategy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.EdgeTraderApp
import com.example.ai.AiStrategyAudit
import com.example.ai.AiStrategyOptimization
import com.example.domain.model.StrategyType
import com.example.domain.model.TradeMode
import com.example.ui.components.FactorChip
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun StrategyScreen() {
    val repository = EdgeTraderApp.instance.firestoreRepository
    val engine = EdgeTraderApp.instance.tradingEngine
    val aiManager = remember { EdgeTraderApp.instance.aiManager }
    val config by repository.configFlow.collectAsState(initial = null)
    val latestSignal by engine.latestSignal.collectAsState()
    val shadowSignals by engine.shadowSignals.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var aiStrategyAudit by remember { mutableStateOf<AiStrategyAudit?>(null) }
    var isAuditingStrategy by remember { mutableStateOf(false) }
    var aiOptimization by remember { mutableStateOf<AiStrategyOptimization?>(null) }
    var isOptimizingStrategy by remember { mutableStateOf(false) }
    var appliedOptimizationNotice by remember { mutableStateOf<String?>(null) }

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

    // Dynamic Trade Protection & Break-Even parameters
    var breakEvenEnabled by remember(config) { mutableStateOf(config?.breakEvenEnabled ?: true) }
    var breakEvenTriggerR by remember(config) { mutableStateOf(config?.breakEvenTriggerR?.toString() ?: "0.8") }
    var breakEvenBufferPips by remember(config) { mutableStateOf(config?.breakEvenBufferPips?.toString() ?: "1.5") }
    var trailingStopEnabled by remember(config) { mutableStateOf(config?.trailingStopEnabled ?: true) }
    var trailingStopTriggerR by remember(config) { mutableStateOf(config?.trailingStopTriggerR?.toString() ?: "1.2") }
    var trailingStopDistanceAtr by remember(config) { mutableStateOf(config?.trailingStopDistanceAtr?.toString() ?: "1.0") }
    var earlyExitOnTrendReversal by remember(config) { mutableStateOf(config?.earlyExitOnTrendReversal ?: true) }

    // Adaptive TP/SL/BE toggles
    var adaptiveTpEnabled by remember(config) { mutableStateOf(config?.adaptiveTpEnabled ?: true) }
    var adaptiveSlEnabled by remember(config) { mutableStateOf(config?.adaptiveSlEnabled ?: true) }
    var adaptiveBeEnabled by remember(config) { mutableStateOf(config?.adaptiveBeEnabled ?: true) }

    // Trade Mode
    var tradeMode by remember(config) {
        mutableStateOf(
            try { TradeMode.valueOf(config?.tradeMode ?: "BALANCED") } catch (_: Exception) { TradeMode.BALANCED }
        )
    }

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

        // AI Strategy Edge Diagnostic Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CyanLight.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().testTag("ai_strategy_audit_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = CyanLight, modifier = Modifier.size(18.dp))
                            Text("AI Strategy Edge Diagnostic", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }

                        if (aiStrategyAudit != null) {
                            Surface(
                                color = EmeraldContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "Edge: ${aiStrategyAudit!!.edgeScore}/100",
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldGain,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    if (aiStrategyAudit == null && !isAuditingStrategy) {
                        Text(
                            "Audit current parameters, regime adaptability, and receive quantitative parameter tuning recommendations for ${strategyType.displayName}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Button(
                            onClick = {
                                isAuditingStrategy = true
                                coroutineScope.launch {
                                    val currentStrategyConfig = com.example.domain.model.StrategyConfig(
                                        strategyType = strategyType,
                                        tradeMode = tradeMode,
                                        emaFastPeriod = fastEma.toIntOrNull() ?: 20,
                                        emaSlowPeriod = slowEma.toIntOrNull() ?: 50,
                                        adxThreshold = adxMin.toDoubleOrNull() ?: 25.0,
                                        atrSlMultiplier = atrSlMultiplier.toDoubleOrNull() ?: 1.5,
                                        riskRewardRatio = rrRatio.toDoubleOrNull() ?: 2.0,
                                        breakEvenTriggerR = breakEvenTriggerR.toDoubleOrNull() ?: 0.8,
                                        trailingStopDistanceAtr = trailingStopDistanceAtr.toDoubleOrNull() ?: 1.0
                                    )
                                    val auditRes = aiManager.auditStrategy(currentStrategyConfig)
                                    aiStrategyAudit = auditRes.getOrNull()
                                    isAuditingStrategy = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run AI Strategy Edge Audit", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    } else if (isAuditingStrategy) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = CyanLight, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Analyzing Strategy Edge & Market Regimes...", style = MaterialTheme.typography.bodySmall, color = CyanLight)
                        }
                    } else if (aiStrategyAudit != null) {
                        val audit = aiStrategyAudit!!
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(color = SurfaceVariantDark, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Market Fit: ${audit.marketFitRating}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = CyanLight)
                                    Text(audit.executiveSummary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }

                            Text("Parameter Analysis:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = CyanLight)
                            audit.parameterAnalysis.forEach { s ->
                                Text("• $s", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }

                            Text("AI Tuning Recommendations:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = GoldHero)
                            audit.recommendedAdjustments.forEach { v ->
                                Text("⚡ $v", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }

                            Surface(color = SurfaceVariantDark, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Risk Plan: ${audit.riskManagementPlan}", style = MaterialTheme.typography.labelSmall, color = EmeraldGain)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    isAuditingStrategy = true
                                    coroutineScope.launch {
                                        val currentStrategyConfig = com.example.domain.model.StrategyConfig(
                                            strategyType = strategyType,
                                            tradeMode = tradeMode,
                                            emaFastPeriod = fastEma.toIntOrNull() ?: 20,
                                            emaSlowPeriod = slowEma.toIntOrNull() ?: 50,
                                            adxThreshold = adxMin.toDoubleOrNull() ?: 25.0,
                                            atrSlMultiplier = atrSlMultiplier.toDoubleOrNull() ?: 1.5,
                                            riskRewardRatio = rrRatio.toDoubleOrNull() ?: 2.0,
                                            breakEvenTriggerR = breakEvenTriggerR.toDoubleOrNull() ?: 0.8,
                                            trailingStopDistanceAtr = trailingStopDistanceAtr.toDoubleOrNull() ?: 1.0
                                        )
                                        val auditRes = aiManager.auditStrategy(currentStrategyConfig)
                                        aiStrategyAudit = auditRes.getOrNull()
                                        isAuditingStrategy = false
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Re-run AI Diagnostics", fontSize = 12.sp, color = CyanLight)
                            }
                        }
                    }
                }
            }
        }

        // AI Strategy Auto-Fine-Tuner & Profit Maximizer
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GoldHero.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().testTag("ai_strategy_fine_tuner_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = GoldHero, modifier = Modifier.size(20.dp))
                            Text("AI Strategy Auto-Fine-Tuning", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }

                        if (aiOptimization != null) {
                            Surface(
                                color = EmeraldContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "+${"%.1f".format(aiOptimization!!.expectedProfitBoostPct)}% Profit Gain",
                                    fontWeight = FontWeight.Black,
                                    color = EmeraldGain,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Text(
                        "AI quantitatively diagnoses bottlenecks in ${strategyType.displayName}, dynamically calibrates indicators, and computes optimized parameters for maximum profitability and win-rate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    if (appliedOptimizationNotice != null) {
                        Surface(
                            color = EmeraldContainer.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGain, modifier = Modifier.size(16.dp))
                                Text(appliedOptimizationNotice!!, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }
                    }

                    if (aiOptimization == null && !isOptimizingStrategy) {
                        Button(
                            onClick = {
                                isOptimizingStrategy = true
                                appliedOptimizationNotice = null
                                coroutineScope.launch {
                                    val currentCfg = com.example.domain.model.StrategyConfig(
                                        strategyType = strategyType,
                                        tradeMode = tradeMode,
                                        emaFastPeriod = fastEma.toIntOrNull() ?: 20,
                                        emaSlowPeriod = slowEma.toIntOrNull() ?: 50,
                                        adxThreshold = adxMin.toDoubleOrNull() ?: 25.0,
                                        atrSlMultiplier = atrSlMultiplier.toDoubleOrNull() ?: 1.5,
                                        riskRewardRatio = rrRatio.toDoubleOrNull() ?: 2.0,
                                        breakEvenTriggerR = breakEvenTriggerR.toDoubleOrNull() ?: 0.8,
                                        trailingStopDistanceAtr = trailingStopDistanceAtr.toDoubleOrNull() ?: 1.0
                                    )
                                    val optRes = aiManager.optimizeAndFineTuneStrategy(currentCfg)
                                    aiOptimization = optRes.getOrNull()
                                    isOptimizingStrategy = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldHero),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("run_ai_tuning_btn")
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ANALYZE & FINE-TUNE STRATEGY", fontWeight = FontWeight.Black, color = Color.Black, fontSize = 13.sp)
                        }
                    } else if (isOptimizingStrategy) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = GoldHero, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Computing Quant Parameter Optimization...", style = MaterialTheme.typography.bodySmall, color = GoldHero)
                        }
                    } else if (aiOptimization != null) {
                        val opt = aiOptimization!!

                        // Expected Outcomes Grid
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                color = SurfaceVariantDark,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("PROFIT FACTOR", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                                    Text("${"%.2f".format(opt.currentProfitFactor)} → ${"%.2f".format(opt.targetProfitFactor)}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = EmeraldGain)
                                }
                            }
                            Surface(
                                color = SurfaceVariantDark,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("EXP. WIN RATE", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                                    Text("${"%.1f".format(opt.expectedWinRate)}%", fontSize = 14.sp, fontWeight = FontWeight.Black, color = CyanLight)
                                }
                            }
                            Surface(
                                color = SurfaceVariantDark,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("SHARPE RATIO", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                                    Text("${"%.2f".format(opt.expectedSharpe)}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = GoldHero)
                                }
                            }
                        }

                        // Identified Bottlenecks
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Identified Bottlenecks:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = CrimsonLoss)
                            opt.detectedBottlenecks.forEach { b ->
                                Text("⚠ $b", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }

                        // Parameter Optimizations List
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Tuned Parameter Adjustments:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = CyanLight)
                            opt.parameterOptimizations.forEach { (param, change) ->
                                Surface(
                                    color = SurfaceVariantDark,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(param, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                        Text(change, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = EmeraldGain)
                                    }
                                }
                            }
                        }

                        // 1-Tap Apply Optimized Parameters Button
                        Button(
                            onClick = {
                                val newCfg = opt.appliedConfig
                                fastEma = newCfg.emaFastPeriod.toString()
                                slowEma = newCfg.emaSlowPeriod.toString()
                                adxMin = newCfg.adxThreshold.toString()
                                atrSlMultiplier = newCfg.atrSlMultiplier.toString()
                                rrRatio = newCfg.riskRewardRatio.toString()
                                breakEvenTriggerR = newCfg.breakEvenTriggerR.toString()
                                breakEvenBufferPips = newCfg.breakEvenBufferPips.toString()
                                trailingStopDistanceAtr = newCfg.trailingStopDistanceAtr.toString()
                                breakEvenEnabled = newCfg.breakEvenEnabled
                                trailingStopEnabled = newCfg.trailingStopEnabled
                                earlyExitOnTrendReversal = newCfg.earlyExitOnTrendReversal

                                coroutineScope.launch {
                                    val current = repository.getOrCreateConfig()
                                    val updated = current.copy(
                                        emaFastPeriod = newCfg.emaFastPeriod,
                                        emaSlowPeriod = newCfg.emaSlowPeriod,
                                        adxThreshold = newCfg.adxThreshold,
                                        atrSlMultiplier = newCfg.atrSlMultiplier,
                                        riskRewardRatio = newCfg.riskRewardRatio,
                                        breakEvenTriggerR = newCfg.breakEvenTriggerR,
                                        breakEvenBufferPips = newCfg.breakEvenBufferPips,
                                        trailingStopDistanceAtr = newCfg.trailingStopDistanceAtr,
                                        breakEvenEnabled = newCfg.breakEvenEnabled,
                                        trailingStopEnabled = newCfg.trailingStopEnabled,
                                        earlyExitOnTrendReversal = newCfg.earlyExitOnTrendReversal,
                                        adaptiveTpEnabled = newCfg.adaptiveTpEnabled,
                                        adaptiveSlEnabled = newCfg.adaptiveSlEnabled,
                                        adaptiveBeEnabled = newCfg.adaptiveBeEnabled
                                    )
                                    repository.updateConfig(updated)
                                    appliedOptimizationNotice = "AI Optimized Parameters applied to Live Bot Engine!"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGain),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("apply_ai_tuning_btn")
                        ) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("APPLY AI OPTIMIZED PARAMETERS", fontWeight = FontWeight.Black, color = Color.Black, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Trade Mode Selector
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Trade Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CyanLight)
                    Text("Pre-configured risk profiles that adjust TP, SL, BE, and position sizing automatically.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TradeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = tradeMode == mode,
                                onClick = {
                                    tradeMode = mode
                                    coroutineScope.launch {
                                        val current = repository.getOrCreateConfig()
                                        repository.updateConfig(current.copy(tradeMode = mode.name))
                                    }
                                },
                                label = { Text(mode.displayName, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when (mode) {
                                        TradeMode.CONSERVATIVE -> EmeraldGain.copy(alpha = 0.15f)
                                        TradeMode.BALANCED -> CyanLight.copy(alpha = 0.15f)
                                        TradeMode.AGGRESSIVE -> GoldHero.copy(alpha = 0.15f)
                                    },
                                    selectedLabelColor = when (mode) {
                                        TradeMode.CONSERVATIVE -> EmeraldGain
                                        TradeMode.BALANCED -> CyanLight
                                        TradeMode.AGGRESSIVE -> GoldHero
                                    },
                                    containerColor = SurfaceVariantDark,
                                    labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = CardBorderDark,
                                    selectedBorderColor = when (mode) {
                                        TradeMode.CONSERVATIVE -> EmeraldGain
                                        TradeMode.BALANCED -> CyanLight
                                        TradeMode.AGGRESSIVE -> GoldHero
                                    },
                                    enabled = true,
                                    selected = tradeMode == mode
                                )
                            )
                        }
                    }

                    val modeDescription = when (tradeMode) {
                        TradeMode.CONSERVATIVE -> "Risk 0.15% | SL 2.0x ATR | TP 2.5R | BE@0.5R | Wider stops, earlier break-even"
                        TradeMode.BALANCED -> "Risk 0.25% | SL 1.5x ATR | TP 2.0R | BE@0.8R | Default risk/reward profile"
                        TradeMode.AGGRESSIVE -> "Risk 0.50% | SL 1.0x ATR | TP 1.5R | BE@1.2R | Tighter stops, more trades"
                    }
                    Surface(color = SurfaceVariantDark, shape = RoundedCornerShape(8.dp)) {
                        Text(modeDescription, style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(10.dp))
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

        // Live Shadow Strategy Comparison
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Shadow Strategy Monitor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CyanLight)
                    Text("Other strategies evaluated on live data. Signals blocked by risk limits shown in red.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                    val activeType = try {
                        StrategyType.valueOf(config?.strategyType ?: StrategyType.PULLBACK.name)
                    } catch (_: Exception) { StrategyType.PULLBACK }

                    StrategyType.entries.forEach { type ->
                        val isShadow = type != activeType
                        val signals = shadowSignals[type] ?: emptyList()
                        val executed = signals.count { it.wasExecuted }
                        val blocked = signals.count { !it.wasExecuted }
                        val lastSignal = signals.lastOrNull()

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isShadow) SurfaceVariantDark else PrimaryBlueContainer
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (!isShadow) CyanLight else CardBorderDark),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (!isShadow) {
                                            Surface(color = CyanContainer, shape = RoundedCornerShape(4.dp)) {
                                                Text("ACTIVE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = CyanLight, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                        }
                                        Text(type.displayName, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    }
                                    Text("${signals.size} signals", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(color = EmeraldContainer, shape = RoundedCornerShape(6.dp)) {
                                        Text("Exec: $executed", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = EmeraldGain, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                    if (blocked > 0) {
                                        Surface(color = CrimsonContainer, shape = RoundedCornerShape(6.dp)) {
                                            Text("Blocked: $blocked", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = CrimsonLoss, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                }

                                if (lastSignal != null) {
                                    HorizontalDivider(color = CardBorderDark)
                                    Text("Last: ${lastSignal.direction.name} @ ${"%.5f".format(lastSignal.price)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    if (lastSignal.blockedReason != null) {
                                        Text("Blocked: ${lastSignal.blockedReason}", style = MaterialTheme.typography.bodySmall, color = CrimsonLoss)
                                    }
                                }

                                val blockedSignals = signals.filter { !it.wasExecuted }
                                if (blockedSignals.isNotEmpty()) {
                                    HorizontalDivider(color = CardBorderDark)
                                    Text("Blocked Trade Reasons:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = CrimsonLoss)
                                    blockedSignals.groupBy { it.blockedReason ?: "Unknown" }.forEach { (reason, group) ->
                                        Text("  ${reason} (${group.size}x)", style = MaterialTheme.typography.bodySmall, color = CrimsonLoss)
                                    }
                                }
                            }
                        }
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

                    HorizontalDivider(color = CardBorderDark, modifier = Modifier.padding(vertical = 4.dp))

                    // Break-Even & Dynamic Trade Protection Section
                    Text(
                        "🛡️ Trade Protection & Break-Even Engine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CyanLight
                    )
                    Text(
                        "Protects against sudden market reversals and avoids turning winning trades into stop-loss hits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    // Auto Break-Even Control
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto Break-Even (Risk Free)", fontWeight = FontWeight.Bold, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                    Text("Ratchets SL to Entry + Profit Buffer once target profit is reached.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                }
                                Switch(
                                    checked = breakEvenEnabled,
                                    onCheckedChange = { breakEvenEnabled = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = CyanLight, checkedTrackColor = CyanContainer)
                                )
                            }
                            if (breakEvenEnabled) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = breakEvenTriggerR,
                                        onValueChange = { breakEvenTriggerR = it },
                                        label = { Text("Trigger Profit (R)") },
                                        placeholder = { Text("0.8") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = breakEvenBufferPips,
                                        onValueChange = { breakEvenBufferPips = it },
                                        label = { Text("Locked Buffer (Pips)") },
                                        placeholder = { Text("1.5") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Dynamic Trailing Stop Control
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Dynamic Trailing Stop", fontWeight = FontWeight.Bold, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                    Text("Follows market moves by ratcheting SL based on volatility (ATR).", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                }
                                Switch(
                                    checked = trailingStopEnabled,
                                    onCheckedChange = { trailingStopEnabled = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGain, checkedTrackColor = EmeraldContainer)
                                )
                            }
                            if (trailingStopEnabled) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = trailingStopTriggerR,
                                        onValueChange = { trailingStopTriggerR = it },
                                        label = { Text("Activation (R)") },
                                        placeholder = { Text("1.2") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = trailingStopDistanceAtr,
                                        onValueChange = { trailingStopDistanceAtr = it },
                                        label = { Text("Trail Distance (x ATR)") },
                                        placeholder = { Text("1.0") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Trend Reversal Early Exit Control
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Trend-Reversal Early Exit", fontWeight = FontWeight.Bold, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                Text("Exits immediately when EMA flips or momentum engulfing candle opposes position, avoiding full SL loss.", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                            Switch(
                                checked = earlyExitOnTrendReversal,
                                onCheckedChange = { earlyExitOnTrendReversal = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = GoldHero, checkedTrackColor = GoldContainer)
                            )
                        }
                    }

                    // Adaptive TP/SL/BE Section
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Adaptive TP/SL/BE", fontWeight = FontWeight.Bold, color = CyanLight, style = MaterialTheme.typography.bodyMedium)
                            Text("Automatically adjusts Stop Loss, Take Profit, and Break-Even based on ATR volatility and ADX trend strength.", style = MaterialTheme.typography.bodySmall, color = TextMuted)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Adaptive SL
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = if (adaptiveSlEnabled) CyanContainer else SurfaceDark),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Adaptive SL", fontWeight = FontWeight.Bold, color = TextPrimary, style = MaterialTheme.typography.labelSmall)
                                        Text("ATR+ADX", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                        Switch(
                                            checked = adaptiveSlEnabled,
                                            onCheckedChange = { adaptiveSlEnabled = it },
                                            colors = SwitchDefaults.colors(checkedThumbColor = CyanLight, checkedTrackColor = CyanContainer)
                                        )
                                    }
                                }
                                // Adaptive TP
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = if (adaptiveTpEnabled) EmeraldContainer else SurfaceDark),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Adaptive TP", fontWeight = FontWeight.Bold, color = TextPrimary, style = MaterialTheme.typography.labelSmall)
                                        Text("ATR+ADX", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                        Switch(
                                            checked = adaptiveTpEnabled,
                                            onCheckedChange = { adaptiveTpEnabled = it },
                                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGain, checkedTrackColor = EmeraldContainer)
                                        )
                                    }
                                }
                                // Adaptive BE
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = if (adaptiveBeEnabled) GoldContainer else SurfaceDark),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Adaptive BE", fontWeight = FontWeight.Bold, color = TextPrimary, style = MaterialTheme.typography.labelSmall)
                                        Text("ATR+ADX", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                        Switch(
                                            checked = adaptiveBeEnabled,
                                            onCheckedChange = { adaptiveBeEnabled = it },
                                            colors = SwitchDefaults.colors(checkedThumbColor = GoldHero, checkedTrackColor = GoldContainer)
                                        )
                                    }
                                }
                            }

                            Surface(color = SurfaceDark, shape = RoundedCornerShape(6.dp)) {
                                val adaptiveDesc = buildString {
                                    if (adaptiveSlEnabled) append("SL widens in strong trends, tightens in weak markets. ")
                                    if (adaptiveTpEnabled) append("TP targets higher in trends, conservative in ranges. ")
                                    if (adaptiveBeEnabled) append("BE triggers earlier in strong moves, later in weak markets.")
                                    if (isBlank()) append("All adaptive features disabled. Using fixed parameters.")
                                }
                                Text(adaptiveDesc.trim(), style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.padding(8.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val current = repository.getOrCreateConfig()
                                val baseUpdated = when (strategyType) {
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

                                val fullConfig = baseUpdated.copy(
                                    tradeMode = tradeMode.name,
                                    breakEvenEnabled = breakEvenEnabled,
                                    breakEvenTriggerR = breakEvenTriggerR.toDoubleOrNull() ?: 0.8,
                                    breakEvenBufferPips = breakEvenBufferPips.toDoubleOrNull() ?: 1.5,
                                    trailingStopEnabled = trailingStopEnabled,
                                    trailingStopTriggerR = trailingStopTriggerR.toDoubleOrNull() ?: 1.2,
                                    trailingStopDistanceAtr = trailingStopDistanceAtr.toDoubleOrNull() ?: 1.0,
                                    earlyExitOnTrendReversal = earlyExitOnTrendReversal,
                                    adaptiveTpEnabled = adaptiveTpEnabled,
                                    adaptiveSlEnabled = adaptiveSlEnabled,
                                    adaptiveBeEnabled = adaptiveBeEnabled
                                )

                                repository.updateConfig(fullConfig)
                                saveStatus = "Strategy & Trade Protection parameters updated successfully!"
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
