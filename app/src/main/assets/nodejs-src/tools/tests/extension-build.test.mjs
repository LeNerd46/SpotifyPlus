import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";
import { bundleExtension, readDeclaredAssets } from "../extension-build.mjs";

const execFileAsync = promisify(execFile);
const testsDir = path.dirname(fileURLToPath(import.meta.url));
const fixturesDir = path.join(testsDir, "fixtures");
const toolsDir = path.resolve(testsDir, "..");

test("API 2 bundles are stamped and transformed", async () => {
    const scriptDir = path.join(fixturesDir, "api2");
    const result = await bundleExtension({
        scriptDir,
        write: false,
    });

    assert.equal(result.api, 2);
    assert.match(result.source, /globalThis\.__spotifyplus_worklet_bundle__\s*=\s*2/);
    assert.match(result.source, /__spotifyPlusWorklet/);
    assert.match(result.source, /__spotifyPlusShareable:\s*"workletGlobal"/);
    assert.match(result.source, /require\("spotifyplus\/react\/reanimated"\)/);
});

test("API 1 bundles remain intentionally untransformed", async () => {
    const scriptDir = path.join(fixturesDir, "api1");
    const result = await bundleExtension({
        scriptDir,
        write: false,
    });

    assert.equal(result.api, 1);
    assert.doesNotMatch(result.source, /__spotifyplus_worklet_bundle__/);
    assert.doesNotMatch(result.source, /__spotifyPlusWorklet/);
});

test("user extension builds reject privileged Node modules", async t => {
    const temporaryDir = await fs.mkdtemp(path.join(os.tmpdir(), "spotifyplus-permissions-"));
    t.after(() => fs.rm(temporaryDir, { force: true, recursive: true }));
    await fs.writeFile(path.join(temporaryDir, "manifest.json"), JSON.stringify({
        id: "tools.permissions",
        name: "Permissions fixture",
        version: "1.0.0",
        main: "dist/index.js",
        api: 2,
    }));
    await fs.mkdir(path.join(temporaryDir, "src"));
    await fs.writeFile(path.join(temporaryDir, "src", "index.ts"), "import 'node:net';\n");

    await assert.rejects(
        bundleExtension({
            scriptDir: temporaryDir,
            write: false,
        }),
        /User extensions cannot import privileged Node module 'node:net'/,
    );
});

test("spotifyplus build writes through the shared builder", async t => {
    const temporaryDir = await fs.mkdtemp(path.join(os.tmpdir(), "spotifyplus-build-"));
    t.after(() => fs.rm(temporaryDir, { force: true, recursive: true }));
    const outfile = path.join(temporaryDir, "index.js");
    const cliPath = path.join(toolsDir, "dev-cli.mjs");
    const scriptDir = path.join(fixturesDir, "api2");

    const { stdout } = await execFileAsync(process.execPath, [
        cliPath,
        "build",
        scriptDir,
        "--outfile",
        outfile,
    ]);
    const output = await fs.readFile(outfile, "utf8");
    const emittedManifest = JSON.parse(await fs.readFile(path.join(temporaryDir, "manifest.json"), "utf8"));

    assert.match(stdout, /built tools\.api2/);
    assert.match(output, /__spotifyplus_worklet_bundle__/);
    assert.match(output, /__spotifyPlusWorklet/);
    assert.equal(emittedManifest.api, 2);
    assert.equal(emittedManifest.id, "tools.api2");
});

test("declared extension assets are copied beside the bundle", async t => {
    const temporaryDir = await fs.mkdtemp(path.join(os.tmpdir(), "spotifyplus-assets-"));
    t.after(() => fs.rm(temporaryDir, { force: true, recursive: true }));
    await fs.mkdir(path.join(temporaryDir, "src"));
    await fs.mkdir(path.join(temporaryDir, "assets", "fonts"), { recursive: true });
    await fs.writeFile(path.join(temporaryDir, "src", "index.ts"), "export default {};");
    await fs.writeFile(path.join(temporaryDir, "assets", "fonts", "example.ttf"), "font-data");
    await fs.writeFile(path.join(temporaryDir, "manifest.json"), JSON.stringify({
        id: "tools.assets",
        name: "Assets fixture",
        version: "1.0.0",
        main: "index.js",
        api: 2,
        assets: ["assets/**/*.ttf"],
    }));

    const outfile = path.join(temporaryDir, "output", "index.js");
    const result = await bundleExtension({ scriptDir: temporaryDir, outfile });

    assert.deepEqual(result.assetOutfiles, [path.join(temporaryDir, "output", "assets", "fonts", "example.ttf")]);
    assert.equal(
        await fs.readFile(path.join(temporaryDir, "output", "assets", "fonts", "example.ttf"), "utf8"),
        "font-data",
    );
    const hotReloadAssets = await readDeclaredAssets(temporaryDir, result.manifest);
    assert.deepEqual(hotReloadAssets, [{
        path: "assets/fonts/example.ttf",
        data: Buffer.from("font-data").toString("base64"),
        size: 9,
    }]);
});

