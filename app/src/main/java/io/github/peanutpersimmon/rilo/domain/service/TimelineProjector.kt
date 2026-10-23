package io.github.peanutpersimmon.rilo.domain.service

import io.github.peanutpersimmon.rilo.domain.model.*
import io.github.peanutpersimmon.rilo.domain.time.epochDayMinuteToMillis
import io.github.peanutpersimmon.rilo.domain.time.TimeInterval
import java.time.ZoneId

/** Today projects record facts and planned entries. */
object TimelineProjector {
    fun projectionRange(awakeDay: AwakeDay, zoneId: ZoneId): TimeInterval {
        val date = awakeDay.calendarDate.value
        val calendarStart = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val calendarEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val start = awakeDay.wakeAtMillis ?: calendarStart
        val end = awakeDay.sleepAtMillis ?: maxOf(awakeDay.interval.endMillis, calendarEnd)
        return if (end > start) TimeInterval(start, end) else awakeDay.interval
    }

    fun projectionRange(window: AwakeDisplayWindow, zoneId: ZoneId): TimeInterval {
        val calendarStart = window.anchorDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val calendarEnd = window.anchorDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val start = if (window.actualWakeAtMillis == null) {
            minOf(window.displayInterval.startMillis, calendarStart)
        } else {
            window.displayInterval.startMillis
        }
        val end = if (window.actualSleepAtMillis == null) {
            maxOf(window.displayInterval.endMillis, calendarEnd)
        } else {
            window.displayInterval.endMillis
        }
        return TimeInterval(start, end)
    }

    fun project(awakeDay: AwakeDay, sessions: List<Session>, events: List<Event>,
        asOfMillis: Long, zoneId: ZoneId): DomainResult<List<TimelineEntry>> =
        projectInternal(
            date = awakeDay.calendarDate.value,
            displayRange = projectionRange(awakeDay, zoneId),
            wakeAtMillis = awakeDay.wakeAtMillis,
            sleepAtMillis = awakeDay.sleepAtMillis,
            wakeSessionId = awakeDay.wakeSessionId,
            sleepSessionId = awakeDay.sleepSessionId,
            sessions = sessions,
            events = events,
            asOfMillis = asOfMillis,
            zoneId = zoneId,
        )

    fun project(window: AwakeDisplayWindow, sessions: List<Session>, events: List<Event>,
        asOfMillis: Long, zoneId: ZoneId): DomainResult<List<TimelineEntry>> =
        when (val result = projectInternal(
            date = window.anchorDate,
            displayRange = window.displayInterval,
            wakeAtMillis = window.actualWakeAtMillis,
            sleepAtMillis = window.actualSleepAtMillis,
            wakeSessionId = window.actualWakeSessionId,
            sleepSessionId = window.actualSleepSessionId,
            sessions = sessions,
            events = events,
            asOfMillis = asOfMillis,
            zoneId = zoneId,
        )) {
            is DomainResult.Failure -> result
            is DomainResult.Success -> DomainResult.Success(
                result.value.mapNotNull { it.clippedTo(window.displayInterval) },
            )
        }

    private fun projectInternal(
        date: java.time.LocalDate,
        displayRange: TimeInterval,
        wakeAtMillis: Long?,
        sleepAtMillis: Long?,
        wakeSessionId: Long?,
        sleepSessionId: Long?,
        sessions: List<Session>,
        events: List<Event>,
        asOfMillis: Long,
        zoneId: ZoneId,
    ): DomainResult<List<TimelineEntry>> {
        val ownedSessions = sessions.filter {
            AwakeDisplayContentProjector.sessionOwnerDate(it, zoneId) == date
        }
        val resolution = when (val result = ActualTimeResolver.resolve(
            ownedSessions,
            displayRange,
            asOfMillis,
        )) {
            is DomainResult.Failure -> return result
            is DomainResult.Success -> result.value
        }
        val entries = mutableListOf<TimelineEntry>()
        wakeAtMillis?.let { entries += WakePoint(it, wakeSessionId) }
        sleepAtMillis?.let { entries += SleepPoint(it, sleepSessionId) }
        resolution.segments.filter { it.sourceType == ActualSourceType.SESSION && it.sessionKind != SessionKind.NIGHT_SLEEP }
            .forEach { segment ->
                entries += SessionBlock(segment.sourceId, segment.interval.startMillis, segment.interval.endMillis,
                    segment.title, segment.categoryId, segment.note, segment.location,
                    requireNotNull(segment.sessionKind))
            }
        ownedSessions.asSequence()
            .filter { it.kind == SessionKind.ACTIVITY && it.temporalType == SessionTemporalType.POINT }
            .filter { it.startAtMillis >= displayRange.startMillis && it.startAtMillis < displayRange.endMillis }
            .forEach { session ->
                entries += SessionPoint(session.id, session.startAtMillis, session.title,
                    session.categoryId, session.note, session.location)
            }
        events.filter { AwakeDisplayContentProjector.eventOwnerDate(it, zoneId) == date }.forEach { e ->
            val minute = e.startMinuteOfDay ?: return@forEach
            val start = epochDayMinuteToMillis(e.dateEpochDay, minute, zoneId)
            val end = e.endMinuteOfDay?.let {
                epochDayMinuteToMillis(e.endDateEpochDay ?: e.dateEpochDay, it, zoneId)
            }
            entries += EventBlock(e.id, start, end, e.title, TimelineState.PLANNED,
                e.categoryId, e.note, e.location)
        }
        return DomainResult.Success(entries.sortedWith(compareBy<TimelineEntry> { it.startAtMillis }
            .thenBy { when (it.entryType) {
                TimelineEntryType.WAKE_POINT -> 0
                TimelineEntryType.SESSION_BLOCK -> 1
                TimelineEntryType.SESSION_POINT -> 2
                TimelineEntryType.EVENT_BLOCK -> 3
                TimelineEntryType.SLEEP_POINT -> 4
            } }.thenBy { it.sourceId ?: Long.MIN_VALUE }))
    }

    private fun TimelineEntry.clippedTo(range: TimeInterval): TimelineEntry? {
        val originalEnd = endAtMillis
        if (originalEnd == null) {
            return takeIf { startAtMillis >= range.startMillis && startAtMillis < range.endMillis }
        }
        val clippedStart = maxOf(startAtMillis, range.startMillis)
        val clippedEnd = minOf(originalEnd, range.endMillis)
        if (clippedEnd <= clippedStart) return null
        return when (this) {
            is SessionBlock -> copy(startAtMillis = clippedStart, endAtMillis = clippedEnd)
            is EventBlock -> copy(startAtMillis = clippedStart, endAtMillis = clippedEnd)
            else -> null
        }
    }
}
