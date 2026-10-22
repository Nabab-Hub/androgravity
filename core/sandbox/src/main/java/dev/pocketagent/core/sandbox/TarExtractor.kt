package dev.pocketagent.core.sandbox

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

object TarExtractor {

    private const val BLOCK_SIZE = 512

    fun extract(
        inputStream: InputStream,
        destinationDir: File,
        isGzip: Boolean = true,
        onProgress: (fileName: String, currentCount: Int) -> Unit = { _, _ -> }
    ) {
        if (!destinationDir.exists()) destinationDir.mkdirs()
        val destCanonical = destinationDir.canonicalPath

        val stream = if (isGzip) GZIPInputStream(inputStream) else inputStream
        val header = ByteArray(BLOCK_SIZE)
        var fileCount = 0

        while (true) {
            val bytesRead = readFully(stream, header)
            if (bytesRead < BLOCK_SIZE) break
            if (isAllZeros(header)) {
                // End of archive is marked by two zero blocks
                break
            }

            val rawName = readString(header, 0, 100)
            val prefix = readString(header, 345, 155)
            val fullName = if (prefix.isNotBlank()) "$prefix/$rawName" else rawName
            val sanitizedName = fullName.trim().removePrefix("./").removePrefix("/")

            if (sanitizedName.isBlank()) continue

            val size = readOctal(header, 124, 12)
            val typeFlag = header[156].toInt().toChar()

            val targetFile = File(destinationDir, sanitizedName)
            if (!targetFile.canonicalPath.startsWith(destCanonical)) {
                // Prevent ZipSlip / path traversal
                skipBytes(stream, calculatePaddedSize(size))
                continue
            }

            fileCount++
            onProgress(sanitizedName, fileCount)

            when (typeFlag) {
                '5' -> {
                    // Directory
                    targetFile.mkdirs()
                }
                '0', '\u0000' -> {
                    // Regular file
                    targetFile.parentFile?.mkdirs()
                    FileOutputStream(targetFile).use { out ->
                        copyBytes(stream, out, size)
                    }
                    val pad = calculatePaddedSize(size) - size
                    if (pad > 0) skipBytes(stream, pad)
                }
                '1', '2' -> {
                    // Link / Symlink: in userspace non-root, write the link target path into a text file or skip
                    val linkName = readString(header, 157, 100)
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeText(linkName)
                    val pad = calculatePaddedSize(size) - size
                    if (pad > 0) skipBytes(stream, pad)
                }
                else -> {
                    // Devices, fifos, etc. -> skip content in Android userspace
                    val padded = calculatePaddedSize(size)
                    if (padded > 0) skipBytes(stream, padded)
                }
            }
        }
    }

    private fun readString(buffer: ByteArray, offset: Int, length: Int): String {
        var end = offset
        while (end < offset + length && buffer[end] != 0.toByte()) {
            end++
        }
        return String(buffer, offset, end - offset, Charsets.UTF_8).trim()
    }

    private fun readOctal(buffer: ByteArray, offset: Int, length: Int): Long {
        var result = 0L
        for (i in offset until offset + length) {
            val b = buffer[i].toInt().toChar()
            if (b in '0'..'7') {
                result = (result shl 3) + (b - '0')
            } else if (result > 0 || b == ' ' || b == '\u0000') {
                if (result > 0) break
            }
        }
        return result
    }

    private fun calculatePaddedSize(size: Long): Long {
        return if (size % BLOCK_SIZE == 0L) size else ((size / BLOCK_SIZE) + 1) * BLOCK_SIZE
    }

    private fun readFully(stream: InputStream, buffer: ByteArray): Int {
        var totalRead = 0
        while (totalRead < buffer.size) {
            val read = stream.read(buffer, totalRead, buffer.size - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return totalRead
    }

    private fun copyBytes(input: InputStream, output: FileOutputStream, count: Long) {
        val buffer = ByteArray(4096)
        var remaining = count
        while (remaining > 0) {
            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read == -1) break
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun skipBytes(stream: InputStream, count: Long) {
        var remaining = count
        val buffer = ByteArray(4096)
        while (remaining > 0) {
            val toSkip = minOf(remaining, buffer.size.toLong()).toInt()
            val read = stream.read(buffer, 0, toSkip)
            if (read == -1) break
            remaining -= read
        }
    }

    private fun isAllZeros(buffer: ByteArray): Boolean {
        for (b in buffer) {
            if (b != 0.toByte()) return false
        }
        return true
    }
}
