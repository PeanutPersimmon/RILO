package io.github.peanutpersimmon.rilo.domain.validation

import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.model.KeywordRule
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.domain.service.SleepCheckInPolicy
import io.github.peanutpersimmon.rilo.domain.time.isMinutePrecision

enum class ValidationError {
    BLANK_TITLE,
    BLANK_NAME,
    INVALID_MINUTE_OF_DAY,
    REMINDER_WITHOUT_ANCHOR,
    INVALID_REMINDER_OFFSET,
    EVENT_END_WITHOUT_START,
    EVENT_END_DATE_WITHOUT_END_TIME,
    EVENT_END_BEFORE_START,
    SESSION_POINT_WITH_END,
    SESSION_SLEEP_POINT,
    SESSION_SLEEP_METADATA,
    SESSION_END_NOT_AFTER_START,
    SESSION_NOT_MINUTE_PRECISION,
    SESSION_SLEEP_TOO_LONG,
    SESSION_OVERLAP,
    BLANK_KEYWORD,
    KEYWORD_RULE_WITHOUT_TARGET,
    BLANK_COMMAND_ID,
}

class DataValidationException(
    val errors: Set<ValidationError>,
) : IllegalArgumentException(errors.joinToString(prefix = "Invalid data: "))

object DataValidator {
    private const val MINUTE_OF_DAY_MIN = 0
    private const val MINUTE_OF_DAY_MAX = 1439

    fun isMinuteOfDayValid(value: Int): Boolean = value in MINUTE_OF_DAY_MIN..MINUTE_OF_DAY_MAX

    fun validateEvent(event: Event): Set<ValidationError> = buildSet {
        if (event.title.trim().isEmpty()) add(ValidationError.BLANK_TITLE)

        listOfNotNull(event.startMinuteOfDay, event.endMinuteOfDay)
            .filterNot(::isMinuteOfDayValid)
            .forEach { add(ValidationError.INVALID_MINUTE_OF_DAY) }

        if (event.startMinuteOfDay == null && event.endMinuteOfDay != null) {
            add(ValidationError.EVENT_END_WITHOUT_START)
        }
        if (event.startReminderOffsetMinutes != null && event.startMinuteOfDay == null)
            add(ValidationError.REMINDER_WITHOUT_ANCHOR)
        if (event.startReminderOffsetMinutes != null && event.startReminderOffsetMinutes < 0)
            add(ValidationError.INVALID_REMINDER_OFFSET)
        if (event.endDateEpochDay != null && event.endMinuteOfDay == null) {
            add(ValidationError.EVENT_END_DATE_WITHOUT_END_TIME)
        }
        if (event.startMinuteOfDay != null && event.endMinuteOfDay != null) {
            val endDate = event.endDateEpochDay ?: event.dateEpochDay
            if (
                endDate < event.dateEpochDay ||
                (endDate == event.dateEpochDay && event.endMinuteOfDay <= event.startMinuteOfDay)
            ) {
                add(ValidationError.EVENT_END_BEFORE_START)
            }
        }

    }

    fun validateSession(session: Session): Set<ValidationError> = buildSet {
        if (session.title.trim().isEmpty()) add(ValidationError.BLANK_TITLE)
        if (!session.startAtMillis.isMinutePrecision() ||
            session.endAtMillis?.isMinutePrecision() == false) {
            add(ValidationError.SESSION_NOT_MINUTE_PRECISION)
        }
        if (session.temporalType == SessionTemporalType.POINT && session.endAtMillis != null) {
            add(ValidationError.SESSION_POINT_WITH_END)
        }
        if (session.kind != SessionKind.ACTIVITY && session.temporalType == SessionTemporalType.POINT) {
            add(ValidationError.SESSION_SLEEP_POINT)
        }
        if (session.kind != SessionKind.ACTIVITY &&
            (session.categoryId != null || session.sourceEventId != null)) {
            add(ValidationError.SESSION_SLEEP_METADATA)
        }
        if (session.temporalType == SessionTemporalType.INTERVAL &&
            session.endAtMillis != null && session.endAtMillis <= session.startAtMillis) {
            add(ValidationError.SESSION_END_NOT_AFTER_START)
        }
        if (session.kind != SessionKind.ACTIVITY && session.endAtMillis?.let { end ->
                SleepCheckInPolicy.isOpenIntervalTooLong(session.startAtMillis, end)
            } == true) {
            add(ValidationError.SESSION_SLEEP_TOO_LONG)
        }
    }

    /** Shared by Room writes and voice preview; actual records may never be in the future. */
    fun validateActualSession(session: Session, nowMillis: Long): io.github.peanutpersimmon.rilo.domain.model.DomainError? {
        val errors = validateSession(session)
        return when {
            session.startAtMillis > nowMillis || session.endAtMillis?.let { it > nowMillis } == true ->
                io.github.peanutpersimmon.rilo.domain.model.DomainError.InvalidTime
            ValidationError.BLANK_TITLE in errors -> io.github.peanutpersimmon.rilo.domain.model.DomainError.EmptyTitle
            ValidationError.SESSION_SLEEP_TOO_LONG in errors ->
                io.github.peanutpersimmon.rilo.domain.model.DomainError.SleepIntervalTooLong
            errors.isNotEmpty() -> io.github.peanutpersimmon.rilo.domain.model.DomainError.InvalidTime
            else -> null
        }
    }

    fun validateKeywordRule(rule: KeywordRule): Set<ValidationError> = buildSet {
        if (rule.keyword.trim().isEmpty()) add(ValidationError.BLANK_KEYWORD)
        if (rule.categoryId == null) {
            add(ValidationError.KEYWORD_RULE_WITHOUT_TARGET)
        }
    }

    fun validateDisplayName(name: String): Set<ValidationError> =
        if (name.trim().isEmpty()) setOf(ValidationError.BLANK_NAME) else emptySet()

    fun validateCommandId(commandId: String): Set<ValidationError> =
        if (commandId.trim().isEmpty()) setOf(ValidationError.BLANK_COMMAND_ID) else emptySet()

    /** Half-open intervals: [start, end). A null end is an open active interval. */
    fun sessionsOverlap(
        firstStart: Long,
        firstEnd: Long?,
        secondStart: Long,
        secondEnd: Long?,
    ): Boolean {
        val firstEndExclusive = firstEnd ?: Long.MAX_VALUE
        val secondEndExclusive = secondEnd ?: Long.MAX_VALUE
        return firstStart < secondEndExclusive && secondStart < firstEndExclusive
    }

    fun overlapsAnySession(
        existingSessions: Iterable<Session>,
        candidate: Session,
    ): Boolean = existingSessions.any { existing ->
        val isSamePersistedSession = candidate.id != 0L && existing.id == candidate.id
        !isSamePersistedSession && existing.temporalType == SessionTemporalType.INTERVAL &&
            candidate.temporalType == SessionTemporalType.INTERVAL && sessionsOverlap(
            firstStart = existing.startAtMillis,
            firstEnd = existing.endAtMillis,
            secondStart = candidate.startAtMillis,
            secondEnd = candidate.endAtMillis,
        )
    }

    fun requireValid(errors: Set<ValidationError>) {
        if (errors.isNotEmpty()) throw DataValidationException(errors)
    }
}
