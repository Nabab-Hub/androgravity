package dev.pocketagent.core.sandbox

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ProotBackend(
    private val context: Context,
    private val rootfsDir: File,
    private val workspaceDir: File,
    private val directBackend: DirectBackend = DirectBackend()
) : ExecBackend {

    override val name: String = "proot"

    val nativeLibraryDir: File by lazy {
        File(context.applicationInfo.nativeLibraryDir)
    }

    val prootBinary: File by lazy {
        File(nativeLibraryDir, "libproot.so")
    }

    val prootLoader: File by lazy {
        File(nativeLibraryDir, "libproot-loader.so")
    }

    val tmpDir: File by lazy {
        File(context.cacheDir, "proot-tmp").apply {
            if (!exists()) mkdirs()
        }
    }

    fun isProotAvailable(): Boolean = prootBinary.exists() && prootBinary.canExecute()

    fun buildProotCommand(
        guestCommand: List<String>,
        customWorkspace: File = workspaceDir
    ): List<String> {
        val cmd = mutableListOf<String>()
        cmd.add(prootBinary.absolutePath)
        cmd.add("--rootfs=${rootfsDir.absolutePath}")
        cmd.add("--kill-on-exit")
        cmd.add("--link2symlink")
        cmd.add("-b")
        cmd.add("/dev")
        cmd.add("-b")
        cmd.add("/proc")
        cmd.add("-b")
        cmd.add("/sys")
        cmd.add("-b")
        cmd.add("${customWorkspace.absolutePath}:/workspace")
        cmd.add("-b")
        cmd.add("${tmpDir.absolutePath}:/tmp")
        cmd.add("-w")
        cmd.add("/workspace")

        if (guestCommand.isNotEmpty()) {
            cmd.addAll(guestCommand)
        } else {
            cmd.add("/bin/sh")
            cmd.add("-l")
        }
        return cmd
    }

    fun buildProotEnvironment(extraEnv: Map<String, String> = emptyMap()): Map<String, String> {
        val env = mutableMapOf<String, String>()
        if (prootLoader.exists()) {
            env["PROOT_LOADER"] = prootLoader.absolutePath
        }
        env["PROOT_TMP_DIR"] = tmpDir.absolutePath
        env["TERM"] = "xterm-256color"
        env["HOME"] = "/root"
        env["PATH"] = "/bin:/sbin:/usr/bin:/usr/sbin:/usr/local/bin"
        env["LANG"] = "en_US.UTF-8"
        env.putAll(extraEnv)
        return env
    }

    override suspend fun start(
        cmd: List<String>,
        env: Map<String, String>,
        cwd: String
    ): PtySession = withContext(Dispatchers.IO) {
        if (!isProotAvailable()) {
            // When proot is not packaged on the current host (e.g. host testing),
            // fallback to direct backend so tests and development flow uninterrupted
            return@withContext directBackend.start(cmd, env, cwd)
        }

        val targetWorkspace = if (cwd.isNotBlank()) File(cwd) else workspaceDir
        val fullCmd = buildProotCommand(cmd, targetWorkspace)
        val fullEnv = buildProotEnvironment(env)

        directBackend.start(fullCmd, fullEnv, targetWorkspace.absolutePath)
    }
}
