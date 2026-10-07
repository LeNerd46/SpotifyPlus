import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

export const NATIVE_SDK_URL = "https://github.com/LeNerd46/SpotifyPlus/releases/download/v0.10-preview-1/spotifyplus-sdk.aar";

// Both languages share the namespace, so avoid identifiers reserved by either one.
const RESERVED_WORDS = new Set((
    "abstract assert boolean break byte case catch char class const continue default do double else enum " +
    "extends final finally float for goto if implements import instanceof int interface long native new " +
    "package private protected public return short static strictfp super switch synchronized this throw " +
    "throws transient try void volatile while true false null _ as fun in is object typealias typeof val var when"
).split(" "));

export function validateNamespace(value) {
    const parts = String(value).trim().split(".");
    return parts.length >= 2 && parts.every(part => /^[A-Za-z][A-Za-z0-9_]*$/.test(part) && !RESERVED_WORDS.has(part))
        || "Use a namespace such as com.example.myplugin, with valid Java/Kotlin identifiers.";
}

async function validateProjectDirectory(directory) {
    if (!String(directory).trim()) return "Enter a new project directory.";
    try {
        await fs.lstat(path.resolve(directory));
        return "That path already exists. Choose a new project directory.";
    } catch (error) {
        if (error.code === "ENOENT") return true;
        throw error;
    }
}

function javaSources(namespace) {
    return {
        "NativePlugin.java": `package ${namespace};

import com.lenerd.spotifyplus.sdk.SpotifyPlusPlugin;
import com.lenerd.spotifyplus.sdk.SpotifyPlusRegistry;
import com.lenerd.spotifyplus.sdk.spotify.SpotifyPlusContext;

public class NativePlugin implements SpotifyPlusPlugin {
    @Override
    public void register(SpotifyPlusRegistry registry, SpotifyPlusContext context) {
        registry.registerComponent(new ExampleComponent());
    }
}
`,
        "ExampleComponent.java": `package ${namespace};

import android.content.Context;
import com.lenerd.spotifyplus.sdk.SpotifyPlusComponent;
import com.lenerd.spotifyplus.sdk.spotify.SpotifyPlusContext;
import org.json.JSONObject;

public class ExampleComponent extends SpotifyPlusComponent<ExampleView> {
    @Override
    public String getName() {
        return "ExampleComponent";
    }

    @Override
    public ExampleView createView(Context context, SpotifyPlusContext spotifyPlusContext) {
        return new ExampleView(context);
    }

    @Override
    public void updateProps(ExampleView view, JSONObject oldProps, JSONObject newProps) {
        if (newProps.has("text")) {
            view.setText(String.valueOf(newProps.opt("text")));
        }
    }
}
`,
        "ExampleView.java": `package ${namespace};

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.TextView;

@SuppressLint("AppCompatCustomView")
public class ExampleView extends TextView {
    public ExampleView(Context context) {
        super(context);
    }

    public ExampleView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ExampleView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }
}
`,
    };
}

function kotlinSources(namespace) {
    return {
        "NativePlugin.kt": `package ${namespace}

import com.lenerd.spotifyplus.sdk.SpotifyPlusPlugin
import com.lenerd.spotifyplus.sdk.SpotifyPlusRegistry
import com.lenerd.spotifyplus.sdk.spotify.SpotifyPlusContext

class NativePlugin : SpotifyPlusPlugin {
    override fun register(registry: SpotifyPlusRegistry, context: SpotifyPlusContext) {
        registry.registerComponent(ExampleComponent())
    }
}
`,
        "ExampleComponent.kt": `package ${namespace}

import android.content.Context
import com.lenerd.spotifyplus.sdk.SpotifyPlusComponent
import com.lenerd.spotifyplus.sdk.spotify.SpotifyPlusContext
import org.json.JSONObject

class ExampleComponent : SpotifyPlusComponent<ExampleView>() {
    override fun getName(): String = "ExampleComponent"

    override fun createView(context: Context, spotifyPlusContext: SpotifyPlusContext): ExampleView {
        return ExampleView(context)
    }

    override fun updateProps(view: ExampleView, oldProps: JSONObject, newProps: JSONObject) {
        if (newProps.has("text")) {
            view.text = newProps.opt("text")?.toString() ?: "null"
        }
    }
}
`,
        "ExampleView.kt": `package ${namespace}

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.TextView

@SuppressLint("AppCompatCustomView")
class ExampleView : TextView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }
}
`,
    };
}

