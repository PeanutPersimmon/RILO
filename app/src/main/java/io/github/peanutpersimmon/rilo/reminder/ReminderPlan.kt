package io.github.peanutpersimmon.rilo.reminder

import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.time.epochDayMinuteToMillis
import java.time.ZoneId

enum class ReminderAnchor { TASK_PLANNED, TASK_DUE, EVENT_START }

data class ScheduledReminder(
    val anchor: ReminderAnchor,
    val ownerId: Long,
    val anchorMillis: Long,
    val offsetMinutes: Int,
) {
    val triggerMillis: Long get() = anchorMillis - offsetMinutes * 60_000L
    val key: String get() = "${anchor.name}:$ownerId"
}

object ReminderPlan {
    fun future(reminders: Iterable<ScheduledReminder>, nowMillis: Long): List<ScheduledReminder> =
        reminders.filter { it.triggerMillis > nowMillis }

    fun matchesDeliveredAlarm(
        current: ScheduledReminder?, anchorMillis: Long, offsetMinutes: Int, triggerMillis: Long,
    ): Boolean = current != null && current.anchorMillis == anchorMillis &&
        current.offsetMinutes == offsetMinutes && current.triggerMillis == triggerMillis

    fun forEvent(event: Event, zone: ZoneId): List<ScheduledReminder> {
        val minute = event.startMinuteOfDay ?: return emptyList()
        val offset = event.startReminderOffsetMinutes ?: return emptyList()
        return listOf(ScheduledReminder(ReminderAnchor.EVENT_START, event.id,
            epochDayMinuteToMillis(event.dateEpochDay, minute, zone), offset))
    }
}
