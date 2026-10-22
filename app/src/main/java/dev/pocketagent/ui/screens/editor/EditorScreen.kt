package dev.pocketagent.ui.screens.editor

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.webkit.WebViewAssetLoader
import dev.pocketagent.core.model.FileNode
import dev.pocketagent.ui.editor.MonacoBridge
import dev.pocketagent.ui.viewmodel.EditorViewModel
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isEditorReady by remember { mutableStateOf(false) }

    // Dialog states
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<String?>(null) }
    var inputName by remember { mutableStateOf("") }

    // SAF Zip Import/Export launchers
    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                viewModel.exportZip(os)
            }
        }
    }

    val importZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use { `is` ->
                viewModel.importZip(`is`)
            }
        }
    }

    // Sync content to webview
    LaunchedEffect(uiState.activeContent, uiState.activeTab, isEditorReady) {
        if (isEditorReady && webViewRef != null) {
            val extension = uiState.activeTab?.substringAfterLast('.', "txt") ?: "txt"
            val lang = when (extension) {
                "kt" -> "kotlin"
                "java" -> "java"
                "js" -> "javascript"
                "ts" -> "typescript"
                "py" -> "python"
                "html" -> "html"
                "css" -> "css"
                "json" -> "json"
                "md" -> "markdown"
                "sh" -> "shell"
                else -> "text"
            }
            val safeContent = JSONObject.quote(uiState.activeContent)
            webViewRef?.evaluateJavascript("window.setContent($safeContent, '$lang');", null)
            webViewRef?.evaluateJavascript("window.setTheme('${if (isDark) "dark" else "light"}');", null)
        }
    }

    // Diff mode toggle in webview
    LaunchedEffect(uiState.isDiffMode, isEditorReady) {
        if (isEditorReady && webViewRef != null) {
            val orig = JSONObject.quote(uiState.diffOriginalContent)
            val mod = JSONObject.quote(uiState.activeContent)
            webViewRef?.evaluateJavascript(
                "window.setDiffMode(${uiState.isDiffMode}, $orig, $mod, 'text');",
                null
            )
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                FileTreeDrawerContent(
                    uiState = uiState,
                    onSelectProject = {
                        viewModel.selectProject(it)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onNewProject = { showNewProjectDialog = true },
                    onOpenFile = { path ->
                        viewModel.openFile(path)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onToggleFolder = { viewModel.toggleFolder(it) },
                    onNewFile = {
                        inputName = ""
                        showNewFileDialog = true
                    },
                    onNewFolder = {
                        inputName = ""
                        showNewFolderDialog = true
                    },
                    onRename = { path ->
                        inputName = path.substringAfterLast('/')
                        showRenameDialog = path
                    },
                    onDelete = { viewModel.deleteItem(it) }
                )
            }
        }
    ) {
        Column(modifier = modifier.fillMaxSize()) {
            // Editor Toolbar
            EditorTopBar(
                activeProjectName = uiState.activeProject?.name ?: "No Project",
                activeTab = uiState.activeTab,
                isDirty = uiState.isDirty,
                isDiffMode = uiState.isDiffMode,
                onToggleDrawer = {
                    coroutineScope.launch {
                        if (drawerState.isOpen) drawerState.close() else drawerState.open()
                    }
                },
                onSave = { viewModel.saveCurrentFile() },
                onToggleDiff = { viewModel.toggleDiffMode() },
                onOpenSearch = { viewModel.toggleSearchDialog(true) },
                onExportZip = {
                    val name = "${uiState.activeProject?.name ?: "workspace"}.zip"
                    exportZipLauncher.launch(name)
                },
                onImportZip = {
                    importZipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed"))
                }
            )

            // Open Files Tab Row
            if (uiState.openTabs.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = uiState.openTabs.indexOf(uiState.activeTab).coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = 8.dp
                ) {
                    uiState.openTabs.forEach { tabPath ->
                        val isSelected = tabPath == uiState.activeTab
                        Tab(
                            selected = isSelected,
                            onClick = { viewModel.openFile(tabPath) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = tabPath.substringAfterLast('/'),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isSelected && uiState.isDirty) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close tab",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { viewModel.closeTab(tabPath) }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // Banner Notification
            uiState.messageBanner?.let { banner ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = banner, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.clearBanner() }, modifier = Modifier.size(18.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                }
            }

            // WebView Editor Host
            Box(modifier = Modifier.weight(1f)) {
                MonacoWebViewContainer(
                    isDark = isDark,
                    onEditorReady = { isEditorReady = true },
                    onContentChanged = { viewModel.onContentChanged(it) },
                    onSave = { viewModel.saveCurrentFile() },
                    onWebViewCreated = { webViewRef = it }
                )
            }
        }
    }

    // Search Dialog
    if (uiState.isSearchOpen) {
        SearchDialog(
            query = uiState.searchQuery,
            results = uiState.searchResults,
            onQueryChange = { viewModel.performSearch(it) },
            onSelectMatch = { match ->
                viewModel.openFile(match.relativePath)
                viewModel.toggleSearchDialog(false)
            },
            onDismiss = { viewModel.toggleSearchDialog(false) }
        )
    }

    // New File Dialog
    if (showNewFileDialog) {
        SimpleInputDialog(
            title = "New File",
            label = "File path (e.g. index.js or src/main.kt)",
            value = inputName,
            onValueChange = { inputName = it },
            onConfirm = {
                viewModel.createFile("", inputName.trim())
                showNewFileDialog = false
            },
            onDismiss = { showNewFileDialog = false }
        )
    }

    // New Folder Dialog
    if (showNewFolderDialog) {
        SimpleInputDialog(
            title = "New Folder",
            label = "Folder name",
            value = inputName,
            onValueChange = { inputName = it },
            onConfirm = {
                viewModel.createFolder("", inputName.trim())
                showNewFolderDialog = false
            },
            onDismiss = { showNewFolderDialog = false }
        )
    }

    // New Project Dialog
    if (showNewProjectDialog) {
        SimpleInputDialog(
            title = "New Project",
            label = "Project name",
            value = inputName,
            onValueChange = { inputName = it },
            onConfirm = {
                viewModel.createProject(inputName.trim())
                showNewProjectDialog = false
            },
            onDismiss = { showNewProjectDialog = false }
        )
    }

    // Rename Dialog
    showRenameDialog?.let { oldPath ->
        SimpleInputDialog(
            title = "Rename",
            label = "New name",
            value = inputName,
            onValueChange = { inputName = it },
            onConfirm = {
                viewModel.renameItem(oldPath, inputName.trim())
                showRenameDialog = null
            },
            onDismiss = { showRenameDialog = null }
        )
    }
}

@Composable
fun EditorTopBar(
    activeProjectName: String,
    activeTab: String?,
    isDirty: Boolean,
    isDiffMode: Boolean,
    onToggleDrawer: () -> Unit,
    onSave: () -> Unit,
    onToggleDiff: () -> Unit,
    onOpenSearch: () -> Unit,
    onExportZip: () -> Unit,
    onImportZip: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleDrawer) {
            Icon(Icons.Default.Menu, contentDescription = "Open file tree")
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activeProjectName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = activeTab ?: "No file open",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onSave, enabled = isDirty) {
            Icon(
                Icons.Default.Save,
                contentDescription = "Save file",
                tint = if (isDirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }

        IconButton(onClick = onToggleDiff) {
            Icon(
                Icons.Default.Difference,
                contentDescription = "Toggle Diff mode",
                tint = if (isDiffMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onOpenSearch) {
            Icon(Icons.Default.Search, contentDescription = "Search in workspace")
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.Folder, contentDescription = "Project options")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Export project as ZIP") },
                    onClick = {
                        menuExpanded = false
                        onExportZip()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Import ZIP into project") },
                    onClick = {
                        menuExpanded = false
                        onImportZip()
                    }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MonacoWebViewContainer(
    isDark: Boolean,
    onEditorReady: () -> Unit,
    onContentChanged: (String) -> Unit,
    onSave: () -> Unit,
    onWebViewCreated: (WebView) -> Unit
) {
    val context = LocalContext.current
    val bridge = remember {
        MonacoBridge(
            onReady = onEditorReady,
            onContentChanged = onContentChanged,
            onSaveRequested = onSave
        )
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false

                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                    .build()

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        return assetLoader.shouldInterceptRequest(request.url)
                    }
                }

                addJavascriptInterface(bridge, "AndroidBridge")
                loadUrl("https://appassets.androidplatform.net/assets/monaco/index.html")
                onWebViewCreated(this)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun FileTreeDrawerContent(
    uiState: dev.pocketagent.ui.viewmodel.EditorUiState,
    onSelectProject: (dev.pocketagent.core.model.Project) -> Unit,
    onNewProject: () -> Unit,
    onOpenFile: (String) -> Unit,
    onToggleFolder: (String) -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var projectDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Project Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { projectDropdown = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = uiState.activeProject?.name ?: "Select Project",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
        }

        DropdownMenu(expanded = projectDropdown, onDismissRequest = { projectDropdown = false }) {
            uiState.projects.forEach { proj ->
                DropdownMenuItem(
                    text = { Text(proj.name) },
                    onClick = {
                        projectDropdown = false
                        onSelectProject(proj)
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("+ New Project...", fontWeight = FontWeight.Bold) },
                onClick = {
                    projectDropdown = false
                    onNewProject()
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Actions: New File / New Folder
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onNewFile, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("File", fontSize = 12.sp)
            }
            TextButton(onClick = onNewFolder, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Folder", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Recursive File Tree
        LazyColumn(modifier = Modifier.weight(1f)) {
            uiState.fileTree?.let { root ->
                items(root.children) { child ->
                    FileTreeNodeItem(
                        node = child,
                        level = 0,
                        expandedFolders = uiState.expandedFolders,
                        onOpenFile = onOpenFile,
                        onToggleFolder = onToggleFolder,
                        onRename = onRename,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

@Composable
fun FileTreeNodeItem(
    node: FileNode,
    level: Int,
    expandedFolders: Set<String>,
    onOpenFile: (String) -> Unit,
    onToggleFolder: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val isExpanded = expandedFolders.contains(node.relativePath)
    var showMenu by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (node.isDirectory) {
                        onToggleFolder(node.relativePath)
                    } else {
                        onOpenFile(node.relativePath)
                    }
                }
                .padding(start = (level * 16).dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (node.isDirectory) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Spacer(modifier = Modifier.width(20.dp))
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )

            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit options", modifier = Modifier.size(14.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            showMenu = false
                            onRename(node.relativePath)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete(node.relativePath)
                        }
                    )
                }
            }
        }

        if (node.isDirectory && isExpanded) {
            for (child in node.children) {
                FileTreeNodeItem(
                    node = child,
                    level = level + 1,
                    expandedFolders = expandedFolders,
                    onOpenFile = onOpenFile,
                    onToggleFolder = onToggleFolder,
                    onRename = onRename,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
fun SearchDialog(
    query: String,
    results: List<dev.pocketagent.core.model.SearchMatch>,
    onQueryChange: (String) -> Unit,
    onSelectMatch: (dev.pocketagent.core.model.SearchMatch) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search in Workspace") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search text across all files...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "${results.size} matches found",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.height(260.dp)) {
                    items(results) { match ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onSelectMatch(match) }
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "${match.relativePath}:${match.lineNumber}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = match.lineContent,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun SimpleInputDialog(
    title: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = value.isNotBlank()) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
