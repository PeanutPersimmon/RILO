package io.github.peanutpersimmon.rilo.ui.components

import io.github.peanutpersimmon.rilo.domain.time.timelineClockLabel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class DayTimelineTick(val instantMillis: Long, val fraction: Float, val label: String)

/** Tick positions and cards use the same elapsed-time fraction of this local day. */
fun dayTimelineTicks(date: LocalDate, zone: ZoneId): List<DayTimelineTick> {
    val start = date.atStartOfDay(zone)
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val first = start.toInstant().toEpochMilli()
    val instants = (0..23).flatMap { hour ->
        val local = date.atTime(hour, 0)
        zone.rules.getValidOffsets(local).map { local.toInstant(it).toEpochMilli() }
    }.plus(first).distinct().filter { it >= first && it < end }.sorted()
    return instants.map { millis ->
        val minute = java.time.Instant.ofEpochMilli(millis).atZone(zone).minute
        DayTimelineTick(millis, ((millis - first).toDouble() / (end - first)).toFloat(),
            timelineClockLabel(millis, zone, DateTimeFormatter.ofPattern(if (minute == 0) "HH" else "HH:mm")))
    }
}
