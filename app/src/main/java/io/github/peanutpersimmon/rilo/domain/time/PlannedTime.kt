package io.github.peanutpersimmon.rilo.domain.time

import io.github.peanutpersimmon.rilo.domain.model.Event
import java.time.LocalDate
import java.time.ZoneId

fun epochDayMinuteToMillis(epochDay: Long, minuteOfDay: Int, zoneId: ZoneId): Long =
    LocalDate.ofEpochDay(epochDay)
        .atTime(java.time.LocalTime.MIN.plusMinutes(minuteOfDay.toLong()))
        .atZone(zoneId)
        .toInstant()
        .toEpochMilli()

fun Event.plannedInterval(zoneId: ZoneId): TimeInterval? {
    val startMinute = startMinuteOfDay ?: return null
    val endMinute = endMinuteOfDay ?: return null
    val start = epochDayMinuteToMillis(dateEpochDay, startMinute, zoneId)
    val end = epochDayMinuteToMillis(endDateEpochDay ?: dateEpochDay, endMinute, zoneId)
    return if (start < end) TimeInterval(start, end) else null
}


