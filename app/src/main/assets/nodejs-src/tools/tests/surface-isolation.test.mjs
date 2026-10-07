import assert from 'node:assert/strict';
import test from 'node:test';
import { createRequire } from 'node:module';
const React = createRequire(new URL('../../../nodejs/ui/renderer.js', import.meta.url))('react');
import { ScriptRegistry } from '../../../nodejs/loader/script-registry.js';
import { HostRuntime } from '../../../nodejs/loader/host-runtime.js';
import { setCommitListener, clearCommitListener, dispatchReactEvent } from '../../../nodejs/ui/renderer.js';

const logger = { info() {}, error() {} };
const element = text => React.createElement('Text', {}, text);

test('same target type routes two mounted instances independently and retains React state on context updates', () => {
    const registry = new ScriptRegistry(logger);
    const batches = new Map([['menu:one', []], ['menu:two', []]]);
    const setters = new Map();
    let mounts = 0;
    function Header({ id, title }) {
        const [count, setCount] = React.useState(() => { mounts++; return 0; });
        setters.set(id, setCount);
        return element(`${title}:${count}`);
    }
    for (const [id, ops] of batches) setCommitListener(id, batch => ops.push(...batch));
    try {
        for (const id of batches.keys()) registry.mountSurface('extension', { id, type: 'contextMenu.header' }, React.createElement(Header, { id, title: id }));
        assert.equal(mounts, 2);
        for (const [id, ops] of batches) {
            assert.ok(ops.some(op => op.op === 'createText' && op.text === `${id}:0`), JSON.stringify(ops));
            ops.length = 0;
        }
        registry.mountSurface('extension', { id: 'menu:one', type: 'contextMenu.header' }, React.createElement(Header, { id: 'menu:one', title: 'updated' }));
        assert.equal(mounts, 2, 'context changes must not remount the component');
        assert.ok(batches.get('menu:one').some(op => op.op === 'updateText' && op.text === 'updated:0'));
        assert.equal(batches.get('menu:two').length, 0);
    } finally {
        registry.unregisterScript('extension');
        for (const id of batches.keys()) clearCommitListener(id);
    }
});

test('unloading one extension keeps another extension on the same surface live', () => {
    const runtime = Object.create(HostRuntime.prototype);
    runtime.registry = new ScriptRegistry(logger);
    runtime.bridge = { unregisterScript() {} };
    const batches = [];
    setCommitListener('shared', ops => batches.push(...ops));
    try {
        for (const id of ['first', 'second']) runtime.registry.mountSurface(id, { id: 'shared', type: 'header' }, element(id));
        runtime.unregisterScript('first');
        assert.equal(runtime.registry.hasMountedSurface('shared'), true);
        batches.length = 0;
        runtime.registry.mountSurface('second', { id: 'shared', type: 'header' }, element('still live'));
        assert.ok(batches.some(op => op.op === 'updateText' && op.text === 'still live'));
        runtime.unregisterScript('second');
        assert.equal(runtime.registry.hasMountedSurface('shared'), false);
    } finally { clearCommitListener('shared'); }
});

test('surface close and extension ownership use exact identifiers, not colon suffixes', () => {
    const registry = new ScriptRegistry(logger);
    const disposed = [];
    const root = id => ({ unmount: () => disposed.push(id), render() {}, getTree() {} });
    registry.trackMountedRoot('a', 'b:c', root('one'));
    registry.trackMountedRoot('a:b', 'c', root('two'));
    registry.unmountAllSurfaces('c');
    assert.deepEqual(disposed, ['two']);
    assert.equal(registry.hasMountedSurface('b:c'), true);
    registry.unregisterScript('a');
    assert.deepEqual(disposed, ['two', 'one']);
});

test('callbacks from an unmounted root cannot fire after the target reopens', () => {
    const registry = new ScriptRegistry(logger);
    let calls = 0;
    let eventId;
    setCommitListener('menu', ops => {
        for (const op of ops) if (op.op === 'createNode') eventId = op.props.onPress?.id;
    });
    try {
        registry.mountSurface('extension', { id: 'menu', type: 'header' }, React.createElement('Button', { onPress: () => calls++ }));
        assert.equal(typeof eventId, 'number');
        dispatchReactEvent(eventId, { surfaceId: 'another-menu' });
        assert.equal(calls, 0, 'a callback cannot be dispatched from another surface');
        dispatchReactEvent(eventId, { surfaceId: 'menu' });
        assert.equal(calls, 1);
        registry.unmountAllSurfaces('menu');
        dispatchReactEvent(eventId, {});
        assert.equal(calls, 1);
    } finally { registry.unregisterScript('extension'); clearCommitListener('menu'); }
});
