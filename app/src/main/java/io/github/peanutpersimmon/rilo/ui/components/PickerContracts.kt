package io.github.peanutpersimmon.rilo.ui.components

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

fun interface DatePickerLauncher {
    fun show(initialDate: LocalDate?, onResult: (LocalDate?) -> Unit)
}

fun interface TimePickerLauncher {
    fun show(initialTime: LocalTime?, onResult: (LocalTime?) -> Unit)
}

fun interface DateTimePickerLauncher {
    fun show(
        initialDate: LocalDate,
        initialTime: LocalTime?,
        timeOptional: Boolean,
        onResult: (LocalDate, LocalTime?) -> Unit,
    )
}

/** Shared rules for every future month calendar; visual rendering is not implemented in this round. */
object CalendarVisualRules {
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY
    const val columnCount: Int = 7

    fun validDate(year: Int, month: Int, requestedDay: Int): LocalDate {
        val yearMonth = YearMonth.of(year, month)
        return yearMonth.atDay(requestedDay.coerceIn(1, yearMonth.lengthOfMonth()))
    }
}
