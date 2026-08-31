import fs from "fs";
import path from "path";
import { build } from "esbuild";
import ts from "typescript";

const root = process.cwd();
const sourcePackagePath = path.join(root, "package.json");
const sourcePackage = JSON.parse(fs.readFileSync(sourcePackagePath, "utf8"));

const primaryOutDir = path.join(root, "dist");
const mirrorOutDir = path.resolve(root, "../nodejs/sdk");

const publicFiles = [
    "index.cjs",
    "dev.cjs",
    "index.d.ts",
    "components.js",
    "components.d.ts",
    "animated.js",
    "animated.d.ts",
    "reanimated.js",
    "reanimated.d.ts",
    "entities.js",
    "entities.d.ts",
    "gesture.js",
    "gesture.d.ts",
    "build.mjs",
    "build.cjs",
    "build.d.ts",
    "package.json",
];

const nativeAnimationTypeFiles = [
    "animations.d.ts",
    "gesture.d.ts",
    "hooks.d.ts",
    "interpolation.d.ts",
    "layout.d.ts",
    "local-adapter.d.ts",
    "runtime.d.ts",
    "types.d.ts",
    "worklet-globals.d.ts",
    "worklets.d.ts",
];

const internalTypeFiles = [
    ...nativeAnimationTypeFiles,
    "components.d.ts",
    "legacy-animated.d.ts",
    "native-animation.d.ts",
    "native-animation-core.d.ts",
    "renderer.d.ts",
    "script-api.d.ts",
    "script-registry.d.ts",
];

function ensureDir(dir) {
    fs.mkdirSync(dir, { recursive: true });
}

function readGenerated(relativePath) {
    return fs.readFileSync(path.resolve(root, "../nodejs", relativePath), "utf8");
}

function writeFile(outDir, relativePath, text) {
    const filePath = path.join(outDir, relativePath);
    ensureDir(path.dirname(filePath));
    fs.writeFileSync(filePath, text, "utf8");
}

function copyFile(sourceDir, targetDir, relativePath) {
    const sourcePath = path.join(sourceDir, relativePath);
    const targetPath = path.join(targetDir, relativePath);
    ensureDir(path.dirname(targetPath));
    fs.copyFileSync(sourcePath, targetPath);
}

function unlinkIfExists(filePath) {
    try {
        fs.unlinkSync(filePath);
    } catch (error) {
        if (error?.code !== "ENOENT") throw error;
    }
}

function rewriteCommonTypeImports(text) {
    return text
        .replace(/from "\.\.\/ui\/components"/g, 'from "spotifyplus/internal/components"')
        .replace(/from "\.\/components"/g, 'from "spotifyplus/internal/components"')
        .replace(/from "\.\.\/components"/g, 'from "spotifyplus/internal/components"')
        .replace(/from "\.\/animated"/g, 'from "spotifyplus/internal/legacy-animated"')
        .replace(/from "\.\.\/ui\/animated"/g, 'from "spotifyplus/internal/legacy-animated"')
        .replace(/from "\.\.\/ui\/native-animation"/g, 'from "spotifyplus/internal/native-animation"')
        .replace(/from "\.\/native-animation\/core"/g, 'from "spotifyplus/internal/native-animation-core"')
        .replace(/from "\.\/native-animation\/(?:layout|types)"/g, 'from "spotifyplus/internal/native-animation-core"')
        .replace(/from "\.\/core"/g, 'from "spotifyplus/internal/native-animation-core"')
        .replace(/from "\.\.\/renderer"/g, 'from "spotifyplus/internal/renderer"')
        .replace(/from "\.\/renderer"/g, 'from "spotifyplus/internal/renderer"')
        .replace(/from "\.\.\/core\/models"/g, 'from "spotifyplus/entities"')
        .replace(/from "\.\.\/core\/extension-assets"/g, 'from "spotifyplus/internal/script-api"')
        .replace(/from "\.\/script-registry"/g, 'from "spotifyplus/internal/script-registry"')
        .replace(/import\("\.\.\/ui\/components"\)/g, 'import("spotifyplus/internal/components")')
        .replace(/import\("\.\/components"\)/g, 'import("spotifyplus/internal/components")')
        .replace(/import\("\.\.\/components"\)/g, 'import("spotifyplus/internal/components")')
        .replace(/import\("\.\.\/ui\/animated"\)/g, 'import("spotifyplus/internal/legacy-animated")')
        .replace(/import\("\.\/animated"\)/g, 'import("spotifyplus/internal/legacy-animated")')
        .replace(/import\("\.\.\/ui\/native-animation"\)/g, 'import("spotifyplus/internal/native-animation")')
        .replace(/import\("\.\/native-animation\/core"\)/g, 'import("spotifyplus/internal/native-animation-core")')
        .replace(/import\("\.\/native-animation\/(?:layout|types)"\)/g, 'import("spotifyplus/internal/native-animation-core")')
        .replace(/import\("\.\/core"\)/g, 'import("spotifyplus/internal/native-animation-core")')
        .replace(/import\("\.\.\/loader\/script-api"\)/g, 'import("spotifyplus/internal/script-api")');
}

