package com.margin.app.domain.planner

import com.margin.app.domain.model.BlockType
import com.margin.app.domain.model.Category
import com.margin.app.domain.model.Difficulty
import com.margin.app.domain.model.EnergyLevel
import com.margin.app.domain.model.TimeRange
import com.margin.app.domain.model.subtractAll
import kotlin.math.abs

/**
 * The scheduling engine.
 *
 * Deterministic by construction: it never reads the clock, never uses randomness, and every
 * sort ends in a tiebreak on id. Given the same [PlannerInput] it returns the same [PlannedDay].
 *
 * The shape of a day it produces: commitments and transitions first, leisure protected at the
 * end of the evening, academic work in the earliest sensible slots, then build, then learning,
 * each separated by a short break. Free time is left free.
 *
 * Pure Kotlin, no Android. See docs/SCHEDULING.md for the pass-by-pass description.
 */
class DayPlanner {

    fun plan(input: PlannerInput): PlannedDay {
        val prefs = input.prefs
        val diagnostics = mutableListOf<Diagnostic>()
        val placed = mutableListOf<PlacedBlock>()

        // ---- Pass 1: frame the day -------------------------------------------------------
        val frame = dayFrame(prefs)
        // Nothing may be scheduled before "now" when we are replanning mid-day.
        val horizonStart = maxOf(frame.start, input.nowMinute?.let { prefs.roundUp(it) } ?: frame.start)
        val horizon = if (horizonStart < frame.end) TimeRange(horizonStart, frame.end) else null

        placed += sleepBlocks(prefs)

        // Blocks already completed, skipped, running or pinned. Never touched, never re-planned.
        val settled = input.settled.sortedBy { it.range }
        placed += settled

        // ---- Pass 2: hard commitments ----------------------------------------------------
        val commitments = input.commitments
            .filter { it.range.duration > 0 }
            .sortedWith(compareBy({ it.range.start }, { it.range.end }, { it.id }))

        diagnostics += detectConflicts(commitments)
        placed += commitments.map { it.toPlacedBlock() }

        // ---- Pass 3: transitions ---------------------------------------------------------
        placed += transitionBuffers(commitments, settled, prefs, frame)

        // ---- Pass 4: free intervals ------------------------------------------------------
        val occupied = placed.map { it.range }.distinct()
        var free = (if (horizon == null) emptyList() else listOf(horizon))
            .subtractAll(occupied)
            .map { trimToGranularity(it, prefs) }
            .filter { it.duration >= prefs.minUsefulSlotMinutes }

        if (free.isEmpty() && input.work.isNotEmpty()) {
            diagnostics += Diagnostic.NothingToPlan(
                "The day is fully committed, so nothing else could be scheduled.",
            )
        }

        // ---- Pass 5: reserve leisure -----------------------------------------------------
        val reservations = mutableListOf<PlacedBlock>()
        for (quota in input.quotas.filter { it.kind == QuotaKind.LEISURE }.sortedBy { it.id }) {
            if (quota.minutes <= 0) continue
            val outcome = reserve(quota, free, prefs)
            reservations += outcome.blocks
            free = outcome.free
            if (outcome.shortfall > 0) {
                diagnostics += Diagnostic.Overloaded(
                    shortfallMinutes = outcome.shortfall,
                    kind = quota.kind,
                    message = "Today is short on downtime by ${outcome.shortfall} min. Nothing was " +
                        "deleted; move or drop something if you want the time back.",
                )
            }
        }

        // Build and learning come after the academic work, but their time is held back from
        // anything that is not urgent, so a pile of ordinary work cannot swallow the evening.
        val postQuotas = input.quotas
            .filter { it.kind != QuotaKind.LEISURE && it.minutes > 0 }
            .sortedWith(compareBy({ it.kind.ordinal }, { it.id }))
        val protectedBudget = (free.sumOf { it.duration } - postQuotas.sumOf { it.minutes })
            .coerceAtLeast(0)

        // ---- Passes 6 to 8: score, place, break ------------------------------------------
        val previousStarts = input.previous
            .filter { it.candidateId != null }
            .groupBy({ it.candidateId!! }, { it.start })
        val placement = placeWork(input.work, free, prefs, settled, protectedBudget, previousStarts)
        placed += placement.blocks
        diagnostics += placement.diagnostics

        // ---- Pass 8b: build, then learning, after the work --------------------------------
        var remaining = free
            .subtractAll(placement.blocks.map { it.range })
            .filter { it.duration >= prefs.minUsefulSlotMinutes }
        val placedSoFar = (settled + placement.blocks).toMutableList()
        for (quota in postQuotas) {
            val outcome = placeAfterWork(quota, remaining, prefs, placedSoFar)
            placed += outcome.blocks
            placedSoFar += outcome.blocks
            remaining = outcome.free
            if (outcome.shortfall >= MIN_REPORTED_SHORTFALL) {
                diagnostics += Diagnostic.Overloaded(
                    shortfallMinutes = outcome.shortfall,
                    kind = quota.kind,
                    message = when (quota.kind) {
                        QuotaKind.BUILD ->
                            "Only part of your build time fits today, short by ${outcome.shortfall} min."
                        QuotaKind.LEARNING ->
                            "Learning time is short by ${outcome.shortfall} min today."
                        QuotaKind.LEISURE -> "Downtime is short by ${outcome.shortfall} min."
                    },
                )
            }
        }

        placed += reservations

        // ---- Pass 9: fill the remainder --------------------------------------------------
        val used = placed.map { it.range }
        val leftover = (if (horizon == null) emptyList() else listOf(horizon))
            .subtractAll(used)
            .filter { it.duration >= MIN_FREE_BLOCK }
        placed += leftover.map { range ->
            PlacedBlock(
                key = "free:${range.start}",
                range = range,
                type = BlockType.FREE,
                title = "Free",
                category = Category.LEISURE,
                reason = "Unassigned time. Yours.",
            )
        }

        val ordered = placed
            .filter { it.duration > 0 }
            .sortedWith(compareBy({ it.range.start }, { it.range.end }, { it.key }))

        if (ordered.none { it.type.isWork } && input.work.isNotEmpty() && diagnostics.isEmpty()) {
            diagnostics += Diagnostic.NothingToPlan(
                "There was no usable gap long enough for the work waiting today.",
            )
        }

        return PlannedDay(
            date = input.date,
            blocks = ordered,
            diagnostics = diagnostics.distinctBy { it.message },
            unplaced = placement.unplaced,
        )
    }

