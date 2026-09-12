package com.margin.app.domain.planner

import com.margin.app.domain.model.BlockStatus
import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.DayState
import com.margin.app.domain.model.Decision
import com.margin.app.domain.model.ScheduleBlock

/**
 * The notifications that guide a day. Lower [rank] wins when two are due at once, and only
 * one is ever posted at a time.
 */
enum class NudgeType(val rank: Int) {
    TRANSITION(0),
    BREAK_SUGGESTION(1),
    /** Weekends, holidays and exam days: the plan for the day is ready, and what is first. */
    DAY_PLAN(2),
    STUDY_START(2),
    MISSED_CHECK(3),
    BREAK_OVER(4),
    UP_NEXT(5),
    BUILD_PROMPT(6),
    LEARNING_PROMPT(7),
    DAILY_REVIEW(8),
}

data class Nudge(
    val type: NudgeType,
    val atMinute: Int,
    /** After this minute the nudge is stale and is dropped rather than posted late. */
    val expiresAt: Int,
    /** Unique per day. A key that has been posted is never posted again. */
    val key: String,
    val blockId: Long? = null,
    val nextBlockId: Long? = null,
    val suggestedMinutes: Int? = null,
    val runMinutes: Int? = null,
    /** For [NudgeType.DAY_PLAN]: which kind of day the plan is for. */
    val dayType: DayType? = null,
)

data class NudgeSettings(
    val enabled: Boolean = true,
    val leadMinutes: Int = 5,
    val missedGraceMinutes: Int = 15,
    val studyStart: Boolean = true,
    val transitions: Boolean = true,
    val breaks: Boolean = true,
    val missed: Boolean = true,
    val upNext: Boolean = true,
    val breakOver: Boolean = true,
    val buildPrompts: Boolean = true,
    val learningPrompts: Boolean = true,
    val dailyReview: Boolean = true,
    val reviewMinute: Int = 21 * 60 + 30,
    val wakeMinute: Int = 6 * 60 + 30,
    val sleepMinute: Int = 23 * 60,
    val breakThreshold: Int = 85,
    val lowEnergy: Boolean = false,
    val dayType: DayType = DayType.WEEKDAY,
)

data class NudgeState(
    val blocks: List<ScheduleBlock>,
    val dayState: DayState,
    val checkInDone: Boolean,
    /** When the running block was last started, as a minute of the day. */
    val activeStartMinute: Int?,
    /** Keys already posted today. */
    val posted: Set<String>,
)

/**
 * Works out every notification the day could still need, from the day itself. Pure: nothing
 * here posts, schedules or reads the clock, which is what lets the restraint rules be tested.
 *
 * The rules, in short: each thing is said at most once; nothing is said outside waking hours;
 * nothing stale is said late; and a prompt that is already covered by another (the transition
 * at the end of a session covers "up next" for the session after it) is not said twice.
 */
object NudgePlanner {

