package com.leafguard.app.ui.navigation

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
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

/** Auth flow routes; the transition effect only force-navigates while the user is on one of these. */
private val authRoutes = setOf(Routes.REGISTER, Routes.LOGIN)

/** Map an auth state to the route the user should be on. */
private fun routeFor(state: AuthState): String = when (state) {
    AuthState.Unregistered -> Routes.REGISTER
    is AuthState.Locked -> Routes.LOGIN
    is AuthState.Unlocked -> Routes.DASHBOARD
}

@Composable
fun LeafGuardNavHost(
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomRoutes

    val authState by authViewModel.authState.collectAsStateWithLifecycle()

    // Pick the start destination synchronously from the initial auth state. Reading the
    // StateFlow.value here (rather than routing through an empty launcher composable) avoids the
    // first-frame race where currentBackStackEntry is null and a launcher effect would not fire —
    // the bug that left the app on a blank screen at launch.
    val startDestination = remember { routeFor(authViewModel.authState.value) }

    // Auth-state transitions: register success -> dashboard, reset -> register, etc. Only acts
    // while the user is on an auth route, so in-app navigation is never hijacked.
    LaunchedEffect(authState) {
        val current = navController.currentDestination?.route
        if (current in authRoutes) {
            val target = routeFor(authState)
            if (current != target) {
                navController.navigate(target) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
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
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
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
                    onResult = { navController.navigate(Routes.RESULT) },
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
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.RESULT) {
                ResultScreen(
                    onBack = { navController.popBackStack() },
                    onScanAnother = {
                        navController.navigate(Routes.SCAN) {
                            popUpTo(Routes.SCAN) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}

