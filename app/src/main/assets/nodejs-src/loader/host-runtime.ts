import { randomUUID } from 'crypto';
import fs from 'fs';
import http from 'http';
import path from 'path';
import { formatDevLogArgs, Logger } from '../core/logger';
import { Bridge } from '../bridge/bridge';
import { GetProgressData, PlatformData, Session, SpotifyTrack, SpotifyTrackData, Surface } from '../core/models';
import { ErrorPacket, Packet, ResponsePacket } from '../core/protocol';
import { RegisteredSurfaceRenderer, ScriptRegistry } from './script-registry';
import { clearCommitListener, dispatchReactEvent, setCommitDispatcher, setCommitListener } from '../ui/renderer';
import React from 'react';
import { parseManifest, ScriptManifest } from './script-manifest';
import type { ExtensionInstallRequest, LocalExtensionInfo, NavigationTarget } from './script-api';
import type { ScriptTrust } from './script-loader';
import { createSpotifyPlusWorkletAdapter } from '../bridge/worklet-adapter';
import type { NativeAnimationAdapter } from '../ui/native-animation/types';
import { matchesAssetPattern, normalizeAssetPattern } from '../core/extension-assets';
import { ExtensionSetting, ExtensionSettings, ExtensionSettingSection } from './settings';

interface PendingRequest {
    resolve: (payload: unknown) => void;
    reject: (error: Error) => void;
    name: string;
}

interface ReloadableScriptLoader {
    getScriptDirectory(scriptId: string): string;
    getScriptTrust(scriptId: string): ScriptTrust;
    loadFromRoot(root: string, trust?: ScriptTrust): void;
    loadScript(scriptDirectory: string, trust?: ScriptTrust): void;
    unloadFromRoot(root: string): void;
    preflightSource(scriptDirectory: string, manifest: ScriptManifest, source: string): ScriptManifest;
    loadScriptFromSource(scriptDirectory: string, manifest: ScriptManifest, source: string, loadNative?: boolean, trust?: ScriptTrust): void;
}

export interface HostConfig {
    elevatedRoot?: string;
    localRoot?: string;
    installedRoot?: string;
    marketplaceRoot?: string;
    developerMode?: boolean;
    allowElevatedHotReload?: boolean;
}

interface HotReloadPayload {
    scriptId?: string;
    buildId?: string;
    bundle?: HotReloadBundle;
}

interface HotReloadBundle {
    buildId: string;
    manifest: ScriptManifest;
    source: string;
    assets?: Array<{
        path: string;
        data: string;
        size?: number;
    }>;
}

type DevLogLevel = 'log' | 'warn' | 'error';

interface DevLogEntry {
    id: number;
    level: DevLogLevel;
    message: string;
    scriptId?: string;
    stack?: string;
    timestamp: string;
}

interface ActiveSideDrawer {
    scriptId: string;
    id: string;
}

export class HostRuntime {
    readonly registry: ScriptRegistry;
    private readonly bridge: Bridge;
    private readonly pendingRequests = new Map<string, PendingRequest>();
    private readonly activeSurfaces = new Map<string, Surface>();
    private activeSideDrawer: ActiveSideDrawer | null = null;
    private scriptLoader: ReloadableScriptLoader | null = null;
    private hotReloadServer: http.Server | null = null;
    private readonly devLogClients = new Set<http.ServerResponse>();
    private nextDevLogId = 1;
    private nodeConsoleForwardingInstalled = false;

    private spotifyConnecting = false;
    private spotifyConnectingWaiters = new Set<(value: boolean) => void>();

    private spotifyReady = false;
    private spotifyReadyWaiters = new Set<(ready: boolean) => void>();

    public platformData: PlatformData;
    public session: Session;

    private message: string = '';
    private extensionSettings: ExtensionSettings[] = [];

    constructor(private readonly logger: Logger, private readonly config: HostConfig = {}) {
        this.registry = new ScriptRegistry(logger.child('Registry'), (scriptId, message, error) => {
            this.reportScriptError(scriptId, message, error);
        });
        this.bridge = new Bridge(logger.child('Bridge'));
        this.platformData = {
            clientVersion: 'unknown',
            osName: 'android',
            osVersion: 'unknown',
            sdkVersion: 0
        };
        this.session = {
            accessToken: ''
        };
    }

    start(): void {
        Object.assign(this.platformData, this.bridge.getPlatformData());
        this.session.accessToken = this.bridge.getAccessToken();

        this.registerEventListeners();

        setCommitDispatcher((surfaceId, ops) => {
            this.bridge.commitSurface(surfaceId, ops);
        });

        if (this.config.developerMode) {
            this.installNodeConsoleForwarding();
            this.startHotReloadServer();
        }
        this.bridge.log('Starting script runtime!');
    }

    setScriptLoader(loader: ReloadableScriptLoader): void {
        this.scriptLoader = loader;
    }

    sendEvent(name: string, payload: unknown = {}): void {
        this.bridge.send({ type: 'event', name, payload });
    }

    sendCommand(name: string, payload: unknown = {}): void {
        this.bridge.send({ type: 'command', name, payload });
    }

    async request<TPayload = unknown>(name: string, payload: unknown = {}): Promise<TPayload> {
        const id = randomUUID();

        return await new Promise<TPayload>((resolve, reject) => {
            this.pendingRequests.set(id, {
                resolve: value => resolve(value as TPayload),
                reject,
                name
            });

            this.bridge.send({ id, type: 'request', name, payload });
        });
    }

    getCurrentTrack(): SpotifyTrack {
        return this.bridge.getCurrentTrack();
    }

    async getTrack(uri: string): Promise<SpotifyTrack | null> {
        return this.bridge.getTrack(uri);
    }

    getProgress(): number {
        return this.bridge.getPlaybackPosition();
    }

