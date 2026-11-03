package com.leafguard.app.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure-logic unit tests for [Routes] helper functions. These run on the JVM (no Android
 * framework) and double as a smoke test that the `test` source set compiles with the
 * version catalog and Truth/Mockk dependencies wired in [app/build.gradle.kts].
 */
class RoutesTest {

    @Test
    fun plantDetail_includesId() {
        assertThat(Routes.plantDetail(42L)).isEqualTo("plant/42")
    }

    @Test
    fun result_includesScanId() {
        // Phase 5: the result screen is addressable by the persisted scan id (result/{scanId}).
        assertThat(Routes.result(7L)).isEqualTo("result/7")
    }

    @Test
    fun scanForPlant_withoutId_isBaseRoute() {
        assertThat(Routes.scanForPlant(null)).isEqualTo("scan")
    }

    @Test
    fun scanForPlant_withId_usesPlantRoute() {
        assertThat(Routes.scanForPlant(9L)).isEqualTo("scan-plant/9")
    }

    @Test
    fun topDestinations_haveFiveEntries() {
        // Material 3 NavigationBar guidance: at most 5 destinations.
        assertThat(TopDestination.entries).hasSize(5)
    }

    @Test
    fun topDestinations_routes_areUnique() {
        val routes = TopDestination.entries.map { it.route }
        assertThat(routes.toSet()).hasSize(routes.size)
    }
}
