package io.github.peanutpersimmon.rilo.domain.service

import io.github.peanutpersimmon.rilo.domain.model.AwakeDisplayContent
import io.github.peanutpersimmon.rilo.domain.model.AwakeDisplayWindow
import io.github.peanutpersimmon.rilo.domain.model.BoundarySource
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.domain.time.TimeInterval
import io.github.peanutpersimmon.rilo.domain.time.epochDayMinuteToMillis
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object AwakeDisplayWindowResolver {
    fun resolveForDate(
        displayDate: LocalDate,
        nightSleeps: List<Session>,
        relevantContent: List<AwakeDisplayContent>,
        defaultWakeMinute: Int,
        defaultSleepMinute: Int,
        zoneId: ZoneId,
        nowMillis: Long? = null,
    ): AwakeDisplayWindow {
        validateDefaults(defaultWakeMinute, defaultSleepMinute)
        val validSleeps = validNightSleeps(nightSleeps)
        val ownedContent = relevantContent.filter { it.ownerDate == displayDate }
        val earliestContent = ownedContent.minOfOrNull { it.startAtMillis }
        // A zero-span content anchor still needs to fit inside the half-open display interval.
        val latestContent = ownedContent.maxOfOrNull {
            if (it.startAtMillis == it.endAtMillis) it.endAtMillis + 1 else it.endAtMillis
        }
        val defaultStart = epochDayMinuteToMillis(displayDate.toEpochDay(), defaultWakeMinute, zoneId)
        val defaultEnd = defaultEnd(displayDate, defaultWakeMinute, defaultSleepMinute, zoneId)
        val wakingSleep = validSleeps.asSequence()
            .filter { session ->
                session.endAtMillis?.let {
                    Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() == displayDate
                } == true
            }
            .maxWithOrNull(compareBy<Session> { requireNotNull(it.endAtMillis) }.thenBy { it.id })
        val actualWake = wakingSleep?.endAtMillis
        val start = actualWake ?: minOf(defaultStart, earliestContent ?: defaultStart)
        val sleepingSession = validSleeps.asSequence()
            .filter { it.startAtMillis > start }
            .minWithOrNull(compareBy<Session> { it.startAtMillis }.thenBy { it.id })
        val actualSleep = sleepingSession?.startAtMillis
        val fallbackEnd = maxOf(defaultEnd, latestContent ?: defaultEnd)
        val end = actualSleep ?: maxOf(fallbackEnd, nowMillis ?: Long.MIN_VALUE).let { candidate ->
            if (candidate > start) candidate else nextDefaultEndAfter(
                startMillis = start,
                displayDate = displayDate,
                defaultSleepMinute = defaultSleepMinute,
                zoneId = zoneId,
            )
        }
        return AwakeDisplayWindow(
            anchorDate = displayDate,
            displayInterval = TimeInterval(start, end),
            actualWakeAtMillis = actualWake,
            actualSleepAtMillis = actualSleep,
            startSource = if (actualWake == null) BoundarySource.DEFAULT else BoundarySource.REAL,
            endSource = when {
                actualSleep != null -> BoundarySource.REAL
                nowMillis != null && end == nowMillis && nowMillis > fallbackEnd -> BoundarySource.NOW
                else -> BoundarySource.DEFAULT
            },
            actualWakeSessionId = wakingSleep?.id,
            actualSleepSessionId = sleepingSession?.id,
        )
    }

    fun resolveToday(
        displayDate: LocalDate,
        nightSleeps: List<Session>,
        relevantContent: List<AwakeDisplayContent>,
        defaultWakeMinute: Int,
        defaultSleepMinute: Int,
        zoneId: ZoneId,
        nowMillis: Long,
    ): AwakeDisplayWindow = resolveForDate(
        displayDate = displayDate,
        nightSleeps = nightSleeps,
        relevantContent = relevantContent,
        defaultWakeMinute = defaultWakeMinute,
        defaultSleepMinute = defaultSleepMinute,
        zoneId = zoneId,
        nowMillis = nowMillis,
    )

    fun resolveHistorical(
        anchorDate: LocalDate,
        nightSleeps: List<Session>,
        relevantContent: List<AwakeDisplayContent>,
        defaultWakeMinute: Int,
        defaultSleepMinute: Int,
        zoneId: ZoneId,
    ): AwakeDisplayWindow = resolveForDate(
        displayDate = anchorDate,
        nightSleeps = nightSleeps,
        relevantContent = relevantContent,
        defaultWakeMinute = defaultWakeMinute,
        defaultSleepMinute = defaultSleepMinute,
        zoneId = zoneId,
        nowMillis = null,
    )

    private fun validNightSleeps(sleeps: List<Session>): List<Session> = sleeps.filter {
        it.kind == SessionKind.NIGHT_SLEEP && it.temporalType == SessionTemporalType.INTERVAL &&
            (it.endAtMillis == null || it.endAtMillis > it.startAtMillis)
    }

    private fun defaultEnd(
        displayDate: LocalDate,
        defaultWakeMinute: Int,
        defaultSleepMinute: Int,
        zoneId: ZoneId,
    ): Long {
        val endDate = if (defaultSleepMinute <= defaultWakeMinute) displayDate.plusDays(1) else displayDate
        return epochDayMinuteToMillis(endDate.toEpochDay(), defaultSleepMinute, zoneId)
    }

    private fun nextDefaultEndAfter(
        startMillis: Long,
        displayDate: LocalDate,
        defaultSleepMinute: Int,
        zoneId: ZoneId,
    ): Long {
        var date = displayDate.plusDays(1)
        var candidate = epochDayMinuteToMillis(date.toEpochDay(), defaultSleepMinute, zoneId)
        while (candidate <= startMillis) {
            date = date.plusDays(1)
            candidate = epochDayMinuteToMillis(date.toEpochDay(), defaultSleepMinute, zoneId)
        }
        return candidate
    }

    private fun validateDefaults(wake: Int, sleep: Int) {
        require(wake in 0..1439)
        require(sleep in 0..1439)
    }
}
