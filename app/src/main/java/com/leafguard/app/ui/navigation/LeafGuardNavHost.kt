package com.leafguard.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.leafguard.app.domain.model.AuthState
import com.leafguard.app.ui.auth.AuthViewModel
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
    TopDestination.RECORDS -> Icons.AutoMirrored.Outlined.ListAlt
}

private val bottomRoutes = TopDestination.entries.map { it.route }.toSet()

/** Routes that belong to the auth flow; the gating effect only force-navigates while on one of these. */
private val authRoutes = setOf(Routes.AUTH_ROOT, Routes.REGISTER, Routes.LOGIN)

@Composable
fun LeafGuardNavHost(
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomRoutes

    val authState by authViewModel.authState.collectAsStateWithLifecycle()

    // Centralized auth gating: whenever the auth state changes AND the user is still on an auth
    // route, navigate to the destination that matches the state. Once the user is inside the
    // main app this effect is a no-op, so in-app navigation is never hijacked.
    LaunchedEffect(authState) {
        if (currentRoute in authRoutes) {
            val target = when (authState) {
                AuthState.Unregistered -> Routes.REGISTER
                is AuthState.Locked -> Routes.LOGIN
                is AuthState.Unlocked -> Routes.DASHBOARD
            }
            if (currentRoute != target) {
                navController.navigate(target) {
                    popUpTo(Routes.AUTH_ROOT) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

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
            startDestination = Routes.AUTH_ROOT,
            modifier = Modifier.padding(innerPadding),
        ) {
            // Launcher: empty until the gating effect routes to register/login/dashboard.
            composable(Routes.AUTH_ROOT) { Box(Modifier) }

            // --- Auth ---
            composable(Routes.REGISTER) {
                AuthScreen(mode = AuthScreen.Mode.REGISTER, viewModel = authViewModel)
            }
            composable(Routes.LOGIN) {
                AuthScreen(mode = AuthScreen.Mode.LOGIN, viewModel = authViewModel)
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
