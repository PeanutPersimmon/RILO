package io.github.peanutpersimmon.rilo.domain.service

import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.time.TimeInterval
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DurationRank(val id: Long?, val durationMillis: Long)
data class DurationPoint(val label: String, val durationMillis: Long)

data class ActualStatistics(
    val segments: List<ActualSegment>,
    val period: StatisticsPeriod,
    val zone: ZoneId,
) {
    val totalMillis: Long get() = segments.sumOf { it.interval.durationMillis }
    private fun categoryKey(segment: ActualSegment): Long = segment.categoryId ?: -1L
    fun rank(): List<DurationRank> =
        segments.groupBy(::categoryKey)
            .map { (id, entries) -> DurationRank(id, entries.sumOf { it.interval.durationMillis }) }
            .filter { it.durationMillis > 0L }
            .sortedWith(compareByDescending<DurationRank> { it.durationMillis }.thenBy { it.id })

    fun selected(id: Long?): List<ActualSegment> = segments.filter { categoryKey(it) == id }

    fun trend(selected: List<ActualSegment>): List<DurationPoint> = period.buckets().map { bucket ->
        val bounds = TimeInterval(bucket.start.atStartOfDay(zone).toInstant().toEpochMilli(), bucket.endExclusive.atStartOfDay(zone).toInstant().toEpochMilli())
        DurationPoint(bucket.label, selected.sumOf { it.interval.intersection(bounds)?.durationMillis ?: 0L })
    }

    fun daily(selected: List<ActualSegment>): Map<LocalDate, Long> {
        val totals = mutableMapOf<LocalDate, Long>()
        selected.forEach { segment ->
            var date = Instant.ofEpochMilli(segment.interval.startMillis).atZone(zone).toLocalDate()
            while (date < period.endExclusive) {
                val next = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val duration = segment.interval.intersection(TimeInterval(date.atStartOfDay(zone).toInstant().toEpochMilli(), next))?.durationMillis ?: 0L
                if (duration > 0L) totals[date] = (totals[date] ?: 0L) + duration
                if (next >= segment.interval.endMillis) break
                date = date.plusDays(1)
            }
        }
        return totals
    }
}

object ActualStatisticsCalculator {
    /** Only completed activity intervals contribute ordinary duration. */
    fun calculate(
        sessions: List<Session>, period: StatisticsPeriod,
        zone: ZoneId, asOfMillis: Long,
    ): DomainResult<ActualStatistics> {
        val confirmedSessions = sessions.filter {
            it.kind == SessionKind.ACTIVITY &&
                it.temporalType == SessionTemporalType.INTERVAL &&
                it.endAtMillis != null && it.endAtMillis > it.startAtMillis &&
                it.endAtMillis <= asOfMillis
        }
        return when (val result = ActualTimeResolver.resolve(confirmedSessions, period.interval(zone), asOfMillis)) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> DomainResult.Success(
                ActualStatistics(
                    segments = result.value.segments,
                    period = period,
                    zone = zone,
                ),
            )
        }
    }
}
