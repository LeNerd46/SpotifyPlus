import assert from "node:assert/strict";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import {
    colorizeDevLogOutput,
    createDevSourceMapper,
    formatDevLogEntry,
} from "../dev-source-map.mjs";

const testsDirectory = path.dirname(fileURLToPath(import.meta.url));

function inlineBundle(sourceMap) {
    const encoded = Buffer.from(JSON.stringify(sourceMap)).toString("base64");
    return `throw new Error();\n//# sourceMappingURL=data:application/json;base64,${encoded}\n`;
}

test("maps Android bundle stack locations to original TypeScript files", () => {
    const scriptDir = path.join(testsDirectory, "fixtures", "source-map-extension");
    const mapper = createDevSourceMapper(inlineBundle({
        version: 3,
        sources: ["src/index.tsx"],
        names: [],
        mappings: "AAAA",
    }), scriptDir, "index.js");

    assert.equal(
        mapper.map("    at render (/data/user/0/com.spotify.music/files/extensions/example/index.js:1:1)"),
        `    at render (${path.join(scriptDir, "src", "index.tsx")}:1:1)`,
    );
});

test("console entries include the original callsite without dumping the generated stack", () => {
    const scriptDir = path.join(testsDirectory, "fixtures", "source-map-extension");
    const mapper = createDevSourceMapper(inlineBundle({
        version: 3,
        sources: ["src/logger.ts"],
        names: [],
        mappings: "AAAA",
    }), scriptDir, "index.js");

    assert.equal(
        formatDevLogEntry({
            level: "warn",
            message: "missing metadata",
            scriptId: "com.example.extension",
            stack: "Error\n    at /data/user/0/com.spotify.music/files/extensions/example/index.js:1:1",
        }, mapper),
        `[com.example.extension] missing metadata\n    at ${path.join(scriptDir, "src", "logger.ts")}:1:1`,
    );
});

test("development log colors follow console severity", () => {
    assert.equal(colorizeDevLogOutput("message", "log", true), "\u001b[36mmessage\u001b[0m");
    assert.equal(colorizeDevLogOutput("message", "warn", true), "\u001b[33mmessage\u001b[0m");
    assert.equal(colorizeDevLogOutput("message", "error", true), "\u001b[31mmessage\u001b[0m");
    assert.equal(colorizeDevLogOutput("message", "error", false), "message");
});