    // ---------------------------------------------------------------------------------------
    // Pass 1: the frame
    // ---------------------------------------------------------------------------------------

    /**
     * Wake to sleep within one calendar date. A sleep time at or before the wake time belongs
     * to the next morning, so the frame ends at midnight and the rest is the next day plan.
     */
    private fun dayFrame(prefs: PlannerPreferences): TimeRange {
        val wake = prefs.wakeMinute.coerceIn(0, MAX_MINUTE)
        val sleep = prefs.sleepMinute.coerceIn(0, MAX_MINUTE)
        val end = if (sleep <= wake) MAX_MINUTE else sleep
        return TimeRange(wake, end)
    }

    private fun sleepBlocks(prefs: PlannerPreferences): List<PlacedBlock> {
        val wake = prefs.wakeMinute.coerceIn(0, MAX_MINUTE)
        val sleep = prefs.sleepMinute.coerceIn(0, MAX_MINUTE)
        val blocks = mutableListOf<PlacedBlock>()
        if (wake > 0) blocks += sleepBlock(TimeRange(0, wake), "sleep:early")
        if (sleep > wake && sleep < MAX_MINUTE) {
            blocks += sleepBlock(TimeRange(sleep, MAX_MINUTE), "sleep:late")
        }
        return blocks
    }

    private fun sleepBlock(range: TimeRange, key: String) = PlacedBlock(
        key = key,
        range = range,
        type = BlockType.SLEEP,
        title = "Sleep",
        category = Category.HEALTH,
        reason = "Outside your waking hours.",
    )

    // ---------------------------------------------------------------------------------------
    // Pass 2: hard commitments
    // ---------------------------------------------------------------------------------------

    private fun detectConflicts(commitments: List<Commitment>): List<Diagnostic> {
        val out = mutableListOf<Diagnostic>()
        for (i in commitments.indices) {
            for (j in i + 1 until commitments.size) {
                val a = commitments[i]
                val b = commitments[j]
                if (b.range.start >= a.range.end) break
                val overlap = a.range.intersect(b.range) ?: continue
                if (overlap.duration < MIN_CONFLICT) continue
                out += Diagnostic.HardConflict(
                    firstTitle = a.title,
                    secondTitle = b.title,
                    range = overlap,
                    message = "${a.title} and ${b.title} overlap by ${overlap.duration} min.",
                )
            }
        }
        return out
    }

