package com.margin.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * Apple's semantic system colours, taken from the iOS palette, so the app reads as native to
 * that design language rather than approximating it. Labels are the translucent greys Apple
 * uses (they pick up the colour of whatever surface they sit on), and fills are the same
 * low-alpha greys that back iOS controls.
 */

@Immutable
data class MarginColors(
    val isDark: Boolean,

    // Grounds
    val groupedBackground: Color,
    val surface: Color,
    val surfaceElevated: Color,

    // Text
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val quaternaryLabel: Color,

    // Lines and fills
    val separator: Color,
    val fill: Color,
    val secondaryFill: Color,
    val tertiaryFill: Color,
    val quaternaryFill: Color,

    // Meaning
    val tint: Color,
    val onTint: Color,
    val destructive: Color,
    val positive: Color,
    val warning: Color,
    val nowLine: Color,

    // Liquid Glass, used only by the navigation layer
    val glassTint: Color,
    val glassRimTop: Color,
    val glassRimBottom: Color,
    val glassEdge: Color,
    val glassSheen: Color,
    val glassFallback: Color,
    val glassSelection: Color,
    val glassIcon: Color,

    val scrim: Color,
)

val LightColors = MarginColors(
    isDark = false,
    groupedBackground = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF2F2F7),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    quaternaryLabel = Color(0x2E3C3C43),
    separator = Color(0x4A3C3C43),
    fill = Color(0x33787880),
    secondaryFill = Color(0x29787880),
    tertiaryFill = Color(0x1F767680),
    quaternaryFill = Color(0x14747480),
    tint = Color(0xFF007AFF),
    onTint = Color(0xFFFFFFFF),
    destructive = Color(0xFFFF3B30),
    positive = Color(0xFF34C759),
    warning = Color(0xFFFF9500),
    nowLine = Color(0xFFFF3B30),
    glassTint = Color(0x61FFFFFF),
    glassRimTop = Color(0xF2FFFFFF),
    glassRimBottom = Color(0x66FFFFFF),
    glassEdge = Color(0x17000000),
    glassSheen = Color(0x80FFFFFF),
    glassFallback = Color(0xF2F7F7FA),
    glassSelection = Color(0x14000000),
    glassIcon = Color(0xFF1C1C1E),
    scrim = Color(0x59000000),
)

val DarkColors = MarginColors(
    isDark = true,
    groupedBackground = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    surfaceElevated = Color(0xFF2C2C2E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    quaternaryLabel = Color(0x29EBEBF5),
    separator = Color(0xA6545458),
    fill = Color(0x5C787880),
    secondaryFill = Color(0x52787880),
    tertiaryFill = Color(0x3D767680),
    quaternaryFill = Color(0x2E767680),
    tint = Color(0xFF0A84FF),
    onTint = Color(0xFFFFFFFF),
    destructive = Color(0xFFFF453A),
    positive = Color(0xFF30D158),
    warning = Color(0xFFFF9F0A),
    nowLine = Color(0xFFFF453A),
    glassTint = Color(0x6B2A2A2E),
    glassRimTop = Color(0x73FFFFFF),
    glassRimBottom = Color(0x1FFFFFFF),
    glassEdge = Color(0x14FFFFFF),
    glassSheen = Color(0x24FFFFFF),
    glassFallback = Color(0xF2202023),
    glassSelection = Color(0x26FFFFFF),
    glassIcon = Color(0xFFF2F2F7),
    scrim = Color(0x8C000000),
)

/** Apple's system accent colours, light then dark. Categories and calendars draw from these. */
@Immutable
data class SystemAccents(
    val blue: Color,
    val green: Color,
    val indigo: Color,
    val orange: Color,
    val pink: Color,
    val purple: Color,
    val red: Color,
    val teal: Color,
    val yellow: Color,
    val brown: Color,
    val gray: Color,
    val mint: Color,
    val cyan: Color,
)

val LightAccents = SystemAccents(
    blue = Color(0xFF007AFF),
    green = Color(0xFF34C759),
    indigo = Color(0xFF5856D6),
    orange = Color(0xFFFF9500),
    pink = Color(0xFFFF2D55),
    purple = Color(0xFFAF52DE),
    red = Color(0xFFFF3B30),
    teal = Color(0xFF30B0C7),
    yellow = Color(0xFFFFCC00),
    brown = Color(0xFFA2845E),
    gray = Color(0xFF8E8E93),
    mint = Color(0xFF00C7BE),
    cyan = Color(0xFF32ADE6),
)

val DarkAccents = SystemAccents(
    blue = Color(0xFF0A84FF),
    green = Color(0xFF30D158),
    indigo = Color(0xFF5E5CE6),
    orange = Color(0xFFFF9F0A),
    pink = Color(0xFFFF375F),
    purple = Color(0xFFBF5AF2),
    red = Color(0xFFFF453A),
    teal = Color(0xFF40CBE0),
    yellow = Color(0xFFFFD60A),
    brown = Color(0xFFAC8E68),
    gray = Color(0xFF8E8E93),
    mint = Color(0xFF63E6E2),
    cyan = Color(0xFF64D2FF),
)

/**
 * The sky behind the Today header, keyed to the phase of the day. Content-layer colour, not
 * glass: it exists so the floating glass has something real to refract, and it tells you the
 * time of day at a glance. Low saturation on purpose; these are skies, not a brand gradient.
 */
@Immutable
data class SkyPalette(val warm: Color, val cool: Color)

enum class DayPhase { DAWN, DAY, DUSK, NIGHT }

fun phaseFor(minuteOfDay: Int): DayPhase = when (minuteOfDay) {
    in 5 * 60 until 11 * 60 -> DayPhase.DAWN
    in 11 * 60 until 17 * 60 -> DayPhase.DAY
    in 17 * 60 until 21 * 60 -> DayPhase.DUSK
    else -> DayPhase.NIGHT
}

fun skyFor(phase: DayPhase, dark: Boolean): SkyPalette = if (dark) {
    when (phase) {
        DayPhase.DAWN -> SkyPalette(Color(0xFF3D2620), Color(0xFF14213A))
        DayPhase.DAY -> SkyPalette(Color(0xFF0F2C47), Color(0xFF0B2E2A))
        DayPhase.DUSK -> SkyPalette(Color(0xFF3F2219), Color(0xFF191B38))
        DayPhase.NIGHT -> SkyPalette(Color(0xFF0C1633), Color(0xFF17122A))
    }
} else {
    when (phase) {
        DayPhase.DAWN -> SkyPalette(Color(0xFFFFD8C2), Color(0xFFD3E5FF))
        DayPhase.DAY -> SkyPalette(Color(0xFFC9E3FF), Color(0xFFD2F2E6))
        DayPhase.DUSK -> SkyPalette(Color(0xFFFFD3BF), Color(0xFFD6DAF3))
        DayPhase.NIGHT -> SkyPalette(Color(0xFFD1D9EE), Color(0xFFE1DCEF))
    }
}
