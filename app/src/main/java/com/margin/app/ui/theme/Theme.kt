package com.margin.app.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.margin.app.ui.components.HighlightIndication

val LocalMarginColors = staticCompositionLocalOf { LightColors }
val LocalAccents = staticCompositionLocalOf { LightAccents }

/** Access point for the semantic palette, mirroring MaterialTheme. */
object MarginTheme {
    val colors: MarginColors
        @Composable @ReadOnlyComposable get() = LocalMarginColors.current

    val accents: SystemAccents
        @Composable @ReadOnlyComposable get() = LocalAccents.current
}

/**
 * Follows the system appearance. Ripples are switched off app-wide in favour of the iOS
 * behaviour: a row highlights grey while pressed, and buttons compress slightly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarginTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val accents = if (darkTheme) DarkAccents else LightAccents
    val scheme = remember(colors) { schemeFor(colors) }
    val highlight = remember(darkTheme) {
        HighlightIndication(if (darkTheme) Color(0x2EFFFFFF) else Color(0x1F000000))
    }

    CompositionLocalProvider(
        LocalMarginColors provides colors,
        LocalAccents provides accents,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MarginTypography,
            shapes = MarginShapes,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides colors.label,
                LocalIndication provides highlight,
                LocalRippleConfiguration provides null,
                content = content,
            )
        }
    }
}

/** Material pickers and dialogs read this, so they sit inside the same palette. */
private fun schemeFor(c: MarginColors): ColorScheme {
    val primaryContainer = c.tint.copy(alpha = if (c.isDark) 0.24f else 0.14f)
    return if (c.isDark) {
        darkColorScheme(
            primary = c.tint,
            onPrimary = c.onTint,
            primaryContainer = primaryContainer,
            onPrimaryContainer = c.tint,
            secondary = c.tint,
            onSecondary = c.onTint,
            secondaryContainer = c.tertiaryFill,
            onSecondaryContainer = c.label,
            tertiary = c.tint,
            onTertiary = c.onTint,
            background = c.groupedBackground,
            onBackground = c.label,
            surface = c.surface,
            onSurface = c.label,
            surfaceVariant = c.surfaceElevated,
            onSurfaceVariant = c.secondaryLabel,
            surfaceContainerLowest = c.surface,
            surfaceContainerLow = c.surface,
            surfaceContainer = c.surface,
            surfaceContainerHigh = c.surfaceElevated,
            surfaceContainerHighest = c.surfaceElevated,
            outline = c.separator,
            outlineVariant = c.separator,
            error = c.destructive,
            onError = Color.White,
            scrim = c.scrim,
        )
    } else {
        lightColorScheme(
            primary = c.tint,
            onPrimary = c.onTint,
            primaryContainer = primaryContainer,
            onPrimaryContainer = c.tint,
            secondary = c.tint,
            onSecondary = c.onTint,
            secondaryContainer = c.tertiaryFill,
            onSecondaryContainer = c.label,
            tertiary = c.tint,
            onTertiary = c.onTint,
            background = c.groupedBackground,
            onBackground = c.label,
            surface = c.surface,
            onSurface = c.label,
            surfaceVariant = c.surfaceElevated,
            onSurfaceVariant = c.secondaryLabel,
            surfaceContainerLowest = c.surface,
            surfaceContainerLow = c.surface,
            surfaceContainer = c.surface,
            surfaceContainerHigh = c.surface,
            surfaceContainerHighest = c.surfaceElevated,
            outline = c.separator,
            outlineVariant = c.separator,
            error = c.destructive,
            onError = Color.White,
            scrim = c.scrim,
        )
    }
}
