import assert from "node:assert/strict";
import { createRequire } from "node:module";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import vm from "node:vm";

const require = createRequire(import.meta.url);
const testsDir = path.dirname(fileURLToPath(import.meta.url));
const { hasObjectTag, isPlainObject } = require(path.resolve(
    testsDir,
    "../../../nodejs/bridge/shareable-types.js",
));
const { SpotifyPlusWorkletAdapter } = require(path.resolve(
    testsDir,
    "../../../nodejs/bridge/worklet-adapter.js",
));
const { DerivedValueView } = require(path.resolve(
    testsDir,
    "../../../nodejs/ui/native-animation/runtime.js",
));
const { SHARED_VALUE_KEY } = require(path.resolve(
    testsDir,
    "../../../nodejs/ui/native-animation/types.js",
));

test("shareable object detection accepts plain objects from a script VM realm", () => {
    const plain = vm.runInNewContext("({ nested: { value: 1 } })");
    const custom = vm.runInNewContext("new (class CustomValue { constructor() { this.value = 1; } })()");

    assert.equal(isPlainObject(plain), true);
    assert.equal(isPlainObject(plain.nested), true);
    assert.equal(isPlainObject(custom), false);
});

test("shareable object tags recognize supported collections across VM realms", () => {
    const values = vm.runInNewContext(`({
        map: new Map(),
        set: new Set(),
        regexp: /lyrics/gi,
        date: new Date(0),
        buffer: new ArrayBuffer(4),
    })`);

    assert.equal(hasObjectTag(values.map, "Map"), true);
    assert.equal(hasObjectTag(values.set, "Set"), true);
    assert.equal(hasObjectTag(values.regexp, "RegExp"), true);
    assert.equal(hasObjectTag(values.date, "Date"), true);
    assert.equal(hasObjectTag(values.buffer, "ArrayBuffer"), true);
});

test("worklet adapter serializes captured values from a script VM realm", () => {
    let serialized;
    const bridge = {
        on() {},
        setWorkletSharedValue(_scriptId, _generation, _id, value) {
            serialized = value;
            return true;
        },
    };
    const logger = {
        error() {},
        info() {},
        warn() {},
    };
    const scope = { scriptId: "shareable-test", generation: 1 };
    const adapter = new SpotifyPlusWorkletAdapter(bridge, logger, scope);
    const value = vm.runInNewContext(`({
        config: { damping: 12, stiffness: 80 },
        samples: [0, 0.5, 1],
        map: new Map([["line", 1]]),
        set: new Set(["active"]),
        regexp: /lyrics/gi,
        date: new Date(0),
        buffer: new Uint8Array([1, 2, 3]).buffer,
    })`);

    adapter.writeMutable(scope, { id: 1, value });

    assert.deepEqual(serialized.config, { damping: 12, stiffness: 80 });
    assert.deepEqual(serialized.samples, [0, 0.5, 1]);
    assert.equal(serialized.map.__spotifyPlusShareable, "map");
    assert.equal(serialized.set.__spotifyPlusShareable, "set");
    assert.equal(serialized.regexp.__spotifyPlusShareable, "regexp");
    assert.equal(serialized.date.value, "1970-01-01T00:00:00.000Z");
    assert.deepEqual(serialized.buffer.bytes, [1, 2, 3]);
});

test("worklet adapter captures read-only derived values as shared-value handles", () => {
    let serialized;
    const bridge = {
        on() {},
        setWorkletSharedValue(_scriptId, _generation, _id, value) {
            serialized = value;
            return true;
        },
    };
    const logger = {
        error() {},
        info() {},
        warn() {},
    };
    const scope = { scriptId: "derived-test", generation: 2 };
    const marker = {
        version: 2,
        scriptId: scope.scriptId,
        generation: scope.generation,
        runtimeId: "derived-test:2:1",
        id: 42,
    };
    const derived = new DerivedValueView({
        [SHARED_VALUE_KEY]: marker,
    });
    const adapter = new SpotifyPlusWorkletAdapter(bridge, logger, scope);

    adapter.writeMutable(scope, { id: 1, value: derived });

    assert.deepEqual(serialized, {
        __spotifyPlusShareable: "sharedValue",
        id: "42",
    });
});
