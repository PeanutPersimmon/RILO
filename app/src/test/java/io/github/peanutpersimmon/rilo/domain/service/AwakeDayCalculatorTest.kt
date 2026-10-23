package io.github.peanutpersimmon.rilo.domain.service

import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class AwakeDayCalculatorTest {
    private val zone = ZoneOffset.UTC
    private val date = LocalDate.of(2026, 9, 19)

    @Test fun nightSleepEndAndNextNightSleepStartDefineAwakeDay() {
        val previous = sleep(1, date.minusDays(1), 23, 40, date, 7, 50)
        val next = sleep(2, date.plusDays(1), 0, 42, date.plusDays(1), 8, 0)
        val result = calculate(listOf(previous, next), at(date.plusDays(1), 0, 0))
        assertEquals(at(date, 7, 50), result.interval.startMillis)
        assertEquals(at(date.plusDays(1), 0, 42), result.interval.endMillis)
    }

    @Test fun awakeDayCanCrossMidnight() {
        val previous = sleep(1, date.minusDays(1), 23, 0, date, 8, 0)
        val next = sleep(2, date.plusDays(1), 1, 0, date.plusDays(1), 8, 0)
        val result = calculate(listOf(previous, next), at(date.plusDays(1), 0, 30))
        assertEquals(17 * 60, result.interval.durationMillis / 60_000)
    }

    @Test fun napDoesNotChangeBoundaries() {
        val nap = session(9, SessionKind.NAP, at(date, 13, 0), at(date, 14, 0))
        val result = calculate(listOf(nap), at(date, 15, 0))
        assertEquals(at(date, 7, 0), result.interval.startMillis)
        assertEquals(at(date.plusDays(1), 0, 0), result.interval.endMillis)
    }

    @Test fun noSleepUsesCallerDefaults() {
        val result = calculate(emptyList(), at(date, 12, 0))
        assertEquals(at(date, 7, 0), result.interval.startMillis)
        assertEquals(at(date.plusDays(1), 0, 0), result.interval.endMillis)
        assertNull(result.wakeAtMillis)
        assertNull(result.sleepAtMillis)
    }

    @Test fun previousSleepOnlyUsesActualWakeAndDefaultEnd() {
        val previous = sleep(1, date.minusDays(1), 23, 0, date, 8, 15)
        val result = calculate(listOf(previous), at(date, 20, 0))
        assertEquals(at(date, 8, 15), result.interval.startMillis)
        assertEquals(at(date.plusDays(1), 0, 0), result.interval.endMillis)
    }

    @Test fun unresolvedCurrentDayExtendsPastDefaultEndToNow() {
        val now = at(date.plusDays(1), 1, 30)
        val result = calculate(emptyList(), now)
        assertEquals(now, result.interval.endMillis)
    }

    @Test fun sleepAfterMidnightClosesPreviousAwakeDay() {
        val next = sleep(2, date.plusDays(1), 0, 42, date.plusDays(1), 8, 0)
        val result = calculate(listOf(next), at(date, 23, 0))
        assertEquals(at(date.plusDays(1), 0, 42), result.interval.endMillis)
        assertEquals(next.startAtMillis, result.sleepAtMillis)
    }

    private fun calculate(sleeps: List<Session>, asOf: Long) = AwakeDayCalculator.calculate(
        calendarDate = date,
        nightSleeps = sleeps,
        defaultWakeMinute = 7 * 60,
        defaultSleepMinute = 0,
        zoneId = zone,
        asOfMillis = asOf,
    )

    private fun sleep(
        id: Long,
        startDate: LocalDate,
        startHour: Int,
        startMinute: Int,
        endDate: LocalDate,
        endHour: Int,
        endMinute: Int,
    ) = session(
        id,
        SessionKind.NIGHT_SLEEP,
        at(startDate, startHour, startMinute),
        at(endDate, endHour, endMinute),
    )

    private fun session(id: Long, kind: SessionKind, start: Long, end: Long?) = Session(
        id = id,
        title = kind.name,
        kind = kind,
        startAtMillis = start,
        endAtMillis = end,
        createdAtMillis = start,
        updatedAtMillis = start,
    )

    private fun at(date: LocalDate, hour: Int, minute: Int): Long = LocalDateTime.of(
        date.year,
        date.month,
        date.dayOfMonth,
        hour,
        minute,
    ).toInstant(zone).toEpochMilli()
}
