package io.github.peanutpersimmon.rilo.ui.components.picker

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class PickerLogicTest {
    @Test fun leapYearFebruaryHas29Days() = assertEquals(29, PickerLogic.days(2024, 2).last)
    @Test fun commonYearFebruaryHas28Days() = assertEquals(28, PickerLogic.days(2023, 2).last)
    @Test fun aprilHas30Days() = assertEquals(30, PickerLogic.days(2026, 4).last)
    @Test fun januaryHas31Days() = assertEquals(31, PickerLogic.days(2026, 1).last)
    @Test fun invalidDayCorrectsToMonthEnd() = assertEquals(LocalDate.of(2025, 2, 28), PickerLogic.correctedDate(2025, 2, 31))
    @Test fun midnightIsSupported() = assertEquals(LocalTime.of(0, 0), PickerLogic.time(0, 0))
    @Test fun lastMinuteIsSupported() = assertEquals(LocalTime.of(23, 59), PickerLogic.time(23, 59))
}
