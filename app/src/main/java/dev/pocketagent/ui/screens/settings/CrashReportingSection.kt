package dev.pocketagent.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.ui.viewmodel.SettingsUiState
import dev.pocketagent.ui.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CrashReportingSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    var showLogsDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Privacy & Crash Diagnostics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Local-first diagnostic reporting. Maza Agent transmits zero telemetry. When enabled, exceptions are scrubbed of all credentials and paths, and saved locally on-device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Local Crash Diagnostics",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (uiState.isCrashReportingEnabled) "Active (${uiState.crashReports.size} reports logged)" else "Disabled (Default)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.isCrashReportingEnabled,
                    onCheckedChange = { viewModel.setCrashReportingEnabled(it) }
                )
            }

            AnimatedVisibility(visible = uiState.isCrashReportingEnabled) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    OutlinedButton(
                        onClick = { showLogsDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Scrubbed Crash Logs (${uiState.crashReports.size})")
                    }
                }
            }
        }
    }

    if (showLogsDialog) {
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            title = {
                Text("Scrubbed Diagnostic Logs", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.height(400.dp)) {
                    Text(
                        text = "All API keys, secrets, and workspace paths are cryptographically redacted before display.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (uiState.crashReports.isEmpty()) {
                        Text(
                            text = "No crashes recorded. Everything is operating cleanly!",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(uiState.crashReports) { report ->
                                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                    .format(Date(report.timestamp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = "$dateStr • ${if (report.isFatal) "FATAL" else "CAUGHT"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (report.isFatal) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = report.exceptionClass,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = report.scrubbedMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = report.scrubbedStackTrace.take(300) + if (report.scrubbedStackTrace.length > 300) "..." else "",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val fullLog = viewModel.getExportedCrashLogs()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Maza Agent Diagnostic Log", fullLog))
                        Toast.makeText(context, "Scrubbed diagnostic logs copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy All")
                }
            },
            dismissButton = {
                Row {
                    if (uiState.crashReports.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                viewModel.clearCrashReports()
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }
                    TextButton(onClick = { showLogsDialog = false }) {
                        Text("Close")
                    }
                }
            }
        )
    }
}