test("asset declarations cannot escape the extension or silently match nothing", async t => {
    const temporaryDir = await fs.mkdtemp(path.join(os.tmpdir(), "spotifyplus-assets-invalid-"));
    t.after(() => fs.rm(temporaryDir, { force: true, recursive: true }));
    await fs.mkdir(path.join(temporaryDir, "src"));
    await fs.writeFile(path.join(temporaryDir, "src", "index.ts"), "export default {};");

    const manifest = {
        id: "tools.assets-invalid",
        name: "Invalid assets fixture",
        version: "1.0.0",
        main: "index.js",
        api: 2,
        assets: ["../secret.ttf"],
    };
    await fs.writeFile(path.join(temporaryDir, "manifest.json"), JSON.stringify(manifest));
    await assert.rejects(
        bundleExtension({ scriptDir: temporaryDir, outfile: path.join(temporaryDir, "output", "index.js") }),
        /cannot escape the extension directory/,
    );

    manifest.assets = ["assets/**/*.ttf"];
    await fs.writeFile(path.join(temporaryDir, "manifest.json"), JSON.stringify(manifest));
    await assert.rejects(
        bundleExtension({ scriptDir: temporaryDir, outfile: path.join(temporaryDir, "output", "index.js") }),
        /patterns matched no files/,
    );
});

test("native extension packages are copied beside the bundle", async t => {
    const temporaryDir = await fs.mkdtemp(path.join(os.tmpdir(), "spotifyplus-native-"));
    t.after(() => fs.rm(temporaryDir, { force: true, recursive: true }));
    await fs.mkdir(path.join(temporaryDir, "src"));
    await fs.writeFile(path.join(temporaryDir, "src", "index.ts"), "export default {};");
    await fs.writeFile(path.join(temporaryDir, "extension.apk"), "dex-data");
    await fs.writeFile(path.join(temporaryDir, "manifest.json"), JSON.stringify({
        id: "tools.native",
        name: "Native fixture",
        version: "1.0.0",
        main: "index.js",
        api: 2,
        native: {
            apk: "extension.apk",
            pluginClass: "tools.native.Plugin",
        },
    }));

    const outfile = path.join(temporaryDir, "output", "index.js");
    const result = await bundleExtension({ scriptDir: temporaryDir, outfile });

    assert.equal(result.nativeOutfile, path.join(temporaryDir, "output", "extension.apk"));
    assert.equal(
        await fs.readFile(path.join(temporaryDir, "output", "extension.apk"), "utf8"),
        "dex-data",
    );
});

test("native extension package paths cannot escape or be missing", async t => {
    const temporaryDir = await fs.mkdtemp(path.join(os.tmpdir(), "spotifyplus-native-invalid-"));
    t.after(() => fs.rm(temporaryDir, { force: true, recursive: true }));
    await fs.mkdir(path.join(temporaryDir, "src"));
    await fs.writeFile(path.join(temporaryDir, "src", "index.ts"), "export default {};");

    const manifest = {
        id: "tools.native-invalid",
        name: "Invalid native fixture",
        version: "1.0.0",
        main: "index.js",
        api: 2,
        native: {
            apk: "../extension.apk",
            pluginClass: "tools.native.Plugin",
        },
    };
    await fs.writeFile(path.join(temporaryDir, "manifest.json"), JSON.stringify(manifest));
    await assert.rejects(
        bundleExtension({ scriptDir: temporaryDir, outfile: path.join(temporaryDir, "output", "index.js") }),
        /cannot escape the extension directory/,
    );

    manifest.native.apk = "missing.apk";
    await fs.writeFile(path.join(temporaryDir, "manifest.json"), JSON.stringify(manifest));
    await assert.rejects(
        bundleExtension({ scriptDir: temporaryDir, outfile: path.join(temporaryDir, "output", "index.js") }),
        /Native APK file not found/,
    );
});