    log(message: string): void {
        this.bridge.log(message);
    }

    logScript(scriptId: string, level: DevLogLevel, args: unknown[]): void {
        const message = formatDevLogArgs(args);
        this.bridge.log(`[${scriptId}] ${level}: ${message}`);
        this.publishDevLog({
            level,
            message,
            scriptId,
            stack: level === 'error' && this.devLogClients.size > 0 ? new Error().stack : undefined,
        });
    }

    reportScriptError(scriptId: string, message: string, error: unknown): void {
        const formatted = formatDevLogArgs([message, error], {
            maxOutputLength: 8000,
            maxStringLength: 4000,
        });
        this.bridge.log(`[${scriptId}] error: ${formatted}`);
        this.publishDevLog({
            level: 'error',
            message: formatted,
            scriptId,
            stack: error instanceof Error ? error.stack : undefined,
        });
    }

    seek(position: number): void {
        this.bridge.seek(position);
    }

    play(): void {
        this.bridge.play();
    }

    pause(): void {
        this.bridge.pause();
    }

    togglePlay(): void {
        this.bridge.togglePlay();
    }

    skipNext(): void {
        this.bridge.skipNext();
    }

    skipPrevious(): void {
        this.bridge.skipPrevious();
    }

    toast(text: string, length: 'short' | 'long' = 'short'): void {
        this.bridge.toast(text, length);
    }

    navigate(uri: string, target: NavigationTarget): boolean {
        return this.bridge.navigate(uri, target);
    }

    navigateBack(): boolean {
        return this.bridge.navigateBack();
    }

    closeSurface(scriptId: string): boolean {
        if (this.activeSideDrawer?.scriptId !== scriptId) return false;

        this.registry.unmountSurface(scriptId, 'sideDrawer');
        clearCommitListener('sideDrawer');
        this.bridge.unregisterSurface('sideDrawer');
        this.activeSideDrawer = null;
        return true;
    }

    storageSet(scriptId: string, key: string, value: unknown): void {
        this.bridge.storageSet(scriptId, key, value);
    }

    async storageGet<T = any>(scriptId: string, key: string): Promise<T | null> {
        return this.bridge.storageGet<T>(scriptId, key);
    }

    storageRemove(scriptId: string, key: string): void {
        this.bridge.storageRemove(scriptId, key);
    }

    storageWriteText(scriptId: string, path: string, value: string): void {
        this.bridge.storageWriteText(scriptId, path, value);
    }

    storageWriteJson(scriptId: string, path: string, value: unknown): void {
        this.bridge.storageWriteJson(scriptId, path, value);
    }

    storageWriteBinary(scriptId: string, path: string, data: string): void {
        this.bridge.storageWriteBinary(scriptId, path, data);
    }

    async storageRead<T = any>(scriptId: string, path: string): Promise<{ type?: 'text' | 'json' | 'binary'; value?: T | string | null; data?: string | null } | null> {
        return this.bridge.storageRead<T>(scriptId, path);
    }

    storageDelete(scriptId: string, path: string): void {
        this.bridge.storageDelete(scriptId, path);
    }

    cacheWriteText(scriptId: string, path: string, value: string): void {
        this.bridge.cacheWriteText(scriptId, path, value);
    }

    cacheWriteJson(scriptId: string, path: string, value: unknown): void {
        this.bridge.cacheWriteJson(scriptId, path, value);
    }

    cacheWriteBinary(scriptId: string, path: string, data: string): void {
        this.bridge.cacheWriteBinary(scriptId, path, data);
    }

    async cacheRead<T = any>(scriptId: string, path: string): Promise<{ type?: 'text' | 'json' | 'binary'; value?: T | string | null; data?: string | null } | null> {
        return this.bridge.cacheRead<T>(scriptId, path);
    }

    cacheDelete(scriptId: string, path: string): void {
        this.bridge.cacheDelete(scriptId, path);
    }

    getDeveloperMode(): boolean {
        return this.bridge.getDeveloperMode();
    }

    setDeveloperMode(enabled: boolean): void {
        this.bridge.setDeveloperMode(enabled);
        this.config.developerMode = enabled;
        if (enabled) {
            this.installNodeConsoleForwarding();
            this.startHotReloadServer();
        }
        else {
            this.stopHotReloadServer();
            if (this.config.localRoot && this.scriptLoader) this.scriptLoader.unloadFromRoot(this.config.localRoot);
        }
    }

    pickLocalExtensionsFolder(): boolean {
        return this.bridge.pickLocalExtensionsFolder();
    }

    getLocalExtensionsFolderDisplayName(): string | null {
        return this.bridge.getLocalExtensionsFolderDisplayName();
    }

    listLocalExtensions(): LocalExtensionInfo[] {
        return this.bridge.listLocalExtensions();
    }

    refreshLocalExtensions(): LocalExtensionInfo[] {
        const extensions = this.bridge.refreshLocalExtensions();
        if (this.config.developerMode && this.config.localRoot && this.scriptLoader) {
            this.scriptLoader.unloadFromRoot(this.config.localRoot);
            this.scriptLoader.loadFromRoot(this.config.localRoot, 'user');
        }
        return extensions;
    }