    private fun Commitment.toPlacedBlock() = PlacedBlock(
        key = id,
        range = range,
        type = type,
        title = title,
        subtitle = subtitle,
        category = category,
        status = status,
        taskId = taskId,
        eventId = eventId,
        timetableEntryId = timetableEntryId,
        routineId = routineId,
        projectId = projectId,
        subjectCode = subjectCode,
        locked = locked,
        academicType = academicType,
    )

    // ---------------------------------------------------------------------------------------
    // Pass 3: transitions
    // ---------------------------------------------------------------------------------------

    /**
     * A commute after the last class of the day and a recovery window after any long
     * commitment. Without this the plan reads "13:40 college ends, 13:41 start studying".
     * Settled blocks occupy their time too, so a transition that already happened earlier
     * today is not planned a second time.
     */
    private fun transitionBuffers(
        commitments: List<Commitment>,
        settled: List<PlacedBlock>,
        prefs: PlannerPreferences,
        frame: TimeRange,
    ): List<PlacedBlock> {
        val buffers = mutableListOf<PlacedBlock>()
        val occupied = (commitments.map { it.range } + settled.map { it.range }).toMutableList()

        val classes = commitments.filter { it.type == BlockType.CLASS } +
            settled.filter { it.type == BlockType.CLASS }.map {
                Commitment(id = it.key, title = it.title, range = it.range, type = it.type, category = it.category)
            }
        var commuteEnd: Int? = null

        if (classes.isNotEmpty() && prefs.commuteMinutes > 0) {
            val lastEnd = classes.maxOf { it.range.end }
            val alreadyTravelled = settled.firstOrNull {
                it.type == BlockType.COMMUTE && it.start >= lastEnd - MIN_BUFFER
            }
            if (alreadyTravelled != null) {
                commuteEnd = alreadyTravelled.end
            } else {
                val commute = fitBuffer(lastEnd, prefs.commuteMinutes, occupied, frame)
                if (commute != null) {
                    buffers += PlacedBlock(
                        key = "commute:$lastEnd",
                        range = commute,
                        type = BlockType.COMMUTE,
                        title = "Travel home",
                        category = Category.PERSONAL,
                        reason = "Getting back after college.",
                    )
                    occupied += commute
                    commuteEnd = commute.end
                }
            }
        }

        // Recovery after anything long enough to be draining.
        val anchors = buildList {
            if (classes.isNotEmpty()) {
                val span = classes.maxOf { it.range.end } - classes.minOf { it.range.start }
                if (span >= prefs.longCommitmentMinutes) {
                    add(commuteEnd ?: classes.maxOf { it.range.end })
                }
            }
            commitments
                .filter { it.type == BlockType.EVENT && it.range.duration >= prefs.longCommitmentMinutes }
                .forEach { add(it.range.end) }
        }.distinct().sorted()

        for (anchor in anchors) {
            if (prefs.decompressionMinutes <= 0) continue
            if (settled.any { it.type == BlockType.DECOMPRESS && abs(it.start - anchor) <= MIN_BUFFER }) continue
            val slot = fitBuffer(anchor, prefs.decompressionMinutes, occupied, frame) ?: continue
            buffers += PlacedBlock(
                key = "decompress:${slot.start}",
                range = slot,
                type = BlockType.DECOMPRESS,
                title = "Settle in",
                category = Category.PERSONAL,
                reason = "Recovery time before work starts, so the day is not back to back.",
            )
            occupied += slot
        }

        return buffers
    }

    /** Places a buffer of [minutes] starting at [from], shortening it rather than dropping it. */
    private fun fitBuffer(
        from: Int,
        minutes: Int,
        occupied: List<TimeRange>,
        frame: TimeRange,
    ): TimeRange? {
        val start = maxOf(from, frame.start)
        if (start >= frame.end) return null
        val end = minOf(start + minutes, frame.end)
        if (end <= start) return null
        val candidate = TimeRange(start, end)
        val clash = occupied.filter { it.overlaps(candidate) }.minByOrNull { it.start }
            ?: return candidate
        // A commitment that already started before the buffer would leaves no room at all.
        val trimmedEnd = minOf(end, clash.start)
        if (trimmedEnd <= start) return null
        val trimmed = TimeRange(start, trimmedEnd)
        return if (trimmed.duration >= MIN_BUFFER) trimmed else null
    }

