package io.github.peanutpersimmon.rilo.domain.service

import io.github.peanutpersimmon.rilo.domain.model.AwakeDay
import io.github.peanutpersimmon.rilo.domain.model.CalendarDate
import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rilo.domain.time.TimeInterval
import io.github.peanutpersimmon.rilo.domain.time.epochDayMinuteToMillis
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object AwakeDayCalculator {
    fun calculate(
        calendarDate: LocalDate,
        nightSleeps: List<Session>,
        defaultWakeMinute: Int,
        defaultSleepMinute: Int,
        zoneId: ZoneId,
        asOfMillis: Long,
    ): AwakeDay {
        require(defaultWakeMinute in 0..1439)
        require(defaultSleepMinute in 0..1439)

        val completedNightSleeps = nightSleeps
            .filter { it.kind == SessionKind.NIGHT_SLEEP && it.temporalType == SessionTemporalType.INTERVAL && it.endAtMillis != null }

        val wakingSleep = completedNightSleeps
            .filter { sleep ->
                Instant.ofEpochMilli(requireNotNull(sleep.endAtMillis)).atZone(zoneId).toLocalDate() == calendarDate
            }
            .maxWithOrNull(compareBy<Session> { requireNotNull(it.endAtMillis) }.thenBy { it.id })

        val start = wakingSleep?.endAtMillis
            ?: epochDayMinuteToMillis(calendarDate.toEpochDay(), defaultWakeMinute, zoneId)

        val nextNightSleep = nightSleeps
            .asSequence()
            .filter { it.kind == SessionKind.NIGHT_SLEEP && it.startAtMillis >= start }
            .minWithOrNull(compareBy<Session> { it.startAtMillis }.thenBy { it.id })

        val fallbackSleepDate = if (defaultSleepMinute <= defaultWakeMinute) {
            calendarDate.plusDays(1)
        } else {
            calendarDate
        }
        val fallbackEnd = epochDayMinuteToMillis(
            fallbackSleepDate.toEpochDay(),
            defaultSleepMinute,
            zoneId,
        )

        val currentLocalDateTime = Instant.ofEpochMilli(asOfMillis).atZone(zoneId)
        val currentAwakeDate = if (
            currentLocalDateTime.hour * 60 + currentLocalDateTime.minute < defaultWakeMinute
        ) {
            currentLocalDateTime.toLocalDate().minusDays(1)
        } else {
            currentLocalDateTime.toLocalDate()
        }

        val end = nextNightSleep?.startAtMillis ?: if (
            calendarDate == currentAwakeDate && asOfMillis > fallbackEnd
        ) {
            asOfMillis
        } else {
            fallbackEnd
        }

        require(start < end) { "Awake-day boundaries must have positive duration." }
        return AwakeDay(
            calendarDate = CalendarDate(calendarDate),
            interval = TimeInterval(start, end),
            wakeAtMillis = wakingSleep?.endAtMillis,
            sleepAtMillis = nextNightSleep?.startAtMillis,
            wakeSessionId = wakingSleep?.id,
            sleepSessionId = nextNightSleep?.id,
        )
    }
}
