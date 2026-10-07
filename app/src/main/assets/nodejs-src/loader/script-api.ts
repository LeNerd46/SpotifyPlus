import { parseMetadataAlbum, parseMetadataArtist, parseMetadataPlaylist } from "../core/models";
import type { MetadataAlbum, MetadataArtist, MetadataPlaylist } from "../core/models";
export type { MetadataAlbum, MetadataArtist, MetadataPlaylist, MetadataArtistRef, MetadataDisc, MetadataDate, MetadataImage, MetadataPlaylistItem } from "../core/models";
import { EventHandler, SurfaceRenderer } from "./script-registry";
import { ContextMenu, ContextMenuTypes, OnClickCallback, PlatformData, Session, ShouldAddCallback, SideDrawerItem, SideOnClickCallback, SpotifyTrack } from "../core/models";
import { Logger } from "../core/logger";
import { HostRuntime } from "./host-runtime";
import React from "react";
import type { UIApi } from "../ui/target-api";
export type { UIApi, UITarget, UITargetName, UISelector, UIComponentProps, UITargetContext, UITargetInfo, UIRegistration, UIPart, UIInstance } from "../ui/target-api";
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
    /**
     * Adds a new button to the context menu
     * @param name The label of the button
     * @param onClick The callback to be executed when this button is pressed
     * @param shouldAdd A callback that gets ran before any buttons are added. This can be used to do logic to determine whether to add the button or not
     * @param disabled Whether the button should be disabled
     * @param types The types of items this button should appear for
     */
    new(name: string, onClick: OnClickCallback, shouldAdd?: ShouldAddCallback, disabled?: boolean, types?: ContextMenuTypes): ContextMenu;

    /**
     * 
     * @param item The item this context menu is being opened for
     * @param options The URI of a Spotify item to give this context menu more context. For example, opening the context menu for a song inside of a playlist would give extra buttons related to that playlist (i.e. remove from playlist). You can pass in the playlist URI to give it that context
     */
    open(item: SpotifyUriInput, options?: ContextMenuOpenOptions): Promise<void>;
    /** Opens the context menu for the now playing screen. The now playing screen might have to be open for this to work, I'm honestly not entirely sure */
    openNowPlaying(): Promise<void>;
}

export interface ContextMenuOpenOptions {
    /** The URI of a Spotify item to give this context menu more context. For example, opening the context menu for a song inside of a playlist would give extra buttons related to that playlist (i.e. remove from playlist). You can pass in the playlist URI to give it that context */
    contextUri?: SpotifyUriInput;
}

export interface SideDrawerConstructor {
    /**
     * Creates a new side drawer button
     * @param name The label of the button
     * @param onClick The callback to be executed when this button is pressed
     * @param icon The icon for the button
     */
    new(name: string, onClick: SideOnClickCallback, icon?: ExtensionAsset): SideDrawerItem;
    /** Opens the side drawer */
    open(): Promise<void>;
}

export type NavigationTarget = 'auto' | 'spotify' | 'external';

