import { EventHandler, SurfaceRenderer } from "./script-registry";
import { ContextMenu, OnClickCallback, PlatformData, Session, ShouldAddCallback, SideDrawerItem, SideOnClickCallback, SpotifyTrack } from "../core/models";
import { Logger } from "../core/logger";
import { HostRuntime } from "./host-runtime";
import React from "react";
import type { ScriptTrust } from "./script-loader";
import {
    createExtensionAssetsApi,
    ExtensionAsset,
    ExtensionAssetsApi,
    ExtensionFontAsset,
    ExtensionFontFace,
    ExtensionFontFamily,
    FontStyle,
    FontWeight,
    getNativeAssetRegistration,
} from "../core/extension-assets";
import { ExtensionSetting, ExtensionSettings, ExtensionSettingSection } from "./settings";
import type { ExtensionEventHandler } from "./extension-event-emitter";

export type {
    ExtensionAsset,
    ExtensionAssetsApi,
    ExtensionFontAsset,
    ExtensionFontFace,
    ExtensionFontFamily,
    FontStyle,
    FontWeight,
};
export type { ExtensionEventHandler } from "./extension-event-emitter";

export interface ScriptConsole {
    log: (...args: unknown[]) => void;
    warn: (...args: unknown[]) => void;
    error: (...args: unknown[]) => void;
}

export interface ContextMenuConstructor {
    new(name: string, onClick: OnClickCallback, shouldAdd?: ShouldAddCallback, disabled?: boolean): ContextMenu;
}

export interface SideDrawerConstructor {
    /** Creates a side-drawer item. Transparent images work best for icons. */
    new(name: string, onClick: SideOnClickCallback, icon?: ExtensionAsset): SideDrawerItem;
}

export type NavigationTarget = 'auto' | 'spotify' | 'external';

export interface NavigationOptions {
    /** Selects which app should handle the URI. Defaults to the best available app. */
    target?: NavigationTarget;
}

export interface NavigationApi {
    /** Opens any absolute URI, including web, Spotify, mail, phone, and map links. */
    open(uri: string, options?: NavigationOptions): boolean;
    /** Opens a Spotify URI or web link inside Spotify. */
    openSpotify(uri: string): boolean;
    /** Opens an HTTP or HTTPS URL in the user's default web browser. */
    openExternal(url: string): boolean;
    /** Navigates back from the current Spotify destination or overlay. */
    back(): boolean;
}

export interface AndroidBackButtonEvent {
    /** The scripted surface that was active when Android's back button was pressed. */
    surfaceId: string;
    /** Whether the default behavior of closing the scripted surface has been prevented. */
    readonly defaultPrevented: boolean;
    /** Keeps the scripted surface open so the extension can handle its own back navigation. */
    preventDefault(): void;
}

export interface ScriptGlobals {
    SpotifyPlus: SpotifyPlusApi;
    Elevated?: ElevatedSpotifyPlusApi;
    console: ScriptConsole;
    setTimeout: typeof setTimeout;
    setInterval: typeof setInterval;
    clearTimeout: typeof clearTimeout;
    clearInterval: typeof clearInterval;
    global: unknown;
    globalThis: unknown;
}

export interface FileStorageOperations {
    write(path: string, value: string): void;
    write<T = any>(path: string, value: T): void;
    write(path: string, data: Uint8Array | ArrayBuffer): void;
    read<T = any>(path: string): Promise<T | string | Uint8Array | null>;
    delete(path: string): void;
}

export interface StorageApi extends FileStorageOperations {
    set(key: string, value: any): void;
    get<T = any>(key: string): Promise<T | null>;
    remove(key: string): void;
    /** Temporary, extension-scoped storage backed by Android's cache directory. */
    Cache: FileStorageOperations;
}

/** Events visible only to this extension. */
export interface ExtensionEventEmitterApi {
    on<TPayload = unknown>(eventName: string, handler: ExtensionEventHandler<TPayload>): void;
    once<TPayload = unknown>(eventName: string, handler: ExtensionEventHandler<TPayload>): void;
    off<TPayload = unknown>(eventName: string, handler: ExtensionEventHandler<TPayload>): void;
    emit<TPayload = unknown>(eventName: string, payload?: TPayload): Promise<void>;
}

export interface LocalExtensionInfo {
    id: string;
    name: string;
    version?: string;
    description?: string;
    author?: string;
    path: string;
    installedAt: string;
}

export interface ExtensionInstallFile {
    path: string;
    data: Uint8Array | ArrayBuffer;
}

