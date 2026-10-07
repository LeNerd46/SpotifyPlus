import assert from 'node:assert/strict';
import test from 'node:test';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';

async function load(entry, globals = {}) {
    const result = await build({
        entryPoints: [fileURLToPath(new URL(`../../../../../../../scripts/marketplace/src/${entry}`, import.meta.url))],
        bundle: true, platform: 'node', format: 'cjs', write: false, external: ['spotifyplus', 'spotifyplus/*', 'react', 'react/*'],
    });
    const module = { exports: {} };
    vm.runInNewContext(result.outputFiles[0].text, { module, exports: module.exports, console, ...globals });
    return module.exports;
}

test('updates require a newer numeric version, respecting prereleases and build metadata', async () => {
    const { isNewerVersion, hasUpdate } = await load('updates.ts');
    for (const [available, installed, expected] of [
        ['1.10.0', '1.9.0', true], ['1.9.0', '1.10.0', false],
        ['2.0.0', '1.99.0', true], ['1.2.3.4', '1.2.3.3', true],
        ['v1.2', '1.2.0', false], ['1.0+new', '1.0+old', false],
        ['1.0', '1.0-rc.1', true], ['1.0-rc.1', '1.0', false],
        ['1.0-beta.10', '1.0-beta.2', true], ['1.0-beta.2', '1.0-beta.10', false],
        ['1.0-rc.1', '1.0-beta.9', true], ['1.0-beta.1', '1.0-beta', true],
        ['1.0-beta', '1.0-beta.1', false], ['latest', '1.0', false],
        ['1.0', undefined, false], ['1.0', 'unknown', false],
    ]) assert.equal(isNewerVersion(available, installed), expected, `${available} vs ${installed}`);
    const installed = { id: 'test', version: '1.0' };
    const extension = { id: 'test', version: '2.0', repository: { owner: 'owner', name: 'repo', branch: 'main' } };
    assert.equal(hasUpdate(installed, extension), true);
    assert.equal(hasUpdate(installed, { ...extension, repository: undefined }), false);
    assert.equal(hasUpdate(installed, { ...extension, id: 'other' }), false);
    assert.equal(hasUpdate({ ...installed, version: '2.0' }, extension), false);
});

test('startup catalog bypasses stale caches, is reused by pages, and can be rechecked', async () => {
    const requests = [];
    let version = '2.0';
    let fail = false;
    const { loadCatalog } = await load('catalog.ts', {
        require: () => ({ SpotifyPlus: { Platform: { Storage: { Cache: {
            read: () => { throw new Error('Startup must bypass caches'); }, write: () => {},
        } } } } }),
        fetch: async url => {
            requests.push(url);
            if (fail) throw new Error('offline');
            if (url.includes('/search/repositories')) return { json: async () => ({ total_count: 1, items: [{ owner: { login: 'owner' }, name: 'repo', default_branch: 'main', stargazers_count: 1 }] }) };
            if (url.endsWith('/releases/latest')) return { ok: false, status: 404 };
            return { ok: true, json: async () => ({ id: 'test', name: 'Test', description: '', version, api: 2, main: 'index.js', authors: [{ name: 'Author' }] }) };
        },
    });
    const startup = loadCatalog();
    assert.equal(loadCatalog(), startup);
    assert.equal((await startup).extensions[0].version, '2.0');
    assert.equal((await startup).incomplete, false);
    assert.equal(requests.length, 3);
    version = '3.0';
    assert.equal((await loadCatalog()).extensions[0].version, '2.0');
    assert.equal((await loadCatalog(true)).extensions[0].version, '3.0');
    fail = true;
    await assert.rejects(loadCatalog(true), /Could not check/);
    fail = false;
    assert.equal((await loadCatalog(true)).extensions[0].version, '3.0');
});

test('a failed repository check is marked incomplete, not reported as up to date', async () => {
    const { loadCatalog } = await load('catalog.ts', {
        require: () => ({ SpotifyPlus: { Platform: { Storage: { Cache: { write: () => {} } } } } }),
        fetch: async url => url.includes('/search/repositories')
            ? { json: async () => ({ items: [{ owner: { login: 'owner' }, name: 'repo', default_branch: 'main' }] }) }
            : { ok: false, status: 503 },
    });
    const result = await loadCatalog();
    assert.equal(result.incomplete, true);
    assert.equal(result.extensions.length, 0);
});

test('installed page places available updates before other installed extensions and refreshes after updating', async () => {
    let installed = [{ id: 'old', name: 'Old', version: '1.0' }, { id: 'current', name: 'Current', version: '2.0' }];
    const jsx = (type, props) => ({ type, props });
    const { default: InstalledPage } = await load('installed-page.tsx', {
        require: name => {
            if (name === 'react') return {
                useState: value => [Array.isArray(value) ? installed : value, () => {}],
                useMemo: compute => compute(), useEffect: () => {},
            };
            if (name === 'react/jsx-runtime') return { jsx, jsxs: jsx };
            if (name === 'spotifyplus') return { SpotifyPlus: { Assets: { image: path => path } } };
            if (name === 'spotifyplus/react') return { View: 'View', Text: 'Text', ScrollView: 'ScrollView', Image: 'Image', Pressable: 'Pressable' };
            if (name === 'spotifyplus/react/reanimated') return {};
            throw new Error(`Unexpected dependency ${name}`);
        },
    });
    const extensions = installed.map(item => ({ ...item, version: '2.0', repository: { owner: 'owner', name: 'repo', branch: 'main' } }));
    const render = () => InstalledPage({ extensions, checking: false, checkError: null, onCheckUpdates() {}, onInstalledChanged() {}, onSelectExtension() {} });
    const nodes = root => {
        const result = [];
        const visit = node => {
            if (Array.isArray(node)) return node.forEach(visit);
            if (!node || typeof node !== 'object') return;
            result.push(node);
            visit(node.props?.children);
        };
        visit(root);
        return result;
    };
    let rendered = nodes(render());
    assert.ok(rendered.some(node => Array.isArray(node.props.children) && node.props.children.join('') === 'Updates (1)'));
    assert.deepEqual(rendered.filter(node => node.props.installed).map(node => node.props.installed.id), ['old', 'current']);
    installed = installed.map(item => ({ ...item, version: '2.0' }));
    rendered = nodes(render());
    assert.ok(rendered.some(node => node.props.children === 'No updates found.'));
    assert.ok(!rendered.some(node => Array.isArray(node.props.children) && node.props.children.join('') === 'Updates (1)'));
});

test('installer rejects a pinned manifest that would downgrade the installed extension', async () => {
    let writes = 0;
    const { installMarketplaceExtension } = await load('install-extension.ts', {
        require: () => ({ SpotifyPlus: {} }),
        __spotifyplus_elevated__: {
            listInstalledExtensions: () => [{ id: 'test', version: '2.0' }],
            uninstallExtension() {}, installExtension() { writes++; },
        },
        fetch: async url => url.includes('/commits/')
            ? { ok: true, json: async () => ({ sha: 'commit' }) }
            : { ok: true, json: async () => ({ id: 'test', name: 'Test', description: '', version: '1.0', api: 2, main: 'index.js' }) },
    });
    await assert.rejects(installMarketplaceExtension({ id: 'test', version: '3.0', repository: { owner: 'owner', name: 'repo', branch: 'main' } }), /no longer offers a newer version/);
    assert.equal(writes, 0);
});
