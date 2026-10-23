package io.github.peanutpersimmon.rilo.domain.service

import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Pure report projection from RILO Sessions. Sleep kind is never inferred on read. */
object SleepReportCalculator {
    fun monthly(nights: List<Session>, naps: List<Session>, month: YearMonth,
        zone: ZoneId, today: LocalDate, asOfMillis: Long): SleepMonthlyReport {
        val start = month.atDay(1)
        val end = month.atEndOfMonth()
        val selected = select(nights, naps, start, end, zone, today, asOfMillis)
        val days = if (selected.cutoff.isBefore(start)) emptyList() else dates(start, selected.cutoff).map { date ->
            val night = selected.nightDays[date]
            SleepReportDay(date,
                night?.bedMillis?.let { SleepStatisticsCalculator.bedtimeMinute(it, zone) },
                night?.wakeMillis?.let { SleepStatisticsCalculator.minuteOfDay(it, zone) },
                night?.durationMinutes,
                selected.napsByDay[date]?.sumOf(::durationMinutes))
        }
        return SleepMonthlyReport(month, selected.metrics, days,
            SleepReportTextFormatter.monthly(month, selected.metrics))
    }

    fun annual(nights: List<Session>, naps: List<Session>, year: Int,
        zone: ZoneId, today: LocalDate, asOfMillis: Long): SleepAnnualReport {
        val selected = select(nights, naps, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31),
            zone, today, asOfMillis)
        return SleepAnnualReport(year, selected.metrics, SleepReportTextFormatter.annual(year, selected.metrics))
    }

    private data class Selection(
        val cutoff: LocalDate,
        val nightDays: Map<LocalDate, SleepDay>,
        val napsByDay: Map<LocalDate, List<Session>>,
        val metrics: SleepReportMetrics,
    )

    private fun select(nights: List<Session>, naps: List<Session>, start: LocalDate, end: LocalDate,
        zone: ZoneId, today: LocalDate, asOfMillis: Long): Selection {
        val cutoff = minOf(end, today)
        val nightDays = SleepStatisticsCalculator.nightDays(nights, zone, asOfMillis)
            .filterKeys { it >= start && it <= cutoff }
        val completedNaps = naps.filter { it.kind == SessionKind.NAP &&
            it.temporalType == SessionTemporalType.INTERVAL && it.endAtMillis != null &&
            it.endAtMillis > it.startAtMillis && it.endAtMillis <= asOfMillis &&
            SleepDayResolver.resolve(it, zone) in start..cutoff }
        val napsByDay = completedNaps.groupBy { SleepDayResolver.resolve(it, zone) }
        val completedNights = nightDays.values.mapNotNull(SleepDay::durationMinutes)
        val elapsed = if (cutoff < start) 0 else ChronoUnit.DAYS.between(start, cutoff).toInt() + 1
        val completedCount = completedNights.size
        val metrics = SleepReportMetrics(
            elapsedDays = elapsed,
            recordedNightDays = nightDays.size,
            completedNightDays = completedCount,
            incompleteNightDays = nightDays.size - completedCount,
            unrecordedDays = elapsed - nightDays.size,
            averageBedMinutes = SleepStatisticsCalculator.averageMinutes(nightDays.values, SleepCalendarMode.BED, zone),
            averageWakeMinutes = SleepStatisticsCalculator.averageMinutes(nightDays.values, SleepCalendarMode.WAKE, zone),
            averageNightMinutes = SleepStatisticsCalculator.averageMinutes(nightDays.values, SleepCalendarMode.DURATION, zone),
            nightDurationBuckets = buckets(completedNights, night = true),
            napCount = completedNaps.size,
            napDays = napsByDay.size,
            averageNapMinutes = completedNaps.map(::durationMinutes).averageRoundedOrNull(),
            napDurationBuckets = buckets(completedNaps.map(::durationMinutes), night = false),
        )
        return Selection(cutoff, nightDays, napsByDay, metrics)
    }

    private fun durationMinutes(session: Session): Int =
        ((requireNotNull(session.endAtMillis) - session.startAtMillis) / 60_000L).toInt()

    private fun dates(start: LocalDate, end: LocalDate): List<LocalDate> =
        generateSequence(start) { it.plusDays(1) }.takeWhile { it <= end }.toList()

    private fun List<Int>.averageRoundedOrNull(): Int? =
        takeIf { it.isNotEmpty() }?.average()?.roundToInt()

    private fun buckets(minutes: List<Int>, night: Boolean): List<SleepDurationBucket> {
        val labels = if (night) listOf("<6h", "6–7h", "7–9h", "9–10h", ">10h")
            else listOf("≤30m", "31–60m", "61–90m", ">90m")
        val counts = IntArray(labels.size)
        minutes.forEach { value ->
            val index = if (night) when {
                value < 360 -> 0
                value < 420 -> 1
                value <= 540 -> 2
                value <= 600 -> 3
                else -> 4
            } else when {
                value <= 30 -> 0
                value <= 60 -> 1
                value <= 90 -> 2
                else -> 3
            }
            counts[index]++
        }
        return labels.mapIndexed { index, label -> SleepDurationBucket(label, counts[index]) }
    }
}
