package com.example.ui.logs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.EdgeTraderApp
import com.example.data.entities.SystemEventEntity
import com.example.domain.model.LogLevel
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LogsScreen() {
    val repository = EdgeTraderApp.instance.repository
    val logs by repository.systemLogsFlow.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    val filteredLogs: List<SystemEventEntity> = remember(logs, selectedLevel) {
        if (selectedLevel == null) logs else logs.filter { it.level == selectedLevel?.name }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Filter Chips Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    null to "ALL",
                    LogLevel.INFO to "INFO",
                    LogLevel.WARN to "WARN",
                    LogLevel.ERROR to "ERROR",
                    LogLevel.CRITICAL to "CRITICAL"
                ).forEach { (lvl, label) ->
                    val isSelected = selectedLevel == lvl
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedLevel = lvl },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlueContainer,
                            selectedLabelColor = PrimaryBlue,
                            containerColor = SurfaceDark,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) PrimaryBlue else CardBorderDark
                        ),
                        modifier = Modifier.testTag("log_filter_$label")
                    )
                }
            }
        }

        // Header with count & clear
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "System Logs (${filteredLogs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            repository.clearHistory()
                        }
                    },
                    modifier = Modifier.testTag("clear_logs_btn")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear", color = TextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Logs List
        if (filteredLogs.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No log entries match the selected filter.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            items(filteredLogs, key = { it.id }) { log ->
                val (lvlColor, bgBadge) = when (log.level) {
                    "INFO" -> PrimaryBlue to PrimaryBlueContainer
                    "WARN" -> GoldHero to GoldContainer
                    "ERROR" -> CrimsonLoss to CrimsonContainer
                    "CRITICAL" -> CrimsonDark to CrimsonContainer
                    else -> TextSecondary to SurfaceVariantDark
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorderDark),
                    modifier = Modifier.fillMaxWidth().testTag("log_item_${log.id}")
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    color = bgBadge,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = log.level,
                                        color = lvlColor,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(log.component, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                            }
                            Text(timeFormat.format(Date(log.timestamp)), style = MaterialTheme.typography.labelSmall, color = TextMuted, fontFamily = FontFamily.Monospace)
                        }

                        Text(
                            text = "[${log.event}] ${log.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )

                        if (!log.symbol.isNullOrBlank()) {
                            Text(
                                text = "Symbol: ${log.symbol} | Correlation: ${log.correlationId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

