package dev.pocketagent.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pocketagent.ui.screens.chat.ChatScreen
import dev.pocketagent.ui.screens.editor.EditorScreen
import dev.pocketagent.ui.screens.manager.AgentManagerScreen
import dev.pocketagent.ui.screens.preview.PreviewScreen
import dev.pocketagent.ui.screens.settings.SettingsScreen
import dev.pocketagent.ui.screens.terminal.TerminalScreen

enum class PrimaryPaneTab(val title: String) {
    CHAT("Chat"),
    MANAGER("Manager"),
    SETTINGS("Settings")
}

enum class SecondaryPaneTab(val title: String) {
    EDITOR("Editor"),
    TERMINAL("Terminal"),
    PREVIEW("Preview")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabletTwoPaneLayout() {
    var primaryTab by remember { mutableStateOf(PrimaryPaneTab.CHAT) }
    var secondaryTab by remember { mutableStateOf(SecondaryPaneTab.EDITOR) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Column: Agent & Navigation (45% width)
        Column(
            modifier = Modifier
                .weight(0.45f)
                .fillMaxHeight()
        ) {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Maza Agent",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PrimaryPaneTab.values().forEach { tab ->
                            FilterChip(
                                selected = primaryTab == tab,
                                onClick = { primaryTab = tab },
                                label = { Text(tab.title, fontSize = 11.sp) },
                                modifier = Modifier.height(30.dp)
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (primaryTab) {
                    PrimaryPaneTab.CHAT -> ChatScreen()
                    PrimaryPaneTab.MANAGER -> AgentManagerScreen()
                    PrimaryPaneTab.SETTINGS -> SettingsScreen()
                }
            }
        }

        // Vertical divider between the two panes
        VerticalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // Right Column: Workspace View (Editor / Terminal / Preview) (55% width)
        Column(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxHeight()
        ) {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Workspace",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SecondaryPaneTab.values().forEach { tab ->
                            FilterChip(
                                selected = secondaryTab == tab,
                                onClick = { secondaryTab = tab },
                                label = { Text(tab.title, fontSize = 11.sp) },
                                modifier = Modifier.height(30.dp)
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (secondaryTab) {
                    SecondaryPaneTab.EDITOR -> EditorScreen()
                    SecondaryPaneTab.TERMINAL -> TerminalScreen()
                    SecondaryPaneTab.PREVIEW -> PreviewScreen()
                }
            }
        }
    }
}
