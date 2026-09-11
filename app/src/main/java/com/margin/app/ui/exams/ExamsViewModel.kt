package com.margin.app.ui.exams

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.margin.app.ai.ImageEncoder
import com.margin.app.ai.VisionImporter
import com.margin.app.data.prefs.PreferencesRepository
import com.margin.app.data.repository.ExamRepository
import com.margin.app.data.repository.TimetableRepository
import com.margin.app.di.AppContainer
import com.margin.app.domain.model.Exam
import com.margin.app.domain.model.Subject
import com.margin.app.domain.usecase.PlanningService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** An import waiting for the user to check it. Nothing is saved until they confirm. */
data class ExamImportReview(val exams: List<Exam>, val warnings: List<String>)

data class ExamsUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val upcoming: List<Exam> = emptyList(),
    val past: List<Exam> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val use24Hour: Boolean = false,
    val importing: Boolean = false,
    val review: ExamImportReview? = null,
    val error: String? = null,
)

private data class ImportState(
    val importing: Boolean = false,
    val review: ExamImportReview? = null,
    val error: String? = null,
)

class ExamsViewModel(
    private val examRepository: ExamRepository,
    private val timetableRepository: TimetableRepository,
    private val preferencesRepository: PreferencesRepository,
    private val planningService: PlanningService,
    private val visionImporter: VisionImporter,
) : ViewModel() {

    private val import = MutableStateFlow(ImportState())

    val state: StateFlow<ExamsUiState> = combine(
        examRepository.observeAll(),
        timetableRepository.observeSubjects(),
        preferencesRepository.preferences,
        import,
    ) { exams, subjects, prefs, importState ->
        val today = LocalDate.now()
        val sorted = exams.sortedWith(compareBy({ it.date }, { it.startMinute ?: 0 }, { it.id }))
        ExamsUiState(
            loading = false,
            today = today,
            upcoming = sorted.filter { !it.date.isBefore(today) },
            past = sorted.filter { it.date.isBefore(today) }.reversed(),
            subjects = subjects.filter { it.active },
            use24Hour = prefs.use24HourTime,
            importing = importState.importing,
            review = importState.review,
            error = importState.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExamsUiState())

    fun save(exam: Exam) = viewModelScope.launch {
        examRepository.upsert(exam)
        replan()
    }

    fun delete(id: Long) = viewModelScope.launch {
        examRepository.delete(id)
        replan()
    }

    fun clearPast() = viewModelScope.launch {
        examRepository.clearBefore(LocalDate.now())
    }

    /** Reads an exam timetable from a photo or PDF. The result is shown for review, not saved. */
    fun importFrom(context: Context, uri: Uri) = viewModelScope.launch {
        import.value = ImportState(importing = true)
        val image = ImageEncoder.encode(context, uri)
        if (image == null) {
            import.value = ImportState(error = "That file could not be opened. Try a photo or a PDF.")
            return@launch
        }
        val subjects = timetableRepository.allSubjects()
        import.value = when (val outcome = visionImporter.readExams(image, subjects, LocalDate.now())) {
            is VisionImporter.ExamOutcome.Success -> ImportState(review = ExamImportReview(outcome.exams, outcome.warnings))
            is VisionImporter.ExamOutcome.Failed -> ImportState(error = outcome.message)
        }
    }

    fun updateReview(exams: List<Exam>) {
        import.update { current -> current.copy(review = current.review?.copy(exams = exams)) }
    }

    fun confirmImport() = viewModelScope.launch {
        val review = import.value.review ?: return@launch
        import.value = ImportState()
        if (review.exams.isEmpty()) return@launch
        examRepository.addAll(review.exams)
        replan()
    }

    fun cancelImport() {
        import.value = ImportState()
    }

    fun dismissError() {
        import.update { it.copy(error = null) }
    }

    private suspend fun replan() {
        runCatching { planningService.replan(LocalDate.now()) }
        runCatching { planningService.replan(LocalDate.now().plusDays(1)) }
    }

    companion object {
        fun create(container: AppContainer) = ExamsViewModel(
            examRepository = container.examRepository,
            timetableRepository = container.timetableRepository,
            preferencesRepository = container.preferencesRepository,
            planningService = container.planningService,
            visionImporter = container.visionImporter,
        )
    }
}
