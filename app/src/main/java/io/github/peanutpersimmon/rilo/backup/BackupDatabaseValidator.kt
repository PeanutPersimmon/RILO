package io.github.peanutpersimmon.rilo.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import java.io.File
import java.util.UUID
import org.json.JSONObject

/** Validates a RILO v1 database, then asks production Room to validate an isolated copy. */
object BackupDatabaseValidator {
    fun validate(context: Context, candidate: File, declaredVersion: Int) {
        if (declaredVersion != ROOM_VERSION)
            throw InvalidBackupException("此备份来自 RILO 的预发布旧数据库结构，当前版本不支持导入。")
        try {
            val schema = context.assets.open(
                "io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase/$declaredVersion.json",
            ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
            SQLiteDatabase.openDatabase(candidate.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
                if (sqlite.version != declaredVersion) throw InvalidBackupException("不是有效的 RILO 备份")
                sqlite.rawQuery("PRAGMA integrity_check", null).use {
                    if (!it.moveToFirst() || it.getString(0) != "ok" || it.moveToNext())
                        throw InvalidBackupException("备份数据无法通过完整性检查")
                }
                sqlite.rawQuery("PRAGMA foreign_key_check", null).use {
                    if (it.moveToFirst()) throw InvalidBackupException("备份数据无法通过完整性检查")
                }
                val expected = schema.getJSONArray("entities")
                val expectedNames = buildSet {
                    for (i in 0 until expected.length()) add(expected.getJSONObject(i).getString("tableName"))
                }
                val expectedIndexes = buildSet {
                    for (i in 0 until expected.length()) {
                        val indexes = expected.getJSONObject(i).optJSONArray("indices") ?: continue
                        for (j in 0 until indexes.length()) add(indexes.getJSONObject(j).getString("name"))
                    }
                }
                val actualNames = mutableSetOf<String>()
                val actualIndexes = mutableSetOf<String>()
                sqlite.rawQuery("SELECT type,name FROM sqlite_master", null).use {
                    while (it.moveToNext()) {
                        when (it.getString(0)) {
                            "table" -> actualNames += it.getString(1)
                            "index" -> actualIndexes += it.getString(1)
                            else -> throw InvalidBackupException("不是有效的 RILO 备份")
                        }
                    }
                }
                if (!actualNames.containsAll(expectedNames + "room_master_table") ||
                    (actualNames - expectedNames - setOf("room_master_table", "sqlite_sequence", "android_metadata")).isNotEmpty() ||
                    (actualIndexes - expectedIndexes).any { !it.startsWith("sqlite_autoindex_") })
                    throw InvalidBackupException("不是有效的 RILO 备份")
                sqlite.rawQuery("SELECT identity_hash FROM room_master_table WHERE id=42", null).use {
                    if (!it.moveToFirst() || it.getString(0) != schema.getString("identityHash"))
                        throw InvalidBackupException("不是有效的 RILO 备份")
                }
                for (i in 0 until expected.length()) {
                    val entity = expected.getJSONObject(i)
                    val table = entity.getString("tableName")
                    val fields = entity.getJSONArray("fields")
                    val expectedColumns = buildSet {
                        for (j in 0 until fields.length()) add(fields.getJSONObject(j).getString("columnName"))
                    }
                    val actualColumns = mutableSetOf<String>()
                    sqlite.rawQuery("PRAGMA table_info(`$table`)", null).use {
                        val nameColumn = it.getColumnIndexOrThrow("name")
                        while (it.moveToNext()) actualColumns += it.getString(nameColumn)
                    }
                    if (actualColumns != expectedColumns) throw InvalidBackupException("不是有效的 RILO 备份")
                }
            }
            probeWithRoom(context, candidate)
        } catch (error: InvalidBackupException) {
            throw error
        } catch (error: Exception) {
            throw InvalidBackupException("备份数据无法通过完整性检查", error)
        }
    }

    private fun probeWithRoom(context: Context, candidate: File) {
        val name = "rilo_import_probe_${UUID.randomUUID()}.db"
        val probe = context.getDatabasePath(name)
        probe.parentFile?.mkdirs()
        try {
            candidate.copyTo(probe)
            val room = Room.databaseBuilder(context, RidoDatabase::class.java, name)
                .build()
            try {
                val sql = room.openHelper.writableDatabase // Room identity/schema validation.
                if (sql.version != ROOM_VERSION) throw InvalidBackupException("备份数据无法通过完整性检查")
                sql.query("SELECT COUNT(*) FROM sessions").close()
            } finally { room.close() }
        } finally {
            context.deleteDatabase(name)
            probe.delete()
        }
    }
}