    fun plan(state: NudgeState, settings: NudgeSettings, nowMinute: Int): List<Nudge> {
        if (!settings.enabled) return emptyList()
        val blocks = state.blocks.sortedBy { it.start }
        val out = mutableListOf<Nudge>()
        val active = blocks.firstOrNull { it.status == BlockStatus.ACTIVE }
        val lead = settings.leadMinutes.coerceAtLeast(0)

        val lastClassEnd = blocks.filter { it.type == BlockType.CLASS }.maxOfOrNull { it.end }
        val plannedWork = blocks.filter { it.type.isWork && it.status == BlockStatus.PLANNED }

        fun nextWorkAfter(minute: Int, excluding: Long? = null): ScheduleBlock? =
            plannedWork.firstOrNull { it.start >= minute - 5 && it.id != excluding }

        // ---- the first study session of the day ------------------------------------------
        val studyOpener = plannedWork
            .filter { it.isAcademic }
            .firstOrNull { lastClassEnd == null || it.start >= lastClassEnd }
        if (settings.studyStart && studyOpener != null) {
            out += Nudge(
                type = NudgeType.STUDY_START,
                atMinute = (studyOpener.start - lead).coerceAtLeast(settings.wakeMinute),
                expiresAt = studyOpener.start + settings.missedGraceMinutes - 1,
                key = "study-start",
                blockId = studyOpener.id,
            )
        }

        // ---- the day's plan, on a weekend, a holiday or an exam day -----------------------
        // There is no college to anchor the day, so the morning starts with what the plan is.
        // Nothing about classes is ever said: on a holiday there are none.
        val firstWork = plannedWork.firstOrNull { !it.optional }
        if (settings.studyStart && settings.dayType != DayType.WEEKDAY && firstWork != null) {
            val at = (firstWork.start - DAY_PLAN_LEAD)
                .coerceAtLeast(settings.wakeMinute + DAY_PLAN_AFTER_WAKE)
                .coerceAtMost(firstWork.start - lead)
            out += Nudge(
                type = NudgeType.DAY_PLAN,
                atMinute = at,
                expiresAt = firstWork.start + settings.missedGraceMinutes - 1,
                key = "day-plan",
                blockId = firstWork.id,
                dayType = settings.dayType,
            )
        }

        // ---- up next and break over --------------------------------------------------------
        for ((index, block) in blocks.withIndex()) {
            val previous = blocks.getOrNull(index - 1)
            if (block.type.isWork && block.status == BlockStatus.PLANNED && block.id != studyOpener?.id) {
                // Straight after another session, the transition prompt already asks.
                val followsWork = previous != null && previous.type.isWork && previous.end >= block.start - 5
                val followsBreak = previous != null && previous.type == BlockType.BREAK && previous.end >= block.start - 5
                if (settings.upNext && !followsWork && !followsBreak && !block.optional) {
                    out += Nudge(
                        type = NudgeType.UP_NEXT,
                        atMinute = block.start - lead,
                        expiresAt = block.start + settings.missedGraceMinutes - 1,
                        key = "up:${block.id}",
                        blockId = block.id,
                    )
                }
            }
            if (block.type == BlockType.BREAK && block.status.isOpen && settings.breakOver) {
                val next = nextWorkAfter(block.end)
                if (next != null && next.start <= block.end + 10) {
                    out += Nudge(
                        type = NudgeType.BREAK_OVER,
                        atMinute = block.end,
                        expiresAt = block.end + 20,
                        key = "break-over:${block.id}",
                        blockId = block.id,
                        nextBlockId = next.id,
                    )
                }
            }
        }

        // ---- the end of a running session ----------------------------------------------
        if (active != null && active.type.isWork && settings.transitions) {
            out += Nudge(
                type = NudgeType.TRANSITION,
                atMinute = active.end,
                expiresAt = active.end + 90,
                key = "end:${active.id}:${active.end}",
                blockId = active.id,
                nextBlockId = nextWorkAfter(active.end, excluding = active.id)?.id,
            )
        }

        // ---- a break, from what has actually been worked ---------------------------------
        if (active != null && settings.breaks) {
            val run = BreakAdvisor.currentRun(blocks, nowMinute, state.activeStartMinute)
            val suggestion = BreakAdvisor.evaluate(run, nowMinute, settings.breakThreshold, settings.lowEnergy)
            // Too close to the end of the session, the transition prompt is the better moment.
            if (suggestion != null && suggestion.atMinute < active.end - 5) {
                out += Nudge(
                    type = NudgeType.BREAK_SUGGESTION,
                    atMinute = suggestion.atMinute,
                    expiresAt = suggestion.atMinute + 45,
                    key = "break:${suggestion.run.startMinute}",
                    blockId = active.id,
                    suggestedMinutes = suggestion.suggestedMinutes,
                    runMinutes = maxOf(suggestion.run.minutes, suggestion.atMinute - suggestion.run.startMinute),
                )
            }
        }

        // ---- a session that should have started ----------------------------------------
        if (settings.missed && active == null) {
            for (block in plannedWork) {
                if (block.optional) continue
                out += Nudge(
                    type = NudgeType.MISSED_CHECK,
                    atMinute = block.start + settings.missedGraceMinutes,
                    expiresAt = block.end - 1,
                    key = "missed:${block.id}",
                    blockId = block.id,
                )
            }
        }

        // ---- build and learning, offered once academics are done -------------------------
        val academicOpen = blocks.filter { it.isAcademic && it.status.isOpen }
        val lastAcademicEnd = blocks.filter { it.isAcademic }.maxOfOrNull { it.end }
        fun offer(type: BlockType, decision: Decision, enabled: Boolean, nudgeType: NudgeType, key: String) {
            if (!enabled || decision != Decision.UNASKED) return
            val block = blocks.firstOrNull { it.type == type && it.optional && it.status == BlockStatus.PLANNED } ?: return
            val academicBefore = academicOpen.filter { it.start < block.start }
            val at = when {
                // Academics still to come: ask once they are due to be finished.
                academicBefore.isNotEmpty() -> maxOf(block.start - lead, academicBefore.maxOf { it.end })
                // Academics already finished: ask now rather than waiting for the slot.
                lastAcademicEnd != null -> minOf(block.start - lead, maxOf(lastAcademicEnd, nowMinute))
                // No academics today at all: ask just before the slot.
                else -> block.start - lead
            }
            out += Nudge(
                type = nudgeType,
                atMinute = at,
                expiresAt = block.end - 1,
                key = key,
                blockId = block.id,
            )
        }
        offer(BlockType.BUILD, state.dayState.buildDecision, settings.buildPrompts, NudgeType.BUILD_PROMPT, "build-prompt")
        offer(BlockType.LEARN, state.dayState.learningDecision, settings.learningPrompts, NudgeType.LEARNING_PROMPT, "learn-prompt")

        // ---- the end of the day ----------------------------------------------------------
        if (settings.dailyReview && !state.checkInDone) {
            val worked = blocks.any { it.status.isWorked || it.status == BlockStatus.SKIPPED || it.status == BlockStatus.MISSED }
            if (worked) {
                out += Nudge(
                    type = NudgeType.DAILY_REVIEW,
                    atMinute = settings.reviewMinute,
                    expiresAt = maxOf(settings.sleepMinute, settings.reviewMinute + 60),
                    key = "review",
                )
            }
        }

        return out
            .filter { it.key !in state.posted }
            .filter { it.atMinute >= settings.wakeMinute - lead }
            .filter { settings.sleepMinute <= settings.wakeMinute || it.atMinute <= settings.sleepMinute }
            .sortedWith(compareBy({ it.atMinute }, { it.type.rank }, { it.key }))
    }

    /** What should be posted now: due, not stale, highest rank first. */
    fun due(nudges: List<Nudge>, nowMinute: Int): List<Nudge> =
        nudges
            .filter { it.atMinute <= nowMinute + DUE_TOLERANCE && nowMinute <= it.expiresAt }
            .sortedWith(compareBy({ it.type.rank }, { it.atMinute }, { it.key }))

    /** The next time something could become due, for the alarm. */
    fun next(nudges: List<Nudge>, nowMinute: Int): Nudge? =
        nudges.filter { it.atMinute > nowMinute + DUE_TOLERANCE }.minWithOrNull(compareBy({ it.atMinute }, { it.type.rank }))

    private const val DUE_TOLERANCE = 1
    private const val DAY_PLAN_LEAD = 60
    private const val DAY_PLAN_AFTER_WAKE = 15
}
