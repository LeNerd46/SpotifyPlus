import assert from "node:assert/strict";
import test from "node:test";

import { ExtensionEventEmitter } from "../../../nodejs/loader/extension-event-emitter.js";

test("extension event emitters do not share listeners", async () => {
    const first = new ExtensionEventEmitter();
    const second = new ExtensionEventEmitter();
    const received = [];

    first.on("settings.changed", payload => received.push(payload));

    await second.emit("settings.changed", "second");
    await first.emit("settings.changed", "first");

    assert.deepEqual(received, ["first"]);
});

test("once and off preserve EventEmitter listener semantics", async () => {
    const emitter = new ExtensionEventEmitter();
    const received = [];
    const persistent = payload => received.push(`on:${payload}`);
    const once = payload => received.push(`once:${payload}`);

    emitter.on("event", persistent);
    emitter.once("event", once);
    await emitter.emit("event", 1);
    emitter.off("event", persistent);
    await emitter.emit("event", 2);

    assert.deepEqual(received, ["on:1", "once:1"]);
});
