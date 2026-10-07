import assert from 'node:assert/strict';
import test from 'node:test';
import EventEmitter from 'node:events';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import Module from 'node:module';
// Emitted runtime files live beside nodejs-src; use the development dependencies for tests.
process.env.NODE_PATH = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../node_modules');
Module._initPaths();
const { ScriptApiFactory } = await import('../../../nodejs/loader/script-api.js');
const { ScriptRegistry } = await import('../../../nodejs/loader/script-registry.js');
const { Bridge } = await import('../../../nodejs/bridge/bridge.js');
const ui = { forExtension() { return {}; } };

const logger = { child() { return this; }, info() {}, error() {}, warn() {} };
function fixture() {
    const calls = [];
    const asyncCalls = [];
    const registry = new ScriptRegistry(logger);
    const runtime = { registry, ui, platformData: {}, session: {},
        callApiSync(operation, args = {}) { calls.push({ operation, args }); return operation === 'library.contains' ? [true] : null; },
        requestApi(operation, args = {}) { asyncCalls.push(operation); calls.push({ operation, args }); return Promise.resolve(null); } };
    return { calls, asyncCalls, registry, api: new ScriptApiFactory(runtime, logger).create('test', 1, path.resolve('tools/tests/fixtures/api2'), path.resolve('tools/tests/fixtures/api2'), []).SpotifyPlus };
}

test('context playback normalizes URLs and validates before dispatch', async () => {
    const { api, calls } = fixture();
    await api.Player.playContext('https://open.spotify.com/intl-en/playlist/abc123?si=test', 4);
    await api.Player.playContext({ uri: 'spotify:album:def456' });
    await api.Player.playContext('spotify:artist:ghi789');
    await api.Player.playContext('spotify:track:jkl012');
    assert.deepEqual(calls, [
        { operation: 'player.playContext', args: { uri: 'spotify:playlist:abc123', index: 4 } },
        { operation: 'player.playContext', args: { uri: 'spotify:album:def456', index: 0 } },
        { operation: 'player.playContext', args: { uri: 'spotify:artist:ghi789', index: 0 } },
        { operation: 'player.playContext', args: { uri: 'spotify:track:jkl012', index: 0 } },
    ]);
    assert.throws(() => api.Player.playContext('spotify:episode:abc123'), /album, playlist, artist or track/);
    assert.throws(() => api.Player.playContext('spotify:album:abc123', -1), /integer/);
    assert.throws(() => api.Player.seek(NaN), /integer/);
    assert.equal(calls.length, 4);
});

test('queue edits retain revision and occurrence index', async () => {
    const { api, calls } = fixture();
    const snapshot = { revision: '42', next: [{ uid: 'one' }, { uid: 'two' }] };
    await api.Queue.move(snapshot, 1, 0);
    await api.Queue.remove(snapshot, 1);
    assert.deepEqual(calls, [
        { operation: 'queue.move', args: { revision: '42', index: 1, toIndex: 0 } },
        { operation: 'queue.remove', args: { revision: '42', index: 1 } },
    ]);
    assert.equal(snapshot.next[1].uid, 'two');
    assert.throws(() => api.Queue.move(snapshot, 0.5, 0), /integer/);
});

