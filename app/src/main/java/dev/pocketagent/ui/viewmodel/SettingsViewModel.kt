package dev.pocketagent.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.pocketagent.PocketAgentApp
import dev.pocketagent.core.model.AutonomyMode
import dev.pocketagent.core.model.ExecBackendType
import dev.pocketagent.core.model.RemoteConfig
import dev.pocketagent.core.model.RemoteStatus
import dev.pocketagent.core.storage.CrashReport
import dev.pocketagent.core.storage.CrashReportManager
import dev.pocketagent.core.storage.SecurePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val geminiApiKey: String = "",
    val anthropicApiKey: String = "",
    val openaiApiKey: String = "",
    val selectedModel: String = "gemini-3.5-flash",
    val autonomyMode: AutonomyMode = AutonomyMode.AGENT_DECIDES,
    val execBackendType: ExecBackendType = ExecBackendType.PROOT,
    val remoteConfig: RemoteConfig = RemoteConfig(),
    val remoteStatus: RemoteStatus? = null,
    val isTestingRemote: Boolean = false,
    val isCrashReportingEnabled: Boolean = false,
    val crashReports: List<CrashReport> = emptyList(),
    val isSavedSuccess: Boolean = false
)

class SettingsViewModel(
    private val app: PocketAgentApp = PocketAgentApp.instance,
    private val securePreferences: SecurePreferences = app.securePreferences,
    private val crashReportManager: CrashReportManager = app.crashReportManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        val geminiKey = securePreferences.getString(SecurePreferences.KEY_GEMINI_API_KEY, "") ?: ""
        val anthropicKey = securePreferences.getString(SecurePreferences.KEY_ANTHROPIC_API_KEY, "") ?: ""
        val openaiKey = securePreferences.getString(SecurePreferences.KEY_OPENAI_API_KEY, "") ?: ""
        val savedModel = securePreferences.getString(SecurePreferences.KEY_SELECTED_MODEL, "gemini-3.5-flash") ?: "gemini-3.5-flash"
        val model = when (savedModel) {
            "gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-3.7-flash", "gpt-4o-mini" -> savedModel
            else -> "gemini-3.5-flash".also {
                securePreferences.putString(SecurePreferences.KEY_SELECTED_MODEL, it)
            }
        }
        val modeStr = securePreferences.getString(SecurePreferences.KEY_AUTONOMY_MODE, AutonomyMode.AGENT_DECIDES.name)
        val mode = try {
            AutonomyMode.valueOf(modeStr ?: AutonomyMode.AGENT_DECIDES.name)
        } catch (_: Exception) {
            AutonomyMode.AGENT_DECIDES
        }

        val backendType = securePreferences.getExecBackendType()
        val remoteCfg = securePreferences.getRemoteConfig()
        val crashEnabled = securePreferences.isCrashReportingEnabled()
        val reports = if (crashEnabled) crashReportManager.getReports() else emptyList()

        _uiState.update {
            it.copy(
                geminiApiKey = geminiKey,
                anthropicApiKey = anthropicKey,
                openaiApiKey = openaiKey,
                selectedModel = model,
                autonomyMode = mode,
                execBackendType = backendType,
                remoteConfig = remoteCfg,
                isCrashReportingEnabled = crashEnabled,
                crashReports = reports,
                isSavedSuccess = false
            )
        }
    }

    fun updateGeminiApiKey(key: String) {
        _uiState.update { it.copy(geminiApiKey = key, isSavedSuccess = false) }
    }

    fun updateOpenAiApiKey(key: String) {
        _uiState.update { it.copy(openaiApiKey = key, isSavedSuccess = false) }
    }

    fun updateSelectedModel(model: String) {
        _uiState.update { it.copy(selectedModel = model, isSavedSuccess = false) }
    }

    fun updateAutonomyMode(mode: AutonomyMode) {
        _uiState.update { it.copy(autonomyMode = mode, isSavedSuccess = false) }
    }

    fun updateExecBackendType(type: ExecBackendType) {
        securePreferences.setExecBackendType(type)
        _uiState.update { it.copy(execBackendType = type, isSavedSuccess = false) }
    }

    fun updateRemoteConfig(config: RemoteConfig) {
        securePreferences.saveRemoteConfig(config)
        _uiState.update { it.copy(remoteConfig = config, isSavedSuccess = false) }
    }

    fun setCrashReportingEnabled(enabled: Boolean) {
        securePreferences.setCrashReportingEnabled(enabled)
        val reports = if (enabled) crashReportManager.getReports() else emptyList()
        _uiState.update { it.copy(isCrashReportingEnabled = enabled, crashReports = reports) }
    }

    fun clearCrashReports() {
        crashReportManager.clearReports()
        _uiState.update { it.copy(crashReports = emptyList()) }
    }

    fun getExportedCrashLogs(): String {
        return crashReportManager.exportReportsAsText()
    }

    fun testRemoteConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingRemote = true, remoteStatus = null) }
            val status = app.remoteExecBackend.ping()
            if (status.isOnline) {
                securePreferences.setExecBackendType(ExecBackendType.REMOTE)
                _uiState.update { it.copy(execBackendType = ExecBackendType.REMOTE) }
            }
            _uiState.update { it.copy(isTestingRemote = false, remoteStatus = status) }
        }
    }

    fun saveSettings() {
        val current = _uiState.value
        securePreferences.putString(SecurePreferences.KEY_GEMINI_API_KEY, current.geminiApiKey.trim())
        securePreferences.putString(SecurePreferences.KEY_ANTHROPIC_API_KEY, current.anthropicApiKey.trim())
        securePreferences.putString(SecurePreferences.KEY_OPENAI_API_KEY, current.openaiApiKey.trim())
        securePreferences.putString(SecurePreferences.KEY_SELECTED_MODEL, current.selectedModel)
        securePreferences.putString(SecurePreferences.KEY_AUTONOMY_MODE, current.autonomyMode.name)
        securePreferences.setExecBackendType(current.execBackendType)
        securePreferences.saveRemoteConfig(current.remoteConfig)

        _uiState.update { it.copy(isSavedSuccess = true) }
    }
}
