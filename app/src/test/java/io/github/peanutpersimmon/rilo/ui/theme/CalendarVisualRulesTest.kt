package io.github.peanutpersimmon.rilo.ui.theme

import io.github.peanutpersimmon.rilo.ui.components.CalendarVisualRules
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarVisualRulesTest {
    @Test
    fun calendar_startsOnMonday_andHasSevenColumns() {
        assertEquals(DayOfWeek.MONDAY, CalendarVisualRules.firstDayOfWeek)
        assertEquals(7, CalendarVisualRules.columnCount)
    }

    @Test
    fun invalidDay_isClampedToMonthLength() {
        assertEquals(LocalDate.of(2024, 2, 29), CalendarVisualRules.validDate(2024, 2, 31))
        assertEquals(LocalDate.of(2025, 2, 28), CalendarVisualRules.validDate(2025, 2, 31))
    }
}
