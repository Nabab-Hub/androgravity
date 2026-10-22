package dev.pocketagent.ui.screens.mcp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pocketagent.core.model.McpPolicy
import dev.pocketagent.core.model.McpServerConfig
import dev.pocketagent.core.model.McpTransportType

@Composable
fun McpServersSection(
    viewModel: McpViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val servers by viewModel.servers.collectAsState()
    val connectingMap by viewModel.connectingMap.collectAsState()
    val statusMap by viewModel.statusMessageMap.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "MCP Tool Servers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Model Context Protocol servers providing external tools.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                onClick = { showAddDialog = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Server", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (servers.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No MCP servers configured.\nTap 'Add Server' to connect remote or local MCP tools.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                servers.forEach { server ->
                    McpServerCard(
                        server = server,
                        isConnecting = connectingMap[server.id] == true,
                        statusMessage = statusMap[server.id],
                        onToggle = { enabled -> viewModel.toggleServer(server.id, enabled) },
                        onPolicyChange = { policy -> viewModel.setPolicy(server.id, policy) },
                        onTestConnection = { viewModel.testConnection(server.id) },
                        onDelete = { viewModel.removeServer(server.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddMcpServerDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, transport, url, cmd, policy ->
                viewModel.addServer(name, transport, url, cmd, policy)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun McpServerCard(
    server: McpServerConfig,
    isConnecting: Boolean,
    statusMessage: String?,
    onToggle: (Boolean) -> Unit,
    onPolicyChange: (McpPolicy) -> Unit,
    onTestConnection: () -> Unit,
    onDelete: () -> Unit
) {
    var expandedTools by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (server.isEnabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Name, Transport badge, Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        server.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            server.transportType.name.replace("_", "/"),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Switch(
                    checked = server.isEnabled,
                    onCheckedChange = onToggle
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // URL or Command info
            Text(
                text = if (server.transportType == McpTransportType.HTTP_SSE) server.url.ifBlank { "No URL specified" }
                else server.command.joinToString(" ").ifBlank { "No command specified" },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Policy row: Allow / Ask / Deny
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Policy:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    McpPolicy.values().forEach { policy ->
                        val selected = server.policy == policy
                        FilterChip(
                            selected = selected,
                            onClick = { onPolicyChange(policy) },
                            label = { Text(policy.name, fontSize = 10.sp) },
                            modifier = Modifier.height(28.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when (policy) {
                                    McpPolicy.ALLOW -> Color(0xFF2E7D32)
                                    McpPolicy.ASK -> Color(0xFFF57C00)
                                    McpPolicy.DENY -> Color(0xFFC62828)
                                },
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Actions row: Test/Connect, Discovered tools button, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onTestConnection,
                        enabled = !isConnecting && server.isEnabled,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Connecting...", fontSize = 11.sp)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Fetch Tools", fontSize = 11.sp)
                        }
                    }

                    if (server.tools.isNotEmpty()) {
                        TextButton(
                            onClick = { expandedTools = !expandedTools },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("${server.tools.size} tools", fontSize = 11.sp)
                            Icon(
                                if (expandedTools) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Status message
            if (statusMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                val isErr = statusMessage.startsWith("Error")
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = if (isErr) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                )
            }

            // Discovered Tools Collapsible
            AnimatedVisibility(visible = expandedTools && server.tools.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    server.tools.forEach { tool ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                "mcp.${server.name}.${tool.name}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (tool.description.isNotBlank()) {
                                Text(
                                    tool.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddMcpServerDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, transport: McpTransportType, url: String, cmd: String, policy: McpPolicy) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf(McpTransportType.HTTP_SSE) }
    var url by remember { mutableStateOf("http://10.0.2.2:8000/sse") }
    var command by remember { mutableStateOf("") }
    var policy by remember { mutableStateOf(McpPolicy.ASK) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add MCP Server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Name (e.g. weather)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Transport selector
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    McpTransportType.values().forEach { t ->
                        FilterChip(
                            selected = transport == t,
                            onClick = { transport = t },
                            label = { Text(t.name.replace("_", "/")) }
                        )
                    }
                }

                if (transport == McpTransportType.HTTP_SSE) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Server URL / SSE Endpoint") },
                        placeholder = { Text("http://10.0.2.2:8000/sse") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = { Text("Command (inside Linux/proot)") },
                        placeholder = { Text("npx -y @modelcontextprotocol/server-everything") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Default Policy
                Text("Default Autonomy Policy:", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    McpPolicy.values().forEach { p ->
                        FilterChip(
                            selected = policy == p,
                            onClick = { policy = p },
                            label = { Text(p.name, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name, transport, url, command, policy) },
                enabled = name.isNotBlank() && (if (transport == McpTransportType.HTTP_SSE) url.isNotBlank() else command.isNotBlank())
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
