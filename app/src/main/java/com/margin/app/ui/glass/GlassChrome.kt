package com.margin.app.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.margin.app.ui.components.Haptics
import com.margin.app.ui.components.pressScale
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginShape
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space
import kotlin.math.ceil

/** One destination in the floating tab bar. */
data class GlassTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/**
 * The iOS 26 tab bar: a floating capsule of Liquid Glass inset from the screen edges, with the
 * selected tab carried by a softer lozenge that springs between positions.
 */
@Composable
fun GlassTabBar(
    backdrop: GlassBackdrop?,
    tabs: List<GlassTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MarginTheme.colors
    val view = LocalView.current

    GlassSurface(
        backdrop = backdrop,
        modifier = modifier.height(Space.tabBarHeight),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
        ) {
            val itemWidth = maxWidth / tabs.size
            val indicatorX by animateDpAsState(
                targetValue = itemWidth * selectedIndex.coerceIn(0, tabs.lastIndex),
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
                label = "tabIndicator",
            )
            if (selectedIndex in tabs.indices) {
                Box(
                    modifier = Modifier
                        .offset(x = indicatorX)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clip(MarginShape.capsule)
                        .background(colors.glassSelection),
                )
            }
            Row(modifier = Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == selectedIndex
                    val tint by animateColorAsState(
                        targetValue = if (selected) colors.tint else colors.glassIcon,
                        label = "tabTint",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(MarginShape.capsule)
                            .selectable(
                                selected = selected,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                                onClick = {
                                    if (!selected) {
                                        Haptics.selection(view)
                                        onSelect(index)
                                    }
                                },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            style = AppleType.tabLabel,
                            color = tint,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A round glass control: the toolbar buttons at the top of each screen and the add button
 * beside the tab bar. Glass lifts under the finger rather than sinking.
 */
@Composable
fun GlassCircleButton(
    backdrop: GlassBackdrop?,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    style: GlassStyle = GlassDefaults.regular(),
    iconTint: Color = MarginTheme.colors.glassIcon,
) {
    val source = remember { MutableInteractionSource() }
    GlassSurface(
        backdrop = backdrop,
        style = style,
        modifier = modifier
            .size(size)
            .pressScale(source, pressedScale = 1.08f)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** A glass capsule holding a short text label, such as "Today" in the Plan toolbar. */
@Composable
fun GlassTextButton(
    backdrop: GlassBackdrop?,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassDefaults.regular(),
) {
    val source = remember { MutableInteractionSource() }
    GlassSurface(
        backdrop = backdrop,
        style = style,
        modifier = modifier
            .height(44.dp)
            .pressScale(source, pressedScale = 1.06f)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = text,
            style = AppleType.headline,
            color = MarginTheme.colors.glassIcon,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
    }
}

/**
 * The soft scroll-edge effect Apple uses in place of an opaque navigation bar: content that
 * scrolls under the status bar blurs progressively and fades, so the bar area stays legible
 * without a hard line. It is not decorative, it only exists where content meets chrome.
 */
@Composable
fun TopScrollEdge(
    backdrop: GlassBackdrop?,
    height: Dp,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 14.dp,
) {
    val colors = MarginTheme.colors
    val effectLayer = if (GlassSupport.blur && backdrop?.layer != null) rememberGraphicsLayer() else null
    var position by remember { mutableStateOf(Offset.Unspecified) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onGloballyPositioned { position = it.positionInWindow() }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                val source = backdrop?.layer
                val origin = backdrop?.origin ?: Offset.Unspecified
                if (source != null && effectLayer != null && origin.isSpecified && position.isSpecified) {
                    val blurPx = blurRadius.toPx()
                    val pad = ceil(blurPx * 2f).toInt().coerceAtLeast(2)
                    val layerSize = IntSize(
                        ceil(size.width).toInt() + pad * 2,
                        ceil(size.height).toInt() + pad * 2,
                    )
                    effectLayer.record(size = layerSize) {
                        translate(left = pad - (position.x - origin.x), top = pad - (position.y - origin.y)) {
                            drawLayer(source)
                        }
                    }
                    effectLayer.topLeft = IntOffset(-pad, -pad)
                    effectLayer.renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
                    drawLayer(effectLayer)
                    // Fade the blurred copy out toward the bottom so the edge has no line.
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black,
                            0.5f to Color.Black.copy(alpha = 0.75f),
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to colors.groupedBackground.copy(alpha = 0.42f),
                            1f to colors.groupedBackground.copy(alpha = 0f),
                        ),
                    )
                } else {
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to colors.groupedBackground.copy(alpha = 0.94f),
                            0.7f to colors.groupedBackground.copy(alpha = 0.6f),
                            1f to colors.groupedBackground.copy(alpha = 0f),
                        ),
                    )
                }
            },
    )
}
