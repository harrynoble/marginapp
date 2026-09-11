package com.margin.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.margin.app.R
import kotlin.math.exp

/*
 * Apple's type scale, set in Inter.
 *
 * SF Pro is licensed for Apple platforms only and cannot ship in an Android app. Inter is the
 * open face closest to it in proportion and rhythm, and, like SF, it has an optical-size axis:
 * text sizes use the 14pt cut, which is wider with more open spacing, and titles use the 32pt
 * "Display" cut, which is tighter and sharper. That split is most of why SF looks like SF.
 *
 * Sizes are Apple's Dynamic Type defaults (Large), in sp so they follow the user font size.
 */

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: FontWeight, opticalSize: Float) = Font(
    resId = R.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("opsz", opticalSize),
    ),
)

/** Inter at text optical size, for everything below 20sp. */
val InterText = FontFamily(
    inter(FontWeight.Normal, 14f),
    inter(FontWeight.Medium, 14f),
    inter(FontWeight.SemiBold, 14f),
    inter(FontWeight.Bold, 14f),
)

/** Inter Display, for titles and large numerals. */
val InterDisplay = FontFamily(
    inter(FontWeight.Normal, 32f),
    inter(FontWeight.Medium, 32f),
    inter(FontWeight.SemiBold, 32f),
    inter(FontWeight.Bold, 32f),
    inter(FontWeight.ExtraBold, 32f),
)

/**
 * Inter's published dynamic-metrics curve: tighter tracking as size grows, which is what keeps
 * large titles from looking loose. Display sizes use a gentler version of it because the
 * display cut is already tighter by design.
 */
private fun tracking(size: Float, display: Boolean): TextUnit {
    val em = -0.0223f + 0.185f * exp(-0.1745f * size)
    return (em * size * if (display) 0.7f else 1f).sp
}

private fun display(size: Float, lineHeight: Float, weight: FontWeight) = TextStyle(
    fontFamily = InterDisplay,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking(size, display = true),
)

private fun text(size: Float, lineHeight: Float, weight: FontWeight) = TextStyle(
    fontFamily = InterText,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking(size, display = false),
)

/** Apple's named text styles. Screens use these names directly so intent is obvious. */
object AppleType {
    val largeTitle = display(34f, 41f, FontWeight.Bold)
    val title1 = display(28f, 34f, FontWeight.Bold)
    val title2 = display(22f, 28f, FontWeight.Bold)
    val title3 = display(20f, 25f, FontWeight.SemiBold)
    val headline = text(17f, 22f, FontWeight.SemiBold)
    val body = text(17f, 22f, FontWeight.Normal)
    val callout = text(16f, 21f, FontWeight.Normal)
    val subheadline = text(15f, 20f, FontWeight.Normal)
    val subheadlineEmphasized = text(15f, 20f, FontWeight.SemiBold)
    val footnote = text(13f, 18f, FontWeight.Normal)
    val footnoteEmphasized = text(13f, 18f, FontWeight.SemiBold)
    val caption1 = text(12f, 16f, FontWeight.Normal)
    val caption2 = text(11f, 13f, FontWeight.Medium)

    /** Tab bar labels, which Apple sets at 10pt medium. */
    val tabLabel = text(10f, 12f, FontWeight.SemiBold)

    /** Times and counts, with tabular figures so columns of numbers line up. */
    val timeGutter = text(15f, 20f, FontWeight.Medium).copy(fontFeatureSettings = "tnum")
    val numeral = display(34f, 40f, FontWeight.Bold).copy(fontFeatureSettings = "tnum")
    val timer = display(64f, 70f, FontWeight.SemiBold).copy(
        fontFeatureSettings = "tnum",
        letterSpacing = (-1.5).sp,
    )
}

/** Material components (dialogs, pickers) read these, so even they speak the same type. */
val MarginTypography = Typography(
    displayLarge = AppleType.timer,
    displayMedium = AppleType.largeTitle,
    displaySmall = AppleType.largeTitle,
    headlineLarge = AppleType.largeTitle,
    headlineMedium = AppleType.title1,
    headlineSmall = AppleType.title2,
    titleLarge = AppleType.title3,
    titleMedium = AppleType.headline,
    titleSmall = AppleType.subheadlineEmphasized,
    bodyLarge = AppleType.body,
    bodyMedium = AppleType.subheadline,
    bodySmall = AppleType.footnote,
    labelLarge = AppleType.subheadlineEmphasized,
    labelMedium = AppleType.footnoteEmphasized,
    labelSmall = AppleType.caption2,
)