    private fun trimToGranularity(range: TimeRange, prefs: PlannerPreferences): TimeRange {
        val start = prefs.roundUp(range.start)
        val end = prefs.roundDown(range.end)
        return if (end > start) TimeRange(start, end) else TimeRange(range.start, range.start)
    }

    // ---------------------------------------------------------------------------------------
    // Pass 5: leisure reservation. This is what protects downtime.
    // ---------------------------------------------------------------------------------------

    private data class Reservation(
        val blocks: List<PlacedBlock>,
        val free: List<TimeRange>,
        val shortfall: Int,
    )

    /**
     * Reserves the quota inside its preferred window first and elsewhere afterwards, taking
     * later slots first so leisure lands at the end of the day rather than mid-afternoon.
     * Reserved intervals are removed from the free pool before any work is placed, which is
     * the whole point: work cannot claim them.
     */
    private fun reserve(
        quota: QuotaCandidate,
        free: List<TimeRange>,
        prefs: PlannerPreferences,
    ): Reservation {
        var remaining = quota.minutes
        val taken = mutableListOf<PlacedBlock>()
        var pool = free

        while (remaining >= quota.minChunk) {
            val windowed = pool.mapNotNull { slot ->
                slot.intersect(quota.window)?.let { slot to it }
            }.filter { it.second.duration >= quota.minChunk }
                .maxWithOrNull(compareBy({ it.second.start }, { it.second.end }))

            val anywhere = pool.filter { it.duration >= quota.minChunk }
                .maxWithOrNull(compareBy({ it.start }, { it.end }))
                ?.let { it to it }

            val target = windowed ?: anywhere ?: break

            val (owner, usable) = target
            val length = prefs.roundDown(minOf(remaining, usable.duration))
            if (length < quota.minChunk) break

            val range = TimeRange(usable.end - length, usable.end)

            taken += PlacedBlock(
                key = "${quota.id}:${range.start}",
                range = range,
                type = quota.type,
                title = quota.title,
                subtitle = quota.subtitle,
                category = quota.category,
                candidateId = quota.id,
                optional = quota.optional,
                reason = quota.reason ?: "Reserved before any work was placed.",
            )
            remaining -= length
            pool = pool.flatMap { if (it == owner) it.minus(range) else listOf(it) }
                .filter { it.duration >= prefs.minUsefulSlotMinutes }
        }

        return Reservation(taken.sortedBy { it.range }, pool, remaining.coerceAtLeast(0))
    }

    // ---------------------------------------------------------------------------------------
    // Pass 8b: build and learning, after the academic work
    // ---------------------------------------------------------------------------------------

    /**
     * Places a build or learning quota in the earliest slot inside its window, which in
     * practice is straight after the academic work. When it follows a run of work directly,
     * a short break goes in first.
     */
    private fun placeAfterWork(
        quota: QuotaCandidate,
        free: List<TimeRange>,
        prefs: PlannerPreferences,
        before: List<PlacedBlock>,
    ): Reservation {
        var remaining = quota.minutes
        val taken = mutableListOf<PlacedBlock>()
        var pool = free
        val context = before.toMutableList()

        while (remaining >= quota.minChunk && pool.isNotEmpty()) {
            val inWindow = pool.mapNotNull { slot ->
                slot.intersect(quota.window)?.let { slot to it }
            }.filter { it.second.duration >= quota.minChunk }
                .minWithOrNull(compareBy({ it.second.start }, { it.second.end }))
            val anywhere = pool.filter { it.duration >= quota.minChunk }
                .minWithOrNull(compareBy({ it.start }, { it.end }))
                ?.let { it to it }
            val (owner, usable) = inWindow ?: anywhere ?: break

            var cursor = usable.start
            val pieces = mutableListOf<PlacedBlock>()
            if (runEndingAt(context, cursor) >= TRANSITION_RUN) {
                val breakLength = minOf(prefs.shortBreakMinutes, TRANSITION_BREAK)
                if (breakLength >= MIN_BREAK && usable.end - (cursor + breakLength) >= quota.minChunk) {
                    val gap = TimeRange(cursor, cursor + breakLength)
                    pieces += PlacedBlock(
                        key = "break:${gap.start}",
                        range = gap,
                        type = BlockType.BREAK,
                        title = "Break",
                        category = Category.LEISURE,
                        reason = "A short gap between one thing and the next.",
                    )
                    cursor += breakLength
                }
            }

            val length = prefs.roundDown(minOf(remaining, usable.end - cursor))
            if (length < quota.minChunk) {
                pool = pool.filter { it != owner }
                continue
            }

            val range = TimeRange(cursor, cursor + length)
            pieces += PlacedBlock(
                key = "${quota.id}:${range.start}",
                range = range,
                type = quota.type,
                title = quota.title,
                subtitle = quota.subtitle,
                category = quota.category,
                projectId = quota.projectId,
                learningGoalId = quota.learningGoalId,
                candidateId = quota.id,
                optional = quota.optional,
                reason = quota.reason ?: when (quota.kind) {
                    QuotaKind.BUILD -> "Your build time, after the academic work."
                    QuotaKind.LEARNING -> "Time for your learning goal."
                    QuotaKind.LEISURE -> "Reserved downtime."
                },
            )
            taken += pieces
            context += pieces
            remaining -= length
            val used = TimeRange(usable.start, range.end)
            pool = pool.flatMap { if (it == owner) it.minus(used) else listOf(it) }
                .filter { it.duration >= prefs.minUsefulSlotMinutes }
        }

        return Reservation(taken.sortedBy { it.range }, pool, remaining.coerceAtLeast(0))
    }

