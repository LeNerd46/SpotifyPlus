import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const testsDirectory = path.dirname(fileURLToPath(import.meta.url));
const runtimeModulePath = path.resolve(
    testsDirectory,
    "../../../nodejs/core/extension-assets.js",
);
const require = createRequire(import.meta.url);

test("nested entry directories register their extension-root manifest", t => {
    const extensionDirectory = fs.mkdtempSync(
        path.join(os.tmpdir(), "spotifyplus-assets-"),
    );
    t.after(() => fs.rmSync(extensionDirectory, { force: true, recursive: true }));

    const entryDirectory = path.join(extensionDirectory, "dist");
    const assetsDirectory = path.join(entryDirectory, "assets", "fonts");
    const manifestPath = path.join(extensionDirectory, "manifest.json");
    const fontPath = path.join(assetsDirectory, "lyrics.ttf");
    fs.mkdirSync(assetsDirectory, { recursive: true });
    fs.writeFileSync(manifestPath, JSON.stringify({
        id: "com.example.lyrics",
        main: "dist/index.js",
        assets: ["assets/**/*"],
    }));
    fs.writeFileSync(fontPath, "fixture");

    delete require.cache[runtimeModulePath];
    const {
        createExtensionAssetsApi,
        getNativeAssetRegistration,
    } = require(runtimeModulePath);
    const api = createExtensionAssetsApi(
        "com.example.lyrics",
        1,
        entryDirectory,
        extensionDirectory,
        ["assets/**/*"],
    );
    const registration = getNativeAssetRegistration(
        api.font("assets/fonts/lyrics.ttf"),
    );

    assert.equal(registration.rootPath, fs.realpathSync(entryDirectory));
    assert.equal(registration.manifestPath, fs.realpathSync(manifestPath));
    assert.equal(registration.filePath, fs.realpathSync(fontPath));
});
