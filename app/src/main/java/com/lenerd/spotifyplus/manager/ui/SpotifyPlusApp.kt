package com.lenerd.spotifyplus.manager.ui

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lenerd.spotifyplus.manager.ui.screens.HomeScreen
import com.lenerd.spotifyplus.manager.ui.screens.SettingsScreen
import com.lenerd.spotifyplus.manager.viewmodel.ManagerViewModel

sealed class AppRoute(val route: String, val label: String) {
    data object Home : AppRoute("home", "Home")
    data object Settings : AppRoute("settings", "Settings")
}

@Composable
fun SpotifyPlusApp(viewModel: ManagerViewModel, context: Context) {
    val navController = rememberNavController()

    val items = listOf(
        AppRoute.Home,
        AppRoute.Settings,
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { screen ->
                    val icon = when (screen) {
                        AppRoute.Home -> Icons.Default.Home
                        AppRoute.Settings -> Icons.Default.Settings
                    }

                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(AppRoute.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = screen.label) },
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppRoute.Home.route) {
                HomeScreen(
                    state = viewModel.uiState,
                    onRefreshStatus = viewModel::refreshStatus,
                    onCheckForUpdates = viewModel::checkForUpdates,
                    onEnableAllHooks = viewModel::enableAllHooks,
                    onDisableAllHooks = viewModel::disableAllHooks,
                    onOpenGithub = { viewModel.openGithub(context) },
                    onOpenUpdate = { viewModel.openGithub(context) },
                    onNodeTest = viewModel::nodeTest
                )
            }

            composable(AppRoute.Settings.route) {
                SettingsScreen(
                    state = viewModel.uiState,
                    onSetAutoCheckUpdates = viewModel::setAutoCheckUpdates,
                    onSetDebugLogging = viewModel::setDebugLogging,
                    onSetStartupCheck = viewModel::setStartupCheck,
                    onSetTheme = viewModel::setTheme
                )
            }
        }
    }
}