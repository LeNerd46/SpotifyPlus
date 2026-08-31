import path from 'path';
import { Logger } from '../core/logger';
import { Packet, parsePacket, stringify } from '../core/protocol';
import EventEmitter from 'events';
import { PlatformData, SpotifyTrack } from '../core/models';
import { MutationOp } from '../ui/renderer';
import type { LocalExtensionInfo, NavigationTarget } from '../loader/script-api';

type NativeStorageRead = {
    type: 'text' | 'json' | 'binary';
    value?: string;
    data?: string;
};

const CACHE_SCRIPT_ID_PREFIX = 'spotifyplus-cache:';
const FILE_DELETE_SCRIPT_ID_PREFIX = 'spotifyplus-delete:';

interface NativeBridge {
    sendToJava(json: string): void;
    pollFromJava(): string | undefined;

    setEventHandler(callback: (type: string, payload: string) => void): void;
    loadApk(scriptId: string, apkPath: string, pluginClass: string): void;
    unregisterScript(scriptId: string): void;

    getPlatformData(): PlatformData;
    getAccessToken(): string;
    log(message: string): void;

    getCurrentTrack(): SpotifyTrack;
    getTrack(uri: string): SpotifyTrack | undefined;
    getPlaybackPosition(): number;
    seek(position: number): void;
    play(): void;
    pause(): void;
    togglePlay(): void;
    skipNext(): void;
    skipPrevious(): void;

    toast(text: string, longLength?: boolean): void;
    navigate(uri: string, target: NavigationTarget): boolean;
    navigateBack(): boolean;

    storageSet(scriptId: string, key: string, value: string): void;
    storageGet(scriptId: string, key: string): string | undefined;
    storageRemove(scriptId: string, key: string): void;
    storageWriteText(scriptId: string, path: string, value: string): void;
    storageWriteJson(scriptId: string, path: string, value: string): void;
    storageWriteBinary(scriptId: string, path: string, data: string): void;
    storageRead(scriptId: string, path: string): NativeStorageRead | undefined;
    getDeveloperMode(): boolean;
    setDeveloperMode(enabled: boolean): void;
    pickLocalExtensionsFolder(): boolean;
    getLocalExtensionsFolderDisplayName(): string | undefined;
    listLocalExtensions(): string | undefined;
    refreshLocalExtensions(): string | undefined;

    registerContextMenu(id: string, scriptId: string, title: string): void;
    registerSideDrawer(id: string, scriptId: string, title: string, iconRegistrationJson?: string): void;

    registerSurface(surfaceId: string): void;
    unregisterSurface(surfaceId: string): void;
    commitSurface(surfaceId: string, opsJson: string): void;

    isWorkletRuntimeAvailable(): boolean;
    createWorkletContext(scriptId: string, generation: number): boolean;
    disposeWorkletContext(scriptId: string, generation: number): boolean;
    disposeWorkletScript(scriptId: string): boolean;
    registerWorklet(scriptId: string, generation: number, workletId: string, source: string, closureJson: string): boolean;
    unregisterWorklet(scriptId: string, generation: number, workletId: string): boolean;
    installWorkletGlobals(scriptId: string, generation: number, moduleName: string, source: string): boolean;
    scheduleWorklet(scriptId: string, generation: number, workletId: string, argsJson: string): boolean;
    registerWorkletMapper(
        scriptId: string,
        generation: number,
        mapperId: string,
        workletId: string,
        surfaceId: string,
        nodeId: number,
        priority: number,
        runEveryFrame: boolean,
    ): boolean;
    unregisterWorkletMapper(scriptId: string, generation: number, mapperId: string): boolean;
    setWorkletSharedValue(scriptId: string, generation: number, sharedValueId: string, valueJson: string): boolean;
    getWorkletSharedValue(scriptId: string, generation: number, sharedValueId: string): string | undefined;
    deleteWorkletSharedValue(scriptId: string, generation: number, sharedValueId: string): boolean;
    cancelWorkletAnimation(scriptId: string, generation: number, sharedValueId: string): boolean;
    getWorkletReducedMotion(): boolean;
    setWorkletReducedMotionOverride(scriptId: string, generation: number, modeJson: string): boolean;
    registerWorkletSource(
        scriptId: string,
        generation: number,
        sourceId: string,
        sharedValueId: string,
        configJson: string,
    ): boolean;
    unregisterWorkletSource(scriptId: string, generation: number, sourceId: string, sharedValueId: string): boolean;
    drainWorkletErrors(): Array<Record<string, unknown>>;
}

