package com.margin.app.ui.exams

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.AcademicType
import com.margin.app.domain.model.Exam
import com.margin.app.domain.model.Subject
import com.margin.app.domain.planner.ExamPlanner
import com.margin.app.ui.components.CapsuleChip
import com.margin.app.ui.components.FormTextField
import com.margin.app.ui.components.GroupedRow
import com.margin.app.ui.components.GroupedSection
import com.margin.app.ui.components.IconTile
import com.margin.app.ui.components.IosSwitch
import com.margin.app.ui.components.LargeTitleScreen
import com.margin.app.ui.components.MarginSheet
import com.margin.app.ui.components.RowSeparator
import com.margin.app.ui.components.SegmentedControl
import com.margin.app.ui.components.TextAction
import com.margin.app.ui.components.TimeRow
import com.margin.app.ui.glass.GlassCircleButton
import com.margin.app.ui.glass.rememberGlassBackdrop
import com.margin.app.ui.theme.AppleType
import com.margin.app.ui.theme.MarginShape
import com.margin.app.ui.theme.MarginTheme
import com.margin.app.ui.theme.Space
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The exam timetable. Kept apart from the weekly timetable: adding exams reshapes the same
 * planner (more revision, spread across every subject) rather than switching to a different app.
 */
@Composable
fun ExamsScreen(
    viewModel: ExamsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MarginTheme.colors
    val accents = MarginTheme.accents
    val backdrop = rememberGlassBackdrop()
    val context = LocalContext.current

    var editing by remember { mutableStateOf<Exam?>(null) }
    var creating by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFrom(context.applicationContext, uri)
    }

    LargeTitleScreen(
        title = "Exams",
        eyebrow = "Exam timetable",
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
                contentDescription = "Add an exam",
                onClick = { creating = true },
            )
        },
    ) {
        item(key = "mode") {
            val pressure = ExamPlanner.pressure(state.today, state.upcoming)
            Text(
                text = when {
                    state.upcoming.isEmpty() -> "No exams coming up. The day is planned around classes as usual."
                    pressure.active -> "Exam mode is on. Revision takes more of the day, and every subject still gets its turn."
                    pressure.nearest != null -> "Exams are approaching. Revision is building up gradually."
                    else -> "Exams further than three weeks out do not change the plan yet."
                },
                style = AppleType.footnote,
                color = colors.secondaryLabel,
                modifier = Modifier.padding(horizontal = Space.gutter + 4.dp, vertical = Space.s),
            )
        }

        item(key = "upcoming") {
            GroupedSection(header = "Upcoming") {
                if (state.upcoming.isEmpty()) {
                    GroupedRow(title = "No exams yet", subtitle = "Add them one by one, or import a photo of the timetable.", titleColor = colors.secondaryLabel)
                }
                state.upcoming.forEachIndexed { index, exam ->
                    if (index > 0) RowSeparator()
                    ExamRow(exam = exam, today = state.today, use24Hour = state.use24Hour, onClick = { editing = exam })
                }
            }
        }

        item(key = "import") {
            GroupedSection(
                modifier = Modifier.padding(top = Space.xl),
                footer = state.error ?: "Every row is shown to you to check before anything is saved.",
            ) {
                GroupedRow(
                    title = if (state.importing) "Reading the timetable…" else "Import from a Photo or PDF",
                    titleColor = if (state.importing) colors.secondaryLabel else colors.tint,
                    leading = { IconTile(Icons.Rounded.DocumentScanner, accents.blue) },
                    onClick = if (state.importing) null else ({
                        viewModel.dismissError()
                        picker.launch(arrayOf("image/*", "application/pdf"))
                    }),
                )
            }
        }

        if (state.past.isNotEmpty()) {
            item(key = "past") {
                GroupedSection(header = "Past") {
                    state.past.take(MAX_PAST).forEachIndexed { index, exam ->
                        if (index > 0) RowSeparator()
                        ExamRow(exam = exam, today = state.today, use24Hour = state.use24Hour, onClick = { editing = exam })
                    }
                    RowSeparator()
                    GroupedRow(title = "Clear Past Exams", titleColor = colors.destructive, onClick = viewModel::clearPast)
                }
            }
        }
    }

    if (creating) {
        ExamEditorSheet(
            exam = null,
            subjects = state.subjects,
            use24Hour = state.use24Hour,
            onDismiss = { creating = false },
            onSave = {
                creating = false
                viewModel.save(it)
            },
            onDelete = null,
        )
    }

    editing?.let { exam ->
        ExamEditorSheet(
            exam = exam,
            subjects = state.subjects,
            use24Hour = state.use24Hour,
            onDismiss = { editing = null },
            onSave = {
                editing = null
                viewModel.save(it)
            },
            onDelete = {
                editing = null
                viewModel.delete(exam.id)
            },
        )
    }

    state.review?.let { review ->
        ExamReviewSheet(
            review = review,
            today = state.today,
            use24Hour = state.use24Hour,
            onRemove = { index -> viewModel.updateReview(review.exams.filterIndexed { i, _ -> i != index }) },
            onDismiss = viewModel::cancelImport,
            onConfirm = viewModel::confirmImport,
        )
    }
}

