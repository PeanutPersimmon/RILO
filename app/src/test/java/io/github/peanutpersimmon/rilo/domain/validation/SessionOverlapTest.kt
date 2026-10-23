package io.github.peanutpersimmon.rilo.domain.validation

import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionOverlapTest {
    @Test fun sleepMustRemainIntervalWithoutOrdinaryMetadata() {
        for (kind in listOf(SessionKind.NIGHT_SLEEP, SessionKind.NAP)) {
            val sleep = session(id = 1, start = 100 * 60_000L, end = 200 * 60_000L).copy(kind = kind)
            assertTrue(DataValidator.validateSession(sleep).isEmpty())
            assertTrue(ValidationError.SESSION_SLEEP_POINT in DataValidator.validateSession(
                sleep.copy(temporalType = SessionTemporalType.POINT, endAtMillis = null)))
            listOf(sleep.copy(categoryId = 3), sleep.copy(sourceEventId = 4))
                .forEach { assertTrue(ValidationError.SESSION_SLEEP_METADATA in DataValidator.validateSession(it)) }
        }
    }

    @Test
    fun exactIntervals_overlap() = assertOverlap(100, 200, 100, 200)

    @Test
    fun partialIntervals_overlap() = assertOverlap(100, 200, 150, 250)

    @Test
    fun containingInterval_overlaps() = assertOverlap(100, 300, 150, 200)

    @Test
    fun adjacentIntervals_doNotOverlap() {
        assertFalse(DataValidator.sessionsOverlap(100, 200, 200, 300))
    }

    @Test
    fun recordBoundaryAllowsEqualAndLaterButRejectsOneMinuteEarlierForEveryActualKind() {
        val previousEnd = 10 * 60 * 60_000L + 50 * 60_000L
        listOf(SessionKind.ACTIVITY, SessionKind.NAP, SessionKind.NIGHT_SLEEP).forEachIndexed { index, kind ->
            val previous = session(
                id = index.toLong() + 1,
                start = previousEnd - 60 * 60_000L,
                end = previousEnd,
            ).copy(kind = kind)
            assertFalse(DataValidator.overlapsAnySession(
                listOf(previous), session(100, previousEnd, previousEnd + 30 * 60_000L),
            ))
            assertTrue(DataValidator.overlapsAnySession(
                listOf(previous), session(101, previousEnd - 60_000L, previousEnd + 30 * 60_000L),
            ))
            assertFalse(DataValidator.overlapsAnySession(
                listOf(previous), session(102, previousEnd + 60_000L, previousEnd + 30 * 60_000L),
            ))
        }
    }

    @Test
    fun separatedIntervals_doNotOverlap() {
        assertFalse(DataValidator.sessionsOverlap(100, 150, 200, 300))
    }

    @Test
    fun activeSession_overlapsLaterInterval() = assertOverlap(100, null, 1_000, 2_000)

    @Test
    fun intervalBeforeActiveSession_doesNotOverlap() {
        assertFalse(DataValidator.sessionsOverlap(100, 200, 200, null))
    }

    @Test
    fun crossMidnightInterval_usesAbsoluteOrdering() = assertOverlap(1_000, 10_000, 9_000, 11_000)

    @Test
    fun editingSession_excludesItsOwnId() {
        val existing = listOf(session(id = 4, start = 100, end = 200))
        val edited = session(id = 4, start = 110, end = 210)
        assertFalse(DataValidator.overlapsAnySession(existing, edited))
    }

    @Test
    fun editingSession_stillDetectsAnotherSession() {
        val existing = listOf(
            session(id = 4, start = 100, end = 200),
            session(id = 5, start = 300, end = 400),
        )
        val edited = session(id = 4, start = 350, end = 450)
        assertTrue(DataValidator.overlapsAnySession(existing, edited))
    }

    private fun assertOverlap(firstStart: Long, firstEnd: Long?, secondStart: Long, secondEnd: Long?) {
        assertTrue(DataValidator.sessionsOverlap(firstStart, firstEnd, secondStart, secondEnd))
        assertTrue(DataValidator.sessionsOverlap(secondStart, secondEnd, firstStart, firstEnd))
    }

    private fun session(id: Long, start: Long, end: Long?) = Session(
        id = id,
        title = "Session $id",
        startAtMillis = start,
        endAtMillis = end,
        createdAtMillis = 1,
        updatedAtMillis = 1,
    )
}
