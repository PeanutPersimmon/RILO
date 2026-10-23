package io.github.peanutpersimmon.rilo.reminder

import io.github.peanutpersimmon.rilo.domain.model.Event
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ReminderPlanTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val day = LocalDate.of(2026, 10, 5)
    private fun event() = Event(id = 9, title = "日程", dateEpochDay = day.toEpochDay(),
        startMinuteOfDay = 840, startReminderOffsetMinutes = 60,
        createdAtMillis = 1, updatedAtMillis = 1)

    @Test fun onlyExplicitNonnegativeOffsetSchedules() {
        assertTrue(ReminderPlan.forEvent(event().copy(startReminderOffsetMinutes = null), zone).isEmpty())
        val start = day.atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        for (offset in listOf(0, 17, 150, 1440)) {
            val reminder = ReminderPlan.forEvent(event().copy(startReminderOffsetMinutes = offset), zone).single()
            assertEquals(ReminderAnchor.EVENT_START, reminder.anchor)
            assertEquals(start - offset * 60_000L, reminder.triggerMillis)
        }
    }

    @Test fun dateOnlyEventDoesNotScheduleAndMovedAlarmIsStale() {
        assertTrue(ReminderPlan.forEvent(event().copy(startMinuteOfDay = null), zone).isEmpty())
        val old = ReminderPlan.forEvent(event(), zone).single()
        val moved = ReminderPlan.forEvent(event().copy(startMinuteOfDay = 900), zone).single()
        assertEquals(old.key, moved.key)
        assertNotEquals(old.triggerMillis, moved.triggerMillis)
        assertFalse(ReminderPlan.matchesDeliveredAlarm(moved, old.anchorMillis, old.offsetMinutes, old.triggerMillis))
        assertTrue(ReminderPlan.matchesDeliveredAlarm(old, old.anchorMillis, old.offsetMinutes, old.triggerMillis))
    }
}
