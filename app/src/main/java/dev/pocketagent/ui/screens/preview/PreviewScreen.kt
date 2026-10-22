package dev.pocketagent.ui.screens.preview

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pocketagent.core.model.ConsoleLogLevel
import dev.pocketagent.ui.browser.AndroidBrowserController
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    viewModel: PreviewViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.notificationMessage) {
        val msg = state.notificationMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearNotification()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(tonalElevation = 2.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    // Navigation and URL row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                (viewModel.browserController as? AndroidBrowserController)?.getOrCreateWebView()?.goBack()
                            },
                            enabled = state.navigationState.canGoBack
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                        }

                        IconButton(
                            onClick = {
                                (viewModel.browserController as? AndroidBrowserController)?.getOrCreateWebView()?.goForward()
                            },
                            enabled = state.navigationState.canGoForward
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward", modifier = Modifier.size(20.dp))
                        }

                        IconButton(
                            onClick = {
                                (viewModel.browserController as? AndroidBrowserController)?.getOrCreateWebView()?.reload()
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload", modifier = Modifier.size(20.dp))
                        }

                        OutlinedTextField(
                            value = state.urlInput,
                            onValueChange = { viewModel.onUrlChange(it) },
                            modifier = Modifier.weight(1f).height(50.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            placeholder = { Text("http://localhost:3000", fontSize = 12.sp) },
                            trailingIcon = {
                                IconButton(onClick = { viewModel.navigate() }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Go", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        // Frame Toggle
                        IconButton(onClick = { viewModel.toggleDeviceFrame() }) {
                            Icon(
                                when (state.deviceFrameMode) {
                                    DeviceFrameMode.RESPONSIVE -> Icons.Default.Computer
                                    DeviceFrameMode.PHONE -> Icons.Default.PhoneAndroid
                                    DeviceFrameMode.TABLET -> Icons.Default.Tablet
                                },
                                contentDescription = "Device Frame: ${state.deviceFrameMode}"
                            )
                        }

                        // Screenshot
                        IconButton(onClick = { viewModel.captureScreenshot() }) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Capture Screenshot")
                        }

                        // Console Log Toggle with Badge
                        IconButton(onClick = { viewModel.toggleConsole() }) {
                            val errorCount = state.filteredLogs.count { it.level == ConsoleLogLevel.ERROR }
                            BadgedBox(
                                badge = {
                                    if (errorCount > 0) {
                                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                                            Text("$errorCount")
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Terminal, contentDescription = "Console Logs")
                            }
                        }
                    }

                    // Quick Port Selector Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ports:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        listOf(3000, 5173, 8080, 8000, 4321, 4200).forEach { port ->
                            FilterChip(
                                selected = state.urlInput.contains(":$port"),
                                onClick = { viewModel.selectPort(port) },
                                label = { Text(":$port", fontSize = 11.sp) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF1E1E1E))
        ) {
            // Main WebView Area (with device mockup wrapper if requested)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val frameModifier = when (state.deviceFrameMode) {
                    DeviceFrameMode.PHONE -> Modifier
                        .width(360.dp)
                        .fillMaxHeight(0.92f)
                        .shadow(8.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .border(4.dp, Color(0xFF333333), RoundedCornerShape(24.dp))
                        .background(Color.White)
                    DeviceFrameMode.TABLET -> Modifier
                        .width(600.dp)
                        .fillMaxHeight(0.92f)
                        .shadow(8.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .border(4.dp, Color(0xFF333333), RoundedCornerShape(16.dp))
                        .background(Color.White)
                    DeviceFrameMode.RESPONSIVE -> Modifier.fillMaxSize()
                }

                Box(modifier = frameModifier) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { _ ->
                            val controller = viewModel.browserController as AndroidBrowserController
                            val wv = controller.getOrCreateWebView()
                            (wv.parent as? ViewGroup)?.removeView(wv)
                            wv
                        }
                    )

                    if (state.navigationState.isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                        )
                    }
                }
            }

            // Bottom Collapsible Console Log Panel
            AnimatedVisibility(
                visible = state.isConsoleOpen,
                enter = expandVertically(),
                exit = shrinkVertically(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    tonalElevation = 8.dp,
                    color = Color(0xFF121212)
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        // Console Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Console (${state.filteredLogs.size})",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                                listOf("ALL", "ERROR", "WARN", "LOG").forEach { lvl ->
                                    FilterChip(
                                        selected = state.selectedLogLevel == lvl,
                                        onClick = { viewModel.selectLogLevel(lvl) },
                                        label = { Text(lvl, fontSize = 10.sp) },
                                        modifier = Modifier.height(24.dp)
                                    )
                                }
                            }

                            Row {
                                IconButton(
                                    onClick = { viewModel.clearConsole() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.toggleConsole() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFF2A2A2A), modifier = Modifier.padding(vertical = 4.dp))

                        // Console Logs List
                        if (state.filteredLogs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No console logs captured.", color = Color.Gray, fontSize = 12.sp)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(state.filteredLogs) { log ->
                                    val (badgeBg, badgeFg) = when (log.level) {
                                        ConsoleLogLevel.ERROR -> Color(0xFFE53935) to Color.White
                                        ConsoleLogLevel.WARN -> Color(0xFFFFB300) to Color.Black
                                        ConsoleLogLevel.INFO -> Color(0xFF1E88E5) to Color.White
                                        ConsoleLogLevel.DEBUG -> Color(0xFF757575) to Color.White
                                        ConsoleLogLevel.LOG -> Color(0xFF43A047) to Color.White
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            timeFormat.format(Date(log.timestamp)),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = Color.DarkGray
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = badgeBg,
                                            shape = RoundedCornerShape(3.dp),
                                            modifier = Modifier.padding(top = 1.dp)
                                        ) {
                                            Text(
                                                log.level.name,
                                                color = badgeFg,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                log.message,
                                                color = Color(0xFFEEEEEE),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
                                            )
                                            if (log.sourceId.isNotBlank()) {
                                                Text(
                                                    "${File(log.sourceId).name}:${log.lineNumber}",
                                                    color = Color.Gray,
                                                    fontSize = 9.sp
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
        }
    }
}
