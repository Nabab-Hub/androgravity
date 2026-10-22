package dev.pocketagent

import android.app.Application
import dev.pocketagent.core.providers.gemini.GeminiProvider
import dev.pocketagent.core.storage.SecurePreferences
import dev.pocketagent.core.storage.database.AppDatabase
import kotlinx.coroutines.launch

class PocketAgentApp : Application() {

    lateinit var securePreferences: SecurePreferences
        private set

    lateinit var database: AppDatabase
        private set

    lateinit var workspaceManager: dev.pocketagent.core.storage.WorkspaceManager
        private set

    lateinit var geminiProvider: GeminiProvider
        private set

    lateinit var linuxManager: dev.pocketagent.core.sandbox.LinuxEnvironmentManager
        private set

    lateinit var directBackend: dev.pocketagent.core.sandbox.DirectBackend
        private set

    lateinit var prootBackend: dev.pocketagent.core.sandbox.ProotBackend
        private set

    lateinit var policyEngine: dev.pocketagent.core.agent.policy.PolicyEngine
        private set

    lateinit var toolRouter: dev.pocketagent.core.agent.tools.ToolRouter
        private set

    lateinit var agentLoop: dev.pocketagent.core.agent.loop.AgentLoop
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                android.util.Log.e("MazaAgent", "FATAL UNCAUGHT EXCEPTION", throwable)
                val stackTrace = android.util.Log.getStackTraceString(throwable)
                val intent = android.content.Intent(this, CrashActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra(CrashActivity.EXTRA_CRASH_INFO, stackTrace)
                }
                startActivity(intent)
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            } catch (t: Throwable) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        try {
            securePreferences = SecurePreferences(this)
        } catch (e: Throwable) {
            android.util.Log.e("MazaAgent", "Failed to init SecurePreferences", e)
        }

        try {
            crashReportManager = dev.pocketagent.core.storage.CrashReportManager(
                context = this,
                securePreferences = securePreferences
            )
        } catch (e: Throwable) {
            android.util.Log.e("MazaAgent", "Failed to init CrashReportManager", e)
        }

        try {
            database = AppDatabase.getInstance(this)
        } catch (e: Throwable) {
            android.util.Log.e("MazaAgent", "Failed to init AppDatabase, falling back to memory db", e)
            database = androidx.room.Room.inMemoryDatabaseBuilder(this, AppDatabase::class.java).build()
        }

        workspaceManager = dev.pocketagent.core.storage.WorkspaceManager(this)

        geminiProvider = GeminiProvider(
            apiKeyProvider = {
                try {
                    securePreferences.getString(SecurePreferences.KEY_GEMINI_API_KEY, "") ?: ""
                } catch (_: Throwable) {
                    ""
                }
            }
        )

        linuxManager = dev.pocketagent.core.sandbox.LinuxEnvironmentManager(this)
        directBackend = dev.pocketagent.core.sandbox.DirectBackend()

        prootBackend = try {
            dev.pocketagent.core.sandbox.ProotBackend(
                context = this,
                rootfsDir = linuxManager.activeRootfsDir,
                workspaceDir = workspaceManager.workspacesRoot,
                directBackend = directBackend
            )
        } catch (e: Throwable) {
            android.util.Log.e("MazaAgent", "Failed to init ProotBackend", e)
            dev.pocketagent.core.sandbox.ProotBackend(this, cacheDir, cacheDir, directBackend)
        }

        browserController = dev.pocketagent.ui.browser.AndroidBrowserController(this)
        policyEngine = dev.pocketagent.core.agent.policy.PolicyEngine()
        toolRouter = dev.pocketagent.core.agent.tools.ToolRouter()

        try {
            mcpServerRepository = dev.pocketagent.core.storage.McpServerRepository(filesDir)
            mcpManager = dev.pocketagent.core.agent.mcp.McpManager(
                repository = mcpServerRepository,
                toolRouter = toolRouter,
                policyEngine = policyEngine,
                execBackend = prootBackend
            )
        } catch (e: Throwable) {
            android.util.Log.e("MazaAgent", "Failed to init McpManager", e)
        }

        agentLoop = dev.pocketagent.core.agent.loop.AgentLoop(
            modelProvider = geminiProvider,
            toolRouter = toolRouter,
            policyEngine = policyEngine,
            browserController = browserController
        )

        sessionRepository = dev.pocketagent.core.storage.SessionRepository(database)
        artifactManager = dev.pocketagent.core.agent.artifacts.ArtifactManager()
        checkpointManager = dev.pocketagent.core.agent.checkpoint.CheckpointManager(directBackend)
        worktreeManager = dev.pocketagent.core.agent.worktree.WorktreeManager(directBackend)
        sessionRecoveryManager = dev.pocketagent.core.storage.SessionRecoveryManager(sessionRepository)

        remoteExecBackend = dev.pocketagent.core.sandbox.RemoteExecBackend(
            configProvider = {
                try {
                    securePreferences.getRemoteConfig()
                } catch (_: Throwable) {
                    dev.pocketagent.core.model.RemoteConfig()
                }
            }
        )

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                sessionRecoveryManager.recoverInterruptedSessions()
            } catch (_: Throwable) {}
        }
    }

    fun getActiveExecBackend(): dev.pocketagent.core.sandbox.ExecBackend {
        return when (securePreferences.getExecBackendType()) {
            dev.pocketagent.core.model.ExecBackendType.REMOTE -> remoteExecBackend
            dev.pocketagent.core.model.ExecBackendType.DIRECT -> directBackend
            dev.pocketagent.core.model.ExecBackendType.PROOT -> {
                if (prootBackend.isProotAvailable()) prootBackend else directBackend
            }
        }
    }

    lateinit var remoteExecBackend: dev.pocketagent.core.sandbox.RemoteExecBackend
        private set

    lateinit var crashReportManager: dev.pocketagent.core.storage.CrashReportManager
        private set

    lateinit var sessionRecoveryManager: dev.pocketagent.core.storage.SessionRecoveryManager
        private set

    lateinit var browserController: dev.pocketagent.ui.browser.AndroidBrowserController
        private set

    lateinit var mcpServerRepository: dev.pocketagent.core.storage.McpServerRepository
        private set

    lateinit var mcpManager: dev.pocketagent.core.agent.mcp.McpManager
        private set

    lateinit var sessionRepository: dev.pocketagent.core.storage.SessionRepository
        private set

    lateinit var artifactManager: dev.pocketagent.core.agent.artifacts.ArtifactManager
        private set

    lateinit var checkpointManager: dev.pocketagent.core.agent.checkpoint.CheckpointManager
        private set

    lateinit var worktreeManager: dev.pocketagent.core.agent.worktree.WorktreeManager
        private set

    companion object {
        lateinit var instance: PocketAgentApp
            private set
    }
}
