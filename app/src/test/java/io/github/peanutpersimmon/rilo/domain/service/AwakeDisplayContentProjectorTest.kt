package io.github.peanutpersimmon.rilo.domain.service

import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AwakeDisplayContentProjectorTest {
    private val zone = ZoneOffset.UTC
    private val date = LocalDate.of(2026, 9, 29)

    @Test fun crossMidnightSessionIsOwnedByItsStartDate() {
        val session = session(at(date, 23), at(date.plusDays(1), 1))

        val content = project(date, sessions = listOf(session)).single()

        assertEquals(date, content.ownerDate)
        assertEquals(at(date, 23), content.startAtMillis)
        assertEquals(at(date.plusDays(1), 1), content.endAtMillis)
    }

    @Test fun todoDoneCanIncludeOnlyTheFollowingDateOverlap() {
        val session = session(at(date, 23), at(date.plusDays(1), 1))
        val content = AwakeDisplayContentProjector.project(date.plusDays(1),
            listOf(session), emptyList(), zone, at(date.plusDays(2), 12),
            includeOverlappingActivityIntervals = true).single()
        assertEquals(date.plusDays(1), content.ownerDate)
        assertEquals(at(date.plusDays(1), 0), content.startAtMillis)
        assertEquals(at(date.plusDays(1), 1), content.endAtMillis)
    }

    @Test fun pointRecordIsOwnedByItsOccurrenceDateAndHasZeroSpan() {
        val point = session(at(date.plusDays(1), 0, 30), at(date.plusDays(1), 0, 30))
            .copy(temporalType = SessionTemporalType.POINT, endAtMillis = null)
        assertTrue(project(date, sessions = listOf(point)).isEmpty())
        val content = project(date.plusDays(1), sessions = listOf(point)).single()
        assertEquals(date.plusDays(1), content.ownerDate)
        assertEquals(point.startAtMillis, content.startAtMillis)
        assertEquals(point.startAtMillis, content.endAtMillis)
    }

    private fun project(
        displayDate: LocalDate,
        sessions: List<Session> = emptyList(),
        events: List<Event> = emptyList(),
    ) = AwakeDisplayContentProjector.project(
        displayDate = displayDate,
        sessions = sessions,
        events = events,
        zoneId = zone,
        asOfMillis = at(date.plusDays(2), 12),
        includePointRecords = true,
    )

    private fun session(start: Long, end: Long) = Session(
        id = 1,
        title = "Record",
        kind = SessionKind.ACTIVITY,
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
