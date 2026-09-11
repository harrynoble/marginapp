package com.margin.app.ui.focus

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.SkipResolution
import com.margin.app.ui.components.Haptics
import com.margin.app.ui.components.PrimaryButton
import com.margin.app.ui.components.ProgressRing
import com.margin.app.ui.components.labelFor
import com.margin.app.ui.components.pressScale
import com.margin.app.ui.glass.GlassBackdrop
import com.margin.app.ui.glass.GlassCircleButton
import com.margin.app.ui.glass.GlassDefaults
import com.margin.app.ui.glass.GlassSurface
import com.margin.app.ui.glass.glassSource
import com.margin.app.ui.glass.rememberGlassBackdrop
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.DarkAccents
import com.margin.app.ui.theme.DarkColors
import com.margin.app.ui.theme.LocalAccents
import com.margin.app.ui.theme.LocalMarginColors
import com.margin.app.ui.theme.MarginShape
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space
import com.margin.app.ui.theme.railFor
import kotlin.math.abs

/**
 * One thing, full screen. Always dark, whatever the system theme, lit by the colour of what
 * you are working on, with the time left counting down to the second and nothing else asking
 * for attention. The controls are clear glass over the glow.
 */
@Composable
fun FocusScreen(
    viewModel: FocusViewModel,
    onClose: () -> Unit,
    onOpenNext: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) onClose()
    }
    LaunchedEffect(state.startedNext) {
        state.startedNext?.let(onOpenNext)
    }
    ImmersiveWindow()

    CompositionLocalProvider(
        LocalMarginColors provides DarkColors,
        LocalAccents provides DarkAccents,
        LocalContentColor provides DarkColors.label,
    ) {
        FocusContent(state = state, viewModel = viewModel, onClose = onClose, modifier = modifier)
    }
}