test('local APIs return values directly, commands return void, and no async request is made', () => {
    const { api, calls, asyncCalls } = fixture();
    assert.equal(api.Library.isLiked('spotify:track:abc123'), true);
    assert.deepEqual(api.Library.contains(['https://open.spotify.com/track/abc123']), [true]);
    const localCommands = [
        () => api.Player.seek(1234), () => api.Player.play(), () => api.Player.pause(),
        () => api.Player.togglePlay(), () => api.Player.skipNext(), () => api.Player.skipPrevious(),
        () => api.Player.setShuffle(true), () => api.Player.toggleShuffle(),
        () => api.Player.cycleRepeat(), () => api.Player.setRepeat('off'),
        () => api.Queue.add('spotify:track:abc123'),
        () => api.Queue.clear({ revision: '1' }),
        () => api.Library.save('spotify:album:abc123'), () => api.Library.remove('spotify:album:abc123'),
        () => api.Library.like('spotify:track:abc123'), () => api.Library.unlike('spotify:track:abc123'),
        () => api.Playlists.delete('spotify:playlist:abc123'), () => api.Playlists.move('spotify:playlist:abc123'),
        () => api.Playlists.addTracks('spotify:playlist:abc123', ['spotify:track:def456']),
        () => api.Playlists.removeTracks('spotify:playlist:abc123', ['row1']),
        () => api.Playlists.moveTracks('spotify:playlist:abc123', ['row1']),
        () => api.Platform.Clipboard.writeText('caf\u00e9 \ud83c\udfb5'), () => api.Platform.Clipboard.clear(),
    ];
    for (const command of localCommands) assert.equal(command(), undefined);
    assert.equal(api.Player.getState(), null);
    assert.equal(api.Queue.get(), null);
    assert.equal(api.Connect.getDevices(), null);
    assert.equal(api.Connect.getCurrentDevice(), null);
    assert.equal(api.Platform.Clipboard.readText(), null);
    assert.equal(calls[2].operation, 'player.seek');
    assert.deepEqual(calls[2].args, { positionMs: 1234 });
    assert.deepEqual(asyncCalls, []);
});

test('API getters retain native snapshots and local failures throw at the call site', () => {
    const values = {
        'player.state': { positionMs: 1200, isPlaying: true },
        'queue.get': { revision: '2', next: [{ uid: 'duplicate', uri: 'spotify:track:a' }] },
        'connect.devices': [{ id: 'local_device', name: 'Phone' }],
        'connect.current': { id: 'local_device', name: 'Phone' },
        'playlists.create': { uri: 'spotify:playlist:abc123', name: 'New' },
    };
    const runtime = { registry: new ScriptRegistry(logger), ui, platformData: {}, session: {},
        callApiSync(operation) {
            if (operation in values) return values[operation];
            throw new Error('Spotify service is not ready');
        },
        requestApi() { assert.fail('Local operation entered the async queue'); },
    };
    const directory = path.resolve('tools/tests/fixtures/api2');
    const api = new ScriptApiFactory(runtime, logger).create('test', 1, directory, directory, []).SpotifyPlus;
    assert.deepEqual(api.Player.getState(), values['player.state']);
    assert.deepEqual(api.Queue.get(), values['queue.get']);
    assert.deepEqual(api.Connect.getDevices(), values['connect.devices']);
    assert.deepEqual(api.Connect.getCurrentDevice(), values['connect.current']);
    assert.deepEqual(api.Playlists.create('New'), values['playlists.create']);
    assert.throws(() => api.Player.seek(1200), /service is not ready/);
    assert.throws(() => api.Library.isLiked('spotify:track:abc123'), /service is not ready/);
});

test('synchronous bridge preserves Unicode, nulls and errors without pending replies', () => {
    const bridge = Object.create(Bridge.prototype);
    bridge.apiPending = new Map();
    const calls = [];
    bridge.addon = { callApiSync(operation, json) {
        calls.push({ operation, json });
        return JSON.stringify(operation === 'library.contains' ? { result: [false, true] } : { result: null });
    } };
    assert.equal(bridge.callApiSync('clipboard.write', { text: 'caf\u00e9 \ud83c\udfb5' }), null);
    assert.match(calls[0].json, /^[\x00-\x7f]*$/);
    assert.equal(JSON.parse(calls[0].json).text, 'caf\u00e9 \ud83c\udfb5');
    assert.deepEqual(bridge.callApiSync('library.contains'), [false, true]);
    bridge.addon.callApiSync = () => JSON.stringify({ error: 'Spotify player is not ready' });
    assert.throws(() => bridge.callApiSync('player.seek'), /player is not ready/);
    bridge.addon.callApiSync = () => { throw new Error('JNI unavailable'); };
    assert.throws(() => bridge.callApiSync('queue.get'), /JNI unavailable/);
    assert.equal(bridge.apiPending.size, 0);
});

