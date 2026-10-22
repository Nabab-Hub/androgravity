package dev.pocketagent.ui.screens.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.pocketagent.PocketAgentApp
import dev.pocketagent.core.agent.artifacts.ArtifactComment
import dev.pocketagent.core.agent.artifacts.TaskItem
import dev.pocketagent.core.agent.checkpoint.ChangedFile
import dev.pocketagent.core.agent.checkpoint.CheckpointInfo
import dev.pocketagent.core.model.AgentSession
import dev.pocketagent.core.model.AutonomyMode
import dev.pocketagent.core.model.Project
import dev.pocketagent.core.model.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class ManagerUiState(
    val sessions: List<AgentSession> = emptyList(),
    val projects: List<Project> = emptyList(),
    val selectedProjectId: String? = null,
    val selectedSession: AgentSession? = null,
    val isLoading: Boolean = false,
    val showNewSessionDialog: Boolean = false
)

data class SessionDetailUiState(
    val session: AgentSession? = null,
    val activeTab: Int = 0, // 0: Chat, 1: Plan, 2: Tasks, 3: Walkthrough, 4: Diff, 5: Comments
    val planText: String? = null,
    val tasks: List<TaskItem> = emptyList(),
    val walkthroughText: String? = null,
    val changedFiles: List<ChangedFile> = emptyList(),
    val diffText: String = "",
    val checkpoints: List<CheckpointInfo> = emptyList(),
    val comments: List<ArtifactComment> = emptyList(),
    val isActionRunning: Boolean = false,
    val actionMessage: String? = null,
    val showAddCommentDialog: Boolean = false
)