function projectFiles(language, namespace, projectName) {
    const kotlin = language === "kotlin";
    const files = {
        "settings.gradle.kts": `pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = ${JSON.stringify(projectName).replaceAll("$", "\\$")}
include(":app")
`,
        "build.gradle.kts": `plugins {
    id("com.android.application") version "8.10.0" apply false
${kotlin ? '    id("org.jetbrains.kotlin.android") version "2.1.20" apply false\n' : ""}}
`,
        "app/build.gradle.kts": `plugins {
    id("com.android.application")
${kotlin ? '    id("org.jetbrains.kotlin.android")\n' : ""}}

android {
    namespace = "${namespace}"
    compileSdk = 35

    defaultConfig {
        applicationId = "${namespace}"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
${kotlin ? '\n    kotlinOptions {\n        jvmTarget = "21"\n    }\n' : ""}}

dependencies {
    compileOnly(files("$rootDir/lib/spotifyplus-sdk.aar"))
}
`,
        "app/src/main/AndroidManifest.xml": `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="Native Plugin" android:allowBackup="false" />
</manifest>
`,
        "gradle.properties": "org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8\nandroid.useAndroidX=true\n",
        "gradle/wrapper/gradle-wrapper.properties": `distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
`,
        ".gitignore": ".gradle/\n.idea/\nlocal.properties\n**/build/\n*.iml\n",
        "README.md": `# Native SpotifyPlus plugin

This ${kotlin ? "Kotlin" : "Java"} Android project contains NativePlugin, ExampleComponent, and ExampleView. It has no activity.

Open this directory in Android Studio, use JDK 21, and install Android SDK Platform 35. Set your SDK location in local.properties (sdk.dir) or ANDROID_HOME.

Build the plugin APK:

\`\`\`sh
./gradlew :app:assembleDebug
\`\`\`

On Windows, use \`gradlew.bat :app:assembleDebug\`.
The APK is written to \`app/build/outputs/apk/debug/app-debug.apk\`.

Copy the APK into your SpotifyPlus extension and add this to its manifest.json:

\`\`\`json
"native": {
    "apk": "native.apk",
    "pluginClass": "${namespace}.NativePlugin"
}
\`\`\`

Name the copied APK \`native.apk\`, or update the manifest path to match.
The SDK in \`lib/spotifyplus-sdk.aar\` is a compile-only dependency; SpotifyPlus provides it at runtime.
`,
    };
    for (const [name, source] of Object.entries(kotlin ? kotlinSources(namespace) : javaSources(namespace))) {
        files[`app/src/main/java/${namespace.replaceAll(".", "/")}/${name}`] = source;
    }
    return files;
}

async function downloadSdk(fetchImpl) {
    try {
        const response = await fetchImpl(NATIVE_SDK_URL, { signal: AbortSignal.timeout(30_000) });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const bytes = Buffer.from(await response.arrayBuffer());
        if (bytes.length < 4 || !bytes.subarray(0, 4).equals(Buffer.from([0x50, 0x4b, 0x03, 0x04]))) {
            throw new Error("The response is not an AAR/ZIP archive");
        }
        return bytes;
    } catch (error) {
        throw new Error(`Could not download the SpotifyPlus SDK from ${NATIVE_SDK_URL}: ${error.message}`);
    }
}

export async function createNativeProject({ projectDir, language, namespace }, { fetchImpl = globalThis.fetch } = {}) {
    if (!["java", "kotlin"].includes(language)) throw new Error("Choose Java or Kotlin.");
    namespace = String(namespace).trim();
    const validation = validateNamespace(namespace);
    if (validation !== true) throw new Error(validation);
    if (!projectDir) throw new Error("Enter a new project directory.");
    projectDir = path.resolve(projectDir);
    const directoryValidation = await validateProjectDirectory(projectDir);
    if (directoryValidation !== true) throw new Error(directoryValidation);

    const templateDir = path.join(
        typeof __dirname === "string" ? __dirname : path.dirname(fileURLToPath(import.meta.url)),
        "native-template",
    );
    const files = projectFiles(language, namespace, path.basename(projectDir));
    for (const name of ["gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar"]) {
        files[name] = await fs.readFile(path.join(templateDir, name));
    }
    files["lib/spotifyplus-sdk.aar"] = await downloadSdk(fetchImpl);

    const parentDir = path.dirname(projectDir);
    await fs.mkdir(parentDir, { recursive: true });
    const stagingDir = await fs.mkdtemp(path.join(parentDir, ".spotifyplus-native-"));
    try {
        for (const [name, contents] of Object.entries(files)) {
            const destination = path.join(stagingDir, name);
            await fs.mkdir(path.dirname(destination), { recursive: true });
            await fs.writeFile(destination, contents);
        }
        await fs.chmod(path.join(stagingDir, "gradlew"), 0o755);
        // Check again after the download before publishing the staged project.
        const finalValidation = await validateProjectDirectory(projectDir);
        if (finalValidation !== true) throw new Error(finalValidation);
        await fs.rename(stagingDir, projectDir);
    } finally {
        await fs.rm(stagingDir, { recursive: true, force: true });
    }
    return projectDir;
}

export async function runCreateNative(options, { prompt, fetchImpl, log = console.log } = {}) {
    if (!prompt) {
        const { default: inquirer } = await import("inquirer");
        prompt = questions => inquirer.prompt(questions);
    }
    const answers = await prompt([
        {
            type: "list",
            name: "language",
            message: "Which language would you like to use?",
            choices: [{ name: "Java", value: "java" }, { name: "Kotlin", value: "kotlin" }],
        },
        {
            type: "input",
            name: "namespace",
            message: "What should the Android namespace be?",
            default: "com.example.myplugin",
            filter: value => value.trim(),
            validate: validateNamespace,
        },
        ...options.projectDir ? [] : [{
            type: "input",
            name: "projectDir",
            message: "Where should the project be created?",
            default: "native",
            filter: value => value.trim(),
            validate: validateProjectDirectory,
        }],
    ]);
    log("[spotifyplus] downloading the native SDK and creating the project...");
    const projectDir = await createNativeProject({
        ...answers,
        projectDir: options.projectDir ?? answers.projectDir,
    }, { fetchImpl });
    log(`[spotifyplus] created ${answers.language} native plugin in ${projectDir}`);
    log(`[spotifyplus] open it in Android Studio with JDK 21, or run ${process.platform === "win32" ? "gradlew.bat" : "./gradlew"} :app:assembleDebug from that directory`);
    return projectDir;
}
