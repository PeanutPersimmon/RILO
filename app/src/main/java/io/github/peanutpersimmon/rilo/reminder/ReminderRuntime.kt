package io.github.peanutpersimmon.rilo.reminder

import android.app.Application
import android.app.AlarmManager
import android.os.Build
import java.io.File
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.backup.BackupRecovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import io.github.peanutpersimmon.rilo.domain.time.RuntimeZoneId

class RidoApplication : Application() {
    var recoveryFailed: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        RuntimeZoneId.refresh()
        val processName = if (Build.VERSION.SDK_INT >= 28) getProcessName()
            else File("/proc/self/cmdline").readText().trim('\u0000')
        if (processName.endsWith(":restart")) return
        // A normal launch has no import marker. Only an interrupted import must block Room open.
        if (BackupRecovery.markerFile(this).exists()) {
            recoveryFailed = !runBlocking { BackupRecovery.recoverBeforeDatabaseOpen(this@RidoApplication) }
        }
        if (recoveryFailed) return
        ReminderRuntime.start(this)
    }
}

object ReminderRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var accessSnapshot: Pair<Boolean, Boolean>? = null
    fun start(application: Application) {
        scope.launch {
            try {
                accessSnapshot = access(application)
                val database = RidoDatabase.getInstance(application)
                val coordinator = ReminderCoordinator(application, database)
                ReminderNotifications.createChannel(application)
                var initial = true
                // Room emits after the outer transaction commits, including Voice and parent reconciliation.
                database.invalidationTracker.createFlow("events").collect {
                    try { coordinator.rebuildAllFutureReminders(forceReschedule = initial) }
                    catch (error: CancellationException) { throw error }
                    catch (error: Exception) { Log.e("RidoReminder", "Alarm reconciliation failed", error) }
                    initial = false
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("RidoReminder", "Startup alarm reconciliation failed", error)
            }
        }
    }

    fun onForeground(application: Application) {
        RuntimeZoneId.refresh()
        io.github.peanutpersimmon.rilo.presentation.time.SystemCurrentTimeProvider.refresh()
        scope.launch {
            val current = access(application)
            val previous = accessSnapshot ?: return@launch // Startup reconciliation is already queued.
            if (previous != current) {
                accessSnapshot = current
                rebuild(application)
            }
        }
    }

    private fun access(application: Application): Pair<Boolean, Boolean> =
        NotificationManagerCompat.from(application).areNotificationsEnabled() to
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                application.getSystemService(AlarmManager::class.java).canScheduleExactAlarms())

    fun rebuild(application: Application, onFinished: (() -> Unit)? = null) {
        scope.launch {
            try { ReminderCoordinator(application, RidoDatabase.getInstance(application))
                .rebuildAllFutureReminders(forceReschedule = true) }
            catch (error: Exception) { Log.e("RidoReminder", "Alarm rebuild failed", error) }
            finally { onFinished?.invoke() }
        }
    }
}
