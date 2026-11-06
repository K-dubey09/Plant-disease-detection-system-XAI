package com.leafguard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.leafguard.app.ui.navigation.LeafGuardNavHost
import com.leafguard.app.ui.theme.LeafGuardTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. The splash screen is installed before setContent so the platform
 * shows the branded launcher while the first frame composes. All UI lives in Compose via
 * [LeafGuardNavHost]; navigation gates between the auth flow and the main app is wired in
 * Phase 2 (PIN authentication) once the auth state holder exists.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // The start destination is chosen synchronously from the auth state inside LeafGuardNavHost
        // (routeFor(authState.value)), so the splash has nothing to gate: release it at once and let
        // the first frame compose.
        splash.setKeepOnScreenCondition { false }
        enableEdgeToEdge()
        setContent {
            LeafGuardTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LeafGuardNavHost()
                }
            }
        }
    }
}
