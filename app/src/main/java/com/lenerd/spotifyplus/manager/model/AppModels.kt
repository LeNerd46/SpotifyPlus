package com.lenerd.spotifyplus.manager.model

import com.lenerd.spotifyplus.BuildConfig

enum class HookStatus {
    HOOKED,
    NOT_HOOKED,
    PARTIAL
}

enum class AppTheme {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED
}

data class HookItem(
    val id: String,
    val name: String,
    val description: String,
    val enabled: Boolean,
    val isScript: Boolean = false
)

data class UpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val updateAvailable: Boolean,
    val changelog: List<String>
)

data class ManagerUiState(
    val packageName: String = "com.spotify.music",
    val moduleEnabled: Boolean = true,
    val hookStatus: HookStatus = HookStatus.NOT_HOOKED,
    val lastCheckedStatus: String = "Never",
    val autoCheckUpdates: Boolean = true,
    val debugLogging: Boolean = false,
    val startupCheck: Boolean = true,
    val theme: AppTheme = AppTheme.AMOLED,
    val generalSettings: List<HookItem> = emptyList(),
    val updateInfo: UpdateInfo = UpdateInfo(
        currentVersion = BuildConfig.VERSION_NAME,
        latestVersion = "0.6.1",
        updateAvailable = false,
        changelog = emptyList()
    ),
    val statusMessage: String = "Waiting for status check...",
    val isSpotifyInstalled: Boolean = true,
    val spotifyVersionName: String = "9.1.28.2522"
)