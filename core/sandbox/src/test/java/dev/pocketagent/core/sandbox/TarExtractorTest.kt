package dev.pocketagent.core.sandbox

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

class TarExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testTarExtraction() {
        val dest = tempFolder.newFolder("extracted")
        val archiveBytes = createSampleTar(
            listOf(
                TarTestEntry("hello.txt", "Hello PocketAgent!".toByteArray(), isDirectory = false),
                TarTestEntry("src/", ByteArray(0), isDirectory = true),
                TarTestEntry("src/main.py", "print('hello')\n".toByteArray(), isDirectory = false)
            )
        )

        TarExtractor.extract(ByteArrayInputStream(archiveBytes), dest, isGzip = false)

        val hello = File(dest, "hello.txt")
        assertThat(hello.exists()).isTrue()
        assertThat(hello.readText()).isEqualTo("Hello PocketAgent!")

        val mainPy = File(dest, "src/main.py")
        assertThat(mainPy.exists()).isTrue()
        assertThat(mainPy.readText()).isEqualTo("print('hello')\n")
    }

    @Test
    fun testPathTraversalIsPrevented() {
        val dest = tempFolder.newFolder("safe_extracted")
        val archiveBytes = createSampleTar(
            listOf(
                TarTestEntry("../escape.txt", "MALICIOUS".toByteArray(), isDirectory = false),
                TarTestEntry("normal.txt", "SAFE".toByteArray(), isDirectory = false)
            )
        )

        TarExtractor.extract(ByteArrayInputStream(archiveBytes), dest, isGzip = false)

        val escape = File(dest.parentFile, "escape.txt")
        assertThat(escape.exists()).isFalse()

        val normal = File(dest, "normal.txt")
        assertThat(normal.exists()).isTrue()
        assertThat(normal.readText()).isEqualTo("SAFE")
    }

    private data class TarTestEntry(val name: String, val content: ByteArray, val isDirectory: Boolean)

    private fun createSampleTar(entries: List<TarTestEntry>): ByteArray {
        val out = ByteArrayOutputStream()
        for (entry in entries) {
            val header = ByteArray(512)
            // Name
            val nameBytes = entry.name.toByteArray(Charsets.UTF_8)
            System.arraycopy(nameBytes, 0, header, 0, minOf(nameBytes.size, 100))
            // Mode: 0000755 for dir, 0000644 for file
            val modeStr = if (entry.isDirectory) "0000755\u0000" else "0000644\u0000"
            System.arraycopy(modeStr.toByteArray(), 0, header, 100, modeStr.length)
            // Size: octal
            val sizeStr = "%011o ".format(entry.content.size)
            System.arraycopy(sizeStr.toByteArray(), 0, header, 124, 12)
            // Typeflag
            header[156] = if (entry.isDirectory) '5'.code.toByte() else '0'.code.toByte()
            // Magic: ustar\0
            val magic = "ustar\u0000".toByteArray()
            System.arraycopy(magic, 0, header, 257, magic.size)

            // Checksum (treated as if checksum field is 8 spaces)
            for (i in 148 until 156) header[i] = ' '.code.toByte()
            var chk = 0
            for (b in header) chk += (b.toInt() and 0xFF)
            val chkStr = "%06o\u0000 ".format(chk)
            System.arraycopy(chkStr.toByteArray(), 0, header, 148, 8)

            out.write(header)
            if (!entry.isDirectory && entry.content.isNotEmpty()) {
                out.write(entry.content)
                val pad = if (entry.content.size % 512 == 0) 0 else 512 - (entry.content.size % 512)
                if (pad > 0) out.write(ByteArray(pad))
            }
        }
        // End of archive marker (two 512-byte zero blocks)
        out.write(ByteArray(1024))
        return out.toByteArray()
    }
}