@Composable
private fun FocusContent(
    state: FocusUiState,
    viewModel: FocusViewModel,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val colors = MarginTheme.colors
    val view = LocalView.current
    val context = LocalContext.current
    val backdrop = rememberGlassBackdrop()
    val block = state.block
    val accentTarget = block?.let { railFor(it.type, it.category) } ?: MarginTheme.accents.blue
    val accent by animateColorAsState(accentTarget, tween(durationMillis = 900), label = "focusAccent")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ground),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassSource(backdrop),
        ) {
            Glow(accent = accent)
        }

        if (block == null) {
            Text(
                text = "That session is no longer on the schedule.",
                style = AppleType.subheadline,
                color = colors.secondaryLabel,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            val remaining = block.end * 60 - state.nowSecond
            val total = block.duration * 60
            val elapsed = state.nowSecond - block.start * 60
            val overrun = remaining < 0
            val progress = if (total <= 0) 0f else (elapsed.toFloat() / total).coerceIn(0f, 1f)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(Space.topBarHeight + Space.xl))
                Text(
                    text = listOfNotNull(
                        if (state.running) "FOCUS" else "PAUSED",
                        labelFor(block.type).takeIf { !it.equals(block.title, ignoreCase = true) }?.uppercase(),
                        block.academicType?.label?.uppercase(),
                    ).joinToString(" · "),
                    style = AppleType.footnoteEmphasized.copy(letterSpacing = 0.6.sp),
                    color = colors.secondaryLabel,
                )
                Spacer(Modifier.height(Space.s))
                Text(
                    text = block.title,
                    style = AppleType.title1,
                    color = colors.label,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                block.subtitle?.let {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        text = it,
                        style = AppleType.subheadline,
                        color = colors.secondaryLabel,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(44.dp))
                ProgressRing(
                    progress = progress,
                    color = if (overrun) colors.warning else accent,
                    stroke = 14.dp,
                    track = Color.White.copy(alpha = 0.10f),
                    modifier = Modifier.size(268.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val clock = (if (overrun) "+" else "") + clockText(abs(remaining))
                        Text(
                            text = clock,
                            style = if (abs(remaining) >= 3600) AppleType.timer.copy(fontSize = 46.sp) else AppleType.timer,
                            color = colors.label,
                            maxLines = 1,
                        )
                        Text(
                            text = when {
                                overrun -> "over"
                                state.running -> "remaining"
                                else -> "paused"
                            },
                            style = AppleType.subheadline,
                            color = colors.secondaryLabel,
                        )
                    }
                }

                Spacer(Modifier.height(Space.xxl))
                Text(
                    text = MarginTime.formatTime(block.start, state.use24Hour) + " – " +
                        MarginTime.formatTime(block.end, state.use24Hour) + "  ·  " +
                        MarginTime.formatDuration(state.plannedMinutes) + " planned" +
                        (if (block.extendedMinutes > 0) " · +" + MarginTime.formatDuration(block.extendedMinutes) else ""),
                    style = AppleType.footnote.copy(fontFeatureSettings = "tnum"),
                    color = colors.secondaryLabel,
                )

                val next = state.next
                if (overrun && state.running) {
                    // The planned time is up. Nothing is forced: move on, or keep going and the
                    // extra time is recorded against this subject.
                    Spacer(Modifier.height(Space.xl))
                    Text(
                        text = if (next != null) {
                            MarginTime.formatDuration(state.plannedMinutes + block.extendedMinutes) + " complete. Next: " + next.title
                        } else {
                            MarginTime.formatDuration(state.plannedMinutes + block.extendedMinutes) + " complete."
                        },
                        style = AppleType.subheadline,
                        color = colors.label,
                        textAlign = TextAlign.Center,
                    )
                } else if (state.breakDue != null) {
                    val suggestion = state.breakDue
                    Spacer(Modifier.height(Space.xl))
                    Text(
                        text = MarginTime.formatDuration(suggestion.run.minutes) + " of work without a real break.",
                        style = AppleType.subheadline,
                        color = colors.label,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(Space.s))
                    GlassChip(
                        backdrop = backdrop,
                        text = "Take ${suggestion.suggestedMinutes} min",
                        onClick = { viewModel.takeBreak(suggestion.suggestedMinutes) },
                    )
                }

                Spacer(Modifier.height(36.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val moveOn = overrun && state.running && next != null
                    PrimaryButton(
                        text = if (moveOn) "Move to next" else "Finish",
                        icon = Icons.Rounded.CheckCircle,
                        color = accent,
                        height = 56.dp,
                        onClick = {
                            Haptics.confirm(view)
                            if (moveOn) viewModel.moveToNext() else viewModel.complete()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    GlassCircleButton(
                        backdrop = backdrop,
                        icon = if (state.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.running) "Pause" else "Resume",
                        onClick = { if (state.running) viewModel.pause() else viewModel.resume() },
                        size = 56.dp,
                        iconSize = 26.dp,
                        style = GlassDefaults.clear(),
                        iconTint = colors.label,
                    )
                }

                Spacer(Modifier.height(Space.xl))
                if (overrun && state.running) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        GlassChip(backdrop = backdrop, text = "Continue 15 min", onClick = { viewModel.continueSession(15) })
                        GlassChip(backdrop = backdrop, text = "Continue 30 min", onClick = { viewModel.continueSession(30) })
                    }
                    if (next != null) {
                        Spacer(Modifier.height(Space.s))
                        GlassChip(backdrop = backdrop, text = "Finish here", onClick = { viewModel.complete() })
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        listOf(10, 15, 30).forEach { extra ->
                            GlassChip(backdrop = backdrop, text = "+$extra min", onClick = { viewModel.extend(extra) })
                        }
                    }
                    Spacer(Modifier.height(Space.s))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        GlassChip(
                            backdrop = backdrop,
                            text = "Later today",
                            onClick = { viewModel.skip(SkipResolution.LATER_TODAY) },
                        )
                        GlassChip(
                            backdrop = backdrop,
                            text = "Tomorrow",
                            onClick = { viewModel.skip(SkipResolution.TOMORROW) },
                        )
                        GlassChip(
                            backdrop = backdrop,
                            text = "Skip today",
                            onClick = { viewModel.skip(SkipResolution.DROP_TODAY) },
                        )
                    }
                }

                if (state.links.isNotEmpty()) {
                    Spacer(Modifier.height(Space.xxl))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MarginShape.card)
                            .background(Color.White.copy(alpha = 0.07f)),
                    ) {
                        state.links.forEach { link ->
                            val openable = link.url.isNotBlank()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = openable) { openLink(context, link.url) }
                                    .padding(horizontal = Space.l, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = link.label,
                                    style = AppleType.body,
                                    color = colors.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (openable) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        tint = colors.secondaryLabel,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                block.reason?.let { reason ->
                    Spacer(Modifier.height(Space.xxl))
                    Text(
                        text = reason,
                        style = AppleType.footnote,
                        color = colors.tertiaryLabel,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(Space.xxxl))
            }
        }

        Row(
            modifier = Modifier
                .statusBarsPadding()
                .height(Space.topBarHeight)
                .padding(horizontal = Space.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassCircleButton(
                backdrop = backdrop,
                icon = Icons.Rounded.Close,
                contentDescription = "Close",
                onClick = onClose,
                style = GlassDefaults.clear(),
                iconTint = colors.label,
            )
        }
    }
}

private val Ground = Color(0xFF050507)

/** The light of the thing you are doing, pooled at the top and reflected low on the right. */
@Composable
private fun Glow(accent: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(Ground)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0f)),
                center = Offset(size.width * 0.3f, size.height * 0.16f),
                radius = size.width * 1.1f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.26f), accent.copy(alpha = 0f)),
                center = Offset(size.width * 0.95f, size.height * 0.8f),
                radius = size.width * 0.85f,
            ),
        )
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.5f),
            ),
        )
    }
}

@Composable
private fun GlassChip(backdrop: GlassBackdrop, text: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    GlassSurface(
        backdrop = backdrop,
        style = GlassDefaults.clear(),
        modifier = Modifier
            .height(40.dp)
            .pressScale(source, pressedScale = 1.06f)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = text,
            style = AppleType.subheadlineEmphasized,
            color = MarginTheme.colors.label,
            modifier = Modifier.padding(horizontal = Space.l),
        )
    }
}

/** "24:59", or "1:04:59" past an hour. */
private fun clockText(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * While focusing: light status and navigation bar icons over the dark ground, and the screen
 * kept on so the countdown stays visible. Both are restored on the way out.
 */
@Composable
private fun ImmersiveWindow() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatus = controller?.isAppearanceLightStatusBars
        val lightNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            if (controller != null) {
                if (lightStatus != null) controller.isAppearanceLightStatusBars = lightStatus
                if (lightNavigation != null) controller.isAppearanceLightNavigationBars = lightNavigation
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Opens a resource in whatever app handles it. A reference that is not a URL is left alone
 * rather than launching something surprising.
 */
private fun openLink(context: Context, url: String) {
    val target = url.trim()
    if (target.isBlank()) return
    val uri = when {
        target.startsWith("http://") || target.startsWith("https://") -> Uri.parse(target)
        target.contains(".") && !target.contains(" ") -> Uri.parse("https://$target")
        else -> return
    }
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
