package dev.pocketagent.ui.screens.terminal

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pocketagent.core.sandbox.BootstrapStatus

@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel = viewModel()
) {
    val bootstrapStatus by viewModel.bootstrapStatus.collectAsState()
    val tabs by viewModel.tabs.collectAsState()
    val activeIndex by viewModel.activeTabIndex.collectAsState()

    var inputCommand by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .imePadding()
    ) {
        // Bootstrap Setup Card (if not ready and tabs empty)
        if (bootstrapStatus !is BootstrapStatus.Ready && tabs.isEmpty()) {
            BootstrapBanner(
                status = bootstrapStatus,
                onStartBootstrap = { viewModel.startBootstrap() },
                onLaunchHostShell = { viewModel.openNewTab("host") }
            )
        } else {
            // Tabs Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = index == activeIndex
                    Row(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFF21262D) else Color(0xFF161B22))
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFF388BFD) else Color(0xFF30363D),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { viewModel.selectTab(index) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (tab.isRunning) Color(0xFF7EE787) else Color(0xFF8B949E)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tab.title,
                            color = if (isSelected) Color(0xFFE6EDF3) else Color(0xFF8B949E),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Tab",
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { viewModel.closeTab(tab.id) },
                            tint = Color(0xFF8B949E)
                        )
                    }
                }

                // Add Tab Button
                IconButton(
                    onClick = { viewModel.openNewTab() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Terminal Tab",
                        tint = Color(0xFF58A6FF)
                    )
                }
            }

            // Active Tab Terminal Buffer
            val currentTab = tabs.getOrNull(activeIndex)
            val terminalScrollState = rememberScrollState()

            LaunchedEffect(currentTab?.output) {
                if ((currentTab?.output?.length ?: 0) > 0) {
                    terminalScrollState.scrollTo(terminalScrollState.maxValue)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                if (currentTab != null) {
                    val annotatedOutput = remember(currentTab.output) {
                        AnsiParser.parse(currentTab.output)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(terminalScrollState)
                    ) {
                        Text(
                            text = annotatedOutput,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No active terminal session.\nTap + above to start a shell.",
                            color = Color(0xFF8B949E),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Extra Keys Row (Termux-style)
            ExtraKeysBar(
                onKey = { key -> viewModel.sendSpecialKey(key) },
                onClear = { viewModel.clearActiveTab() }
            )

            // Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$ ",
                    color = Color(0xFF58A6FF),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                OutlinedTextField(
                    value = inputCommand,
                    onValueChange = { inputCommand = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Enter command (e.g. uname -a, node -v, git status)...",
                            fontSize = 12.sp,
                            color = Color(0xFF484F58)
                        )
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Color(0xFFE6EDF3)
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF388BFD),
                        unfocusedBorderColor = Color(0xFF30363D),
                        focusedContainerColor = Color(0xFF0D1117),
                        unfocusedContainerColor = Color(0xFF0D1117)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputCommand.isNotBlank()) {
                                viewModel.sendInput("$inputCommand\n", echo = true)
                                inputCommand = ""
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.width(6.dp))

                Button(
                    onClick = {
                        if (inputCommand.isNotBlank()) {
                            viewModel.sendInput("$inputCommand\n", echo = true)
                            inputCommand = ""
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Run")
                }
            }
        }
    }
}

@Composable
fun ExtraKeysBar(
    onKey: (String) -> Unit,
    onClear: () -> Unit
) {
    val keys = listOf(
        "ESC" to "\u001b",
        "TAB" to "\t",
        "^C" to "\u0003",
        "^D" to "\u0004",
        "^Z" to "\u001a",
        "|" to "|",
        "~" to "~",
        "/" to "/",
        "-" to "-",
        "$" to "$",
        "▲" to "\u001b[A",
        "▼" to "\u001b[B",
        "◀" to "\u001b[D",
        "▶" to "\u001b[C"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161B22))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEach { (label, code) ->
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onKey(code) },
                color = Color(0xFF21262D),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = Color(0xFFE6EDF3),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Clear button
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { onClear() },
            color = Color(0xFF30363D),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = "CLEAR",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                color = Color(0xFFFFA198),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun BootstrapBanner(
    status: BootstrapStatus,
    onStartBootstrap: () -> Unit,
    onLaunchHostShell: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color(0xFF58A6FF)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Linux Sandbox Setup",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFE6EDF3),
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Install Alpine Linux minirootfs (~3.5 MB) to run isolated Linux shells with proot, nodejs, python3, and git.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8B949E),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (status) {
                    is BootstrapStatus.Idle -> {
                        Button(
                            onClick = onStartBootstrap,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Install Alpine Linux Rootfs")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = onLaunchHostShell,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Launch Direct Shell Fallback")
                        }
                    }

                    is BootstrapStatus.Downloading -> {
                        Text(
                            text = "Downloading rootfs: ${status.percent}% (${status.bytesDownloaded / 1024} KB)",
                            color = Color(0xFF58A6FF),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { status.percent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    is BootstrapStatus.Verifying -> {
                        Text(
                            text = "Verifying SHA-256 integrity...",
                            color = Color(0xFFFFA657),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    is BootstrapStatus.Extracting -> {
                        Text(
                            text = "Extracting files: ${status.currentFile} (#${status.count})",
                            color = Color(0xFF7EE787),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    is BootstrapStatus.Configuring -> {
                        Text(
                            text = "Configuring /etc/resolv.conf and workspace mount...",
                            color = Color(0xFFD2A8FF),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    is BootstrapStatus.Failed -> {
                        Text(
                            text = "Error: ${status.error}",
                            color = Color(0xFFFF7B72),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onStartBootstrap,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Retry Installation")
                        }
                    }

                    is BootstrapStatus.Ready -> {
                        Text("Linux Sandbox is ready!", color = Color(0xFF7EE787))
                    }
                }
            }
        }
    }
}
