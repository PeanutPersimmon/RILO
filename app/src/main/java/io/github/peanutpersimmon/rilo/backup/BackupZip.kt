package io.github.peanutpersimmon.rilo.backup

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** The format has exactly three regular files. No ZIP path is ever used as a filesystem path. */
object BackupZip {
    const val MAX_DATABASE_BYTES = 512L * 1024 * 1024
    private const val MAX_ARCHIVE_BYTES = 600L * 1024 * 1024
    private const val MAX_JSON_BYTES = 64L * 1024
    private const val MAX_RATIO = 1000L
    const val MANIFEST = "manifest.json"
    const val DATABASE = "database/rilo.db"
    const val SETTINGS = "settings.json"
    private val entries = setOf(MANIFEST, DATABASE, SETTINGS)

    data class Extracted(val manifest: ByteArray, val database: File, val settings: ByteArray)

    fun write(output: OutputStream, manifest: ByteArray, database: File, settings: ByteArray) {
        require(manifest.size <= MAX_JSON_BYTES && settings.size <= MAX_JSON_BYTES)
        require(database.length() in 1..MAX_DATABASE_BYTES)
        ZipOutputStream(output, StandardCharsets.UTF_8).use { zip ->
            fun entry(name: String, source: InputStream) {
                zip.putNextEntry(ZipEntry(name))
                source.use { it.copyTo(zip) }
                zip.closeEntry()
            }
            entry(MANIFEST, manifest.inputStream())
            entry(DATABASE, database.inputStream())
            entry(SETTINGS, settings.inputStream())
        }
    }

    fun extract(
        archive: File,
        outputDatabase: File,
        validateManifest: (ByteArray) -> Unit = {},
    ): Extracted {
        if (archive.length() !in 1..MAX_ARCHIVE_BYTES) throw InvalidBackupException("备份文件已损坏")
        try {
            checkCentralDirectory(archive)
            ZipFile(archive, StandardCharsets.UTF_8).use { zip ->
                val listed = Collections.list(zip.entries())
                if (listed.size != 3 || listed.map { it.name }.toSet() != entries)
                    throw InvalidBackupException("不是有效的 RILO 备份")
                var total = 0L
                fun read(name: String, limit: Long, sink: OutputStream): ByteArray? {
                    val entry = zip.getEntry(name) ?: throw InvalidBackupException("不是有效的 RILO 备份")
                    if (entry.isDirectory || entry.method !in setOf(ZipEntry.STORED, ZipEntry.DEFLATED) ||
                        entry.size !in 1..limit || entry.compressedSize < 1 ||
                        entry.size / entry.compressedSize > MAX_RATIO)
                        throw InvalidBackupException("备份文件已损坏")
                    val crc = CRC32()
                    val bytes = if (sink is java.io.ByteArrayOutputStream) sink else null
                    zip.getInputStream(entry).use { input ->
                        val buffer = ByteArray(8192)
                        var count = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            count += n
                            total += n
                            if (count > limit || total > MAX_DATABASE_BYTES + 2 * MAX_JSON_BYTES)
                                throw InvalidBackupException("备份文件已损坏")
                            crc.update(buffer, 0, n)
                            sink.write(buffer, 0, n)
                        }
                        if (count != entry.size || crc.value != entry.crc) throw InvalidBackupException("备份文件已损坏")
                    }
                    return bytes?.toByteArray()
                }
                val manifest = java.io.ByteArrayOutputStream().use { read(MANIFEST, MAX_JSON_BYTES, it)!! }
                validateManifest(manifest)
                outputDatabase.parentFile?.mkdirs()
                outputDatabase.outputStream().use { read(DATABASE, MAX_DATABASE_BYTES, it) }
                val settings = java.io.ByteArrayOutputStream().use { read(SETTINGS, MAX_JSON_BYTES, it)!! }
                return Extracted(manifest, outputDatabase, settings)
            }
        } catch (error: InvalidBackupException) {
            throw error
        } catch (error: Exception) {
            throw InvalidBackupException("备份文件已损坏", error)
        }
    }

    /** Reads Unix mode from each central header; ZipFile does not expose symlink attributes. */
    private fun checkCentralDirectory(file: File) = RandomAccessFile(file, "r").use { raf ->
        val length = raf.length()
        val floor = (length - 65_557).coerceAtLeast(0)
        var eocd = length - 22
        while (eocd >= floor) {
            raf.seek(eocd)
            if (raf.leInt() == 0x06054b50L) break
            eocd--
        }
        if (eocd < floor) throw InvalidBackupException("备份文件已损坏")
        raf.seek(eocd + 4)
        val disk = raf.leShort()
        val centralDisk = raf.leShort()
        val diskEntries = raf.leShort()
        val count = raf.leShort()
        val centralSize = raf.leInt()
        val centralOffset = raf.leInt()
        val commentLength = raf.leShort()
        if (disk != 0 || centralDisk != 0 || diskEntries != 3 || count != 3 ||
            centralSize == 0xffffffffL || centralOffset == 0xffffffffL ||
            commentLength != 0 || eocd + 22 != length || centralOffset + centralSize != eocd)
            throw InvalidBackupException("不是有效的 RILO 备份")
        raf.seek(centralOffset)
        val names = mutableSetOf<String>()
        repeat(count) {
            if (raf.leInt() != 0x02014b50L) throw InvalidBackupException("备份文件已损坏")
            val madeBy = raf.leShort()
            raf.leShort() // version needed
            val flags = raf.leShort()
            val method = raf.leShort()
            raf.skipBytes(4) // DOS time/date
            raf.leInt() // CRC
            val compressed = raf.leInt()
            val uncompressed = raf.leInt()
            val nameLength = raf.leShort()
            val extraLength = raf.leShort()
            val entryCommentLength = raf.leShort()
            val entryDisk = raf.leShort()
            raf.leShort() // internal attributes
            val external = raf.leInt()
            val localOffset = raf.leInt()
            if (flags and 1 != 0 || flags and 0x40 != 0 || method !in setOf(0, 8) ||
                compressed == 0xffffffffL || uncompressed == 0xffffffffL ||
                nameLength !in 1..64 || extraLength != 0 || entryCommentLength != 0 ||
                entryDisk != 0 || localOffset >= centralOffset)
                throw InvalidBackupException("备份文件已损坏")
            // Reject special file modes even if the producer lies about the originating OS.
            val unixType = ((external ushr 16) and 0xf000).toInt()
            if (unixType != 0 && unixType != 0x8000) throw InvalidBackupException("备份文件已损坏")
            val nameBytes = ByteArray(nameLength)
            raf.readFully(nameBytes)
            val name = nameBytes.toString(StandardCharsets.US_ASCII)
            if (name !in entries || !names.add(name)) throw InvalidBackupException("不是有效的 RILO 备份")
        }
        if (raf.filePointer != eocd) throw InvalidBackupException("备份文件已损坏")
    }

    private fun RandomAccessFile.leShort(): Int {
        val a = readUnsignedByte()
        val b = readUnsignedByte()
        return a or (b shl 8)
    }

    private fun RandomAccessFile.leInt(): Long =
        leShort().toLong() or (leShort().toLong() shl 16)
}