test('storage reads are synchronous and retain JSON, text, binary and missing values', () => {
    const registry = new ScriptRegistry(logger);
    const runtime = { registry, ui, platformData: {}, session: {},
        storageGet: () => ({ enabled: true }),
        storageRead: (_id, name) => name === 'missing' ? null : name === 'binary'
            ? { type: 'binary', data: 'AP+A' } : name === 'json'
            ? { type: 'json', value: { count: 2 } } : { type: 'text', value: 'text' },
        cacheRead: () => ({ type: 'binary', data: 'AP+A' }),
    };
    const directory = path.resolve('tools/tests/fixtures/api2');
    const storage = new ScriptApiFactory(runtime, logger).create('test', 1, directory, directory, []).SpotifyPlus.Platform.Storage;
    assert.deepEqual(storage.get('settings'), { enabled: true });
    assert.deepEqual(storage.read('json'), { count: 2 });
    assert.equal(storage.read('text'), 'text');
    assert.equal(storage.read('missing'), null);
    assert.deepEqual(storage.read('binary'), Uint8Array.of(0, 255, 128));
    assert.deepEqual(storage.Cache.read('binary'), Uint8Array.of(0, 255, 128));
});

test('network and native UI operations retain asynchronous request dispatch', async () => {
    const { api } = fixture();
    for (const request of [api.Search.search('query'), api.Library.list(), api.User.getCurrent(),
        api.Playlists.get('spotify:playlist:abc123'), api.Connect.transfer('local_device'),
        api.Player.playContext('spotify:album:abc123'), api.SideDrawer.open()]) {
        assert.equal(typeof request.then, 'function');
        await request;
    }
});

test('native menus normalize each supported entity and retain parent context', async () => {
    const { api, calls } = fixture();
    await api.SideDrawer.open();
    await api.ContextMenu.openNowPlaying();
    for (const type of ['track', 'album', 'artist', 'playlist']) {
        await api.ContextMenu.open(`https://open.spotify.com/${type}/abc123?si=test`);
    }
    await api.ContextMenu.open({ uri: 'spotify:track:def456' }, { contextUri: { uri: 'spotify:playlist:abc123' } });
    assert.deepEqual(calls, [
        { operation: 'side.open', args: {} },
        { operation: 'menu.openNowPlaying', args: {} },
        ...['track', 'album', 'artist', 'playlist'].map(type => ({ operation: 'menu.open', args: { uri: `spotify:${type}:abc123` } })),
        { operation: 'menu.open', args: { uri: 'spotify:track:def456', contextUri: 'spotify:playlist:abc123' } },
    ]);
    for (const invalid of ['spotify:episode:abc123', 'spotify:show:abc123', 'spotify:home', 'https://example.com/track/abc123', 'spotify:track:']) {
        assert.throws(() => api.ContextMenu.open(invalid));
    }
    assert.throws(() => api.ContextMenu.open('spotify:track:abc123', { contextUri: 'garbage' }));
    assert.equal(calls.length, 7);
});

test('native UI opening reports bridge failures to React callers', async () => {
    const registry = new ScriptRegistry(logger);
    const runtime = { registry, ui, platformData: {}, session: {}, requestApi() { return Promise.reject(new Error('Native menu unavailable')); } };
    const directory = path.resolve('tools/tests/fixtures/api2');
    const api = new ScriptApiFactory(runtime, logger).create('test', 1, directory, directory, []).SpotifyPlus;
    await assert.rejects(api.SideDrawer.open(), /Native menu unavailable/);
    await assert.rejects(api.ContextMenu.openNowPlaying(), /Native menu unavailable/);
    await assert.rejects(api.ContextMenu.open('spotify:album:abc123'), /Native menu unavailable/);
});

test('Spotify events isolate extensions and stop delivery after unload', async () => {
    const errors = [];
    const registry = new ScriptRegistry(logger, (...args) => errors.push(args));
    const a = registry.getExtensionEventEmitter('a');
    const b = registry.getExtensionEventEmitter('b');
    const received = [];
    a.on('deviceChanged', data => { data.device.name = 'mutated'; throw new Error('extension failure'); });
    b.once('deviceChanged', data => received.push(data.device.name));
    b.on('songChanged', data => received.push(data.uri));
    await registry.broadcastSpotifyEvent('deviceChanged', { device: { name: 'Kitchen' } });
    await registry.broadcastSpotifyEvent('deviceChanged', { device: { name: 'Bedroom' } });
    assert.deepEqual(received, ['Kitchen']);
    assert.equal(errors.length, 2);
    registry.unregisterScript('b');
    await registry.broadcastSpotifyEvent('songChanged', { uri: 'spotify:track:test' });
    assert.deepEqual(received, ['Kitchen']);
});

