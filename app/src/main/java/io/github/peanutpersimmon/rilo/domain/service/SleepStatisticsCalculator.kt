package io.github.peanutpersimmon.rilo.domain.service

import io.github.peanutpersimmon.rilo.domain.model.Session
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt

enum class SleepCalendarMode(val label: String) { BED("入睡时间"), WAKE("起床时间"), DURATION("睡眠时长") }
enum class SleepChartMode(val label: String) { BED("入睡时间"), WAKE("起床时间"), NIGHT("夜间时长"), NAP("午睡时长") }

data class SleepDay(
    val date: LocalDate,
    val bedMillis: Long?,
    val wakeMillis: Long?,
    val durationMinutes: Int?,
    val hasNightRecord: Boolean,
)
data class SleepPoint(val date: LocalDate, val valueMinutes: Int?)

object SleepStatisticsCalculator {
    /** The same representative night is used by Statistics and both reports. */
    fun representativeNights(sessions: List<Session>, zone: ZoneId, asOfMillis: Long = Long.MAX_VALUE): Map<LocalDate, Session> = sessions
        .filter { it.kind == SessionKind.NIGHT_SLEEP && it.temporalType == SessionTemporalType.INTERVAL &&
            it.startAtMillis <= asOfMillis && (it.endAtMillis == null || it.endAtMillis <= asOfMillis) }
        .groupBy { SleepDayResolver.resolve(it, zone) }
        .mapValues { (_, records) ->
            records.maxWith(compareBy<Session> { it.updatedAtMillis }.thenBy { it.id })
        }

    fun nightDays(sessions: List<Session>, zone: ZoneId, asOfMillis: Long = Long.MAX_VALUE): Map<LocalDate, SleepDay> =
        representativeNights(sessions, zone, asOfMillis).mapValues { (date, latest) ->
            SleepDay(date, latest.startAtMillis, latest.endAtMillis,
                latest.endAtMillis?.takeIf { it > latest.startAtMillis }?.let { ((it - latest.startAtMillis) / 60_000L).toInt() }, true)
        }

    fun calendarDays(month: YearMonth, nights: Map<LocalDate, SleepDay>): List<SleepDay?> {
        val offset = month.atDay(1).dayOfWeek.value - 1
        val count = ((offset + month.lengthOfMonth() + 6) / 7) * 7
        return List(count) { index ->
            val day = index - offset + 1
            if (day in 1..month.lengthOfMonth()) nights[month.atDay(day)] ?: SleepDay(month.atDay(day), null, null, null, false)
            else null
        }
    }

    fun averageMinutes(days: Collection<SleepDay>, mode: SleepCalendarMode, zone: ZoneId): Int? {
        val values = days.mapNotNull { day -> when (mode) {
            SleepCalendarMode.BED -> day.bedMillis?.let { bedtimeMinute(it, zone) }
            SleepCalendarMode.WAKE -> day.wakeMillis?.let { minuteOfDay(it, zone) }
            SleepCalendarMode.DURATION -> day.durationMinutes
        } }
        return values.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    }

    fun linePoints(nights: Map<LocalDate, SleepDay>, start: LocalDate, end: LocalDate, zone: ZoneId, bed: Boolean): List<SleepPoint> =
        generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.map { date ->
            val millis = if (bed) nights[date]?.bedMillis else nights[date]?.wakeMillis
            SleepPoint(date, millis?.let { if (bed) bedtimeMinute(it, zone) else minuteOfDay(it, zone) })
        }.toList()

    fun linePoints(nights: Map<LocalDate, SleepDay>, start: LocalDate, zone: ZoneId, bed: Boolean): List<SleepPoint> =
        linePoints(nights, start, start.plusDays(6), zone, bed)

    fun durationPoints(month: YearMonth, nights: Map<LocalDate, SleepDay>, naps: List<Session>, zone: ZoneId, nap: Boolean, asOfMillis: Long = Long.MAX_VALUE): List<SleepPoint> {
        val napTotals = if (nap) naps.filter { it.kind == SessionKind.NAP && it.temporalType == SessionTemporalType.INTERVAL &&
            it.endAtMillis != null && it.endAtMillis > it.startAtMillis && it.endAtMillis <= asOfMillis }
            .groupBy { SleepDayResolver.resolve(it, zone) }
            .mapValues { (_, records) -> records.sumOf { ((it.endAtMillis!! - it.startAtMillis) / 60_000L).toInt() } }
        else emptyMap()
        return (1..month.lengthOfMonth()).map { number ->
            val date = month.atDay(number)
            SleepPoint(date, if (nap) napTotals[date] else nights[date]?.durationMinutes)
        }
    }

    fun averageNapMinutes(naps: List<Session>, month: YearMonth, zone: ZoneId, asOfMillis: Long): Int? {
        val minutes = naps.filter {
            it.kind == SessionKind.NAP && it.temporalType == SessionTemporalType.INTERVAL && it.endAtMillis != null &&
                it.endAtMillis > it.startAtMillis && it.endAtMillis <= asOfMillis &&
                YearMonth.from(SleepDayResolver.resolve(it, zone)) == month
        }.map { ((it.endAtMillis!! - it.startAtMillis) / 60_000L).toInt() }
        return minutes.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    }
    fun minuteOfDay(millis: Long, zone: ZoneId): Int = Instant.ofEpochMilli(millis).atZone(zone).let { it.hour * 60 + it.minute }
    fun bedtimeMinute(millis: Long, zone: ZoneId): Int = minuteOfDay(millis, zone).let { if (it < 12 * 60) it + 24 * 60 else it }
    fun clock(minutes: Int): String {
        val normalized = Math.floorMod(minutes, 1440)
        return "%02d:%02d".format(normalized / 60, normalized % 60)
    }
    fun duration(minutes: Int): String = if (minutes >= 60) "${minutes / 60}h${minutes % 60}m" else "${minutes}m"
}
