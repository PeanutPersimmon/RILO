package io.github.peanutpersimmon.rilo.ui.feature.editor.components

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderBellVisibilityTest {
    private val date = LocalDate.of(2026, 10, 5)

    @Test fun taskPlannedAndDueNeedBothDateAndClock() {
        assertFalse(reminderBellVisible(null, null))
        assertFalse(reminderBellVisible(date, null))
        assertFalse(reminderBellVisible(null, LocalTime.of(10, 0)))
        assertTrue(reminderBellVisible(date, LocalTime.of(10, 0)))
        assertTrue(reminderBellVisible(date, LocalTime.of(12, 0)))
    }

    @Test fun eventDateOnlyHidesBellAndStartClockShowsIt() {
        assertFalse(reminderBellVisible(date, null))
        assertTrue(reminderBellVisible(date, LocalTime.of(14, 0)))
    }
}
