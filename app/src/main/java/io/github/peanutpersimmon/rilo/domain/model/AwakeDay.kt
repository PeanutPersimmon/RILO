package io.github.peanutpersimmon.rilo.domain.model

import io.github.peanutpersimmon.rilo.domain.time.TimeInterval
import java.time.LocalDate

@JvmInline
value class CalendarDate(val value: LocalDate)

data class AwakeDay(
    val calendarDate: CalendarDate,
    val interval: TimeInterval,
    val wakeAtMillis: Long?,
    val sleepAtMillis: Long?,
    val wakeSessionId: Long? = null,
    val sleepSessionId: Long? = null,
)

