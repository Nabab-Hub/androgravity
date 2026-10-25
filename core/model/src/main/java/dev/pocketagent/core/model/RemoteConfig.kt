package dev.pocketagent.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ExecBackendType {
    PROOT,
    DIRECT,
    REMOTE
}

@Serializable
data class RemoteConfig(
    val host: String = "127.0.0.1",
    val port: Int = 8787,
    val token: String = "",
    val useTls: Boolean = false,
    val isEnabled: Boolean = false,
    val remoteWorkspacePath: String = ""
) {
    val baseUrl: String
        get() {
            val scheme = if (useTls) "https" else "http"
            return "$scheme://$host:$port"
        }
}

@Serializable
data class RemoteStatus(
    val isOnline: Boolean,
    val os: String = "Unknown",
    val version: String = "",
    val uptime: Long = 0L,
    val latencyMs: Long = -1L,
    val errorMessage: String? = null
)
