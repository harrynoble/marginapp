package com.margin.app.data.seed

import com.margin.app.domain.model.Category
import com.margin.app.domain.model.Routine
import com.margin.app.domain.model.RoutineKind
import com.margin.app.domain.model.Subject
import com.margin.app.domain.model.TimetableEntry
import com.margin.app.domain.model.TimetableKind
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY

/**
 * The timetable this app was built around: MITS Kochi, S3 CS (A), Jul to Dec 2026.
 *
 * It is seeded on first run so the app already knows the week and never asks for it.
 * Everything here is fully editable in the Timetable screen, and clearing the seed is a
 * single action in Settings, so this is a starting point rather than a hard-coded schedule.
 *
 * Mon to Thu period grid:
 *   1  08:00-08:55   recess 08:55-09:15   2  09:15-10:05   3  10:05-10:55
 *   4  10:55-11:45   recess 11:45-12:00   5  12:00-12:50   6  12:50-13:40
 * Friday runs on its own timings.
 */
object TimetableSeed {

    private fun at(hour: Int, minute: Int) = hour * 60 + minute

    // Mon to Thu period boundaries.
    private val P1 = at(8, 0) to at(8, 55)
    private val RECESS_1 = at(8, 55) to at(9, 15)
    private val P2 = at(9, 15) to at(10, 5)
    private val P3 = at(10, 5) to at(10, 55)
    private val P4 = at(10, 55) to at(11, 45)
    private val RECESS_2 = at(11, 45) to at(12, 0)
    private val P5 = at(12, 0) to at(12, 50)
    private val P6 = at(12, 50) to at(13, 40)

    /** Monday and Wednesday afternoons are a single three-period lab. */
    private val LAB_BLOCK = at(10, 55) to at(13, 40)

    val subjects: List<Subject> = listOf(
        Subject("DSA", "Data Structures and Algorithms", "DSA", reviewWeight = 1.35f, colorIndex = 0),
        Subject("DELD", "Digital Electronics and Logic Design", "DELD", reviewWeight = 1.15f, colorIndex = 1),
        Subject("ToC", "Theory of Computation", "ToC", reviewWeight = 1.2f, colorIndex = 2),
        Subject("FP", "Functional Programming", "FP", reviewWeight = 1.0f, colorIndex = 3),
        Subject("MIS3", "Mathematics for Information Science 3", "Maths", reviewWeight = 1.15f, colorIndex = 4),
        Subject("EE", "Engineering Economics", "Econ", reviewWeight = 0.6f, colorIndex = 5),
    )

    val entries: List<TimetableEntry> = buildList {
        // ---- Monday ---------------------------------------------------------------------
        add(lecture(MONDAY, P1, "FP", "Functional Programming", "Leda Kamal"))
        add(recess(MONDAY, RECESS_1))
        add(lecture(MONDAY, P2, "ToC", "Theory of Computation", "Jisha James"))
        add(tutorial(MONDAY, P3, "MIS3", "Maths for Information Science 3", "Sneha Sebastian, New faculty"))
        add(lab(MONDAY, LAB_BLOCK, "DELD", "Digital Electronics lab", "Dr. Ameena Ashraf, Dr. Rajesh Cherian Roy, Sheena K V"))

        // ---- Tuesday --------------------------------------------------------------------
        add(lecture(TUESDAY, P1, "MIS3", "Maths for Information Science 3", "Sneha Sebastian"))
        add(recess(TUESDAY, RECESS_1))
        add(tutorial(TUESDAY, P2, "EE", "Engineering Economics", "Babitha George, Maria Mathew"))
        add(lecture(TUESDAY, P3, "DSA", "Data Structures and Algorithms", "Basil Baby"))
        add(lecture(TUESDAY, P4, "ToC", "Theory of Computation", "Jisha James, Sneha Sebastian, Neethu Krishna"))
        add(recess(TUESDAY, RECESS_2))
        add(tutorial(TUESDAY, P5, "DELD", "Digital Electronics and Logic Design", "Dr. Ameena Ashraf, Sreetha V Kumar S"))
        add(lecture(TUESDAY, P6, "FP", "Functional Programming", "Leda Kamal"))

        // ---- Wednesday ------------------------------------------------------------------
        add(lecture(WEDNESDAY, P1, "DELD", "Digital Electronics and Logic Design", "Dr. Ameena Ashraf"))
        add(recess(WEDNESDAY, RECESS_1))
        add(lecture(WEDNESDAY, P2, "FP", "Functional Programming", "Leda Kamal"))
        add(mentoring(WEDNESDAY, P3))
        add(lab(WEDNESDAY, LAB_BLOCK, "DSA", "Data Structures lab", "Basil Baby, Anu Jose, Jency Thomas"))

        // ---- Thursday -------------------------------------------------------------------
        add(lecture(THURSDAY, P1, "ToC", "Theory of Computation", "Jisha James"))
        add(recess(THURSDAY, RECESS_1))
        add(lecture(THURSDAY, P2, "DELD", "Digital Electronics and Logic Design", "Dr. Ameena Ashraf"))
        add(lecture(THURSDAY, P3, "DSA", "Data Structures and Algorithms", "Basil Baby"))
        add(lecture(THURSDAY, P4, "EE", "Engineering Economics", "Babitha George"))
        add(recess(THURSDAY, RECESS_2))
        add(lecture(THURSDAY, P5, "MIS3", "Maths for Information Science 3", "Sneha Sebastian"))
        add(lecture(THURSDAY, P6, "FP", "Functional Programming", "Leda Kamal"))

        // ---- Friday, on its own timings --------------------------------------------------
        add(tutorial(FRIDAY, at(8, 0) to at(8, 55), "DSA", "Data Structures and Algorithms", "Basil Baby, Dr. Abdul Ali"))
        add(tutorial(FRIDAY, at(8, 55) to at(9, 50), "ToC", "Theory of Computation", "Jisha James, Sneha Sebastian"))
        add(recess(FRIDAY, at(9, 50) to at(10, 10)))
        add(lecture(FRIDAY, at(10, 10) to at(11, 0), "EE", "Engineering Economics", "Babitha George"))
        add(departmentHour(FRIDAY, at(11, 0) to at(11, 50)))
        add(lecture(FRIDAY, at(11, 50) to at(12, 40), "MIS3", "Maths for Information Science 3", "Sneha Sebastian"))
    }

