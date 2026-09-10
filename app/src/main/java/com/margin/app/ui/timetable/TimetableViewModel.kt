package com.margin.app.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.margin.app.data.prefs.PreferencesRepository
import com.margin.app.data.repository.TimetableRepository
import com.margin.app.di.AppContainer
import com.margin.app.domain.model.ExceptionType
import com.margin.app.domain.model.Routine
import com.margin.app.domain.model.Subject
import com.margin.app.domain.model.TimetableEntry
import com.margin.app.domain.model.TimetableException
import com.margin.app.domain.usecase.PlanningService
import com.margin.app.domain.usecase.SeedService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

data class TimetableUiState(
    val selectedDay: DayOfWeek = LocalDate.now().dayOfWeek,
    val entriesByDay: Map<DayOfWeek, List<TimetableEntry>> = emptyMap(),
    val subjects: List<Subject> = emptyList(),
    val routines: List<Routine> = emptyList(),
    val upcomingExceptions: List<TimetableException> = emptyList(),
    val use24Hour: Boolean = false,
    val loading: Boolean = true,
) {
    val entriesForSelected: List<TimetableEntry>
        get() = entriesByDay[selectedDay].orEmpty().sortedBy { it.start }

    fun teachingMinutes(day: DayOfWeek): Int =
        entriesByDay[day].orEmpty().filter { it.kind.isTeaching }.sumOf { it.range.duration }
}

class TimetableViewModel(
    private val timetableRepository: TimetableRepository,
    private val preferencesRepository: PreferencesRepository,
    private val planningService: PlanningService,
    private val seedService: SeedService,
) : ViewModel() {

    private val selectedDay = MutableStateFlow(LocalDate.now().dayOfWeek)

    val state: StateFlow<TimetableUiState> = combine(
        timetableRepository.observeEntries(),
        timetableRepository.observeSubjects(),
        timetableRepository.observeRoutines(),
        timetableRepository.observeExceptionsFrom(LocalDate.now()),
        combine(preferencesRepository.preferences, selectedDay) { prefs, day -> prefs to day },
    ) { entries, subjects, routines, exceptions, (prefs, day) ->
        TimetableUiState(
            selectedDay = day,
            entriesByDay = entries.groupBy { it.dayOfWeek },
            subjects = subjects,
            routines = routines,
            upcomingExceptions = exceptions.sortedBy { it.date },
            use24Hour = prefs.use24HourTime,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimetableUiState())

    fun selectDay(day: DayOfWeek) {
        selectedDay.value = day
    }

    fun saveEntry(entry: TimetableEntry) = viewModelScope.launch {
        timetableRepository.upsertEntry(entry)
        planningService.replan(LocalDate.now())
    }

    fun deleteEntry(entry: TimetableEntry) = viewModelScope.launch {
        timetableRepository.deleteEntry(entry)
        planningService.replan(LocalDate.now())
    }

    fun cancelOn(date: LocalDate, entryId: Long) = viewModelScope.launch {
        timetableRepository.cancelClass(date, entryId)
        planningService.replan(date)
    }

    fun markHoliday(date: LocalDate, note: String?) = viewModelScope.launch {
        timetableRepository.markHoliday(date, note)
        planningService.replan(date)
    }

    fun removeException(exception: TimetableException) = viewModelScope.launch {
        timetableRepository.removeException(exception.id)
        planningService.replan(exception.date)
    }

    fun addExtraClass(
        date: LocalDate,
        title: String,
        subjectCode: String?,
        start: Int,
        end: Int,
    ) = viewModelScope.launch {
        timetableRepository.addException(
            TimetableException(
                date = date,
                type = ExceptionType.EXTRA,
                title = title,
                subjectCode = subjectCode,
                start = start,
                end = end,
            ),
        )
        planningService.replan(date)
    }

    fun saveSubject(subject: Subject) = viewModelScope.launch {
        timetableRepository.upsertSubject(subject)
        planningService.replan(LocalDate.now())
    }

    fun saveRoutine(routine: Routine) = viewModelScope.launch {
        timetableRepository.upsertRoutine(routine)
        planningService.replan(LocalDate.now())
    }

    fun deleteRoutine(id: Long) = viewModelScope.launch {
        timetableRepository.deleteRoutine(id)
        planningService.replan(LocalDate.now())
    }

    fun restoreSeed() = viewModelScope.launch {
        seedService.resetToSeed()
        planningService.replan(LocalDate.now())
    }

    companion object {
        fun create(container: AppContainer) = TimetableViewModel(
            timetableRepository = container.timetableRepository,
            preferencesRepository = container.preferencesRepository,
            planningService = container.planningService,
            seedService = container.seedService,
        )
    }
}
