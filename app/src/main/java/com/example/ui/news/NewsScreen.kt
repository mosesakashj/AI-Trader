package com.example.ui.news

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.api.MarketInsightsRepository
import com.example.ui.components.EconomicCalendarCard
import com.example.ui.components.MarketSentimentBar
import com.example.ui.components.NewsFeedCard
import com.example.ui.theme.*

@Composable
fun NewsScreen() {
    val insightsRepo = remember { MarketInsightsRepository() }
    val news by insightsRepo.news.collectAsState()
    val economicEvents by insightsRepo.economicEvents.collectAsState()
    val sentiment by insightsRepo.sentiment.collectAsState()

    LaunchedEffect(Unit) {
        insightsRepo.refreshAll()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            MarketSentimentBar(sentiment = sentiment)
        }

        item {
            Text(
                text = "News & Events",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            NewsFeedCard(articles = news)
        }

        item {
            EconomicCalendarCard(events = economicEvents)
        }
    }
}
