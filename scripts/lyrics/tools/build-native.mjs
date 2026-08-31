import { spawnSync } from 'node:child_process';
import { copyFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const toolsDirectory = path.dirname(fileURLToPath(import.meta.url));
const extensionDirectory = path.resolve(toolsDirectory, '..');
const nativeDirectory = path.join(extensionDirectory, 'src', 'native');
const gradleCommand = process.platform === 'win32'
    ? path.join(nativeDirectory, 'gradlew.bat')
    : path.join(nativeDirectory, 'gradlew');
const result = spawnSync(
    gradleCommand,
    [':app:assembleRelease'],
    {
        cwd: nativeDirectory,
        encoding: 'utf8',
        shell: process.platform === 'win32',
        stdio: 'inherit',
    },
);

if (result.error) throw result.error;
if (result.status !== 0) {
    throw new Error(`Lyrics native build exited with code ${result.status}`);
}

const sourceApk = path.join(
    nativeDirectory,
    'app',
    'build',
    'outputs',
    'apk',
    'release',
    'app-release-unsigned.apk',
);
const destinationApk = path.join(extensionDirectory, 'lyrics.apk');
await mkdir(path.dirname(destinationApk), { recursive: true });
await copyFile(sourceApk, destinationApk);
