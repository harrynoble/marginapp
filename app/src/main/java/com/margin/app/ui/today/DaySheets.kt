package com.margin.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.margin.app.core.MarginTime
import com.margin.app.ui.components.CapsuleChip
import com.margin.app.ui.components.GroupedRow
import com.margin.app.ui.components.GroupedSection
import com.margin.app.ui.components.IconTile
import com.margin.app.ui.components.IosSwitch
import com.margin.app.ui.components.MarginSheet
import com.margin.app.ui.components.RowSeparator
import com.margin.app.ui.components.TimePickerSheet
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space

/**
 * "Lighten today". Asks what absolutely has to happen, whether build and learning stay, and
 * rebuilds the day around the answer. What is left out is not recorded as skipped and is not
 * piled onto tomorrow.
 */
@Composable
fun LightenSheet(
    options: List<LightenOption>,
    hasBuild: Boolean,
    hasLearning: Boolean,
    lowEnergy: Boolean,
    onDismiss: () -> Unit,
    onApply: (essentials: Set<String>, priorities: Set<String>, dropBuild: Boolean, dropLearning: Boolean, lowEnergy: Boolean) -> Unit,
) {
    val colors = MarginTheme.colors
    var essentials by remember { mutableStateOf(emptySet<String>()) }
    var priorities by remember { mutableStateOf(emptySet<String>()) }
    var dropBuild by remember { mutableStateOf(true) }
    var dropLearning by remember { mutableStateOf(true) }
    var tired by remember { mutableStateOf(lowEnergy) }

    MarginSheet(
        onDismiss = onDismiss,
        title = "Lighten Today",
        trailingText = "Rebuild",
        onTrailing = { onApply(essentials, priorities, dropBuild && hasBuild, dropLearning && hasLearning, tired) },
    ) {
        GroupedSection(
            modifier = Modifier.padding(top = Space.s),
            header = "What absolutely needs to happen today?",
            footer = if (options.isEmpty()) {
                "Nothing academic is left today. Anything due today or tomorrow is always kept."
            } else {
                "Anything due today, or an exam within two days, is kept either way."
            },
        ) {
            if (options.isEmpty()) {
                GroupedRow(title = "Nothing left to choose", titleColor = colors.secondaryLabel)
            }
            options.forEachIndexed { index, option ->
                if (index > 0) RowSeparator(inset = 56.dp)
                CheckRow(
                    title = option.label,
                    detail = option.detail,
                    checked = option.key in essentials,
                    onToggle = {
                        essentials = if (option.key in essentials) essentials - option.key else essentials + option.key
                    },
                )
            }
        }

        val subjectOptions = options.filter { it.subjectCode != null && it.key in essentials }
        if (subjectOptions.size > 1) {
            GroupedSection(header = "What should get priority?") {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(Space.m),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    subjectOptions.forEach { option ->
                        val code = option.subjectCode!!
                        CapsuleChip(
                            text = option.label,
                            selected = code in priorities,
                            onClick = { priorities = if (code in priorities) priorities - code else priorities + code },
                        )
                    }
                }
            }
        }

        GroupedSection(
            modifier = Modifier.padding(top = Space.xl),
            footer = "Everything else is left off today. It is not marked as skipped, and tomorrow is not made heavier for it.",
        ) {
            if (hasBuild) {
                GroupedRow(title = "Remove build today", trailing = { IosSwitch(dropBuild, { dropBuild = it }) })
                RowSeparator()
            }
            if (hasLearning) {
                GroupedRow(title = "Remove new-skill learning today", trailing = { IosSwitch(dropLearning, { dropLearning = it }) })
                RowSeparator()
            }
            GroupedRow(
                title = "Low energy",
                subtitle = "Shorter sessions, easier work first, more breaks",
                trailing = { IosSwitch(tired, { tired = it }) },
            )
        }
    }
}

