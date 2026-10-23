package io.github.peanutpersimmon.rilo.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.preferences.AppearancePreferences
import io.github.peanutpersimmon.rilo.data.preferences.PlanViewOrderPreferences
import io.github.peanutpersimmon.rilo.reminder.ReminderCoordinator
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

enum class BackupStage { VALIDATING, SAVING_CURRENT, RESTORING, RESTARTING }
enum class RestoreOutcome { SUCCESS_RESTART, ROLLED_BACK_RESTART, RECOVERY_PENDING_RESTART }

class PreparedBackup internal constructor(
    val manifest: BackupManifest,
    val settings: BackupSettings,
    internal val candidate: File,
    internal val directory: File,
) {
    fun discard() { directory.deleteRecursively() }
}

class BackupService(private val context: Context) {
    private val application = context.applicationContext
    private val appearance = AppearancePreferences(application)
    private val planOrder = PlanViewOrderPreferences(application)

    fun suggestedFileName(): String = "RILO_backup_${LocalDateTime.now().format(
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"))}.zip"

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val stage = temporaryDirectory()
        try {
            val databaseSnapshot = File(stage, RidoDatabase.DATABASE_NAME)
            val settings = BackupOperationCoordinator.export {
                val currentSettings = readSettings()
                val database = RidoDatabase.getInstance(application)
                BackupFiles.checkpoint(database)
                BackupFiles.copySynced(BackupFiles.databaseFile(application), databaseSnapshot)
                currentSettings
            }
            val packageInfo = application.packageManager.getPackageInfo(application.packageName, 0)
            @Suppress("DEPRECATION")
            val code = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
            val manifest = BackupManifest(BACKUP_FORMAT_VERSION, packageInfo.versionName.orEmpty(), code,
                ROOM_VERSION, Instant.now().toString(), application.packageName,
                BackupFiles.sha256(databaseSnapshot), databaseSnapshot.length())
            val archive = File(stage, "backup.zip")
            archive.outputStream().use { output ->
                BackupZip.write(output, BackupFormat.encodeManifest(manifest), databaseSnapshot,
                    BackupFormat.encodeSettings(settings))
            }
            // Finish the archive locally before touching the user's selected SAF document.
            val output = application.contentResolver.openOutputStream(uri, "w")
                ?: throw IllegalStateException("Cannot open selected export destination")
            output.use { sink -> archive.inputStream().use { it.copyTo(sink) } }
        } catch (error: Exception) {
            Log.e("RidoBackup", "Export failed", error)
            throw error
        } finally { stage.deleteRecursively() }
    }

    suspend fun prepareImport(uri: Uri): PreparedBackup = withContext(Dispatchers.IO) {
        val stage = temporaryDirectory()
        try {
            val archive = File(stage, "source.zip")
            val input = application.contentResolver.openInputStream(uri)
                ?: throw InvalidBackupException("备份文件已损坏")
            input.use { source ->
                FileOutputStream(archive).use { target ->
                    val buffer = ByteArray(8192)
                    var copied = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        copied += count
                        if (copied > 600L * 1024 * 1024) throw InvalidBackupException("备份文件已损坏")
                        target.write(buffer, 0, count)
                    }
                    target.fd.sync()
                }
            }
            var parsedManifest: BackupManifest? = null
            val extracted = BackupZip.extract(archive, File(stage, "candidate.db")) { bytes ->
                parsedManifest = BackupFormat.parseManifest(bytes, application.packageName)
            }
            val manifest = requireNotNull(parsedManifest)
            if (extracted.database.length() != manifest.databaseSizeBytes)
                throw InvalidBackupException("备份文件已损坏")
            if (BackupFiles.sha256(extracted.database) != manifest.databaseSha256)
                throw InvalidBackupException("备份文件已损坏")
            val settings = BackupFormat.parseSettings(extracted.settings)
            BackupDatabaseValidator.validate(application, extracted.database, manifest.databaseVersion)
            archive.delete()
            PreparedBackup(manifest, settings, extracted.database, stage)
        } catch (error: Exception) {
            stage.deleteRecursively()
            Log.e("RidoBackup", "Import preflight failed", error)
            if (error is InvalidBackupException) throw error
            throw InvalidBackupException("备份文件已损坏", error)
        }
    }

    suspend fun restore(prepared: PreparedBackup, onStage: (BackupStage) -> Unit = {}): RestoreOutcome =
        withContext(Dispatchers.IO) {
            try {
                BackupOperationCoordinator.withImport {
                    val database = RidoDatabase.getInstance(application)
                    val reminders = ReminderCoordinator(application, database)
                    var closed = false
                    var closeAttempted = false
                    var markerWritten = false
                    try {
                        if (prepared.candidate.length() != prepared.manifest.databaseSizeBytes ||
                            BackupFiles.sha256(prepared.candidate) != prepared.manifest.databaseSha256)
                            throw InvalidBackupException("备份文件已损坏")
                        onStage(BackupStage.SAVING_CURRENT)
                        reminders.cancelAllForImport()
                        BackupFiles.checkpoint(database)
                        val rollback = BackupRecovery.rollbackDirectory(application)
                        if (rollback.exists()) rollback.deleteRecursively()
                        rollback.mkdirs()
                        BackupFiles.copySynced(BackupFiles.databaseFile(application),
                            File(rollback, BackupRecovery.ROLLBACK_DATABASE))
                        BackupFiles.writeSynced(BackupFormat.encodeSettings(readSettings()),
                            File(rollback, BackupRecovery.ROLLBACK_SETTINGS))
                        BackupRecovery.writeMarker(application, ImportMarker(ImportPhase.IMPORTING,
                            prepared.manifest.databaseVersion, prepared.manifest.databaseSha256))
                        markerWritten = true
                        BackupOperationCoordinator.freezeForRestart()
                        closeAttempted = true
                        RidoDatabase.closeForImport()
                        closed = true
                        onStage(BackupStage.RESTORING)
                        BackupFiles.replaceDatabase(application, prepared.candidate)
                        appearance.setDark(prepared.settings.night)
                        planOrder.setOrderNames(prepared.settings.planViewOrder.map { it.name })
                        val installed = BackupFiles.databaseFile(application)
                        if (installed.length() != prepared.manifest.databaseSizeBytes ||
                            BackupFiles.sha256(installed) != prepared.manifest.databaseSha256)
                            throw InvalidBackupException("备份文件已损坏")
                        BackupRecovery.writeMarker(application, ImportMarker(ImportPhase.REPLACED_PENDING_VERIFY,
                            prepared.manifest.databaseVersion, prepared.manifest.databaseSha256))
                        onStage(BackupStage.RESTARTING)
                        RestoreOutcome.SUCCESS_RESTART
                    } catch (error: Exception) {
                        Log.e("RidoBackup", "Import failed", error)
                        if (closeAttempted && !closed) return@withImport RestoreOutcome.RECOVERY_PENDING_RESTART
                        if (!closed) {
                            if (markerWritten) BackupRecovery.markerFile(application).delete()
                            ReminderCoordinator(application, database).rebuildAllFutureReminders(forceReschedule = true)
                            throw InvalidBackupException("导入失败，当前数据未改变", error)
                        }
                        try {
                            BackupRecovery.restoreRollback(application)
                            BackupRecovery.markerFile(application).delete()
                            RestoreOutcome.ROLLED_BACK_RESTART
                        } catch (rollbackError: Exception) {
                            Log.e("RidoBackup", "Rollback failed; startup recovery will retry", rollbackError)
                            RestoreOutcome.RECOVERY_PENDING_RESTART
                        }
                    }
                }
            } finally { prepared.discard() }
        }

    private suspend fun readSettings(): BackupSettings = BackupSettings(
        appearance.dark.first(), BackupFormat.normalizedOrder(planOrder.orderNames.first()))

    private fun temporaryDirectory(): File = File(application.cacheDir, "rilo_backup/${UUID.randomUUID()}").also {
        if (!it.mkdirs()) throw IllegalStateException("Cannot create backup staging directory")
    }
}
