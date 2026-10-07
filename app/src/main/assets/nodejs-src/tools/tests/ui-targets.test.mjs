import assert from 'node:assert/strict';
import test from 'node:test';
import { createRequire } from 'node:module';
const React = createRequire(new URL('../../../nodejs/ui/renderer.js', import.meta.url))('react');
import { UITargetRegistry } from '../../../nodejs/ui/target-registry.js';
import { setCommitListener } from '../../../nodejs/ui/renderer.js';

const tick = () => new Promise(resolve => setImmediate(resolve));
const descriptor = (id, title = id) => ({ id, target: 'contextMenu.header', context: { instanceId: id, title, uri: null, pageUri: null }, operations: ['replace', 'before', 'after', 'overlay'] });
function fixture(deferred = false, snapshot = []) {
    const requests = [], commits = new Map(), pending = [], errors = [];
    let sequence = 0;
    const registry = new UITargetRegistry({
        request(operation, args) {
            requests.push({ operation, ...args });
            if (operation === 'ui.attach') {
                const result = { surfaceId: `surface:${++sequence}` };
                return deferred ? new Promise(resolve => pending.push(() => resolve(result))) : Promise.resolve(result);
            }
            if (operation === 'ui.inspect') return Promise.resolve({ name: args.target, available: true, operations: ['replace', 'before', 'after', 'overlay'], instances: 0, conflicts: [] });
            if (operation === 'ui.snapshot') return Promise.resolve(snapshot);
            if (operation === 'ui.invokeAction' && args.partId === 'expired') return Promise.reject(new Error('Native part has expired'));
            return Promise.resolve(null);
        },
        registerSurface(id) { commits.set(id, []); setCommitListener(id, ops => commits.get(id).push(...ops)); },
        unregisterSurface() {}, report(owner, error) { errors.push({ owner, error }); },
    });
    return { registry, requests, commits, pending, errors };
}
const text = value => React.createElement('Text', {}, value);

test('two instances preserve component state during context updates and resolve replacement precedence', async () => {
    const { registry, commits } = fixture();
    let mounts = 0;
    function Header({ context, Original }) {
        const [state] = React.useState(() => ++mounts);
        return React.createElement('View', {}, text(`${context.title}:${state}`), React.createElement(Original));
    }
    const a = registry.forExtension('a', 1), b = registry.forExtension('b', 1);
    a.replace('contextMenu.header', Header);
    b.replace('contextMenu.header', () => text('second replacement'));
    b.insertAfter('contextMenu.header', () => text('insertion'));
    registry.open(descriptor('one')); registry.open(descriptor('two'));
    await tick();
    assert.equal(mounts, 2);
    registry.open(descriptor('one', 'changed'));
    assert.equal(mounts, 2);
    assert.ok(commits.get('surface:1').some(op => op.op === 'updateText' && op.text === 'changed:1'));
    assert.ok(!commits.get('surface:2').some(op => op.text === 'changed:1'));
    const info = await a.inspect('contextMenu.header');
    assert.equal(info.conflicts.length, 2);
    assert.equal(info.conflicts[0].winner, 'a');
    registry.setOrder(['b', 'a']);
    assert.equal((await a.inspect('contextMenu.header')).conflicts[0].winner, 'b');
    registry.close('one'); registry.close('two'); await tick();
});

test('instance discovery filters targets and returns isolated parts snapshots', async () => {
    const first = { ...descriptor('menu'), target: 'contextMenu.root' };
    first.context.parts = [{ id: 'sleep', semanticId: 'sleep-timer', title: 'Sleep timer', kind: 'action', enabled: true }];
    const other = { ...descriptor('drawer'), target: 'navigation.drawer' };
    const { registry } = fixture(false, [first, other]);
    const api = registry.forExtension('a', 1);
    const instances = await api.listInstances('contextMenu.root');
    assert.equal(instances.length, 1);
    assert.equal(instances[0].context.instanceId, 'menu');
    instances[0].context.parts[0].title = 'Mutated';
    assert.equal(first.context.parts[0].title, 'Sleep timer');
    assert.equal((await api.listInstances('settings.page')).length, 0);
    await assert.rejects(api.listInstances({ screen: 'home.page' }), /Expected a named UI target/);
});

