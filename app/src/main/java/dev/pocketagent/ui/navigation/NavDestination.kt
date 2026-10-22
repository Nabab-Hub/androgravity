package dev.pocketagent.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

sealed class NavDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Manager : NavDestination("manager", "Manager", Icons.Default.Dashboard)
    object Chat : NavDestination("chat", "Chat", Icons.Default.Forum)
    object Editor : NavDestination("editor", "Editor", Icons.Default.Code)
    object Terminal : NavDestination("terminal", "Terminal", Icons.Default.Terminal)
    object Preview : NavDestination("preview", "Preview", Icons.Default.Preview)
    object Settings : NavDestination("settings", "Settings", Icons.Default.Settings)

    companion object {
        val bottomNavItems = listOf(Manager, Chat, Editor, Terminal, Preview)
    }
}
