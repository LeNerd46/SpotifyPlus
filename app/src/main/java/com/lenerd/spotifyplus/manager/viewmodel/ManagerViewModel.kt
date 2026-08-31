package com.lenerd.spotifyplus.manager.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lenerd.spotifyplus.BuildConfig
import com.lenerd.spotifyplus.manager.model.*
import okhttp3.*
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.util.regex.Pattern
import kotlin.math.max


class ManagerViewModel : ViewModel() {
    val LEADING_NUMBER: Pattern = Pattern.compile("^(\\d+)")

    var uiState by mutableStateOf(
        ManagerUiState(
            generalSettings = listOf(
                HookItem(
                    id = "lastfmUsername",
                    name = "Beautiful Lyrics",
                    description = "Enhanced synced lyrics overlay and animations",
                    enabled = true
                ),
                HookItem(
                    id = "theme",
                    name = "Theme Hook",
                    description = "Overrides Spotify colors and AMOLED styling",
                    enabled = true
                ),
                HookItem(
                    id = "translation",
                    name = "Lyrics Translation",
                    description = "Translates current lyrics through external logic",
                    enabled = false
                ),
                HookItem(
                    id = "background",
                    name = "Animated Background",
                    description = "Dynamic blurred/art-driven visual background",
                    enabled = true
                ),
                HookItem(
                    id = "script_adblock",
                    name = "Request Blocking Script",
                    description = "Blocks selected network endpoints",
                    enabled = false,
                    isScript = true
                ),
                HookItem(
                    id = "script_dev",
                    name = "Debug Script",
                    description = "Extra logging and experimental diagnostics",
                    enabled = false,
                    isScript = true
                )
            )
        )
    )
        private set

    fun refreshStatus() {
        val enabledHooks = uiState.generalSettings.count { it.enabled }

        val status = when {
            !uiState.moduleEnabled -> HookStatus.NOT_HOOKED
            enabledHooks == 0 -> HookStatus.NOT_HOOKED
            enabledHooks < uiState.generalSettings.size -> HookStatus.PARTIAL
            else -> HookStatus.HOOKED
        }

        uiState = uiState.copy(
            hookStatus = status,
            lastCheckedStatus = "Just now",
            statusMessage = when (status) {
                HookStatus.HOOKED -> "SpotifyPlus is active and all hooks are enabled."
                HookStatus.PARTIAL -> "SpotifyPlus is partially active. Some hooks/scripts are disabled."
                HookStatus.NOT_HOOKED -> "SpotifyPlus is not fully active or no hooks are enabled."
            }
        )
    }

    fun checkForUpdates() {
        val client = OkHttpClient()
        val request: Request =
            Request.Builder().url("https://api.github.com/repos/lenerd46/spotifyplus/releases/latest").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) return;

                val content = response.body!!.string()
                if (content.isEmpty()) return

                val json: JsonObject = JsonParser.parseString(content).getAsJsonObject()
                val latest = json.get("tag_name").getAsString().replace("v", "")

                val l = latest.substring(1)
                val c = BuildConfig.VERSION_NAME

                var updateAvailable = false

                val la: Array<String?> = l.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val ca: Array<String?> = c.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

                val len = max(la.size, ca.size)
                for (i in 0..<len) {
                    val lv = if (i < la.size) parseSegment(la[i]) else 0L
                    val cv = if (i < ca.size) parseSegment(ca[i]) else 0L

                    if (lv > cv) {
                        updateAvailable = true
                        break
                    } else if (lv < cv) {
                        break
                    }
                }

