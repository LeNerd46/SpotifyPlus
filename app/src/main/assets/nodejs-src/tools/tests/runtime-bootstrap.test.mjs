import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import vm from "node:vm";
import { fileURLToPath } from "node:url";

const testsDirectory = path.dirname(fileURLToPath(import.meta.url));
const runtimeSourcePath = path.resolve(testsDirectory, "../../../../cpp/worklet-runtime.cpp");
const rawRuntimeSource = fs.readFileSync(runtimeSourcePath, "utf8");
const bootstrapStartMarker = 'R"SPOTIFYPLUS(';
const bootstrapEndMarker = ')SPOTIFYPLUS";';
const bootstrapStart = rawRuntimeSource.indexOf(bootstrapStartMarker);
const bootstrapEnd = rawRuntimeSource.indexOf(
    bootstrapEndMarker,
    bootstrapStart + bootstrapStartMarker.length,
);

if (bootstrapStart < 0 || bootstrapEnd < 0) {
    throw new Error(`Could not extract CONTEXT_BOOTSTRAP from ${runtimeSourcePath}`);
}

const bootstrapSource = rawRuntimeSource.slice(
    bootstrapStart + bootstrapStartMarker.length,
    bootstrapEnd,
);

function createRuntimeContext() {
    const sharedValues = new Map();
    const rnCalls = [];
    let now = 0;
    const context = vm.createContext({
        __spotifyplusCallWorklet: () => undefined,
        __spotifyplusCancelAnimation: () => true,
        __spotifyplusDispatchCommand: () => true,
        __spotifyplusEmitUpdate: () => true,
        __spotifyplusGetRelativeCoords: () => null,
        __spotifyplusGetSharedValue: id => sharedValues.get(String(id)),
        __spotifyplusLog: () => undefined,
        __spotifyplusMeasure: () => null,
        __spotifyplusNow: () => now,
        __spotifyplusRequestFrame: () => undefined,
        __spotifyplusScheduleOnRN: (id, args) => {
            rnCalls.push({ id, args });
        },
        __spotifyplusScrollTo: () => true,
        __spotifyplusSetNativeProps: () => true,
        __spotifyplusSetSharedValue: (id, value) => {
            sharedValues.set(String(id), value);
            return true;
        },
    });
    vm.runInContext(bootstrapSource, context, { filename: "worklet-runtime-bootstrap.js" });
    return {
        context,
        rnCalls,
        setNow(value) {
            now = value;
        },
    };
}

test("UI bootstrap revives inline worklets and shareable collections", () => {
    const { context } = createRuntimeContext();
    const value = context.__spotifyplusReviveShareable({
        __spotifyPlusShareable: "inlineWorklet",
        metadata: {
            code: "function (increment) { return captured + increment; }",
            closure: { captured: 40 },
        },
    });
    const map = context.__spotifyplusReviveShareable({
        __spotifyPlusShareable: "map",
        entries: [["answer", 42]],
    });

    assert.equal(value(2), 42);
    assert.equal(map.get("answer"), 42);
});

test("timing animations survive 10,000 driven frames and clean callbacks exactly once", () => {
    const { context, rnCalls, setNow } = createRuntimeContext();
    const serializedDescriptor = {
        __spotifyPlusAnimation: true,
        type: "timing",
        toValue: 100,
        config: {
            duration: 300,
            easing: { type: "linear" },
            reduceMotion: "never",
        },
        callback: {
            __spotifyPlusShareable: "inlineWorklet",
            metadata: {
                code: "function (finished, current) { report(finished, current); }",
                closure: {
                    report: {
                        __spotifyPlusShareable: "rnFunction",
                        id: "animation-callback",
                    },
                },
            },
        },
        __spotifyPlusCleanup: {
            __spotifyPlusShareable: "rnFunction",
            id: "animation-cleanup",
        },
    };
    const descriptor = context.__spotifyplusReviveShareable(serializedDescriptor);
    const state = context.__spotifyplusCreateAnimationState(descriptor, 0, 0, false);

    for (let frame = 0; frame < 10_000; frame++) {
        const timestamp = frame * (1000 / 120);
        setNow(timestamp);
        context.__spotifyplusStepAnimationState(state, timestamp, false);
    }

    assert.equal(state.finished, true);
    assert.equal(state.current, 100);
    assert.deepEqual(
        rnCalls.map(call => call.id),
        ["animation-callback", "animation-cleanup"],
    );
});
