package com.resizelab.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = ForestDeep,
    secondary = Color(0xFF5F6E35),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAF5CC),
    onSecondaryContainer = Color(0xFF20290A),
    tertiary = Color(0xFF7A5A2E),
    onTertiary = Color.White,
    tertiaryContainer = WarmSand,
    onTertiaryContainer = Color(0xFF2A1C06),
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDF1EA),
    onSurfaceVariant = Stone,
    outline = Color(0xFF8B988F),
    outlineVariant = Line,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Ink,
    inverseOnSurface = Color(0xFFEFF3ED),
    inversePrimary = Lime
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF003829),
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = Lime,
    onSecondary = Color(0xFF293500),
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = Color(0xFFE9C28A),
    onTertiary = Color(0xFF422C09),
    tertiaryContainer = Color(0xFF5D431D),
    onTertiaryContainer = Color(0xFFFFDEB0),
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkMuted,
    outline = Color(0xFF839189),
    outlineVariant = DarkOutline,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = DarkText,
    inverseOnSurface = Color(0xFF17211C),
    inversePrimary = ForestDeep
)

private val ResizeLabShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp)
)

@Composable
fun ResizeLabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ResizeLabTypography,
        shapes = ResizeLabShapes,
        content = content
    )
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}