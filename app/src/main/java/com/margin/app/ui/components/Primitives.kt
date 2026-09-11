package com.margin.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.margin.app.domain.model.Category
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginShape
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space
import com.margin.app.ui.theme.accentFor

/*
 * The content layer. Everything here is a solid surface: Apple keeps glass for navigation and
 * builds content from grouped, rounded sections on the grouped background, set in bold,
 * left-aligned type.
 */

/** A bold, sentence-case heading for a group of content, as Health and Fitness use. */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onTrailing: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Space.gutter + 4.dp, end = Space.gutter, top = Space.xxl, bottom = Space.s),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = text,
            style = AppleType.title3.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            color = MarginTheme.colors.label,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null && onTrailing != null) {
            Text(
                text = trailing,
                style = AppleType.subheadline,
                color = MarginTheme.colors.tint,
                modifier = Modifier
                    .clip(MarginShape.small)
                    .clickable(onClick = onTrailing)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

/** The small grey caption above a form group, iOS inset-grouped style. */
@Composable
fun FormHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = AppleType.footnote.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(0.4f, androidx.compose.ui.unit.TextUnitType.Sp)),
        color = MarginTheme.colors.secondaryLabel,
        modifier = modifier.padding(start = Space.l, end = Space.l, top = Space.xl, bottom = 7.dp),
    )
}

/** Explanatory text under a form group. */
@Composable
fun FormFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AppleType.footnote,
        color = MarginTheme.colors.secondaryLabel,
        modifier = modifier.padding(start = Space.l, end = Space.l, top = 7.dp),
    )
}

/**
 * An inset grouped section: rows on a rounded surface, set 16dp in from the screen edges.
 */
@Composable
fun GroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter),
    ) {
        if (header != null) FormHeader(header)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MarginShape.card)
                .background(MarginTheme.colors.surface),
            content = content,
        )
        if (footer != null) FormFooter(footer)
    }
}

/** A single-pixel separator, inset from the leading edge the way iOS insets it past icons. */
@Composable
fun RowSeparator(inset: Dp = Space.l) {
    val hairline = with(LocalDensity.current) { 1f.toDp() }
    Box(
        modifier = Modifier
            .padding(start = inset)
            .fillMaxWidth()
            .height(hairline)
            .background(MarginTheme.colors.separator),
    )
}

/** The workhorse list row. */
@Composable
fun GroupedRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
    titleColor: Color = MarginTheme.colors.label,
    onClick: (() -> Unit)? = null,
) {
    val colors = MarginTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = if (subtitle != null) 60.dp else 52.dp)
            .padding(horizontal = Space.l, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Space.m))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppleType.body,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = AppleType.footnote,
                    color = colors.secondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(Space.s))
            Text(
                text = value,
                style = AppleType.body,
                color = colors.secondaryLabel,
                maxLines = 1,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(Space.s))
            trailing()
        }
        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.tertiaryLabel,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** The rounded-square coloured icon of iOS Settings. */
@Composable
fun IconTile(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MarginShape.iconTile)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.62f),
        )
    }
}

@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun CategoryDot(category: Category, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    ColorDot(color = accentFor(category), modifier = modifier, size = size)
}

/** A vertical capsule of colour, the calendar-style marker at the start of a timeline row. */
@Composable
fun CategoryRail(color: Color, modifier: Modifier = Modifier, height: Dp = 34.dp) {
    Box(
        modifier = modifier
            .width(4.dp)
            .height(height)
            .clip(MarginShape.capsule)
            .background(color),
    )
}

/** Apple's activity-ring idiom: a thick round-capped arc over a faint full track. */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 7.dp,
    track: Color = color.copy(alpha = 0.18f),
    content: @Composable BoxScope.() -> Unit = {},
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 90f),
        label = "ring",
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val s = stroke.toPx()
            val d = size.minDimension - s
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            drawArc(track, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(width = s))
            if (animated > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(d, d),
                    style = Stroke(width = s, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/** A thin capsule bar. The track is a fill, so it stays visible on any surface. */
@Composable
fun ProportionBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MarginTheme.colors.tint,
    track: Color = MarginTheme.colors.tertiaryFill,
    height: Dp = 6.dp,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 120f),
        label = "bar",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(MarginShape.capsule)
            .background(track),
    ) {
        if (animated > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(MarginShape.capsule)
                    .background(color),
            )
        }
    }
}

/** A single number with its label, the tile Fitness and Health are built from. */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = MarginTheme.colors.secondaryLabel,
) {
    Column(
        modifier = modifier
            .clip(MarginShape.tile)
            .background(MarginTheme.colors.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = AppleType.footnoteEmphasized,
            color = accent,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = AppleType.title2.copy(fontFeatureSettings = "tnum"),
            color = MarginTheme.colors.label,
            maxLines = 1,
        )
    }
}

/** Calm and useful. No exclamation marks, no confetti. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = MarginTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xxxl, vertical = Space.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.tertiaryLabel,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(Space.xs))
        }
        Text(text = title, style = AppleType.title3, color = colors.label, textAlign = TextAlign.Center)
        Text(
            text = body,
            style = AppleType.subheadline,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(Space.s))
            action()
        }
    }
}
