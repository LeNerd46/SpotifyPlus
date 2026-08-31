import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const testsDirectory = path.dirname(fileURLToPath(import.meta.url));
const nodeSourceRoot = path.resolve(testsDirectory, "../..");
const projectRoot = path.resolve(nodeSourceRoot, "../../../../..");
const hostRuntimeSource = fs.readFileSync(
    path.join(nodeSourceRoot, "loader/host-runtime.ts"),
    "utf8",
);
const scriptLoaderSource = fs.readFileSync(
    path.join(nodeSourceRoot, "loader/script-loader.ts"),
    "utf8",
);
const scriptApiSource = fs.readFileSync(
    path.join(nodeSourceRoot, "loader/script-api.ts"),
    "utf8",
);
const hostSource = fs.readFileSync(
    path.join(nodeSourceRoot, "host.ts"),
    "utf8",
);
const devCliSource = fs.readFileSync(
    path.join(nodeSourceRoot, "tools/dev-cli.mjs"),
    "utf8",
);
const scriptManagerSource = fs.readFileSync(
    path.join(
        projectRoot,
        "app/src/main/java/com/lenerd/spotifyplus/module/scripting/ScriptManager.java",
    ),
    "utf8",
);

test("elevated hot reload requires a debuggable Android build", () => {
    assert.match(
        scriptManagerSource,
        /hostConfig\.put\("allowElevatedHotReload", BuildConfig\.DEBUG\)/,
    );
    assert.match(
        hostRuntimeSource,
        /trust === 'elevated' && !this\.config\.allowElevatedHotReload/,
    );
});

test("hot reload preserves trust and waits before acknowledging success", () => {
    assert.match(
        hostRuntimeSource,
        /const manifest = this\.scriptLoader\.preflightSource\(/,
    );
    assert.match(
        hostRuntimeSource,
        /writeHotReloadAssets\(scriptDirectory, validatedBundle\)/,
    );
    assert.match(
        hostRuntimeSource,
        /loadScriptFromSource\(scriptDirectory, manifest, bundle\.source, false, trust\)/,
    );
    assert.match(hostRuntimeSource, /await this\.handleHotReload\(/);
    assert.doesNotMatch(hostRuntimeSource, /void this\.handleHotReload\(/);
});

test("developer mode exposes a live script console stream", () => {
    assert.match(hostRuntimeSource, /request\.method === 'GET' && request\.url === '\/dev-logs'/);
    assert.match(hostRuntimeSource, /'content-type': 'text\/event-stream; charset=utf-8'/);
    assert.match(hostRuntimeSource, /publishDevLog\(/);
    assert.match(hostRuntimeSource, /for \(const level of \['log', 'warn', 'error'\] as const\)/);
});

test("a failed reload retains the script directory and trust for the next rebuild", () => {
    assert.match(scriptLoaderSource, /private readonly scriptDirectories = new Map<string, string>\(\)/);
    assert.match(scriptLoaderSource, /this\.restoreScriptMetadata\(manifest\.id, previousDirectory, previousTrust\)/);
    assert.match(scriptLoaderSource, /const previousDirectory = this\.scriptDirectories\.get\(scriptId\)/);
});

test("dev retries an initial reload while the Spotify runtime is still starting", () => {
    assert.match(devCliSource, /connectionError\.runtimeUnavailable = true/);
    assert.match(devCliSource, /rebuild\("runtime connection retry"\)/);
});

test("marketplace installs use a persistent root separate from developer extensions", () => {
    assert.match(
        scriptManagerSource,
        /new File\(activity\.getFilesDir\(\), "spotifyplus-installed-extensions"\)/,
    );
    assert.match(
        scriptManagerSource,
        /hostConfig\.put\("installedRoot", installedScripts\.getAbsolutePath\(\)\)/,
    );
    assert.match(hostSource, /loader\.loadFromRoot\(config\.installedRoot, 'user'\)/);
    assert.match(
        hostRuntimeSource,
        /listInstalledExtensions\(\)[\s\S]*?const root = this\.config\.installedRoot/,
    );
    assert.match(scriptApiSource, /installExtension\(request: ExtensionInstallRequest\)/);
    assert.match(scriptApiSource, /uninstallExtension\(extensionId: string\)/);
    assert.match(scriptApiSource, /listInstalledExtensions\(\)/);
});

test("marketplace installation validates, swaps, and loads one extension", () => {
    assert.match(hostRuntimeSource, /writeInstallPackage\(staging, manifest, request\.manifest, request\.files\)/);
    assert.match(hostRuntimeSource, /fs\.renameSync\(target, backup\)/);
    assert.match(hostRuntimeSource, /fs\.renameSync\(staging, target\)/);
    assert.match(hostRuntimeSource, /this\.scriptLoader\.loadScript\(target, 'user'\)/);
    assert.match(hostRuntimeSource, /Extension package contains undeclared file/);
});

test("assets and native packages resolve beside the manifest main entry", () => {
    assert.match(scriptLoaderSource, /const entryDirectory = path\.dirname\(entryPath\)/);
    assert.match(
        scriptLoaderSource,
        /entryDirectory,\s+scriptDirectory,\s+manifest\.assets/,
    );
    assert.match(
        scriptLoaderSource,
        /path\.resolve\(entryDirectory, manifest\.native\.apk\)/,
    );
    assert.match(
        hostRuntimeSource,
        /path\.resolve\(root, path\.dirname\(bundle\.manifest\.main\)\)/,
    );
});

test("user extension React imports resolve to the host React runtime", () => {
    assert.match(scriptLoaderSource, /specifier === ['"]react['"]/);
    assert.match(scriptLoaderSource, /return React;/);
    assert.match(scriptLoaderSource, /specifier === ['"]react\/jsx-runtime['"]/);
    assert.match(scriptLoaderSource, /specifier === ['"]react\/jsx-dev-runtime['"]/);
    assert.match(scriptLoaderSource, /return hostRequire\(specifier\)/);
});

test("marketplace uninstallation removes only the installed package and restores a fallback", () => {
    assert.match(hostRuntimeSource, /uninstallExtension\(extensionId: string\)/);
    assert.match(hostRuntimeSource, /`\.uninstall-\$\{extensionId\}-\$\{randomUUID\(\)\}`/);
    assert.match(hostRuntimeSource, /this\.scriptLoader\.unloadFromRoot\(target\)/);
    assert.match(hostRuntimeSource, /this\.activateFallbackExtension\(extensionId\)/);
    assert.match(hostRuntimeSource, /findExtensionDirectory\(root, extensionId\)/);
});
