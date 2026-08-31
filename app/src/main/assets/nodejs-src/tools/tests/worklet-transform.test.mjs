import assert from "node:assert/strict";
import test from "node:test";
import vm from "node:vm";
import { transformWorklets } from "../worklet-transform.mjs";

function evaluateBinding(code, bindingName) {
    return vm.runInNewContext(`${code}\n${bindingName};`, {});
}

test("explicit worklets receive canonical and Reanimated-compatible metadata", async () => {
    const source = [
        "const offset = { value: 4 };",
        "const scale = 2;",
        "const work = (input) => {",
        "    'worklet';",
        "    const { value } = offset;",
        "    return input * scale + value;",
        "};",
    ].join("\n");
    const transformed = await transformWorklets(source, {
        filename: "fixtures/explicit.ts",
        inlineSourceMap: false,
        rootDir: "fixtures",
    });

    assert.equal(transformed.workletCount, 1);
    const work = evaluateBinding(transformed.code, "work");
    const metadata = work.__spotifyPlusWorklet;

    assert.equal(metadata.version, 2);
    assert.match(metadata.hash, /^[a-f0-9]{64}$/);
    assert.equal(metadata.hash, work.__workletHash);
    assert.equal(metadata.closure, work.__closure);
    assert.equal(work.__initData.code, metadata.code);
    assert.equal(metadata.location, "explicit.ts:3:14");
    assert.equal(JSON.parse(metadata.sourceMap).version, 3);

    const uiFunction = vm.runInNewContext(`(${metadata.code})`, {});
    assert.equal(uiFunction.call({ __closure: metadata.closure }, 3), 10);
});

test("hashes and generated worklet code are stable", async () => {
    const source = "const amount = 3; const work = () => { 'worklet'; return amount * 2; };";
    const options = {
        filename: "stable/worklet.ts",
        inlineSourceMap: false,
        rootDir: "stable",
    };
    const first = await transformWorklets(source, options);
    const second = await transformWorklets(source, options);

    assert.equal(first.worklets[0].hash, second.worklets[0].hash);
    assert.equal(first.worklets[0].code, second.worklets[0].code);
    assert.equal(first.worklets[0].sourceMap, second.worklets[0].sourceMap);
});

