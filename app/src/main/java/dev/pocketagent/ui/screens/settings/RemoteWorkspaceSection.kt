package dev.pocketagent.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.pocketagent.core.model.ExecBackendType
import dev.pocketagent.ui.viewmodel.SettingsUiState
import dev.pocketagent.ui.viewmodel.SettingsViewModel

@Composable
fun RemoteWorkspaceSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    var showToken by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Execution Sandbox Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose where the agent executes shell commands, runs npm/python, and manages processes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            val backendOptions = listOf(
                Triple(ExecBackendType.PROOT, "Embedded Linux (PRoot)", "Runs offline in local Alpine Linux userland on device"),
                Triple(ExecBackendType.DIRECT, "Direct App Sandbox", "Runs in Android app process sandbox (limited toolset)"),
                Triple(ExecBackendType.REMOTE, "Termux Bridge / Remote PC", "Runs inside local Termux (127.0.0.1:8787) or desktop PC / Cloud VM")
            )

            backendOptions.forEach { (type, title, desc) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (uiState.execBackendType == type),
                            onClick = { viewModel.updateExecBackendType(type) }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (uiState.execBackendType == type),
                        onClick = { viewModel.updateExecBackendType(type) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = title, fontWeight = FontWeight.Medium)
                        Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            AnimatedVisibility(visible = uiState.execBackendType == ExecBackendType.REMOTE) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(
                        text = "Remote Host Configuration",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uiState.remoteConfig.host,
                            onValueChange = { viewModel.updateRemoteConfig(uiState.remoteConfig.copy(host = it)) },
                            label = { Text("Host / IP") },
                            placeholder = { Text("127.0.0.1 (Termux) or 192.168.1.x") },
                            modifier = Modifier.weight(2f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = uiState.remoteConfig.port.toString(),
                            onValueChange = {
                                val portNum = it.toIntOrNull() ?: 8787
                                viewModel.updateRemoteConfig(uiState.remoteConfig.copy(port = portNum))
                            },
                            label = { Text("Port") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = uiState.remoteConfig.token,
                        onValueChange = { viewModel.updateRemoteConfig(uiState.remoteConfig.copy(token = it)) },
                        label = { Text("Auth Bearer Token") },
                        placeholder = { Text("Secret daemon token") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = uiState.remoteConfig.remoteWorkspacePath,
                        onValueChange = { viewModel.updateRemoteConfig(uiState.remoteConfig.copy(remoteWorkspacePath = it)) },
                        label = { Text("Remote Workspace Path (Optional)") },
                        placeholder = { Text("/home/user/projects/workspace") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Use HTTPS / WSS", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = uiState.remoteConfig.useTls,
                            onCheckedChange = { viewModel.updateRemoteConfig(uiState.remoteConfig.copy(useTls = it)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { viewModel.testRemoteConnection() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isTestingRemote
                    ) {
                        if (uiState.isTestingRemote) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing Connection...")
                        } else {
                            Text("Test Remote Daemon Connection")
                        }
                    }

                    uiState.remoteStatus?.let { status ->
                        Spacer(modifier = Modifier.height(8.dp))
                        val bgColor = if (status.isOnline) Color(0xFF1B5E20).copy(alpha = 0.2f) else MaterialTheme.colorScheme.errorContainer
                        val contentColor = if (status.isOnline) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onErrorContainer

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bgColor, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (status.isOnline) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                if (status.isOnline) {
                                    Text(
                                        text = "Online: ${status.os} (v${status.version})",
                                        fontWeight = FontWeight.Bold,
                                        color = contentColor
                                    )
                                    Text(
                                        text = "Latency: ${status.latencyMs} ms",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = contentColor
                                    )
                                } else {
                                    Text(
                                        text = "Connection Failed",
                                        fontWeight = FontWeight.Bold,
                                        color = contentColor
                                    )
                                    Text(
                                        text = status.errorMessage ?: "Make sure pocket-api is running on host.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = contentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
