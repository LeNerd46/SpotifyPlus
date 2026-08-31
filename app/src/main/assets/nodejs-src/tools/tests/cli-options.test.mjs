import assert from "node:assert/strict";
import path from "node:path";
import test from "node:test";
import { parseCliArgs } from "../cli-options.mjs";

test("dev is parsed as a command instead of an extension directory", () => {
    const cwd = path.resolve("workspace");
    const options = parseCliArgs(["dev", "extension", "--port", "4000"], cwd);

    assert.equal(options.command, "dev");
    assert.equal(options.scriptDir, path.join(cwd, "extension"));
    assert.equal(options.port, 4000);
});

test("build accepts source and output paths", () => {
    const cwd = path.resolve("workspace");
    const options = parseCliArgs([
        "build",
        ".",
        "--entry=src/main.tsx",
        "--outfile",
        "dist/index.js",
        "--minify",
        "--sourcemap",
    ], cwd);

    assert.equal(options.command, "build");
    assert.equal(options.entryPath, "src/main.tsx");
    assert.equal(options.outfile, "dist/index.js");
    assert.equal(options.minify, true);
    assert.equal(options.sourcemap, true);
});

test("legacy directory-only invocation still means dev", () => {
    const cwd = path.resolve("workspace");
    const options = parseCliArgs(["extension"], cwd);

    assert.equal(options.command, "dev");
    assert.equal(options.scriptDir, path.join(cwd, "extension"));
});
