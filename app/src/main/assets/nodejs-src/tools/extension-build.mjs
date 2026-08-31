import fs from "node:fs/promises";
import path from "node:path";
import { build } from "esbuild";
import {
    SPOTIFYPLUS_WORKLET_BUNDLE_MARKER,
    spotifyPlusWorkletsPlugin,
} from "./worklet-transform.mjs";

const SOURCE_CANDIDATES = [
    "src/index.tsx",
    "src/index.ts",
    "src/index.jsx",
    "src/index.js",
    "index.tsx",
    "index.ts",
    "index.jsx",
    "index.js",
];

const DEFAULT_EXTERNALS = [
    "react",
    "react/*",
    "spotifyplus",
    "spotifyplus/*",
];

const BLOCKED_USER_NODE_MODULES = new Set([
    "child_process",
    "cluster",
    "dgram",
    "fs",
    "fs/promises",
    "inspector",
    "module",
    "net",
    "process",
    "repl",
    "tls",
    "vm",
    "worker_threads",
]);

function userModulePermissionsPlugin() {
    return {
        name: "spotifyplus-user-module-permissions",
        setup(buildContext) {
            buildContext.onResolve({ filter: /.*/ }, args => {
                const normalized = args.path.startsWith("node:") ? args.path.slice(5) : args.path;
                if (!BLOCKED_USER_NODE_MODULES.has(normalized)) return null;
                return {
                    errors: [{
                        text: `User extensions cannot import privileged Node module '${args.path}'. Use SpotifyPlus APIs or injected platform globals instead.`,
                    }],
                };
            });
        },
    };
}

async function isFile(filePath) {
    try {
        return (await fs.stat(filePath)).isFile();
    } catch (error) {
        if (error?.code === "ENOENT") return false;
        throw error;
    }
}

function resolveFromScriptDir(scriptDir, candidate) {
    return path.isAbsolute(candidate)
        ? path.normalize(candidate)
        : path.resolve(scriptDir, candidate);
}

function manifestApi(manifest) {
    const api = manifest.api ?? 1;
    if (!Number.isInteger(api) || api < 1) throw new Error("manifest.api must be a positive integer when provided");
    return api;
}