export type SpotifyUriInput = string | { uri: string };
export interface SearchResult { uri: string; text: string; }
export interface SearchResponse { query: string; items: SearchResult[]; }
export interface SearchOptions { limit?: number; locale?: string; }
export type RepeatMode = 'off' | 'repeat' | 'repeat-one';
export interface PlaybackState {
    contextUri: string;
    trackUri: string | null;
    isPlaying: boolean;
    isPaused: boolean;
    isBuffering: boolean;
    positionMs: number;
    shuffle: boolean;
    repeat: RepeatMode;
}
export interface ConnectDevice {
    /** The ID of this device */
    id: string;
    /** The display name of this device */
    name: string;
    /** The type of this device */
    type: string;
    /** Whether this device is the currently active device */
    isActive: boolean;
    isLocal: boolean;
    isDisabled: boolean;
    /** Whether you are allowed to change the volume of this device */
    supportsVolume: boolean;
    /** Spotify's raw volume value (0–65535) */
    volume: number;
}
export interface SpotifyEvents {
    contextChanged: { uri: string; previousUri: string };
    songChanged: { uri: string | null; previousUri: string | null };
    playPause: { isPlaying: boolean; isPaused: boolean };
    /** An acknowledged local seek, or a remote position discontinuity exceeding 1.5 seconds. */
    trackSeeked: { positionMs: number; previousPositionMs: number };
    shuffleChanged: { enabled: boolean };
    repeatChanged: { mode: RepeatMode };
    deviceChanged: { device: ConnectDevice | null; previousDeviceId: string | null };
}
export interface LibraryItem { uri: string; name: string; imageUri: string; pinned: boolean; }
export interface SpotifyUser { username: string; displayName: string; uri: string; images: Array<{ url: string; width: number; height: number }>; }
export interface Page<T> { items: T[]; offset: number; limit: number; total: number; }
export interface PageOptions { offset?: number; limit?: number; }
export interface PlaylistItem { uri: string; rowId: string; name: string; addedAt: number; }
export interface PlaylistPage extends Page<PlaylistItem> { uri: string; name: string; description: string; ownedBySelf: boolean; imageUri?: string; }
export interface PlaylistsApi {
    /**
     * Gets details about a playlist
     * @param playlist The URI of the playlist
     * @param options Pagination options
     */
    get(playlist: SpotifyUriInput, options?: PageOptions): Promise<PlaylistPage>;
    /**
     * Creates a new playlist
     * @param name The name of the playlist
     * @returns The URI and name of the created playlist
     */
    create(name: string): { uri: string; name: string };
    /**
     * Deletes a user's playlist. Must have permission to delete the playlist, obviously
     * @param playlist The URI of the playlist to delete
     */
    delete(playlist: SpotifyUriInput): void;
    /**
     * Moves a playlist in the user's library. If no before playlist is given, it will move make the playlist the first playlist in the user's library
     * @param playlist The playlist to move
     * @param before The given playlist will move ahead of this playlist
     */
    move(playlist: SpotifyUriInput, before?: SpotifyUriInput): void;
    /**
     * Adds songs to a playlist
     * @param playlist The playlist to add songs to
     * @param tracks A list of songs to add
     */
    addTracks(playlist: SpotifyUriInput, tracks: SpotifyUriInput[]): void;
    /**
     * Removes entries from a playlist. 
     * @param playlist The playlist to remove songs from
     * @param rowIds A list of row IDs to remove. These are **NOT** song IDs. This are IDs of rows inside of the playlist
     */
    removeTracks(playlist: SpotifyUriInput, rowIds: string[]): void;
    /**
     * Moves songs within a playlist. If no beforeRowId is given, it will move songs to the beginning of the playlist
     * @param playlist The playlist to move songs in
     * @param rowIds A list of row IDs to move. These are **NOT** song IDs. These are IDs of rows inside of the playlist
     * @param beforeRowId The row ID of the row you want to move the songs in front of
     */
    moveTracks(playlist: SpotifyUriInput, rowIds: string[], beforeRowId?: string): void;
}
export interface LibraryApi {
    /**
     * Lists items saved in the user's library
     * @param options Pagination options and what type of library items to return
     */
    list(options?: PageOptions & { type?: 'all' | 'playlist' | 'album' | 'artist' }): Promise<Page<LibraryItem>>;
    /**
     * Saves items to the user's library
     * @param items A list of URIs of items to save
     */
    save(items: SpotifyUriInput | SpotifyUriInput[]): void;
    /**
     * Removes items from the user's library
     * @param items A list of URIs of items to remove
     */
    remove(items: SpotifyUriInput | SpotifyUriInput[]): void;
    /**
     * Checks if items are saved in the user's library
     * @param items A list of URIs of the items to check
     * @returns A list of booleans indicating whether each item is saved
     */
    contains(items: SpotifyUriInput[]): boolean[];
    /**
     * Adds a song to the user's liked songs
     * @param track The URI of the song to like
     */
    like(track: SpotifyUriInput): void;
    /**
     * Removes a song from the user's liked songs
     * @param track The URI of the song to unlike
     */
    unlike(track: SpotifyUriInput): void;
    /**
     * Checks if a song is liked by the user
     * @param track The URI of the song to check
     * @returns A boolean indicating whether the song is liked
     */
    isLiked(track: SpotifyUriInput): boolean;
}
export interface QueueEntry { uri: string; uid: string; metadata: Record<string, string>; }
export interface QueueSnapshot { revision: string; current: QueueEntry | null; next: QueueEntry[]; previous: QueueEntry[]; }
export interface QueueApi {
    /** Gets a snapshot of the current queue */
    get(): QueueSnapshot;
    /**
     * Adds songs to the queue
     * @param items A list of URIs of songs to add to the queue
     */
    add(items: SpotifyUriInput | SpotifyUriInput[]): void;
    /**
     * Removes songs from the queue
     * @param snapshot The queue snapshot to work in
     * @param index The index to remove
     */
    remove(snapshot: QueueSnapshot, index: number): void;
    /**
     * Reorders songs in the queue
     * @param snapshot The queue snapshot to work in
     * @param index Index of the song you want to move
     * @param toIndex Index of where you want to move this song to
     */
    move(snapshot: QueueSnapshot, index: number, toIndex: number): void;
    /**
     * Clears the queue
     * @param snapshot The queue snapshot to work in
     */
    clear(snapshot: QueueSnapshot): void;
}

