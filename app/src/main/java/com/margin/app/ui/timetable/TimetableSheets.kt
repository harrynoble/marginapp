package com.margin.app.ui.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.margin.app.ai.TimetableImport
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.AcademicType
import com.margin.app.domain.model.Difficulty
import com.margin.app.domain.model.Subject
import com.margin.app.domain.model.TimetableCheck
import com.margin.app.domain.model.TimetableEntry
import com.margin.app.domain.model.TimetableKind
import com.margin.app.ui.components.GroupedRow
import com.margin.app.ui.components.GroupedSection
import com.margin.app.ui.components.IosStepper
import com.margin.app.ui.components.MarginSheet
import com.margin.app.ui.components.RowSeparator
import com.margin.app.ui.components.SegmentedControl
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginShape
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space
import java.time.format.TextStyle
import java.util.Locale

/** Importance as the user thinks of it. Stored as 0 to 3. */
internal val ImportanceLabels = listOf("Normal", "Important", "Very important", "Top priority")

/**
 * How the planner should treat one subject: how much it matters to the user, how hard it is,
 * and how much revision each class hour earns.
 */
@Composable
fun SubjectSheet(
    subject: Subject,
    onDismiss: () -> Unit,
    onSave: (Subject) -> Unit,
) {
    var importance by remember { mutableStateOf(subject.importance.coerceIn(0, 3)) }
    var difficulty by remember { mutableStateOf(subject.difficulty) }
    var weight by remember { mutableFloatStateOf(subject.reviewWeight) }

    MarginSheet(
        onDismiss = onDismiss,
        title = subject.shortName,
        trailingText = "Save",
        onTrailing = {
            onSave(subject.copy(importance = importance, difficulty = difficulty, reviewWeight = weight))
        },
    ) {
        Text(
            text = subject.name,
            style = AppleType.subheadline,
            color = MarginTheme.colors.secondaryLabel,
            modifier = Modifier.padding(horizontal = Space.gutter + Space.l, vertical = Space.s),
        )
        GroupedSection(
            header = "Importance",
            footer = "Important subjects get earlier, longer sessions. Every subject is still covered.",
        ) {
            SegmentedControl(
                options = (0..3).toList(),
                selected = importance,
                label = { listOf("Normal", "High", "Higher", "Top")[it] },
                onSelect = { importance = it },
                modifier = Modifier.padding(Space.m),
            )
        }
        GroupedSection(header = "Difficulty", footer = "Harder subjects are placed when you are usually sharpest.") {
            SegmentedControl(
                options = Difficulty.entries,
                selected = difficulty,
                label = { it.label },
                onSelect = { difficulty = it },
                modifier = Modifier.padding(Space.m),
            )
        }
        GroupedSection(footer = "Revision earned for each hour of class. 1.0 is average.") {
            GroupedRow(
                title = "Revision weight",
                value = String.format(Locale.US, "%.1f", weight),
                trailing = {
                    IosStepper(
                        onDecrement = { weight = (weight - 0.1f).coerceAtLeast(0f) },
                        onIncrement = { weight = (weight + 0.1f).coerceAtMost(3f) },
                        canDecrement = weight > 0.05f,
                        canIncrement = weight < 2.95f,
                    )
                },
            )
        }
    }
}

/**
 * What the import read, laid out by day. Theory, lab and tutorial are shown for every class so
 * a misread type is obvious, and anything the app is unsure of is flagged: a subject it has
 * never seen, or two classes that overlap. Tap a class to fix its subject, time, day or type;
 * add one the import missed. Only what is confirmed here replaces the week.
 */
