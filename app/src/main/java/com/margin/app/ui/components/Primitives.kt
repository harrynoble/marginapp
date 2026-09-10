package com.margin.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.margin.app.domain.model.Category
import com.margin.app.ui.theme.SectionLabelStyle
import com.margin.app.ui.theme.Space
import com.margin.app.ui.theme.accentFor

/** The one section header used everywhere. Small, quiet, consistent. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text.uppercase(),
            style = SectionLabelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        trailing?.invoke()
    }
}

/**
 * The standard surface. Separation comes from a hairline outline and a surface shift rather
 * than a drop shadow, which keeps a long timeline from looking like a stack of paper.
 */
@Composable
fun MarginCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surface,
    border: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(Space.l),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = color,
        border = if (border) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun CategoryDot(category: Category, modifier: Modifier = Modifier, size: Int = 8) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(accentFor(category)),
    )
}

/** A thin vertical accent rail. Three device-independent pixels of colour, no more. */
@Composable
fun AccentRail(color: Color, modifier: Modifier = Modifier, height: Int = 44) {
    Box(
        modifier = modifier
            .width(3.dp)
            .heightIn(min = height.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
    )
}

@Composable
fun MetaChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.s, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A calm, useful empty state. No confetti, no exclamation marks. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Space.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            Spacer(Modifier.height(Space.s))
            action()
        }
    }
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    MarginCard(
        modifier = modifier,
        color = MaterialTheme.colorScheme.errorContainer,
        border = false,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(Space.s))
            Text(
                text = "Try again",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.clickable(onClick = onRetry),
            )
        }
    }
}

/** A single label and value on one line. The whole of Insights is built from these. */
@Composable
fun StatRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (accent != null) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Spacer(Modifier.width(Space.s))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A quiet horizontal bar. Used sparingly and never animated for effect. */
@Composable
fun ProportionBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    height: Int = 6,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(height.dp / 2))
            .background(track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height.dp)
                .clip(RoundedCornerShape(height.dp / 2))
                .background(color),
        )
    }
}
