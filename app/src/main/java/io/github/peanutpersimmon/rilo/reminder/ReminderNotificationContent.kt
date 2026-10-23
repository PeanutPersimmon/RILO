package io.github.peanutpersimmon.rilo.reminder

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The anchor is the planned event time, even when an early alarm fires on another date. */
internal object ReminderNotificationContent {
    fun body(reminder: ScheduledReminder, nowMillis: Long, zone: ZoneId): String {
        val anchor = Instant.ofEpochMilli(reminder.anchorMillis).atZone(zone)
        val firedDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val time = anchor.format(DateTimeFormatter.ofPattern("HH:mm"))
        val whenLabel = if (anchor.toLocalDate() == firedDate) time
            else "${anchor.monthValue}/${anchor.dayOfMonth} $time"
        val early = when (reminder.offsetMinutes) {
            0 -> null
            60 -> "提前 1 小时"
            else -> "提前 ${reminder.offsetMinutes} 分钟"
        }
        return when (reminder.anchor) {
            ReminderAnchor.TASK_PLANNED -> if (early == null) "$whenLabel · 计划开始"
                else "$whenLabel 开始 · $early"
            ReminderAnchor.TASK_DUE -> if (early == null) "$whenLabel 截止"
                else "$whenLabel 截止 · $early"
            ReminderAnchor.EVENT_START -> if (early == null) "$whenLabel · 日程开始"
                else "$whenLabel 开始 · $early"
        }
    }
}