function rewriteInternalComponents(text) {
    return rewriteCommonTypeImports(text)
        .replace(
            /import \{ type AnimatedNodeLike as LegacyAnimatedNodeLike \} from "spotifyplus\/internal\/legacy-animated";/,
            'import { type AnimatedNodeLike as LegacyAnimatedNodeLike } from "spotifyplus/internal/legacy-animated";',
        )
        .replace(
            /import \* as NativeAnimatedCore from "spotifyplus\/internal\/native-animation-core";/,
            'import * as NativeAnimatedCore from "spotifyplus/internal/native-animation-core";',
        )
        .replace(
            /import type \{ NativeAnimatedNodeLike \} from "spotifyplus\/internal\/native-animation-core";/,
            'import type { NativeAnimatedNodeLike } from "spotifyplus/internal/native-animation-core";',
        );
}

function rewritePublicComponents(text) {
    return text
        .replace(/from "\.\.\/ui\/components"/g, 'from "spotifyplus/internal/components"')
        .replace(/import\("\.\.\/ui\/components"\)/g, 'import("spotifyplus/internal/components")')
        .replace(/from "\.\/animated"/g, 'from "spotifyplus/internal/native-animation-core"')
        .replace(/import\("\.\/animated"\)/g, 'import("spotifyplus/internal/native-animation-core")')
        .replace(/from "\.\.\/ui\/animated"/g, 'from "spotifyplus/internal/legacy-animated"')
        .replace(/import\("\.\.\/ui\/animated"\)/g, 'import("spotifyplus/internal/legacy-animated")');
}

function rewritePublicSelfReferences(text) {
    return text.replaceAll('"spotifyplus/internal/', '"./internal/');
}

function rewriteInternalSelfReferences(text) {
    return text
        .replaceAll('"spotifyplus/internal/', '"./')
        .replaceAll('"spotifyplus/entities"', '"../entities"');
}