function nonnegativeIndex(value: number): number {
    if (!Number.isSafeInteger(value) || value < 0) throw new TypeError('Expected a non-negative integer index');
    return value;
}

function spotifyUri(input: SpotifyUriInput): string {
    let uri = typeof input === 'string' ? input : input?.uri;
    if (typeof uri !== 'string') throw new TypeError('Expected a Spotify URI, URL, or object with uri');
    if (uri.startsWith('https://open.spotify.com/')) {
        const parts = new URL(uri).pathname.split('/').filter(Boolean);
        if (parts[0]?.startsWith('intl-')) parts.shift();
        if (parts.length !== 2) throw new TypeError('Invalid Spotify URL');
        uri = `spotify:${parts[0]}:${parts[1]}`;
    }
    if (!/^spotify:(track|episode|album|artist|playlist):[A-Za-z0-9]+$/.test(uri)) throw new TypeError('Unsupported Spotify URI');
    return uri;
}

/** Validate the kind before dispatching a request to Spotify's internal endpoints. */
function metadataUri(input: SpotifyUriInput, kind: 'album' | 'artist' | 'playlist'): string {
    const uri = spotifyUri(input);
    if (!new RegExp(`^spotify:${kind}:[A-Za-z0-9]{22}$`).test(uri)) throw new TypeError(`Expected a Spotify ${kind} URI`);
    return uri;
}

export interface NavigationOptions {
    target?: NavigationTarget;
}

export interface NavigationApi {
    /** Opens a URI. Can be any URI */
    open(uri: string, options?: NavigationOptions): boolean;
    /** Specifically opens a Spotify URI */
    openSpotify(uri: string): boolean;
    /** Opens a URL inside of a web browser */
    openExternal(url: string): boolean;
    /** Pretty much presses the back button */
    back(): boolean;
}

export interface AndroidBackButtonEvent {
    surfaceId: string;
    readonly defaultPrevented: boolean;
    /** Prevents the default handling of the back button, that way your extension can handle it */
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
    read<T = any>(path: string): T | string | Uint8Array | null;
    delete(path: string): void;
}

export interface StorageApi extends FileStorageOperations {
    set(key: string, value: any): void;
    get<T = any>(key: string): T | null;
    remove(key: string): void;
    /** Temporary, extension-scoped storage backed by Android's cache directory. */
    Cache: FileStorageOperations;
}

/** Spotify state events are broadcast to every extension. Custom emits stay local. */
export interface ExtensionEventEmitterApi {
    on<K extends keyof SpotifyEvents>(eventName: K, handler: ExtensionEventHandler<SpotifyEvents[K]>): void;
    on<TPayload = unknown>(eventName: string, handler: ExtensionEventHandler<TPayload>): void;
    once<K extends keyof SpotifyEvents>(eventName: K, handler: ExtensionEventHandler<SpotifyEvents[K]>): void;
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
    getUIExtensions(): Array<{ id: string; name: string }>;
    setUIExtensionOrder(order: string[]): void;
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
    /** Interact with the Spotify UI */
    UI: UIApi;

    /** Functions to search Spotify for stuff */
    Search: {
        /**
         * Searches Spotify for matching items
         * @param query The words to search for
         * @param options Optional limit and locale for the search
         */
        search(query: string, options?: SearchOptions): Promise<SearchResponse>;
    };

