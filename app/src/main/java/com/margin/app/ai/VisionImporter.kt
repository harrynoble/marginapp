package com.margin.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.margin.app.core.MarginTime
import com.margin.app.data.prefs.AiSettingsRepository
import com.margin.app.domain.model.AcademicType
import com.margin.app.domain.model.Exam
import com.margin.app.domain.model.Subject
import com.margin.app.domain.model.TimetableEntry
import com.margin.app.domain.model.TimetableKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Reads a timetable or an exam timetable from a photo or a PDF. The model only extracts; every
 * row is then checked here, and the result goes to a review screen where the user corrects it
 * before anything is saved. OCR and models misread tables, so nothing here is trusted blindly.
 */
class VisionImporter(private val aiSettingsRepository: AiSettingsRepository) {

    sealed interface TimetableOutcome {
        data class Success(val import: TimetableImport) : TimetableOutcome
        data class Failed(val message: String) : TimetableOutcome
    }

    sealed interface ExamOutcome {
        data class Success(val exams: List<Exam>, val warnings: List<String>) : ExamOutcome
        data class Failed(val message: String) : ExamOutcome
    }

    suspend fun readTimetable(image: EncodedImage, known: List<Subject>): TimetableOutcome {
        val provider = AiProviderFactory.create(aiSettingsRepository.current())
            ?: return TimetableOutcome.Failed(NO_KEY)
        return when (val outcome = provider.completeWithImage(TIMETABLE_PROMPT, "Extract this timetable.", image)) {
            is AiOutcome.Failure -> TimetableOutcome.Failed(outcome.message)
            is AiOutcome.Success -> {
                val payload = JsonText.firstObject(outcome.text)
                    ?: return TimetableOutcome.Failed("The timetable could not be read. Try a clearer, straight-on photo.")
                val dto = runCatching { json.decodeFromString<TimetableDto>(payload) }.getOrNull()
                    ?: return TimetableOutcome.Failed("The timetable could not be read. Try a clearer, straight-on photo.")
                val result = TimetableImportValidator.validate(dto.entries.map { it.toRaw() }, dto.subjects.map { it.code to it.name }, known)
                if (result.entries.isEmpty()) {
                    TimetableOutcome.Failed("No classes could be read from that image.")
                } else {
                    TimetableOutcome.Success(result)
                }
            }
        }
    }

    suspend fun readExams(image: EncodedImage, known: List<Subject>, today: LocalDate): ExamOutcome {
        val provider = AiProviderFactory.create(aiSettingsRepository.current())
            ?: return ExamOutcome.Failed(NO_KEY)
        val prompt = EXAM_PROMPT + "\nToday is $today. Use the next occurrence of any date without a year."
        return when (val outcome = provider.completeWithImage(prompt, "Extract this exam timetable.", image)) {
            is AiOutcome.Failure -> ExamOutcome.Failed(outcome.message)
            is AiOutcome.Success -> {
                val payload = JsonText.firstObject(outcome.text)
                    ?: return ExamOutcome.Failed("The exam timetable could not be read.")
                val dto = runCatching { json.decodeFromString<ExamsDto>(payload) }.getOrNull()
                    ?: return ExamOutcome.Failed("The exam timetable could not be read.")
                val warnings = mutableListOf<String>()
                val exams = dto.exams.mapNotNull { row ->
                    val date = CommandValidator.parseDate(row.date, today)
                    if (date == null || date.isBefore(today.minusDays(1)) || date.isAfter(today.plusDays(365))) {
                        warnings += "Skipped a row with an unreadable date: ${row.subjectName ?: row.subjectCode ?: "unknown"}"
                        return@mapNotNull null
                    }
                    val subject = SubjectMatcher.match(row.subjectCode, known) ?: SubjectMatcher.match(row.subjectName, known)
                    val start = row.start?.let { MarginTime.parseTime(it) }
                    val end = row.end?.let { MarginTime.parseTime(it) }
                    Exam(
                        subjectCode = subject?.code ?: row.subjectCode?.trim()?.uppercase()?.take(10),
                        title = subject?.name ?: row.subjectName?.trim().orEmpty().ifBlank { row.subjectCode ?: "Exam" },
                        date = date,
                        startMinute = start,
                        endMinute = end?.takeIf { start == null || it > start },
                        academicType = if (row.type?.contains("lab", ignoreCase = true) == true) AcademicType.LAB else AcademicType.THEORY,
                    )
                }.sortedWith(compareBy({ it.date }, { it.startMinute ?: 0 }))
                if (exams.isEmpty()) ExamOutcome.Failed("No exams could be read from that image.") else ExamOutcome.Success(exams, warnings)
            }
        }
    }

    // ---- wire shapes ---------------------------------------------------------------------------

    @Serializable
    private data class TimetableDto(
        val entries: List<EntryDto> = emptyList(),
        val subjects: List<SubjectDto> = emptyList(),
    )

