package com.margin.app.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * A near-neutral, slightly warm paper ground with one deep evergreen accent. The palette is
 * fixed rather than dynamic: the product identity is quiet, and wallpaper-derived colour
 * would pull the timeline in an arbitrary direction every time the user changes a wallpaper.
 */

// ---- Light ----------------------------------------------------------------------------------

val LightBackground = Color(0xFFFBFAF7)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceRaised = Color(0xFFF4F2EC)
val LightSurfaceSunken = Color(0xFFF0EEE7)
val LightPrimary = Color(0xFF1F5245)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFDCE9E2)
val LightOnPrimaryContainer = Color(0xFF0B2A22)
val LightSecondary = Color(0xFF4A5C56)
val LightOutline = Color(0xFFC9C6BC)
val LightOutlineVariant = Color(0xFFE4E1D8)
val LightOnSurface = Color(0xFF15181A)
val LightOnSurfaceVariant = Color(0xFF6B6E6A)
val LightError = Color(0xFF8C3A28)
val LightErrorContainer = Color(0xFFF6E1DB)
val LightOnErrorContainer = Color(0xFF48180E)

// ---- Dark -----------------------------------------------------------------------------------

val DarkBackground = Color(0xFF0F1110)
val DarkSurface = Color(0xFF181A19)
val DarkSurfaceRaised = Color(0xFF212423)
val DarkSurfaceSunken = Color(0xFF131615)
val DarkPrimary = Color(0xFF8FC5B1)
val DarkOnPrimary = Color(0xFF10312A)
val DarkPrimaryContainer = Color(0xFF234B40)
val DarkOnPrimaryContainer = Color(0xFFC8E7DA)
val DarkSecondary = Color(0xFFA9B8B1)
val DarkOutline = Color(0xFF3A403D)
val DarkOutlineVariant = Color(0xFF2C302E)
val DarkOnSurface = Color(0xFFECEDEA)
val DarkOnSurfaceVariant = Color(0xFF9BA09B)
val DarkError = Color(0xFFE49384)
val DarkErrorContainer = Color(0xFF43201A)
val DarkOnErrorContainer = Color(0xFFF7D9D2)

// ---- Category accents -------------------------------------------------------------------------
// Used at small sizes only: a 3dp rail on a block, a dot in a list. Never as a large fill.

val CategoryLight = listOf(
    Color(0xFF3F5E78), // Academics, slate blue
    Color(0xFF2E6B57), // Build, evergreen
    Color(0xFF6B5E8C), // Learning, muted violet
    Color(0xFF7C6A55), // Personal, taupe
    Color(0xFFA2544A), // Health, clay
    Color(0xFFB08442), // Leisure, ochre
    Color(0xFF74786F), // Other, grey
)

val CategoryDark = listOf(
    Color(0xFF8FB4D4),
    Color(0xFF82C4AA),
    Color(0xFFB3A4D1),
    Color(0xFFC6B295),
    Color(0xFFDFA096),
    Color(0xFFDDBB77),
    Color(0xFFA8ACA8),
)
