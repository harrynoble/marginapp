package com.margin.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginShape
import com.margin.app.ui.theme.MarginTheme

/*
 * iOS controls, rebuilt to their real proportions rather than restyled Material widgets:
 * the 51x31 switch, the capsule segmented control of iOS 26, the stepper, and capsule buttons.
 */

/** The filled capsule for the one primary action in a view. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = MarginTheme.colors.tint,
    height: Dp = 50.dp,
) {
    val colors = MarginTheme.colors
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(height)
            .pressScale(source)
            .clip(MarginShape.capsule)
            .background(if (enabled) color else colors.tertiaryFill)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (enabled) colors.onTint else colors.tertiaryLabel
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text = text, style = AppleType.headline, color = fg, maxLines = 1)
    }
}

/** A tinted-tone capsule for secondary actions, the iOS "bordered" button. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = MarginTheme.colors.tint,
    height: Dp = 50.dp,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(height)
            .pressScale(source)
            .clip(MarginShape.capsule)
            .background(color.copy(alpha = if (MarginTheme.colors.isDark) 0.22f else 0.13f))
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (enabled) color else MarginTheme.colors.tertiaryLabel
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text = text, style = AppleType.headline, color = fg, maxLines = 1)
    }
}

/** A round button on the content layer (not glass), for secondary actions inside cards. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
    tint: Color = MarginTheme.colors.label,
    background: Color = MarginTheme.colors.tertiaryFill,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size)
            .pressScale(source)
            .clip(CircleShape)
            .background(background)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.44f))
    }
}

/** Plain tinted text, the Cancel and Done of every iOS sheet. */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    color: Color = MarginTheme.colors.tint,
) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(MarginShape.small)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = if (emphasized) AppleType.headline else AppleType.body,
            color = if (enabled) color else MarginTheme.colors.tertiaryLabel,
            maxLines = 1,
        )
    }
}

/** The iOS switch at its real 51x31 size, green when on. */
@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MarginTheme.colors
    val view = LocalView.current
    val track by animateColorAsState(
        targetValue = if (checked) colors.positive else colors.fill,
        label = "switchTrack",
    )
    val thumbX by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 520f),
        label = "switchThumb",
    )
    Box(
        modifier = modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(MarginShape.capsule)
            .background(track)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onValueChange = {
                    Haptics.light(view)
                    onCheckedChange(it)
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbX)
                .size(27.dp)
                .shadow(elevation = 2.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** The iOS 26 segmented control: a capsule track with a lifted thumb that slides. */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MarginTheme.colors
    val view = LocalView.current
    val index = options.indexOf(selected).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(MarginShape.capsule)
            .background(colors.tertiaryFill)
            .padding(2.dp),
    ) {
        val segment = maxWidth / options.size.coerceAtLeast(1)
        val x by animateDpAsState(
            targetValue = segment * index,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 480f),
            label = "segment",
        )
        Box(
            modifier = Modifier
                .offset(x = x)
                .width(segment)
                .fillMaxHeight()
                .shadow(elevation = 2.dp, shape = MarginShape.capsule, clip = false)
                .clip(MarginShape.capsule)
                .background(if (colors.isDark) Color(0xFF636366) else Color.White),
        )
        Row(modifier = Modifier.fillMaxSize()) {
            options.forEachIndexed { i, option ->
                val isSelected = i == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(MarginShape.capsule)
                        .selectable(
                            selected = isSelected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = {
                                if (!isSelected) {
                                    Haptics.selection(view)
                                    onSelect(option)
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(option),
                        style = AppleType.footnote.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = colors.label,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A selectable capsule, used where a few short options sit in a row. */
@Composable
fun CapsuleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = MarginTheme.colors
    val background by animateColorAsState(
        targetValue = if (selected) colors.tint else colors.tertiaryFill,
        label = "chip",
    )
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(34.dp)
            .pressScale(source)
            .clip(MarginShape.capsule)
            .background(background)
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = AppleType.subheadline.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (selected) colors.onTint else colors.label,
            maxLines = 1,
        )
    }
}

/** The iOS minus/plus stepper. */
@Composable
fun IosStepper(
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    canDecrement: Boolean = true,
    canIncrement: Boolean = true,
) {
    val colors = MarginTheme.colors
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(MarginShape.capsule)
            .background(colors.tertiaryFill),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperHalf(Icons.Rounded.Remove, "Decrease", canDecrement, onDecrement)
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(18.dp)
                .background(colors.separator),
        )
        StepperHalf(Icons.Rounded.Add, "Increase", canIncrement, onIncrement)
    }
}

@Composable
private fun StepperHalf(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = MarginTheme.colors
    val view = LocalView.current
    Box(
        modifier = Modifier
            .width(46.dp)
            .fillMaxHeight()
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    Haptics.light(view)
                    onClick()
                },
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) colors.label else colors.tertiaryLabel,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** A value shown as a soft pill, as iOS shows a compact date or time picker. */
@Composable
fun ValuePill(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(
        modifier = modifier
            .clip(MarginShape.small)
            .background(MarginTheme.colors.tertiaryFill)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AppleType.body.copy(fontFeatureSettings = "tnum"),
            color = MarginTheme.colors.label,
            maxLines = 1,
        )
    }
}

/** A thin full-width rule for use outside grouped sections. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MarginTheme.colors.separator),
    )
}
