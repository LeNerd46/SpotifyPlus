import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

test('packaged runtime loads its renderer without development node_modules', async () => {
    const runtime = fileURLToPath(new URL('../../../nodejs/', import.meta.url));
    const isolated = await fs.mkdtemp(path.join(os.tmpdir(), 'spotifyplus-runtime-'));
    try {
        for (const directory of ['loader', 'core', 'bridge', 'ui', 'node_modules']) {
            await fs.cp(path.join(runtime, directory), path.join(isolated, directory), { recursive: true });
        }
        for (const mode of ['development', 'production']) {
            const result = spawnSync(process.execPath, ['--no-global-search-paths', '-e', `
                const assert = require('node:assert/strict');
                const React = require('./node_modules/react');
                const { ScriptRegistry } = require('./loader/script-registry');
                assert.equal(typeof ScriptRegistry, 'function');
                assert.equal(React.isValidElement(React.createElement('view')), true);
                assert.equal(typeof require('./node_modules/react-reconciler'), 'function');
            `], { cwd: isolated, env: { ...process.env, NODE_PATH: '', NODE_ENV: mode }, encoding: 'utf8' });
            assert.equal(result.status, 0, result.error?.message ?? result.stderr);
        }
    } finally {
        await fs.rm(isolated, { recursive: true, force: true });
    }
});
