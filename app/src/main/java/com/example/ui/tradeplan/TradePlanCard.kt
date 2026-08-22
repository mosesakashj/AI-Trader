package com.example.ui.tradeplan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.*
import com.example.domain.model.TradeDirection
import com.example.ui.components.FactorChip
import com.example.ui.theme.*

@Composable
fun TradePlanCard(
    plan: TradePlan,
    onExecute: (TradePlan) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val isBuy = plan.signal.direction == TradeDirection.BUY
    val directionColor = if (isBuy) EmeraldGain else CrimsonLoss
    val directionBg = if (isBuy) EmeraldContainer else CrimsonContainer

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, directionColor.copy(alpha = 0.3f)),
        modifier = modifier.fillMaxWidth().testTag("trade_plan_card_${plan.signal.symbol}_${plan.strategyType}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = plan.signal.symbol,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium,
                        color = when (plan.symbolConfig.assetType) {
                            AssetType.CRYPTO -> CyanLight
                            AssetType.COMMODITY -> GoldHero
                            AssetType.FOREX -> EmeraldGain
                            else -> TextPrimary
                        }
                    )
                    Surface(color = directionBg, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = plan.signal.direction.name,
                            color = directionColor,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Surface(color = PrimaryBlueContainer, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = plan.strategyType.displayName,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PriceLevelBox("ENTRY", SymbolCatalog.formatPrice(plan.signal.symbol, plan.signal.price), TextPrimary, Modifier.weight(1f))
                PriceLevelBox("STOP LOSS", SymbolCatalog.formatPrice(plan.signal.symbol, plan.signal.stopLoss), CrimsonLoss, Modifier.weight(1f))
                PriceLevelBox("TAKE PROFIT", SymbolCatalog.formatPrice(plan.signal.symbol, plan.signal.takeProfit), EmeraldGain, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoPill("R:R", "%.1f".format(plan.signal.rrRatio), if (plan.signal.rrRatio >= 2.0) EmeraldGain else GoldHero)
                    InfoPill("LOTS", "%.2f".format(plan.positionSize), PrimaryBlue)
                    InfoPill("RISK", "$%.2f".format(plan.riskAmount), CrimsonLoss)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (plan.marketSession.isOpen) {
                        Surface(color = EmeraldContainer, shape = RoundedCornerShape(4.dp)) {
                            Text("OPEN", style = MaterialTheme.typography.labelSmall, color = EmeraldDark, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    } else {
                        Surface(color = GoldContainer, shape = RoundedCornerShape(4.dp)) {
                            Text("CLOSED", style = MaterialTheme.typography.labelSmall, color = GoldHero, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FactorChip("Spread: ${SymbolCatalog.formatPrice(plan.signal.symbol, plan.currentQuote.spread)}", plan.currentQuote.spread <= plan.symbolConfig.spreadLimit, Modifier.weight(1f, fill = false))
                FactorChip("Valid", plan.validation?.isValid == true, Modifier.weight(1f, fill = false))
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (expanded) "Hide Details" else "Show Details", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider(color = CardBorderDark, modifier = Modifier.padding(vertical = 4.dp))
                    Text(plan.signal.explanation.reason, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FactorChip("EMA Trend", plan.signal.explanation.trendCheck, Modifier.fillMaxWidth())
                        FactorChip("ADX Momentum", plan.signal.explanation.adxCheck, Modifier.fillMaxWidth())
                        FactorChip("Pullback/Breakout", plan.signal.explanation.pullbackCheck, Modifier.fillMaxWidth())
                        FactorChip("Candle Confirmation", plan.signal.explanation.candleCheck, Modifier.fillMaxWidth())
                        FactorChip("Session Check", plan.signal.explanation.sessionCheck, Modifier.fillMaxWidth())
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IndicatorPill("EMA F", "%.2f".format(plan.indicators.emaFast))
                        IndicatorPill("EMA S", "%.2f".format(plan.indicators.emaSlow))
                        IndicatorPill("ADX", "%.1f".format(plan.indicators.adx))
                        IndicatorPill("ATR", "%.4f".format(plan.indicators.atr))
                    }
                    if (plan.validation != null && !plan.validation.isValid) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(color = CrimsonContainer, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, CrimsonLoss.copy(alpha = 0.3f))) {
                            Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonLoss, modifier = Modifier.size(14.dp))
                                Text(plan.validation.reason, style = MaterialTheme.typography.labelSmall, color = CrimsonDark)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { showConfirmDialog = true },
                enabled = plan.validation?.isValid != false && plan.positionSize > 0,
                colors = ButtonDefaults.buttonColors(containerColor = directionColor, disabledContainerColor = TextMuted.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp).testTag("execute_trade_btn_${plan.signal.symbol}")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("EXECUTE ${plan.signal.direction.name} ${plan.signal.symbol}", fontWeight = FontWeight.Black, color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirm ${plan.signal.direction.name} ${plan.signal.symbol}", fontWeight = FontWeight.Bold, color = directionColor) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Strategy: ${plan.strategyType.displayName}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("Entry: ${SymbolCatalog.formatPrice(plan.signal.symbol, plan.signal.price)}", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Stop Loss: ${SymbolCatalog.formatPrice(plan.signal.symbol, plan.signal.stopLoss)}", color = CrimsonLoss, style = MaterialTheme.typography.bodySmall)
                    Text("Take Profit: ${SymbolCatalog.formatPrice(plan.signal.symbol, plan.signal.takeProfit)}", color = EmeraldGain, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(color = CardBorderDark)
                    Text("Volume: ${"%.2f".format(plan.positionSize)} lots", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Risk: $${"%.2f".format(plan.riskAmount)}", color = CrimsonLoss, style = MaterialTheme.typography.bodySmall)
                    Text("R:R: ${"%.1f".format(plan.signal.rrRatio)}", color = GoldHero, style = MaterialTheme.typography.bodySmall)
                    if (plan.validation != null && !plan.validation.isValid) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("BLOCKED: ${plan.validation.reason}", color = CrimsonLoss, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showConfirmDialog = false; onExecute(plan) }, enabled = plan.validation?.isValid != false && plan.positionSize > 0, colors = ButtonDefaults.buttonColors(containerColor = directionColor)) {
                    Text("CONFIRM & SEND", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { OutlinedButton(onClick = { showConfirmDialog = false }) { Text("Cancel", color = TextSecondary) } },
            containerColor = SurfaceDark
        )
    }
}

@Composable
private fun PriceLevelBox(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(color = SurfaceVariantDark, shape = RoundedCornerShape(10.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun InfoPill(label: String, value: String, color: Color) {
    Surface(color = SurfaceVariantDark, shape = RoundedCornerShape(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(text = value, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun IndicatorPill(label: String, value: String) {
    Surface(color = SurfaceVariantDark, shape = RoundedCornerShape(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(text = value, style = MaterialTheme.typography.labelSmall, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
        }
    }
}
