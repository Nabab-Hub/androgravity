package dev.pocketagent.core.sandbox

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow

interface PtySession {
    val pid: Int
    val output: Flow<ByteArray>
    val exitCode: Deferred<Int>

    suspend fun write(data: ByteArray)
    suspend fun write(text: String) = write(text.toByteArray(Charsets.UTF_8))
    suspend fun resize(cols: Int, rows: Int)
    suspend fun kill(signal: Int = 9)
}

data class ExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String = ""
)
