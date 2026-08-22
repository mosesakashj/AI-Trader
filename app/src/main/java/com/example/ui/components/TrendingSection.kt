package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.TrendingSymbol
import com.example.data.api.MarketSentiment
import com.example.ui.theme.*

@Composable
fun TrendingMoversCard(
    trending: List<TrendingSymbol>,
    onSymbolClick: (String) -> Unit = {},
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
                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = GoldHero, modifier = Modifier.size(20.dp))
                    Text(
                        text = "Trending Movers",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Surface(color = GoldContainer, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = "24H",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = GoldHero,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                trending.take(8).forEach { item ->
                    TrendingChip(item = item, onClick = { onSymbolClick(item.symbol) })
                }
            }
        }
    }
}

@Composable
private fun TrendingChip(
    item: TrendingSymbol,
    onClick: () -> Unit
) {
    val isPositive = item.change24h >= 0
    val sentimentColor = when (item.sentiment) {
        "bullish" -> EmeraldGain
        "bearish" -> CrimsonLoss
        else -> TextMuted
    }

    Surface(
        onClick = onClick,
        color = SurfaceVariantDark,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, sentimentColor.copy(alpha = 0.3f)),
        modifier = Modifier.width(130.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.symbol,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Surface(
                    color = sentimentColor.copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier.size(8.dp)
                ) {}
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$${"%.2f".format(item.price)}",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = if (isPositive) EmeraldGain else CrimsonLoss,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "${if (isPositive) "+" else ""}${"%.2f".format(item.change24h)}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (isPositive) EmeraldGain else CrimsonLoss
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val volWidth = (item.volatility.coerceIn(0.0, 5.0) / 5.0).toFloat()
            Box(modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(SurfaceDark)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(volWidth)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(sentimentColor.copy(alpha = 0.3f), sentimentColor)
                            )
                        )
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }
    }
}