test('native parts stay scoped to an instance and remount when the model replaces an opaque ID', async () => {
    const { registry, commits, errors } = fixture();
    let mounts = 0;
    const first = { ...descriptor('one'), target: 'contextMenu.root' };
    first.context.parts = [{ id: 'first', semanticId: 'sleep-timer', title: 'Sleep timer', kind: 'action', enabled: true }];
    registry.forExtension('a', 1).replace('contextMenu.root', ({ context, NativePart }) => {
        React.useState(() => ++mounts);
        return React.createElement(NativePart, { id: context.parts[0].id });
    });
    registry.open(first); await tick();
    assert.ok(commits.get('surface:1').some(op => op.type === 'NativePart' && op.props.partId === 'first'));
    registry.open({ ...first, context: { ...first.context, parts: [{ ...first.context.parts[0], id: 'second' }] } });
    assert.equal(mounts, 1, 'retain replacement state while refreshing a native model');
    assert.ok(commits.get('surface:1').some(op => op.type === 'NativePart' && op.props.partId === 'second'));
    assert.deepEqual(errors, []);
    registry.forExtension('b', 1).replace('navigation.drawer', ({ NativePart }) => React.createElement(NativePart, { id: 'second' }));
    registry.open({ ...descriptor('two'), target: 'navigation.drawer', context: { ...descriptor('two').context, parts: [] } });
    await tick();
    assert.equal(errors.length, 1);
    assert.match(String(errors[0].error), /expired or belongs to another target/);
    registry.close('one'); registry.close('two'); await tick();
});

test('action dispatch forwards live IDs, propagates native expiry, and revokes unloaded callers', async () => {
    const { registry, requests } = fixture();
    const api = registry.forExtension('a', 1);
    await api.invokeAction('menu', 'sleep');
    assert.deepEqual(requests.at(-1), { operation: 'ui.invokeAction', instanceId: 'menu', partId: 'sleep' });
    await assert.rejects(api.invokeAction('menu', 'expired'), /Native part has expired/);
    await assert.rejects(api.invokeAction('', 'sleep'), /live instanceId/);
    registry.unregister('a');
    const count = requests.length;
    await assert.rejects(api.invokeAction('menu', 'sleep'), /unloaded/);
    assert.equal(requests.length, count);
});

test('unload revokes stale registrations and a pending attach is detached before remount', async () => {
    const { registry, requests, pending, errors } = fixture(true);
    const old = registry.forExtension('a', 1);
    old.replace('contextMenu.header', () => text('old'));
    registry.open(descriptor('one'));
    registry.unregister('a');
    assert.throws(() => old.replace('contextMenu.header', () => null), /unloaded/);
    registry.forExtension('a', 2).replace('contextMenu.header', () => text('new'));
    assert.equal(pending.length, 1, 'do not attach a second host while the old request is pending');
    pending.shift()(); await tick();
    assert.deepEqual(requests.filter(r => r.operation !== 'ui.inspect').map(r => r.operation), ['ui.attach', 'ui.detach', 'ui.attach']);
    pending.shift()(); await tick();
    registry.close('one'); await tick();
    assert.equal(requests.at(-1).surfaceId, 'surface:2');
    assert.deepEqual(errors, []);
});

test('closing before attach completion never registers a renderer', async () => {
    const { registry, pending, commits, requests } = fixture(true);
    registry.forExtension('a', 1).replace('contextMenu.header', () => null);
    registry.open(descriptor('one')); registry.close('one');
    pending.shift()(); await tick();
    assert.equal(commits.size, 0);
    assert.equal(requests.at(-1).operation, 'ui.detach');
});

test('screen-scoped selectors match only a verified alias on the exact screen', async () => {
    const { registry, commits } = fixture();
    registry.forExtension('a', 1).replace({ screen: 'nowPlaying.page', resourceId: 'com.spotify.music:id/content' }, () => text('matched'));
    registry.open({ ...descriptor('one'), target: 'nowPlaying.page', selectors: [{ screen: 'nowPlaying.page', resourceId: 'com.spotify.music:id/content' }] });
    registry.open({ ...descriptor('two'), target: 'home.page', selectors: [{ screen: 'home.page', resourceId: 'com.spotify.music:id/content' }] });
    registry.open({ ...descriptor('three'), target: 'nowPlaying.page' });
    await tick();
    assert.equal(commits.size, 1);
    for (const id of ['one', 'two', 'three']) registry.close(id);
    await tick();
});

test('native failure restores one instance without retrying it or disturbing another instance', async () => {
    const { registry, requests, commits, errors } = fixture();
    registry.forExtension('a', 1).replace('contextMenu.header', ({ context }) => text(context.title));
    registry.open(descriptor('one')); registry.open(descriptor('two')); await tick();
    registry.fail('one', 'surface:1', 'invalid native slot'); await tick();
    registry.open(descriptor('one', 'changed')); registry.open(descriptor('two', 'still alive')); await tick();
    assert.equal(requests.filter(r => r.operation === 'ui.attach').length, 2);
    assert.equal(errors.length, 1);
    assert.ok(commits.get('surface:2').some(op => op.text === 'still alive'));
    registry.close('one'); registry.close('two'); await tick();
});
