package io.github.peanutpersimmon.rilo.domain.service

import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class SleepReportCalculatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 15)
    private val now = time(2026, 10, 15, 20, 0)

    private fun time(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun sleep(kind: SessionKind, start: Long, end: Long?, id: Long = 1,
        updated: Long = start): Session = Session(id = id, title = "睡眠", kind = kind,
        startAtMillis = start, endAtMillis = end, createdAtMillis = start, updatedAtMillis = updated)

    @Test fun sleepDayUsesWakeDateAndActiveUsesBedDate() {
        val normal = sleep(SessionKind.NIGHT_SLEEP, time(2026, 10, 15, 23, 30), time(2026, 10, 16, 7, 30))
        val monthEdge = sleep(SessionKind.NIGHT_SLEEP, time(2026, 10, 31, 23, 30), time(2026, 11, 1, 7, 30))
        val yearEdge = sleep(SessionKind.NIGHT_SLEEP, time(2026, 12, 31, 23, 30), time(2027, 1, 1, 7, 30))
        val nap = sleep(SessionKind.NAP, time(2026, 10, 15, 13, 0), time(2026, 10, 15, 14, 0))
        val active = sleep(SessionKind.NIGHT_SLEEP, time(2026, 10, 15, 23, 30), null)
        assertEquals(LocalDate.of(2026, 10, 16), SleepDayResolver.resolve(normal, zone))
        assertEquals(LocalDate.of(2026, 11, 1), SleepDayResolver.resolve(monthEdge, zone))
        assertEquals(LocalDate.of(2027, 1, 1), SleepDayResolver.resolve(yearEdge, zone))
        assertEquals(LocalDate.of(2026, 10, 15), SleepDayResolver.resolve(nap, zone))
        assertEquals(LocalDate.of(2026, 10, 15), SleepDayResolver.resolve(active, zone))
    }

    @Test fun crossMonthAndYearBelongToOneReportOnly() {
        val monthEdge = sleep(SessionKind.NIGHT_SLEEP, time(2026, 10, 31, 23, 30), time(2026, 11, 1, 7, 30))
        val yearEdge = sleep(SessionKind.NIGHT_SLEEP, time(2026, 12, 31, 23, 30), time(2027, 1, 1, 7, 30))
        val asOf = time(2027, 2, 1, 12, 0)
        val date = LocalDate.of(2027, 2, 1)
        assertEquals(0, SleepReportCalculator.monthly(listOf(monthEdge), emptyList(), YearMonth.of(2026, 10), zone, date, asOf).metrics.recordedNightDays)
        assertEquals(1, SleepReportCalculator.monthly(listOf(monthEdge), emptyList(), YearMonth.of(2026, 11), zone, date, asOf).metrics.recordedNightDays)
        assertEquals(0, SleepReportCalculator.annual(listOf(yearEdge), emptyList(), 2026, zone, date, asOf).metrics.recordedNightDays)
        assertEquals(1, SleepReportCalculator.annual(listOf(yearEdge), emptyList(), 2027, zone, date, asOf).metrics.recordedNightDays)
    }

    @Test fun timeAveragesUseTheExistingStatisticsAlgorithm() {
        val nights = listOf(
            sleep(SessionKind.NIGHT_SLEEP, time(2026, 10, 12, 23, 30), time(2026, 10, 13, 6, 30), 1),
            sleep(SessionKind.NIGHT_SLEEP, time(2026, 10, 14, 0, 30), time(2026, 10, 14, 7, 30), 2),
            sleep(SessionKind.NIGHT_SLEEP, time(2026, 10, 15, 1, 30), time(2026, 10, 15, 8, 30), 3),
        )
        val two = SleepReportCalculator.monthly(nights.take(2), emptyList(), YearMonth.of(2026, 10), zone, today, now)
        val all = SleepReportCalculator.monthly(nights, emptyList(), YearMonth.of(2026, 10), zone, today, now)
        assertEquals("00:00", SleepStatisticsCalculator.clock(two.metrics.averageBedMinutes!!))
        assertEquals("00:30", SleepStatisticsCalculator.clock(all.metrics.averageBedMinutes!!))
        assertEquals("07:30", SleepStatisticsCalculator.clock(all.metrics.averageWakeMinutes!!))
        val stats = SleepStatisticsCalculator.nightDays(nights, zone, now).values
        assertEquals(SleepStatisticsCalculator.averageMinutes(stats, SleepCalendarMode.BED, zone), all.metrics.averageBedMinutes)
        assertEquals(SleepStatisticsCalculator.averageMinutes(stats, SleepCalendarMode.WAKE, zone), all.metrics.averageWakeMinutes)
        assertEquals(SleepStatisticsCalculator.averageMinutes(stats, SleepCalendarMode.DURATION, zone), all.metrics.averageNightMinutes)
    }

    @Test fun representativeNightUsesLastUpdateThenStableIdAcrossStatisticsAndReports() {
        val first = sleep(SessionKind.NIGHT_SLEEP, time(2026, 10, 14, 22, 0),
            time(2026, 10, 15, 6, 0), 9, updated = 100)
        val latest = sleep(SessionKind.NIGHT_SLEEP, time(2026, 10, 14, 23, 0),
            time(2026, 10, 15, 7, 0), 2, updated = 200)
        val sameUpdate = latest.copy(id = 3)
        val nights = listOf(first, latest, sameUpdate)
        val selected = SleepStatisticsCalculator.representativeNights(nights, zone, now)
        assertEquals(3L, selected[today]?.id)
        val statistics = SleepStatisticsCalculator.nightDays(nights, zone, now).getValue(today)
        val monthly = SleepReportCalculator.monthly(nights, emptyList(), YearMonth.of(2026, 10), zone, today, now)
        val annual = SleepReportCalculator.annual(nights, emptyList(), 2026, zone, today, now)
        assertEquals(1, monthly.metrics.recordedNightDays)
        assertEquals(1, monthly.metrics.completedNightDays)
        assertEquals(statistics.durationMinutes, monthly.metrics.averageNightMinutes)
        assertEquals(monthly.metrics.averageNightMinutes, annual.metrics.averageNightMinutes)
        assertEquals(SleepStatisticsCalculator.bedtimeMinute(statistics.bedMillis!!, zone), monthly.metrics.averageBedMinutes)
        assertEquals(SleepStatisticsCalculator.minuteOfDay(statistics.wakeMillis!!, zone), annual.metrics.averageWakeMinutes)
    }

    @Test fun napsCountSessionsDaysAndDailySumWithoutChangingNightMetrics() {
        val first = sleep(SessionKind.NAP, time(2026, 10, 15, 13, 0), time(2026, 10, 15, 13, 30), 1)
        val second = sleep(SessionKind.NAP, time(2026, 10, 15, 16, 0), time(2026, 10, 15, 17, 0), 2)
        val monthly = SleepReportCalculator.monthly(emptyList(), listOf(first, second), YearMonth.of(2026, 10), zone, today, now)
        val annual = SleepReportCalculator.annual(emptyList(), listOf(first, second), 2026, zone, today, now)
        assertEquals(2, monthly.metrics.napCount)
        assertEquals(1, monthly.metrics.napDays)
        assertEquals(45, monthly.metrics.averageNapMinutes)
        assertEquals(90, monthly.days.single { it.date == today }.napMinutes)
        assertEquals(0, monthly.metrics.recordedNightDays)
        assertEquals(monthly.metrics.averageNapMinutes, annual.metrics.averageNapMinutes)
        assertEquals(SleepStatisticsCalculator.averageNapMinutes(listOf(first, second), YearMonth.of(2026, 10), zone, now), monthly.metrics.averageNapMinutes)
    }

    @Test fun activeNightIsRecordedButNeverCompletedOrTimed() {
        val active = sleep(SessionKind.NIGHT_SLEEP, time(2026, 10, 15, 19, 0), null)
        val report = SleepReportCalculator.monthly(listOf(active), emptyList(), YearMonth.of(2026, 10), zone, today, now)
        assertEquals(1, report.metrics.recordedNightDays)
        assertEquals(0, report.metrics.completedNightDays)
        assertEquals(1, report.metrics.incompleteNightDays)
        assertNull(report.metrics.averageNightMinutes)
        assertNull(report.days.single { it.date == today }.nightMinutes)
    }

    @Test fun missingDaysStopAtTodayAndFutureMonthsStayEmpty() {
        val current = SleepReportCalculator.monthly(emptyList(), emptyList(), YearMonth.of(2026, 10), zone, today, now)
        val past = SleepReportCalculator.monthly(emptyList(), emptyList(), YearMonth.of(2026, 9), zone, today, now)
        val future = SleepReportCalculator.monthly(emptyList(), emptyList(), YearMonth.of(2026, 11), zone, today, now)
        val year = SleepReportCalculator.annual(emptyList(), emptyList(), 2026, zone, today, now)
        assertEquals(15, current.metrics.unrecordedDays)
        assertEquals(30, past.metrics.unrecordedDays)
        assertEquals(0, future.metrics.unrecordedDays)
        assertEquals(0, future.days.size)
        assertEquals(288, year.metrics.elapsedDays)
        assertEquals(288, year.metrics.unrecordedDays)
    }

    @Test fun reportTrustsPersistedKindAndNeverReclassifiesByClock() {
        val earlyNap = sleep(SessionKind.NAP, time(2026, 10, 15, 5, 0), time(2026, 10, 15, 6, 0), 1)
        val middayNight = sleep(SessionKind.NIGHT_SLEEP, time(2026, 10, 15, 13, 0), time(2026, 10, 15, 14, 0), 2)
        val report = SleepReportCalculator.monthly(listOf(middayNight, earlyNap), listOf(middayNight, earlyNap), YearMonth.of(2026, 10), zone, today, now)
        assertEquals(1, report.metrics.recordedNightDays)
        assertEquals(1, report.metrics.napCount)
    }

    @Test fun objectiveTextAndPngNamesUseTheReportModel() {
        val monthly = SleepReportCalculator.monthly(emptyList(), emptyList(), YearMonth.of(2026, 10), zone, today, now)
        val annual = SleepReportCalculator.annual(emptyList(), emptyList(), 2026, zone, today, now)
        assertEquals("RILO_Sleep_Monthly_2026_10.png", SleepReportFileName.forReport(monthly))
        assertEquals("RILO_Sleep_Annual_2026.png", SleepReportFileName.forReport(annual))
        assertTrue(monthly.summary.none { "未记录" in it })
        assertTrue(monthly.summary.none { "梦" in it || "夜醒" in it || "醒来状态" in it })
    }
}