class AgentManagerViewModel(
    private val app: PocketAgentApp = PocketAgentApp.instance
) : ViewModel() {

    private val sessionRepo = app.sessionRepository
    private val workspaceManager = app.workspaceManager
    private val artifactManager = app.artifactManager
    private val checkpointManager = app.checkpointManager
    private val worktreeManager = app.worktreeManager

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    private val _selectedProjectId = MutableStateFlow<String?>(null)
    private val _selectedSession = MutableStateFlow<AgentSession?>(null)
    private val _showNewDialog = MutableStateFlow(false)

    init {
        refreshProjects()
    }

    fun refreshProjects() {
        _projects.value = workspaceManager.listProjects()
    }

    val uiState: StateFlow<ManagerUiState> = combine(
        sessionRepo.getAllSessions(),
        _projects,
        _selectedProjectId,
        _selectedSession,
        _showNewDialog
    ) { sessions: List<AgentSession>, projects: List<Project>, selectedProj: String?, selectedSess: AgentSession?, showDialog: Boolean ->
        val filtered = if (selectedProj != null) {
            sessions.filter { it.projectId == selectedProj }
        } else {
            sessions
        }
        ManagerUiState(
            sessions = filtered,
            projects = projects,
            selectedProjectId = selectedProj,
            selectedSession = selectedSess,
            showNewSessionDialog = showDialog
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ManagerUiState())

    private val _detailState = MutableStateFlow(SessionDetailUiState())
    val detailState: StateFlow<SessionDetailUiState> = _detailState.asStateFlow()

    fun selectProject(projectId: String?) {
        _selectedProjectId.value = projectId
    }

    fun openNewSessionDialog() {
        _showNewDialog.value = true
    }

    fun closeNewSessionDialog() {
        _showNewDialog.value = false
    }

    fun createSession(title: String, projectId: String? = null, model: String = "gemini-1.5-flash", mode: AutonomyMode = AutonomyMode.AGENT_DECIDES) {
        viewModelScope.launch {
            val projId = projectId ?: uiState.value.projects.firstOrNull()?.id ?: "default"
            val newSession = AgentSession(
                id = UUID.randomUUID().toString(),
                projectId = projId,
                title = title.ifBlank { "Agent Session ${System.currentTimeMillis() % 10000}" },
                status = SessionStatus.PLANNING,
                mode = mode,
                model = model
            )
            sessionRepo.upsertSession(newSession)
            _showNewDialog.value = false
            selectSession(newSession)
        }
    }

    fun selectSession(session: AgentSession) {
        _selectedSession.value = session
        loadSessionDetails(session)
    }

    fun clearSelectedSession() {
        _selectedSession.value = null
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            sessionRepo.deleteSession(sessionId)
            if (_selectedSession.value?.id == sessionId) {
                _selectedSession.value = null
            }
        }
    }

    fun setDetailTab(tabIndex: Int) {
        _detailState.value = _detailState.value.copy(activeTab = tabIndex)
    }

    fun loadSessionDetails(session: AgentSession) {
        viewModelScope.launch {
            val workspaceRoot = workspaceManager.getProjectDir(session.projectId)
            val plan = artifactManager.getPlan(workspaceRoot, session.id)
            val tasks = artifactManager.parseTasks(workspaceRoot, session.id)
            val walkthrough = artifactManager.getWalkthrough(workspaceRoot, session.id)
            val changed = checkpointManager.getChangedFiles(workspaceRoot)
            val diff = checkpointManager.getDiff(workspaceRoot)
            val checkpoints = checkpointManager.getCheckpointHistory(workspaceRoot)
            val comments = artifactManager.getComments(workspaceRoot, session.id)

            _detailState.value = SessionDetailUiState(
                session = session,
                planText = plan,
                tasks = tasks,
                walkthroughText = walkthrough,
                changedFiles = changed,
                diffText = diff,
                checkpoints = checkpoints,
                comments = comments
            )
        }
    }

    fun toggleTask(taskIndex: Int) {
        val session = _selectedSession.value ?: return
        viewModelScope.launch {
            val workspaceRoot = workspaceManager.getProjectDir(session.projectId)
            val ok = artifactManager.toggleTask(workspaceRoot, session.id, taskIndex)
            if (ok) {
                val updatedTasks = artifactManager.parseTasks(workspaceRoot, session.id)
                _detailState.value = _detailState.value.copy(tasks = updatedTasks)
            }
        }
    }

    fun undoLastStep() {
        val session = _selectedSession.value ?: return
        viewModelScope.launch {
            _detailState.value = _detailState.value.copy(isActionRunning = true)
            val workspaceRoot = workspaceManager.getProjectDir(session.projectId)
            val success = checkpointManager.undoLastStep(workspaceRoot)
            val diff = checkpointManager.getDiff(workspaceRoot)
            val changed = checkpointManager.getChangedFiles(workspaceRoot)
            val checkpoints = checkpointManager.getCheckpointHistory(workspaceRoot)

            _detailState.value = _detailState.value.copy(
                isActionRunning = false,
                actionMessage = if (success) "Undid last checkpoint step successfully." else "Failed to undo step.",
                diffText = diff,
                changedFiles = changed,
                checkpoints = checkpoints
            )
        }
    }

    fun revertSession(startCommit: String) {
        val session = _selectedSession.value ?: return
        viewModelScope.launch {
            _detailState.value = _detailState.value.copy(isActionRunning = true)
            val workspaceRoot = workspaceManager.getProjectDir(session.projectId)
            val success = checkpointManager.revertSession(workspaceRoot, startCommit)
            val diff = checkpointManager.getDiff(workspaceRoot)
            val changed = checkpointManager.getChangedFiles(workspaceRoot)
            val checkpoints = checkpointManager.getCheckpointHistory(workspaceRoot)

            _detailState.value = _detailState.value.copy(
                isActionRunning = false,
                actionMessage = if (success) "Reverted to initial commit." else "Failed to revert session.",
                diffText = diff,
                changedFiles = changed,
                checkpoints = checkpoints
            )
        }
    }

    fun openAddCommentDialog() {
        _detailState.value = _detailState.value.copy(showAddCommentDialog = true)
    }

    fun closeAddCommentDialog() {
        _detailState.value = _detailState.value.copy(showAddCommentDialog = false)
    }

    fun addComment(artifactName: String, lineNumber: Int?, section: String?, commentText: String) {
        val session = _selectedSession.value ?: return
        viewModelScope.launch {
            val workspaceRoot = workspaceManager.getProjectDir(session.projectId)
            val comment = ArtifactComment(
                artifactName = artifactName,
                lineNumber = lineNumber,
                sectionHeader = section,
                commentText = commentText
            )
            artifactManager.addComment(workspaceRoot, session.id, comment)
            val updatedComments = artifactManager.getComments(workspaceRoot, session.id)
            _detailState.value = _detailState.value.copy(
                comments = updatedComments,
                showAddCommentDialog = false
            )
        }
    }

    fun deleteComment(commentId: String) {
        val session = _selectedSession.value ?: return
        viewModelScope.launch {
            val workspaceRoot = workspaceManager.getProjectDir(session.projectId)
            artifactManager.deleteComment(workspaceRoot, session.id, commentId)
            val updated = artifactManager.getComments(workspaceRoot, session.id)
            _detailState.value = _detailState.value.copy(comments = updated)
        }
    }
}
