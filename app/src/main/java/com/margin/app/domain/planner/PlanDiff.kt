package com.margin.app.domain.planner

import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.TimeRange

/**
 * Explaining what changed is a product requirement, not a nicety: when the plan shifts,
 * the user is told which blocks moved and why, instead of watching eight things rearrange.
 */
enum class ChangeKind { ADDED, REMOVED, MOVED, SHORTENED, LENGTHENED, UNCHANGED }

data class PlanChange(
    val kind: ChangeKind,
    val title: String,
    val type: BlockType,
    val from: TimeRange?,
    val to: TimeRange?,
)

data class PlanDiff(val changes: List<PlanChange>) {

    /** Changes worth telling the user about. Free time and sleep shuffling is not news. */
    val meaningful: List<PlanChange>
        get() = changes.filter {
            it.kind != ChangeKind.UNCHANGED &&
                it.type != BlockType.FREE &&
                it.type != BlockType.SLEEP
        }

    val isQuiet: Boolean get() = meaningful.isEmpty()

    /** Whether protected time survived the replan. Used for the reassurance line. */
    fun protectedMinutes(blocks: List<PlacedBlock>): Int =
        blocks.filter { it.type == BlockType.LEISURE || it.type == BlockType.BREAK }
            .sumOf { it.duration }

    companion object {
        fun of(previous: List<PlacedBlock>, next: List<PlacedBlock>): PlanDiff {
            if (previous.isEmpty()) {
                return PlanDiff(
                    next.filter { it.type != BlockType.FREE && it.type != BlockType.SLEEP }
                        .map { PlanChange(ChangeKind.ADDED, it.title, it.type, null, it.range) },
                )
            }

            val before = previous.associateBy { identity(it) }
            val after = next.associateBy { identity(it) }
            val changes = mutableListOf<PlanChange>()

            for ((key, old) in before) {
                val new = after[key]
                if (new == null) {
                    changes += PlanChange(ChangeKind.REMOVED, old.title, old.type, old.range, null)
                    continue
                }
                val kind = when {
                    new.range.start != old.range.start -> ChangeKind.MOVED
                    new.duration < old.duration -> ChangeKind.SHORTENED
                    new.duration > old.duration -> ChangeKind.LENGTHENED
                    else -> ChangeKind.UNCHANGED
                }
                changes += PlanChange(kind, new.title, new.type, old.range, new.range)
            }

            for ((key, new) in after) {
                if (!before.containsKey(key)) {
                    changes += PlanChange(ChangeKind.ADDED, new.title, new.type, null, new.range)
                }
            }

            return PlanDiff(changes.sortedWith(compareBy({ it.kind.ordinal }, { it.title })))
        }

        /**
         * Two blocks are the same thing if they refer to the same task, event or class.
         * Generic blocks fall back to type plus title so a break is not confused with leisure.
         */
        private fun identity(block: PlacedBlock): String = when {
            block.taskId != null -> "task:${block.taskId}"
            block.eventId != null -> "event:${block.eventId}"
            block.timetableEntryId != null -> "class:${block.timetableEntryId}"
            block.routineId != null -> "routine:${block.routineId}"
            else -> "${block.type.key}:${block.title}:${block.start}"
        }
    }
}
