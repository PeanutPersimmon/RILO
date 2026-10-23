package io.github.peanutpersimmon.rilo.domain.usecase

import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordEditorDefaultsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 10, 16)
    private val dayStart = at(today, 0, 0)

    @Test fun onlyNightSleepUsesWakeTime() {
        val now = at(today, 9, 0)
        val night = row(SessionKind.NIGHT_SLEEP, at(today, 0, 30), at(today, 8, 0))

        assertEquals(at(today, 8, 0), latestPreviousActualEnd(listOf(night), dayStart, now))
    }

    @Test fun laterRecordWinsOverNightSleep() {
        val rows = listOf(
            row(SessionKind.NIGHT_SLEEP, at(today, 0, 30), at(today, 8, 0)),
            row(SessionKind.ACTIVITY, at(today, 8, 0), at(today, 10, 0)),
        )

        assertEquals(at(today, 10, 0), latestPreviousActualEnd(rows, dayStart, at(today, 11, 0)))
    }

    @Test fun laterNapWinsOverRecord() {
        val rows = listOf(
            row(SessionKind.ACTIVITY, at(today, 8, 0), at(today, 11, 30)),
            row(SessionKind.NAP, at(today, 12, 40), at(today, 13, 20)),
        )

        assertEquals(at(today, 13, 20), latestPreviousActualEnd(rows, dayStart, at(today, 14, 10)))
    }

    @Test fun laterRecordWinsOverEarlierNap() {
        val rows = listOf(
            row(SessionKind.NAP, at(today, 9, 0), at(today, 9, 30)),
            row(SessionKind.ACTIVITY, at(today, 9, 30), at(today, 11, 0)),
        )

        assertEquals(at(today, 11, 0), latestPreviousActualEnd(rows, dayStart, at(today, 12, 0)))
    }

    @Test fun crossMidnightNightSleepUsesEndOnCurrentDay() {
        val night = row(
            SessionKind.NIGHT_SLEEP,
            at(today.minusDays(1), 23, 30),
            at(today, 8, 0),
        )

        assertEquals(at(today, 8, 0), latestPreviousActualEnd(listOf(night), dayStart, at(today, 9, 0)))
    }

    @Test fun activeNapIsNotBoundary() {
        val activeNap = row(SessionKind.NAP, at(today, 12, 30), null)

        assertNull(latestPreviousActualEnd(listOf(activeNap), dayStart, at(today, 13, 0)))
    }

    @Test fun activeNightSleepIsNotBoundary() {
        val activeNight = row(SessionKind.NIGHT_SLEEP, at(today, 1, 0), null)

        assertNull(latestPreviousActualEnd(listOf(activeNight), dayStart, at(today, 9, 0)))
    }

    @Test fun futureEndIsNotBoundary() {
        val future = row(SessionKind.NAP, at(today, 12, 30), at(today, 14, 0))

        assertNull(latestPreviousActualEnd(listOf(future), dayStart, at(today, 13, 0)))
    }

    @Test fun noCompletedBoundaryKeepsFallbackAvailable() {
        val point = row(SessionKind.ACTIVITY, at(today, 8, 0), null)
            .copy(temporalType = SessionTemporalType.POINT)

        assertNull(latestPreviousActualEnd(listOf(point), dayStart, at(today, 9, 0)))
    }

    @Test fun mixedCandidatesUseMaximumEndWithoutKindPriority() {
        val now = at(today, 14, 10)
        val rows = listOf(
            row(SessionKind.NIGHT_SLEEP, at(today, 0, 30), at(today, 8, 0)),
            row(SessionKind.NAP, at(today, 9, 0), at(today, 9, 30)),
            row(SessionKind.ACTIVITY, at(today, 9, 30), at(today, 11, 30)),
            row(SessionKind.NAP, at(today, 12, 40), at(today, 13, 20)),
            row(SessionKind.ACTIVITY, at(today, 14, 0), null),
            row(SessionKind.NIGHT_SLEEP, at(today, 14, 0), at(today, 15, 0)),
        )

        assertEquals(at(today, 13, 20), latestPreviousActualEnd(rows, dayStart, now))
    }

    @Test fun boundaryEndingExactlyNowIsAllowed() {
        val now = at(today, 13, 0)
        val boundary = row(SessionKind.NAP, at(today, 12, 30), now)

        assertEquals(now, latestPreviousActualEnd(listOf(boundary), dayStart, now))
    }

    private fun row(kind: SessionKind, start: Long, end: Long?) = Session(
        title = "事实",
        kind = kind,
        temporalType = SessionTemporalType.INTERVAL,
        startAtMillis = start,
        endAtMillis = end,
        createdAtMillis = 1,
        updatedAtMillis = 1,
    )

    private fun at(date: LocalDate, hour: Int, minute: Int): Long =
        date.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
}