@Composable
fun TimetableReviewSheet(
    review: TimetableImport,
    use24Hour: Boolean,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = MarginTheme.colors
    val flagged = review.entries.indices.count { flagFor(review, it) != null }
    MarginSheet(
        onDismiss = onDismiss,
        title = "Check the Timetable",
        trailingText = "Replace",
        trailingEnabled = review.entries.isNotEmpty(),
        onTrailing = onConfirm,
    ) {
        Text(
            text = "${review.entries.size} classes read" +
                (if (flagged > 0) ", $flagged to check" else "") +
                ". Tap a class to fix its subject, time, day or type. This replaces your current weekly timetable.",
            style = AppleType.subheadline,
            color = colors.secondaryLabel,
            modifier = Modifier.padding(horizontal = Space.gutter + Space.l, vertical = Space.s),
        )
        if (review.warnings.isNotEmpty()) {
            GroupedSection(header = "Not imported") {
                review.warnings.forEachIndexed { index, warning ->
                    if (index > 0) RowSeparator()
                    GroupedRow(title = warning, titleColor = colors.secondaryLabel)
                }
            }
        }
        review.entries.withIndex().groupBy { it.value.dayOfWeek }.toSortedMap().forEach { (day, indexed) ->
            GroupedSection(header = day.getDisplayName(TextStyle.FULL, Locale.getDefault())) {
                indexed.sortedBy { it.value.start }.forEachIndexed { row, (position, entry) ->
                    if (row > 0) RowSeparator()
                    val flag = flagFor(review, position)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(position) }
                            .heightIn(min = 52.dp)
                            .padding(start = Space.l, end = Space.s, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = MarginTime.formatTime(entry.start, use24Hour),
                            style = AppleType.timeGutter,
                            color = colors.secondaryLabel,
                            modifier = Modifier.width(72.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.title, style = AppleType.body, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = listOfNotNull(
                                    entry.subjectCode,
                                    if (entry.kind == TimetableKind.RECESS) "Recess" else AcademicType.of(entry.kind)?.label,
                                    MarginTime.formatDuration(entry.range.duration),
                                ).joinToString(" · "),
                                style = AppleType.footnote,
                                color = colors.secondaryLabel,
                                maxLines = 1,
                            )
                            if (flag != null) {
                                Text(text = flag, style = AppleType.footnote, color = colors.warning, maxLines = 2)
                            }
                        }
                        Spacer(Modifier.width(Space.s))
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Remove ${entry.title}",
                            tint = colors.tertiaryLabel,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(MarginShape.capsule)
                                .clickable { onRemove(position) }
                                .padding(8.dp),
                        )
                    }
                }
            }
        }
        GroupedSection(modifier = Modifier.padding(top = Space.l)) {
            GroupedRow(title = "Add a Missing Class", titleColor = colors.tint, onClick = onAdd)
        }
    }
}

/**
 * What changed in the stored week compared with the confirmed one. The user either puts the
 * confirmed week back or says the stored one is right; nothing is changed without them.
 */
@Composable
fun TimetableCheckSheet(
    check: TimetableCheck,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
    onAccept: () -> Unit,
) {
    val colors = MarginTheme.colors
    MarginSheet(onDismiss = onDismiss, title = "Timetable Check", leadingText = "Close") {
        Text(
            text = "${check.issues.size} " + (if (check.issues.size == 1) "entry differs" else "entries differ") +
                " from the timetable you confirmed (${check.confirmedCount} entries).",
            style = AppleType.subheadline,
            color = colors.secondaryLabel,
            modifier = Modifier.padding(horizontal = Space.gutter + Space.l, vertical = Space.s),
        )
        GroupedSection(header = "What changed") {
            check.issues.forEachIndexed { index, issue ->
                if (index > 0) RowSeparator()
                Text(
                    text = issue.description,
                    style = AppleType.body,
                    color = colors.label,
                    modifier = Modifier.padding(horizontal = Space.l, vertical = 12.dp),
                )
            }
        }
        GroupedSection(
            modifier = Modifier.padding(top = Space.l),
            footer = "Restoring puts back the timetable you confirmed. Keeping the current one makes it the new confirmed timetable.",
        ) {
            GroupedRow(title = "Restore Confirmed Timetable", titleColor = colors.tint, onClick = onRestore)
            RowSeparator()
            GroupedRow(title = "Keep Current as Correct", onClick = onAccept)
        }
    }
}

/** Why a row deserves a second look, or null if nothing about it is doubtful. */
private fun flagFor(review: TimetableImport, index: Int): String? {
    val entry = review.entries[index]
    val overlap = review.entries.withIndex().firstOrNull { (other, candidate) ->
        other != index && candidate.dayOfWeek == entry.dayOfWeek && candidate.range.overlaps(entry.range)
    }?.value
    return when {
        overlap != null -> "Overlaps ${overlap.subjectCode ?: overlap.title} at " + "%02d:%02d".format(overlap.start / 60, overlap.start % 60)
        entry.subjectCode != null && entry.subjectCode in review.newSubjectCodes ->
            "New subject ${entry.subjectCode}. If it is one you already have, tap to pick it."
        else -> null
    }
}
