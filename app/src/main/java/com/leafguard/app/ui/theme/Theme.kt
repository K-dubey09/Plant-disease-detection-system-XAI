package com.leafguard.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = LeafGreen40,
    onPrimary = Color.White,
    primaryContainer = LeafGreen80,
    onPrimaryContainer = Color(0xFF002104),
    secondary = LeafGreenGrey40,
    onSecondary = Color.White,
    secondaryContainer = LeafGreenGrey80,
    onSecondaryContainer = Color(0xFF1B351E),
    tertiary = LeafAmber40,
    onTertiary = Color.White,
    tertiaryContainer = LeafAmber80,
    onTertiaryContainer = Color(0xFF3A1F00),
    background = LeafLightBg,
    onBackground = LeafLightOnSurface,
    surface = LeafLightSurface,
    onSurface = LeafLightOnSurface,
    surfaceVariant = LeafGreenGrey80,
    onSurfaceVariant = Color(0xFF424940),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = LeafGreen80,
    onPrimary = Color(0xFF003914),
    primaryContainer = LeafGreen40,
    onPrimaryContainer = LeafGreen80,
    secondary = LeafGreenGrey80,
    onSecondary = Color(0xFF2C3E2F),
    secondaryContainer = LeafGreenGrey40,
    onSecondaryContainer = LeafGreenGrey80,
    tertiary = LeafAmber80,
    onTertiary = Color(0xFF422C00),
    tertiaryContainer = LeafAmber40,
    onTertiaryContainer = LeafAmber80,
    background = LeafDarkBg,
    onBackground = LeafDarkOnSurface,
    surface = LeafDarkSurface,
    onSurface = LeafDarkOnSurface,
    surfaceVariant = LeafGreenGrey40,
    onSurfaceVariant = Color(0xFFC0CBBF),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

@Composable
fun LeafGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color (Material You) is opt-in; on by default for Android 12+ to feel native.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = LeafTypography,
        content = content,
    )
}
