plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lenerd.spotifyplus"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lenerd.spotifyplus"
        minSdk = 30
        //noinspection EditedTargetSdkVersion
        targetSdk = 36
        versionCode = 1
        versionName = "0.11.0.0"
        vectorDrawables.useSupportLibrary = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags("")
                arguments("-DANDROID_STL=c++_shared")
                targets("native-lib", "spotifyplus_bridge")
            }
        }

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("libnode/bin/", "$buildDir/generated/jniLibs")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

val copySpotifyPlusBridge by tasks.registering(Copy::class) {
    val abis = listOf("armeabi-v7a", "arm64-v8a", "x86_64")

    dependsOn("externalNativeBuildDebug")

    abis.forEach { abi ->
        from(fileTree("$projectDir/.cxx") {
            include("**/$abi/libspotifyplus_bridge.so")
        })
        into("$buildDir/generated/jniLibs/$abi")
    }
}

tasks.matching { it.name == "mergeDebugJniLibFolders" }.configureEach {
    dependsOn(copySpotifyPlusBridge)
}

val nodeAssetsSourceDir = layout.projectDirectory.dir("src/main/assets/nodejs-src")
val nodeAssetsOutputDir = layout.projectDirectory.dir("src/main/assets/nodejs")
val spotifyPlusBridgeAbis = listOf("armeabi-v7a", "arm64-v8a", "x86_64")

val syncSpotifyPlusNodeAddons by tasks.registering {
    group = "build"
    description = "Packages the current native SpotifyPlus Node bridge for each Android ABI."
    dependsOn("externalNativeBuildDebug")

    val bridgeOutputs = spotifyPlusBridgeAbis.map { abi ->
        layout.buildDirectory.file("intermediates/cmake/debug/obj/$abi/libspotifyplus_bridge.so")
    }
    val addonOutputs = spotifyPlusBridgeAbis.map { abi ->
        nodeAssetsOutputDir.file("addons/$abi/spotifyplus_bridge.node")
    }
    inputs.files(bridgeOutputs)
    outputs.files(addonOutputs)

    doLast {
        spotifyPlusBridgeAbis.forEach { abi ->
            val bridge = layout.buildDirectory
                .file("intermediates/cmake/debug/obj/$abi/libspotifyplus_bridge.so")
                .get()
                .asFile
            if (!bridge.isFile) {
                throw GradleException("Missing compiled SpotifyPlus Node bridge for $abi: $bridge")
            }

            val addon = nodeAssetsOutputDir
                .file("addons/$abi/spotifyplus_bridge.node")
                .asFile
            addon.parentFile.mkdirs()
            bridge.copyTo(addon, overwrite = true)
        }
    }
}

tasks.matching { it.name == "mergeDebugAssets" }.configureEach {
    dependsOn(syncSpotifyPlusNodeAddons)
}

val npmExecutable = if (System.getProperty("os.name").lowercase().contains("windows")) {
    "npm.cmd"
} else {
    "npm"
}

val installNodeAssetDependencies by tasks.registering(Exec::class) {
    group = "build"
    description = "Installs the locked Node asset build dependencies."
    workingDir(nodeAssetsSourceDir.asFile)
    commandLine(npmExecutable, "ci", "--ignore-scripts")

    inputs.file(nodeAssetsSourceDir.file("package.json"))
    inputs.file(nodeAssetsSourceDir.file("package-lock.json"))
    outputs.file(nodeAssetsSourceDir.file("node_modules/.package-lock.json"))
}

val buildNodeAssets by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds transformed SpotifyPlus Node runtime and SDK assets."
    dependsOn(installNodeAssetDependencies)
    workingDir(nodeAssetsSourceDir.asFile)
    commandLine(npmExecutable, "run", "build")

    inputs.files(fileTree(nodeAssetsSourceDir) {
        include("**/*.json")
        include("**/*.js")
        include("**/*.mjs")
        include("**/*.ts")
        include("**/*.tsx")
        exclude("dist/**")
        exclude("node_modules/**")
        exclude("sdk/components.ts")
        exclude("tools/tests/**")
    })
    outputs.dirs(
        nodeAssetsOutputDir.dir("bridge"),
        nodeAssetsOutputDir.dir("core"),
        nodeAssetsOutputDir.dir("loader"),
        nodeAssetsOutputDir.dir("scripts"),
        nodeAssetsOutputDir.dir("sdk"),
        nodeAssetsOutputDir.dir("ui"),
    )
    outputs.files(
        nodeAssetsOutputDir.file("host.d.ts"),
        nodeAssetsOutputDir.file("host.js"),
    )
}

