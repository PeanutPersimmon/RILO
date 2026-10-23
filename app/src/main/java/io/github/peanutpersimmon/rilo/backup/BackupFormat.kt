package io.github.peanutpersimmon.rilo.backup

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.peanutpersimmon.rilo.presentation.plan.PlanViewMode
import io.github.peanutpersimmon.rilo.presentation.plan.PlanViewModel
import java.time.Instant

const val BACKUP_FORMAT_VERSION = 2
const val ROOM_VERSION = 1

data class BackupManifest(
    val backupFormatVersion: Int,
    val appVersionName: String,
    val appVersionCode: Long,
    val databaseVersion: Int,
    val exportedAt: String,
    val packageName: String,
    val databaseSha256: String,
    val databaseSizeBytes: Long,
)

data class BackupSettings(val night: Boolean, val planViewOrder: List<PlanViewMode>)

class InvalidBackupException(val userMessage: String, cause: Throwable? = null) :
    IllegalArgumentException(userMessage, cause)

object BackupFormat {
    fun encodeManifest(value: BackupManifest): ByteArray = JsonObject().apply {
        addProperty("backupFormatVersion", value.backupFormatVersion)
        addProperty("appVersionName", value.appVersionName)
        addProperty("appVersionCode", value.appVersionCode)
        addProperty("databaseVersion", value.databaseVersion)
        addProperty("exportedAt", value.exportedAt)
        addProperty("packageName", value.packageName)
        addProperty("databaseSha256", value.databaseSha256)
        addProperty("databaseSizeBytes", value.databaseSizeBytes)
    }.toString().toByteArray(Charsets.UTF_8)

    fun parseManifest(bytes: ByteArray, expectedPackage: String): BackupManifest = try {
        val json = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
        val allowed = setOf("backupFormatVersion", "appVersionName", "appVersionCode", "databaseVersion",
            "exportedAt", "packageName", "databaseSha256", "databaseSizeBytes")
        require(json.keySet() == allowed)
        val version = json.int("backupFormatVersion")
        if (version != BACKUP_FORMAT_VERSION) throw InvalidBackupException("此备份来自 RILO 的预发布旧数据库结构，当前版本不支持导入。")
        val dbVersion = json.int("databaseVersion")
        if (dbVersion != ROOM_VERSION) throw InvalidBackupException("此备份来自 RILO 的预发布旧数据库结构，当前版本不支持导入。")
        val packageName = json.string("packageName")
        require(packageName == expectedPackage)
        val hash = json.string("databaseSha256")
        require(hash.matches(Regex("[0-9a-f]{64}")))
        val size = json.long("databaseSizeBytes")
        require(size in 1..BackupZip.MAX_DATABASE_BYTES)
        val exportedAt = json.string("exportedAt")
        Instant.parse(exportedAt)
        BackupManifest(version, json.string("appVersionName"), json.long("appVersionCode"),
            dbVersion, exportedAt, packageName, hash, size)
    } catch (error: InvalidBackupException) {
        throw error
    } catch (error: Exception) {
        throw InvalidBackupException("不是有效的 RILO 备份", error)
    }

    fun encodeSettings(settings: BackupSettings): ByteArray = JsonObject().apply {
        addProperty("night", settings.night)
        add("planViewOrder", JsonArray().also { array ->
            settings.planViewOrder.forEach { array.add(it.name) }
        })
    }.toString().toByteArray(Charsets.UTF_8)

    fun parseSettings(bytes: ByteArray): BackupSettings = try {
        val json = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
        require(json.keySet().all { it == "night" || it == "planViewOrder" })
        val night = if (json.has("night")) {
            val value = json.get("night")
            require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean)
            value.asBoolean
        } else false
        val order = if (json.has("planViewOrder")) {
            val value = json.get("planViewOrder")
            require(value.isJsonArray)
            val names = value.asJsonArray.map { item ->
                require(item.isJsonPrimitive && item.asJsonPrimitive.isString)
                item.asString
            }
            val currentNames = PlanViewMode.entries.map { it.name }.toSet()
            require(names.size == names.toSet().size && names.toSet() == currentNames)
            normalizedOrder(names)
        } else PlanViewModel.DEFAULT_VIEW_ORDER
        BackupSettings(night, order)
    } catch (error: Exception) {
        throw InvalidBackupException("不是有效的 RILO 备份", error)
    }

    fun normalizedOrder(names: List<String>): List<PlanViewMode> {
        val parsed = names.mapNotNull { name -> PlanViewMode.entries.firstOrNull { it.name == name } }.distinct()
        return parsed + PlanViewModel.DEFAULT_VIEW_ORDER.filter { it !in parsed }
    }

    private fun JsonObject.string(key: String): String {
        val value = get(key)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }

    private fun JsonObject.long(key: String): Long {
        val value = get(key)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        return value.asString.toLong().also { require(it >= 0) }
    }

    private fun JsonObject.int(key: String): Int = long(key).toInt().also { require(it.toLong() == long(key)) }
}