function makePackageJson() {
    const version = sourcePackage.version;
    return JSON.stringify({
        name: sourcePackage.name,
        version,
        type: "commonjs",
        main: "./index.cjs",
        types: "./index.d.ts",
        exports: {
            ".": {
                types: "./index.d.ts",
                default: "./index.cjs",
            },
            "./react": {
                types: "./components.d.ts",
                default: "./components.js",
            },
            "./react/animated": {
                types: "./animated.d.ts",
                default: "./animated.js",
            },
            "./react/reanimated": {
                types: "./reanimated.d.ts",
                default: "./reanimated.js",
            },
            "./react/Animated": {
                types: "./reanimated.d.ts",
                default: "./reanimated.js",
            },
            "./react/Gesture": {
                types: "./gesture.d.ts",
                default: "./gesture.js",
            },
            "./build": {
                types: "./build.d.ts",
                import: "./build.mjs",
                require: "./build.cjs",
            },
            "./entities": {
                types: "./entities.d.ts",
                default: "./entities.js",
            },
            "./internal/components": {
                types: "./internal/components.d.ts",
                default: "./components.js",
            },
            "./internal/legacy-animated": {
                types: "./internal/legacy-animated.d.ts",
                default: "./components.js",
            },
            "./internal/native-animation": {
                types: "./internal/native-animation.d.ts",
                default: "./animated.js",
            },
            "./internal/native-animation-core": {
                types: "./internal/native-animation-core.d.ts",
                default: "./animated.js",
            },
            "./internal/renderer": {
                types: "./internal/renderer.d.ts",
                default: "./components.js",
            },
            "./internal/script-api": {
                types: "./internal/script-api.d.ts",
                default: "./index.cjs",
            },
            "./internal/script-registry": {
                types: "./internal/script-registry.d.ts",
                default: "./index.cjs",
            },
        },
        files: [
            ...publicFiles,
            "internal/*.d.ts",
        ],
        bin: {
            spotifyplus: "./dev.cjs",
        },
        dependencies: {
            "@babel/core": sourcePackage.dependencies?.["@babel/core"] ?? "^7.29.0",
            "@babel/generator": sourcePackage.dependencies?.["@babel/generator"] ?? "^7.29.0",
            "@babel/plugin-transform-typescript": sourcePackage.dependencies?.["@babel/plugin-transform-typescript"] ?? "^7.28.6",
            esbuild: sourcePackage.dependencies?.esbuild ?? sourcePackage.devDependencies?.esbuild ?? "^0.28.2",
        },
        typesVersions: {
            "*": {
                react: ["components.d.ts"],
                "react/animated": ["animated.d.ts"],
                "react/reanimated": ["reanimated.d.ts"],
                "react/Animated": ["reanimated.d.ts"],
                "react/Gesture": ["gesture.d.ts"],
                build: ["build.d.ts"],
                entities: ["entities.d.ts"],
                "internal/components": ["internal/components.d.ts"],
                "internal/legacy-animated": ["internal/legacy-animated.d.ts"],
                "internal/native-animation": ["internal/native-animation.d.ts"],
                "internal/native-animation-core": ["internal/native-animation-core.d.ts"],
                "internal/renderer": ["internal/renderer.d.ts"],
                "internal/script-api": ["internal/script-api.d.ts"],
                "internal/script-registry": ["internal/script-registry.d.ts"],
            },
        },
        peerDependencies: {
            react: sourcePackage.dependencies?.react ?? "^19.2.4",
        },
        devDependencies: {
            react: sourcePackage.dependencies?.react ?? "^19.2.4",
            "@types/react": sourcePackage.devDependencies?.["@types/react"] ?? "^19.2.4",
        },
    }, null, 4) + "\n";
}

