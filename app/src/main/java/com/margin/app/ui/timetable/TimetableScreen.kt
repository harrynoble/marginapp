package com.margin.app.ui.timetable

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.ExceptionType
import com.margin.app.domain.model.Subject
import com.margin.app.domain.model.TimetableEntry
import com.margin.app.domain.model.TimetableException
import com.margin.app.domain.model.TimetableKind
import com.margin.app.ui.components.CategoryRail
import com.margin.app.ui.components.ColorDot
import com.margin.app.ui.components.GroupedRow
import com.margin.app.ui.components.GroupedSection
import com.margin.app.ui.components.IconTile
import com.margin.app.ui.components.IosSwitch
import com.margin.app.ui.components.LargeTitleScreen
import com.margin.app.ui.components.RowSeparator
import com.margin.app.ui.components.SegmentedControl
import com.margin.app.ui.components.TextAction
import com.margin.app.ui.glass.GlassCircleButton
import com.margin.app.ui.glass.rememberGlassBackdrop
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginShape
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space
import com.margin.app.ui.theme.subjectAccent
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/** Separators start where the class title does, past the time column and the rail. */
private val EntryInset = Space.l + 76.dp + 4.dp + Space.m

/**
 * The recurring week, reached from Settings. It is already filled in from the timetable the
 * app shipped with; this screen exists for the day something changes, and otherwise stays out
 * of the way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MarginTheme.colors
    val accents = MarginTheme.accents
    val backdrop = rememberGlassBackdrop()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<TimetableEntry?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pickingDayOff by remember { mutableStateOf(false) }
    var editingSubject by remember { mutableStateOf<Subject?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFrom(context.applicationContext, uri)
    }

    val entries = state.entriesForSelected
    val allEntries = remember(state.entriesByDay) { state.entriesByDay.values.flatten() }

    LargeTitleScreen(
        title = "Timetable",
        eyebrow = "College",
        backdrop = backdrop,
        bottomClearance = Space.xxxl,
        modifier = modifier,
        navigation = {
            GlassCircleButton(
                backdrop = backdrop,
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = "Back",
                onClick = onBack,
                iconSize = 30.dp,
            )
        },
        actions = {
            GlassCircleButton(
                backdrop = backdrop,
                icon = Icons.Rounded.Add,
                contentDescription = "Add a class",
                onClick = { creating = true },
            )
        },
    ) {
        item(key = "days") {
            SegmentedControl(
                options = DayOfWeek.entries,
                selected = state.selectedDay,
                label = { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3) },
                onSelect = viewModel::selectDay,
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.xs),
            )
        }

        item(key = "summary") {
            val classes = entries.count { it.kind.isTeaching }
            Text(
                text = if (classes == 0) {
                    "No classes"
                } else {
                    "$classes ${if (classes == 1) "class" else "classes"} · " +
                        MarginTime.formatDuration(state.teachingMinutes(state.selectedDay)) + " of teaching"
                },
                style = AppleType.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(horizontal = Space.gutter + 4.dp, vertical = Space.s),
            )
        }

        item(key = "entries") {
            GroupedSection {
                if (entries.isEmpty()) {
                    GroupedRow(
                        title = "Nothing on this day",
                        subtitle = "Leave it clear for your own work, or add a class.",
                        titleColor = colors.secondaryLabel,
                    )
                } else {
                    entries.forEachIndexed { index, entry ->
                        if (index > 0) RowSeparator(inset = EntryInset)
                        EntryRow(
                            entry = entry,
                            subjectIndex = state.subjects.indexOfFirst { it.code == entry.subjectCode },
                            use24Hour = state.use24Hour,
                            onClick = { editing = entry },
                        )
                    }
                }
            }
        }

        if (state.upcomingExceptions.isNotEmpty()) {
            item(key = "exceptions") {
                GroupedSection(header = "Changes ahead") {
                    state.upcomingExceptions.forEachIndexed { index, exception ->
                        if (index > 0) RowSeparator()
                        GroupedRow(
                            title = MarginTime.dayLabel(exception.date),
                            subtitle = describe(exception, allEntries, state.use24Hour),
                            trailing = { TextAction(text = "Undo", onClick = { viewModel.removeException(exception) }) },
                        )
                    }
                }
            }
        }

        item(key = "day-off") {
            GroupedSection(
                modifier = Modifier.padding(top = Space.xl),
                footer = "A day off clears college for that date only. The rest of the week is untouched.",
            ) {
                GroupedRow(
                    title = "Add a Day Off",
                    titleColor = colors.tint,
                    leading = { IconTile(Icons.Rounded.BeachAccess, accents.green) },
                    onClick = { pickingDayOff = true },
                )
            }
        }

        item(key = "import") {
            GroupedSection(
                footer = state.import.error
                    ?: "New semester? Read the timetable from a photo or PDF. You check every class before it replaces this one.",
            ) {
                GroupedRow(
                    title = if (state.import.importing) "Reading the timetable…" else "Import from a Photo or PDF",
                    titleColor = if (state.import.importing) colors.secondaryLabel else colors.tint,
                    leading = { IconTile(Icons.Rounded.DocumentScanner, accents.blue) },
                    onClick = if (state.import.importing) null else ({ picker.launch(arrayOf("image/*", "application/pdf")) }),
                )
            }
        }

        if (state.subjects.isNotEmpty()) {
            item(key = "subjects") {
                GroupedSection(
                    header = "Subjects",
                    footer = "Theory and lab are tracked separately, so neither is forgotten. Tap a subject to set how much it matters.",
                ) {
                    state.subjects.forEachIndexed { index, subject ->
                        if (index > 0) RowSeparator(inset = Space.l + 12.dp + Space.m)
                        GroupedRow(
                            title = subject.name,
                            subtitle = listOf(
                                subject.code,
                                ImportanceLabels[subject.importance.coerceIn(0, 3)],
                                subject.difficulty.label,
                            ).joinToString(" · "),
                            leading = { ColorDot(color = subjectAccent(index), size = 12.dp) },
                            showChevron = true,
                            onClick = { editingSubject = subject },
                        )
                    }
                }
            }
        }

        if (state.routines.isNotEmpty()) {
            item(key = "routines") {
                GroupedSection(
                    header = "Routines",
                    footer = "Meals are off by default: eat when it suits you, and take a meal break from Today. " +
                        "Turn one on to have its time kept free.",
                ) {
                    state.routines.forEachIndexed { index, routine ->
                        if (index > 0) RowSeparator()
                        GroupedRow(
                            title = routine.title,
                            subtitle = MarginTime.formatTime(routine.start, state.use24Hour) + " – " +
                                MarginTime.formatTime(routine.end, state.use24Hour),
                            trailing = {
                                IosSwitch(
                                    checked = routine.active,
                                    onCheckedChange = { viewModel.saveRoutine(routine.copy(active = it)) },
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        TimetableEntrySheet(
            entry = null,
            day = state.selectedDay,
            subjects = state.subjects,
            use24Hour = state.use24Hour,
            onDismiss = { creating = false },
            onSave = {
                viewModel.saveEntry(it)
                creating = false
            },
            onDelete = null,
        )
    }

    editing?.let { entry ->
        val today = LocalDate.now()
        TimetableEntrySheet(
            entry = entry,
            day = entry.dayOfWeek,
            subjects = state.subjects,
            use24Hour = state.use24Hour,
            onDismiss = { editing = null },
            onSave = {
                viewModel.saveEntry(it)
                editing = null
            },
            onDelete = {
                viewModel.deleteEntry(entry)
                editing = null
            },
            // Only offered when the class actually happens today.
            onCancelToday = if (entry.dayOfWeek == today.dayOfWeek) {
                {
                    viewModel.cancelOn(today, entry.id)
                    editing = null
                }
            } else {
                null
            },
        )
    }

    editingSubject?.let { subject ->
        SubjectSheet(
            subject = subject,
            onDismiss = { editingSubject = null },
            onSave = {
                editingSubject = null
                viewModel.saveSubject(it)
            },
        )
    }

    state.import.review?.let { review ->
        TimetableReviewSheet(
            review = review,
            use24Hour = state.use24Hour,
            onRemove = viewModel::removeFromReview,
            onDismiss = viewModel::cancelImport,
            onConfirm = viewModel::confirmImport,
        )
    }

    if (pickingDayOff) {
        val todayMillis = remember {
            LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = todayMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDayOff = false },
            confirmButton = {
                TextAction(text = "Add", emphasized = true, onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.markHoliday(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(), null)
                    }
                    pickingDayOff = false
                })
            },
            dismissButton = { TextAction(text = "Cancel", onClick = { pickingDayOff = false }) },
            shape = MarginShape.card,
            colors = DatePickerDefaults.colors(containerColor = colors.surface),
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun describe(exception: TimetableException, entries: List<TimetableEntry>, use24Hour: Boolean): String =
    when (exception.type) {
        ExceptionType.HOLIDAY -> listOfNotNull("Day off", exception.note).joinToString(" · ")
        ExceptionType.CANCELLED -> {
            val title = entries.firstOrNull { it.id == exception.entryId }?.title
            listOfNotNull(if (title != null) "$title cancelled" else "Class cancelled", exception.note)
                .joinToString(" · ")
        }
        ExceptionType.EXTRA -> listOfNotNull(
            "Extra: " + (exception.title ?: exception.subjectCode ?: "class"),
            MarginTime.formatTime(exception.start, use24Hour),
        ).joinToString(" · ")
    }

@Composable
private fun EntryRow(
    entry: TimetableEntry,
    subjectIndex: Int,
    use24Hour: Boolean,
    onClick: () -> Unit,
) {
    val colors = MarginTheme.colors
    val recess = entry.kind == TimetableKind.RECESS
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = Space.l, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = MarginTime.formatTime(entry.start, use24Hour),
            style = AppleType.timeGutter,
            color = colors.secondaryLabel,
            maxLines = 1,
            modifier = Modifier.width(76.dp),
        )
        CategoryRail(color = if (recess || subjectIndex < 0) colors.tertiaryLabel else subjectAccent(subjectIndex))
        Spacer(Modifier.width(Space.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = AppleType.body,
                color = if (recess) colors.secondaryLabel else colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                entry.subjectCode?.takeIf { it != entry.title },
                entry.kind.label.takeIf {
                    entry.kind != TimetableKind.LECTURE && !it.equals(entry.title, ignoreCase = true)
                },
                entry.faculty,
                entry.location,
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = AppleType.footnote,
                    color = colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(Space.s))
        Text(
            text = MarginTime.formatDurationShort(entry.range.duration),
            style = AppleType.subheadline.copy(fontFeatureSettings = "tnum"),
            color = colors.secondaryLabel,
            maxLines = 1,
        )
    }
}
