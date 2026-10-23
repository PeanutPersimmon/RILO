package io.github.peanutpersimmon.rilo.domain.time

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** User-entered minutes must exist and must identify one instant. */
fun resolveLocalMinute(date: LocalDate, time: LocalTime, zone: ZoneId, referenceMillis: Long? = null): Long {
    val local = date.atTime(time.withSecond(0).withNano(0))
    val offsets = zone.rules.getValidOffsets(local)
    if (offsets.isEmpty()) throw DateTimeException("该当地时间因夏令时切换不存在，请重新选择时间")
    val reference = referenceMillis?.let { Instant.ofEpochMilli(it).atZone(zone) }
    val offset = if (offsets.size == 1) offsets.single() else {
        reference?.takeIf {
            it.toLocalDateTime().withSecond(0).withNano(0) == local && it.offset in offsets
        }?.offset ?: throw DateTimeException("该当地时间因夏令时回拨出现两次，请选择无歧义的时间")
    }
    return local.toInstant(offset).toEpochMilli()
}

/** Keep ordinary labels unchanged; distinguish the two occurrences of an overlap minute. */
fun timelineClockLabel(millis: Long, zone: ZoneId, formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")): String {
    val local = Instant.ofEpochMilli(millis).atZone(zone)
    val text = local.format(formatter)
    return if (zone.rules.getValidOffsets(local.toLocalDateTime()).size > 1) "$text ${local.offset}" else text
}
