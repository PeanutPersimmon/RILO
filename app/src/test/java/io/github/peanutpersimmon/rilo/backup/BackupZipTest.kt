package io.github.peanutpersimmon.rilo.backup

import io.github.peanutpersimmon.rilo.presentation.plan.PlanViewMode
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupZipTest {
    @get:Rule val folder = TemporaryFolder()
    private val manifest = BackupFormat.encodeManifest(BackupManifest(1, "1.0", 1, 6,
        "2026-10-04T00:00:00Z", "io.github.peanutpersimmon.rilo", "a".repeat(64), 10))
    private val settings = BackupFormat.encodeSettings(BackupSettings(true, PlanViewMode.entries))

    @Test fun generatedZipHasExactlyThreeEntriesAndNoAiCredentials() {
        val database = folder.newFile("rilo.db").apply { writeBytes("SQLite test".toByteArray()) }
        val archive = folder.newFile("backup.zip")
        archive.outputStream().use { BackupZip.write(it, manifest, database, settings) }
        ZipFile(archive).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            assertEquals(listOf("manifest.json", "database/rilo.db", "settings.json"), names)
            assertFalse(names.any { it.contains("rilo_qwen_credentials") || it.endsWith("-wal") || it.endsWith("-shm") })
            for (name in listOf("manifest.json", "settings.json")) {
                val text = zip.getInputStream(zip.getEntry(name)).bufferedReader().readText()
                for (secret in listOf("encrypted_api_key", "api_key_iv", "workspace_id"))
                    assertFalse(text.contains(secret))
            }
        }
        val extracted = BackupZip.extract(archive, File(folder.root, "candidate.db"))
        assertArrayEquals(database.readBytes(), extracted.database.readBytes())
        assertArrayEquals(settings, extracted.settings)
    }

    @Test fun missingUnknownAndTraversingEntriesAreRejected() {
        listOf(
            listOf(BackupZip.DATABASE, BackupZip.SETTINGS),
            listOf(BackupZip.MANIFEST, BackupZip.DATABASE, "other.json"),
            listOf(BackupZip.MANIFEST, BackupZip.DATABASE, "../settings.json"),
            listOf(BackupZip.MANIFEST, BackupZip.DATABASE, "..\\settings.json"),
        ).forEachIndexed { index, names ->
            val archive = folder.newFile("bad$index.zip")
            ZipOutputStream(archive.outputStream()).use { zip ->
                names.forEach { name ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(if (name == BackupZip.DATABASE) "db".toByteArray() else "{}".toByteArray())
                    zip.closeEntry()
                }
            }
            assertThrows(InvalidBackupException::class.java) {
                BackupZip.extract(archive, File(folder.root, "out$index.db"))
            }
        }
    }

    @Test fun duplicatedNameAndCorruptArchiveAreRejected() {
        val archive = folder.newFile("duplicate.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            listOf(BackupZip.MANIFEST, BackupZip.DATABASE, "manifeat.json").forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write("data".toByteArray())
                zip.closeEntry()
            }
        }
        val bytes = archive.readBytes()
        val old = "manifeat.json".toByteArray()
        val new = "manifest.json".toByteArray()
        for (i in 0..bytes.size - old.size) {
            if (old.indices.all { bytes[i + it] == old[it] }) new.copyInto(bytes, i)
        }
        assertThrows(InvalidBackupException::class.java) {
            BackupZip.extract(File(folder.root, "same-name.zip").apply { writeBytes(bytes) },
                File(folder.root, "duplicate.db"))
        }
        val duplicateCentral = archive.readBytes().copyOf().also { it[0] = 0 }
        assertThrows(InvalidBackupException::class.java) {
            BackupZip.extract(File(folder.root, "corrupt.zip").apply { writeBytes(duplicateCentral) },
                File(folder.root, "corrupt.db"))
        }
        assertThrows(InvalidBackupException::class.java) {
            BackupZip.extract(File(folder.root, "bad-header.zip").apply { writeBytes(byteArrayOf(1, 2, 3)) },
                File(folder.root, "header.db"))
        }
    }

    @Test fun symlinkMetadataAndExtremeCompressionAreRejected() {
        val ordinary = folder.newFile("ordinary.zip")
        ZipOutputStream(ordinary.outputStream()).use { zip ->
            listOf(BackupZip.MANIFEST, BackupZip.DATABASE, BackupZip.SETTINGS).forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write("data".toByteArray())
                zip.closeEntry()
            }
        }
        val bytes = ordinary.readBytes()
        val central = byteArrayOf(0x50, 0x4b, 0x01, 0x02)
        val offset = (0..bytes.size - central.size).first { position ->
            central.indices.all { bytes[position + it] == central[it] }
        }
        bytes[offset + 40] = 0
        bytes[offset + 41] = 0xA0.toByte() // Unix symlink type in external attributes.
        assertThrows(InvalidBackupException::class.java) {
            BackupZip.extract(File(folder.root, "symlink.zip").apply { writeBytes(bytes) },
                File(folder.root, "symlink.db"))
        }

        val compressed = folder.newFile("compressed.zip")
        ZipOutputStream(compressed.outputStream()).use { zip ->
            listOf(BackupZip.MANIFEST, BackupZip.DATABASE, BackupZip.SETTINGS).forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(if (name == BackupZip.DATABASE) ByteArray(10 * 1024 * 1024) else "{}".toByteArray())
                zip.closeEntry()
            }
        }
        assertThrows(InvalidBackupException::class.java) {
            BackupZip.extract(compressed, File(folder.root, "expanded.db"))
        }
    }
}
