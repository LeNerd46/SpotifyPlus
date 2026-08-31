//@ts-ignore
import fs from 'fs';
//@ts-ignore
import path from 'path';
//@ts-ignore
import vm from 'vm';
//@ts-ignore
import { createRequire } from 'module';
//@ts-ignore
import { URL, URLSearchParams } from 'url';
//@ts-ignore
import { TextEncoder, TextDecoder } from 'util';
//@ts-ignore
import { builtinModules } from 'module';
import { Logger } from '../core/logger';
import { ScriptApiFactory } from './script-api';
import {
    CURRENT_SCRIPT_API,
    LEGACY_SCRIPT_API,
    ScriptManifest,
    parseManifest,
} from './script-manifest';
import { HostRuntime } from './host-runtime';
import { resolveFetchGlobals } from './fetch-globals';
import { createRoot, setCommitListener } from '../ui/renderer';
import React from 'react';

export type ScriptTrust = 'elevated' | 'user';

export class ScriptLoader {
    private readonly apiFactory: ScriptApiFactory;
    private readonly scriptRoots: string[] = [];
    private readonly scriptDirectories = new Map<string, string>();
    private readonly scriptTrust = new Map<string, ScriptTrust>();
    private readonly scriptGenerations = new Map<string, number>();

    constructor(private readonly runtime: HostRuntime, private readonly logger: Logger) {
        this.apiFactory = new ScriptApiFactory(runtime, logger.child('Api'));
    }

    loadFromRoots(roots: string[], trust: ScriptTrust = 'user'): void {
        this.scriptRoots.length = 0;
        this.scriptRoots.push(...roots.map(root => path.resolve(root)));
        for (const root of roots) this.loadFromRoot(root, trust);
    }

    loadFromRoot(root: string, trust: ScriptTrust = 'user'): void {
        if (!fs.existsSync(root)) {
            this.logger.warn(`Scripts root does not exist: ${root}`);
            return;
        }

        const entries = fs.readdirSync(root, { withFileTypes: true });
        for (const entry of entries) {
            if (!entry.isDirectory()) continue;
            const scriptDirectory = path.join(root, entry.name);
            try {
                this.loadScript(scriptDirectory, trust);
            } catch (error) {
                this.logger.error(`Failed to load script at ${scriptDirectory}`, error);
            }
        }
    }

    unloadFromRoot(root: string): void {
        const resolvedRoot = path.resolve(root);
        for (const script of this.runtime.registry.getScripts()) {
            const directory = path.resolve(script.directoryPath);
            if (directory !== resolvedRoot && !directory.startsWith(resolvedRoot + path.sep)) continue;
            this.runtime.unregisterScript(script.manifest.id);
            this.scriptDirectories.delete(script.manifest.id);
            this.scriptTrust.delete(script.manifest.id);
        }
    }

    loadScript(scriptDirectory: string, trust: ScriptTrust = 'user'): void {
        const manifest = this.readManifest(scriptDirectory);
        const entryPath = path.resolve(scriptDirectory, manifest.main);
        assertPathInsideScript(scriptDirectory, entryPath);

        if (!fs.existsSync(entryPath)) throw new Error(`Script entry not found: ${entryPath}`);

        const source = fs.readFileSync(entryPath, 'utf8');
        this.executeScript(scriptDirectory, manifest, entryPath, source, true, trust);
    }

    loadScriptFromSource(scriptDirectory: string, manifest: ScriptManifest, source: string, loadNative = false, trust: ScriptTrust = this.getScriptTrust(manifest.id)): void {
        const validatedManifest = this.preflightSource(scriptDirectory, manifest, source);
        const entryPath = path.resolve(scriptDirectory, validatedManifest.main);
        this.executeScript(scriptDirectory, validatedManifest, entryPath, source, loadNative, trust);
    }

    preflightSource(scriptDirectory: string, manifest: ScriptManifest, source: string): ScriptManifest {
        const validatedManifest = parseManifest(manifest);
        const entryPath = path.resolve(scriptDirectory, validatedManifest.main);
        assertPathInsideScript(scriptDirectory, entryPath);
        if (validatedManifest.api === CURRENT_SCRIPT_API) {
            assertTransformedApi2Bundle(source, entryPath);
        }
        new vm.Script(source, { filename: entryPath });
        return validatedManifest;
    }

    getScriptTrust(scriptId: string): ScriptTrust {
        return this.scriptTrust.get(scriptId) ?? 'user';
    }

    getScriptDirectory(scriptId: string): string {
        const existing = this.runtime.registry.getScript(scriptId);
        if (existing) return existing.directoryPath;

        const previousDirectory = this.scriptDirectories.get(scriptId);
        if (previousDirectory) return previousDirectory;

        const root = this.scriptRoots[0] ?? path.join(__dirname, 'scripts');
        return path.join(root, scriptId);
    }

