package io.github.peanutpersimmon.rilo.ui.components.picker

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

object PickerLogic {
    val years: IntRange = 1900..2100
    val months: IntRange = 1..12
    val hours: IntRange = 0..23
    val minutes: IntRange = 0..59

    fun days(year: Int, month: Int): IntRange = 1..YearMonth.of(year, month).lengthOfMonth()

    fun correctedDate(year: Int, month: Int, day: Int): LocalDate {
        val safeYear = year.coerceIn(years)
        val safeMonth = month.coerceIn(months)
        return YearMonth.of(safeYear, safeMonth).atDay(day.coerceIn(1, days(safeYear, safeMonth).last))
    }

    fun time(hour: Int, minute: Int): LocalTime =
        LocalTime.of(hour.coerceIn(hours), minute.coerceIn(minutes))
}
