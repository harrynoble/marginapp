package com.margin.app.data.repository

import com.margin.app.data.db.dao.ExamDao
import com.margin.app.data.db.dao.LearningGoalDao
import com.margin.app.data.db.toDomain
import com.margin.app.data.db.toEntity
import com.margin.app.domain.model.Exam
import com.margin.app.domain.model.LearningGoal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Skills the user wants to learn. Learning is its own category, tracked apart from study. */
class GoalRepository(private val dao: LearningGoalDao) {

    fun observeGoals(): Flow<List<LearningGoal>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun activeGoals(): List<LearningGoal> = dao.allActive().map { it.toDomain() }

    suspend fun goal(id: Long?): LearningGoal? = id?.let { dao.byId(it)?.toDomain() }

    suspend fun upsert(goal: LearningGoal): Long = dao.upsert(goal.toEntity())

    suspend fun delete(id: Long) = dao.delete(id)
}

/** The exam timetable, kept apart from the weekly college timetable. */
class ExamRepository(private val dao: ExamDao) {

    fun observeAll(): Flow<List<Exam>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeUpcoming(from: LocalDate): Flow<List<Exam>> =
        dao.observeFrom(from.toEpochDay()).map { list -> list.map { it.toDomain() } }

    suspend fun upcoming(from: LocalDate): List<Exam> = dao.from(from.toEpochDay()).map { it.toDomain() }

    suspend fun exam(id: Long): Exam? = dao.byId(id)?.toDomain()

    suspend fun upsert(exam: Exam): Long = dao.upsert(exam.toEntity())

    suspend fun addAll(exams: List<Exam>) = dao.insertAll(exams.map { it.copy(id = 0).toEntity() })

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clearBefore(date: LocalDate) = dao.deleteBefore(date.toEpochDay())
}