    @Serializable
    private data class EntryDto(
        val day: String = "",
        val start: String = "",
        val end: String = "",
        @SerialName("subject_code") val subjectCode: String? = null,
        val title: String = "",
        val kind: String = "lecture",
        val faculty: String? = null,
        val location: String? = null,
    ) {
        fun toRaw() = RawEntry(day, start, end, subjectCode, title, kind, faculty, location)
    }

    @Serializable
    private data class SubjectDto(val code: String = "", val name: String = "")

    @Serializable
    private data class ExamsDto(val exams: List<ExamDto> = emptyList())

    @Serializable
    private data class ExamDto(
        @SerialName("subject_code") val subjectCode: String? = null,
        @SerialName("subject_name") val subjectName: String? = null,
        val date: String = "",
        val start: String? = null,
        val end: String? = null,
        val type: String? = null,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

        const val NO_KEY = "Reading a timetable from an image needs an assistant key. Add one in " +
            "Settings, or enter the classes by hand."

        private val TIMETABLE_PROMPT = """
You extract a weekly college timetable from an image. You do not plan anything.
Return JSON only, no prose, no code fences:
{"entries":[{"day":"Mon","start":"08:00","end":"08:55","subject_code":"DSA","title":"Data Structures and Algorithms","kind":"lecture","faculty":"","location":""}],
 "subjects":[{"code":"DSA","name":"Data Structures and Algorithms"}]}
Rules:
- One entry per class slot. Times are 24-hour HH:MM.
- kind is one of: lecture, lab, tutorial, recess. Practicals and labs are "lab"; keep a lab and
  the theory lecture of the same subject as separate entries.
- A lab or session spanning several periods is ONE entry covering the whole span.
- Include breaks printed on the timetable as kind "recess".
- Use subject codes exactly as printed. Never invent a class that is not on the image.
""".trim()

        private val EXAM_PROMPT = """
You extract an exam timetable from an image. You do not plan anything.
Return JSON only, no prose, no code fences:
{"exams":[{"subject_code":"DSA","subject_name":"Data Structures","date":"YYYY-MM-DD","start":"10:00","end":"13:00","type":"theory"}]}
type is "theory" or "lab". Leave start and end out if they are not printed. Never invent an exam.
""".trim()
    }
}

/** One row as the model returned it, before any checking. */
data class RawEntry(
    val day: String,
    val start: String,
    val end: String,
    val subjectCode: String?,
    val title: String,
    val kind: String,
    val faculty: String?,
    val location: String?,
)

data class TimetableImport(
    val entries: List<TimetableEntry>,
    val subjects: List<Subject>,
    val warnings: List<String>,
    /** Subject codes not already in the app. The review screen asks about each one. */
    val newSubjectCodes: Set<String> = emptySet(),
)

/**
 * Subject identity for imported timetables. Printed codes carry their type ("EE(T)" is an
 * Economics tutorial); a code is matched to an existing subject only on an exact code, short
 * name or full name, never on a loose resemblance.
 */
object SubjectCodes {

    data class Split(val code: String?, val kind: TimetableKind?)

    private val SUFFIX = Regex("""^(.*?)\s*[(\[]\s*(T|TUT|TUTORIAL|P|PR|PRACTICAL|L|LAB)\s*[)\]]\s*$""", RegexOption.IGNORE_CASE)
    private val TITLE_SUFFIX = Regex("""\s*[(\[]\s*(T|TUT|TUTORIAL)\s*[)\]]\s*$""", RegexOption.IGNORE_CASE)

    fun split(raw: String?): Split {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return Split(null, null)
        val match = SUFFIX.matchEntire(text)
            ?: return Split(text.replace(" ", "").uppercase(), null)
        val base = match.groupValues[1].replace(" ", "").uppercase().ifBlank { null }
        val kind = when (match.groupValues[2].uppercase()) {
            "T", "TUT", "TUTORIAL" -> TimetableKind.TUTORIAL
            else -> TimetableKind.LAB
        }
        return Split(base, kind)
    }

    fun cleanTitle(raw: String): String = raw.replace(TITLE_SUFFIX, "").trim()

    fun resolve(code: String, title: String, known: List<Subject>): Subject? {
        fun norm(value: String) = value.lowercase().replace(Regex("[^a-z0-9]"), "")
        known.firstOrNull { norm(it.code) == norm(code) }?.let { return it }
        known.firstOrNull { norm(it.shortName) == norm(code) }?.let { return it }
        if (title.isNotBlank()) known.firstOrNull { norm(it.name) == norm(title) }?.let { return it }
        return null
    }
}

/**
 * Checks every extracted row: a real day, readable times in a plausible order, a sane length,
 * a known kind. Rows that fail are dropped with a warning the user sees, never saved silently.
 * Pure, so it is unit tested.
 */
object TimetableImportValidator {

