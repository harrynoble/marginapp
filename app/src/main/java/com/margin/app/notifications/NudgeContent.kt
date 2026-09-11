package com.margin.app.notifications

import android.content.Context
import com.margin.app.core.MarginTime
import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.ScheduleBlock
import com.margin.app.domain.planner.Nudge
import com.margin.app.domain.planner.NudgeType
import com.margin.app.notifications.NotificationActionReceiver as A

/**
 * What each guidance prompt says and offers. Short, plain, and always with the one or two
 * choices that matter at that moment.
 */
object NudgeContent {

    data class Content(
        val title: String,
        val body: String,
        val actions: List<Notifier.Action>,
        val openTarget: String? = null,
    )

    fun build(
        context: Context,
        nudge: Nudge,
        blocks: List<ScheduleBlock>,
        use24Hour: Boolean,
    ): Content? {
        val block = blocks.firstOrNull { it.id == nudge.blockId }
        val next = blocks.firstOrNull { it.id == nudge.nextBlockId }
        fun act(label: String, action: String, id: Long, extra: Int = 0) =
            Notifier.Action(label, Notifier.actionIntent(context, action, id, extra))
        fun open(label: String, target: String) =
            Notifier.Action(label, Notifier.openIntent(context, target))

        return when (nudge.type) {
            NudgeType.STUDY_START -> {
                block ?: return null
                Content(
                    title = "Ready to study?",
                    body = "First up: ${block.title} · ${MarginTime.formatDuration(block.duration)}" +
                        (block.subtitle?.let { "\n$it" } ?: ""),
                    actions = listOf(
                        act("Start", A.ACTION_START, block.id),
                        act("Later", A.ACTION_LATER, block.id, LATER_MINUTES),
                        open("I'm out", LaunchRequests.TARGET_OUT),
                    ),
                )
            }

            NudgeType.UP_NEXT -> {
                block ?: return null
                Content(
                    title = "Up next: ${block.title}",
                    body = MarginTime.formatTime(block.start, use24Hour) + " · " +
                        MarginTime.formatDuration(block.duration) + (block.subtitle?.let { " · $it" } ?: ""),
                    actions = listOf(
                        act("Start", A.ACTION_START, block.id),
                        act("Snooze 10", A.ACTION_LATER, block.id, SNOOZE_MINUTES),
                        act("Skip", A.ACTION_SKIP, block.id),
                    ),
                )
            }

            NudgeType.BREAK_OVER -> {
                next ?: return null
                Content(
                    title = "Break's over",
                    body = "Next: ${next.title} · ${MarginTime.formatDuration(next.duration)}",
                    actions = listOf(
                        act("Start", A.ACTION_START, next.id),
                        act("5 more min", A.ACTION_LATER, next.id, 5),
                    ),
                )
            }

            NudgeType.TRANSITION -> {
                block ?: return null
                if (block.status != BlockStatus.ACTIVE) return null
                val planned = if (block.plannedMinutes > 0) block.plannedMinutes else block.duration
                val nextWork = next?.takeIf { it.type.isWork }
                Content(
                    title = "${block.title} · ${MarginTime.formatDuration(planned + block.extendedMinutes)} complete",
                    body = if (nextWork != null) "Move to ${nextWork.title}?" else "Keep going, or finish here?",
                    actions = listOfNotNull(
                        if (nextWork != null) act("Move to next", A.ACTION_MOVE_NEXT, block.id) else act("Finish", A.ACTION_COMPLETE, block.id),
                        act("Continue", A.ACTION_CONTINUE, block.id, CONTINUE_MINUTES),
                        if (nextWork != null) act("Finish", A.ACTION_COMPLETE, block.id) else null,
                    ),
                )
            }

            NudgeType.BREAK_SUGGESTION -> {
                block ?: return null
                val run = nudge.runMinutes ?: 0
                val suggested = nudge.suggestedMinutes ?: 15
                Content(
                    title = "You've been working for ${MarginTime.formatDuration(run)}",
                    body = "Take a $suggested-minute break?",
                    actions = listOf(
                        act("$suggested min", A.ACTION_BREAK, block.id, suggested),
                        act("30 min", A.ACTION_BREAK, block.id, 30),
                        act("Continue", A.ACTION_DISMISS, block.id),
                    ),
                )
            }

            NudgeType.MISSED_CHECK -> {
                block ?: return null
                if (block.status != BlockStatus.PLANNED) return null
                Content(
                    title = "${block.title} was due ${(nudge.atMinute - block.start).coerceAtLeast(1)} min ago",
                    body = "Are you out?",
                    actions = listOf(
                        act("Start now", A.ACTION_START, block.id),
                        open("I'm out", LaunchRequests.TARGET_OUT),
                        act("Later", A.ACTION_LATER, block.id, LATER_MINUTES),
                    ),
                    openTarget = LaunchRequests.TARGET_OUT,
                )
            }

            NudgeType.BUILD_PROMPT -> {
                block ?: return null
                val academicsDone = blocks.none { it.isAcademic && it.status.isOpen }
                Content(
                    title = if (academicsDone) "You've finished today's academic plan" else "Build time",
                    body = "Want to build something? ${block.title} · ${MarginTime.formatDuration(block.duration)}",
                    actions = listOf(
                        act("Yes", A.ACTION_BUILD_YES, block.id),
                        act("Not today", A.ACTION_BUILD_NO, block.id),
                    ),
                    openTarget = LaunchRequests.TARGET_BUILD,
                )
            }

            NudgeType.LEARNING_PROMPT -> {
                block ?: return null
                Content(
                    title = "Learn something new today?",
                    body = "${block.title} · ${MarginTime.formatDuration(block.duration)}",
                    actions = listOf(
                        act("Yes", A.ACTION_LEARN_YES, block.id),
                        act("Not today", A.ACTION_LEARN_NO, block.id),
                    ),
                    openTarget = LaunchRequests.TARGET_LEARN,
                )
            }

            NudgeType.DAILY_REVIEW -> {
                val worked = blocks.filter { it.status.isWorked }
                fun minutes(type: (ScheduleBlock) -> Boolean) = worked.filter(type)
                    .sumOf { if (it.elapsedMinutes > 0) it.elapsedMinutes else it.duration }
                val academics = minutes { it.isAcademic }
                val build = minutes { it.type == BlockType.BUILD }
                val learning = minutes { it.type == BlockType.LEARN }
                val skipped = blocks.count { it.status == BlockStatus.SKIPPED || it.status == BlockStatus.MISSED }
                val parts = buildList {
                    if (academics > 0) add("Academics ${MarginTime.formatDuration(academics)}")
                    if (build > 0) add("Build ${MarginTime.formatDuration(build)}")
                    if (learning > 0) add("Learning ${MarginTime.formatDuration(learning)}")
                    if (skipped > 0) add("$skipped skipped")
                }
                Content(
                    title = "Today's summary",
                    body = parts.joinToString(" · ").ifBlank { "Close out the day in a minute." },
                    actions = listOf(open("Review", LaunchRequests.TARGET_CHECK_IN)),
                    openTarget = LaunchRequests.TARGET_CHECK_IN,
                )
            }
        }
    }

    const val LATER_MINUTES = 30
    const val SNOOZE_MINUTES = 10
    const val CONTINUE_MINUTES = 15
}
