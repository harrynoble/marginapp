package com.margin.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.margin.app.R
import com.margin.app.core.MarginTime
import com.margin.app.data.prefs.UserPreferences
import com.margin.app.ui.components.DaySky
import com.margin.app.ui.components.DurationRow
import com.margin.app.ui.components.GroupedSection
import com.margin.app.ui.components.PrimaryButton
import com.margin.app.ui.components.RowSeparator
import com.margin.app.ui.components.TextAction
import com.margin.app.ui.components.TimeRow
import com.margin.app.ui.settings.SettingsViewModel
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.SmoothRoundedCornerShape
import com.margin.app.ui.theme.Space

private const val LAST_STEP = 3

/**
 * Apple's welcome pattern: the app icon, a bold centred title, four features in a column, and
 * one button. Setup after that is three short pages, every value already sensibly filled, and
 * all of it skippable. Nothing here asks for the timetable; it is already built in.
 */
@Composable
fun OnboardingScreen(
    viewModel: SettingsViewModel,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MarginTheme.colors
    var step by rememberSaveable { mutableIntStateOf(0) }

    fun finish(notifications: Boolean?) {
        viewModel.update { prefs ->
            prefs.copy(
                onboardingComplete = true,
                notificationsEnabled = notifications ?: prefs.notificationsEnabled,
            )
        }
        onFinish()
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        finish(granted)
    }

    BackHandler(enabled = step > 0) { step -= 1 }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.groupedBackground),
    ) {
        DaySky(minuteOfDay = remember { MarginTime.nowMinute() }, height = 440.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = targetState > initialState
                    val spec = tween<androidx.compose.ui.unit.IntOffset>(420, easing = FastOutSlowInEasing)
                    (slideInHorizontally(spec) { w -> if (forward) w / 3 else -w / 3 } + fadeIn(tween(280))) togetherWith
                        (slideOutHorizontally(spec) { w -> if (forward) -w / 3 else w / 3 } + fadeOut(tween(180)))
                },
                label = "onboarding",
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    when (page) {
                        0 -> WelcomePage()
                        1 -> DayPage(state.prefs, viewModel)
                        2 -> ProtectPage(state.prefs, viewModel)
                        else -> NotificationsPage()
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Space.xxl, end = Space.xxl, top = Space.m, bottom = Space.l),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PageDots(count = LAST_STEP + 1, current = step)
                Spacer(Modifier.height(Space.l))
                PrimaryButton(
                    text = if (step == LAST_STEP) "Turn On Notifications" else "Continue",
                    onClick = {
                        when {
                            step < LAST_STEP -> step += 1
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> finish(true)
                        }
                    },
                    height = 54.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.xs))
                TextAction(
                    text = if (step == LAST_STEP) "Not Now" else "Set Up Later",
                    onClick = { finish(if (step == LAST_STEP) false else null) },
                )
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    val colors = MarginTheme.colors
    val accents = MarginTheme.accents
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        AppIcon()
        Spacer(Modifier.height(Space.xxl))
        Text(
            text = "Welcome to Margin",
            style = AppleType.largeTitle,
            color = colors.label,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
            Feature(
                icon = Icons.Rounded.PlayCircle,
                color = accents.blue,
                title = "Always knows what's next",
                body = "Open it and the answer is at the top: what to do now, and for how long.",
            )
            Feature(
                icon = Icons.Rounded.CalendarMonth,
                color = accents.indigo,
                title = "Plans around your day",
                body = "College, travel and meals go in first. Your work fits into what's left.",
            )
            Feature(
                icon = Icons.Rounded.Spa,
                color = accents.green,
                title = "Protects your downtime",
                body = "Breaks, leisure and time to build things are kept, not squeezed out.",
            )
            Feature(
                icon = Icons.Rounded.Autorenew,
                color = accents.orange,
                title = "Adapts when things change",
                body = "Skip, run over or say what happened. The rest of the day rearranges itself.",
            )
        }
        Spacer(Modifier.height(Space.xxl))
    }
}

