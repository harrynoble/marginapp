package com.margin.app.domain.model

/** A half-open interval of minutes within a single day: [start, end). */
data class TimeRange(val start: Int, val end: Int) : Comparable<TimeRange> {

    init {
        require(end >= start) { "TimeRange end ($end) precedes start ($start)" }
    }

    val duration: Int get() = end - start

    val isEmpty: Boolean get() = end <= start

    fun overlaps(other: TimeRange): Boolean = start < other.end && other.start < end

    fun contains(minute: Int): Boolean = minute in start until end

    fun contains(other: TimeRange): Boolean = other.start >= start && other.end <= end

    fun intersect(other: TimeRange): TimeRange? {
        val s = maxOf(start, other.start)
        val e = minOf(end, other.end)
        return if (e > s) TimeRange(s, e) else null
    }

    /** Removes [other] from this range, yielding zero, one or two remaining pieces. */
    fun minus(other: TimeRange): List<TimeRange> {
        if (!overlaps(other)) return listOf(this)
        val pieces = mutableListOf<TimeRange>()
        if (other.start > start) pieces += TimeRange(start, other.start)
        if (other.end < end) pieces += TimeRange(other.end, end)
        return pieces
    }

    fun shiftedTo(newStart: Int): TimeRange = TimeRange(newStart, newStart + duration)

    fun take(minutes: Int): TimeRange = TimeRange(start, minOf(end, start + minutes))

    override fun compareTo(other: TimeRange): Int {
        val byStart = start.compareTo(other.start)
        return if (byStart != 0) byStart else end.compareTo(other.end)
    }

    companion object {
        fun of(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) =
            TimeRange(startHour * 60 + startMinute, endHour * 60 + endMinute)
    }
}

/** Subtracts every range in [blocks] from this list of free intervals. */
fun List<TimeRange>.subtractAll(blocks: List<TimeRange>): List<TimeRange> {
    var result = this
    for (block in blocks) {
        result = result.flatMap { it.minus(block) }.filter { !it.isEmpty }
    }
    return result.sorted()
}
