package com.margin.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnPrimary,
    secondaryContainer = LightSurfaceRaised,
    onSecondaryContainer = LightOnSurface,
    tertiary = LightSecondary,
    onTertiary = LightOnPrimary,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceRaised,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainer = LightSurfaceRaised,
    surfaceContainerHigh = LightSurfaceRaised,
    surfaceContainerLow = LightSurface,
    surfaceContainerLowest = LightSurface,
    surfaceContainerHighest = LightSurfaceSunken,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = Color.White,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    scrim = Color(0x99000000),
)

private val DarkScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnPrimary,
    secondaryContainer = DarkSurfaceRaised,
    onSecondaryContainer = DarkOnSurface,
    tertiary = DarkSecondary,
    onTertiary = DarkOnPrimary,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceRaised,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainer = DarkSurfaceRaised,
    surfaceContainerHigh = DarkSurfaceRaised,
    surfaceContainerLow = DarkSurface,
    surfaceContainerLowest = DarkSurfaceSunken,
    surfaceContainerHighest = DarkSurfaceRaised,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = Color(0xFF2B0D08),
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    scrim = Color(0xCC000000),
)

/** Category accents are theme dependent, so they travel with the theme rather than as constants. */
val LocalCategoryColors = staticCompositionLocalOf { CategoryLight }

@Composable
fun MarginTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val accents = if (darkTheme) CategoryDark else CategoryLight

    CompositionLocalProvider(
        LocalCategoryColors provides accents,
        LocalTextStyle provides MarginTypography.bodyMedium,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MarginTypography,
            shapes = MarginShapes,
            content = content,
        )
    }
}
