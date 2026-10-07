import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { validateNativeApk } from '../../../nodejs/loader/native-apk.js';
import { HostRuntime } from '../../../nodejs/loader/host-runtime.js';

const apk = fs.readFileSync(new URL('../../../../../../../scripts/lyrics/lyrics.apk', import.meta.url));

test('native APK validation accepts a real APK and rejects empty, placeholder, and damaged downloads', () => {
    validateNativeApk(apk, 'lyrics.apk');
    for (const invalid of [Buffer.alloc(0), Buffer.from('\r\n'), Buffer.from('version https://git-lfs.github.com/spec/v1'), apk.subarray(0, apk.length - 1)]) {
        assert.throws(() => validateNativeApk(invalid, 'lyrics.apk'), /Invalid native APK lyrics.apk/);
    }
    const damaged = Buffer.from(apk);
    const name = damaged.indexOf(Buffer.from('classes.dex'), damaged.readUInt32LE(damaged.length - 6));
    assert.ok(name >= 0);
    damaged[name] = 120;
    assert.throws(() => validateNativeApk(damaged, 'lyrics.apk'), /classes.dex is missing/);
});

test('an invalid native APK leaves the previous install and active extension untouched', () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'spotifyplus-apk-'));
    const manifest = { id: 'test.lyrics', name: 'Lyrics', version: '2', api: 2, main: 'dist/index.js', native: { apk: 'lyrics.apk', pluginClass: 'example.Plugin' } };
    const target = path.join(root, manifest.id);
    fs.mkdirSync(target);
    fs.writeFileSync(path.join(target, 'manifest.json'), 'previous install');
    const runtime = Object.create(HostRuntime.prototype);
    runtime.config = { installedRoot: root };
    runtime.scriptLoader = { preflightSource() { assert.fail('APK must be validated before activation'); } };
    runtime.registry = { getScript() { assert.fail('Active extension must remain untouched'); } };
    try {
        assert.throws(() => runtime.installExtension({ manifest, files: [
            { path: 'dist/index.js', data: Buffer.from('module.exports = {};') },
            { path: 'dist/lyrics.apk', data: Buffer.from('\r\n') },
        ] }), /Invalid native APK dist\/lyrics.apk/);
        assert.equal(fs.readFileSync(path.join(target, 'manifest.json'), 'utf8'), 'previous install');
        assert.deepEqual(fs.readdirSync(root), [manifest.id]);
    } finally {
        fs.rmSync(root, { recursive: true, force: true });
    }
});