export class Bridge extends EventEmitter {
    private readonly addon: NativeBridge;
    private pollingHandle: NodeJS.Timeout | null = null;

    constructor(private readonly logger: Logger) {
        super();
        const addonPath = path.join(__dirname, '..', 'spotifyplus_bridge.node');
        this.logger.info(`Loading addon from ${addonPath}`);
        this.addon = require(addonPath) as NativeBridge;

        this.addon.setEventHandler((type, payload) => {
            this.emit(type, JSON.parse(payload));
        });
    }

    send(packet: Packet): void {
        const json = stringify(packet);
        this.logger.info(`Sending packet ${packet.type}:${packet.name}`);
        this.addon.sendToJava(json);
    }

    startPolling(onPacket: (packet: Packet) => void, intervalMs = 8): void {
        // if (this.pollingHandle) return;
        // this.pollingHandle = setInterval(() => {
        //     const json = this.addon.pollFromJava();
        //     if (!json) return;
        //     try {
        //         const packet = parsePacket(json);
        //         onPacket(packet);
        //     } catch (error) {
        //         this.logger.error('Failed to parse packet from Java', error);
        //     }
        // }, intervalMs);
    }

    stopPolling(): void {
        // if (!this.pollingHandle) return;
        // clearInterval(this.pollingHandle);
        // this.pollingHandle = null;
    }

    loadApk(scriptId: string, apkPath: string, pluginClass: string): void {
        this.addon.loadApk(scriptId, apkPath, pluginClass);
    }

    unregisterScript(scriptId: string): void {
        this.addon.disposeWorkletScript(scriptId);
        this.addon.unregisterScript(scriptId);
    }

    getPlatformData(): PlatformData {
        return this.addon.getPlatformData();
    }

    getAccessToken(): string {
        return this.addon.getAccessToken();
    }

    log(message: string): void {
        this.addon.log(message);
    }

    getCurrentTrack(): SpotifyTrack {
        return this.addon.getCurrentTrack();
    }

    getTrack(uri: string): SpotifyTrack | null {
        return this.addon.getTrack(uri) ?? null;
    }

    getPlaybackPosition(): number {
        return this.addon.getPlaybackPosition();
    }

    seek(position: number): void {
        this.addon.seek(position);
    }

    play(): void {
        this.addon.play();
    }

    pause(): void {
        this.addon.pause();
    }

    togglePlay(): void {
        this.addon.togglePlay();
    }

    skipNext(): void {
        this.addon.skipNext();
    }

    skipPrevious(): void {
        this.addon.skipPrevious();
    }

    toast(text: string, length: 'short' | 'long' = 'short'): void {
        this.addon.toast(text, length === 'long');
    }

    navigate(uri: string, target: NavigationTarget): boolean {
        return this.addon.navigate(uri, target);
    }

    navigateBack(): boolean {
        return this.addon.navigateBack();
    }

    storageSet(scriptId: string, key: string, value: unknown): void {
        this.addon.storageSet(scriptId, key, JSON.stringify(value));
    }

    storageGet<T = any>(scriptId: string, key: string): T | null {
        const value = this.addon.storageGet(scriptId, key);
        if (value == null) return null;

        try {
            return JSON.parse(value) as T;
        } catch {
            return null;
        }
    }

    storageRemove(scriptId: string, key: string): void {
        this.addon.storageRemove(scriptId, key);
    }

    storageWriteText(scriptId: string, path: string, value: string): void {
        this.addon.storageWriteText(scriptId, path, value);
    }

