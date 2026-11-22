// [NEW FILE] Dark mode: the light and dark color sets, and the saved Light/Dark/System choice.
package com.moneymate.app.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode(val label: String) {
    SYSTEM("Use device setting"),
    LIGHT("Light"),
    DARK("Dark")
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("moneymate_settings", Context.MODE_PRIVATE)
    var themeMode: ThemeMode
        get() = prefs.getString("theme_mode", null)
            ?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } } ?: ThemeMode.SYSTEM
        set(value) { prefs.edit().putString("theme_mode", value.name).apply() }
}

@Immutable
data class MoneyMateColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val mint: Color,
    val green: Color,
    val red: Color,
    val balanceCard: Color,
    val balanceLabel: Color,
    val balanceDivider: Color
)

private val LightPalette = MoneyMateColors(
    isDark = false,
    background = Color(0xFFF7FAF8),
    surface = Color.White,
    ink = Color(0xFF17251F),
    muted = Color(0xFF6A7972),
    mint = Color(0xFFDDF5E8),
    green = Color(0xFF267552),
    red = Color(0xFFC6434D),
    balanceCard = Color(0xFF10261F),
    balanceLabel = Color(0xFFBCE0C9),
    balanceDivider = Color(0xFF426355)
)

private val DarkPalette = MoneyMateColors(
    isDark = true,
    background = Color(0xFF0E1613),
    surface = Color(0xFF18221E),
    ink = Color(0xFFE4EDE8),
    muted = Color(0xFF9AABA3),
    mint = Color(0xFF1E3A2E),
    green = Color(0xFF5CC48F),
    red = Color(0xFFFF8A92),
    balanceCard = Color(0xFF173A2D),
    balanceLabel = Color(0xFFBCE0C9),
    balanceDivider = Color(0xFF3B5E50)
)

val LocalMoneyMateColors = staticCompositionLocalOf { LightPalette }

object MoneyMateTheme {
    val colors: MoneyMateColors
        @Composable @ReadOnlyComposable get() = LocalMoneyMateColors.current
}

@Composable
fun MoneyMateTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.green, onPrimary = Color(0xFF0B2016),
            secondary = palette.green, onSecondary = Color(0xFF0B2016),
            secondaryContainer = palette.mint, onSecondaryContainer = Color(0xFFCDEBDB),
            background = palette.background, onBackground = palette.ink,
            surface = palette.surface, onSurface = palette.ink,
            surfaceVariant = Color(0xFF24302B), onSurfaceVariant = palette.muted,
            surfaceContainerLowest = Color(0xFF0B110F), surfaceContainerLow = Color(0xFF151E1A),
            surfaceContainer = Color(0xFF1C2723), surfaceContainerHigh = Color(0xFF222D28),
            surfaceContainerHighest = Color(0xFF2A3631),
            outline = Color(0xFF5E6F68), outlineVariant = Color(0xFF34423C),
            error = palette.red
        )
    } else {
        // original light colors
        lightColorScheme(
            primary = palette.green, secondary = palette.green,
            background = palette.background, surface = palette.surface, onSurface = palette.ink
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                @Suppress("DEPRECATION")
                window.navigationBarColor = palette.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalMoneyMateColors provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
