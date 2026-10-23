package io.github.peanutpersimmon.rilo.backup

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rilo.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Compiled in CI; actual cross-process relaunch still needs a device check. */
@RunWith(AndroidJUnit4::class)
class BackupRestartManifestTest {
    @Test fun restartTrampolineUsesIndependentProcessAndTask() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.packageManager
        val main = manager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        val helper = manager.getActivityInfo(ComponentName(context, BackupRestartActivity::class.java), 0)
        assertEquals("${context.packageName}:restart", helper.processName)
        assertNotEquals(main.taskAffinity, helper.taskAffinity)
    }
}