val lyricsExtensionDir = rootProject.layout.projectDirectory.dir("scripts/lyrics")
val settingsExtensionDir = rootProject.layout.projectDirectory.dir("scripts/settings")
val marketplaceExtensionDir = rootProject.layout.projectDirectory.dir("scripts/marketplace")
val extensionCompilerInputs = files(
    nodeAssetsSourceDir.file("tools/cli-options.mjs"),
    nodeAssetsSourceDir.file("tools/dev-cli.mjs"),
    nodeAssetsSourceDir.file("tools/extension-build.mjs"),
    nodeAssetsSourceDir.file("tools/worklet-transform.mjs"),
)

val installLyricsExtensionDependencies by tasks.registering(Exec::class) {
    group = "build"
    description = "Installs the locked Beautiful Lyrics build dependencies."
    dependsOn(buildNodeAssets)
    workingDir(lyricsExtensionDir.asFile)
    commandLine(npmExecutable, "ci", "--ignore-scripts")

    inputs.file(lyricsExtensionDir.file("package.json"))
    inputs.file(lyricsExtensionDir.file("package-lock.json"))
    inputs.dir(nodeAssetsOutputDir.dir("sdk"))
    outputs.file(lyricsExtensionDir.file("node_modules/.package-lock.json"))
}

val buildLyricsExtension by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the transformed API-2 Beautiful Lyrics extension."
    dependsOn(installLyricsExtensionDependencies)
    workingDir(lyricsExtensionDir.asFile)
    commandLine(npmExecutable, "run", "build")

    inputs.files(fileTree(lyricsExtensionDir) {
        include("manifest.json")
        include("package.json")
        include("src/**/*.js")
        include("src/**/*.jsx")
        include("src/**/*.ts")
        include("src/**/*.tsx")
        include("tools/**/*.mjs")
        include("assets/**/*")
        include("src/native/**/*.java")
        include("src/native/**/*.kts")
        exclude("src/node_modules/**")
        exclude("node_modules/**")
    })
    inputs.files(extensionCompilerInputs)
    outputs.files(
        nodeAssetsOutputDir.file("scriptss/beautiful-lyrics/index.js"),
        nodeAssetsOutputDir.file("scriptss/beautiful-lyrics/manifest.json"),
        nodeAssetsOutputDir.file("scriptss/beautiful-lyrics/lyrics.apk"),
    )
    outputs.dir(nodeAssetsOutputDir.dir("scriptss/beautiful-lyrics/assets"))

    doLast {
        val bundle = nodeAssetsOutputDir.file("scriptss/beautiful-lyrics/index.js").asFile
        val marker = "globalThis.__spotifyplus_worklet_bundle__ = 2;"
        if (!bundle.readText().contains(marker)) {
            throw GradleException(
                "Beautiful Lyrics was built without the SpotifyPlus API-2 worklet transform."
            )
        }
    }
}

val installSettingsExtensionDependencies by tasks.registering(Exec::class) {
    group = "build"
    description = "Installs the locked Settings extension dependencies."
    dependsOn(buildNodeAssets)
    workingDir(settingsExtensionDir.asFile)
    commandLine(npmExecutable, "ci", "--ignore-scripts")
    inputs.file(settingsExtensionDir.file("package.json"))
    inputs.file(settingsExtensionDir.file("package-lock.json"))
    outputs.file(settingsExtensionDir.file("node_modules/.package-lock.json"))
}

