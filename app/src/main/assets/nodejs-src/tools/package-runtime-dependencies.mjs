import fs from 'node:fs/promises';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const sourceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const outputRoot = path.resolve(sourceRoot, '../nodejs/node_modules');
const require = createRequire(path.join(sourceRoot, 'package.json'));
const copied = new Set();

async function copyPackage(name) {
    if (copied.has(name)) return;
    copied.add(name);
    const manifestPath = require.resolve(`${name}/package.json`);
    const manifest = JSON.parse(await fs.readFile(manifestPath, 'utf8'));
    await fs.cp(path.dirname(manifestPath), path.join(outputRoot, name), { recursive: true });
    for (const dependency of Object.keys(manifest.dependencies ?? {})) {
        await copyPackage(dependency);
    }
}

// Only the on-device renderer dependencies belong in the APK, not build tools.
await copyPackage('react');
await copyPackage('react-reconciler');
console.log(`Packaged runtime dependencies: ${[...copied].join(', ')}`);
