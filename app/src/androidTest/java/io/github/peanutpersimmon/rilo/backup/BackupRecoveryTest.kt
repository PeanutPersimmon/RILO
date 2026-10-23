package io.github.peanutpersimmon.rilo.backup

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rilo.presentation.plan.PlanViewMode
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRecoveryTest {
    private class IsolatedContext(base: Context, private val root: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
        override fun getDatabasePath(name: String): File = File(root, "databases/$name").apply {
            parentFile?.mkdirs()
        }
    }

    @Test fun interruptedImportRestoresDatabaseAndWhiteListedSettingsWithoutTouchingCredentials(): Unit = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "rollback-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = IsolatedContext(base, root)
        try {
            val current = BackupFiles.databaseFile(context)
            current.writeText("candidate database")
            File("${current.path}-wal").writeText("stale WAL")
            File("${current.path}-shm").writeText("stale SHM")
            val rollback = BackupRecovery.rollbackDirectory(context).apply { mkdirs() }
            File(rollback, BackupRecovery.ROLLBACK_DATABASE).writeText("original database")
            val originalSettings = BackupSettings(true, PlanViewMode.entries.reversed())
            File(rollback, BackupRecovery.ROLLBACK_SETTINGS)
                .writeBytes(BackupFormat.encodeSettings(originalSettings))
            val credentialFile = File(context.filesDir, "datastore/rilo_qwen_credentials.preferences_pb")
                .apply { parentFile?.mkdirs(); writeText("device-only-credential") }
            BackupRecovery.writeMarker(context, ImportMarker(ImportPhase.IMPORTING, 6, "a".repeat(64)))
            var restoredSettings: BackupSettings? = null
            assertTrue(BackupRecovery.recoverBeforeDatabaseOpen(context) { _, settings ->
                restoredSettings = settings
            })
            assertEquals("original database", current.readText())
            assertEquals(originalSettings, restoredSettings)
            assertFalse(File("${current.path}-wal").exists())
            assertFalse(File("${current.path}-shm").exists())
            assertEquals("device-only-credential", credentialFile.readText())
            assertFalse(BackupRecovery.markerFile(context).exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun failedColdStartVerificationAutomaticallyUsesRollback(): Unit = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "verify-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = IsolatedContext(base, root)
        try {
            val current = BackupFiles.databaseFile(context).apply { writeText("bad replacement") }
            val rollback = BackupRecovery.rollbackDirectory(context).apply { mkdirs() }
            File(rollback, BackupRecovery.ROLLBACK_DATABASE).writeText("good original")
            File(rollback, BackupRecovery.ROLLBACK_SETTINGS).writeBytes(
                BackupFormat.encodeSettings(BackupSettings(false, PlanViewMode.entries)))
            BackupRecovery.writeMarker(context, ImportMarker(ImportPhase.REPLACED_PENDING_VERIFY,
                6, "a".repeat(64)))
            var settingsRestored = false
            assertTrue(BackupRecovery.recoverBeforeDatabaseOpen(context) { _, _ ->
                settingsRestored = true
            })
            assertEquals("good original", current.readText())
            assertTrue(settingsRestored)
        } finally { root.deleteRecursively() }
    }

    @Test fun failedSettingsRestoreKeepsMarkerAndRetriesBeforeOpeningDatabase(): Unit = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "settings-failure-${UUID.randomUUID()}").apply { mkdirs() }
        val context = IsolatedContext(base, root)
        try {
            val current = BackupFiles.databaseFile(context).apply { writeText("replacement") }
            val rollback = BackupRecovery.rollbackDirectory(context).apply { mkdirs() }
            File(rollback, BackupRecovery.ROLLBACK_DATABASE).writeText("old database")
            val expected = BackupSettings(true, PlanViewMode.entries.reversed())
            File(rollback, BackupRecovery.ROLLBACK_SETTINGS)
                .writeBytes(BackupFormat.encodeSettings(expected))
            BackupRecovery.writeMarker(context, ImportMarker(ImportPhase.IMPORTING, 6, "a".repeat(64)))

            assertFalse(BackupRecovery.recoverBeforeDatabaseOpen(context) { _, _ ->
                error("simulated settings write failure")
            })
            assertTrue(BackupRecovery.markerFile(context).exists())
            var restored: BackupSettings? = null
            assertTrue(BackupRecovery.recoverBeforeDatabaseOpen(context) { _, settings ->
                restored = settings
            })
            assertEquals("old database", current.readText())
            assertEquals(expected, restored)
            assertFalse(BackupRecovery.markerFile(context).exists())
        } finally { root.deleteRecursively() }
    }
}
