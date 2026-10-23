package io.github.peanutpersimmon.rilo.domain.service

import io.github.peanutpersimmon.rilo.domain.model.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test
import io.github.peanutpersimmon.rilo.presentation.statistics.StatisticsSection

class StatisticsCalculatorTest {
    @Test fun statisticsSectionsContainOnlyCategoryAndSleep() {
        assertEquals(listOf(StatisticsSection.CATEGORY, StatisticsSection.SLEEP), StatisticsSection.entries)
    }
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 9, 22)
    private val base = day.atStartOfDay(zone).toInstant().toEpochMilli()
    private val hour = 3_600_000L
    private val period = StatisticsPeriod(StatisticsGranularity.DAY, day)

    private fun record(id: Long, start: Long, end: Long?, kind: SessionKind = SessionKind.ACTIVITY,
        type: SessionTemporalType = SessionTemporalType.INTERVAL, categoryId: Long? = null,
        tags: Set<Long> = emptySet(), sourceEventId: Long? = null) = Session(id = id, title = "record",
            kind = kind, temporalType = type, startAtMillis = start, endAtMillis = end,
            categoryId = categoryId, sourceEventId = sourceEventId,
            createdAtMillis = base, updatedAtMillis = base)

    @Test fun onlyCompletedActivityIntervalsContribute() {
        val rows = listOf(
            record(1, base, base + hour, categoryId = 7, tags = setOf(3)),
            record(2, base + 2 * hour, null, type = SessionTemporalType.POINT, categoryId = 7, tags = setOf(3)),
            record(3, base + 3 * hour, null),
            record(4, base + 4 * hour, base + 5 * hour, SessionKind.NIGHT_SLEEP),
            record(5, base + 6 * hour, base + 7 * hour, SessionKind.NAP),
            record(6, base + 8 * hour, base + 9 * hour, categoryId = 7, tags = setOf(3), sourceEventId = 40),
        )
        val stats = (ActualStatisticsCalculator.calculate(rows, period, zone, base + 10 * hour) as DomainResult.Success).value
        assertEquals(2 * hour, stats.totalMillis)
        assertEquals(setOf(1L, 6L), stats.segments.map { it.sourceId }.toSet())
        assertEquals(listOf(DurationRank(7, 2 * hour)), stats.rank())
        assertTrue(stats.trend(stats.segments).isEmpty()) // Day has no trend buckets.
        val weekStats = (ActualStatisticsCalculator.calculate(rows,
            StatisticsPeriod(StatisticsGranularity.WEEK, day), zone, base + 10 * hour) as DomainResult.Success).value
        assertEquals(2 * hour, weekStats.trend(weekStats.segments).sumOf { it.durationMillis })
        assertEquals(mapOf(day to 2 * hour), stats.daily(stats.segments))
        assertEquals(2 * hour, stats.selected(7).sumOf { it.interval.durationMillis })
    }

    @Test fun crossMidnightIntervalIsClippedToPeriod() {
        val stats = (ActualStatisticsCalculator.calculate(
            listOf(record(1, base - hour, base + hour)), period, zone, base + 2 * hour) as DomainResult.Success).value
        assertEquals(hour, stats.totalMillis)
    }

    @Test fun sleepCalendarAndNapTotalsRemainStable() {
        val night = Session(id = 1, title = "night", kind = SessionKind.NIGHT_SLEEP,
            startAtMillis = base - hour, endAtMillis = base + 7 * hour,
            createdAtMillis = base, updatedAtMillis = base)
        val nap = Session(id = 2, title = "nap", kind = SessionKind.NAP,
            startAtMillis = base + 10 * hour, endAtMillis = base + 10 * hour + 30 * 60_000L,
            createdAtMillis = base, updatedAtMillis = base)
        val nights = SleepStatisticsCalculator.nightDays(listOf(night), zone)
        assertEquals(480, nights[day]?.durationMinutes)
        assertEquals(30, SleepStatisticsCalculator.averageNapMinutes(listOf(nap),
            YearMonth.from(day), zone, base + 15 * hour))
        assertTrue(SleepStatisticsCalculator.nightDays(listOf(night.copy(
            temporalType = SessionTemporalType.POINT)), zone).isEmpty())
        assertEquals(null, SleepStatisticsCalculator.averageNapMinutes(listOf(nap.copy(
            temporalType = SessionTemporalType.POINT)), YearMonth.from(day), zone, base + 15 * hour))
    }

    @Test fun periodAndDstBoundariesRemainStable() {
        val month = StatisticsPeriod(StatisticsGranularity.MONTH, day)
        assertEquals(LocalDate.of(2026, 9, 1), month.start)
        assertEquals(LocalDate.of(2026, 10, 1), month.endExclusive)
        val dst = StatisticsPeriod(StatisticsGranularity.DAY, LocalDate.of(2026, 3, 8))
        assertEquals(23 * hour, dst.interval(ZoneId.of("America/New_York")).durationMillis)
    }
}