    listInstalledExtensions(): LocalExtensionInfo[] {
        const root = this.config.installedRoot;
        if (!root || !fs.existsSync(root)) return [];

        const extensions: LocalExtensionInfo[] = [];
        for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
            if (!entry.isDirectory() || entry.name.startsWith('.')) continue;

            const directory = path.join(root, entry.name);
            try {
                extensions.push(readInstalledExtensionInfo(directory));
            } catch (error) {
                this.logger.warn(`Ignoring invalid installed extension at ${directory}`, error);
            }
        }
        return extensions;
    }

    installExtension(request: ExtensionInstallRequest): LocalExtensionInfo {
        const installedRoot = this.config.installedRoot;
        if (!installedRoot) throw new Error('Marketplace installation is not configured');
        if (!this.scriptLoader) throw new Error('Marketplace installation is unavailable while the script loader is starting');

        const manifest = parseManifest(request?.manifest);
        assertInstallableScriptId(manifest.id);
        if (!Array.isArray(request?.files)) throw new Error('Extension package files must be an array');

        const root = path.resolve(installedRoot);
        fs.mkdirSync(root, { recursive: true });

        const target = path.resolve(root, manifest.id);
        assertPathInside(root, target);
        const staging = path.resolve(root, `.install-${manifest.id}-${randomUUID()}`);
        const backup = path.resolve(root, `.backup-${manifest.id}-${randomUUID()}`);
        assertPathInside(root, staging);
        assertPathInside(root, backup);

        fs.mkdirSync(staging, { recursive: false });
        let backupCreated = false;
        let targetInstalled = false;
        let previousWasActive = false;
        let shouldActivate = false;
        let previousActiveDirectory: string | undefined;
        let previousActiveTrust: ScriptTrust | undefined;

        try {
            writeInstallPackage(staging, manifest, request.manifest, request.files);
            const entryPath = path.resolve(staging, ...manifest.main.split('/'));
            assertPathInside(staging, entryPath);
            const source = fs.readFileSync(entryPath, 'utf8');
            this.scriptLoader.preflightSource(staging, manifest, source);

            const activeScript = this.registry.getScript(manifest.id);
            const developerOverride = activeScript !== undefined
                && this.config.developerMode === true
                && this.config.localRoot !== undefined
                && isPathInside(this.config.localRoot, activeScript.directoryPath);
            shouldActivate = activeScript === undefined
                || (activeScript.trust === 'user' && !developerOverride);
            previousWasActive = activeScript !== undefined && path.resolve(activeScript.directoryPath) === target;
            if (activeScript && shouldActivate) {
                previousActiveDirectory = activeScript.directoryPath;
                previousActiveTrust = activeScript.trust;
                this.unregisterScript(manifest.id);
            }

            if (fs.existsSync(target)) {
                fs.renameSync(target, backup);
                backupCreated = true;
            }
            fs.renameSync(staging, target);
            targetInstalled = true;

            if (shouldActivate) {
                this.scriptLoader.loadScript(target, 'user');
                for (const surface of this.activeSurfaces.values()) {
                    this.renderSurface(surface, manifest.id);
                }
            } else {
                this.logger.info(`Installed ${manifest.id}; an extension with the same ID is active from ${activeScript?.directoryPath ?? 'another script root'}`);
            }

            if (backupCreated) {
                try {
                    removeInstallDirectory(root, backup);
                } catch (cleanupError) {
                    this.logger.warn(`Installed ${manifest.id}, but could not remove its backup at ${backup}`, cleanupError);
                }
                backupCreated = false;
            }

            const installed = readInstalledExtensionInfo(target);
            this.logger.info(`Installed extension ${installed.id} v${installed.version ?? ''}`.trim());
            return installed;
        } catch (error) {
            if (targetInstalled && fs.existsSync(target)) {
                if (shouldActivate) this.unregisterScript(manifest.id);
                removeInstallDirectory(root, target);
                targetInstalled = false;
            }
            if (backupCreated && fs.existsSync(backup)) {
                try {
                    fs.renameSync(backup, target);
                    backupCreated = false;
                } catch (restoreError) {
                    this.logger.error(`Failed to restore ${manifest.id}; its backup remains at ${backup}`, restoreError);
                }
            }
            if (shouldActivate && previousActiveDirectory && previousActiveTrust) {
                try {
                    const restoreDirectory = previousWasActive ? target : previousActiveDirectory;
                    if (fs.existsSync(restoreDirectory)) {
                        this.scriptLoader.loadScript(restoreDirectory, previousActiveTrust);
                    }
                } catch (restoreError) {
                    this.logger.error(`Failed to reactivate ${manifest.id} after installation failed`, restoreError);
                }
            }
            throw error;
        } finally {
            if (fs.existsSync(staging)) removeInstallDirectory(root, staging);
        }
    }

    uninstallExtension(extensionId: string): LocalExtensionInfo {
        const installedRoot = this.config.installedRoot;
        if (!installedRoot) throw new Error('Marketplace installation is not configured');
        if (!this.scriptLoader) throw new Error('Marketplace uninstallation is unavailable while the script loader is starting');

        assertInstallableScriptId(extensionId);
        const root = path.resolve(installedRoot);
        const target = path.resolve(root, extensionId);
        assertPathInside(root, target);
        if (!fs.existsSync(target)) throw new Error(`${extensionId} is not installed`);

        const installed = readInstalledExtensionInfo(target);
        if (installed.id !== extensionId) {
            throw new Error(`Installed extension ID mismatch: expected ${extensionId}, got ${installed.id}`);
        }

        const removal = path.resolve(root, `.uninstall-${extensionId}-${randomUUID()}`);
        assertPathInside(root, removal);
        const activeScript = this.registry.getScript(extensionId);
        const wasActive = activeScript !== undefined && path.resolve(activeScript.directoryPath) === target;

        if (wasActive) this.scriptLoader.unloadFromRoot(target);

        try {
            fs.renameSync(target, removal);
            removeInstallDirectory(root, removal);
        } catch (error) {
            if (fs.existsSync(removal) && !fs.existsSync(target)) {
                fs.renameSync(removal, target);
            }
            if (wasActive && fs.existsSync(target)) {
                this.scriptLoader.loadScript(target, 'user');
            }
            throw error;
        }

        if (wasActive) this.activateFallbackExtension(extensionId);
        this.logger.info(`Uninstalled extension ${extensionId}`);
        return installed;
    }

    private activateFallbackExtension(extensionId: string): void {
        if (!this.scriptLoader) return;

        const roots = [
            this.config.developerMode ? this.config.localRoot : undefined,
            this.config.marketplaceRoot,
        ];
        for (const root of roots) {
            const directory = findExtensionDirectory(root, extensionId);
            if (!directory) continue;

            try {
                this.scriptLoader.loadScript(directory, 'user');
                for (const surface of this.activeSurfaces.values()) {
                    this.renderSurface(surface, extensionId);
                }
                this.logger.info(`Reactivated fallback extension ${extensionId} from ${directory}`);
                return;
            } catch (error) {
                this.logger.error(`Failed to reactivate fallback extension ${extensionId} from ${directory}`, error);
            }
        }
    }

    registerContextMenu(id: string, scriptId: string, title: string): void {
        this.bridge.registerContextMenu(id, scriptId, title);
    }

    registerSideDrawer(id: string, scriptId: string, title: string, iconRegistrationJson?: string): void {
        this.bridge.registerSideDrawer(id, scriptId, title, iconRegistrationJson);
    }

    registerEventListeners(): void {
        this.bridge.on('menu.press', payload => {
            const data = payload as { scriptId: string; id: string; uri: string; };
            if (!data) {
                this.bridge.log('Failed to read context menu press data');
                return;
            }

            this.registry.emitContextMenuPress(data.scriptId, data.id, data.uri);
        });

        this.bridge.on('side.press', payload => {
            const data = payload as { scriptId: string; id: string; };
            if (!data) {
                this.bridge.log('Failed to read side drawer press data');
                return;
            }

            this.activeSideDrawer = this.mountSideDrawer(data) ? data : null;
        });

        this.bridge.on('side.close', payload => {
            const data = payload as { scriptId: string; id: string; };
            if (!data) return;
            this.closeSurface(data.scriptId);
        });

        this.bridge.on('android.backPressed', async payload => {
            const data = payload as { scriptId: string; surfaceId: string; };
            if (!data?.scriptId) return;

            let defaultPrevented = false;
            const event = {
                surfaceId: data.surfaceId,
                get defaultPrevented() {
                    return defaultPrevented;
                },
                preventDefault() {
                    defaultPrevented = true;
                },
            };

            await this.registry.emitToScript(data.scriptId, 'android.backPressed', event);
            if (!defaultPrevented) this.closeSurface(data.scriptId);
        });

        this.bridge.on('react.surfaceEvent', payload => {
            const surface = payload as Surface;
            if (!surface) return;
            this.activeSurfaces.set(surface.id, surface);

            this.renderSurface(surface);
        });

        this.bridge.on('react.surfaceClose', payload => {
            const data = payload as { surfaceId: string };
            if (!data?.surfaceId) return;
            if (data.surfaceId === 'sideDrawer') this.activeSideDrawer = null;
            this.activeSurfaces.delete(data.surfaceId);
            this.registry.unmountAllSurfaces(data.surfaceId);
            clearCommitListener(data.surfaceId);
            this.bridge.unregisterSurface(data.surfaceId);
        });

        this.bridge.on('react.event', payload => {
            const data = payload as { eventId: number; payload: any; targetId: number; surfaceId: string; eventName: string };
            const eventId = Number(data?.eventId);
            if (!Number.isFinite(eventId)) return;

            dispatchReactEvent(eventId, {
                ...(data?.payload ?? {}),
                targetId: data.targetId,
                surfaceId: data.surfaceId,
                eventName: data.eventName,
            });
        });

        this.bridge.on('event.updateToken', payload => {
            const data = payload as Session;
            Object.assign(this.session, data);
        });
    }

    unregisterScript(scriptId: string): void {
        const surfaceIds = this.registry.unregisterScript(scriptId);
        for (const surfaceId of surfaceIds) {
            clearCommitListener(surfaceId);
            const surface = this.activeSurfaces.get(surfaceId);
            if (surface) clearCommitListener(surface.type);
        }
        this.bridge.unregisterScript(scriptId);
    }

    private renderSurface(surface: Surface, scriptId?: string): void {
        const renderers = this.registry.getSurfaceRenderers(surface.id);
        for (const renderer of renderers) {
            if (scriptId && renderer.scriptId !== scriptId) continue;
            this.renderSurfaceRenderer(renderer, surface);
        }
    }

    private renderSurfaceRenderer(renderer: RegisteredSurfaceRenderer, surface: Surface): void {
        try {
            const element = renderer.renderer(surface as any);
            this.bridge.registerSurface(surface.id);

            setCommitListener(surface.type, ops => {
                this.bridge.commitSurface(surface.id, ops);
            });

            this.registry.mountSurface(renderer.scriptId, surface, element);
        } catch (error) {
            this.bridge.log(`${error}`);
            this.reportScriptError(renderer.scriptId, `Failed to render surface ${surface.type}`, error);
        }
    }

    private mountSideDrawer(data: ActiveSideDrawer): boolean {
        this.bridge.registerSurface('sideDrawer');
        try {
            const mounted = this.registry.emitSideDrawerPress(data.scriptId, data.id);
            if (!mounted) this.bridge.unregisterSurface('sideDrawer');
            return mounted;
        } catch (error) {
            this.bridge.unregisterSurface('sideDrawer');
            throw error;
        }
    }

    private async handleHotReload(payload: HotReloadPayload): Promise<void> {
        if (!this.scriptLoader) {
            throw new Error('Hot reload failed because the script loader is not ready');
        }

        if (!payload?.scriptId || !payload?.buildId || !payload?.bundle) {
            throw new Error('Hot reload payload was missing scriptId, buildId, or bundle');
        }

        try {
            const bundle = payload.bundle;
            if (bundle.buildId !== payload.buildId) throw new Error(`Build ID mismatch: expected ${payload.buildId}, got ${bundle.buildId}`);
            if (bundle.manifest.id !== payload.scriptId) throw new Error(`Script ID mismatch: expected ${payload.scriptId}, got ${bundle.manifest.id}`);
            const trust = this.scriptLoader.getScriptTrust(payload.scriptId);
            if (trust === 'elevated' && !this.config.allowElevatedHotReload) {
                throw new Error(`Hot reload is not allowed for elevated script ${payload.scriptId} in this build`);
            }
            if (bundle.manifest.native) this.bridge.log(`Hot reload for ${payload.scriptId} is reloading JavaScript only; native code remains from the original load`);

            const scriptDirectory = this.scriptLoader.getScriptDirectory(payload.scriptId);
            const sideDrawerToRemount = this.activeSideDrawer?.scriptId === payload.scriptId ? { ...this.activeSideDrawer } : null;
            const manifest = this.scriptLoader.preflightSource(scriptDirectory, bundle.manifest, bundle.source);
            const validatedBundle = { ...bundle, manifest };
            this.writeHotReloadAssets(scriptDirectory, validatedBundle);
            this.unregisterScript(payload.scriptId);
            this.scriptLoader.loadScriptFromSource(scriptDirectory, manifest, bundle.source, false, trust);

            for (const surface of this.activeSurfaces.values()) {
                this.renderSurface(surface, payload.scriptId);
            }

            if (sideDrawerToRemount && !this.mountSideDrawer(sideDrawerToRemount)) {
                this.activeSideDrawer = null;
                this.bridge.log(`Hot reload could not remount side drawer item ${sideDrawerToRemount.id}`);
            }

            this.bridge.log(`Hot reloaded ${payload.scriptId} (${payload.buildId})`);
        } catch (error) {
            this.bridge.log(`Hot reload failed for ${payload.scriptId}: ${error instanceof Error ? error.message : String(error)}`);
            this.logger.error(`Hot reload failed for ${payload.scriptId}`, error);
            throw error;
        }
    }

    private writeHotReloadAssets(scriptDirectory: string, bundle: HotReloadBundle): void {
        const root = path.resolve(scriptDirectory);
        const entryDirectory = path.resolve(root, path.dirname(bundle.manifest.main));
        assertPathInside(root, entryDirectory);
        const patterns = bundle.manifest.assets.map(normalizeAssetPattern);
        let totalBytes = 0;

        for (const asset of bundle.assets ?? []) {
            const normalizedPath = normalizeHotReloadAssetPath(asset.path);
            if (!patterns.some(pattern => matchesAssetPattern(normalizedPath, pattern))) {
                throw new Error(`Hot reload asset is not declared in manifest.assets: ${normalizedPath}`);
            }

            const bytes = Buffer.from(asset.data, 'base64');
            if (asset.size !== undefined && bytes.byteLength !== asset.size) {
                throw new Error(`Hot reload asset size mismatch: ${normalizedPath}`);
            }
            totalBytes += bytes.byteLength;
            if (totalBytes > 20 * 1024 * 1024) throw new Error('Hot reload assets exceed the 20 MB limit');

            const destination = path.resolve(entryDirectory, ...normalizedPath.split('/'));
            assertPathInside(root, destination);
            fs.mkdirSync(path.dirname(destination), { recursive: true });
            fs.writeFileSync(destination, bytes);
        }

        fs.writeFileSync(
            path.join(root, 'manifest.json'),
            `${JSON.stringify(bundle.manifest, null, 2)}\n`,
            'utf8',
        );
    }

    private startHotReloadServer(port = 37846): void {
        if (this.hotReloadServer) return;

        const server = http.createServer((request, response) => {
            if (request.method === 'GET' && request.url === '/dev-logs') {
                this.attachDevLogClient(request, response);
                return;
            }

            if (request.method !== 'POST' || request.url !== '/hot-reload') {
                response.writeHead(404, { 'content-type': 'application/json' });
                response.end(JSON.stringify({ ok: false, error: 'not found' }));
                return;
            }

            let body = '';
            request.setEncoding('utf8');
            request.on('data', chunk => {
                body += chunk;
                if (body.length > 32 * 1024 * 1024) {
                    request.destroy(new Error('Hot reload payload is too large'));
                }
            });
            request.on('end', async () => {
                try {
                    const bundle = JSON.parse(body) as HotReloadBundle;
                    if (!bundle?.manifest?.id || !bundle?.buildId || typeof bundle.source !== 'string') {
                        throw new Error('Invalid hot reload bundle');
                    }

                    await this.handleHotReload({
                        scriptId: bundle.manifest.id,
                        buildId: bundle.buildId,
                        bundle,
                    });

                    response.writeHead(200, { 'content-type': 'application/json' });
                    response.end(JSON.stringify({ ok: true, scriptId: bundle.manifest.id, buildId: bundle.buildId }));
                } catch (error) {
                    response.writeHead(400, { 'content-type': 'application/json' });
                    response.end(JSON.stringify({
                        ok: false,
                        error: error instanceof Error ? error.message : String(error),
                        stack: error instanceof Error ? error.stack : undefined,
                    }));
                }
            });
            request.on('error', error => {
                this.logger.error('Hot reload request failed', error);
            });
        });

        server.on('error', error => {
            this.hotReloadServer = null;
            this.bridge.log(`Hot reload server failed: ${error instanceof Error ? error.message : String(error)}`);
        });

        server.listen(port, '127.0.0.1', () => {
            this.bridge.log(`Hot reload server listening on 127.0.0.1:${port}`);
        });

        this.hotReloadServer = server;
    }

    private installNodeConsoleForwarding(): void {
        if (this.nodeConsoleForwardingInstalled) return;
        this.nodeConsoleForwardingInstalled = true;

        for (const level of ['log', 'warn', 'error'] as const) {
            const original = console[level].bind(console);
            console[level] = (...args: unknown[]) => {
                original(...args);
                if (this.devLogClients.size === 0) return;

                try {
                    this.publishDevLog({
                        level,
                        message: formatDevLogArgs(args, {
                            maxOutputLength: 8000,
                            maxStringLength: 4000,
                        }),
                        stack: level === 'error' ? new Error().stack : undefined,
                    });
                } catch { }
            };
        }
    }

    private stopHotReloadServer(): void {
        if (!this.hotReloadServer) return;
        for (const response of this.devLogClients) response.end();
        this.devLogClients.clear();
        this.hotReloadServer.close();
        this.hotReloadServer = null;
        this.bridge.log('Hot reload server stopped');
    }

    private attachDevLogClient(request: http.IncomingMessage, response: http.ServerResponse): void {
        response.writeHead(200, {
            'cache-control': 'no-cache, no-transform',
            'connection': 'keep-alive',
            'content-type': 'text/event-stream; charset=utf-8',
        });
        response.write(': connected\n\n');
        this.devLogClients.add(response);

        const heartbeat = setInterval(() => response.write(': heartbeat\n\n'), 15000);
        request.on('close', () => {
            clearInterval(heartbeat);
            this.devLogClients.delete(response);
        });
    }

    private publishDevLog(entry: Omit<DevLogEntry, 'id' | 'timestamp'>): void {
        if (this.devLogClients.size === 0) return;

        const payload = JSON.stringify({
            ...entry,
            id: this.nextDevLogId++,
            timestamp: new Date().toISOString(),
        } satisfies DevLogEntry);
        for (const response of this.devLogClients) {
            try {
                response.write(`data: ${payload}\n\n`);
            } catch {
                this.devLogClients.delete(response);
            }
        }
    }

    private async handleIncomingPacket(packet: Packet): Promise<void> {
        this.logger.info(`Incoming ${packet.type}:${packet.name ?? packet.id}`);

        switch (packet.type) {
            case 'event':
                if (packet.name === 'event.connecting') {
                    this.markSpotifyConnecting();
                }
                if (packet.name === 'event.ready') {
                    this.markSpotifyReady();
                    Object.assign(this.platformData, packet.payload as PlatformData);
                }
                if (packet.name === 'event.updateToken') {
                    Object.assign(this.session, packet.payload as Session);
                }
                if (packet.name === 'menu.press') {
                    const payload = packet.payload as { scriptId: string; id: string, uri: string };
                    this.registry.emitContextMenuPress(payload.scriptId, payload.id, payload.uri);
                }
                if (packet.name === 'side.press') {
                    const payload = packet.payload as { scriptId: string; id: string };
                    const items = this.registry.getSideDrawerItems();
                    const item = items.get(payload.id);

                    const result = item?.item.onClick();
                    if (result && React.isValidElement(result)) {
                        this.bridge.registerSurface('sideDrawer');
                        setCommitListener('sideDrawer', ops => {
                            this.sendCommand('react.commit', { surfaceId: 'sideDrawer', ops });
                        });

                        this.registry.mountSurface(payload.scriptId, { id: 'sideDrawer', type: 'sideDrawer' }, result);
                    }
                }
                if (packet.name === 'side.close') {
                    const payload = packet.payload as { scriptId: string; id: string };
                    this.registry.unmountSurface(payload.scriptId, 'sideDrawer');
                    clearCommitListener('sideDrawer');
                    this.bridge.unregisterSurface('sideDrawer');
                }
                if (packet.name === 'react.surfaceEvent') {
                    const payload = packet.payload as Surface;
                    const renderers = this.registry.getSurfaceRenderers(payload.id);

                    for (const renderer of renderers) {
                        const element = renderer.renderer(payload as any);
                        setCommitListener(payload.type, ops => {
                            this.sendCommand('react.commit', { surfaceId: payload.id, ops });
                        });

                        this.registry.mountSurface(renderer.scriptId, payload, element);
                    }
                }
                if (packet.name === 'react.event') {
                    const payload = packet.payload as { eventId: number; payload: any, targetId: string, surfaceId: string, eventName: string };
                    const eventId = Number(payload?.eventId);
                    if (!Number.isFinite(eventId)) return;

                    dispatchReactEvent(eventId, {
                        ...(payload?.payload ?? {}),
                        targetId: payload.targetId,
                        surfaceId: payload.surfaceId,
                        eventName: payload.eventName
                    });
                }
                if (packet.name === 'react.surfaceClose') {
                    const payload = packet.payload as { surfaceId: string };
                    this.registry.unmountAllSurfaces(payload.surfaceId);
                    clearCommitListener(payload.surfaceId);
                }

                await this.registry.emit(packet.name!, packet.payload);
                break;

            case 'command':
                await this.registry.emit(packet.name!, packet.payload);
                break;

            case 'response':
                this.handleResponse(packet as ResponsePacket);
                break;

            case 'error':
                this.handleErrorPacket(packet as ErrorPacket<{ message?: string; stack?: string; code?: string }>);
                break;

            case 'request':
                this.logger.warn(`Unexpected request from Java: ${packet.name}`);
                break;
        }
    }

    private handleResponse(packet: ResponsePacket): void {
        if (!packet.id) {
            this.logger.warn(`Response without ID for ${packet.name}`);
            return;
        }

        const pending = this.pendingRequests.get(packet.id);
        if (!pending) {
            this.logger.warn(`No pending request for response ${packet.id}`);
            return;
        }

        this.pendingRequests.delete(packet.id);
        pending.resolve(packet.payload);
    }

    private handleErrorPacket(packet: ErrorPacket<{ message?: string; stack?: string; code?: string }>): void {
        if (packet.id) {
            const pending = this.pendingRequests.get(packet.id);
            if (pending) {
                this.pendingRequests.delete(packet.id);
                const error = new Error(packet.payload?.message ?? `Request failed: ${packet.name}`);

                if (packet.payload?.stack) error.stack = packet.payload.stack;
                pending.reject(error);
                return;
            }
        }

        this.logger.error(`Unhandled error packet ${packet.name}`, packet.payload);
    }

    waitForSpotifyConnecting(): Promise<void> {
        if (this.spotifyConnecting) return Promise.resolve();

        return new Promise<void>(resolve => {
            const waiter = () => {
                this.spotifyConnectingWaiters.delete(waiter);
                resolve();
            };

            this.spotifyConnectingWaiters.add(waiter);
        });
    }

    private markSpotifyConnecting(): void {
        if (this.spotifyConnecting) return;

        this.spotifyConnecting = true;

        for (const waiter of this.spotifyConnectingWaiters) {
            try {
                waiter(true);
            } catch { }
        }

        this.spotifyConnectingWaiters.clear();
    }

    private markSpotifyReady(): void {
        if (this.spotifyReady) return;

        this.spotifyReady = true;

        for (const waiter of this.spotifyReadyWaiters) {
            try {
                waiter(true);
            } catch { }
        }

        this.spotifyReadyWaiters.clear();
    }

    loadApk(scriptId: string, apkPath: string, pluginClass: string): void {
        this.bridge.loadApk(scriptId, apkPath, pluginClass);
    }

    createWorkletContext(scriptId: string, generation: number): boolean {
        return this.bridge.createWorkletContext(scriptId, generation);
    }

    disposeWorkletContext(scriptId: string, generation: number): boolean {
        return this.bridge.disposeWorkletContext(scriptId, generation);
    }

    createAnimationAdapter(scriptId: string, generation: number): NativeAnimationAdapter {
        return createSpotifyPlusWorkletAdapter(
            this.bridge,
            this.logger.child(`Animated:${scriptId}`),
            Object.freeze({ scriptId, generation }),
        );
    }

    settingsTest(message: string) {
        this.message = message;
    }

    readMessage(): string {
        return this.message;
    }

    registerSetting(extensionId: string, setting: ExtensionSettingSection) {
        let extension = this.extensionSettings.find(x => x.extensionId === extensionId);
        if (!extension) {
            extension = { extensionId, settings: [] };
            this.extensionSettings.push(extension);
        }

        extension.settings.push(setting);
    }

    registerSettings(extensionId: string, settings: ExtensionSettingSection[]) {
        let extension = this.extensionSettings.find(x => x.extensionId === extensionId);
        if (!extension) {
            extension = { extensionId, settings };
            this.extensionSettings.push(extension);
        } else {
            extension = { extensionId, settings };
        }
    }

    getExtensionSettings(): ExtensionSettings[] {
        return this.extensionSettings;
    }

    emitSettingChanged(extensionId: string, setting: ExtensionSetting) {
        this.emitToExtension(extensionId, 'settings.changed', setting);
    }

    emitToExtension(extensionId: string, eventName: string, payload: unknown): void {
        void this.registry.emitToExtension(extensionId, eventName, payload);
    }
}

