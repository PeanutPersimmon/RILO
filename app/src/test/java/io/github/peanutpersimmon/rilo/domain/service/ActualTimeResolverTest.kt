package io.github.peanutpersimmon.rilo.domain.service

import io.github.peanutpersimmon.rilo.domain.model.*
import io.github.peanutpersimmon.rilo.domain.time.TimeInterval
import org.junit.Assert.*
import org.junit.Test

class ActualTimeResolverTest {
    private fun session(id: Long, start: Long, end: Long?, type: SessionTemporalType = SessionTemporalType.INTERVAL) =
        Session(id = id, title = "record", temporalType = type, startAtMillis = start,
            endAtMillis = end, createdAtMillis = 0, updatedAtMillis = 0)

    @Test fun pointDoesNotProduceDurationOrOverlap() {
        val result = ActualTimeResolver.resolve(
            listOf(session(1, 0, 100), session(2, 50, null, SessionTemporalType.POINT)),
            TimeInterval(0, 200), 200)
        assertTrue(result is DomainResult.Success)
        assertEquals(listOf(1L), (result as DomainResult.Success).value.segments.map { it.sourceId })
    }

    @Test fun completedIntervalIsClipped() {
        val result = ActualTimeResolver.resolve(listOf(session(1, 0, 100)), TimeInterval(20, 80), 200)
        assertEquals(60L, (result as DomainResult.Success).value.segments.single().interval.durationMillis)
    }

    @Test fun overlappingIntervalsRemainInvalid() {
        val result = ActualTimeResolver.resolve(
            listOf(session(1, 0, 100), session(2, 50, 150)), TimeInterval(0, 200), 200)
        assertEquals(DomainError.SessionOverlap, (result as DomainResult.Failure).error)
    }
}

