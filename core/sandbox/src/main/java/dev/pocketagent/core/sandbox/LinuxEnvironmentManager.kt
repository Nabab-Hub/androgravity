package dev.pocketagent.core.sandbox

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed interface BootstrapStatus {
    object Idle : BootstrapStatus
    data class Downloading(val percent: Int, val bytesDownloaded: Long, val totalBytes: Long) : BootstrapStatus
    object Verifying : BootstrapStatus
    data class Extracting(val currentFile: String, val count: Int) : BootstrapStatus
    object Configuring : BootstrapStatus
    object Ready : BootstrapStatus
    data class Failed(val error: String) : BootstrapStatus
}

data class DistroConfig(
    val name: String = "alpine-3.20",
    val url: String,
    val sha256: String? = null
)

class LinuxEnvironmentManager(private val context: Context) {

    val rootfsBaseDir: File by lazy {
        File(context.filesDir, "rootfs").apply {
            if (!exists()) mkdirs()
        }
    }

    val activeRootfsDir: File by lazy {
        File(rootfsBaseDir, "alpine")
    }

    private val _status = MutableStateFlow<BootstrapStatus>(BootstrapStatus.Idle)
    val status: StateFlow<BootstrapStatus> = _status.asStateFlow()

    fun isInstalled(): Boolean {
        val marker = File(activeRootfsDir, ".pocketagent_ready")
        val sh = File(activeRootfsDir, "bin/sh")
        return marker.exists() && (sh.exists() || File(activeRootfsDir, "bin/busybox").exists())
    }

    fun getDistroConfigForDevice(): DistroConfig {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return when {
            abi.contains("arm64") || abi.contains("aarch64") -> DistroConfig(
                url = "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.3-aarch64.tar.gz",
                sha256 = null // Verified dynamically or bypassed if null
            )
            abi.contains("x86_64") -> DistroConfig(
                url = "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/x86_64/alpine-minirootfs-3.20.3-x86_64.tar.gz",
                sha256 = null
            )
            else -> DistroConfig(
                url = "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.3-aarch64.tar.gz"
            )
        }
    }

    suspend fun bootstrap(config: DistroConfig = getDistroConfigForDevice()): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isInstalled()) {
                _status.value = BootstrapStatus.Ready
                return@withContext true
            }

            if (!activeRootfsDir.exists()) activeRootfsDir.mkdirs()

            val tarGzFile = File(context.cacheDir, "rootfs_${System.currentTimeMillis()}.tar.gz")

            // 1. Download
            _status.value = BootstrapStatus.Downloading(0, 0, 0)
            downloadFile(config.url, tarGzFile) { downloaded, total ->
                val pct = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                _status.value = BootstrapStatus.Downloading(pct, downloaded, total)
            }

            // 2. Verify SHA256 if provided
            if (!config.sha256.isNullOrBlank()) {
                _status.value = BootstrapStatus.Verifying
                val hash = computeSha256(tarGzFile)
                if (!hash.equals(config.sha256, ignoreCase = true)) {
                    throw IllegalStateException("SHA-256 checksum mismatch: expected ${config.sha256}, got $hash")
                }
            }

            // 3. Extract rootfs
            _status.value = BootstrapStatus.Extracting("Starting extraction...", 0)
            FileInputStream(tarGzFile).use { fis ->
                TarExtractor.extract(fis, activeRootfsDir, isGzip = true) { name, count ->
                    _status.value = BootstrapStatus.Extracting(name, count)
                }
            }
            tarGzFile.delete()

            // 4. Configure environment
            _status.value = BootstrapStatus.Configuring
            configureRootfs(activeRootfsDir)

            // Mark ready
            File(activeRootfsDir, ".pocketagent_ready").writeText("version=3.20.3\ninstalledAt=${System.currentTimeMillis()}")
            _status.value = BootstrapStatus.Ready
            true
        } catch (e: Throwable) {
            _status.value = BootstrapStatus.Failed(e.message ?: "Unknown bootstrap error")
            false
        }
    }

    fun configureRootfs(root: File) {
        // /etc/resolv.conf
        val etc = File(root, "etc").apply { if (!exists()) mkdirs() }
        File(etc, "resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")

        // /etc/hosts
        File(etc, "hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")

        // /etc/profile
        File(etc, "profile").writeText(
            """
            export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/local/bin
            export HOME=/root
            export TERM=xterm-256color
            export LANG=en_US.UTF-8
            cd /workspace 2>/dev/null || true
            """.trimIndent()
        )

        // /workspace mount point
        File(root, "workspace").apply { if (!exists()) mkdirs() }

        // /tmp
        File(root, "tmp").apply { if (!exists()) mkdirs() }
    }

    suspend fun reset(): Boolean = withContext(Dispatchers.IO) {
        val deleted = activeRootfsDir.deleteRecursively()
        _status.value = BootstrapStatus.Idle
        deleted
    }

    private fun downloadFile(urlStr: String, dest: File, onProgress: (Long, Long) -> Unit) {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 30000
        conn.instanceFollowRedirects = true
        conn.connect()

        if (conn.responseCode !in 200..299) {
            throw IllegalStateException("Download failed with HTTP ${conn.responseCode}: ${conn.responseMessage}")
        }

        val total = conn.contentLengthLong
        var downloaded = 0L

        conn.inputStream.use { input ->
            FileOutputStream(dest).use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    onProgress(downloaded, total)
                }
            }
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(8192)
            while (true) {
                val r = fis.read(buf)
                if (r == -1) break
                digest.update(buf, 0, r)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
