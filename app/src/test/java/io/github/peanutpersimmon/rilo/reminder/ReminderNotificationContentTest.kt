package io.github.peanutpersimmon.rilo.reminder

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderNotificationContentTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(day: Int, hour: Int, minute: Int) = LocalDate.of(2026, 10, day)
        .atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test fun plannedAndDueShowAnchorClockAndEarlyOffset() {
        val planned = ScheduledReminder(ReminderAnchor.TASK_PLANNED, 1, at(5, 10, 0), 0)
        val due = ScheduledReminder(ReminderAnchor.TASK_DUE, 1, at(5, 12, 0), 10)
        assertEquals("10:00 · 计划开始", ReminderNotificationContent.body(planned, at(5, 10, 0), zone))
        assertEquals("12:00 截止 · 提前 10 分钟",
            ReminderNotificationContent.body(due, at(5, 11, 50), zone))
    }

    @Test fun eventAndCrossDayReminderShowCorrectDateAndTime() {
        val event = ScheduledReminder(ReminderAnchor.EVENT_START, 4, at(6, 14, 0), 0)
        assertEquals("14:00 · 日程开始", ReminderNotificationContent.body(event, at(6, 14, 0), zone))
        val early = event.copy(anchorMillis = at(6, 0, 30), offsetMinutes = 60)
        assertEquals("10/6 00:30 开始 · 提前 1 小时",
            ReminderNotificationContent.body(early, at(5, 23, 30), zone))
    }
}
