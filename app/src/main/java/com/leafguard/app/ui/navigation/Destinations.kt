package com.leafguard.app.ui.navigation

/**
 * Centralized route definitions for the LeafGuard navigation graph.
 *
 * Routes are plain string constants (not sealed objects) so they can be used directly with
 * `navController.navigate(...)` and query-parameter templating (`{arg}`). Each destination
 * also declares the phase in which its real implementation ships, so placeholder screens can
 * surface an accurate "implemented in Phase N" message.
 */
object Routes {

    // --- Auth flow (Phase 2) ---
    const val AUTH_ROOT = "auth"                 // launcher: routes to register/login/dashboard from auth state
    const val REGISTER = "auth/register"
    const val LOGIN = "auth/login"

    // --- Main app (bottom-nav destinations) ---
    const val DASHBOARD = "dashboard"
    const val PLANTS = "plants"
    const val SCAN = "scan"                 // optional ?plantId=
    const val HISTORY = "history"
    const val RECORDS = "records"

    // --- Secondary destinations ---
    const val IMAGES = "images"
    const val PLANT_DETAIL = "plant/{plantId}"
    const val RESULT = "result/{scanId}"

    fun plantDetail(plantId: Long): String = "plant/$plantId"
    fun result(scanId: Long): String = "result/$scanId"
    fun scanForPlant(plantId: Long?): String =
        if (plantId == null) SCAN else "$SCAN?plantId=$plantId"
}

/**
 * Bottom navigation destinations shown in the primary [NavigationBar]. Limited to five
 * entries per Material 3 guidance; `Images` is reached from the Dashboard and each plant
 * detail rather than occupying a bottom slot.
 */
enum class TopDestination(
    val route: String,
    val labelRes: Int,
    val phase: Int,
) {
    DASHBOARD(Routes.DASHBOARD, com.leafguard.app.R.string.nav_dashboard, 6),
    PLANTS(Routes.PLANTS, com.leafguard.app.R.string.nav_plants, 3),
    SCAN(Routes.SCAN, com.leafguard.app.R.string.nav_scan, 5),
    HISTORY(Routes.HISTORY, com.leafguard.app.R.string.nav_history, 6),
    RECORDS(Routes.RECORDS, com.leafguard.app.R.string.nav_records, 6),
}
