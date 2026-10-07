import assert from 'node:assert/strict';
import test from 'node:test';
import { HostRuntime } from '../../../nodejs/loader/host-runtime.js';
import { ScriptRegistry } from '../../../nodejs/loader/script-registry.js';

function fixture() {
    const listeners = new Map();
    const closed = [];
    const logger = { info() {}, error() {} };
    // Exercise host dispatch with a fake transport, without loading the Android addon.
    const runtime = Object.create(HostRuntime.prototype);
    runtime.registry = new ScriptRegistry(logger);
    runtime.bridge = {
        on: (name, handler) => listeners.set(name, handler),
        unregisterSurface: id => closed.push(id),
    };
    runtime.activeSideDrawer = { scriptId: 'settings', id: 'settings' };
    runtime.registerEventListeners();
    return { runtime, closed, back: () => listeners.get('android.backPressed')({ scriptId: 'settings', surfaceId: 'sideDrawer' }) };
}

test('Android back reaches only the owning extension and respects async preventDefault', async () => {
    const { runtime, closed, back } = fixture();
    let delivered = 0;
    runtime.registry.on('settings', 'android.backPressed', async event => {
        await Promise.resolve();
        assert.equal(event.surfaceId, 'sideDrawer');
        assert.equal(event.defaultPrevented, false);
        event.preventDefault();
        assert.equal(event.defaultPrevented, true);
        delivered++;
    });
    runtime.registry.on('other', 'android.backPressed', () => assert.fail('Back leaked to another extension'));
    await back();
    assert.equal(delivered, 1);
    assert.deepEqual(closed, []);
});

test('unhandled Android back closes the surface and unregisters native back handling', async () => {
    const { runtime, closed, back } = fixture();
    await back();
    assert.equal(runtime.activeSideDrawer, null);
    assert.deepEqual(closed, ['sideDrawer']);
});

test('an extension can explicitly close its surface while preventing default back', async () => {
    const { runtime, closed, back } = fixture();
    runtime.registry.on('settings', 'android.backPressed', event => {
        event.preventDefault();
        runtime.closeSurface('settings');
    });
    await back();
    assert.deepEqual(closed, ['sideDrawer']);
});
