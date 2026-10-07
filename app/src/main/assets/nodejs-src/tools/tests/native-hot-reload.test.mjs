import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { once } from 'node:events';
import test from 'node:test';
import { HostRuntime } from '../../../nodejs/loader/host-runtime.js';
import { ScriptLoader } from '../../../nodejs/loader/script-loader.js';
import { ScriptRegistry } from '../../../nodejs/loader/script-registry.js';

const apk = fs.readFileSync(new URL('../../../../../../../scripts/lyrics/lyrics.apk', import.meta.url));
const logger = { info() {}, warn() {}, error() {}, child() { return this; } };
const manifest = {
    id: 'test.native-dev', name: 'Native dev', version: '1', api: 1,
    main: 'dist/index.js', assets: ['assets/**/*'],
    native: { apk: 'plugin.apk', pluginClass: 'example.Plugin' },
};

function fixture(t) {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'spotifyplus-native-dev-'));
    t.after(() => fs.rmSync(root, { recursive: true, force: true }));
    const calls = [];
    const nativeComponents = new Set();
    const runtime = Object.create(HostRuntime.prototype);
    runtime.config = {};
    runtime.logger = logger;
    runtime.activeSurfaces = new Map();
    runtime.registry = new ScriptRegistry(logger);
    runtime.bridge = {
        log() {},
        unregisterScript() { nativeComponents.clear(); calls.push('unregister'); },
        loadApk(id, apkPath) {
            assert.ok(fs.readFileSync(apkPath).equals(apk) || fs.readFileSync(apkPath).length === apk.length);
            nativeComponents.add('NativeView');
            calls.push(['loadApk', apkPath]);
        },
    };
    const loader = new ScriptLoader(runtime, logger);
    // Use the real loader/registry/lifecycle, with only Android and script API
    // bindings stubbed. JS asserts native registration has already happened.
    loader.apiFactory = { create: () => ({ console: { log() {
        assert.ok(nativeComponents.has('NativeView'), 'native component must exist when JS executes');
        calls.push('js');
    } } }) };
    loader.scriptDirectories.set(manifest.id, root);
    loader.scriptTrust.set(manifest.id, 'user');
    runtime.scriptLoader = loader;
    const bundle = (bytes = apk) => ({
        buildId: 'dev-test', manifest, source: 'console.log("native ready");',
        nativeApk: { path: 'plugin.apk', data: bytes.toString('base64'), size: bytes.length },
        assets: [{ path: 'assets/test.txt', data: Buffer.from('asset').toString('base64'), size: 5 }],
    });
    const reload = bundle => runtime.handleHotReload({ scriptId: manifest.id, buildId: bundle.buildId, bundle });
    return { root, runtime, calls, bundle, reload };
}

test('TypeScript-only reloads restore native registrations before executing JS and persist a nested main', async t => {
    const { root, calls, bundle, reload } = fixture(t);
    await reload(bundle());
    const nativePath = calls[1][1];
    assert.deepEqual(calls.map(call => Array.isArray(call) ? call[0] : call), ['unregister', 'loadApk', 'js']);
    assert.equal(path.dirname(nativePath), path.join(root, '.spotifyplus-native'));
    assert.ok(fs.readFileSync(path.join(root, 'dist/plugin.apk')).equals(apk));
    assert.equal(fs.readFileSync(path.join(root, 'dist/index.js'), 'utf8'), bundle().source);
    assert.equal(JSON.parse(fs.readFileSync(path.join(root, 'manifest.json'))).main, 'dist/index.js');
    const modifiedAt = fs.statSync(nativePath).mtimeMs;
    await reload(bundle());
    assert.equal(calls[4][1], nativePath);
    assert.equal(fs.statSync(nativePath).mtimeMs, modifiedAt, 'loaded APK snapshot must not be rewritten');
    assert.deepEqual(calls.map(call => Array.isArray(call) ? call[0] : call), [
        'unregister', 'loadApk', 'js', 'unregister', 'loadApk', 'js',
    ]);
});

test('a changed APK loads from a different immutable path and leaves the old revision intact', async t => {
    const { root, calls, bundle, reload } = fixture(t);
    await reload(bundle());
    const previousPath = calls[1][1];
    const changed = Buffer.from(apk);
    changed[10] ^= 1; // ZIP local-header timestamp: valid APK with different bytes.
    await reload(bundle(changed));
    assert.notEqual(calls[4][1], previousPath);
    assert.ok(fs.readFileSync(previousPath).equals(apk));
    assert.ok(fs.readFileSync(calls[4][1]).equals(changed));
    assert.ok(fs.readFileSync(path.join(root, 'dist/plugin.apk')).equals(changed));
});

test('missing, corrupt, mismatched, and undeclared native payloads leave the running extension untouched', async t => {
    const { root, runtime, calls, bundle, reload } = fixture(t);
    await reload(bundle());
    const previousFiles = fs.readdirSync(root);
    const previousCalls = calls.length;
    const invalid = [
        { ...bundle(), nativeApk: undefined },
        { ...bundle(), nativeApk: { path: 'plugin.apk', data: 'AA==', size: 1 } },
        { ...bundle(), nativeApk: { ...bundle().nativeApk, path: '../plugin.apk' } },
        { ...bundle(), nativeApk: { ...bundle().nativeApk, path: 'other.apk' } },
        { ...bundle(), nativeApk: { ...bundle().nativeApk, size: 0 } },
        { ...bundle(), manifest: { ...manifest, native: undefined } },
        { ...bundle(), assets: [{ path: 'unlisted.txt', data: 'AA==' }] },
    ];
    for (const candidate of invalid) {
        await assert.rejects(reload(candidate));
        assert.equal(calls.length, previousCalls);
        assert.ok(runtime.registry.getScript(manifest.id));
        assert.deepEqual(fs.readdirSync(root), previousFiles);
        assert.ok(fs.readFileSync(path.join(root, 'dist/plugin.apk')).equals(apk));
    }
});

test('runtime advertises native hot reload and acknowledges only after registration', async t => {
    const { runtime, calls, bundle } = fixture(t);
    runtime.startHotReloadServer(0);
    const server = runtime.hotReloadServer;
    await once(server, 'listening');
    t.after(() => new Promise(resolve => { server.closeAllConnections(); server.close(resolve); }));
    const url = `http://127.0.0.1:${server.address().port}`;
    assert.deepEqual(await (await fetch(`${url}/dev-capabilities`)).json(), { nativeHotReload: true });
    const response = await fetch(`${url}/hot-reload`, {
        method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(bundle()),
    });
    assert.equal(response.status, 200, await response.text());
    assert.equal(calls.at(-1), 'js');
});
