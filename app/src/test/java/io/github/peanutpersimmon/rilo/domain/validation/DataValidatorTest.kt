package io.github.peanutpersimmon.rilo.domain.validation

import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.model.KeywordRule
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataValidatorTest {
    @Test
    fun minuteOfDay_boundaries_areValidated() {
        assertFalse(DataValidator.isMinuteOfDayValid(-1))
        assertTrue(DataValidator.isMinuteOfDayValid(0))
        assertTrue(DataValidator.isMinuteOfDayValid(1439))
        assertFalse(DataValidator.isMinuteOfDayValid(1440))
    }

    @Test
    fun event_dateOnly_isValid() {
        assertTrue(DataValidator.validateEvent(event()).isEmpty())
    }

    @Test
    fun event_blankTitle_isInvalid() {
        val invalid = event().copy(title = "\t")
        assertEventError(invalid, ValidationError.BLANK_TITLE)
    }

    @Test
    fun event_startOnly_isValid() {
        assertTrue(DataValidator.validateEvent(event(startMinuteOfDay = 840)).isEmpty())
    }

    @Test
    fun event_reminderAcceptsAnyNonnegativeMinutesButRequiresStart() {
        val timed = event(startMinuteOfDay = 840)
        for (offset in listOf(0, 5, 17, 45, 120, 150, 1440, 4320)) {
            assertTrue(DataValidator.validateEvent(timed.copy(startReminderOffsetMinutes = offset)).isEmpty())
        }
        assertEventError(timed.copy(startReminderOffsetMinutes = -1), ValidationError.INVALID_REMINDER_OFFSET)
        assertEventError(event().copy(startReminderOffsetMinutes = 17), ValidationError.REMINDER_WITHOUT_ANCHOR)
    }

    @Test
    fun event_sameDayIntervalMustHavePositiveDuration() {
        assertTrue(DataValidator.validateEvent(event(startMinuteOfDay = 840, endMinuteOfDay = 900)).isEmpty())
        assertEventError(event(startMinuteOfDay = 840, endMinuteOfDay = 840), ValidationError.EVENT_END_BEFORE_START)
    }

    @Test
    fun event_sameDayEndBeforeStart_isInvalid() {
        assertEventError(
            event(startMinuteOfDay = 900, endMinuteOfDay = 899),
            ValidationError.EVENT_END_BEFORE_START,
        )
    }

    @Test
    fun event_endWithoutStart_isInvalid() {
        assertEventError(event(endMinuteOfDay = 900), ValidationError.EVENT_END_WITHOUT_START)
    }

    @Test
    fun event_crossMidnightPlan_isValid() {
        assertTrue(
            DataValidator.validateEvent(
                event(
                    startMinuteOfDay = 1380,
                    endDateEpochDay = 2,
                    endMinuteOfDay = 90,
                ),
            ).isEmpty(),
        )
    }

    @Test
    fun session_endBeforeStart_isInvalid() {
        assertSessionError(session(startAtMillis = 200, endAtMillis = 100))
    }

    @Test
    fun session_blankTitle_isInvalid() {
        val invalid = session(startAtMillis = 100, endAtMillis = 200).copy(title = " ")
        assertTrue(ValidationError.BLANK_TITLE in DataValidator.validateSession(invalid))
    }

    @Test
    fun session_zeroDuration_isInvalid() {
        assertSessionError(session(startAtMillis = 100, endAtMillis = 100))
    }

    @Test
    fun session_positiveDuration_isValid() {
        assertTrue(DataValidator.validateSession(session(startAtMillis = 100, endAtMillis = 101)).isEmpty())
    }

    @Test
    fun session_requiresMinutePrecision() {
        val session = session(startAtMillis = 100, endAtMillis = 101)
            .copy(startAtMillis = 100 * 60_000L + 1L)
        assertTrue(ValidationError.SESSION_NOT_MINUTE_PRECISION in
            DataValidator.validateSession(session))
    }

    @Test
    fun sleepDurationAcceptsUpToTwentyFourHours() {
        val start = 100 * 60_000L
        fun sleep(durationMinutes: Long) = Session(
            title = "夜间睡眠",
            kind = SessionKind.NIGHT_SLEEP,
            temporalType = SessionTemporalType.INTERVAL,
            startAtMillis = start,
            endAtMillis = start + durationMinutes * 60_000L,
            createdAtMillis = start,
            updatedAtMillis = start,
        )

        assertTrue(DataValidator.validateSession(sleep(23 * 60L + 59L)).isEmpty())
        assertTrue(DataValidator.validateSession(sleep(24 * 60L)).isEmpty())
        assertTrue(ValidationError.SESSION_SLEEP_TOO_LONG in
            DataValidator.validateSession(sleep(24 * 60L + 1L)))
    }

    @Test
    fun pointHasNoEndAndDoesNotOverlapAnInterval() {
        val interval = session(startAtMillis = 100, endAtMillis = 300)
        val point = session(startAtMillis = 200, endAtMillis = null)
            .copy(temporalType = SessionTemporalType.POINT)
        assertTrue(DataValidator.validateSession(point).isEmpty())
        assertFalse(DataValidator.overlapsAnySession(listOf(interval), point))
        assertFalse(DataValidator.overlapsAnySession(listOf(point), interval))
        assertTrue(ValidationError.SESSION_POINT_WITH_END in
            DataValidator.validateSession(point.copy(endAtMillis = 201)))
    }

    @Test
    fun overlappingIntervalsStillConflictAndSleepPointIsInvalid() {
        val first = session(startAtMillis = 100, endAtMillis = 300)
        val second = session(startAtMillis = 200, endAtMillis = 400)
        assertTrue(DataValidator.overlapsAnySession(listOf(first), second))
        for (kind in listOf(SessionKind.NAP, SessionKind.NIGHT_SLEEP)) {
            assertTrue(ValidationError.SESSION_SLEEP_POINT in
                DataValidator.validateSession(first.copy(kind = kind, temporalType = SessionTemporalType.POINT,
                    endAtMillis = null)))
            assertTrue(DataValidator.validateSession(first.copy(kind = kind)).isEmpty())
        }
    }

    @Test
    fun adjacentActualIntervalsOfEveryKindRemainLegalOnCreateAndEdit() {
        val first = session(9 * 60, 9 * 60 + 34).copy(id = 1)
        val next = session(9 * 60 + 34, 10 * 60).copy(id = 2)
        assertFalse(DataValidator.overlapsAnySession(listOf(first), next))
        assertTrue(DataValidator.overlapsAnySession(listOf(
            first.copy(endAtMillis = (9 * 60 + 35) * 60_000L),
        ), next))
        val sleep = first.copy(kind = SessionKind.NIGHT_SLEEP)
        assertFalse(DataValidator.overlapsAnySession(listOf(sleep), next))
        val nap = session(13 * 60 + 20, 14 * 60 + 10).copy(id = 3, kind = SessionKind.NAP)
        assertFalse(DataValidator.overlapsAnySession(listOf(nap),
            session(14 * 60 + 10, 15 * 60)))
        assertFalse(DataValidator.overlapsAnySession(listOf(session(12 * 60, 13 * 60 + 20)), nap))
        assertFalse(DataValidator.overlapsAnySession(listOf(first, next),
            next.copy(startAtMillis = first.endAtMillis!!)))
        assertTrue(DataValidator.overlapsAnySession(listOf(first),
            next.copy(startAtMillis = first.endAtMillis - 1)))
    }

    @Test
    fun session_crossMidnight_isValidBecauseInstantsAreOrdered() {
        assertTrue(DataValidator.validateSession(session(startAtMillis = 1_000, endAtMillis = 10_000)).isEmpty())
    }

    @Test
    fun keywordRule_blankKeyword_isInvalid() {
        val errors = DataValidator.validateKeywordRule(keywordRule(keyword = " ", categoryId = 1))
        assertTrue(ValidationError.BLANK_KEYWORD in errors)
    }

    @Test
    fun keywordRule_withoutCategory_isInvalid() {
        val errors = DataValidator.validateKeywordRule(keywordRule(keyword = "meeting"))
        assertTrue(ValidationError.KEYWORD_RULE_WITHOUT_TARGET in errors)
    }

    @Test
    fun displayName_isTrimmedAndMustNotBeBlank() {
        assertTrue(ValidationError.BLANK_NAME in DataValidator.validateDisplayName("  "))
        assertTrue(DataValidator.validateDisplayName("Custom").isEmpty())
    }

    private fun assertEventError(event: Event, error: ValidationError) {
        assertTrue(error in DataValidator.validateEvent(event))
    }

    private fun assertSessionError(session: Session) {
        assertTrue(ValidationError.SESSION_END_NOT_AFTER_START in DataValidator.validateSession(session))
    }

    private fun event(
        startMinuteOfDay: Int? = null,
        endDateEpochDay: Long? = null,
        endMinuteOfDay: Int? = null,
    ) = Event(
        title = "Event",
        dateEpochDay = 1,
        startMinuteOfDay = startMinuteOfDay,
        endDateEpochDay = endDateEpochDay,
        endMinuteOfDay = endMinuteOfDay,
        createdAtMillis = 1,
        updatedAtMillis = 1,
    )

    private fun session(startAtMillis: Long, endAtMillis: Long?) = Session(
        title = "Session",
        startAtMillis = startAtMillis * 60_000L,
        endAtMillis = endAtMillis?.times(60_000L),
        createdAtMillis = 1,
        updatedAtMillis = 1,
    )

    private fun keywordRule(
        keyword: String,
        categoryId: Long? = null,
    ) = KeywordRule(
        keyword = keyword,
        categoryId = categoryId,
        createdAtMillis = 1,
        updatedAtMillis = 1,
    )
}
