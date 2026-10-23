package io.github.peanutpersimmon.rilo.domain.service

import java.time.LocalDate
import java.time.YearMonth

/** Mirrors Sleep's seven-day windows, clipped at each month boundary. */
data class SleepTrendWindow(val start: LocalDate, val end: LocalDate) {
    companion object {
        fun current(today: LocalDate): SleepTrendWindow {
            val month = YearMonth.from(today)
            val initial = today.minusDays(6)
            val start = maxOf(initial, month.atDay(1))
            val end = if (initial.isBefore(month.atDay(1))) minOf(start.plusDays(6), month.atEndOfMonth()) else today
            return SleepTrendWindow(start, end)
        }
        private fun first(month: YearMonth): SleepTrendWindow =
            SleepTrendWindow(month.atDay(1), minOf(month.atDay(7), month.atEndOfMonth()))
        private fun last(month: YearMonth): SleepTrendWindow =
            SleepTrendWindow(maxOf(month.atEndOfMonth().minusDays(6), month.atDay(1)), month.atEndOfMonth())
    }

    fun shifted(direction: Int): SleepTrendWindow {
        val month = YearMonth.from(start)
        return when {
            direction < 0 && start.isAfter(month.atDay(1)) -> {
                val next = maxOf(start.minusDays(7), month.atDay(1))
                SleepTrendWindow(next, minOf(next.plusDays(6), month.atEndOfMonth()))
            }
            direction < 0 -> last(month.minusMonths(1))
            direction > 0 && end.isBefore(month.atEndOfMonth()) -> {
                val next = start.plusDays(7)
                SleepTrendWindow(next, minOf(next.plusDays(6), month.atEndOfMonth()))
            }
            direction > 0 -> first(month.plusMonths(1))
            else -> this
        }
    }
}