    /** Interact with Spotify Connect */
    Connect: {
        /** Lists available Spotify Connect devices */
        getDevices(): ConnectDevice[];
        /** Gets the active Spotify Connect device */
        getCurrentDevice(): ConnectDevice | null;
        /**
         * Transfers playback to a different device
         * @param device The device to connect to
         */
        transfer(device: string | ConnectDevice): Promise<void>;
    };

    /** Interact and manage playlists */
    Playlists: PlaylistsApi;

    /** Interact with the current user */
    User: {
        /** Get information about the current user */
        getCurrent(): Promise<SpotifyUser>;
    };

    /** Interact with the user's library */
    Library: LibraryApi;

    /** Interact with the playback queue */
    Queue: QueueApi;
    readonly scriptId: string;
    readonly version: number;

    log(...args: unknown[]): void;
    warn(...args: unknown[]): void;
    error(...args: unknown[]): void;

    /** Handles the Android back button for this extension's active scripted surface */
    on(eventName: 'android.backPressed', handler: (event: AndroidBackButtonEvent) => void | Promise<void>): void;
    on(eventName: string, handler: EventHandler): void;
    off(eventName: 'android.backPressed', handler: (event: AndroidBackButtonEvent) => void | Promise<void>): void;
    off(eventName: string, handler: EventHandler): void;

    request<TPayload = unknown>(name: string, payload?: unknown): Promise<TPayload>;
    toast(text: string, length?: 'short' | 'long'): void;
    /** @deprecated Use Navigation.open(uri) instead */
    openUri(uri: string): void;
    emit(eventName: string, payload?: unknown): void;

    /** Receive different events from Spotify */
    readonly Events: ExtensionEventEmitterApi;

    /** Helpers to load assets into your extension */
    Assets: ExtensionAssetsApi;

    /** Navigates within Spotify or hands links to another app */
    Navigation: NavigationApi;

    /** Interacts with the user's device */
    Platform: {
        /** Interact with the device's clipboard */
        Clipboard: {
            /** Get the current copied text */
            readText(): string | null;
            /** Copy text to the clipboard */
            writeText(text: string): void;
            /** Clear the clipboard */
            clear(): void;
        };
        /** Contains information about the user's device and the current Spotify version */
        PlatformData: PlatformData;
        /** Contains information about the user's current Spotify session */
        Session: Session;
        /** Interacts with your extension's storage and preferences */
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

        /**
         * Gets information about an album
         * @param uri The URI of the album
         * @async
         */
        getAlbum(uri: SpotifyUriInput): Promise<MetadataAlbum | null>;

        /**
         * Gets information about an artist
         * @param uri The URI of the artist
         * @async
         */
        getArtist(uri: SpotifyUriInput): Promise<MetadataArtist | null>;

        /**
         * Gets information about a playlist
         * @param uri The URI of the playlist
         * @async
         */
        getPlaylist(uri: SpotifyUriInput): Promise<MetadataPlaylist | null>;
    }

    /** Interacts with the Spotify player */
    Player: {
        /**
         * Plays a song in a given context. For example, start playing a playlist
         * @param context The album, playlist, or artist to play from
         * @param index The index to start at
         */
        playContext(context: SpotifyUriInput, index?: number): Promise<void>;
        /** Gets the current playback state */
        getState(): PlaybackState;
        /** Sets the player's shuffle state */
        setShuffle(enabled: boolean): void;
        /** Toggles the player's shuffle state */
        toggleShuffle(): void;
        /** Cycle's the player's repeat state */
        cycleRepeat(): void;
        /** Sets the player's repeat state */
        setRepeat(mode: RepeatMode): void;
        /**
         * Gets the current track
         * 
         * Not all information is available when using this method.
         * 
         * Artists will always contain one element containing just the main artists
         * 
         * Explicit will always return false
         * 
         * Refer to the documentation at https://lenerd46.github.io/spotifyplus-docs/docs/spotifyplus-api/player/get-current-track for more information
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
        /** Skips to the next song in the queue */
        skipNext(): void;
        /** Skips to the beginning of the track or the previous song in the */
        skipPrevious(): void;
    }

    /** Create custom UI using React
     * @deprecated
     */
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

