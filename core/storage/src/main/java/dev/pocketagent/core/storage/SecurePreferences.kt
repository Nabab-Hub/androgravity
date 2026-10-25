package dev.pocketagent.core.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

interface SecureStorage {
    fun getString(key: String, defaultValue: String? = null): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
    fun clear()
}

class SecurePreferences(context: Context) : SecureStorage {
    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            Log.e("SecurePreferences", "Failed to initialize EncryptedSharedPreferences, falling back to private prefs", e)
            context.getSharedPreferences("${PREFS_FILE_NAME}_fallback", Context.MODE_PRIVATE)
        }
    }

    override fun getString(key: String, defaultValue: String?): String? {
        return prefs.getString(key, defaultValue)
    }

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return prefs.getInt(key, defaultValue)
    }

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun getRemoteConfig(): dev.pocketagent.core.model.RemoteConfig {
        return dev.pocketagent.core.model.RemoteConfig(
            host = getString(KEY_REMOTE_HOST, "127.0.0.1") ?: "127.0.0.1",
            port = getInt(KEY_REMOTE_PORT, 8787),
            token = getString(KEY_REMOTE_TOKEN, "") ?: "",
            useTls = getBoolean(KEY_REMOTE_TLS, false),
            isEnabled = getBoolean(KEY_REMOTE_ENABLED, false),
            remoteWorkspacePath = getString(KEY_REMOTE_WORKSPACE_PATH, "") ?: ""
        )
    }

    fun saveRemoteConfig(config: dev.pocketagent.core.model.RemoteConfig) {
        putString(KEY_REMOTE_HOST, config.host)
        putInt(KEY_REMOTE_PORT, config.port)
        putString(KEY_REMOTE_TOKEN, config.token)
        putBoolean(KEY_REMOTE_TLS, config.useTls)
        putBoolean(KEY_REMOTE_ENABLED, config.isEnabled)
        putString(KEY_REMOTE_WORKSPACE_PATH, config.remoteWorkspacePath)
    }

    fun getExecBackendType(): dev.pocketagent.core.model.ExecBackendType {
        val name = getString(KEY_EXEC_BACKEND_TYPE, dev.pocketagent.core.model.ExecBackendType.PROOT.name)
        return try {
            dev.pocketagent.core.model.ExecBackendType.valueOf(name ?: dev.pocketagent.core.model.ExecBackendType.PROOT.name)
        } catch (_: Exception) {
            dev.pocketagent.core.model.ExecBackendType.PROOT
        }
    }

    fun setExecBackendType(type: dev.pocketagent.core.model.ExecBackendType) {
        putString(KEY_EXEC_BACKEND_TYPE, type.name)
    }

    fun isCrashReportingEnabled(): Boolean {
        return getBoolean(KEY_CRASH_REPORTING_ENABLED, false)
    }

    fun setCrashReportingEnabled(enabled: Boolean) {
        putBoolean(KEY_CRASH_REPORTING_ENABLED, enabled)
    }

    companion object {
        private const val PREFS_FILE_NAME = "pocketagent_secure_prefs"
        const val KEY_GEMINI_API_KEY = "gemini_api_key"
        const val KEY_ANTHROPIC_API_KEY = "anthropic_api_key"
        const val KEY_OPENAI_API_KEY = "openai_api_key"
        const val KEY_GROQ_API_KEY = "groq_api_key"
        const val KEY_SELECTED_PROVIDER = "selected_provider"
        const val KEY_SELECTED_MODEL = "selected_model"
        const val KEY_AUTONOMY_MODE = "autonomy_mode"
        const val KEY_EXEC_BACKEND_TYPE = "exec_backend_type"
        const val KEY_REMOTE_HOST = "remote_host"
        const val KEY_REMOTE_PORT = "remote_port"
        const val KEY_REMOTE_TOKEN = "remote_token"
        const val KEY_REMOTE_TLS = "remote_tls"
        const val KEY_REMOTE_ENABLED = "remote_enabled"
        const val KEY_REMOTE_WORKSPACE_PATH = "remote_workspace_path"
        const val KEY_CRASH_REPORTING_ENABLED = "crash_reporting_enabled"
    }
}
