package io.github.peanutpersimmon.rilo.domain.service

import io.github.peanutpersimmon.rilo.domain.time.TimeInterval
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class StatisticsGranularity(val label: String) { DAY("日"), WEEK("周"), MONTH("月"), YEAR("年") }

data class StatisticsPeriod(val granularity: StatisticsGranularity, val anchor: LocalDate) {
    val start: LocalDate get() = when (granularity) {
        StatisticsGranularity.DAY -> anchor
        StatisticsGranularity.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        StatisticsGranularity.MONTH -> anchor.withDayOfMonth(1)
        StatisticsGranularity.YEAR -> anchor.withDayOfYear(1)
    }
    val endExclusive: LocalDate get() = when (granularity) {
        StatisticsGranularity.DAY -> start.plusDays(1)
        StatisticsGranularity.WEEK -> start.plusWeeks(1)
        StatisticsGranularity.MONTH -> start.plusMonths(1)
        StatisticsGranularity.YEAR -> start.plusYears(1)
    }
    fun shifted(direction: Long): StatisticsPeriod = copy(anchor = when (granularity) {
        StatisticsGranularity.DAY -> anchor.plusDays(direction)
        StatisticsGranularity.WEEK -> anchor.plusWeeks(direction)
        StatisticsGranularity.MONTH -> anchor.plusMonths(direction)
        StatisticsGranularity.YEAR -> anchor.plusYears(direction)
    })
    fun interval(zone: ZoneId): TimeInterval = TimeInterval(
        start.atStartOfDay(zone).toInstant().toEpochMilli(),
        endExclusive.atStartOfDay(zone).toInstant().toEpochMilli(),
    )
    fun label(): String = when (granularity) {
        StatisticsGranularity.DAY -> "${start.year}年${start.monthValue}月${start.dayOfMonth}日"
        StatisticsGranularity.WEEK -> {
            val end = endExclusive.minusDays(1)
            if (start.year == end.year) "${start.year}年${start.monthValue}月${start.dayOfMonth}日 - ${end.monthValue}月${end.dayOfMonth}日"
            else "${start.year}年${start.monthValue}月${start.dayOfMonth}日 - ${end.year}年${end.monthValue}月${end.dayOfMonth}日"
        }
        StatisticsGranularity.MONTH -> "${start.year}年${start.monthValue}月"
        StatisticsGranularity.YEAR -> "${start.year}年"
    }
    fun buckets(): List<StatisticsBucket> {
        if (granularity == StatisticsGranularity.DAY) return emptyList()
        return if (granularity == StatisticsGranularity.YEAR) (1..12).map { month ->
            val date = YearMonth.of(start.year, month).atDay(1)
            StatisticsBucket(date, date.plusMonths(1), "${month}月")
        } else generateSequence(start) { it.plusDays(1) }.takeWhile { it < endExclusive }
            .map { StatisticsBucket(it, it.plusDays(1), if (granularity == StatisticsGranularity.WEEK) "${it.monthValue}/${it.dayOfMonth}" else "${it.dayOfMonth}") }.toList()
    }
}

data class StatisticsBucket(val start: LocalDate, val endExclusive: LocalDate, val label: String)
