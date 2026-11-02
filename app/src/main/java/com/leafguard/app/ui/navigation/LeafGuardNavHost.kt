package com.leafguard.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.leafguard.app.ui.screens.AuthScreen
import com.leafguard.app.ui.screens.DashboardScreen
import com.leafguard.app.ui.screens.HistoryScreen
import com.leafguard.app.ui.screens.ImagesScreen
import com.leafguard.app.ui.screens.PlantDetailScreen
import com.leafguard.app.ui.screens.PlantsScreen
import com.leafguard.app.ui.screens.RecordsScreen
import com.leafguard.app.ui.screens.ResultScreen
import com.leafguard.app.ui.screens.ScanScreen

private fun TopDestination.icon(): ImageVector = when (this) {
    TopDestination.DASHBOARD -> Icons.Outlined.BarChart
    TopDestination.PLANTS -> Icons.Outlined.Spa
    TopDestination.SCAN -> Icons.Outlined.CenterFocusStrong
    TopDestination.HISTORY -> Icons.Outlined.History
    TopDestination.RECORDS -> Icons.Outlined.ListAlt
}

private val bottomRoutes = TopDestination.entries.map { it.route }.toSet()

@Composable
fun LeafGuardNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopDestination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon(), contentDescription = null) },
                            label = { Text(stringResource(dest.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.REGISTER,
            modifier = Modifier.padding(innerPadding),
        ) {
            // --- Auth ---
            composable(Routes.REGISTER) {
                AuthScreen(
                    mode = AuthScreen.Mode.REGISTER,
                    onAuthenticated = { navController.navigate(Routes.DASHBOARD) { popUpTo(0) } },
                )
            }
            composable(Routes.LOGIN) {
                AuthScreen(
                    mode = AuthScreen.Mode.LOGIN,
                    onAuthenticated = { navController.navigate(Routes.DASHBOARD) { popUpTo(0) } },
                )
            }

            // --- Bottom-nav destinations ---
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenPlants = { navController.navigate(Routes.PLANTS) },
                    onOpenImages = { navController.navigate(Routes.IMAGES) },
                    onScan = { navController.navigate(Routes.scanForPlant(null)) },
                )
            }
            composable(Routes.PLANTS) {
                PlantsScreen(onOpenPlant = { id -> navController.navigate(Routes.plantDetail(id)) })
            }
            composable(Routes.SCAN) {
                ScanScreen(
                    onResult = { scanId -> navController.navigate(Routes.result(scanId)) },
                )
            }
            composable(Routes.HISTORY) { HistoryScreen() }
            composable(Routes.RECORDS) { RecordsScreen() }

            // --- Secondary ---
            composable(Routes.IMAGES) { ImagesScreen() }
            composable(
                Routes.PLANT_DETAIL,
                arguments = listOf(navArgument("plantId") { type = NavType.LongType }),
            ) { entry ->
                PlantDetailScreen(
                    plantId = entry.arguments?.getLong("plantId") ?: -1L,
                    onScanForPlant = { id -> navController.navigate(Routes.scanForPlant(id)) },
                )
            }
            composable(
                Routes.RESULT,
                arguments = listOf(navArgument("scanId") { type = NavType.LongType }),
            ) { entry ->
                ResultScreen(scanId = entry.arguments?.getLong("scanId") ?: -1L)
            }
        }
    }
}
