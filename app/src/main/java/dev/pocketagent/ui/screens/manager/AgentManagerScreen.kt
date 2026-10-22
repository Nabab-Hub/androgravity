package dev.pocketagent.ui.screens.manager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pocketagent.core.model.AgentSession
import dev.pocketagent.core.model.AutonomyMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentManagerScreen(
    viewModel: AgentManagerViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    if (state.selectedSession != null) {
        SessionDetailScreen(
            viewModel = viewModel,
            onBack = { viewModel.clearSelectedSession() }
        )
    } else {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { viewModel.openNewSessionDialog() }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Session")
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Project Filter Bar
                if (state.projects.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.selectedProjectId == null,
                            onClick = { viewModel.selectProject(null) },
                            label = { Text("All Projects") }
                        )
                        state.projects.forEach { proj ->
                            FilterChip(
                                selected = state.selectedProjectId == proj.id,
                                onClick = { viewModel.selectProject(proj.id) },
                                label = { Text(proj.name) }
                            )
                        }
                    }
                }

                if (state.sessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Dashboard,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No Agent Sessions Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Create a new agent session to plan, build, and inspect code.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.openNewSessionDialog() }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("New Session")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.sessions, key = { it.id }) { session ->
                            SessionCard(
                                session = session,
                                onClick = { viewModel.selectSession(session) },
                                onDelete = { viewModel.deleteSession(session.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showNewSessionDialog) {
        NewSessionDialog(
            projects = state.projects,
            onDismiss = { viewModel.closeNewSessionDialog() },
            onConfirm = { title, projId, model, mode ->
                viewModel.createSession(title, projId, model, mode)
            }
        )
    }
}

@Composable
fun SessionCard(
    session: AgentSession,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(session.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timeline, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Step ${session.currentStep}/${session.maxSteps}", style = MaterialTheme.typography.bodySmall)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(formatElapsedTime(session.elapsedTimeMs), style = MaterialTheme.typography.bodySmall)
                }

                if (session.changedFilesCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Difference, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${session.changedFilesCount} files changed", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${session.model} • ${formatDate(session.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSessionDialog(
    projects: List<dev.pocketagent.core.model.Project>,
    onDismiss: () -> Unit,
    onConfirm: (title: String, projectId: String?, model: String, mode: AutonomyMode) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedProject by remember { mutableStateOf(projects.firstOrNull()?.id) }
    var selectedModel by remember { mutableStateOf("gemini-1.5-flash") }
    var selectedMode by remember { mutableStateOf(AutonomyMode.AGENT_DECIDES) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Agent Session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task / Session Title") },
                    placeholder = { Text("e.g. Build Todo App with Vite") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (projects.isNotEmpty()) {
                    Text("Target Project:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        projects.forEach { proj ->
                            FilterChip(
                                selected = selectedProject == proj.id,
                                onClick = { selectedProject = proj.id },
                                label = { Text(proj.name) }
                            )
                        }
                    }
                }

                Text("Autonomy Mode:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedMode == AutonomyMode.AGENT_DECIDES,
                        onClick = { selectedMode = AutonomyMode.AGENT_DECIDES },
                        label = { Text("Semi-Auto") }
                    )
                    FilterChip(
                        selected = selectedMode == AutonomyMode.ALWAYS_PROCEED,
                        onClick = { selectedMode = AutonomyMode.ALWAYS_PROCEED },
                        label = { Text("Full Auto") }
                    )
                    FilterChip(
                        selected = selectedMode == AutonomyMode.ASK_ALWAYS,
                        onClick = { selectedMode = AutonomyMode.ASK_ALWAYS },
                        label = { Text("Manual") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        title.ifBlank { "Agent Session" },
                        selectedProject,
                        selectedModel,
                        selectedMode
                    )
                }
            ) {
                Text("Start Session")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatElapsedTime(ms: Long): String {
    if (ms <= 0L) return "0s"
    val sec = (ms / 1000) % 60
    val min = (ms / (1000 * 60))
    return if (min > 0) "${min}m ${sec}s" else "${sec}s"
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