    /**
     * Recurring commitments that surround college. These are ordinary routines the user can
     * edit or delete; they exist so the first generated day is realistic rather than empty.
     */
    val routines: List<Routine> = listOf(
        Routine(
            title = "Travel to college",
            kind = RoutineKind.COMMUTE,
            category = Category.PERSONAL,
            daysMask = Routine.maskOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY),
            start = at(7, 10),
            end = at(8, 0),
        ),
        Routine(
            title = "Lunch",
            kind = RoutineKind.MEAL,
            category = Category.HEALTH,
            daysMask = Routine.maskOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY),
            start = at(14, 30),
            end = at(15, 10),
        ),
        Routine(
            title = "Lunch",
            kind = RoutineKind.MEAL,
            category = Category.HEALTH,
            daysMask = Routine.maskOf(FRIDAY),
            start = at(13, 40),
            end = at(14, 20),
        ),
        Routine(
            title = "Lunch",
            kind = RoutineKind.MEAL,
            category = Category.HEALTH,
            daysMask = Routine.maskOf(SATURDAY, SUNDAY),
            start = at(13, 0),
            end = at(13, 45),
        ),
        Routine(
            title = "Dinner",
            kind = RoutineKind.MEAL,
            category = Category.HEALTH,
            daysMask = Routine.EVERY_DAY,
            start = at(20, 0),
            end = at(20, 40),
        ),
    )

    // ---- builders ----------------------------------------------------------------------------

    private fun lecture(day: DayOfWeek, slot: Pair<Int, Int>, code: String, title: String, faculty: String) =
        TimetableEntry(
            dayOfWeek = day,
            start = slot.first,
            end = slot.second,
            subjectCode = code,
            title = title,
            kind = TimetableKind.LECTURE,
            faculty = faculty,
        )

    private fun tutorial(day: DayOfWeek, slot: Pair<Int, Int>, code: String, title: String, faculty: String) =
        TimetableEntry(
            dayOfWeek = day,
            start = slot.first,
            end = slot.second,
            subjectCode = code,
            title = "$title tutorial",
            kind = TimetableKind.TUTORIAL,
            faculty = faculty,
        )

    private fun lab(day: DayOfWeek, slot: Pair<Int, Int>, code: String, title: String, faculty: String) =
        TimetableEntry(
            dayOfWeek = day,
            start = slot.first,
            end = slot.second,
            subjectCode = code,
            title = title,
            kind = TimetableKind.LAB,
            faculty = faculty,
        )

    private fun recess(day: DayOfWeek, slot: Pair<Int, Int>) =
        TimetableEntry(
            dayOfWeek = day,
            start = slot.first,
            end = slot.second,
            subjectCode = null,
            title = "Recess",
            kind = TimetableKind.RECESS,
        )

    private fun mentoring(day: DayOfWeek, slot: Pair<Int, Int>) =
        TimetableEntry(
            dayOfWeek = day,
            start = slot.first,
            end = slot.second,
            subjectCode = null,
            title = "Mentoring",
            kind = TimetableKind.MENTORING,
        )

    private fun departmentHour(day: DayOfWeek, slot: Pair<Int, Int>) =
        TimetableEntry(
            dayOfWeek = day,
            start = slot.first,
            end = slot.second,
            subjectCode = null,
            title = "Department hour",
            kind = TimetableKind.DEPARTMENT,
        )
}