test('bridge correlates replies, preserves Unicode and rejects failures', async () => {
    const bridge = Object.create(Bridge.prototype);
    EventEmitter.call(bridge);
    bridge.apiPending = new Map();
    bridge.on("spotify.api.result", response => bridge.apiPending.get(response.id)?.(response));
    const requests = [];
    bridge.addon = { requestApi(...args) { requests.push(args); } };
    const text = 'caf\u00e9 \ud83c\udfb5';
    const first = bridge.requestApi('clipboard.write', { text });
    const second = bridge.requestApi('queue.get');
    assert.equal(JSON.parse(requests[0][2]).text, text);
    assert.match(requests[0][2], /^[\x00-\x7f]*$/);
    bridge.emit('spotify.api.result', { id: requests[1][0], result: { revision: '9' } });
    bridge.emit('spotify.api.result', { id: requests[0][0], error: 'Clipboard unavailable' });
    assert.deepEqual(await second, { revision: '9' });
    await assert.rejects(first, /Clipboard unavailable/);
    assert.equal(bridge.apiPending.size, 0);
    bridge.addon.requestApi = () => { throw new Error('Native bridge unavailable'); };
    await assert.rejects(bridge.requestApi('queue.get'), /Native bridge unavailable/);
    assert.equal(bridge.apiPending.size, 0);
});


test('context menus evaluate visibility on every opening and isolate callback failures', () => {
    const { registry } = fixture();
    let allowed = true;
    const seen = [];
    registry.registerContextMenu('test', 'conditional', { shouldAdd(uri, contextUri) {
        seen.push([uri, contextUri]); return allowed;
    } });
    registry.registerContextMenu('other', 'always', {});
    registry.registerContextMenu('other', 'throws', { shouldAdd() { throw new Error('bad extension'); } });
    registry.registerContextMenu('other', 'truthy', { shouldAdd() { return 1; } });
    registry.registerContextMenu('other', 'async', { shouldAdd() { return Promise.resolve(true); } });
    registry.registerContextMenu('other', 'disabled', { disabled: true, shouldAdd() { throw new Error('must not run'); } });
    const ids = ['conditional', 'throws', 'always', 'truthy', 'async', 'disabled', 'missing'];
    assert.deepEqual(registry.evaluateContextMenus(ids, 'spotify:track:a', 'spotify:playlist:b'), ['conditional', 'always']);
    allowed = false;
    assert.deepEqual(registry.evaluateContextMenus(ids, 'spotify:track:c', ''), ['always']);
    assert.deepEqual(seen, [['spotify:track:a', 'spotify:playlist:b'], ['spotify:track:c', '']]);
    registry.unregisterScript('other');
    assert.deepEqual(registry.evaluateContextMenus(ids, '', ''), []);
});

test('context menu constructor preserves legacy arguments and accepts one or multiple types', () => {
    const registry = new ScriptRegistry(logger);
    const calls = [];
    const runtime = { registry, ui, platformData: {}, session: {}, registerContextMenu(...args) { calls.push(args); } };
    const api = new ScriptApiFactory(runtime, logger).create('test', 1, path.resolve('tools/tests/fixtures/api2'), path.resolve('tools/tests/fixtures/api2'), []).SpotifyPlus;
    const click = () => {};
    const predicate = () => true;
    new api.ContextMenu('Legacy', click, predicate, false).register();
    new api.ContextMenu('Track', click, undefined, false, 'track').register();
    const types = ['album', 'playlist'];
    new api.ContextMenu('Multiple', click, predicate, false, types).register();
    types.push('artist');
    assert.equal(calls[0][2].shouldAdd, predicate);
    assert.equal(calls[0][2].types, undefined);
    assert.deepEqual(calls[1][2].types, ['track']);
    assert.deepEqual(calls[2][2].types, ['album', 'playlist']);
    assert.throws(() => new api.ContextMenu('Invalid', click, undefined, false, 'episode'), /Invalid context menu type/);
});
