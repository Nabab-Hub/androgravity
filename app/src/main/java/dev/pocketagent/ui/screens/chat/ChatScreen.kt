package dev.pocketagent.ui.screens.chat

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import dev.pocketagent.core.model.Project
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pocketagent.core.agent.policy.AutonomyMode
import dev.pocketagent.core.model.Message
import dev.pocketagent.core.model.ToolCall
import dev.pocketagent.ui.viewmodel.ChatViewModel
import dev.pocketagent.ui.viewmodel.PendingApproval

@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(),
    modifier: Modifier = Modifier,
    sessionId: String? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(sessionId) {
        if (sessionId != null) {
            viewModel.loadSession(sessionId)
        } else {
            viewModel.loadActiveWorkspace()
        }
    }

    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        // Workspace & New Session Bar
        WorkspaceSelectorBar(
            activeProject = uiState.activeProject,
            projects = uiState.projects,
            onSelectWorkspace = { viewModel.switchWorkspace(it) },
            onCreateWorkspace = { viewModel.createNewWorkspace(it) },
            onNewSession = { viewModel.startNewSession() }
        )

        // Autonomy Mode Selector Bar
        AutonomyModeBar(
            currentMode = uiState.autonomyMode,
            onSelectMode = { viewModel.setAutonomyMode(it) }
        )

        // Message List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(uiState.messages, key = { it.id }) { message ->
                MessageItem(message = message)
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (uiState.isStreaming) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.currentStep > 0) "Agent is executing step #${uiState.currentStep}..." else "Agent is thinking...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.cancelStream() }) {
                            Text("Stop", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            uiState.errorMessage?.let { error ->
                item {
                    val clipboardManager = LocalClipboardManager.current
                    val context = LocalContext.current
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Error",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(error))
                                        Toast.makeText(context, "Error copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy error",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            SelectionContainer {
                                Text(text = error, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        // Pending Approval Card
        uiState.pendingApproval?.let { pending ->
            ApprovalBanner(
                pending = pending,
                onApprove = { viewModel.approvePending() },
                onDeny = { viewModel.denyPending() }
            )
        }

        // Input Bar
        SurfaceInputBar(
            inputText = inputText,
            onInputChange = { inputText = it },
            isStreaming = uiState.isStreaming,
            onSend = {
                if (inputText.isNotBlank()) {
                    val prompt = inputText
                    inputText = ""
                    viewModel.sendMessage(prompt)
                }
            },
            onCancel = { viewModel.cancelStream() }
        )
    }
}

@Composable
fun WorkspaceSelectorBar(
    activeProject: Project?,
    projects: List<Project>,
    onSelectWorkspace: (Project) -> Unit,
    onCreateWorkspace: (String) -> Unit,
    onNewSession: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newWorkspaceName by remember { mutableStateOf("") }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Workspace") },
            text = {
                OutlinedTextField(
                    value = newWorkspaceName,
                    onValueChange = { newWorkspaceName = it },
                    label = { Text("Workspace Name") },
                    placeholder = { Text("e.g. my-app") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newWorkspaceName.isNotBlank()) {
                            onCreateWorkspace(newWorkspaceName.trim())
                            newWorkspaceName = ""
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.clickable { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = activeProject?.name ?: "Select Workspace",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                Text(
                    text = "Select Workspace",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                projects.forEach { proj ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (proj.id == activeProject?.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    proj.name,
                                    fontWeight = if (proj.id == activeProject?.id) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelectWorkspace(proj)
                        }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create New Workspace...", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    onClick = {
                        expanded = false
                        showCreateDialog = true
                    }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // New Chat Button
        OutlinedButton(
            onClick = onNewSession,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("New Chat", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun AutonomyModeBar(
    currentMode: AutonomyMode,
    onSelectMode: (AutonomyMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Mode:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))

        AutonomyChip(
            label = "Semi-Auto",
            selected = currentMode == AutonomyMode.SEMI_AUTONOMY,
            onClick = { onSelectMode(AutonomyMode.SEMI_AUTONOMY) }
        )
        Spacer(modifier = Modifier.width(6.dp))
        AutonomyChip(
            label = "Full Autonomy",
            selected = currentMode == AutonomyMode.FULL_AUTONOMY,
            onClick = { onSelectMode(AutonomyMode.FULL_AUTONOMY) }
        )
        Spacer(modifier = Modifier.width(6.dp))
        AutonomyChip(
            label = "Manual",
            selected = currentMode == AutonomyMode.MANUAL,
            onClick = { onSelectMode(AutonomyMode.MANUAL) }
        )
    }
}

@Composable
fun AutonomyChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ApprovalBanner(
    pending: PendingApproval,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Approval Required: ${pending.toolCall.name}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = pending.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = pending.toolCall.argsJson,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                    .padding(6.dp)
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onDeny,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Deny")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Approve")
                }
            }
        }
    }
}

@Composable
fun MessageItem(message: Message) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    when (message) {
        is Message.User -> {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Surface(
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        SelectionContainer(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = message.text,
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(message.text))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy message",
                                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
        is Message.Assistant -> {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Column(modifier = Modifier.fillMaxWidth(0.92f)) {
                    if (message.text.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                SelectionContainer {
                                    Text(
                                        text = message.text,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(message.text))
                                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Copy response",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    for (tool in message.toolCalls) {
                        Spacer(modifier = Modifier.height(6.dp))
                        ToolCallCard(toolCall = tool)
                    }
                }
            }
        }
        is Message.ToolResult -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tool Result: ${message.toolName}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (message.ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(message.output))
                                Toast.makeText(context, "Tool result copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy tool result",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    SelectionContainer {
                        Text(
                            text = message.output.take(2000),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        is Message.System -> {
            SelectionContainer {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
fun ToolCallCard(toolCall: ToolCall) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Tool: ${toolCall.name}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        val toCopy = "${toolCall.name}(${toolCall.argsJson})"
                        clipboardManager.setText(AnnotatedString(toCopy))
                        Toast.makeText(context, "Tool call copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy tool call",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide args" else "Show args", style = MaterialTheme.typography.labelSmall)
                }
            }

            AnimatedVisibility(visible = expanded) {
                SelectionContainer {
                    Text(
                        text = toolCall.argsJson,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SurfaceInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    isStreaming: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            placeholder = { Text("Ask Androgravity to plan, code, or run...") },
            modifier = Modifier.weight(1f),
            maxLines = 4,
            shape = RoundedCornerShape(24.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        if (isStreaming) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cancel stream", tint = MaterialTheme.colorScheme.error)
            }
        } else {
            IconButton(
                onClick = onSend,
                enabled = inputText.isNotBlank(),
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
