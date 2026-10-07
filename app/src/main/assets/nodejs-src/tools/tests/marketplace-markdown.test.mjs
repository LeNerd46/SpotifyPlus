import assert from 'node:assert/strict';
import test from 'node:test';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';
import { parseAsync } from '@babel/core';
import { bundleExtension } from '../extension-build.mjs';

const sourceFile = name => fileURLToPath(new URL(`../../../../../../../scripts/marketplace/src/${name}`, import.meta.url));
const jsx = (type, props) => ({ type, props });
const opened = [];
const react = { useMemo: compute => compute(), useState: value => [value, () => {}] };
const sdk = { SpotifyPlus: { Navigation: { open: url => opened.push(url) } } };

test('the production marketplace bundle avoids unsupported Unicode regexes and registers its drawer button', async () => {
    const scriptDir = fileURLToPath(new URL('../../../../../../../scripts/marketplace/', import.meta.url));
    const bundle = await bundleExtension({ scriptDir, write: false });
    const ast = await parseAsync(bundle.source, { babelrc: false, configFile: false });
    let regexCount = 0;
    function inspect(node) {
        if (!node || typeof node !== 'object') return;
        if (node.type === 'RegExpLiteral') {
            regexCount++;
            assert.doesNotMatch(node.pattern, /\\[pP]\{/, 'Android cannot parse Unicode property escapes');
        }
        for (const value of Object.values(node)) {
            if (Array.isArray(value)) value.forEach(inspect);
            else if (value && typeof value === 'object') inspect(value);
        }
    }
    inspect(ast);
    assert.ok(regexCount > 0);
    let registered = false;
    let startupChecks = 0;
    const module = { exports: {} };
    const noIcuRegExp = function(pattern, flags) {
        assert.doesNotMatch(String(pattern), /\\[pP]\{/);
        return new RegExp(pattern, flags);
    };
    vm.runInNewContext(bundle.source, {
        module, exports: module.exports, URL, console, RegExp: noIcuRegExp,
        fetch: async () => {
            startupChecks++;
            return { json: async () => ({ items: [] }) };
        },
        require: name => {
            if (name === 'spotifyplus') return { SpotifyPlus: {
                Assets: { image: path => path },
                Platform: { Storage: { Cache: { write: () => {} } } },
                SideDrawer: class {
                    constructor(name, onClick) { assert.equal(name, 'Marketplace'); assert.equal(typeof onClick, 'function'); }
                    register() { registered = true; }
                },
            } };
            if (name === 'react') return react;
            if (name === 'react/jsx-runtime') return { jsx, jsxs: jsx };
            if (name === 'spotifyplus/react/reanimated') return {};
            if (name === 'spotifyplus/react') return {};
            throw new Error(`Unexpected startup dependency ${name}`);
        },
    });
    assert.equal(registered, true);
    assert.equal(startupChecks, 1, 'checks for updates before the marketplace drawer opens');

    const model = await bundleExtension({ scriptDir, entryPath: 'src/markdown-model.ts', write: false });
    const modelModule = { exports: {} };
    vm.runInNewContext(model.source, { module: modelModule, exports: modelModule.exports, URL, RegExp: noIcuRegExp });
    const tokens = modelModule.exports.parseMarkdown('中文**加粗** — **bold**\n\n| A | B |\n| - | - |\n| 1 | 2 |');
    assert.ok(tokens.some(token => token.type === 'table'));
    assert.ok(tokens[0].tokens.some(token => token.type === 'strong' && token.text === '加粗'));
});

async function load(name, extras = {}) {
    const bundle = await build({ entryPoints: [sourceFile(name)], bundle: true, platform: 'node', format: 'cjs', write: false,
        external: ['spotifyplus', 'spotifyplus/*', 'react', 'react/*'] });
    const module = { exports: {} };
    vm.runInNewContext(bundle.outputFiles[0].text, {
        module, exports: module.exports, URL, console,
        require: name => {
            if (name === 'spotifyplus') return sdk;
            if (name === 'react') return react;
            if (name === 'react/jsx-runtime') return { jsx, jsxs: jsx };
            if (name === 'spotifyplus/react') return Object.fromEntries(['Text', 'View', 'Image', 'Pressable', 'HorizontalScrollView'].map(type => [type, type]));
            throw new Error(`Unexpected external ${name}`);
        }, ...extras,
    });
    return module.exports;
}

function render(node) {
    if (Array.isArray(node)) return node.flatMap(item => render(item));
    if (node == null || typeof node !== 'object') return node;
    if (typeof node.type === 'function') return render(node.type(node.props));
    return { ...node, props: { ...node.props, children: render(node.props.children) } };
}

function elements(node, type) {
    if (Array.isArray(node)) return node.flatMap(item => elements(item, type));
    if (!node || typeof node !== 'object') return [];
    return [...(node.type === type ? [node] : []), ...elements(node.props.children, type)];
}
const textContent = node => Array.isArray(node) ? node.map(textContent).join('')
    : node && typeof node === 'object' ? textContent(node.props.children) : typeof node === 'string' ? node : '';

test('GFM renders nested Text with inherited emphasis, links, strike, code, and decoded entities', async () => {
    const { Markdown } = await load('markdown.tsx');
    const tree = render(Markdown({ source: '**bold _italic_** ~~[old](guide.md)~~ `a &amp; <b>` &copy; &amp;', baseUrl: 'https://example.com/docs/' }));
    const texts = elements(tree, 'Text');
    const bold = texts.find(text => text.props.fontWeight === 'bold' && textContent(text).startsWith('bold'));
    assert.ok(elements(bold, 'Text').some(text => text.props.fontStyle === 'italic'), 'italic must be nested inside bold Text');
    const strike = texts.find(text => text.props.textDecorationLine === 'line-through');
    const link = elements(strike, 'Text').find(text => text.props.onPress);
    link.props.onPress();
    assert.equal(opened.at(-1), 'https://example.com/docs/guide.md');
    assert.equal(textContent(tree), 'bold italic old a &amp; <b> © &');
    assert.ok(texts.some(text => text.props.fontFamily === 'monospace'));
    assert.equal(elements(tree, 'View').length, 4, 'inline formatting must not create separate View layouts');
});

test('GFM block structures include ordered/nested/task lists, tables, quotes, fences, and hard breaks', async () => {
    const { Markdown } = await load('markdown.tsx');
    const tree = render(Markdown({ source: '# Heading\n\n3. first\n   - [x] nested\n4. second\n\n> quote\n\n---\n\n| left | right |\n| :--- | ---: |\n| **A** | B |\n\n```js\nconst x = "<b>&amp;";\n```\n\nsoft\nline  \nhard<br>break' }));
    const texts = elements(tree, 'Text');
    assert.ok(texts.some(text => text.props.fontSize === 32));
    assert.ok(textContent(tree).includes('3.first☑nested4.second'));
    assert.ok(elements(tree, 'View').some(view => view.props.style?.borderLeftWidth === 4));
    assert.equal(elements(tree, 'HorizontalScrollView').length, 2);
    assert.ok(texts.some(text => text.props.textAlign === 'right'));
    assert.ok(texts.some(text => text.props.text === 'const x = "<b>&amp;";'));
    assert.ok(textContent(tree).endsWith('soft line\nhard\nbreak'));
});

test('relative images and reference links resolve; executable URLs and raw HTML stay inert', async () => {
    const { Markdown } = await load('markdown.tsx');
    const tree = render(Markdown({ source: '[docs][ref]\n\n[ref]: ../README.md\n\n![preview](images/screen.png)\n\n[bad](javascript:alert) [data](data:text/html,bad) <script>alert(1)</script>', baseUrl: 'https://example.com/docs/' }));
    const links = elements(tree, 'Text').filter(text => text.props.onPress);
    assert.equal(links.length, 1);
    links[0].props.onPress();
    assert.equal(opened.at(-1), 'https://example.com/README.md');
    assert.equal(elements(tree, 'Image')[0].props.source, 'https://example.com/docs/images/screen.png');
    assert.ok(textContent(tree).includes('<script>alert(1)</script>'));
    const { resolveMarkdownUrl } = await load('markdown-model.ts');
    for (const url of ['javascript:alert(1)', 'java&#x73;cript:bad', 'data:image/png;base64,x', 'file:///private', '#heading', 'java\nscript:bad']) {
        assert.equal(resolveMarkdownUrl(url, 'https://example.com/'), undefined);
    }
    assert.equal(resolveMarkdownUrl('mailto:dev@example.com'), 'mailto:dev@example.com');
    assert.equal(resolveMarkdownUrl('mailto:dev@example.com', undefined, true), undefined);
});

test('latest release body supplies every extension changelog and replaces old file caches', async () => {
    let cached = JSON.stringify({ schemaVersion: 3, fetchedAt: Date.now(), data: [{ changelog: 'old file contents' }] });
    const requests = [];
    const body = "Intro\r\n\r\n## What's New\r\n- **Added** Markdown";
    sdk.SpotifyPlus.Platform = { Storage: { Cache: { read: async () => cached, write: (_key, value) => { cached = value; } } } };
    const { fetchManifest } = await load('fetch-metadata.ts', { fetch: async (url, options) => {
        requests.push(String(url));
        if (url === 'https://api.github.com/repos/owner/repo/releases/latest') {
            assert.equal(options.headers.Accept, 'application/vnd.github+json');
            return { ok: true, json: async () => ({ body, tag_name: 'v1.2.3' }) };
        }
        assert.equal(url, 'https://raw.githubusercontent.com/owner/repo/main/manifest.json');
        return { json: async () => ['inline', 'file', 'none'].map((id, index) => ({
            id, name: id, description: '', version: '1', api: 2, main: 'index.js', branch: 'release',
            changelog: ['# Old inline body', 'notes/CHANGELOG.md', undefined][index],
        })) };
    } });
    const entries = await fetchManifest('owner', 'repo', 'main', 0, 'MIT');
    for (const entry of entries) {
        assert.equal(entry.changelog, body);
        assert.equal(entry.changelogBaseUrl, 'https://raw.githubusercontent.com/owner/repo/v1.2.3/');
    }
    assert.equal(requests.length, 2, 'only one release request for the three extensions');
    await fetchManifest('owner', 'repo', 'main', 0, 'MIT');
    assert.equal(requests.length, 2);
    assert.equal(JSON.parse(cached).schemaVersion, 4);
});

test('missing releases, empty or invalid bodies, and failed requests preserve extension metadata without a changelog', async () => {
    for (const release of [
        { ok: false, status: 404 },
        { ok: true, json: async () => ({ body: null }) },
        { ok: true, json: async () => ({ body: ' \r\n\t' }) },
        { ok: true, json: async () => ({ body: 42 }) },
        { ok: true, json: async () => null },
        { ok: false, status: 403 },
        { ok: true, json: async () => { throw new Error('Invalid JSON'); } },
        new Error('Network failed'),
    ]) {
        sdk.SpotifyPlus.Platform.Storage.Cache = { read: async () => null, write: () => {} };
        const { fetchManifest } = await load('fetch-metadata.ts', {
            console: { ...console, warn: () => {} },
            fetch: async url => {
                if (url.endsWith('/releases/latest')) {
                    if (release instanceof Error) throw release;
                    return release;
                }
                assert.ok(url.endsWith('/manifest.json'), 'never fetch a changelog file');
                return { json: async () => ({ id: 'extension', name: 'Extension', description: '', version: '1',
                    api: 2, main: 'index.js', changelog: 'CHANGELOG.md' }) };
            },
        });
        const entries = await fetchManifest('owner', 'repo', 'main', 0, 'MIT');
        assert.equal(entries.length, 1);
        assert.equal(entries[0].changelog, undefined);
        assert.equal(entries[0].changelogBaseUrl, undefined);
    }
});

test('the whole changelog card is omitted without release notes and renders the release Markdown otherwise', async () => {
    const { default: ChangelogCard } = await load('changelog-card.tsx');
    for (const source of [undefined, '', ' \r\n\t']) assert.equal(render(ChangelogCard({ source })), null);
    const tree = render(ChangelogCard({ source: '## Release\n\n- **New** feature' }));
    assert.ok(textContent(tree).includes('ChangelogRelease•New feature'));
    assert.ok(elements(tree, 'Text').some(text => text.props.fontWeight === 'bold' && textContent(text) === 'New'));
});