    storageWriteJson(scriptId: string, path: string, value: unknown): void {
        this.addon.storageWriteJson(scriptId, path, JSON.stringify(value));
    }

    storageWriteBinary(scriptId: string, path: string, data: string): void {
        this.addon.storageWriteBinary(scriptId, path, data);
    }

    storageRead<T = any>(scriptId: string, path: string): { type?: 'text' | 'json' | 'binary'; value?: T | string | null; data?: string | null } | null {
        const payload = this.addon.storageRead(scriptId, path);
        if (!payload) return null;

        if (payload.type === 'json') {
            try {
                return {
                    type: 'json',
                    value: payload.value ? JSON.parse(payload.value) as T : null,
                    data: null
                };
            } catch {
                return {
                    type: 'json',
                    value: null,
                    data: null
                };
            }
        }

        if (payload.type === 'text') {
            return {
                type: 'text',
                value: payload.value ?? null,
                data: null
            };
        }

        return {
            type: 'binary',
            value: null,
            data: payload.data ?? null
        };
    }

    storageDelete(scriptId: string, path: string): void {
        this.storageRemove(`${FILE_DELETE_SCRIPT_ID_PREFIX}${scriptId}`, path);
    }

    cacheWriteText(scriptId: string, path: string, value: string): void {
        this.storageWriteText(`${CACHE_SCRIPT_ID_PREFIX}${scriptId}`, path, value);
    }

    cacheWriteJson(scriptId: string, path: string, value: unknown): void {
        this.storageWriteJson(`${CACHE_SCRIPT_ID_PREFIX}${scriptId}`, path, value);
    }

    cacheWriteBinary(scriptId: string, path: string, data: string): void {
        this.storageWriteBinary(`${CACHE_SCRIPT_ID_PREFIX}${scriptId}`, path, data);
    }

    cacheRead<T = any>(scriptId: string, path: string): { type?: 'text' | 'json' | 'binary'; value?: T | string | null; data?: string | null } | null {
        return this.storageRead<T>(`${CACHE_SCRIPT_ID_PREFIX}${scriptId}`, path);
    }

    cacheDelete(scriptId: string, path: string): void {
        this.storageDelete(`${CACHE_SCRIPT_ID_PREFIX}${scriptId}`, path);
    }

    getDeveloperMode(): boolean {
        return this.addon.getDeveloperMode();
    }

    setDeveloperMode(enabled: boolean): void {
        this.addon.setDeveloperMode(enabled);
    }

    pickLocalExtensionsFolder(): boolean {
        return this.addon.pickLocalExtensionsFolder();
    }

    getLocalExtensionsFolderDisplayName(): string | null {
        return this.addon.getLocalExtensionsFolderDisplayName() ?? null;
    }

    listLocalExtensions(): LocalExtensionInfo[] {
        return parseLocalExtensions(this.addon.listLocalExtensions());
    }

    refreshLocalExtensions(): LocalExtensionInfo[] {
        return parseLocalExtensions(this.addon.refreshLocalExtensions());
    }

    registerContextMenu(id: string, scriptId: string, title: string): void {
        this.addon.registerContextMenu(id, scriptId, title);
    }

    registerSideDrawer(id: string, scriptId: string, title: string, iconRegistrationJson?: string): void {
        if (iconRegistrationJson) {
            this.addon.registerSideDrawer(id, scriptId, title, iconRegistrationJson);
            return;
        }
        this.addon.registerSideDrawer(id, scriptId, title);
    }

    registerSurface(surfaceId: string): void {
        this.addon.registerSurface(surfaceId);
    }

    unregisterSurface(surfaceId: string): void {
        this.addon.unregisterSurface(surfaceId);
    }

    commitSurface(surfaceId: string, ops: MutationOp[]): void {
        this.addon.commitSurface(surfaceId, JSON.stringify(ops));
    }

    isWorkletRuntimeAvailable(): boolean {
        return this.addon.isWorkletRuntimeAvailable();
    }

