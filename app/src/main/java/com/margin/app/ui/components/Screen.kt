package com.margin.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.margin.app.ui.glass.GlassBackdrop
import com.margin.app.ui.glass.TopScrollEdge
import com.margin.app.ui.glass.glassSource
import com.margin.app.ui.glass.rememberGlassBackdrop
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space

/** Room left at the bottom of every tab so the floating tab bar never covers the last row. */
val TabBarClearance: Dp = Space.tabBarHeight + 28.dp

/**
 * The iOS screen: a bold, left-aligned large title that scrolls with the content, glass
 * controls pinned to the top corners, a soft scroll-edge effect under the status bar in place
 * of an opaque bar, and a small centred title that fades in once the large one has scrolled
 * away. Content extends edge to edge; chrome floats over it.
 */
@Composable
fun LargeTitleScreen(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    listState: LazyListState = rememberLazyListState(),
    backdrop: GlassBackdrop = rememberGlassBackdrop(),
    bottomClearance: Dp = TabBarClearance,
    background: @Composable BoxScope.() -> Unit = {},
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val colors = MarginTheme.colors
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val titleThresholdPx = with(LocalDensity.current) { 44.dp.toPx() }

    val collapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > titleThresholdPx
        }
    }
    val inlineAlpha by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "inlineTitle",
    )
    // As on iOS, the scroll edge only appears once content actually runs under the bar.
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    val edgeAlpha by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "scrollEdge",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.groupedBackground),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassSource(backdrop),
        ) {
            background()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = statusTop + Space.topBarHeight,
                    bottom = navBottom + bottomClearance,
                ),
            ) {
                item(key = "large-title") {
                    LargeTitle(title = title, eyebrow = eyebrow)
                }
                content()
            }
        }

        TopScrollEdge(
            backdrop = backdrop,
            height = statusTop + Space.topBarHeight + 28.dp,
            modifier = Modifier.graphicsLayer { alpha = edgeAlpha },
        )

        Text(
            text = title,
            style = AppleType.headline,
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 14.dp, start = 96.dp, end = 96.dp)
                .graphicsLayer { alpha = inlineAlpha },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(Space.topBarHeight)
                .padding(horizontal = Space.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigation?.invoke()
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

@Composable
private fun LargeTitle(title: String, eyebrow: String?) {
    val colors = MarginTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.gutter + 4.dp, end = Space.gutter, top = 6.dp, bottom = Space.m),
    ) {
        if (eyebrow != null) {
            Text(
                text = eyebrow,
                style = AppleType.subheadlineEmphasized,
                color = colors.secondaryLabel,
                maxLines = 1,
            )
        }
        Text(
            text = title,
            style = AppleType.largeTitle,
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