export interface ExtensionInstallRequest {
    manifest: unknown;
    files: ExtensionInstallFile[];
}

export interface ElevatedSpotifyPlusApi {
    getDeveloperMode(): boolean;
    setDeveloperMode(enabled: boolean): void;
    pickLocalExtensionsFolder(): boolean;
    getLocalExtensionsFolderDisplayName(): string | null;
    listLocalExtensions(): LocalExtensionInfo[];
    refreshLocalExtensions(): LocalExtensionInfo[];
    listInstalledExtensions(): LocalExtensionInfo[];
    installExtension(request: ExtensionInstallRequest): LocalExtensionInfo;
    uninstallExtension(extensionId: string): LocalExtensionInfo;
    settingsTest(): string;
    getExtensionSettings(): ExtensionSettings[];
    emitToExtension(extensionId: string, eventName: string, payload?: unknown): void;
    settingsChanged(extensionId: string, setting: ExtensionSetting): void;
}

export interface SpotifyPlusApi {
    readonly scriptId: string;
    readonly version: number;

    log(...args: unknown[]): void;
    warn(...args: unknown[]): void;
    error(...args: unknown[]): void;

    /** Handles the Android back button for this extension's active scripted surface. */
    on(eventName: 'android.backPressed', handler: (event: AndroidBackButtonEvent) => void | Promise<void>): void;
    on(eventName: string, handler: EventHandler): void;
    off(eventName: 'android.backPressed', handler: (event: AndroidBackButtonEvent) => void | Promise<void>): void;
    off(eventName: string, handler: EventHandler): void;

    request<TPayload = unknown>(name: string, payload?: unknown): Promise<TPayload>;
    toast(text: string, length?: 'short' | 'long'): void;
    /** @deprecated Use Navigation.open(uri) instead. */
    openUri(uri: string): void;
    emit(eventName: string, payload?: unknown): void;

    /** Emits and receives events isolated to this extension. */
    readonly Events: ExtensionEventEmitterApi;

    /** Resolves files bundled inside this extension. */
    Assets: ExtensionAssetsApi;

    /** Navigates within Spotify or hands links to another app. */
    Navigation: NavigationApi;

    /** Interacts with the user's device */
    Platform: {
        /** Contains information about the user's device and the current Spotify version */
        PlatformData: PlatformData;
        /** Contains information about the user's current Spotify session */
        Session: Session;
        /** Interacts with your script's storage and preferences */
        Storage: StorageApi;
    }

    /** Make internal Spotify API requests */
    Internal: {
        /**
         * Gets information about a track
         * @param uri The URI of the song
         * @async
         */
        getTrack(uri: string): Promise<SpotifyTrack | null>;
    }

    /** Interacts with the Spotify player */
    Player: {
        /**
         * Gets the current track
         * 
         * Not all information is available when using this method.
         * 
         * Artists will always contain one element containing just the main artists
         * 
         * Explicit will always return false
         * 
         * Refer to the documentation at https://www.spotifyplus.dev/docs/script-basics/player for more information
         */
        getCurrentTrack(): SpotifyTrack;
        /** Gets the current playback position in milliseconds */
        getProgress(): number;
        /**
         * Skips to a given position in the song
         * @param position The position in the song to skip to in milliseconds
         */
        seek(position: number): void;
        /** Resumes playback of the current song */
        play(): void;
        /** Pauses playback of the current song */
        pause(): void;
        /** Toggles playback of the current song */
        togglePlay(): void;
        /** 
         * Skips to the next song in the queue 
         * 
         * This will only work after the user opens the now playing view once
         * */
        skipNext(): void;
        /**
         * Skips to the beginning of the track or the previous song in the queue
         * 
         * This will only work after the user opens the now playing view once
         */
        skipPrevious(): void;
    }

    /** Create custom UI using React */
    Surfaces: {
        /**
         * Register your React component inside of Spotify
         * @param surfaceType The surface that should trigger your React component to appear
         * @param renderer I honestly don't know what this is for
         */
        register(surfaceType: string, renderer: SurfaceRenderer<any>): void;
        /** Closes the scripted view currently shown by this extension. */
        close(): boolean;
    }

    Settings: {
        test(message: string): void;
        registerSetting(setting: ExtensionSettingSection): void;
        registerSettings(settings: ExtensionSettingSection[]): void;
    }

    ContextMenu: ContextMenuConstructor;
    SideDrawer: SideDrawerConstructor;
}

export class ScriptApiFactory {
    constructor(private readonly runtime: HostRuntime, private readonly logger: Logger) { }

