import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const sourceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const emittedRoot = path.resolve(sourceRoot, "../nodejs");
const expectedRoot = path.resolve(path.dirname(sourceRoot), "nodejs");

if (emittedRoot !== expectedRoot || path.basename(emittedRoot) !== "nodejs") {
    throw new Error(`Refusing to clean unexpected runtime output path: ${emittedRoot}`);
}

const generatedDirectories = [
    "bridge",
    "core",
    "loader",
    "scripts",
    "sdk",
    "ui",
];
const generatedHostFiles = [
    "host.d.ts",
    "host.d.ts.map",
    "host.js",
    "host.js.map",
];

async function removeGeneratedDirectory(directory) {
    try {
        await fs.rm(directory, { force: true, recursive: true });
    } catch (error) {
        if (error?.code !== "EBUSY" && error?.code !== "EPERM") throw error;

        let entries;
        try {
            entries = await fs.readdir(directory);
        } catch (readError) {
            if (readError?.code === "ENOENT") return;
            throw readError;
        }
        await Promise.all(entries.map(entry => fs.rm(path.join(directory, entry), {
            force: true,
            recursive: true,
        })));
    }
}

for (const relativePath of [...generatedDirectories, ...generatedHostFiles]) {
    const generatedPath = path.join(emittedRoot, relativePath);
    if (generatedDirectories.includes(relativePath)) {
        await removeGeneratedDirectory(generatedPath);
    } else {
        await fs.rm(generatedPath, { force: true });
    }
}

console.log(`Removed generated runtime JavaScript from ${emittedRoot}`);