function normalizeAssetPath(value, fieldName) {
    if (typeof value !== "string" || value.trim().length === 0) {
        throw new Error(`${fieldName} must be a non-empty string`);
    }
    if (path.isAbsolute(value) || /^[a-zA-Z]:/.test(value)) {
        throw new Error(`${fieldName} must be relative to the extension directory`);
    }
    const normalized = value.trim().replaceAll("\\", "/").replace(/^\.\//, "");
    const segments = normalized.split("/");
    if (segments.some(segment => !segment || segment === "." || segment === "..")) {
        throw new Error(`${fieldName} cannot escape the extension directory`);
    }
    return normalized;
}

function assetPatternRegExp(pattern) {
    let expression = "";
    for (let index = 0; index < pattern.length; index += 1) {
        const character = pattern[index];
        if (character === "*" && pattern[index + 1] === "*") {
            const followedBySlash = pattern[index + 2] === "/";
            expression += followedBySlash ? "(?:.*/)?" : ".*";
            index += followedBySlash ? 2 : 1;
            continue;
        }
        if (character === "*") {
            expression += "[^/]*";
            continue;
        }
        if (character === "?") {
            expression += "[^/]";
            continue;
        }
        expression += /[.+^${}()|[\]\\]/.test(character) ? `\\${character}` : character;
    }
    return new RegExp(`^${expression}$`);
}

async function collectFiles(directory, root = directory) {
    const files = [];
    for (const entry of await fs.readdir(directory, { withFileTypes: true })) {
        if (entry.name === "node_modules" || entry.name === ".git") continue;
        const absolutePath = path.join(directory, entry.name);
        if (entry.isSymbolicLink()) {
            throw new Error(`Extension assets cannot be symbolic links: ${absolutePath}`);
        }
        if (entry.isDirectory()) files.push(...await collectFiles(absolutePath, root));
        else if (entry.isFile()) files.push({
            absolutePath,
            relativePath: path.relative(root, absolutePath).replaceAll(path.sep, "/"),
        });
    }
    return files;
}

async function collectPatternFiles(scriptDir, patterns) {
    const candidates = new Map();
    for (const item of patterns) {
        const segments = item.pattern.split("/");
        const literalSegments = [];
        for (const segment of segments) {
            if (/[*?]/.test(segment)) break;
            literalSegments.push(segment);
        }
        const searchPath = literalSegments.length > 0
            ? path.join(scriptDir, ...literalSegments)
            : scriptDir;
        let stat;
        try {
            stat = await fs.stat(searchPath);
        } catch (error) {
            if (error?.code === "ENOENT") continue;
            throw error;
        }
        if (stat.isFile()) {
            candidates.set(searchPath, {
                absolutePath: searchPath,
                relativePath: path.relative(scriptDir, searchPath).replaceAll(path.sep, "/"),
            });
            continue;
        }
        if (!stat.isDirectory()) continue;
        for (const file of await collectFiles(searchPath, scriptDir)) {
            candidates.set(file.absolutePath, file);
        }
    }
    return [...candidates.values()];
}

async function resolveDeclaredAssetFiles(scriptDir, manifest) {
    if (manifest.assets === undefined) return [];
    if (!Array.isArray(manifest.assets)) throw new Error("manifest.assets must be an array of relative glob patterns");

    const patterns = manifest.assets.map((pattern, index) => ({
        pattern: normalizeAssetPath(pattern, `manifest.assets[${index}]`),
        expression: null,
        matches: 0,
    }));
    for (const item of patterns) item.expression = assetPatternRegExp(item.pattern);

    const includedFiles = [];
    for (const file of await collectPatternFiles(scriptDir, patterns)) {
        let included = false;
        for (const item of patterns) {
            if (!item.expression.test(file.relativePath)) continue;
            item.matches += 1;
            included = true;
        }
        if (!included) continue;

        includedFiles.push(file);
    }

    const unmatched = patterns.filter(item => item.matches === 0).map(item => item.pattern);
    if (unmatched.length > 0) {
        throw new Error(`manifest.assets patterns matched no files: ${unmatched.join(", ")}`);
    }
    return includedFiles;
}

async function copyDeclaredAssets(scriptDir, outputDirectory, manifest) {
    const copied = [];
    for (const file of await resolveDeclaredAssetFiles(scriptDir, manifest)) {
        const destination = path.join(outputDirectory, ...file.relativePath.split("/"));
        if (path.resolve(destination) !== path.resolve(file.absolutePath)) {
            await fs.mkdir(path.dirname(destination), { recursive: true });
            await fs.copyFile(file.absolutePath, destination);
        }
        copied.push(destination);
    }
    return copied;
}

async function copyNativePackage(scriptDir, outputDirectory, manifest) {
    if (manifest.native === undefined) return null;
    if (!manifest.native || typeof manifest.native !== "object" || Array.isArray(manifest.native)) {
        throw new Error("manifest.native must be an object");
    }

    const relativePath = normalizeAssetPath(manifest.native.apk, "manifest.native.apk");
    const source = path.join(scriptDir, ...relativePath.split("/"));
    if (!await isFile(source)) {
        throw new Error(`Native APK file not found: ${source}`);
    }

    const destination = path.join(outputDirectory, ...relativePath.split("/"));
    if (path.resolve(destination) !== path.resolve(source)) {
        await fs.mkdir(path.dirname(destination), { recursive: true });
        await fs.copyFile(source, destination);
    }
    return destination;
}

export async function readDeclaredAssets(scriptDir, manifest) {
    const assets = [];
    for (const file of await resolveDeclaredAssetFiles(path.resolve(scriptDir), manifest)) {
        const data = await fs.readFile(file.absolutePath);
        assets.push({
            path: file.relativePath,
            data: data.toString("base64"),
            size: data.byteLength,
        });
    }
    return assets;
}

export async function readManifest(scriptDir) {
    const absoluteScriptDir = path.resolve(scriptDir);
    const manifestPath = path.join(absoluteScriptDir, "manifest.json");
    let manifest;

    try {
        manifest = JSON.parse(await fs.readFile(manifestPath, "utf8"));
    } catch (error) {
        if (error instanceof SyntaxError) throw new Error(`${manifestPath} is not valid JSON: ${error.message}`);
        if (error?.code === "ENOENT") throw new Error(`Missing extension manifest: ${manifestPath}`);
        throw error;
    }

    if (!manifest || typeof manifest !== "object" || Array.isArray(manifest)) {
        throw new Error(`${manifestPath} must contain a JSON object`);
    }
    if (typeof manifest.id !== "string" || manifest.id.trim().length === 0) {
        throw new Error("manifest.id must be a non-empty string");
    }
    if (typeof manifest.main !== "string" || manifest.main.trim().length === 0) {
        throw new Error("manifest.main must be a non-empty string");
    }

    manifestApi(manifest);
    if (manifest.assets !== undefined && !Array.isArray(manifest.assets)) {
        throw new Error("manifest.assets must be an array of relative glob patterns");
    }
    return manifest;
}

export async function resolveExtensionEntry(scriptDir, manifest, explicitEntry) {
    const absoluteScriptDir = path.resolve(scriptDir);
    const candidates = [];

    if (explicitEntry) candidates.push(explicitEntry);
    else {
        if (typeof manifest.source === "string" && manifest.source.trim()) candidates.push(manifest.source);
        candidates.push(...SOURCE_CANDIDATES, manifest.main);
    }

    for (const candidate of [...new Set(candidates)]) {
        const candidatePath = resolveFromScriptDir(absoluteScriptDir, candidate);
        if (await isFile(candidatePath)) return candidatePath;
    }

    if (explicitEntry) {
        throw new Error(`Extension entry does not exist: ${resolveFromScriptDir(absoluteScriptDir, explicitEntry)}`);
    }

    throw new Error(
        `Could not find an extension entry in ${absoluteScriptDir}. Add src/index.tsx, set manifest.source, or pass --entry.`,
    );
}

export function resolveExtensionOutput(scriptDir, manifest, explicitOutfile) {
    return resolveFromScriptDir(path.resolve(scriptDir), explicitOutfile ?? manifest.main);
}

export async function bundleExtension(options = {}) {
    const scriptDir = path.resolve(options.scriptDir ?? process.cwd());
    const manifest = options.manifest ?? await readManifest(scriptDir);
    const api = manifestApi(manifest);
    const entryPath = await resolveExtensionEntry(scriptDir, manifest, options.entryPath);
    const write = options.write !== false;
    const outfile = resolveExtensionOutput(scriptDir, manifest, options.outfile);
    const isApi2 = api >= 2;
    const plugins = [...(options.plugins ?? [])];

    plugins.unshift(userModulePermissionsPlugin());

    if (isApi2) {
        plugins.unshift(spotifyPlusWorkletsPlugin({
            globals: options.workletGlobals,
            rootDir: scriptDir,
            transformDependencies: options.transformDependencies === true,
        }));
    }

    const result = await build({
        absWorkingDir: scriptDir,
        banner: isApi2 ? { js: SPOTIFYPLUS_WORKLET_BUNDLE_MARKER } : undefined,
        bundle: true,
        entryPoints: [entryPath],
        external: [
            ...DEFAULT_EXTERNALS,
            ...(options.external ?? []),
        ],
        format: "cjs",
        jsx: "automatic",
        logLevel: options.logLevel ?? "silent",
        minify: options.minify === true,
        outfile,
        platform: "node",
        plugins,
        sourcemap: options.sourcemap ?? false,
        target: options.target ?? "es2020",
        write,
    });

    let manifestOutfile = null;
    let assetOutfiles = [];
    let nativeOutfile = null;
    if (write) {
        manifestOutfile = path.join(path.dirname(outfile), "manifest.json");
        const sourceManifestPath = path.join(scriptDir, "manifest.json");
        if (path.normalize(sourceManifestPath) !== path.normalize(manifestOutfile)) {
            await fs.mkdir(path.dirname(manifestOutfile), { recursive: true });
            await fs.copyFile(sourceManifestPath, manifestOutfile);
        }
        assetOutfiles = await copyDeclaredAssets(scriptDir, path.dirname(outfile), manifest);
        nativeOutfile = await copyNativePackage(scriptDir, path.dirname(outfile), manifest);
    }

    const source = write
        ? null
        : result.outputFiles?.find(file => file.path === outfile)?.text ?? result.outputFiles?.[0]?.text ?? null;
    if (!write && !source) throw new Error("esbuild did not produce an in-memory extension bundle");

    return {
        api,
        buildId: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        entryPath,
        manifest,
        manifestOutfile,
        assetOutfiles,
        nativeOutfile,
        outfile,
        result,
        source,
        scriptDir,
    };
}
