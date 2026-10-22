package dev.pocketagent.ui.screens.placeholders

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PlaceholderScreen(
    title: String,
    subtitle: String,
    icon: ImageVector,
    phase: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = phase,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun ManagerScreen() {
    PlaceholderScreen(
        title = "Agent Manager",
        subtitle = "Multi-agent parallel mission control and worktree coordinator.",
        icon = Icons.Default.Dashboard,
        phase = "Coming in Phase 5"
    )
}

@Composable
fun EditorScreen() {
    PlaceholderScreen(
        title = "Monaco Code Editor",
        subtitle = "Workspace file tree and full-featured syntax & diff editor.",
        icon = Icons.Default.Code,
        phase = "Coming in Phase 2"
    )
}

@Composable
fun TerminalScreen() {
    PlaceholderScreen(
        title = "Embedded Linux Terminal",
        subtitle = "Alpine rootfs under proot with package manager (node, python, git).",
        icon = Icons.Default.Terminal,
        phase = "Coming in Phase 3"
    )
}
