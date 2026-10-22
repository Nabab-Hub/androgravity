package dev.pocketagent.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.pocketagent.PocketAgentApp
import dev.pocketagent.core.model.FileNode
import dev.pocketagent.core.model.Project
import dev.pocketagent.core.model.SearchMatch
import dev.pocketagent.core.storage.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStream

data class EditorUiState(
    val projects: List<Project> = emptyList(),
    val activeProject: Project? = null,
    val fileTree: FileNode? = null,
    val openTabs: List<String> = emptyList(),
    val activeTab: String? = null,
    val activeContent: String = "",
    val isDirty: Boolean = false,
    val isDiffMode: Boolean = false,
    val diffOriginalContent: String = "",
    val searchQuery: String = "",
    val searchResults: List<SearchMatch> = emptyList(),
    val isSearchOpen: Boolean = false,
    val expandedFolders: Set<String> = emptySet(),
    val isFileTreeOpen: Boolean = false,
    val messageBanner: String? = null
)

class EditorViewModel(
    private val workspaceManager: WorkspaceManager = PocketAgentApp.instance.workspaceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        loadProjects()
    }

    fun loadProjects() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = workspaceManager.listProjects()
            if (list.isEmpty()) {
                val defaultProject = workspaceManager.createProject("hello_world")
                _uiState.update {
                    it.copy(
                        projects = listOf(defaultProject),
                        activeProject = defaultProject
                    )
                }
                refreshTree(defaultProject.id)
                openFile("README.md")
            } else {
                val current = _uiState.value.activeProject ?: list.first()
                _uiState.update {
                    it.copy(
                        projects = list,
                        activeProject = current
                    )
                }
                refreshTree(current.id)
                if (_uiState.value.activeTab == null) {
                    openFile("README.md")
                }
            }
        }
    }

    fun selectProject(project: Project) {
        _uiState.update {
            it.copy(
                activeProject = project,
                openTabs = emptyList(),
                activeTab = null,
                activeContent = "",
                isDirty = false
            )
        }
        refreshTree(project.id)
        openFile("README.md")
    }

    fun createProject(name: String, parentDir: File? = null) {
        viewModelScope.launch {
            val project = workspaceManager.createProject(name, parentDir)
            loadProjects()
            selectProject(project)
        }
    }

    fun openExternalFolder(folderPath: String) {
        viewModelScope.launch {
            val file = File(folderPath)
            val project = workspaceManager.registerExternalProject(file)
            loadProjects()
            selectProject(project)
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            workspaceManager.deleteProject(projectId)
            _uiState.update { it.copy(activeProject = null) }
            loadProjects()
        }
    }

    fun refreshTree(projectId: String? = _uiState.value.activeProject?.id) {
        if (projectId == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tree = workspaceManager.getFileTree(projectId)
                _uiState.update { it.copy(fileTree = tree) }
            } catch (e: Exception) {
                // Ignored if project was deleted
            }
        }
    }

    fun toggleFolder(relativePath: String) {
        _uiState.update { state ->
            val set = state.expandedFolders.toMutableSet()
            if (set.contains(relativePath)) {
                set.remove(relativePath)
            } else {
                set.add(relativePath)
            }
            state.copy(expandedFolders = set)
        }
    }

    fun openFile(relativePath: String) {
        val projectId = _uiState.value.activeProject?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = workspaceManager.readFile(projectId, relativePath)
                val tabs = _uiState.value.openTabs.toMutableList()
                if (!tabs.contains(relativePath)) {
                    tabs.add(relativePath)
                }
                _uiState.update {
                    it.copy(
                        openTabs = tabs,
                        activeTab = relativePath,
                        activeContent = content,
                        diffOriginalContent = content,
                        isDirty = false,
                        isDiffMode = false
                    )
                }
            } catch (e: Exception) {
                // File might not exist yet
            }
        }
    }

    fun closeTab(relativePath: String) {
        val tabs = _uiState.value.openTabs.filter { it != relativePath }
        val newActive = if (_uiState.value.activeTab == relativePath) {
            tabs.lastOrNull()
        } else {
            _uiState.value.activeTab
        }

        _uiState.update {
            it.copy(
                openTabs = tabs,
                activeTab = newActive
            )
        }

        if (newActive != null) {
            openFile(newActive)
        } else {
            _uiState.update { it.copy(activeContent = "", isDirty = false) }
        }
    }

    fun onContentChanged(newContent: String) {
        _uiState.update {
            it.copy(
                activeContent = newContent,
                isDirty = newContent != it.diffOriginalContent
            )
        }
    }

    fun saveCurrentFile() {
        val projectId = _uiState.value.activeProject?.id ?: return
        val path = _uiState.value.activeTab ?: return
        val content = _uiState.value.activeContent

        try {
            workspaceManager.writeFile(projectId, path, content)
            _uiState.update {
                it.copy(
                    isDirty = false,
                    diffOriginalContent = content,
                    messageBanner = "Saved $path"
                )
            }
            refreshTree(projectId)
        } catch (e: Exception) {
            _uiState.update { it.copy(messageBanner = "Save failed: ${e.message}") }
        }
    }

    fun createFile(parentRelative: String, fileName: String) {
        val projectId = _uiState.value.activeProject?.id ?: return
        val targetPath = if (parentRelative.isBlank() || parentRelative == ".") fileName else "$parentRelative/$fileName"
        if (workspaceManager.createFile(projectId, targetPath)) {
            refreshTree(projectId)
            openFile(targetPath)
        }
    }

    fun createFolder(parentRelative: String, folderName: String) {
        val projectId = _uiState.value.activeProject?.id ?: return
        val targetPath = if (parentRelative.isBlank() || parentRelative == ".") folderName else "$parentRelative/$folderName"
        if (workspaceManager.createFolder(projectId, targetPath)) {
            refreshTree(projectId)
        }
    }

    fun renameItem(oldRelative: String, newName: String) {
        val projectId = _uiState.value.activeProject?.id ?: return
        val parent = oldRelative.substringBeforeLast('/', "")
        val newRelative = if (parent.isEmpty()) newName else "$parent/$newName"
        if (workspaceManager.rename(projectId, oldRelative, newRelative)) {
            refreshTree(projectId)
            if (_uiState.value.activeTab == oldRelative) {
                closeTab(oldRelative)
                openFile(newRelative)
            }
        }
    }

    fun deleteItem(relativePath: String) {
        val projectId = _uiState.value.activeProject?.id ?: return
        if (workspaceManager.delete(projectId, relativePath)) {
            closeTab(relativePath)
            refreshTree(projectId)
        }
    }

    fun performSearch(query: String) {
        val projectId = _uiState.value.activeProject?.id ?: return
        val results = workspaceManager.search(projectId, query)
        _uiState.update { it.copy(searchQuery = query, searchResults = results) }
    }

    fun toggleSearchDialog(isOpen: Boolean) {
        _uiState.update { it.copy(isSearchOpen = isOpen) }
    }

    fun toggleFileTree(isOpen: Boolean) {
        _uiState.update { it.copy(isFileTreeOpen = isOpen) }
    }

    fun toggleDiffMode() {
        _uiState.update { it.copy(isDiffMode = !it.isDiffMode) }
    }

    fun clearBanner() {
        _uiState.update { it.copy(messageBanner = null) }
    }

    fun exportZip(outputStream: OutputStream) {
        val projectId = _uiState.value.activeProject?.id ?: return
        workspaceManager.exportToZip(projectId, outputStream)
    }

    fun importZip(inputStream: InputStream) {
        val projectId = _uiState.value.activeProject?.id ?: return
        workspaceManager.importFromZip(projectId, inputStream)
        refreshTree(projectId)
    }
}
