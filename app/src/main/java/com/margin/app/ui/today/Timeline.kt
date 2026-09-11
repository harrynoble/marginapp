package com.margin.app.ui.today

import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.ScheduleBlock

/**
 * How the day is presented, as distinct from how it is stored.
 *
 * The planner keeps every class as its own block, because it needs them: revision is derived
 * per subject and the timeline must know exactly when each period ends. The person does not
 * need them. They know their timetable, so the display folds a run of classes and recesses
 * into one "College" entry and keeps the individual periods out of sight.
 *
 * Pure Kotlin with no Android dependencies, so it is unit tested directly.
 */
sealed interface TimelineItem {
    val start: Int
    val end: Int
    val key: String

    data class Single(val block: ScheduleBlock) : TimelineItem {
        override val start: Int get() = block.start
        override val end: Int get() = block.end
        override val key: String get() = "block-${block.id}"
    }

    /** A continuous stretch at college: classes, recesses and any short free period between. */
    data class College(val blocks: List<ScheduleBlock>) : TimelineItem {
        override val start: Int get() = blocks.first().start
        override val end: Int get() = blocks.last().end
        override val key: String get() = "college-$start"

        val classCount: Int get() = blocks.count { it.type == BlockType.CLASS }

        /** The period happening at [minute], if any. Used only for the Now card. */
        fun periodAt(minute: Int): ScheduleBlock? =
            blocks.firstOrNull { minute >= it.start && minute < it.end && it.type != BlockType.FREE }

        fun nextPeriodAfter(minute: Int): ScheduleBlock? =
            blocks.firstOrNull { it.start > minute && it.type == BlockType.CLASS }
    }
}

object Timeline {

    /** Free time shorter than this between two classes is a gap in college, not free time. */
    private const val MAX_ABSORBED_GAP = 60

    /** A block belongs to college if it came from the timetable. */
    fun isCollege(block: ScheduleBlock): Boolean =
        block.type == BlockType.CLASS || block.timetableEntryId != null

    /**
     * Folds every run of college blocks into a single [TimelineItem.College]. Order is kept,
     * everything else passes through untouched, and a run with no actual class in it (a
     * recess on its own) is left as individual blocks.
     */
    fun collapse(blocks: List<ScheduleBlock>): List<TimelineItem> {
        val sorted = blocks.sortedWith(compareBy({ it.start }, { it.end }, { it.id }))
        val out = mutableListOf<TimelineItem>()
        var run = mutableListOf<ScheduleBlock>()

        fun flush() {
            if (run.isEmpty()) return
            // Free time left dangling at the end of a run belongs to the day, not to college.
            val tail = mutableListOf<ScheduleBlock>()
            while (run.isNotEmpty() && run.last().type == BlockType.FREE) {
                tail.add(0, run.removeAt(run.lastIndex))
            }
            if (run.any { it.type == BlockType.CLASS }) {
                out += TimelineItem.College(run.toList())
            } else {
                run.forEach { out += TimelineItem.Single(it) }
            }
            tail.forEach { out += TimelineItem.Single(it) }
            run = mutableListOf()
        }

        for ((index, block) in sorted.withIndex()) {
            when {
                isCollege(block) -> {
                    val contiguous = run.isEmpty() || block.start - run.last().end <= MAX_ABSORBED_GAP
                    if (!contiguous) flush()
                    run += block
                }

                // A short free period sandwiched between classes is still time at college.
                block.type == BlockType.FREE &&
                    run.isNotEmpty() &&
                    block.duration <= MAX_ABSORBED_GAP &&
                    nextNonFree(sorted, index)?.let { isCollege(it) } == true -> run += block

                else -> {
                    flush()
                    out += TimelineItem.Single(block)
                }
            }
        }
        flush()
        return out
    }

    private fun nextNonFree(blocks: List<ScheduleBlock>, from: Int): ScheduleBlock? {
        for (i in from + 1 until blocks.size) {
            if (blocks[i].type != BlockType.FREE) return blocks[i]
        }
        return null
    }

    /** The parts of the day the Today screen groups its list by. */
    enum class Part(val label: String) { MORNING("Morning"), AFTERNOON("Afternoon"), EVENING("Evening") }

    fun partOf(minute: Int): Part = when {
        minute < 12 * 60 -> Part.MORNING
        minute < 17 * 60 -> Part.AFTERNOON
        else -> Part.EVENING
    }
}
