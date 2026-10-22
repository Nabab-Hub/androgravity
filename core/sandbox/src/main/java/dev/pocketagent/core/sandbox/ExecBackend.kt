package dev.pocketagent.core.sandbox

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

interface ExecBackend {
    val name: String
    suspend fun start(
        cmd: List<String>,
        env: Map<String, String> = emptyMap(),
        cwd: String = ""
    ): PtySession

    suspend fun exec(
        cmd: List<String>,
        env: Map<String, String> = emptyMap(),
        cwd: String = ""
    ): ExecResult {
        val session = start(cmd, env, cwd)
        val outStream = ByteArrayOutputStream()
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                session.output.collect { bytes ->
                    outStream.write(bytes)
                }
            } catch (_: Throwable) {
            }
        }
        val code = session.exitCode.await()
        job.cancel()
        return ExecResult(
            exitCode = code,
            stdout = outStream.toString(Charsets.UTF_8.name())
        )
    }
}

class DirectPtySession(
    private val process: Process,
    override val pid: Int = extractProcessPid(process)
) : PtySession {

    private val _exitCode = CompletableDeferred<Int>()
    override val exitCode = _exitCode

    private val stdin: OutputStream = process.outputStream

    override val output: Flow<ByteArray> = flow {
        val stream: InputStream = process.inputStream
        val buffer = ByteArray(4096)
        try {
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                emit(buffer.copyOf(read))
            }
        } catch (_: Throwable) {
        } finally {
            try {
                val code = process.waitFor()
                _exitCode.complete(code)
            } catch (t: Throwable) {
                _exitCode.complete(-1)
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        stdin.write(data)
        stdin.flush()
    }

    override suspend fun resize(cols: Int, rows: Int) {
        // Direct process pipes don't have ioctl TIOCSWINSZ unless attached to a real pty
    }

    override suspend fun kill(signal: Int) = withContext(Dispatchers.IO) {
        process.destroyForcibly()
        _exitCode.complete(137) // 128 + 9 (SIGKILL)
        Unit
    }

    companion object {
        fun extractProcessPid(process: Process): Int {
            return try {
                val method = process.javaClass.getMethod("pid")
                (method.invoke(process) as Long).toInt()
            } catch (_: Throwable) {
                try {
                    val field = process.javaClass.getDeclaredField("pid")
                    field.isAccessible = true
                    field.getInt(process)
                } catch (_: Throwable) {
                    0
                }
            }
        }
    }
}

class DirectBackend : ExecBackend {
    override val name: String = "direct"

    override suspend fun start(
        cmd: List<String>,
        env: Map<String, String>,
        cwd: String
    ): PtySession = withContext(Dispatchers.IO) {
        val builder = ProcessBuilder(cmd)
        if (cwd.isNotBlank()) {
            val dir = File(cwd)
            if (dir.exists()) builder.directory(dir)
        }
        val pEnv = builder.environment()
        env.forEach { (k, v) -> pEnv[k] = v }
        builder.redirectErrorStream(true) // Merge stdout and stderr for terminal
        val process = builder.start()
        DirectPtySession(process)
    }
}
