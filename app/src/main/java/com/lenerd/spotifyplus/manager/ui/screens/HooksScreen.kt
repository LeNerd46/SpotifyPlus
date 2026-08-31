package com.lenerd.spotifyplus.manager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lenerd.spotifyplus.manager.model.ManagerUiState
import com.lenerd.spotifyplus.manager.ui.components.HookItemCard

@Composable
fun HooksScreen(
    state: ManagerUiState,
    onToggleHook: (String) -> Unit,
    onToggleScript: (String) -> Unit,
    onEnableAllHooks: () -> Unit,
    onDisableAllHooks: () -> Unit
) {
    val normalHooks = state.generalSettings.filter { !it.isScript }
    val scripts = state.generalSettings.filter { it.isScript }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Hooks & Scripts",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Button(onClick = onEnableAllHooks) {
                Text("Enable All")
            }
        }

        item {
            OutlinedButton(onClick = onDisableAllHooks) {
                Text("Disable All")
            }
        }

        item {
            Text(
                text = "Hooks",
                style = MaterialTheme.typography.titleLarge
            )
        }

        items(normalHooks, key = { it.id }) { hook ->
            HookItemCard(
                item = hook,
                onToggle = onToggleHook
            )
        }

        item {
            Text(
                text = "Scripts",
                style = MaterialTheme.typography.titleLarge
            )
        }

        items(scripts, key = { it.id }) { script ->
            HookItemCard(
                item = script,
                onToggle = onToggleScript
            )
        }
    }
}