val installMarketplaceExtensionDependencies by tasks.registering(Exec::class) {
    group = "build"
    description = "Installs the locked Marketplace extension dependencies."
    dependsOn(buildNodeAssets)
    workingDir(marketplaceExtensionDir.asFile)
    commandLine(npmExecutable, "ci", "--ignore-scripts")
    inputs.file(marketplaceExtensionDir.file("package.json"))
    inputs.file(marketplaceExtensionDir.file("package-lock.json"))
    outputs.file(marketplaceExtensionDir.file("node_modules/.package-lock.json"))
}

val buildSettingsExtension by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the transformed API-2 Settings extension."
    dependsOn(installSettingsExtensionDependencies)
    workingDir(settingsExtensionDir.asFile)
    commandLine(npmExecutable, "run", "build")

    inputs.files(fileTree(settingsExtensionDir) {
        include("manifest.json")
        include("package.json")
        include("src/**/*.js")
        include("src/**/*.jsx")
        include("src/**/*.ts")
        include("src/**/*.tsx")
        include("assets/**/*")
        exclude("node_modules/**")
    })
    inputs.files(extensionCompilerInputs)
    outputs.files(
        nodeAssetsOutputDir.file("elevated/settings/index.js"),
        nodeAssetsOutputDir.file("elevated/settings/manifest.json"),
    )
    if (settingsExtensionDir.dir("assets").asFile.exists()) {
        outputs.dir(nodeAssetsOutputDir.dir("elevated/settings/assets"))
    }
}

val buildMarketplaceExtension by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the transformed API-2 Marketplace extension."
    dependsOn(installMarketplaceExtensionDependencies)
    workingDir(marketplaceExtensionDir.asFile)
    commandLine(npmExecutable, "run", "build")

    inputs.files(fileTree(marketplaceExtensionDir) {
        include("manifest.json")
        include("package.json")
        include("src/**/*.js")
        include("src/**/*.jsx")
        include("src/**/*.ts")
        include("src/**/*.tsx")
        include("assets/**/*")
        exclude("node_modules/**")
    })
    inputs.files(extensionCompilerInputs)
    outputs.files(
        nodeAssetsOutputDir.file("elevated/marketplace/index.js"),
        nodeAssetsOutputDir.file("elevated/marketplace/manifest.json"),
    )
    if (marketplaceExtensionDir.dir("assets").asFile.exists()) {
        outputs.dir(nodeAssetsOutputDir.dir("elevated/marketplace/assets"))
    }
}

val buildBuiltInExtensions by tasks.registering {
    group = "build"
    description = "Builds every bundled SpotifyPlus extension through the worklet compiler."
    dependsOn(
        buildSettingsExtension,
        buildMarketplaceExtension,
    )
}

tasks.matching {
    it.name == "preBuild" || (it.name.startsWith("merge") && it.name.endsWith("Assets"))
}.configureEach {
    dependsOn(buildBuiltInExtensions)
}

//tasks.matching { it.name == "mergeReleaseJniLibFolders" }.configureEach {
//    dependsOn(copySpotifyPlusBridge)
//}

dependencies {

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.7")

    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    debugImplementation("androidx.compose.ui:ui-tooling")

    compileOnly(files("$rootDir/libxposed/api-100.aar"))
    implementation(files("$rootDir/libxposed/service-100.aar"))
    implementation(files("$rootDir/libxposed/interface-100.aar"))

    implementation("org.luckypray:dexkit:2.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.flexbox:flexbox:3.0.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.mikhaellopez:circleview:1.4.1")
    implementation("com.github.pemistahl:lingua:1.2.2")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.media3:media3-exoplayer:1.9.3")
    implementation("androidx.media3:media3-ui:1.9.3")
    implementation("androidx.media3:media3-exoplayer-hls:1.9.3")
    implementation("org.jsoup:jsoup:1.21.2")

    implementation("com.facebook.yoga:yoga:3.2.1")
    implementation("com.facebook.soloader:soloader:0.12.1")
    implementation("com.facebook.fbjni:fbjni-java-only:0.7.0")

    implementation(project(":spotifyplus-sdk"))

    compileOnly("de.robv.android.xposed:api:82")
}
