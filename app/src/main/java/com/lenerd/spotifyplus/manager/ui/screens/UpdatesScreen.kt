package com.lenerd.spotifyplus.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lenerd.spotifyplus.manager.model.ManagerUiState

@Composable
fun UpdatesScreen(
    state: ManagerUiState,
    onCheckForUpdates: () -> Unit,
    onInstallUpdate: () -> Unit
) {
    val update = state.updateInfo

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Updates",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Current Version: ${update.currentVersion}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Latest Version: ${update.latestVersion}",
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = if (update.updateAvailable) "Update Available" else "You're up to date",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Button(
                        onClick = onCheckForUpdates,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Text("Check Again")
                    }

                    if (update.updateAvailable) {
                        Button(
                            onClick = onInstallUpdate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Text("Install Update")
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Changelog",
                        style = MaterialTheme.typography.titleLarge
                    )

                    if (update.changelog.isEmpty()) {
                        Text(
                            text = "No changelog available.",
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        update.changelog.forEachIndexed { index, line ->
                            Text(
                                text = "${index + 1}. $line",
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}