    private executeScript(scriptDirectory: string, manifest: ScriptManifest, entryPath: string, source: string, loadNative: boolean, trust: ScriptTrust): void {
        const previousDirectory = this.scriptDirectories.get(manifest.id);
        const previousTrust = this.scriptTrust.get(manifest.id);
        const entryDirectory = path.dirname(entryPath);
        const generation = (this.scriptGenerations.get(manifest.id) ?? 0) + 1;
        this.scriptGenerations.set(manifest.id, generation);
        const api = this.apiFactory.create(
            manifest.id,
            generation,
            entryDirectory,
            scriptDirectory,
            manifest.assets,
            trust,
        );
        const globals: Record<string, any> = {};
        globals.__spotifyplus_api__ = api;
        if (trust === 'elevated' && api.Elevated) globals.__spotifyplus_elevated__ = api.Elevated;

        const nodeRequire = createRequire(entryPath);
        const hostRequire = createRequire(__filename);
        //@ts-ignore
        const componentsPath = path.resolve(__dirname, '../ui/components.js');
        const componentsModule = nodeRequire(componentsPath);
        const hostComponents = selectAnimatedHostComponents(componentsModule);
        let disposeAnimatedModule: (() => void) | undefined;
        let reanimatedModule: Record<string, any>;
        let reactModule = componentsModule;

        const legacyAnimatedPath = path.resolve(__dirname, '../ui/animated.js');
        const legacyAnimatedCore = nodeRequire(legacyAnimatedPath);
        const legacyAnimatedModule = createCommonJsFacade(
            legacyAnimatedCore.createAnimatedModule(hostComponents),
        );

        if (manifest.api === CURRENT_SCRIPT_API) {
            assertTransformedApi2Bundle(source, entryPath);
            if (!this.runtime.createWorkletContext(manifest.id, generation)) {
                throw new Error(`Unable to create the UI worklet context for ${manifest.id}@${generation}.`);
            }

            try {
                const animatedPath = path.resolve(__dirname, '../ui/native-animation/index.js');
                const animatedCore = nodeRequire(animatedPath);
                const adapter = this.runtime.createAnimationAdapter(manifest.id, generation);
                const boundAnimated = animatedCore.createAnimatedModule(
                    adapter,
                    Object.freeze({ scriptId: manifest.id, generation }),
                    hostComponents,
                );
                disposeAnimatedModule = boundAnimated.dispose;
                reanimatedModule = createCommonJsFacade(createPublicAnimatedApi(boundAnimated));
            } catch (error) {
                this.runtime.disposeWorkletContext(manifest.id, generation);
                throw error;
            }
        } else {
            const api1Path = path.resolve(__dirname, '../ui/native-animation/api1.js');
            const api1Module = nodeRequire(api1Path);
            const boundAnimated = api1Module.createAnimatedAPI1(hostComponents);
            reanimatedModule = createCommonJsFacade(boundAnimated);
            reactModule = createApi1ReactFacade(componentsModule, boundAnimated);
        }

        const gestureModule = createGestureFacade(reanimatedModule);

        const fetchResolution = resolveFetchGlobals(() => nodeRequire('node-fetch'));
        if (!fetchResolution.globals.fetch) {
            this.logger.warn(
                `Fetch API is not available for script ${manifest.id}`,
                fetchResolution.fallbackError,
            );
        }

        const localRequire = (specifier: string) => {
            if (specifier === 'react') {
                return React;
            }

            if (specifier === 'react/jsx-runtime' || specifier === 'react/jsx-dev-runtime') {
                return hostRequire(specifier);
            }

            if (specifier === 'spotifyplus') {
                return {
                    SpotifyPlus: api.SpotifyPlus,
                    default: api.SpotifyPlus
                };
            }
            if (specifier === 'spotifyplus/react') {
                return reactModule;
            }

            if (specifier === 'spotifyplus/animated') {
                if (manifest.api !== LEGACY_SCRIPT_API) {
                    throw new Error("API-2 extensions must import classic animations from 'spotifyplus/react/animated' or Reanimated-style animations from 'spotifyplus/react/reanimated'.");
                }
                return reanimatedModule;
            }

            if (specifier === 'spotifyplus/react/animated') {
                return legacyAnimatedModule;
            }

            if (specifier === 'spotifyplus/react/reanimated') {
                return reanimatedModule;
            }

            // Compatibility alias for extensions built before the animation modules were split.
            if (specifier === 'spotifyplus/react/Animated') {
                return reanimatedModule;
            }

            if (specifier === 'spotifyplus/react/Animated/core') {
                if (manifest.api !== LEGACY_SCRIPT_API) {
                    throw new Error("'spotifyplus/react/Animated/core' is not public in API 2. Import from 'spotifyplus/react/reanimated'.");
                }
                return reanimatedModule;
            }

            if (specifier === 'spotifyplus/react/Gesture') {
                if (manifest.api !== CURRENT_SCRIPT_API) {
                    throw new Error("'spotifyplus/react/Gesture' requires manifest.api 2.");
                }
                return gestureModule;
            }

            if (trust !== 'elevated') {
                if (isBlockedUserModule(specifier)) {
                    throw new Error(`User extensions cannot require privileged Node module '${specifier}'`);
                }

                if (specifier.startsWith('.') || specifier.startsWith('/')) {
                    const resolved = nodeRequire.resolve(specifier);
                    const scriptRoot = path.resolve(scriptDirectory);
                    const resolvedPath = path.resolve(resolved);
                    if (resolvedPath !== scriptRoot && !resolvedPath.startsWith(scriptRoot + path.sep)) {
                        throw new Error(`User extension require escaped its extension directory: ${specifier}`);
                    }
                }
            }

            return nodeRequire(specifier);
        };

        const module = { exports: {} as any };

        globals.require = localRequire;
        globals.module = module;
        globals.exports = module.exports;
        globals.__filename = entryPath;
        globals.__dirname = path.dirname(entryPath);

        if (trust === 'elevated') {
            //@ts-ignore
            globals.process = process;
        }
        //@ts-ignore
        globals.Buffer = Buffer;
        globals.console = api.console;

        const timeoutHandles = new Set<any>();
        const intervalHandles = new Set<any>();

        globals.setTimeout = (callback: (...args: any[]) => void, delay?: number, ...args: any[]) => {
            const handle = setTimeout(() => {
                timeoutHandles.delete(handle);
                callback(...args);
            }, delay, ...args);
            timeoutHandles.add(handle);
            return handle;
        };
        globals.clearTimeout = (handle: any) => {
            timeoutHandles.delete(handle);
            clearTimeout(handle);
        };
        globals.setInterval = (callback: (...args: any[]) => void, delay?: number, ...args: any[]) => {
            const handle = setInterval(callback, delay, ...args);
            intervalHandles.add(handle);
            return handle;
        };
        globals.clearInterval = (handle: any) => {
            intervalHandles.delete(handle);
            clearInterval(handle);
        };
        this.runtime.registry.addCleanup(manifest.id, () => {
            for (const handle of timeoutHandles) clearTimeout(handle);
            for (const handle of intervalHandles) clearInterval(handle);
            timeoutHandles.clear();
            intervalHandles.clear();
        });
        //@ts-ignore
        globals.setImmediate = typeof setImmediate === 'function' ? setImmediate : (fn: (...args: any[]) => void, ...args: any[]) => setTimeout(fn, 0, ...args);
        //@ts-ignore
        globals.clearImmediate = typeof clearImmediate === 'function' ? clearImmediate : clearTimeout;

        globals.queueMicrotask = typeof queueMicrotask === 'function' ? queueMicrotask : (callback: () => void) => Promise.resolve().then(callback);

        globals.URL = URL;
        globals.URLSearchParams = URLSearchParams;
        globals.TextEncoder = TextEncoder;
        globals.TextDecoder = TextDecoder;

        Object.assign(globals, fetchResolution.globals);

        globals.Promise = Promise;
        globals.Symbol = Symbol;
        globals.Map = Map;
        globals.Set = Set;
        globals.WeakMap = WeakMap;
        globals.WeakSet = WeakSet;
        globals.Array = Array;
        globals.Object = Object;
        globals.String = String;
        globals.Number = Number;
        globals.Boolean = Boolean;
        globals.Date = Date;
        globals.RegExp = RegExp;
        globals.Error = Error;
        globals.TypeError = TypeError;
        globals.JSON = JSON;
        globals.Math = Math;
        globals.Reflect = Reflect;
        globals.Proxy = Proxy;

        globals.global = globals;
        globals.globalThis = globals;
        globals.self = globals;

        let context: any;
        let script: any;
        let registered = false;
        try {
            context = vm.createContext(globals, {
                name: `SpotifyPlusScript:${manifest.id}`,
                codeGeneration: {
                    strings: true,
                    wasm: false
                }
            });

            script = new vm.Script(source, {
                filename: entryPath,
            });

            this.runtime.registry.registerScript({ manifest, directoryPath: scriptDirectory, trust, generation });
            registered = true;
            this.scriptDirectories.set(manifest.id, scriptDirectory);
            this.scriptTrust.set(manifest.id, trust);
            if (disposeAnimatedModule) {
                this.runtime.registry.addCleanup(manifest.id, disposeAnimatedModule);
            }
        } catch (error) {
            if (registered) {
                this.runtime.unregisterScript(manifest.id);
            } else {
                disposeAnimatedModule?.();
            }
            this.restoreScriptMetadata(manifest.id, previousDirectory, previousTrust);
            throw error;
        }

        try {
            if (manifest.native && loadNative) {
                const apkPath = path.resolve(entryDirectory, manifest.native.apk);
                assertPathInsideScript(scriptDirectory, apkPath);
                if (!fs.existsSync(apkPath)) throw new Error(`Native APK file not found: ${apkPath}`);

                this.runtime.loadApk(manifest.id, apkPath, manifest.native.pluginClass);
            }

            script.runInContext(context);

            const exported = module.exports?.default ?? module.exports;
            const config = module.exports?.config ?? {};

            if (typeof exported === 'function') {
                const surfaceId = config.surface ?? manifest.id;
                const root = createRoot(surfaceId);
                setCommitListener(surfaceId, (ops) => {
                    this.runtime.sendCommand('react.commit', { surfaceId, ops });
                });

                root.render(React.createElement(exported));
                this.runtime.registry.trackMountedRoot(manifest.id, surfaceId, root);
            }

            this.logger.info(`Loaded script ${manifest.id} from ${entryPath}`);
        } catch (error) {
            this.runtime.unregisterScript(manifest.id);
            this.restoreScriptMetadata(manifest.id, previousDirectory, previousTrust);
            throw error;
        }
    }