private const val MAX_PAST = 10

private val DateFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

private fun describe(exam: Exam, use24Hour: Boolean): String = listOfNotNull(
    exam.academicType.label,
    exam.date.format(DateFormat),
    exam.startMinute?.let { MarginTime.formatTime(it, use24Hour) },
).joinToString(" · ")

private fun countdown(today: LocalDate, exam: Exam): String {
    val days = ExamPlanner.daysUntil(today, exam)
    return when {
        days < 0 -> "Done"
        days == 0 -> "Today"
        days == 1 -> "Tomorrow"
        else -> "$days days"
    }
}

@Composable
private fun ExamRow(exam: Exam, today: LocalDate, use24Hour: Boolean, onClick: () -> Unit) {
    val colors = MarginTheme.colors
    val days = ExamPlanner.daysUntil(today, exam)
    GroupedRow(
        title = exam.title,
        subtitle = describe(exam, use24Hour),
        value = countdown(today, exam),
        titleColor = if (days < 0) colors.secondaryLabel else colors.label,
        showChevron = true,
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamEditorSheet(
    exam: Exam?,
    subjects: List<Subject>,
    use24Hour: Boolean,
    onDismiss: () -> Unit,
    onSave: (Exam) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val colors = MarginTheme.colors
    var subjectCode by remember { mutableStateOf(exam?.subjectCode) }
    var title by remember { mutableStateOf(exam?.title.orEmpty()) }
    var type by remember { mutableStateOf(exam?.academicType ?: AcademicType.THEORY) }
    var date by remember { mutableStateOf(exam?.date ?: LocalDate.now().plusDays(7)) }
    var timed by remember { mutableStateOf(exam?.startMinute != null) }
    var start by remember { mutableStateOf(exam?.startMinute ?: 10 * 60) }
    var notes by remember { mutableStateOf(exam?.notes.orEmpty()) }
    var pickingDate by remember { mutableStateOf(false) }

    val resolvedTitle = title.trim().ifBlank { subjects.firstOrNull { it.code == subjectCode }?.name.orEmpty() }

    MarginSheet(
        onDismiss = onDismiss,
        title = if (exam == null) "New Exam" else "Edit Exam",
        trailingText = "Save",
        trailingEnabled = resolvedTitle.isNotBlank(),
        onTrailing = {
            onSave(
                Exam(
                    id = exam?.id ?: 0,
                    subjectCode = subjectCode,
                    title = resolvedTitle,
                    date = date,
                    startMinute = if (timed) start else null,
                    endMinute = if (timed) exam?.endMinute?.takeIf { it > start } else null,
                    academicType = type,
                    notes = notes.trim().ifBlank { null },
                ),
            )
        },
    ) {
        if (subjects.isNotEmpty()) {
            GroupedSection(modifier = Modifier.padding(top = Space.s), header = "Subject") {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(Space.m),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    subjects.forEach { subject ->
                        CapsuleChip(
                            text = subject.shortName,
                            selected = subjectCode == subject.code,
                            onClick = {
                                subjectCode = if (subjectCode == subject.code) null else subject.code
                                if (subjectCode != null && (title.isBlank() || subjects.any { it.name == title })) {
                                    title = subject.name
                                }
                            },
                        )
                    }
                }
            }
        }

        GroupedSection(modifier = Modifier.padding(top = Space.s)) {
            FormTextField(value = title, onValueChange = { title = it }, placeholder = "Exam name")
        }

        GroupedSection(header = "Type") {
            SegmentedControl(
                options = AcademicType.entries,
                selected = type,
                label = { it.label },
                onSelect = { type = it },
                modifier = Modifier.padding(Space.m),
            )
        }

        GroupedSection(header = "When") {
            GroupedRow(
                title = "Date",
                value = date.format(DateFormat),
                showChevron = true,
                onClick = { pickingDate = true },
            )
            RowSeparator()
            GroupedRow(title = "Start time known", trailing = { IosSwitch(timed, { timed = it }) })
            if (timed) {
                RowSeparator()
                TimeRow(title = "Starts", minute = start, use24Hour = use24Hour, onChange = { start = it })
            }
        }

        GroupedSection(header = "Notes") {
            FormTextField(value = notes, onValueChange = { notes = it }, placeholder = "Syllabus, room, anything", singleLine = false, minLines = 2)
        }

        if (onDelete != null) {
            GroupedSection(modifier = Modifier.padding(top = Space.l)) {
                GroupedRow(title = "Delete Exam", titleColor = colors.destructive, onClick = onDelete)
            }
        }
    }

    if (pickingDate) {
        val todayMillis = remember { LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextAction(text = "Done", emphasized = true, onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    pickingDate = false
                })
            },
            dismissButton = { TextAction(text = "Cancel", onClick = { pickingDate = false }) },
            shape = MarginShape.card,
            colors = DatePickerDefaults.colors(containerColor = colors.surface),
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** What the import read, for the user to correct before it becomes their exam timetable. */
@Composable
private fun ExamReviewSheet(
    review: ExamImportReview,
    today: LocalDate,
    use24Hour: Boolean,
    onRemove: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = MarginTheme.colors
    MarginSheet(
        onDismiss = onDismiss,
        title = "Check the Exams",
        trailingText = if (review.exams.isEmpty()) "Add" else "Add ${review.exams.size}",
        trailingEnabled = review.exams.isNotEmpty(),
        onTrailing = onConfirm,
    ) {
        Text(
            text = "Remove anything that was misread. You can edit the rest after adding.",
            style = AppleType.subheadline,
            color = colors.secondaryLabel,
            modifier = Modifier.padding(horizontal = Space.gutter + Space.l, vertical = Space.s),
        )
        GroupedSection(
            modifier = Modifier.padding(top = Space.s),
            footer = review.warnings.takeIf { it.isNotEmpty() }?.joinToString("\n"),
        ) {
            if (review.exams.isEmpty()) {
                GroupedRow(title = "Nothing left to add", titleColor = colors.secondaryLabel)
            }
            review.exams.forEachIndexed { index, exam ->
                if (index > 0) RowSeparator()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(start = Space.l, end = Space.s, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(exam.title, style = AppleType.body, color = colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            describe(exam, use24Hour) + " · " + countdown(today, exam),
                            style = AppleType.footnote,
                            color = colors.secondaryLabel,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(Space.s))
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Remove ${exam.title}",
                        tint = colors.tertiaryLabel,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(MarginShape.capsule)
                            .clickable { onRemove(index) }
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}