    fun validate(
        rows: List<RawEntry>,
        subjectNames: List<Pair<String, String>>,
        known: List<Subject>,
    ): TimetableImport {
        val warnings = mutableListOf<String>()
        val entries = mutableListOf<TimetableEntry>()
        for (row in rows) {
            val day = parseDay(row.day)
            val start = collegeTime(row.start)
            val end = collegeTime(row.end)
            val label = row.title.ifBlank { row.subjectCode ?: "a class" }
            when {
                day == null -> warnings += "Skipped $label: unreadable day \"${row.day}\"."
                start == null || end == null -> warnings += "Skipped $label on ${day.name.lowercase()}: unreadable time."
                end <= start -> warnings += "Skipped $label: it ends before it starts."
                end - start > MAX_MINUTES -> warnings += "Skipped $label: longer than six hours."
                else -> {
                    // "EE(T)" is Economics as a tutorial, not a new subject called "EE(T)".
                    val split = SubjectCodes.split(row.subjectCode)
                    var kind = parseKind(row.kind)
                    if (split.kind != null && kind == TimetableKind.LECTURE) kind = split.kind
                    val title = SubjectCodes.cleanTitle(row.title)
                    val code = split.code?.takeIf { kind != TimetableKind.RECESS }
                        ?.let { raw -> SubjectCodes.resolve(raw, title, known)?.code ?: raw.take(10) }
                    entries += TimetableEntry(
                        dayOfWeek = day,
                        start = start,
                        end = end,
                        subjectCode = code,
                        title = title.ifBlank { if (kind == TimetableKind.RECESS) "Recess" else code ?: "Class" },
                        kind = kind,
                        faculty = row.faculty?.trim()?.ifBlank { null },
                        location = row.location?.trim()?.ifBlank { null },
                    )
                }
            }
        }

        // The same slot read twice is kept once.
        val unique = entries
            .distinctBy { Triple(it.dayOfWeek, it.start, it.subjectCode ?: it.title) }
            .sortedWith(compareBy({ it.dayOfWeek.value }, { it.start }))

        val codes = unique.mapNotNull { it.subjectCode }.toSet()
        val names = subjectNames
            .mapNotNull { (code, name) -> SubjectCodes.split(code).code?.let { it to SubjectCodes.cleanTitle(name) } }
            .toMap()
        val subjects = codes.sorted().mapIndexed { index, code ->
            known.firstOrNull { it.code == code } ?: Subject(
                code = code,
                name = names[code]?.ifBlank { null }
                    ?: unique.firstOrNull { it.subjectCode == code && it.kind != TimetableKind.LAB }?.title
                    ?: code,
                shortName = code,
                colorIndex = index,
            )
        }
        // Subjects the app has never seen are flagged for the user rather than merged with a
        // lookalike: "Economics" and "Engineering Economics" may or may not be the same course.
        val newCodes = codes.filter { code -> known.none { it.code == code } }.toSet()
        return TimetableImport(unique, subjects, warnings, newCodes)
    }

    fun parseDay(raw: String): DayOfWeek? {
        val text = raw.trim().lowercase()
        if (text.isEmpty()) return null
        text.toIntOrNull()?.let { return if (it in 1..7) DayOfWeek.of(it) else null }
        return DayOfWeek.entries.firstOrNull { it.name.lowercase().startsWith(text.take(3)) && text.length >= 2 }
    }

    /** College runs in the day: a bare "1:40" on a timetable is 13:40, not the small hours. */
    fun collegeTime(raw: String): Int? {
        val minute = MarginTime.parseTime(raw) ?: return null
        val explicit = raw.lowercase().let { it.contains("am") || it.contains("pm") }
        return if (!explicit && minute < 7 * 60 && minute >= 60) minute + 12 * 60 else minute
    }

    fun parseKind(raw: String): TimetableKind {
        val text = raw.trim().lowercase()
        return when {
            text.contains("lab") || text.contains("practical") || text == "pr" -> TimetableKind.LAB
            text.contains("tut") -> TimetableKind.TUTORIAL
            text.contains("recess") || text.contains("break") || text.contains("lunch") -> TimetableKind.RECESS
            text.contains("mentor") -> TimetableKind.MENTORING
            text.contains("department") -> TimetableKind.DEPARTMENT
            else -> TimetableKind.LECTURE
        }
    }

    private const val MAX_MINUTES = 6 * 60
}

/** Turns a picked image or PDF into something small enough to send. */
object ImageEncoder {

    private const val MAX_EDGE = 1600

    suspend fun encode(context: Context, uri: Uri): EncodedImage? = withContext(Dispatchers.IO) {
        runCatching {
            val type = context.contentResolver.getType(uri).orEmpty()
            val bitmap = if (type == "application/pdf") renderPdf(context, uri) else decode(context, uri)
            bitmap?.let { toJpeg(it) }
        }.getOrNull()
    }

    private fun decode(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun renderPdf(context: Context, uri: Uri): Bitmap? {
        val descriptor: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        descriptor.use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val scale = MAX_EDGE.toFloat() / maxOf(page.width, page.height)
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    Canvas(bitmap).drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }

    private fun toJpeg(source: Bitmap): EncodedImage {
        val scale = MAX_EDGE.toFloat() / maxOf(source.width, source.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
        } else {
            source
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return EncodedImage(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP), "image/jpeg")
    }
}
