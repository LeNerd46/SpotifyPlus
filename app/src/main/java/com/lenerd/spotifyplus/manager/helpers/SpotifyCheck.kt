package com.lenerd.spotifyplus.manager.util

import android.content.Context
import android.content.pm.PackageManager

object SpotifyCheck {
    data class Result(
        val installed: Boolean,
        val versionName: String?
    )

    fun check(context: Context, packageName: String = "com.spotify.music"): Result {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            Result(
                installed = true,
                versionName = packageInfo.versionName
            )
        } catch (e: PackageManager.NameNotFoundException) {
            Result(
                installed = false,
                versionName = null
            )
        }
    }
}