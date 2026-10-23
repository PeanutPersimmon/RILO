package io.github.peanutpersimmon.rilo.reminder

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.peanutpersimmon.rilo.domain.time.RuntimeZoneId

class ReminderRebuildReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RuntimeZoneId.refresh()
        io.github.peanutpersimmon.rilo.presentation.time.SystemCurrentTimeProvider.refresh()
        val pending = goAsync()
        ReminderRuntime.rebuild(context.applicationContext as Application) { pending.finish() }
    }
}