    /** Minutes of back-to-back work that end exactly at [minute]. A break resets the count. */
    private fun runEndingAt(blocks: List<PlacedBlock>, minute: Int): Int {
        var cursor = minute
        var total = 0
        while (true) {
            val previous = blocks.firstOrNull { it.type.isWork && it.end == cursor } ?: break
            total += previous.duration
            cursor = previous.start
        }
        return total
    }

    // ---------------------------------------------------------------------------------------
    // Passes 6 to 8: score, place, break
    // ---------------------------------------------------------------------------------------

    private data class Placement(
        val blocks: List<PlacedBlock>,
        val diagnostics: List<Diagnostic>,
        val unplaced: List<WorkCandidate>,
    )

    private fun placeWork(
        work: List<WorkCandidate>,
        free: List<TimeRange>,
        prefs: PlannerPreferences,
        settled: List<PlacedBlock>,
        protectedBudget: Int,
        previousStarts: Map<String, List<Int>>,
    ): Placement {
        if (work.isEmpty()) return Placement(emptyList(), emptyList(), emptyList())

        val byId = work.filter { it.minutes > 0 }.associateBy { it.id }
        val remaining = byId.mapValues { it.value.minutes }.toMutableMap()
        val blocks = mutableListOf<PlacedBlock>()
        val diagnostics = mutableListOf<Diagnostic>()

        var workBudget = (prefs.effectiveWorkCeiling -
            settled.filter { it.type.isWork }.sumOf { it.duration }).coerceAtLeast(0)
        var ceilingHit = workBudget <= 0 && remaining.isNotEmpty()
        var usedSoFar = 0

        for (interval in free.sortedWith(compareBy({ it.start }, { it.end }))) {
            if (workBudget <= 0) break
            var cursor = interval.start
            var runSinceBreak = 0

            while (cursor < interval.end && workBudget > 0) {
                val available = interval.end - cursor
                if (available < prefs.minUsefulSlotMinutes) break

                val choice = chooseCandidate(
                    byId = byId,
                    remaining = remaining,
                    cursor = cursor,
                    prefs = prefs,
                    available = available,
                    workBudget = workBudget,
                    protectedLeft = protectedBudget - usedSoFar,
                    previousStarts = previousStarts,
                ) ?: break
                val candidate = choice.candidate

                // A break is due before this session would push the run past the limit.
                if (runSinceBreak > 0 && runSinceBreak + choice.length > prefs.effectiveBreakInterval) {
                    val breakLength = minOf(prefs.shortBreakMinutes, available)
                    val sessionFloor = minOf(candidate.minSession, remaining[candidate.id] ?: 0)
                    if (breakLength >= MIN_BREAK && available - breakLength >= sessionFloor) {
                        val breakRange = TimeRange(cursor, cursor + breakLength)
                        blocks += PlacedBlock(
                            key = "break:${breakRange.start}",
                            range = breakRange,
                            type = BlockType.BREAK,
                            title = "Break",
                            category = Category.LEISURE,
                            reason = "You will have been working for $runSinceBreak min by then.",
                        )
                        cursor += breakLength
                        runSinceBreak = 0
                        continue
                    }
                }

                val range = TimeRange(cursor, cursor + choice.length)
                blocks += PlacedBlock(
                    key = "${candidate.id}@${range.start}",
                    range = range,
                    type = candidate.type,
                    title = candidate.title,
                    subtitle = candidate.subtitle,
                    category = candidate.category,
                    taskId = candidate.taskId,
                    projectId = candidate.projectId,
                    subjectCode = candidate.subjectCode,
                    academicType = candidate.academicType,
                    candidateId = candidate.id,
                    learningGoalId = candidate.learningGoalId,
                    reason = explain(candidate, range, prefs),
                )
                val left = (remaining[candidate.id] ?: 0) - choice.length
                if (left <= 0) remaining.remove(candidate.id) else remaining[candidate.id] = left
                workBudget -= choice.length
                usedSoFar += choice.length
                cursor += choice.length
                runSinceBreak += choice.length
                if (workBudget <= 0 && remaining.isNotEmpty()) ceilingHit = true
            }
        }

        if (ceilingHit) {
            diagnostics += Diagnostic.WorkCeilingReached(
                ceilingMinutes = prefs.effectiveWorkCeiling,
                message = "You reached your daily work ceiling of " +
                    "${prefs.effectiveWorkCeiling / 60}h ${prefs.effectiveWorkCeiling % 60}m. " +
                    "The rest was left for another day.",
            )
        }

        val unplaced = remaining.entries
            .sortedBy { it.key }
            .mapNotNull { (id, left) ->
                val candidate = byId[id] ?: return@mapNotNull null
                val done = candidate.minutes - left
                // A handful of minutes shaved off something that mostly fitted is not a
                // shortfall the user needs telling about.
                if (done > 0 && left < MIN_FRAGMENT) return@mapNotNull null
                diagnostics += Diagnostic.InsufficientTime(
                    candidateId = id,
                    title = candidate.title,
                    needed = candidate.minutes,
                    placed = done,
                    message = if (done == 0) {
                        "No gap today was long enough for ${candidate.title}, which needs at " +
                            "least ${candidate.minSession} min."
                    } else {
                        "${candidate.title}: $done of ${candidate.minutes} min fit today."
                    },
                )
                candidate.copy(minutes = left)
            }

        return Placement(blocks, diagnostics, unplaced)
    }