                uiState = if (updateAvailable) {
                    uiState.copy(
                        updateInfo = UpdateInfo(
                            currentVersion = BuildConfig.VERSION_NAME,
                            latestVersion = latest,
                            updateAvailable = true,
                            changelog = listOf("Hello")
                        )
                    )
                } else {
                    uiState.copy(
                        updateInfo = UpdateInfo(
                            currentVersion = BuildConfig.VERSION_NAME,
                            latestVersion = BuildConfig.VERSION_NAME,
                            updateAvailable = false,
                            changelog = emptyList()
                        )
                    )
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                uiState = uiState.copy(
                    updateInfo = UpdateInfo(
                        currentVersion = BuildConfig.VERSION_NAME,
                        latestVersion = BuildConfig.VERSION_NAME,
                        updateAvailable = false,
                        changelog = emptyList()
                    )
                )
            }
        })

        uiState = uiState.copy(
            updateInfo = UpdateInfo(
                currentVersion = "1.0.0",
                latestVersion = "1.1.0",
                updateAvailable = true,
                changelog = listOf(
                    "Added manager dashboard",
                    "Improved hook status detection",
                    "Added script toggles",
                    "Improved AMOLED theme support"
                )
            ),
            statusMessage = "Checked for updates."
        )
    }

    private fun parseSegment(seg: String?): Long {
        var seg = seg
        seg = seg?.trim { it <= ' ' }
        val m = LEADING_NUMBER.matcher(seg)
        if (m.find()) {
            try {
                return m.group(1).toLong()
            } catch (e: NumberFormatException) {
                // extremely large number; fallback
                return 0L
            }
        }
        return 0L
    }

    fun installUpdate() {
        uiState = uiState.copy(
            updateInfo = uiState.updateInfo.copy(
                currentVersion = uiState.updateInfo.latestVersion,
                updateAvailable = false
            ),
            statusMessage = "Update installed successfully."
        )
    }

    fun toggleHook(id: String) {
        uiState = uiState.copy(
            generalSettings = uiState.generalSettings.map {
                if (!it.isScript && it.id == id) it.copy(enabled = !it.enabled) else it
            }
        )
        refreshStatus()
    }

    fun toggleScript(id: String) {
        uiState = uiState.copy(
            generalSettings = uiState.generalSettings.map {
                if (it.isScript && it.id == id) it.copy(enabled = !it.enabled) else it
            }
        )
        refreshStatus()
    }

    fun enableAllHooks() {
        uiState = uiState.copy(
            generalSettings = uiState.generalSettings.map { it.copy(enabled = true) }
        )
        refreshStatus()
    }

    fun disableAllHooks() {
        uiState = uiState.copy(
            generalSettings = uiState.generalSettings.map { it.copy(enabled = false) }
        )
        refreshStatus()
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        uiState = uiState.copy(autoCheckUpdates = enabled)
    }

    fun setDebugLogging(enabled: Boolean) {
        uiState = uiState.copy(debugLogging = enabled)
    }

    fun setStartupCheck(enabled: Boolean) {
        uiState = uiState.copy(startupCheck = enabled)
    }

    fun setTheme(theme: AppTheme) {
        uiState = uiState.copy(theme = theme)
    }

    fun openGithub(context: Context) {
        context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/LeNerd46/SpotifyPlus/releases".toUri()))
    }

    fun setSpotifyInstalled(installed: Boolean, versionName: String?) {
        uiState = uiState.copy(isSpotifyInstalled = installed, spotifyVersionName = versionName ?: "Not installed")
    }

    fun nodeTest() {
        @SuppressLint("StaticFieldLeak")
        object : AsyncTask<Void?, Void?, String?>() {
            override fun doInBackground(vararg params: Void?): String {
                return try {
                    val localNodeServer = URL("http://127.0.0.1:3000/")
                    BufferedReader(InputStreamReader(localNodeServer.openStream())).use { it.readText() }
                } catch (ex: Exception) {
                    ex.stackTraceToString()
                }
            }

            override fun onPostExecute(result: String?) {
                if (result != null) {
                    uiState = uiState.copy(
                        updateInfo = UpdateInfo(
                            currentVersion = result,
                            latestVersion = "Success!",
                            updateAvailable = true,
                            changelog = emptyList()
                        )
                    )
                } else {
                    uiState = uiState.copy(
                        updateInfo = UpdateInfo(
                            currentVersion = "Result was null",
                            latestVersion = "Not really a Success!",
                            updateAvailable = true,
                            changelog = emptyList()
                        )
                    )
                }
            }
        }.execute()
    }
}