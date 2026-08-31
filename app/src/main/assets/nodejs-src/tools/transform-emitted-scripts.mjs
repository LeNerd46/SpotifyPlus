import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { bundleExtension } from "./extension-build.mjs";
import { transformWorklets } from "./worklet-transform.mjs";

const sourceRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const sourceScriptsRoot = path.join(sourceRoot, "scripts");
const emittedScriptsRoot = path.resolve(sourceRoot, "../nodejs/scripts");

async function walk(directory) {
    const files = [];
    for (const entry of await fs.readdir(directory, { withFileTypes: true })) {
        const entryPath = path.join(directory, entry.name);
        if (entry.isDirectory()) files.push(...await walk(entryPath));
        else files.push(entryPath);
    }
    return files;
}

async function copyJsonAssets(sourceDir, outputDir) {
    for (const sourcePath of await walk(sourceDir)) {
        if (path.extname(sourcePath).toLowerCase() !== ".json") continue;
        const relativePath = path.relative(sourceDir, sourcePath);
        const outputPath = path.join(outputDir, relativePath);
        await fs.mkdir(path.dirname(outputPath), { recursive: true });
        await fs.copyFile(sourcePath, outputPath);
    }
}

async function sourcePathForOutput(sourceDir, outputDir, outputPath) {
    const relativePath = path.relative(outputDir, outputPath);
    const extensionlessPath = relativePath.slice(0, -path.extname(relativePath).length);
    for (const extension of [".tsx", ".ts", ".jsx", ".js"]) {
        const candidatePath = path.join(sourceDir, `${extensionlessPath}${extension}`);
        try {
            if ((await fs.stat(candidatePath)).isFile()) return candidatePath;
        } catch (error) {
            if (error?.code !== "ENOENT") throw error;
        }
    }
    return path.join(sourceDir, relativePath);
}

async function transformSupplementalOutputs(sourceDir, outputDir, mainPath) {
    let workletCount = 0;
    for (const outputPath of await walk(outputDir)) {
        if (path.extname(outputPath).toLowerCase() !== ".js") continue;
        if (path.resolve(outputPath) === path.resolve(mainPath)) continue;

        const input = await fs.readFile(outputPath, "utf8");
        const transformed = await transformWorklets(input, {
            filename: await sourcePathForOutput(sourceDir, outputDir, outputPath),
            rootDir: sourceDir,
        });
        await fs.writeFile(outputPath, `${transformed.code}\n`, "utf8");
        workletCount += transformed.workletCount;
    }
    return workletCount;
}

for (const entry of await fs.readdir(sourceScriptsRoot, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;

    const sourceDir = path.join(sourceScriptsRoot, entry.name);
    const manifestPath = path.join(sourceDir, "manifest.json");
    let manifest;
    try {
        manifest = JSON.parse(await fs.readFile(manifestPath, "utf8"));
    } catch (error) {
        if (error?.code === "ENOENT") continue;
        throw error;
    }

    const outputDir = path.join(emittedScriptsRoot, entry.name);
    await fs.mkdir(outputDir, { recursive: true });
    await copyJsonAssets(sourceDir, outputDir);

    const api = manifest.api ?? 1;
    if (api < 2) {
        console.log(`Preserved API ${api} script ${manifest.id} without worklet transformation`);
        continue;
    }

    const result = await bundleExtension({
        logLevel: "silent",
        manifest,
        outfile: path.join(outputDir, manifest.main),
        scriptDir: sourceDir,
        sourcemap: false,
        write: true,
    });
    const supplementalWorklets = await transformSupplementalOutputs(
        sourceDir,
        outputDir,
        result.outfile,
    );
    console.log(
        `Bundled API ${api} script ${manifest.id} -> ${result.outfile}; transformed ${supplementalWorklets} supplemental worklets`,
    );
}
