package io.github.peanutpersimmon.rilo.domain.time

/** A non-empty half-open interval [startMillis, endMillis). */
data class TimeInterval(
    val startMillis: Long,
    val endMillis: Long,
) {
    init {
        require(startMillis < endMillis) { "A TimeInterval must have positive duration." }
    }

    val durationMillis: Long get() = endMillis - startMillis

    fun intersects(other: TimeInterval): Boolean =
        startMillis < other.endMillis && other.startMillis < endMillis

    fun contains(instantMillis: Long): Boolean =
        instantMillis >= startMillis && instantMillis < endMillis

    fun contains(other: TimeInterval): Boolean =
        other.startMillis >= startMillis && other.endMillis <= endMillis

    fun intersection(other: TimeInterval): TimeInterval? {
        val start = maxOf(startMillis, other.startMillis)
        val end = minOf(endMillis, other.endMillis)
        return if (start < end) TimeInterval(start, end) else null
    }

    fun clipTo(bounds: TimeInterval): TimeInterval? = intersection(bounds)

    fun subtract(other: TimeInterval): List<TimeInterval> {
        val overlap = intersection(other) ?: return listOf(this)
        return buildList(2) {
            if (startMillis < overlap.startMillis) add(TimeInterval(startMillis, overlap.startMillis))
            if (overlap.endMillis < endMillis) add(TimeInterval(overlap.endMillis, endMillis))
        }
    }
}

object IntervalMath {
    fun subtract(
        source: TimeInterval,
        occupied: Iterable<TimeInterval>,
    ): List<TimeInterval> = occupied
        .sortedWith(compareBy(TimeInterval::startMillis, TimeInterval::endMillis))
        .fold(listOf(source)) { remaining, blocker -> remaining.flatMap { it.subtract(blocker) } }

    fun hasAnyOverlap(intervals: Iterable<TimeInterval>): Boolean {
        val sorted = intervals.sortedWith(compareBy(TimeInterval::startMillis, TimeInterval::endMillis))
        return sorted.zipWithNext().any { (first, second) -> first.intersects(second) }
    }
}