/** The launcher icon, drawn at its real proportions: the adaptive foreground over its ground. */
@Composable
private fun AppIcon() {
    val shape = SmoothRoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .size(96.dp)
            .shadow(elevation = 16.dp, shape = shape, clip = false, spotColor = Color.Black.copy(alpha = 0.3f))
            .clip(shape)
            .background(IconGround),
        contentAlignment = Alignment.Center,
    ) {
        // The adaptive foreground is 108 units with the visible icon in the middle 72.
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(96.dp * (108f / 72f)),
        )
    }
}

private val IconGround = Color(0xFF16302A)

@Composable
private fun Feature(icon: ImageVector, color: Color, title: String, body: String) {
    val colors = MarginTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(38.dp))
        Spacer(Modifier.width(Space.l))
        Column {
            Text(text = title, style = AppleType.headline, color = colors.label)
            Spacer(Modifier.height(2.dp))
            Text(text = body, style = AppleType.subheadline, color = colors.secondaryLabel)
        }
    }
}

@Composable
private fun PageHeader(icon: ImageVector, color: Color, title: String, body: String) {
    val colors = MarginTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(SmoothRoundedCornerShape(18.dp))
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(Space.xxl))
        Text(text = title, style = AppleType.title1, color = colors.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.s))
        Text(text = body, style = AppleType.body, color = colors.secondaryLabel, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.l))
    }
}

@Composable
private fun DayPage(prefs: UserPreferences, viewModel: SettingsViewModel) {
    PageHeader(
        icon = Icons.Rounded.WbSunny,
        color = MarginTheme.accents.orange,
        title = "Your day",
        body = "Margin plans between these times and keeps the rest of your evening yours.",
    )
    GroupedSection {
        TimeRow(
            title = "Wake up",
            minute = prefs.wakeMinute,
            use24Hour = prefs.use24HourTime,
            onChange = { m -> viewModel.update { it.copy(wakeMinute = m) } },
        )
        RowSeparator()
        TimeRow(
            title = "Bedtime",
            minute = prefs.sleepMinute,
            use24Hour = prefs.use24HourTime,
            onChange = { m -> viewModel.update { it.copy(sleepMinute = m) } },
        )
        RowSeparator()
        DurationRow(
            title = "Travel home",
            minutes = prefs.commuteMinutes,
            onChange = { m -> viewModel.update { it.copy(commuteMinutes = m) } },
            max = 180,
        )
    }
}

@Composable
private fun ProtectPage(prefs: UserPreferences, viewModel: SettingsViewModel) {
    PageHeader(
        icon = Icons.Rounded.Shield,
        color = MarginTheme.accents.green,
        title = "What Margin protects",
        body = "These are floors. Work is placed around them, never through them.",
    )
    GroupedSection {
        DurationRow(
            title = "Leisure each day",
            minutes = prefs.minLeisureMinutes,
            onChange = { m -> viewModel.update { it.copy(minLeisureMinutes = m) } },
            step = 15,
            max = 8 * 60,
        )
        RowSeparator()
        DurationRow(
            title = "Build on weekdays",
            minutes = prefs.buildMinutesWeekday,
            onChange = { m -> viewModel.update { it.copy(buildMinutesWeekday = m) } },
            step = 15,
            max = 6 * 60,
        )
        RowSeparator()
        DurationRow(
            title = "Daily work limit",
            minutes = prefs.maxWorkMinutesPerDay,
            onChange = { m -> viewModel.update { it.copy(maxWorkMinutesPerDay = m) } },
            step = 15,
            min = 30,
            max = 12 * 60,
        )
    }
}

@Composable
private fun NotificationsPage() {
    PageHeader(
        icon = Icons.Rounded.NotificationsActive,
        color = MarginTheme.accents.red,
        title = "Stay on track",
        body = "A quiet nudge when your next block starts and when a break is over. Classes are " +
            "never announced, and nothing else is sent.",
    )
}

@Composable
private fun PageDots(count: Int, current: Int) {
    val colors = MarginTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            val color by animateColorAsState(
                targetValue = if (index == current) colors.label else colors.tertiaryLabel,
                label = "dot",
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
