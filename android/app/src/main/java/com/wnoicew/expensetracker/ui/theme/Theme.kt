package com.wnoicew.expensetracker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueDarkTheme,
    onPrimary = TextMainDark,
    secondary = AccentCyan,
    background = BgPrimaryDark,
    surface = BgCardDark,
    surfaceVariant = BgElevatedDark,
    onBackground = TextMainDark,
    onSurface = TextMainDark,
    onSurfaceVariant = TextMutedDark,
    outline = GlassBorderDark
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlueLightTheme,
    onPrimary = Color.White,
    secondary = AccentCyan,
    background = BgPrimaryLight,
    surface = BgCardLight,
    surfaceVariant = BgElevatedLight,
    onBackground = TextMainLight,
    onSurface = TextMainLight,
    onSurfaceVariant = TextMutedLight,
    outline = GlassBorderLight
)

@Composable
fun MoneyTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