    create(
        scriptId: string,
        generation: number,
        assetDirectory: string,
        manifestDirectory: string,
        declaredAssets: readonly string[],
        trust: ScriptTrust = 'user',
    ): ScriptGlobals {
        const scriptLogger = this.logger.child(scriptId);
        const runtime = this.runtime;

        const ScriptContextMenu = class extends ContextMenu {
            constructor(name: string, onClick: OnClickCallback, shouldAdd?: ShouldAddCallback, disabled?: boolean) {
                super(name, onClick, shouldAdd, disabled, (menu: ContextMenu) => {
                    const id = `${scriptId}:${menu.name}`;
                    runtime.registry.registerContextMenu(scriptId, id, menu);
                    runtime.registerContextMenu(id, scriptId, menu.name);
                    // runtime.sendCommand("menu.register", { id, scriptId, title: menu.name, disabled: menu.disabled });
                });
            }
        };

        const ScriptSideDrawer = class extends SideDrawerItem {
            constructor(name: string, onClick: SideOnClickCallback, icon?: ExtensionAsset) {
                super(name, onClick, icon, (drawer: SideDrawerItem) => {
                    const id = `${scriptId}:${drawer.name}`;
                    runtime.registry.registerSideDrawer(scriptId, id, drawer);
                    const iconRegistration = getNativeAssetRegistration(drawer.icon);
                    runtime.registerSideDrawer(
                        id,
                        scriptId,
                        drawer.name,
                        iconRegistration ? JSON.stringify(iconRegistration) : undefined,
                    );
                    // runtime.sendCommand("side.register", { id, scriptId, title: drawer.name });
                });
            }
        };

        const api: SpotifyPlusApi = {
            scriptId,
            version: 1,
            log: (...args) => this.runtime.logScript(scriptId, 'log', args),
            warn: (...args) => this.runtime.logScript(scriptId, 'warn', args),
            error: (...args) => this.runtime.logScript(scriptId, 'error', args),
            on: (eventName, handler) => this.runtime.registry.on(scriptId, eventName, handler as EventHandler),
            off: (eventName, handler) => this.runtime.registry.off(scriptId, eventName, handler as EventHandler),
            request: (name, payload = {}) => this.runtime.request(name, payload),
            toast: (text, length = 'short') => this.runtime.toast(text, length),
            openUri: uri => {
                this.runtime.navigate(uri, 'auto');
            },
            emit: (eventName, payload = {}) => this.runtime.sendEvent(eventName, payload),
            Events: this.runtime.registry.getExtensionEventEmitter(scriptId),
            Assets: createExtensionAssetsApi(
                scriptId,
                generation,
                assetDirectory,
                manifestDirectory,
                declaredAssets,
            ),
            Navigation: {
                open: (uri, options = {}) => this.runtime.navigate(uri, options.target ?? 'auto'),
                openSpotify: uri => this.runtime.navigate(uri, 'spotify'),
                openExternal: url => this.runtime.navigate(url, 'external'),
                back: () => this.runtime.navigateBack(),
            },
            Platform: {
                PlatformData: this.runtime.platformData,
                Session: this.runtime.session,
                Storage: {
                    set: (key, value) => this.runtime.storageSet(scriptId, key, value),
                    get: async <T = any>(key: string): Promise<T | null> => this.runtime.storageGet<T>(scriptId, key),
                    remove: key => this.runtime.storageRemove(scriptId, key),
                    write: <T = any>(path: string, value: T): void => {
                        if (isBinaryLike(value)) {
                            this.runtime.storageWriteBinary(scriptId, path, toBase64(value));
                            return;
                        }

                        if (typeof value === 'string') {
                            this.runtime.storageWriteText(scriptId, path, value);
                            return;
                        }

                        this.runtime.storageWriteJson(scriptId, path, value);
                    },
                    read: async <T = any>(path: string): Promise<T | string | Uint8Array | null> => {
                        const payload = await this.runtime.storageRead<T>(scriptId, path);
                        if (!payload) return null;

                        if (payload.type === 'binary') return payload.data ? fromBase64(payload.data) : null;
                        if (payload.type === 'json' || payload.type === 'text') return payload.value ?? null;
                        if (typeof payload.data === 'string') return fromBase64(payload.data);
                        return payload.value ?? null;
                    },
                    delete: path => this.runtime.storageDelete(scriptId, path),
                    Cache: {
                        write: <T = any>(path: string, value: T): void => {
                            if (isBinaryLike(value)) {
                                this.runtime.cacheWriteBinary(scriptId, path, toBase64(value));
                                return;
                            }

                            if (typeof value === 'string') {
                                this.runtime.cacheWriteText(scriptId, path, value);
                                return;
                            }

                            this.runtime.cacheWriteJson(scriptId, path, value);
                        },
                        read: async <T = any>(path: string): Promise<T | string | Uint8Array | null> => {
                            const payload = await this.runtime.cacheRead<T>(scriptId, path);
                            if (!payload) return null;

                            if (payload.type === 'binary') return payload.data ? fromBase64(payload.data) : null;
                            if (payload.type === 'json' || payload.type === 'text') return payload.value ?? null;
                            if (typeof payload.data === 'string') return fromBase64(payload.data);
                            return payload.value ?? null;
                        },
                        delete: path => this.runtime.cacheDelete(scriptId, path)
                    }
                }
            },
            Internal: {
                getTrack: async (uri: string) => this.runtime.getTrack(uri)
            },
            Player: {
                getCurrentTrack: () => this.runtime.getCurrentTrack(),
                getProgress: () => this.runtime.getProgress(),
                seek: position => this.runtime.seek(position),
                play: () => this.runtime.play(),
                pause: () => this.runtime.pause(),
                togglePlay: () => this.runtime.togglePlay(),
                skipNext: () => this.runtime.skipNext(),
                skipPrevious: () => this.runtime.skipPrevious()
            },
            Surfaces: {
                register: (surfaceType, renderer) => {
                    this.runtime.registry.registerSurfaceRenderer(scriptId, surfaceType, renderer)
                },
                close: () => this.runtime.closeSurface(scriptId),
            },
            Settings: {
                test: (message: string) => {
                    this.runtime.settingsTest(message);
                },
                registerSetting: (setting: ExtensionSettingSection) => {
                    this.runtime.registerSetting(scriptId, setting);
                },
                registerSettings: (settings: ExtensionSettingSection[]) => {
                    this.runtime.registerSettings(scriptId, settings);
                }
            },
            ContextMenu: ScriptContextMenu,
            SideDrawer: ScriptSideDrawer
        };

        const scriptConsole: ScriptConsole = {
            log: (...args) => api.log(...args),
            warn: (...args) => api.warn(...args),
            error: (...args) => api.error(...args)
        };

        const globals: ScriptGlobals = {
            SpotifyPlus: api,
            console: scriptConsole,
            setTimeout,
            setInterval,
            clearTimeout,
            clearInterval,
            global: undefined,
            globalThis: undefined
        };

        if (trust === 'elevated') {
            globals.Elevated = {
                getDeveloperMode: () => runtime.getDeveloperMode(),
                setDeveloperMode: enabled => runtime.setDeveloperMode(enabled),
                pickLocalExtensionsFolder: () => runtime.pickLocalExtensionsFolder(),
                getLocalExtensionsFolderDisplayName: () => runtime.getLocalExtensionsFolderDisplayName(),
                listLocalExtensions: () => runtime.listLocalExtensions(),
                refreshLocalExtensions: () => runtime.refreshLocalExtensions(),
                listInstalledExtensions: () => runtime.listInstalledExtensions(),
                installExtension: request => runtime.installExtension(request),
                uninstallExtension: extensionId => runtime.uninstallExtension(extensionId),
                settingsTest: () => runtime.readMessage(),
                getExtensionSettings: () => runtime.getExtensionSettings(),
                emitToExtension: (extensionId, eventName, payload) => {
                    runtime.emitToExtension(extensionId, eventName, payload);
                },
                settingsChanged: (extensionId, setting) => runtime.emitSettingChanged(extensionId, setting)
            };
        }

        globals.global = globals;
        globals.globalThis = globals;
        return globals;
    }
}

function isBinaryLike(value: unknown): value is Uint8Array | ArrayBuffer | ArrayBufferView {
    return value instanceof Uint8Array || value instanceof ArrayBuffer || ArrayBuffer.isView(value);
}

function toUint8Array(value: Uint8Array | ArrayBuffer | ArrayBufferView): Uint8Array {
    if (value instanceof Uint8Array) return value;
    if (value instanceof ArrayBuffer) return new Uint8Array(value);
    return new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
}

function toBase64(value: Uint8Array | ArrayBuffer | ArrayBufferView): string {
    const bytes = toUint8Array(value);
    return Buffer.from(bytes).toString('base64');
}

function fromBase64(value: string): Uint8Array {
    return Uint8Array.from(Buffer.from(value, 'base64'));
}

export declare const SpotifyPlus: SpotifyPlusApi;
