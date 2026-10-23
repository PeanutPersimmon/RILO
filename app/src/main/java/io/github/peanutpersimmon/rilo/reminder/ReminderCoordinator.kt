package io.github.peanutpersimmon.rilo.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.mapper.toDomain
import io.github.peanutpersimmon.rilo.backup.BackupOperationCoordinator
import java.time.ZoneId
import io.github.peanutpersimmon.rilo.domain.time.RuntimeZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reconciles committed Room state with OS alarms. The registry is only a scheduling cache. */
class ReminderCoordinator(private val context: Context, private val database: RidoDatabase) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val registry = context.getSharedPreferences("rilo_reminder_alarm_registry", Context.MODE_PRIVATE)
    private val mutex = globalMutex

    suspend fun rebuildAllFutureReminders(forceReschedule: Boolean = false) = BackupOperationCoordinator.mutation {
        mutex.withLock {
        val now = System.currentTimeMillis()
        val zone = RuntimeZoneId.current
        val desired = database.eventDao().getAll().flatMap { ReminderPlan.forEvent(it.toDomain(), zone) }
            .associateBy { it.key }
        val previous = registry.getStringSet("keys", emptySet()).orEmpty().toSet()
        val nextKeys = mutableSetOf<String>()
        val edit = registry.edit()
        (previous - desired.keys).forEach { key ->
            cancel(key)
            edit.remove("alarm_$key")
        }
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        desired.values.forEach { reminder ->
            val key = reminder.key
            val content = "${reminder.anchorMillis}:${reminder.offsetMinutes}:${reminder.triggerMillis}"
            val signature = "$content:$exact"
            val old = registry.getString("alarm_$key", null)
            if (reminder.triggerMillis > now) {
                if (forceReschedule || old != signature) schedule(reminder)
                edit.putString("alarm_$key", signature)
                nextKeys += key
            } else if (old?.startsWith("$content:") == true) {
                // An inexact alarm may still be pending after its requested trigger.
                nextKeys += key
            } else {
                if (key in previous) cancel(key)
                edit.remove("alarm_$key")
            }
        }
        edit.putStringSet("keys", nextKeys).commit()
        }
    }

    suspend fun cancelAllForImport() = BackupOperationCoordinator.mutation {
        mutex.withLock {
            val keys = registry.getStringSet("keys", emptySet()).orEmpty().toSet()
            val edit = registry.edit()
            keys.forEach { key -> cancel(key); edit.remove("alarm_$key") }
            edit.remove("keys").commit()
        }
    }

    private fun schedule(reminder: ScheduledReminder) {
        // Remove the previous token before creating a new one so queued old broadcasts keep old extras.
        cancel(reminder.key)
        val intent = alarmIntent(reminder.key).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_ANCHOR_MILLIS, reminder.anchorMillis)
            putExtra(ReminderAlarmReceiver.EXTRA_OFFSET, reminder.offsetMinutes)
            putExtra(ReminderAlarmReceiver.EXTRA_TRIGGER_MILLIS, reminder.triggerMillis)
        }
        val pending = PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms())
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerMillis, pending)
            else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerMillis, pending)
        } catch (error: SecurityException) {
            // Exact access can be revoked between the check and the call.
            Log.w("RiloReminder", "Exact alarm access changed; using an inexact alarm", error)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerMillis, pending)
        }
    }

    private fun cancel(key: String) {
        PendingIntent.getBroadcast(context, 0, alarmIntent(key),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun alarmIntent(key: String): Intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
        action = "io.github.peanutpersimmon.rilo.REMINDER"
        data = Uri.parse("rilo-reminder://alarm/$key")
    }

    companion object { private val globalMutex = Mutex() }
}
