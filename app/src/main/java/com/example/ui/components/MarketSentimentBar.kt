package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.api.MarketSentiment
import com.example.ui.theme.*

@Composable
fun MarketSentimentBar(
    sentiment: MarketSentiment,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, CardBorderDark),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = CyanLight, modifier = Modifier.size(20.dp))
                    Text(
                        text = "Market Pulse",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Fear & Greed", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        text = "${sentiment.fearGreedIndex}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = when {
                            sentiment.fearGreedIndex >= 75 -> EmeraldGain
                            sentiment.fearGreedIndex >= 50 -> GoldHero
                            else -> CrimsonLoss
                        }
                    )
                    Text(
                        text = sentiment.fearGreedLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            sentiment.fearGreedIndex >= 75 -> EmeraldGain
                            sentiment.fearGreedIndex >= 50 -> GoldHero
                            else -> CrimsonLoss
                        }
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    InfoRow(label = "VIX", value = "%.1f".format(sentiment.vix))
                    InfoRow(label = "DXY", value = "%.2f".format(sentiment.dxy))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SentimentPill(label = "Gold", trend = sentiment.goldTrend, modifier = Modifier.weight(1f))
                SentimentPill(label = "Crypto", trend = sentiment.cryptoTrend, modifier = Modifier.weight(1f))
                SentimentPill(label = "Forex", trend = sentiment.forexTrend, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, color = TextPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SentimentPill(label: String, trend: String, modifier: Modifier = Modifier) {
    val color = when (trend) {
        "bullish" -> EmeraldGain
        "bearish" -> CrimsonLoss
        else -> TextMuted
    }
    val icon = when (trend) {
        "bullish" -> Icons.Default.TrendingUp
        "bearish" -> Icons.Default.TrendingDown
        else -> Icons.Default.TrendingFlat
    }

    Surface(
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(
                    trend.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}