    createWorkletContext(scriptId: string, generation: number): boolean {
        return this.addon.createWorkletContext(scriptId, generation);
    }

    disposeWorkletContext(scriptId: string, generation: number): boolean {
        return this.addon.disposeWorkletContext(scriptId, generation);
    }

    registerWorklet(scriptId: string, generation: number, workletId: string, source: string, closure: unknown): boolean {
        return this.addon.registerWorklet(scriptId, generation, workletId, source, JSON.stringify(closure ?? {}));
    }

    unregisterWorklet(scriptId: string, generation: number, workletId: string): boolean {
        return this.addon.unregisterWorklet(scriptId, generation, workletId);
    }

    installWorkletGlobals(scriptId: string, generation: number, moduleName: string, source: string): boolean {
        return this.addon.installWorkletGlobals(scriptId, generation, moduleName, source);
    }

    scheduleWorklet(scriptId: string, generation: number, workletId: string, args: unknown[]): boolean {
        return this.addon.scheduleWorklet(scriptId, generation, workletId, JSON.stringify(args));
    }

    registerWorkletMapper(
        scriptId: string,
        generation: number,
        mapperId: string,
        workletId: string,
        surfaceId: string,
        nodeId: number,
        priority: number,
        runEveryFrame: boolean,
    ): boolean {
        return this.addon.registerWorkletMapper(
            scriptId,
            generation,
            mapperId,
            workletId,
            surfaceId,
            nodeId,
            priority,
            runEveryFrame,
        );
    }

    unregisterWorkletMapper(scriptId: string, generation: number, mapperId: string): boolean {
        return this.addon.unregisterWorkletMapper(scriptId, generation, mapperId);
    }

    setWorkletSharedValue(scriptId: string, generation: number, sharedValueId: string, value: unknown): boolean {
        return this.addon.setWorkletSharedValue(scriptId, generation, sharedValueId, JSON.stringify(value));
    }

    getWorkletSharedValue<T>(scriptId: string, generation: number, sharedValueId: string): T | undefined {
        const value = this.addon.getWorkletSharedValue(scriptId, generation, sharedValueId);
        if (value === undefined) return undefined;
        return JSON.parse(value) as T;
    }

    deleteWorkletSharedValue(scriptId: string, generation: number, sharedValueId: string): boolean {
        return this.addon.deleteWorkletSharedValue(scriptId, generation, sharedValueId);
    }

    cancelWorkletAnimation(scriptId: string, generation: number, sharedValueId: string): boolean {
        return this.addon.cancelWorkletAnimation(scriptId, generation, sharedValueId);
    }

    getWorkletReducedMotion(): boolean {
        return this.addon.getWorkletReducedMotion();
    }

    setWorkletReducedMotionOverride(
        scriptId: string,
        generation: number,
        mode: "system" | "always" | "never" | null,
    ): boolean {
        return this.addon.setWorkletReducedMotionOverride(scriptId, generation, JSON.stringify(mode));
    }

    registerWorkletSource(
        scriptId: string,
        generation: number,
        sourceId: string,
        sharedValueId: string,
        config: unknown,
    ): boolean {
        return this.addon.registerWorkletSource(
            scriptId,
            generation,
            sourceId,
            sharedValueId,
            JSON.stringify(config ?? {}),
        );
    }

    unregisterWorkletSource(
        scriptId: string,
        generation: number,
        sourceId: string,
        sharedValueId: string,
    ): boolean {
        return this.addon.unregisterWorkletSource(scriptId, generation, sourceId, sharedValueId);
    }

    drainWorkletErrors(): Array<Record<string, unknown>> {
        return this.addon.drainWorkletErrors();
    }
}

function parseLocalExtensions(raw?: string): LocalExtensionInfo[] {
    if (!raw) return [];
    try {
        const parsed = JSON.parse(raw);
        return Array.isArray(parsed) ? parsed as LocalExtensionInfo[] : [];
    } catch {
        return [];
    }
}
