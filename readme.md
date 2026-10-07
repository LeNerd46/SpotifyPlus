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

Create a native extension project with `npx spotifyplus create-native` (or `npx spotifyplus create-native my-plugin`). The Inquirer prompts ask for Java or Kotlin, an Android namespace, and a new project directory. The project includes a Gradle wrapper, a compile-only SpotifyPlus SDK in `lib`, and sample `NativePlugin`, `ExampleComponent`, and `ExampleView` classes, with no activity. Open it in Android Studio using JDK 21 and Android SDK Platform 35, then build with `./gradlew :app:assembleDebug` (`gradlew.bat :app:assembleDebug` on Windows). Its README explains how to attach the APK to your extension manifest.

`spotifyplus dev` sends the compiled `manifest.native.apk` with every JavaScript reload and registers the native plugin before executing the new bundle. Native source compilation is separate: rebuild your Java/Kotlin project and copy the resulting APK to the declared path in the extension project root. Dev watches that APK and sends it automatically, with no Spotify restart. The phone places the APK and assets beside `manifest.main` (including `dist/index.js`); Android loads each APK revision from an immutable snapshot using a fresh class loader. Both the phone's SpotifyPlus runtime and the development SDK must support native hot reload; the CLI checks compatibility before sending a native extension. APKs are limited to 32 MB, and declared assets to 20 MB. This reloads SDK plugins that register components; process-wide hooks or manually loaded `.so` libraries may still require a restart.

> [!NOTE]
> The latest recommended version of Spotify to use is v9.1.82.2160. Spotify Plus is not guaranteed to work past this version

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

See [the modern API reference](docs/modern-api.md) for the 9.1.82.2160 extension APIs, events, and device validation checklist.
