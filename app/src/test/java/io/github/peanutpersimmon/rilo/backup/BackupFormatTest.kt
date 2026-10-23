package io.github.peanutpersimmon.rilo.backup

import io.github.peanutpersimmon.rilo.presentation.plan.PlanViewMode
import org.junit.Assert.*
import org.junit.Test

class BackupFormatTest {
    private val packageName = "io.github.peanutpersimmon.rilo"
    private val manifest = BackupManifest(2, "1.0", 1, 1, "2026-10-04T00:00:00Z",
        packageName, "a".repeat(64), 123)

    @Test fun manifestRoundTripKeepsFormatAndRoomVersionsSeparate() {
        assertEquals(manifest, BackupFormat.parseManifest(BackupFormat.encodeManifest(manifest), packageName))
        assertEquals(2, BACKUP_FORMAT_VERSION)
        assertEquals(1, ROOM_VERSION)
    }

    @Test fun unsupportedFormatPackageAndFutureDatabaseAreRejected() {
        listOf(
            manifest.copy(backupFormatVersion = 1),
            manifest.copy(packageName = "another.app"),
            manifest.copy(databaseVersion = 7),
        ).forEach { value ->
            assertThrows(InvalidBackupException::class.java) {
                BackupFormat.parseManifest(BackupFormat.encodeManifest(value), packageName)
            }
        }
    }

    @Test fun preReleaseV7BackupHasExplicitRejectionMessage() {
        val old = manifest.copy(backupFormatVersion = 1, databaseVersion = 7)
        val error = assertThrows(InvalidBackupException::class.java) {
            BackupFormat.parseManifest(BackupFormat.encodeManifest(old), packageName)
        }
        assertEquals("此备份来自 RILO 的预发布旧数据库结构，当前版本不支持导入。", error.userMessage)
    }

    @Test fun settingsRoundTripContainsOnlyWhiteListedKeys() {
        val settings = BackupSettings(true, PlanViewMode.entries.reversed())
        val json = BackupFormat.encodeSettings(settings).toString(Charsets.UTF_8)
        assertEquals(settings, BackupFormat.parseSettings(json.toByteArray()))
        assertTrue(json.contains("\"night\":true"))
        assertTrue(json.contains("\"planViewOrder\""))
        listOf("encrypted_api_key", "api_key_iv", "workspace_id", "rilo_qwen_credentials",
            "provider", "model", "endpoint").forEach { assertFalse(json.contains(it)) }
    }

    @Test fun missingOptionalSettingsUseDefaultsButInvalidValuesFail() {
        val defaults = BackupFormat.parseSettings("{}".toByteArray())
        assertFalse(defaults.night)
        assertEquals(PlanViewMode.entries.toSet(), defaults.planViewOrder.toSet())
        listOf(
            "{\"night\":\"true\"}",
            "{\"night\":1}",
            "{\"planViewOrder\":[\"UNKNOWN\"]}",
            "{\"planViewOrder\":[\"DAY\",\"DAY\"]}",
            "{\"encrypted_api_key\":\"secret\"}",
        ).forEach { value ->
            assertThrows(InvalidBackupException::class.java) {
                BackupFormat.parseSettings(value.toByteArray())
            }
        }
    }

    @Test fun legacySixViewBackupIsRejected() {
        val json = """{"night":false,"planViewOrder":["TASK_LIST","DAY","WEEK","BOARD","TODO_DONE","MONTH"]}"""
        assertThrows(InvalidBackupException::class.java) {
            BackupFormat.parseSettings(json.toByteArray())
        }
    }
}