    /** Register settings for your extension */
    Settings: {
        /** This was just a test, as you can probably tell */
        test(message: string): void;
        /**
         * Registers a single section for your extension's settings
         * @param setting The section to register
         */
        registerSetting(setting: ExtensionSettingSection): void;
        /**
         * Registers your extension's settings
         * @param settings The setting sections to register
         */
        registerSettings(settings: ExtensionSettingSection[]): void;
    }

    /** Interact with Spotify's context menus */
    ContextMenu: ContextMenuConstructor;

    /** Interact with the side drawer */
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
            static open(item: SpotifyUriInput, options: ContextMenuOpenOptions = {}): Promise<void> {
                const uri = spotifyUri(item);
                if (!/^spotify:(track|album|artist|playlist):[^:]+$/.test(uri)) {
                    throw new TypeError('Context menu requires a track, album, artist or playlist');
                }
                return runtime.requestApi<void>('menu.open', {
                    uri,
                    ...(options.contextUri === undefined ? {} : { contextUri: spotifyUri(options.contextUri) }),
                });
            }
            static openNowPlaying(): Promise<void> { return runtime.requestApi<void>('menu.openNowPlaying'); }
            constructor(name: string, onClick: OnClickCallback, shouldAdd?: ShouldAddCallback, disabled?: boolean, types?: ContextMenuTypes) {
                super(name, onClick, shouldAdd, disabled, (menu: ContextMenu) => {
                    const id = `${scriptId}:${menu.name}`;
                    runtime.registry.registerContextMenu(scriptId, id, menu);
                    runtime.registerContextMenu(id, scriptId, menu);
                }, types);
            }
        };

        const ScriptSideDrawer = class extends SideDrawerItem {
            static open(): Promise<void> { return runtime.requestApi<void>('side.open'); }
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
            Search: { search: (query, options = {}) => runtime.requestApi<SearchResponse>('search', { ...options, query }) },
            Connect: {
                getDevices: () => runtime.callApiSync<ConnectDevice[]>('connect.devices'),
                getCurrentDevice: () => runtime.callApiSync<ConnectDevice | null>('connect.current'),
                transfer: device => runtime.requestApi<void>('connect.transfer', { deviceId: typeof device === 'string' ? device : device.id }),
            },
            Playlists: {
                get: (playlist, options = {}) => runtime.requestApi<PlaylistPage>('playlists.get', { ...options, uri: spotifyUri(playlist) }),
                create: name => runtime.callApiSync<{ uri: string; name: string }>('playlists.create', { name }),
                delete: playlist => void runtime.callApiSync<void>('playlists.delete', { uri: spotifyUri(playlist) }),
                move: (playlist, before) => void runtime.callApiSync<void>('playlists.move', { uri: spotifyUri(playlist), before: before === undefined ? 'start' : spotifyUri(before) }),
                addTracks: (playlist, tracks) => void runtime.callApiSync<void>('playlists.addTracks', { uri: spotifyUri(playlist), uris: tracks.map(spotifyUri) }),
                removeTracks: (playlist, rowIds) => void runtime.callApiSync<void>('playlists.removeTracks', { uri: spotifyUri(playlist), rowIds }),
                moveTracks: (playlist, rowIds, before) => void runtime.callApiSync<void>('playlists.moveTracks', { uri: spotifyUri(playlist), rowIds, before }),
            },
            User: { getCurrent: () => runtime.requestApi<SpotifyUser>('user.get') },
            Library: {
                list: (options = {}) => runtime.requestApi<Page<LibraryItem>>('library.list', options),
                save: items => void runtime.callApiSync<void>('library.save', { uris: (Array.isArray(items) ? items : [items]).map(spotifyUri) }),
                remove: items => void runtime.callApiSync<void>('library.remove', { uris: (Array.isArray(items) ? items : [items]).map(spotifyUri) }),
                contains: items => runtime.callApiSync<boolean[]>('library.contains', { uris: items.map(spotifyUri) }),
                like: track => void runtime.callApiSync<void>('library.save', { uris: [spotifyUri(track)] }),
                unlike: track => void runtime.callApiSync<void>('library.remove', { uris: [spotifyUri(track)] }),
                isLiked: track => runtime.callApiSync<boolean[]>('library.contains', { uris: [spotifyUri(track)] })[0] ?? false,
            },
            Queue: {
                get: () => runtime.callApiSync<QueueSnapshot>('queue.get'),
                add: items => void runtime.callApiSync<void>('queue.add', { uris: (Array.isArray(items) ? items : [items]).map(spotifyUri) }),
                remove: (snapshot, index) => void runtime.callApiSync<void>('queue.remove', { revision: snapshot.revision, index: nonnegativeIndex(index) }),
                move: (snapshot, index, toIndex) => void runtime.callApiSync<void>('queue.move', { revision: snapshot.revision, index: nonnegativeIndex(index), toIndex: nonnegativeIndex(toIndex) }),
                clear: snapshot => void runtime.callApiSync<void>('queue.clear', { revision: snapshot.revision }),
            },
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
                Clipboard: {
                    readText: () => runtime.callApiSync<string | null>('clipboard.read'),
                    writeText: text => void runtime.callApiSync<void>('clipboard.write', { text }),
                    clear: () => void runtime.callApiSync<void>('clipboard.clear'),
                },
                PlatformData: this.runtime.platformData,
                Session: this.runtime.session,
                Storage: {
                    set: (key, value) => this.runtime.storageSet(scriptId, key, value),
                    get: <T = any>(key: string): T | null => this.runtime.storageGet<T>(scriptId, key),
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
                    read: <T = any>(path: string): T | string | Uint8Array | null => {
                        const payload = this.runtime.storageRead<T>(scriptId, path);
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
                        read: <T = any>(path: string): T | string | Uint8Array | null => {
                            const payload = this.runtime.cacheRead<T>(scriptId, path);
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
                getTrack: async (uri: string) => this.runtime.getTrack(spotifyUri(uri)),
                getAlbum: async album => {
                    const uri = metadataUri(album, 'album');
                    const raw = await runtime.requestApi<Record<string, any> | null>('album.get', { uri });
                    return raw ? parseMetadataAlbum(raw, uri) : null;
                },
                getArtist: async artist => {
                    const uri = metadataUri(artist, 'artist');
                    const raw = await runtime.requestApi<Record<string, any> | null>('artist.get', { uri });
                    return raw ? parseMetadataArtist(raw, uri) : null;
                },
                getPlaylist: async playlist => {
                    const uri = metadataUri(playlist, 'playlist');
                    const raw = await runtime.requestApi<Record<string, any> | null>('playlists.getMetadata', { uri });
                    return raw ? parseMetadataPlaylist(raw, uri) : null;
                }
            },
            Player: {
                playContext: (context, index = 0) => {
                    const uri = spotifyUri(context);
                    if (!/^spotify:(album|playlist|artist|track):[A-Za-z0-9]+$/.test(uri)) throw new TypeError('Expected an album, playlist, artist or track');
                    return runtime.requestApi<void>('player.playContext', { uri, index: nonnegativeIndex(index) });
                },
                getState: () => runtime.callApiSync<PlaybackState>('player.state'),
                setShuffle: enabled => void runtime.callApiSync<void>('player.shuffle', { enabled }),
                toggleShuffle: () => void runtime.callApiSync<void>('player.toggleShuffle'),
                cycleRepeat: () => void runtime.callApiSync<void>('player.cycleRepeat'),
                setRepeat: mode => void runtime.callApiSync<void>('player.repeat', { mode }),
                getCurrentTrack: () => this.runtime.getCurrentTrack(),
                getProgress: () => this.runtime.getProgress(),
                seek: position => void runtime.callApiSync<void>('player.seek', { positionMs: nonnegativeIndex(position) }),
                play: () => void runtime.callApiSync<void>('player.play'),
                pause: () => void runtime.callApiSync<void>('player.pause'),
                togglePlay: () => void runtime.callApiSync<void>('player.togglePlay'),
                skipNext: () => void runtime.callApiSync<void>('player.skipNext'),
                skipPrevious: () => void runtime.callApiSync<void>('player.skipPrevious')
            },
            UI: runtime.ui.forExtension(scriptId, generation),
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
                getUIExtensions: () => runtime.getUIExtensions(),
                setUIExtensionOrder: order => runtime.setUIExtensionOrder(order),
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
