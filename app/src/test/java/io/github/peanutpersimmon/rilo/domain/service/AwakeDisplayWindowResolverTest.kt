package io.github.peanutpersimmon.rilo.domain.service

import io.github.peanutpersimmon.rilo.domain.model.BoundarySource
import io.github.peanutpersimmon.rilo.domain.model.AwakeDisplayContent
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AwakeDisplayWindowResolverTest {
    private val zone = ZoneOffset.UTC
    private val date = LocalDate.of(2026, 9, 29)
    private val wake = sleep(1, at(date.minusDays(1), 23), at(date, 10))

    @Test fun emptyDateUsesDefaultSevenToTwentyTwo() {
        assertWindow(historical(), at(date, 7), at(date, 22),
            BoundarySource.DEFAULT, BoundarySource.DEFAULT)
    }

    @Test fun earlyOwnedContentMovesFallbackStartEarlier() {
        val result = historical(content = listOf(content(date, 5, 30, 6, 30)))
        assertWindow(result, at(date, 5, 30), at(date, 22),
            BoundarySource.DEFAULT, BoundarySource.DEFAULT)
    }

    @Test fun lateOwnedPointMovesFallbackEndLater() {
        val result = historical(content = listOf(point(date, 23, 30)))
        assertWindow(result, at(date, 7), at(date, 23, 30) + 1,
            BoundarySource.DEFAULT, BoundarySource.DEFAULT)
    }

    @Test fun earlyOwnedPointMovesFallbackStartEarlier() {
        val result = historical(content = listOf(point(date, 5, 30)))
        assertWindow(result, at(date, 5, 30), at(date, 22),
            BoundarySource.DEFAULT, BoundarySource.DEFAULT)
    }

    @Test fun crossMidnightOwnedIntervalExtendsItsStartDate() {
        val result = historical(content = listOf(
            AwakeDisplayContent(date, at(date, 23), at(date.plusDays(1), 1)),
        ))
        assertWindow(result, at(date, 7), at(date.plusDays(1), 1),
            BoundarySource.DEFAULT, BoundarySource.DEFAULT)
    }

    @Test fun nextDayStandalonePointCannotExtendPreviousDate() {
        val result = historical(content = listOf(point(date.plusDays(1), 0, 30)))
        assertWindow(result, at(date, 7), at(date, 22),
            BoundarySource.DEFAULT, BoundarySource.DEFAULT)
    }

    @Test fun realWakeIsHardStartAgainstEarlierContent() {
        val result = historical(
            sleeps = listOf(wake),
            content = listOf(point(date, 6)),
        )
        assertWindow(result, at(date, 10), at(date, 22),
            BoundarySource.REAL, BoundarySource.DEFAULT)
    }

    @Test fun realSleepIsHardEndAgainstLaterContent() {
        val realSleep = sleep(2, at(date, 23), null)
        val result = historical(
            sleeps = listOf(realSleep),
            content = listOf(point(date, 23, 30)),
        )
        assertWindow(result, at(date, 7), at(date, 23),
            BoundarySource.DEFAULT, BoundarySource.REAL)
    }

    @Test fun realWakeAndCrossMidnightSleepRemainComplete() {
        val nextSleep = sleep(2, at(date.plusDays(1), 2), null)
        val result = historical(sleeps = listOf(wake, nextSleep))
        assertWindow(result, at(date, 10), at(date.plusDays(1), 2),
            BoundarySource.REAL, BoundarySource.REAL)
    }

    @Test fun realSleepAtNextDayNineIsNotTruncated() {
        val nextSleep = sleep(2, at(date.plusDays(1), 9), null)
        val result = historical(sleeps = listOf(wake, nextSleep))
        assertWindow(result, at(date, 10), at(date.plusDays(1), 9),
            BoundarySource.REAL, BoundarySource.REAL)
    }

    @Test fun todayAfterDefaultEndExtendsToNow() {
        val now = at(date, 23, 45)
        assertWindow(today(now = now), at(date, 7), now,
            BoundarySource.DEFAULT, BoundarySource.NOW)
    }

    @Test fun todayExactlyAtDefaultEndKeepsDefaultBoundarySource() {
        assertWindow(today(now = at(date, 22)), at(date, 7), at(date, 22),
            BoundarySource.DEFAULT, BoundarySource.DEFAULT)
    }

    @Test fun todayBeforeDefaultStartStillUsesCurrentNaturalDateWindow() {
        val result = today(now = at(date, 3))
        assertWindow(result, at(date, 7), at(date, 22),
            BoundarySource.DEFAULT, BoundarySource.DEFAULT)
        assertEquals(date, result.anchorDate)
    }

    @Test fun historicalNeverUsesNow() {
        assertWindow(historical(), at(date, 7), at(date, 22),
            BoundarySource.DEFAULT, BoundarySource.DEFAULT)
    }

    private fun today(
        sleeps: List<Session> = emptyList(),
        content: List<AwakeDisplayContent> = emptyList(),
        now: Long,
    ) = AwakeDisplayWindowResolver.resolveToday(
        displayDate = date,
        nightSleeps = sleeps,
        relevantContent = content,
        defaultWakeMinute = 7 * 60,
        defaultSleepMinute = 22 * 60,
        zoneId = zone,
        nowMillis = now,
    )

    private fun historical(
        sleeps: List<Session> = emptyList(),
        content: List<AwakeDisplayContent> = emptyList(),
    ) = AwakeDisplayWindowResolver.resolveHistorical(
        anchorDate = date,
        nightSleeps = sleeps,
        relevantContent = content,
        defaultWakeMinute = 7 * 60,
        defaultSleepMinute = 22 * 60,
        zoneId = zone,
    )

    private fun point(owner: LocalDate, hour: Int, minute: Int = 0) =
        AwakeDisplayContent(owner, at(owner, hour, minute))

    private fun content(owner: LocalDate, startHour: Int, startMinute: Int,
        endHour: Int, endMinute: Int) = AwakeDisplayContent(
        owner,
        at(owner, startHour, startMinute),
        at(owner, endHour, endMinute),
    )

    private fun assertWindow(
        result: io.github.peanutpersimmon.rilo.domain.model.AwakeDisplayWindow,
        start: Long,
        end: Long,
        startSource: BoundarySource,
        endSource: BoundarySource,
    ) {
        assertEquals(start, result.displayInterval.startMillis)
        assertEquals(end, result.displayInterval.endMillis)
        assertEquals(startSource, result.startSource)
        assertEquals(endSource, result.endSource)
    }

    private fun sleep(id: Long, start: Long, end: Long?) = Session(
        id = id,
        title = "Night sleep",
        kind = SessionKind.NIGHT_SLEEP,
        startAtMillis = start,
        endAtMillis = end,
        createdAtMillis = start,
        updatedAtMillis = start,
    )

    private fun at(day: LocalDate, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(day.year, day.month, day.dayOfMonth, hour, minute)
            .toInstant(zone)
            .toEpochMilli()
}
