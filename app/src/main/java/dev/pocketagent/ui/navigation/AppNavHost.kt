package dev.pocketagent.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.pocketagent.ui.screens.chat.ChatScreen
import dev.pocketagent.ui.screens.editor.EditorScreen
import dev.pocketagent.ui.screens.manager.AgentManagerScreen
import dev.pocketagent.ui.screens.preview.PreviewScreen
import dev.pocketagent.ui.screens.terminal.TerminalScreen
import dev.pocketagent.ui.screens.settings.SettingsScreen

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavHost() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= 720.dp) {
            TabletTwoPaneLayout()
        } else {
            PhoneNavigationLayout()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneNavigationLayout() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavDestination.Chat.route

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentRoute) {
                            NavDestination.Manager.route -> "Manager"
                            NavDestination.Chat.route -> "Androgravity Chat"
                            NavDestination.Editor.route -> "Editor"
                            NavDestination.Terminal.route -> "Terminal"
                            NavDestination.Preview.route -> "Preview"
                            NavDestination.Settings.route -> "Settings"
                            else -> "Androgravity"
                        }
                    )
                },
                navigationIcon = {
                    if (currentRoute == NavDestination.Settings.route) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (currentRoute != NavDestination.Settings.route) {
                        IconButton(onClick = {
                            navController.navigate(NavDestination.Settings.route) {
                                launchSingleTop = true
                            }
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                }
            )
        },
        bottomBar = {
            val density = LocalDensity.current
            val isImeOpen = WindowInsets.ime.getBottom(density) > 0
            if (!isImeOpen) {
                NavigationBar {
                    NavDestination.bottomNavItems.filterNotNull().forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute == NavDestination.Settings.route) {
                                    navController.popBackStack()
                                }
                                if (currentRoute != destination.route) {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.title) },
                            label = { Text(destination.title) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavDestination.Chat.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavDestination.Manager.route) { AgentManagerScreen() }
            composable(NavDestination.Chat.route) { ChatScreen() }
            composable(NavDestination.Editor.route) { EditorScreen() }
            composable(NavDestination.Terminal.route) { TerminalScreen() }
            composable(NavDestination.Preview.route) { PreviewScreen() }
            composable(NavDestination.Settings.route) { SettingsScreen() }
        }
    }
}
