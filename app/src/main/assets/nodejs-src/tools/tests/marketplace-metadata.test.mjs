import assert from 'node:assert/strict';
import test from 'node:test';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';

test('marketplace resolves repository previews and replaces legacy cached paths', async () => {
    const result = await build({
        entryPoints: [fileURLToPath(new URL('../../../../../../../scripts/marketplace/src/fetch-metadata.ts', import.meta.url))],
        bundle: true, platform: 'node', format: 'cjs', write: false,
        external: ['spotifyplus'],
    });
    let cached = JSON.stringify({ fetchedAt: Date.now(), data: [{ preview: 'screenshot.png' }] });
    let requests = 0;
    const module = { exports: {} };
    const context = vm.createContext({
        module, exports: module.exports, console,
        require: () => ({ SpotifyPlus: { Platform: { Storage: { Cache: {
            read: async () => cached,
            write: (_key, value) => { cached = value; },
        } } } } }),
        fetch: async url => {
            requests++;
            if (url.endsWith('/releases/latest')) return { ok: false, status: 404 };
            return { json: async () => [
                { id: 'preview', name: 'Preview', description: '', version: '1', api: 2, main: 'index.js', preview: 'screenshot.png', branch: 'release' },
                { id: 'no-preview', name: 'No preview', description: '', version: '1', api: 2, main: 'index.js' },
                { id: 'remote', name: 'Remote', description: '', version: '1', api: 2, main: 'index.js', preview: 'https://example.com/image.png' },
            ] };
        },
    });
    vm.runInContext(result.outputFiles[0].text, context);
    const entries = await module.exports.fetchManifest('owner', 'repo', 'main', 0, 'MIT');
    assert.equal(entries[0].preview, 'https://raw.githubusercontent.com/owner/repo/release/screenshot.png');
    assert.equal(entries[1].preview, undefined);
    assert.equal(entries[2].preview, 'https://example.com/image.png');
    await module.exports.fetchManifest('owner', 'repo', 'main', 0, 'MIT');
    assert.equal(requests, 2);
});
