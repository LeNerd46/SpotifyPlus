import assert from "node:assert/strict";
import { createRequire } from "node:module";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const require = createRequire(import.meta.url);
const testsDir = path.dirname(fileURLToPath(import.meta.url));
const { resolveFetchGlobals } = require(path.resolve(
    testsDir,
    "../../../nodejs/loader/fetch-globals.js",
));

test("script fetch globals prefer and bind the Node 18 Fetch API", async () => {
    const host = {
        marker: "host",
        async fetch() {
            return this.marker;
        },
        Headers: class Headers {},
        Request: class Request {},
        Response: class Response {},
    };
    const resolution = resolveFetchGlobals(
        () => {
            throw new Error("fallback should not load");
        },
        host,
    );

    assert.equal(await resolution.globals.fetch(), "host");
    assert.equal(resolution.globals.Headers, host.Headers);
    assert.equal(resolution.globals.Request, host.Request);
    assert.equal(resolution.globals.Response, host.Response);
    assert.equal(resolution.fallbackError, undefined);
});

test("script fetch globals retain a CommonJS-compatible fallback", () => {
    const fallback = Object.assign(async () => "fallback", {
        Headers: class Headers {},
        Request: class Request {},
        Response: class Response {},
    });
    const resolution = resolveFetchGlobals(() => fallback, {});

    assert.equal(resolution.globals.fetch, fallback);
    assert.equal(resolution.globals.Headers, fallback.Headers);
    assert.equal(resolution.fallbackError, undefined);
});
