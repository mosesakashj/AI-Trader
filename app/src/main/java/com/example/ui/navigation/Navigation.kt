package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Markets : Screen("markets", "Markets", Icons.AutoMirrored.Filled.ShowChart)
    object Strategy : Screen("strategy", "Strategy", Icons.Default.Psychology)
    object News : Screen("news", "News", Icons.Default.Newspaper)
    object Positions : Screen("positions", "Positions", Icons.Default.AccountBalanceWallet)
    object History : Screen("history", "History", Icons.Default.History)
    object Backtest : Screen("backtest", "Backtest", Icons.Default.Science)
    object Risk : Screen("risk", "Risk", Icons.Default.Shield)
    object Health : Screen("health", "System Health", Icons.Default.MonitorHeart)
    object Logs : Screen("logs", "Logs", Icons.Default.ReceiptLong)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Security : Screen("security", "Docs & Safety", Icons.Default.Security)
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Markets,
    Screen.Positions,
    Screen.Backtest,
    Screen.Settings
)

val drawerNavItems = listOf(
    Screen.Dashboard,
    Screen.Markets,
    Screen.Strategy,
    Screen.News,
    Screen.Positions,
    Screen.History,
    Screen.Backtest,
    Screen.Risk,
    Screen.Health,
    Screen.Logs,
    Screen.Settings,
    Screen.Security
)
