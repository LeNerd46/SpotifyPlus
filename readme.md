<div align="center">
  
# Spotify Plus
Spotify Plus is an Xposed module that adds beautiful lyrics to Spotify<br/><br/>
This project is still very early in development, many features are still not implemented yet

![GitHub Downloads](https://img.shields.io/github/downloads/LeNerd46/SpotifyPlus/total)
![Discord](https://img.shields.io/discord/1507540683429384233?link=https%3A%2F%2Fdiscord.gg%2FA98SNReGWD)
![Static Badge](https://img.shields.io/badge/Progress-gray?logo=notion&link=https%3A%2F%2Fsilly-existence-81d.notion.site%2FSpotify-Plus-a14130b941f1826185b60104b1cc6471)

</div>

Xposed module to modify your Spotify app on your Android phone.

Extension developers can bundle scoped images, fonts, audio, data, and other files. See [Extension assets](scripts/ASSETS.md) for manifest declarations, runtime APIs, font families, and hot reload behavior.

> [!NOTE]
> The latest recommended version of Spotify to use is v9.1.28.2252. Spotify Plus is not guaranteed to work past this version

## Building locally

Use JDK 21 and the checked-in Gradle wrapper. Set `JAVA_HOME` to the JDK directory and select that same JDK under IntelliJ's **Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JVM**. On Windows ARM, the Microsoft OpenJDK 21 **x64** build works with this project's Android build tools. Do not rely on an SDK name such as `jbr-21`: an IDE update can replace the runtime at that path with a different Java version.

Install Android SDK Platform 36, NDK 27.0.12077973, and CMake 3.22.1, and set `sdk.dir` in your local `local.properties`. Node.js and npm must also be on `PATH`; Gradle installs the locked dependencies for the runtime and bundled extensions.

The native Node libraries are not tracked by Git. Download `nodejs-mobile-v18.20.4-android.zip` from the [Node.js Mobile release](https://github.com/nodejs-mobile/nodejs-mobile/releases/tag/v18.20.4) and copy its `bin` directory into `app/libnode`. There must be a `libnode.so` in each of `bin/arm64-v8a`, `bin/armeabi-v7a`, and `bin/x86_64`.

From the repository root on Windows:

```powershell
.\gradlew.bat build
```

For only a debug APK, use `.\gradlew.bat assembleDebug`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Resources and Feedback
Join the Spotify Plus community! We have a Telegram channel where you can get updates, discuss the module, and give feedback and discuss directly. You can join [here](https://t.me/spotifypluscool)
