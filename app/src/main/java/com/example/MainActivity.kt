package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ui.backtest.BacktestScreen
import com.example.ui.components.EmergencyStopDialog
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.health.HealthScreen
import com.example.ui.history.HistoryScreen
import com.example.ui.logs.LogsScreen
import com.example.ui.markets.MarketsScreen
import com.example.ui.navigation.Screen
import com.example.ui.navigation.bottomNavItems
import com.example.ui.navigation.drawerNavItems
import com.example.ui.positions.PositionsScreen
import com.example.ui.risk.RiskScreen
import com.example.ui.security.SecurityDocumentationScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.strategy.StrategyScreen
import com.example.ui.theme.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val dashboardViewModel: DashboardViewModel by hiltViewModel()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EdgeTraderTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Dashboard.route

                var showEmergencyDialog by remember { mutableStateOf(false) }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = SurfaceDark,
                            drawerContentColor = TextPrimary,
                            modifier = Modifier.width(300.dp)
                        ) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = PrimaryBlueContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
                                    }
                                }
                                Column {
                                    Text("EdgeTrader", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = TextPrimary)
                                    Text("On-Device Algorithmic Engine", style = MaterialTheme.typography.labelSmall, color = PrimaryBlue)
                                }
                            }
                            HorizontalDivider(color = CardBorderDark, modifier = Modifier.padding(vertical = 12.dp))

                            drawerNavItems.forEach { screen ->
                                val isSelected = currentRoute == screen.route
                                NavigationDrawerItem(
                                    icon = { Icon(screen.icon, contentDescription = null, tint = if (isSelected) PrimaryBlue else TextSecondary) },
                                    label = { Text(screen.title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) OnPrimaryBlueContainer else TextSecondary) },
                                    selected = isSelected,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        if (currentRoute != screen.route) {
                                            navController.navigate(screen.route) {
                                                popUpTo(Screen.Dashboard.route) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = PrimaryBlueContainer,
                                        unselectedContainerColor = Color.Transparent
                                    ),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).testTag("drawer_item_${screen.route}")
                                )
                            }
                        }
                    }
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = BackgroundDark,
                        topBar = {
                            TopAppBar(
                                title = {
                                    val currentScreen = drawerNavItems.firstOrNull { it.route == currentRoute } ?: Screen.Dashboard
                                    Text(
                                        text = currentScreen.title,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = TextPrimary
                                    )
                                },
                                navigationIcon = {
                                    IconButton(
                                        onClick = { scope.launch { drawerState.open() } },
                                        modifier = Modifier.testTag("open_drawer_btn")
                                    ) {
                                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextPrimary)
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = { showEmergencyDialog = true },
                                        modifier = Modifier.testTag("appbar_emergency_btn")
                                    ) {
                                        Icon(Icons.Default.Dangerous, contentDescription = "Emergency Stop", tint = CrimsonLoss)
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = SurfaceDark,
                                    titleContentColor = TextPrimary
                                )
                            )
                        },
                        bottomBar = {
                            NavigationBar(
                                containerColor = SurfaceDark,
                                contentColor = TextPrimary,
                                tonalElevation = 1.dp
                            ) {
                                bottomNavItems.forEach { screen ->
                                    val isSelected = currentRoute == screen.route
                                    NavigationBarItem(
                                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                                        label = { Text(screen.title, style = MaterialTheme.typography.labelSmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                        selected = isSelected,
                                        onClick = {
                                            if (currentRoute != screen.route) {
                                                navController.navigate(screen.route) {
                                                    popUpTo(Screen.Dashboard.route) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = PrimaryBlue,
                                            selectedTextColor = PrimaryBlue,
                                            indicatorColor = PrimaryBlueContainer,
                                            unselectedIconColor = TextSecondary,
                                            unselectedTextColor = TextSecondary
                                        ),
                                        modifier = Modifier.testTag("bottom_nav_${screen.route}")
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .background(BackgroundDark)
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = Screen.Dashboard.route
                            ) {
                                composable(Screen.Dashboard.route) {
                                    DashboardScreen(
                                        viewModel = dashboardViewModel,
                                        onNavigateToMarkets = { navController.navigate(Screen.Markets.route) },
                                        onNavigateToPositions = { navController.navigate(Screen.Positions.route) },
                                        onNavigateToStrategy = { navController.navigate(Screen.Strategy.route) }
                                    )
                                }
                                composable(Screen.Markets.route) {
                                    MarketsScreen()
                                }
                                composable(Screen.Strategy.route) {
                                    StrategyScreen()
                                }
                                composable(Screen.Positions.route) {
                                    PositionsScreen()
                                }
                                composable(Screen.History.route) {
                                    HistoryScreen()
                                }
                                composable(Screen.Backtest.route) {
                                    BacktestScreen()
                                }
                                composable(Screen.Risk.route) {
                                    RiskScreen()
                                }
                                composable(Screen.Health.route) {
                                    HealthScreen()
                                }
                                composable(Screen.Logs.route) {
                                    LogsScreen()
                                }
                                composable(Screen.Settings.route) {
                                    SettingsScreen(
                                        onNavigateToSecurity = { navController.navigate(Screen.Security.route) }
                                    )
                                }
                                composable(Screen.Security.route) {
                                    SecurityDocumentationScreen()
                                }
                            }
                        }
                    }
                }

                if (showEmergencyDialog) {
                    EmergencyStopDialog(
                        onConfirm = {
                            dashboardViewModel.triggerEmergencyStop()
                            showEmergencyDialog = false
                        },
                        onDismiss = { showEmergencyDialog = false }
                    )
                }
            }
        }
    }
}