    private restoreScriptMetadata(scriptId: string, directory: string | undefined, trust: ScriptTrust | undefined): void {
        if (directory) this.scriptDirectories.set(scriptId, directory);
        else this.scriptDirectories.delete(scriptId);

        if (trust) this.scriptTrust.set(scriptId, trust);
        else this.scriptTrust.delete(scriptId);
    }

    private readManifest(scriptDirectory: string): ScriptManifest {
        const manifestPath = path.join(scriptDirectory, 'manifest.json');
        if (!fs.existsSync(manifestPath)) throw new Error(`Missing manifest.json in ${scriptDirectory}`);

        const rawText = fs.readFileSync(manifestPath, 'utf8');
        return parseManifest(JSON.parse(rawText));
    }
}

function assertTransformedApi2Bundle(source: string, entryPath: string) {
    if (/globalThis\.__spotifyplus_worklet_bundle__\s*=\s*2\s*;/.test(source)) {
        return;
    }
    throw new Error(
        `API-2 bundle ${entryPath} was not built with the SpotifyPlus worklet transform. Run 'spotifyplus build' and reinstall the extension.`,
    );
}

function selectAnimatedHostComponents(componentsModule: Record<string, any>) {
    return {
        View: componentsModule.View,
        Text: componentsModule.Text,
        Image: componentsModule.Image,
        ScriptView: componentsModule.ScriptView,
        RenderView: componentsModule.RenderView,
        CanvasView: componentsModule.CanvasView,
        ScrollView: componentsModule.ScrollView,
        FlatList: componentsModule.FlatList,
    };
}