test("automatic callbacks follow hook aliases and encode Animated imports as worklet globals", async () => {
    const source = [
        "import { interpolate as mix, useAnimatedStyle as makeStyle } from 'spotifyplus/react/reanimated';",
        "const styleHook = makeStyle;",
        "const progress: { value: number } = { value: 0.5 };",
        "const updater = ({ max = 1 }: { max?: number } = {}) => ({",
        "    opacity: mix(progress.value, [0, max], [0, 1]),",
        "});",
        "export const style = styleHook(updater);",
    ].join("\n");
    const transformed = await transformWorklets(source, {
        filename: "aliases.ts",
        inlineSourceMap: false,
    });

    assert.equal(transformed.workletCount, 1);
    assert.deepEqual(transformed.worklets[0].globals, { mix: "interpolate" });
    assert.doesNotMatch(transformed.worklets[0].code, /: number|max\?:/);
    assert.match(transformed.code, /__spotifyPlusShareable:\s*"workletGlobal"/);
    assert.match(transformed.code, /name:\s*"interpolate"/);
    assert.match(transformed.code, /globals:\s*\{\s*"mix":\s*"interpolate"/s);
});

test("namespace imports use recursively revivable global markers", async () => {
    const source = [
        "import * as Animation from 'spotifyplus/react/reanimated';",
        "const value = { value: 0.25 };",
        "export const style = Animation.useAnimatedStyle(() => ({",
        "    opacity: Animation.interpolate(value.value, [0, 1], [0, 1]),",
        "}));",
    ].join("\n");
    const transformed = await transformWorklets(source, {
        filename: "namespace.ts",
        inlineSourceMap: false,
    });

    assert.deepEqual(transformed.worklets[0].globals, {
        "Animation.interpolate": "interpolate",
    });
    assert.match(transformed.code, /Animation:\s*\{\s*interpolate:\s*\{/s);
});

test("TypeScript CommonJS import wrappers retain canonical worklet globals", async () => {
    const source = [
        "const __importStar = value => value;",
        "const Animation = __importStar(require('spotifyplus/react/reanimated'));",
        "const progress = { value: 0.5 };",
        "const style = (0, Animation.useAnimatedStyle)(() => ({",
        "    opacity: (0, Animation.interpolate)(progress.value, [0, 1], [0, 1]),",
        "}));",
    ].join("\n");
    const transformed = await transformWorklets(source, {
        filename: "compiled.js",
        inlineSourceMap: false,
    });

    assert.equal(transformed.workletCount, 1);
    assert.deepEqual(transformed.worklets[0].globals, {
        "Animation.interpolate": "interpolate",
    });
    assert.match(transformed.code, /__spotifyPlusShareable:\s*"workletGlobal"/);
});

test("scroll and gesture handler objects are automatically workletized", async () => {
    const source = [
        "import { useAnimatedScrollHandler as makeHandler } from 'spotifyplus/react/reanimated';",
        "const onEnd = event => event.y;",
        "const handlers = {",
        "    onScroll(event) { return event.x; },",
        "    onEnd,",
        "};",
        "export const handler = makeHandler(handlers);",
    ].join("\n");
    const transformed = await transformWorklets(source, {
        filename: "handlers.ts",
        inlineSourceMap: false,
    });

    assert.equal(transformed.workletCount, 2);
    assert.match(transformed.code, /onScroll:\s*\(\(\) =>/);
    assert.match(transformed.code, /const onEnd = \(\(\) =>/);
});

test("gesture builder callbacks are workletized without matching unrelated methods", async () => {
    const source = [
        "import { Gesture } from 'spotifyplus/react/Gesture';",
        "const unrelated = { onUpdate(callback) { return callback; } };",
        "const ordinary = unrelated.onUpdate(value => value + 1);",
        "export const gesture = Gesture.Pan().onUpdate(event => event.translationX);",
        "void ordinary;",
    ].join("\n");
    const transformed = await transformWorklets(source, {
        filename: "gesture-builder.ts",
        inlineSourceMap: false,
    });

    assert.equal(transformed.workletCount, 1);
    assert.match(transformed.code, /Gesture\.Pan\(\)\.onUpdate\(\(\(\) =>/);
    assert.match(transformed.code, /unrelated\.onUpdate\(value => value \+ 1\)/);
});

test("nested and recursive TypeScript worklets preserve their semantics", async () => {
    const source = [
        "const offset = 2;",
        "const factorial = ({ n }: { n: number }): number => {",
        "    'worklet';",
        "    const addOffset = (value: number): number => {",
        "        'worklet';",
        "        return value + offset;",
        "    };",
        "    if (n <= 1) return addOffset(1) - offset;",
        "    return n * factorial({ n: n - 1 });",
        "};",
    ].join("\n");
    const transformed = await transformWorklets(source, {
        filename: "nested.ts",
        inlineSourceMap: false,
    });

    assert.equal(transformed.workletCount, 2);
    assert.doesNotThrow(() => new vm.Script(transformed.code));
    const factorial = evaluateBinding(transformed.code, "factorial");
    assert.equal(factorial.__spotifyPlusWorklet.closure.offset, 2);
    assert.equal(factorial({ n: 5 }), 120);
});

test("ordinary functions remain ordinary", async () => {
    const source = "const untouched = value => value + 1;";
    const transformed = await transformWorklets(source, {
        filename: "ordinary.js",
        inlineSourceMap: false,
    });

    assert.equal(transformed.workletCount, 0);
    assert.doesNotMatch(transformed.code, /__spotifyPlusWorklet/);
    assert.equal(evaluateBinding(transformed.code, "untouched")(2), 3);
});

test("JSX inside a worklet fails with an actionable error", async () => {
    await assert.rejects(
        transformWorklets(
            "const render = () => { 'worklet'; return <View />; };",
            {
                filename: "invalid.tsx",
                inlineSourceMap: false,
            },
        ),
        /cannot contain JSX/,
    );
});