@Composable
private fun CheckRow(title: String, detail: String?, checked: Boolean, onToggle: () -> Unit) {
    val colors = MarginTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .heightIn(min = 56.dp)
            .padding(horizontal = Space.l, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .then(
                    if (checked) Modifier.background(colors.tint)
                    else Modifier.border(1.7.dp, colors.tertiaryLabel, CircleShape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(Space.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = AppleType.body, color = colors.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!detail.isNullOrBlank()) {
                Text(text = detail, style = AppleType.footnote, color = colors.secondaryLabel, maxLines = 1)
            }
        }
    }
}

/**
 * "When will you be back?" The day is replanned around the answer, and academic work that
 * no longer fits moves to tomorrow.
 */
@Composable
fun OutSheet(
    nowMinute: Int,
    sleepMinute: Int,
    use24Hour: Boolean,
    onDismiss: () -> Unit,
    onChoose: (backMinute: Int?) -> Unit,
) {
    val accents = MarginTheme.accents
    var picking by remember { mutableStateOf(false) }
    val laterToday = (nowMinute + 120).roundTo5().coerceAtMost(sleepMinute - 30)
    val tonight = maxOf(nowMinute + 60, 21 * 60).roundTo5().coerceAtMost(sleepMinute - 15)

    MarginSheet(onDismiss = onDismiss, title = "When will you be back?") {
        GroupedSection(
            modifier = Modifier.padding(top = Space.s),
            footer = "Margin replans around it. Anything that no longer fits today moves to tomorrow.",
        ) {
            if (laterToday > nowMinute + 30) {
                GroupedRow(
                    title = "Later today",
                    subtitle = "Back around " + MarginTime.formatTime(laterToday, use24Hour),
                    leading = { IconTile(Icons.Rounded.Schedule, accents.blue) },
                    onClick = { onChoose(laterToday) },
                )
                RowSeparator(inset = 58.dp)
            }
            if (tonight > nowMinute + 30 && tonight != laterToday) {
                GroupedRow(
                    title = "Tonight",
                    subtitle = "Back by " + MarginTime.formatTime(tonight, use24Hour),
                    leading = { IconTile(Icons.Rounded.NightsStay, accents.indigo) },
                    onClick = { onChoose(tonight) },
                )
                RowSeparator(inset = 58.dp)
            }
            GroupedRow(
                title = "Tomorrow",
                subtitle = "Out for the rest of today",
                leading = { IconTile(Icons.Rounded.Bedtime, accents.purple) },
                onClick = { onChoose(null) },
            )
            RowSeparator(inset = 58.dp)
            GroupedRow(
                title = "Choose a time",
                leading = { IconTile(Icons.AutoMirrored.Rounded.DirectionsWalk, accents.gray) },
                showChevron = true,
                onClick = { picking = true },
            )
        }
    }

    if (picking) {
        TimePickerSheet(
            title = "Back at",
            initialMinute = (nowMinute + 90).roundTo5(),
            use24Hour = use24Hour,
            onDismiss = { picking = false },
            onConfirm = { minute ->
                picking = false
                onChoose(if (minute <= nowMinute) null else minute)
            },
        )
    }
}

/**
 * The build or learning offer: which project or goal, and for how long. "Not today" is a
 * perfectly good answer and is never counted against anything.
 */
@Composable
fun OfferSheet(
    title: String,
    question: String,
    options: List<Pair<Long, String>>,
    initialId: Long?,
    initialMinutes: Int,
    emptyHint: String,
    acceptLabel: String,
    onDismiss: () -> Unit,
    onAccept: (id: Long?, minutes: Int) -> Unit,
    onDecline: () -> Unit,
) {
    val colors = MarginTheme.colors
    var selected by remember { mutableStateOf(initialId ?: options.firstOrNull()?.first) }
    var minutes by remember { mutableIntStateOf(initialMinutes) }

    MarginSheet(
        onDismiss = onDismiss,
        title = title,
        leadingText = "Not today",
        onLeading = onDecline,
        trailingText = acceptLabel,
        onTrailing = { onAccept(selected, minutes) },
    ) {
        Text(
            text = question,
            style = AppleType.subheadline,
            color = colors.secondaryLabel,
            modifier = Modifier.padding(horizontal = Space.gutter + Space.l, vertical = Space.s),
        )
        GroupedSection(modifier = Modifier.padding(top = Space.s)) {
            if (options.isEmpty()) {
                GroupedRow(title = emptyHint, titleColor = colors.secondaryLabel)
            }
            options.forEachIndexed { index, (id, name) ->
                if (index > 0) RowSeparator(inset = 56.dp)
                CheckRow(title = name, detail = null, checked = selected == id, onToggle = { selected = id })
            }
        }
        GroupedSection(header = "For how long") {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(Space.m),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                listOf(30, 45, 60, 90, 120).forEach { option ->
                    CapsuleChip(
                        text = MarginTime.formatDuration(option),
                        selected = minutes == option,
                        onClick = { minutes = option },
                    )
                }
            }
        }
    }
}

private fun Int.roundTo5(): Int = ((this + 4) / 5) * 5