function createCommonJsFacade(animated: Record<string, any>) {
    return Object.freeze({
        ...animated,
        Animated: animated,
        default: animated,
        __esModule: true,
    });
}

function createPublicAnimatedApi(animated: Record<string, any>) {
    const {
        dispose: _dispose,
        runtime: _runtime,
        ...publicApi
    } = animated;
    return Object.freeze(publicApi);
}

function createApi1ReactFacade(componentsModule: Record<string, any>, animated: Record<string, any>) {
    return Object.freeze({
        ...componentsModule,
        Animated: animated,
        default: Object.freeze({
            ...(componentsModule.default ?? componentsModule),
            Animated: animated,
        }),
    });
}

function createGestureFacade(animatedModule: Record<string, any>) {
    const gesture = animatedModule.Gesture;
    return Object.freeze({
        Gesture: gesture,
        GestureDetector: animatedModule.GestureDetector,
        Directions: animatedModule.Directions,
        MouseButton: animatedModule.MouseButton,
        GestureState: animatedModule.GestureState,
        default: gesture,
        __esModule: true,
    });
}

const blockedUserModules = new Set([
    'child_process',
    'cluster',
    'dgram',
    'fs',
    'fs/promises',
    'inspector',
    'module',
    'net',
    'process',
    'repl',
    'tls',
    'vm',
    'worker_threads'
]);

function isBlockedUserModule(specifier: string): boolean {
    const normalized = specifier.startsWith('node:') ? specifier.slice(5) : specifier;
    return blockedUserModules.has(normalized) || (builtinModules.includes(normalized) && blockedUserModules.has(normalized.split('/')[0]));
}

function assertPathInsideScript(scriptDirectory: string, candidate: string): void {
    const root = path.resolve(scriptDirectory);
    const relative = path.relative(root, candidate);
    if (relative === '' || (!relative.startsWith(`..${path.sep}`) && relative !== '..' && !path.isAbsolute(relative))) {
        return;
    }
    throw new Error(`Manifest path escaped the extension directory: ${candidate}`);
}