function makeIndexDts(scriptApiDts) {
    const sourceFile = ts.createSourceFile("script-api.d.ts", scriptApiDts, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
    const typeNames = sourceFile.statements
        .filter(statement => ts.isInterfaceDeclaration(statement) || ts.isTypeAliasDeclaration(statement) || ts.isEnumDeclaration(statement))
        .map(statement => statement.name.text);

    return [
        'export declare const SpotifyPlus: import("spotifyplus/internal/script-api").SpotifyPlusApi;',
        `export type { ${typeNames.join(", ")} } from "spotifyplus/internal/script-api";`,
        "",
    ].join("\n");
}

function makeScriptRegistryDts() {
    return [
        'import React from "react";',
        'import type { Surface } from "spotifyplus/entities";',
        "",
        "export type EventHandler = (payload: unknown) => void | Promise<void>;",
        "export type SurfaceRenderer<T extends string = string> = (surface: Surface & { type: T }) => React.ReactElement;",
        "",
    ].join("\n");
}

function readExportedApiDeclarations(relativePath, variableNames = []) {
    const text = readGenerated(relativePath);
    const sourceFile = ts.createSourceFile(relativePath, text, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
    const includedVariables = new Set(variableNames);

    return sourceFile.statements
        .filter(statement => {
            if (ts.isInterfaceDeclaration(statement) || ts.isTypeAliasDeclaration(statement) || ts.isEnumDeclaration(statement)) {
                return statement.modifiers?.some(modifier => modifier.kind === ts.SyntaxKind.ExportKeyword);
            }

            if (!ts.isVariableStatement(statement)) return false;

            return statement.declarationList.declarations.some(declaration =>
                ts.isIdentifier(declaration.name) && includedVariables.has(declaration.name.text));
        })
        .map(statement => statement.getFullText(sourceFile).trim())
        .join("\n\n");
}

function makeScriptApiDts() {
    return [
        'import type { ContextMenu, OnClickCallback, PlatformData, Session, ShouldAddCallback, SideDrawerItem, SideOnClickCallback, SpotifyTrack } from "spotifyplus/entities";',
        'import type { EventHandler, SurfaceRenderer } from "spotifyplus/internal/script-registry";',
        "",
        readExportedApiDeclarations("loader/settings.d.ts"),
        "",
        readExportedApiDeclarations("core/extension-assets.d.ts"),
        "",
        readExportedApiDeclarations("loader/extension-event-emitter.d.ts"),
        "",
        readExportedApiDeclarations("loader/script-api.d.ts", ["SpotifyPlus"]),
        "",
    ].join("\n");
}

function writeTypes(outDir) {
    const scriptApiDts = rewriteInternalSelfReferences(makeScriptApiDts());

    writeFile(outDir, "index.d.ts", rewritePublicSelfReferences(makeIndexDts(scriptApiDts)));
    writeFile(
        outDir,
        "components.d.ts",
        rewritePublicSelfReferences(rewritePublicComponents(readGenerated("sdk/components.d.ts"))),
    );
    writeFile(
        outDir,
        "animated.d.ts",
        rewritePublicSelfReferences(rewriteCommonTypeImports(readGenerated("sdk/animated.d.ts"))
            .replace(/from "\.\.\/ui\/animated"/g, 'from "spotifyplus/internal/legacy-animated"')
            .replace(/from "\.\/components"/g, 'from "spotifyplus/react"')),
    );
    writeFile(
        outDir,
        "reanimated.d.ts",
        rewritePublicSelfReferences(rewriteCommonTypeImports(readGenerated("ui/native-animation/index.d.ts"))
            .replace(/from "\.\.\/components"/g, 'from "spotifyplus/react"')),
    );
    writeFile(outDir, "gesture.d.ts", 'export * from "./internal/gesture";\n');
    writeFile(outDir, "entities.d.ts", rewriteCommonTypeImports(readGenerated("core/models.d.ts")));
    writeFile(outDir, "build.d.ts", [
        'import type { Plugin } from "esbuild";',
        "",
        "export interface SpotifyPlusWorkletTransformOptions {",
        "    filename?: string;",
        "    globals?: readonly string[];",
        "    inlineSourceMap?: boolean;",
        "    rootDir?: string;",
        "    onWorklet?: (metadata: Readonly<Record<string, unknown>>) => void;",
        "}",
        "",
        "export interface SpotifyPlusWorkletsPluginOptions {",
        "    globals?: readonly string[];",
        "    rootDir?: string;",
        "    transformDependencies?: boolean;",
        "}",
        "",
        "export declare const SPOTIFYPLUS_WORKLET_VERSION: 2;",
        "export declare const SPOTIFYPLUS_WORKLET_BUNDLE_MARKER: string;",
        "export declare const SPOTIFYPLUS_ANIMATED_MODULE: string;",
        "export declare const SPOTIFYPLUS_GESTURE_MODULE: string;",
        "export declare function transformWorklets(source: string, options?: SpotifyPlusWorkletTransformOptions): Promise<{",
        "    code: string;",
        "    map: Record<string, unknown> | null;",
        "    workletCount: number;",
        "    worklets: readonly Readonly<Record<string, unknown>>[];",
        "}>;",
        "export declare function spotifyPlusWorkletsPlugin(options?: SpotifyPlusWorkletsPluginOptions): Plugin;",
        "",
    ].join("\n"));

    writeFile(
        outDir,
        "internal/components.d.ts",
        rewriteInternalSelfReferences(rewriteInternalComponents(readGenerated("ui/components.d.ts"))),
    );
    writeFile(
        outDir,
        "internal/legacy-animated.d.ts",
        rewriteInternalSelfReferences(rewriteCommonTypeImports(readGenerated("ui/animated.d.ts"))),
    );
    writeFile(
        outDir,
        "internal/native-animation.d.ts",
        rewriteInternalSelfReferences(rewriteCommonTypeImports(readGenerated("ui/native-animation/index.d.ts"))
            .replace(/from "\.\.\/components"/g, 'from "spotifyplus/react"')),
    );
    writeFile(
        outDir,
        "internal/native-animation-core.d.ts",
        rewriteInternalSelfReferences(rewriteCommonTypeImports(readGenerated("ui/native-animation/core.d.ts"))),
    );
    for (const fileName of nativeAnimationTypeFiles) {
        writeFile(
            outDir,
            path.join("internal", fileName),
            rewriteInternalSelfReferences(rewriteCommonTypeImports(readGenerated(path.join("ui/native-animation", fileName)))),
        );
    }
    writeFile(
        outDir,
        "internal/renderer.d.ts",
        rewriteInternalSelfReferences(rewriteCommonTypeImports(readGenerated("ui/renderer.d.ts"))),
    );
    writeFile(outDir, "internal/script-registry.d.ts", rewriteInternalSelfReferences(makeScriptRegistryDts()));
    writeFile(
        outDir,
        "internal/script-api.d.ts",
        scriptApiDts,
    );
}

async function bundleJs(outDir) {
    ensureDir(outDir);
    const common = {
        bundle: true,
        platform: "node",
        format: "cjs",
        target: "es2020",
        external: ["react"],
        logLevel: "info",
    };

    await Promise.all([
        build({
            ...common,
            entryPoints: [path.join(root, "sdk/index.ts")],
            outfile: path.join(outDir, "index.cjs"),
        }),
        build({
            ...common,
            entryPoints: [path.join(root, "sdk/components.ts")],
            outfile: path.join(outDir, "components.js"),
        }),
        build({
            ...common,
            entryPoints: [path.join(root, "sdk/animated.ts")],
            outfile: path.join(outDir, "animated.js"),
        }),
        build({
            ...common,
            entryPoints: [path.join(root, "sdk/reanimated.ts")],
            outfile: path.join(outDir, "reanimated.js"),
        }),
        build({
            ...common,
            entryPoints: [path.join(root, "sdk/gesture.ts")],
            outfile: path.join(outDir, "gesture.js"),
        }),
        build({
            ...common,
            entryPoints: [path.join(root, "sdk/entities.ts")],
            outfile: path.join(outDir, "entities.js"),
        }),
        build({
            ...common,
            entryPoints: [path.join(root, "tools/dev-cli.mjs")],
            outfile: path.join(outDir, "dev.cjs"),
            external: [
                "@babel/core",
                "@babel/generator",
                "@babel/plugin-transform-typescript",
                "esbuild",
            ],
        }),
        build({
            ...common,
            entryPoints: [path.join(root, "tools/worklet-transform.mjs")],
            outfile: path.join(outDir, "build.cjs"),
            external: [
                "@babel/core",
                "@babel/generator",
                "@babel/plugin-transform-typescript",
                "esbuild",
            ],
        }),
        build({
            ...common,
            entryPoints: [path.join(root, "tools/worklet-transform.mjs")],
            outfile: path.join(outDir, "build.mjs"),
            format: "esm",
            external: [
                "@babel/core",
                "@babel/generator",
                "@babel/plugin-transform-typescript",
                "esbuild",
            ],
        }),
    ]);
}

async function buildPackage() {
    fs.rmSync(primaryOutDir, { recursive: true, force: true });

    await bundleJs(primaryOutDir);
    writeTypes(primaryOutDir);
    writeFile(primaryOutDir, "package.json", makePackageJson());

    ensureDir(mirrorOutDir);
    unlinkIfExists(path.join(mirrorOutDir, "index.js"));
    unlinkIfExists(path.join(mirrorOutDir, "runtime.js"));
    unlinkIfExists(path.join(mirrorOutDir, "runtime.d.ts"));

    for (const file of publicFiles) copyFile(primaryOutDir, mirrorOutDir, file);
    for (const file of internalTypeFiles) copyFile(primaryOutDir, mirrorOutDir, path.join("internal", file));

    console.log(`Built SDK package in ${path.relative(root, primaryOutDir)}`);
    console.log(`Mirrored SDK package to ${path.relative(root, mirrorOutDir)}`);
}

await buildPackage();
