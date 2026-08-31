package com.lenerd.spotifyplus.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lenerd.spotifyplus.manager.model.AppTheme
import com.lenerd.spotifyplus.manager.model.ManagerUiState

@Composable
fun SettingsScreen(
    state: ManagerUiState,
    onSetAutoCheckUpdates: (Boolean) -> Unit,
    onSetDebugLogging: (Boolean) -> Unit,
    onSetStartupCheck: (Boolean) -> Unit,
    onSetTheme: (AppTheme) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingSwitchRow(
                        title = "Auto-check for updates",
                        subtitle = "Check for updates when opening the app",
                        checked = state.autoCheckUpdates,
                        onCheckedChange = onSetAutoCheckUpdates
                    )

                    SettingSwitchRow(
                        title = "Debug logging",
                        subtitle = "Enable verbose logs for development and diagnostics",
                        checked = state.debugLogging,
                        onCheckedChange = onSetDebugLogging
                    )

                    SettingSwitchRow(
                        title = "Startup hook check",
                        subtitle = "Check whether SpotifyPlus is currently hooked on app open",
                        checked = state.startupCheck,
                        onCheckedChange = onSetStartupCheck
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .selectableGroup()
                ) {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.titleLarge
                    )

                    AppTheme.entries.forEach { theme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = state.theme == theme,
                                    onClick = { onSetTheme(theme) }
                                )
                                .padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.theme == theme,
                                onClick = { onSetTheme(theme) }
                            )
                            Text(
                                text = theme.name.lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}