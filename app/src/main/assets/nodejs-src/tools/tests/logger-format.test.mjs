import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import ts from "typescript";

const testsDirectory = path.dirname(fileURLToPath(import.meta.url));
const loggerSource = fs.readFileSync(path.resolve(testsDirectory, "../../core/logger.ts"), "utf8");
const compiled = ts.transpileModule(loggerSource, {
    compilerOptions: {
        esModuleInterop: true,
        module: ts.ModuleKind.CommonJS,
        target: ts.ScriptTarget.ES2020,
    },
}).outputText;
const loggerModule = { exports: {} };
const require = createRequire(import.meta.url);
new Function("require", "module", "exports", compiled)(require, loggerModule, loggerModule.exports);
const { formatDevLogArgs } = loggerModule.exports;

test("development logs pretty print objects and JSON strings", () => {
    const objectOutput = formatDevLogArgs(["payload", {
        account: {
            id: 42,
            roles: ["listener", "developer"],
        },
    }]);
    const jsonOutput = formatDevLogArgs(['{"marketplace":{"enabled":true,"items":[1,2]}}']);

    assert.match(objectOutput, /payload \{\n  account: \{/);
    assert.match(objectOutput, /roles: \[\n      'listener',\n      'developer'/);
    assert.match(jsonOutput, /\{\n  marketplace: \{/);
    assert.match(jsonOutput, /items: \[\n      1,\n      2/);
});

test("development logs remain bounded and tolerate circular objects", () => {
    const circular = { name: "example" };
    circular.self = circular;

    assert.match(formatDevLogArgs([circular]), /self: '\[Circular\]'/);
    assert.ok(formatDevLogArgs([{ value: "x".repeat(1000) }], {
        maxOutputLength: 120,
        maxStringLength: 1000,
    }).length <= 120);
});
