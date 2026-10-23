package io.github.peanutpersimmon.rilo.backup

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.peanutpersimmon.rilo.data.preferences.AppearancePreferences
import io.github.peanutpersimmon.rilo.data.preferences.PlanViewOrderPreferences
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class ImportPhase { IMPORTING, REPLACED_PENDING_VERIFY }

internal data class ImportMarker(val phase: ImportPhase, val databaseVersion: Int, val sha256: String)

internal object BackupRecovery {
    private const val TAG = "RidoBackupRecovery"
    private suspend fun writeSettings(context: Context, settings: BackupSettings) {
        AppearancePreferences(context).setDark(settings.night)
        PlanViewOrderPreferences(context).setOrderNames(settings.planViewOrder.map { it.name })
    }
    fun rollbackDirectory(context: Context) = File(context.filesDir, "backup_rollback")
    fun markerFile(context: Context) = File(context.filesDir, "backup_import_state.json")

    fun writeMarker(context: Context, value: ImportMarker) {
        val bytes = JsonObject().apply {
            addProperty("phase", value.phase.name)
            addProperty("databaseVersion", value.databaseVersion)
            addProperty("sha256", value.sha256)
        }.toString().toByteArray(Charsets.UTF_8)
        BackupFiles.writeSynced(bytes, markerFile(context))
    }

    suspend fun recoverBeforeDatabaseOpen(
        context: Context,
        settingsWriter: suspend (Context, BackupSettings) -> Unit = ::writeSettings,
    ): Boolean = withContext(Dispatchers.IO) {
        val marker = markerFile(context)
        if (!marker.exists()) return@withContext true
        try {
            val state = parseMarker(marker)
            when (state.phase) {
                ImportPhase.IMPORTING -> restoreRollback(context, settingsWriter)
                ImportPhase.REPLACED_PENDING_VERIFY -> {
                    try {
                        val current = BackupFiles.databaseFile(context)
                        if (BackupFiles.sha256(current) != state.sha256)
                            throw InvalidBackupException("备份文件已损坏")
                        BackupDatabaseValidator.validate(context, current, state.databaseVersion)
                    } catch (error: Exception) {
                        Log.e(TAG, "Restored database failed cold-start validation; rolling back", error)
                        restoreRollback(context, settingsWriter)
                    }
                }
            }
            if (!marker.delete()) throw IllegalStateException("Could not clear import marker")
            rollbackDirectory(context).deleteRecursively()
            true
        } catch (error: Exception) {
            Log.e(TAG, "Interrupted import recovery failed; refusing to open Room", error)
            false
        }
    }

    suspend fun restoreRollback(
        context: Context,
        settingsWriter: suspend (Context, BackupSettings) -> Unit = ::writeSettings,
    ) {
        val directory = rollbackDirectory(context)
        val database = File(directory, ROLLBACK_DATABASE)
        val settings = File(directory, ROLLBACK_SETTINGS)
        if (!database.isFile || !settings.isFile) throw IllegalStateException("Rollback files are missing")
        val parsed = BackupFormat.parseSettings(settings.readBytes())
        BackupFiles.replaceDatabase(context, database)
        settingsWriter(context, parsed)
    }

    private fun parseMarker(file: File): ImportMarker {
        val json = JsonParser.parseString(file.readText()).asJsonObject
        return ImportMarker(
            ImportPhase.valueOf(json.get("phase").asString),
            json.get("databaseVersion").asInt,
            json.get("sha256").asString,
        )
    }

    const val ROLLBACK_DATABASE = "rilo.db"
    const val ROLLBACK_SETTINGS = "settings.json"
}
