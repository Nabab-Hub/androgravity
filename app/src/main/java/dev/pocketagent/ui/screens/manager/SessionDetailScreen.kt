package dev.pocketagent.ui.screens.manager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.core.model.SessionStatus
import dev.pocketagent.ui.screens.chat.ChatScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    viewModel: AgentManagerViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.detailState.collectAsState()
    val session = state.session ?: return

    val tabs = listOf("Chat", "Plan", "Tasks", "Walkthrough", "Diff", "Comments")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session.title, maxLines = 1, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusBadge(session.status)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Step ${session.currentStep}/${session.maxSteps}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadSessionDetails(session) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = state.activeTab,
                edgePadding = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = state.activeTab == index,
                        onClick = { viewModel.setDetailTab(index) },
                        text = {
                            Text(
                                title,
                                fontWeight = if (state.activeTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            if (state.actionMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.actionMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (state.activeTab) {
                    0 -> ChatScreen(sessionId = session.id)
                    1 -> PlanTab(planText = state.planText)
                    2 -> TasksTab(
                        tasks = state.tasks,
                        onToggleTask = { idx -> viewModel.toggleTask(idx) }
                    )
                    3 -> WalkthroughTab(walkthroughText = state.walkthroughText)
                    4 -> DiffTab(
                        diffText = state.diffText,
                        changedFiles = state.changedFiles,
                        isActionRunning = state.isActionRunning,
                        onUndoStep = { viewModel.undoLastStep() },
                        onRevert = {
                            val initial = state.checkpoints.lastOrNull()?.commitHash ?: "HEAD"
                            viewModel.revertSession(initial)
                        }
                    )
                    5 -> CommentsTab(
                        comments = state.comments,
                        onOpenAddComment = { viewModel.openAddCommentDialog() },
                        onDeleteComment = { id -> viewModel.deleteComment(id) }
                    )
                }
            }
        }
    }

    if (state.showAddCommentDialog) {
        AddCommentDialog(
            onDismiss = { viewModel.closeAddCommentDialog() },
            onConfirm = { artName, lineNum, sec, comment ->
                viewModel.addComment(artName, lineNum, sec, comment)
            }
        )
    }
}

@Composable
fun PlanTab(planText: String?) {
    if (planText.isNullOrBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No plan.md artifact created for this session yet.", color = MaterialTheme.colorScheme.outline)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = planText,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun TasksTab(
    tasks: List<dev.pocketagent.core.agent.artifacts.TaskItem>,
    onToggleTask: (Int) -> Unit
) {
    if (tasks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No task checklist found in tasks.md.", color = MaterialTheme.colorScheme.outline)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tasks) { task ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleTask(task.index) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = task.isCompleted,
                            onCheckedChange = { onToggleTask(task.index) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = task.text,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (task.isCompleted) FontWeight.Normal else FontWeight.Medium,
                            color = if (task.isCompleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WalkthroughTab(walkthroughText: String?) {
    if (walkthroughText.isNullOrBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No walkthrough.md artifact created yet.", color = MaterialTheme.colorScheme.outline)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = walkthroughText,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun DiffTab(
    diffText: String,
    changedFiles: List<dev.pocketagent.core.agent.checkpoint.ChangedFile>,
    isActionRunning: Boolean,
    onUndoStep: () -> Unit,
    onRevert: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Controls Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onUndoStep,
                enabled = !isActionRunning,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Undo Step", fontSize = 12.sp)
            }
            Button(
                onClick = onRevert,
                enabled = !isActionRunning,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Revert All", fontSize = 12.sp)
            }
        }

        if (changedFiles.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                changedFiles.forEach { file ->
                    AssistChip(
                        onClick = {},
                        label = { Text("${file.statusCode} ${file.path}") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (file.statusCode.contains("M")) Color(0xFFE3F2FD) else Color(0xFFE8F5E9)
                        )
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E))
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = diffText.ifBlank { "(no changes recorded in diff)" },
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Color(0xFFD4D4D4)
            )
        }
    }
}

@Composable
fun CommentsTab(
    comments: List<dev.pocketagent.core.agent.artifacts.ArtifactComment>,
    onOpenAddComment: () -> Unit,
    onDeleteComment: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (comments.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No review comments yet.\nAdd comments to guide the agent in subsequent turns.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(comments) { comment ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${comment.artifactName} ${if (comment.lineNumber != null) "Line ${comment.lineNumber}" else ""}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(onClick = { onDeleteComment(comment.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline)
                                }
                            }
                            Text(text = comment.commentText, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onOpenAddComment,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.AddComment, contentDescription = "Add Comment")
        }
    }
}

@Composable
fun AddCommentDialog(
    onDismiss: () -> Unit,
    onConfirm: (artifactName: String, lineNum: Int?, section: String?, comment: String) -> Unit
) {
    var artifactName by remember { mutableStateOf("plan.md") }
    var lineNumberStr by remember { mutableStateOf("") }
    var commentText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Review Feedback") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = artifactName,
                    onValueChange = { artifactName = it },
                    label = { Text("Target Artifact (e.g. plan.md, tasks.md)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lineNumberStr,
                    onValueChange = { lineNumberStr = it },
                    label = { Text("Line Number (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    label = { Text("Comment / Feedback for Agent") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (commentText.isNotBlank()) {
                        val line = lineNumberStr.toIntOrNull()
                        onConfirm(artifactName, line, null, commentText)
                    }
                }
            ) {
                Text("Add Feedback")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun StatusBadge(status: SessionStatus) {
    val (bg, fg) = when (status) {
        SessionStatus.PLANNING -> Color(0xFFE1F5FE) to Color(0xFF0288D1)
        SessionStatus.RUNNING -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        SessionStatus.WAITING_APPROVAL -> Color(0xFFFFF3E0) to Color(0xFFEF6C00)
        SessionStatus.DONE -> Color(0xFFEDE7F6) to Color(0xFF512DA8)
        SessionStatus.FAILED -> Color(0xFFFFEBEE) to Color(0xFFC62828)
        SessionStatus.CANCELLED -> Color(0xFFECEFF1) to Color(0xFF455A64)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = status.name,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}