function normalizeHotReloadAssetPath(value: string): string {
    if (typeof value !== 'string' || value.trim().length === 0 || value.includes('\0')) {
        throw new Error('Hot reload asset path must be a non-empty relative path');
    }
    if (path.isAbsolute(value) || /^[a-zA-Z]:/.test(value)) {
        throw new Error(`Hot reload asset path must be relative: ${value}`);
    }
    const normalized = value.replace(/\\/g, '/').replace(/^\.\//, '');
    const segments = normalized.split('/');
    if (segments.some(segment => !segment || segment === '.' || segment === '..') || /[*?]/.test(normalized)) {
        throw new Error(`Invalid hot reload asset path: ${value}`);
    }
    return normalized;
}

function assertPathInside(root: string, candidate: string): void {
    const relative = path.relative(root, candidate);
    if (relative === '' || (!relative.startsWith(`..${path.sep}`) && relative !== '..' && !path.isAbsolute(relative))) {
        return;
    }
    throw new Error(`Path escaped its extension directory: ${candidate}`);
}

const MAX_INSTALL_FILE_BYTES = 64 * 1024 * 1024;
const MAX_INSTALL_PACKAGE_BYTES = 128 * 1024 * 1024;
const MAX_INSTALL_FILE_COUNT = 5000;

function writeInstallPackage(
    stagingDirectory: string,
    manifest: ScriptManifest,
    rawManifest: unknown,
    files: ExtensionInstallRequest['files'],
): void {
    if (files.length === 0) throw new Error('Extension package contains no files');
    if (files.length > MAX_INSTALL_FILE_COUNT) {
        throw new Error(`Extension package contains more than ${MAX_INSTALL_FILE_COUNT} files`);
    }

    const entryDirectory = path.posix.dirname(manifest.main) === '.'
        ? ''
        : path.posix.dirname(manifest.main);
    const nativePath = manifest.native
        ? joinManifestEntryPath(entryDirectory, manifest.native.apk)
        : undefined;
    const assetPatterns = manifest.assets.map(pattern => ({
        pattern: normalizeAssetPattern(pattern),
        matches: 0,
    }));
    const writtenPaths = new Set<string>();
    let totalBytes = 0;

    for (const file of files) {
        const relativePath = normalizeInstallFilePath(file?.path);
        if (relativePath === 'manifest.json') {
            throw new Error('Extension package cannot replace its validated manifest');
        }
        if (writtenPaths.has(relativePath)) throw new Error(`Extension package contains duplicate file ${relativePath}`);

        const entryRelativePath = relativeToManifestEntry(entryDirectory, relativePath);
        const matchingPatterns = entryRelativePath === undefined
            ? []
            : assetPatterns.filter(item => matchesAssetPattern(entryRelativePath, item.pattern));
        const allowed = relativePath === manifest.main
            || relativePath === nativePath
            || matchingPatterns.length > 0;
        if (!allowed) throw new Error(`Extension package contains undeclared file ${relativePath}`);

        const bytes = installFileBuffer(file.data, relativePath);
        if (bytes.byteLength > MAX_INSTALL_FILE_BYTES) {
            throw new Error(`Extension package file exceeds the 64 MB limit: ${relativePath}`);
        }
        totalBytes += bytes.byteLength;
        if (totalBytes > MAX_INSTALL_PACKAGE_BYTES) {
            throw new Error('Extension package exceeds the 128 MB limit');
        }

        const destination = path.resolve(stagingDirectory, ...relativePath.split('/'));
        assertPathInside(stagingDirectory, destination);
        fs.mkdirSync(path.dirname(destination), { recursive: true });
        fs.writeFileSync(destination, bytes);
        writtenPaths.add(relativePath);
        for (const item of matchingPatterns) item.matches += 1;
    }

    if (!writtenPaths.has(manifest.main)) {
        throw new Error(`Extension package is missing its main entry ${manifest.main}`);
    }
    if (nativePath && !writtenPaths.has(nativePath)) {
        throw new Error(`Extension package is missing its native APK ${nativePath}`);
    }
    const unmatchedAssets = assetPatterns.filter(item => item.matches === 0).map(item => item.pattern);
    if (unmatchedAssets.length > 0) {
        throw new Error(`Extension asset patterns matched no package files: ${unmatchedAssets.join(', ')}`);
    }

    fs.writeFileSync(
        path.join(stagingDirectory, 'manifest.json'),
        `${JSON.stringify({
            ...(rawManifest as Record<string, unknown>),
            id: manifest.id,
            name: manifest.name,
            version: manifest.version,
            main: manifest.main,
            permissions: manifest.permissions,
            api: manifest.api,
            assets: manifest.assets,
            ...(manifest.native ? { native: manifest.native } : {}),
        }, null, 2)}\n`,
        'utf8',
    );
}

function normalizeInstallFilePath(value: unknown): string {
    if (typeof value !== 'string' || value.trim().length === 0 || value.includes('\0')) {
        throw new Error('Extension package file path must be a non-empty relative path');
    }
    if (path.isAbsolute(value) || /^[a-zA-Z]:/.test(value)) {
        throw new Error(`Extension package file path must be relative: ${value}`);
    }

    const normalized = value.trim().replace(/\\/g, '/').replace(/^\.\//, '');
    const segments = normalized.split('/');
    if (segments.some(segment => !segment || segment === '.' || segment === '..') || /[*?]/.test(normalized)) {
        throw new Error(`Invalid extension package file path: ${value}`);
    }
    return normalized;
}

function installFileBuffer(data: unknown, relativePath: string): Buffer {
    if (Buffer.isBuffer(data)) return Buffer.from(data);
    if (ArrayBuffer.isView(data)) {
        return Buffer.from(data.buffer, data.byteOffset, data.byteLength);
    }
    if (Object.prototype.toString.call(data) === '[object ArrayBuffer]') {
        return Buffer.from(data as ArrayBuffer);
    }
    throw new Error(`Extension package file has invalid binary data: ${relativePath}`);
}

function joinManifestEntryPath(entryDirectory: string, relativePath: string): string {
    return entryDirectory ? `${entryDirectory}/${relativePath}` : relativePath;
}

function relativeToManifestEntry(entryDirectory: string, repositoryPath: string): string | undefined {
    if (!entryDirectory) return repositoryPath;
    const prefix = `${entryDirectory}/`;
    return repositoryPath.startsWith(prefix) ? repositoryPath.slice(prefix.length) : undefined;
}

function assertInstallableScriptId(scriptId: string): void {
    if (!/^[a-zA-Z0-9][a-zA-Z0-9._-]*$/.test(scriptId)) {
        throw new Error(`Extension ID cannot be used as an install directory: ${scriptId}`);
    }
}

function readInstalledExtensionInfo(directory: string): LocalExtensionInfo {
    const manifestPath = path.join(directory, 'manifest.json');
    const raw = JSON.parse(fs.readFileSync(manifestPath, 'utf8')) as Record<string, unknown>;
    const manifest = parseManifest(raw);
    const authors = Array.isArray(raw.authors)
        ? raw.authors
            .map(author => author && typeof author === 'object' ? (author as Record<string, unknown>).name : undefined)
            .filter((name): name is string => typeof name === 'string' && name.trim().length > 0)
        : [];

    return {
        id: manifest.id,
        name: manifest.name,
        version: manifest.version,
        description: manifest.description,
        author: manifest.author ?? authors.join(', '),
        path: directory,
        installedAt: fs.statSync(directory).mtime.toISOString(),
    };
}

function removeInstallDirectory(root: string, directory: string): void {
    assertPathInside(root, directory);
    fs.rmSync(directory, { recursive: true, force: true });
}

function findExtensionDirectory(root: string | undefined, extensionId: string): string | undefined {
    if (!root || !fs.existsSync(root)) return undefined;

    for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
        if (!entry.isDirectory() || entry.name.startsWith('.')) continue;

        const directory = path.join(root, entry.name);
        try {
            if (readInstalledExtensionInfo(directory).id === extensionId) return directory;
        } catch {
            // Invalid or unrelated directories are ignored while locating a fallback.
        }
    }
    return undefined;
}

function isPathInside(root: string, candidate: string): boolean {
    const resolvedRoot = path.resolve(root);
    const resolvedCandidate = path.resolve(candidate);
    const relative = path.relative(resolvedRoot, resolvedCandidate);
    return relative === ''
        || (!relative.startsWith(`..${path.sep}`) && relative !== '..' && !path.isAbsolute(relative));
}