    private data class Choice(val candidate: WorkCandidate, val length: Int, val score: Int)

    private fun chooseCandidate(
        byId: Map<String, WorkCandidate>,
        remaining: Map<String, Int>,
        cursor: Int,
        prefs: PlannerPreferences,
        available: Int,
        workBudget: Int,
        protectedLeft: Int,
        previousStarts: Map<String, List<Int>>,
    ): Choice? {
        val slot = TimeRange(cursor, cursor + available)
        val options = remaining.entries.sortedBy { it.key }.mapNotNull { (id, left) ->
            val candidate = byId[id] ?: return@mapNotNull null
            // A few minutes left over from an earlier interval is not worth a calendar entry.
            // Dropping the remainder is honest; a five minute session is theatre.
            if (left < MIN_FRAGMENT) return@mapNotNull null

            val floor = minOf(candidate.minSession, left)
            if (floor <= 0 || available < floor || workBudget < floor) return@mapNotNull null

            // Ordinary work may not eat the time kept for build and learning.
            val budgetHere = if (candidate.urgent) workBudget else minOf(workBudget, protectedLeft)
            if (budgetHere < floor) return@mapNotNull null

            // Some work cannot happen yet, whatever the day looks like.
            candidate.earliestStart?.let { if (cursor < it) return@mapNotNull null }

            // A deadline falling today means the work has to land before it.
            val cap = candidate.deadlineMinute?.let { it - cursor } ?: Int.MAX_VALUE
            if (cap < floor) return@mapNotNull null

            val ceiling = if (candidate.splittable) sessionCeiling(candidate, prefs) else left
            var length = minOf(left, ceiling, available, budgetHere, cap)
            length = prefs.roundDown(length)
            if (length < floor) length = floor

            // Never leave a stub behind. If finishing the work here only costs a few extra
            // minutes, take it all rather than scheduling a two minute session later.
            val remainder = left - length
            if (remainder in 1 until floor) {
                val whole = left
                if (whole <= available && whole <= budgetHere && whole <= cap) length = whole
            }

            if (length > available || length > budgetHere || length > cap) return@mapNotNull null

            // Keep work where it already was when nothing ahead of it moved, so a replan does
            // not reshuffle the evening for no visible reason.
            val sticky = previousStarts[candidate.id]?.any { abs(it - cursor) <= STICKY_TOLERANCE } == true

            Choice(candidate, length, score(candidate, slot, prefs) + if (sticky) STICKY_BONUS else 0)
        }
        return options.maxWithOrNull(compareBy({ it.score }, { it.candidate.id }))
    }

