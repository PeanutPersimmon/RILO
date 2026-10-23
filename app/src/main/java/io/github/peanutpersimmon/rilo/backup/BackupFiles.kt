package io.github.peanutpersimmon.rilo.backup

import android.content.Context
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal object BackupFiles {
    fun checkpoint(database: RidoDatabase) {
        val sqlite = database.openHelper.writableDatabase
        sqlite.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
            if (!cursor.moveToFirst()) throw IllegalStateException("No WAL checkpoint result")
            val busy = cursor.getInt(0)
            val log = cursor.getInt(1)
            val merged = cursor.getInt(2)
            if (busy != 0 || log != merged && !(log == -1 && merged == -1))
                throw IllegalStateException("WAL checkpoint incomplete: $busy/$log/$merged")
        }
    }

    fun databaseFile(context: Context): File = context.getDatabasePath(RidoDatabase.DATABASE_NAME)

    fun copySynced(source: File, destination: File) {
        if (!source.isFile || source.length() == 0L) throw IllegalStateException("Missing database snapshot")
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.copying")
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            if (temporary.length() != source.length()) throw IllegalStateException("Snapshot copy incomplete")
            moveReplace(temporary, destination)
        } finally { temporary.delete() }
    }

    fun writeSynced(bytes: ByteArray, destination: File) {
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.writing")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            moveReplace(temporary, destination)
        } finally { temporary.delete() }
    }

    fun replaceDatabase(context: Context, source: File) {
        val target = databaseFile(context)
        listOf(target, File("${target.path}-wal"), File("${target.path}-shm")).forEach { file ->
            if (file.exists() && !file.delete()) throw IllegalStateException("Cannot remove old database file")
        }
        copySynced(source, target)
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun moveReplace(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
