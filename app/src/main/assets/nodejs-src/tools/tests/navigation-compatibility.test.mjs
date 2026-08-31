import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const testsDirectory = path.dirname(fileURLToPath(import.meta.url));
const cppDirectory = path.resolve(testsDirectory, "../../../../cpp");
const exportsSource = fs.readFileSync(
    path.join(cppDirectory, "spotifyplus-exports.cpp"),
    "utf8",
);
const bridgeSource = fs.readFileSync(
    path.join(cppDirectory, "spotifyplus_bridge.cpp"),
    "utf8",
);

test("navigation preserves the legacy OpenUri bridge ABI", () => {
    assert.match(exportsSource, /extern "C" void SpotifyPlus_OpenUri\(const char\* uri\)/);
    assert.match(bridgeSource, /g_navigate \|\| g_legacyOpenUri/);
    assert.match(bridgeSource, /if \(!resolve_symbols\(\) \|\| !g_navigateBack\) return result;/);
});