    /** On a low-energy day sessions are shorter, so the same work comes in smaller pieces. */
    private fun sessionCeiling(candidate: WorkCandidate, prefs: PlannerPreferences): Int =
        if (prefs.energyMode == EnergyMode.LIGHT) {
            prefs.roundDown((candidate.maxSession * LIGHT_SESSION_FACTOR).toInt())
                .coerceAtLeast(candidate.minSession)
        } else {
            candidate.maxSession
        }

    /**
     * Deterministic scoring. Higher wins. The components are deliberately coarse so that the
     * ordering can be explained to the user rather than being an opaque weighted sum.
     */
    internal fun score(candidate: WorkCandidate, slot: TimeRange, prefs: PlannerPreferences): Int {
        var score = 0

        score += when (val d = candidate.daysToDeadline) {
            null -> 0
            else -> when {
                d < 0 -> 120
                d == 0 -> 100
                d == 1 -> 70
                d == 2 -> 45
                d <= 4 -> 30
                d <= 7 -> 18
                else -> 8
            }
        }

        score += candidate.priority.weight
        score += candidate.importance

        // Energy fit: demanding work belongs in the peak window and not late at night.
        val demanding = candidate.difficulty == Difficulty.HARD || candidate.energy == EnergyLevel.HIGH
        if (demanding) {
            if (prefs.peakWindow.contains(slot.start)) score += 14
            if (slot.start >= prefs.eveningFatigueAfter) score -= 22
            if (prefs.energyMode == EnergyMode.LIGHT) score -= 10
        } else if (slot.start >= prefs.eveningFatigueAfter) {
            score += 8
        }

        candidate.preferredWindow?.let { window ->
            if (window.contains(slot.start)) score += 25 else score -= 10
        }

        // Reviewing the classes of the day is worth more early in the evening than late.
        if (candidate.type == BlockType.REVIEW && slot.start >= prefs.eveningFatigueAfter) score -= 12

        // Mild preference against starting something that cannot get a full session here.
        if (slot.duration < candidate.minSession * 2 && candidate.minutes > candidate.maxSession) {
            score -= 6
        }

        return score
    }

    private fun explain(candidate: WorkCandidate, range: TimeRange, prefs: PlannerPreferences): String {
        val parts = candidate.reasons.toMutableList()
        if (parts.none { it.contains("due") }) {
            candidate.daysToDeadline?.let { d ->
                parts += when {
                    d < 0 -> "it is overdue"
                    d == 0 -> "it is due today"
                    d == 1 -> "it is due tomorrow"
                    else -> "it is due in $d days"
                }
            }
        }
        if (candidate.priority.weight >= 22) parts += "it is ${candidate.priority.label.lowercase()} priority"
        if (prefs.peakWindow.contains(range.start) &&
            (candidate.difficulty == Difficulty.HARD || candidate.energy == EnergyLevel.HIGH)
        ) {
            parts += "these are your sharpest hours"
        }
        if (candidate.preferredWindow?.contains(range.start) == true) {
            parts += "it is inside the time you prefer for it"
        }
        if (parts.isEmpty()) parts += "the day had room here"
        return "Scheduled because " + joinClauses(parts.distinct()) + "."
    }

    private fun joinClauses(parts: List<String>): String = when (parts.size) {
        0 -> ""
        1 -> parts[0]
        else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }

    private companion object {
        const val MAX_MINUTE = 24 * 60
        const val MIN_CONFLICT = 5
        const val MIN_BUFFER = 10
        const val MIN_BREAK = 5
        const val MIN_FREE_BLOCK = 15
        const val MIN_FRAGMENT = 10
        const val MIN_REPORTED_SHORTFALL = 10
        const val TRANSITION_RUN = 40
        const val TRANSITION_BREAK = 10
        const val STICKY_BONUS = 12
        const val STICKY_TOLERANCE = 5
        const val LIGHT_SESSION_FACTOR = 0.75f
    }
}
