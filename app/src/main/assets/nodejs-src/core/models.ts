import React from "react";
import { SpotifyPlusApi } from "../loader/script-api";

export interface SpotifyTrackData {
    title: string;
    trackNumber: number;
    durationMs: number;
    explicit: boolean;
    uri: string;
    artist: string;
    artists: string[];
    album: SpotifyAlbumData;
}

export interface SpotifyAlbumData {
    title: string;
    artist: string;
    release?: Date;
    image: string;
}

export class SpotifyAlbum {
    readonly title: string;
    readonly artist: string;
    readonly release?: Date;
    readonly image: string;

    constructor(title: string, artist: string, image: string, release?: Date) {
        this.title = title;
        this.artist = artist;
        this.release = release;
        this.image = image;
    }

    static from(data: SpotifyAlbumData): SpotifyAlbum {
        return new SpotifyAlbum(data.title, data.artist, data.image, data.release);
    }

    toJSON(): SpotifyAlbumData {
        return {
            title: this.title,
            artist: this.artist,
            release: this.release,
            image: this.image
        };
    }
}

export class SpotifyTrack {
    readonly title: string;
    readonly trackNumber: number;
    readonly durationMs: number;
    readonly explicit: boolean;
    readonly uri: string;
    readonly id: string;
    readonly artist: string;
    readonly artists: string[];
    readonly album: SpotifyAlbumData;

    constructor(data: SpotifyTrackData) {
        this.uri = data.uri;
        this.id = this.uri.split(':')[2];
        this.title = data.title;
        this.trackNumber = data.trackNumber;
        this.artist = data.artist;
        this.artists = data.artists;
        this.album = data.album;
        this.durationMs = data.durationMs;
        this.explicit = data.explicit;
    }

    static from(data: SpotifyTrackData): SpotifyTrack {
        return new SpotifyTrack(data);
    }

    get displayName(): string {
        return `${this.title} - ${this.artist}`;
    }

    toJSON(): SpotifyTrackData {
        return {
            title: this.title,
            trackNumber: this.trackNumber,
            durationMs: this.durationMs,
            explicit: this.explicit,
            uri: this.uri,
            artist: this.artist,
            album: this.album,
            artists: this.artists
        };
    }
}

export interface GetProgressData {
    position: number;
}

export interface MenuItemDefinition {
    id: string;
    title: string;
}

export interface MenuContext {
    type: string;
    track?: SpotifyTrackData;
    [key: string]: unknown;
}

export interface PlatformData {
    /** The current version of the Spotify app */
    clientVersion: string;
    /** The name of the operating system */
    osName: string;
    /** The current major version of Android */
    osVersion: string;
    /** The current Android SDK version or API level */
    sdkVersion: number;
}

export interface Session {
    /** The user's Spotify access token. This is used to authenticate requests to the Spotify API */
    accessToken: string;
}

export type ContextMenuRegister = (menu: ContextMenu) => void;
export type OnClickCallback = (uri: string) => void;
export type ContextMenuType = "track" | "artist" | "album" | "playlist";
export type ContextMenuTypes = ContextMenuType | readonly ContextMenuType[];
export type ShouldAddCallback = (uri: string, contextUri: string) => boolean;

export class ContextMenu {
    private readonly registerThing?: ContextMenuRegister;

    public name: string;
    readonly onClick: OnClickCallback;
    readonly shouldAdd?: ShouldAddCallback;
    readonly types?: readonly ContextMenuType[];
    // Icon
    public disabled: boolean;

    constructor(name: string, onClick: OnClickCallback, shouldAdd?: ShouldAddCallback, disabled?: boolean, registerThing?: ContextMenuRegister, types?: ContextMenuTypes) {
        this.name = name;
        this.onClick = onClick;
        this.shouldAdd = shouldAdd;
        const values = types === undefined ? undefined : typeof types === 'string' ? [types] : [...types];
        if (values?.some(type => !['track', 'artist', 'album', 'playlist'].includes(type))) {
            throw new TypeError('Invalid context menu type');
        }
        this.types = values === undefined ? undefined : Object.freeze([...new Set(values)]);
        this.disabled = disabled ?? false;
        this.registerThing = registerThing;
    }

    register(): this {
        if (!this.registerThing) {
            throw new Error('ContextMenu register thing has not been initialized');
        }

        this.registerThing(this);
        return this;
    }
}

export type SideDrawerRegister = (drawer: SideDrawerItem) => void;
export type SideOnClickCallback = () => React.ReactElement | void;

export interface SideDrawerIcon {
    readonly type: "extension-asset";
    readonly uri: string;
    readonly mimeType: string;
    readonly name: string;
}

export class SideDrawerItem {
    private readonly registerThing?: SideDrawerRegister;

    public name: string;
    readonly onClick: SideOnClickCallback;
    readonly icon?: SideDrawerIcon;

    constructor(name: string, onClick: SideOnClickCallback, icon?: SideDrawerIcon, registerThing?: SideDrawerRegister) {
        this.name = name;
        this.onClick = onClick;
        this.icon = icon;
        this.registerThing = registerThing;
    }

    register(): this {
        if (!this.registerThing) {
            throw new Error('SideDrawer register thing has not been initialized');
        }

        this.registerThing(this);
        return this;
    }
}

export interface UriData {
    type: string;
    id?: string;
}

export class Uri {
    public type: string;
    public id?: string;

    constructor(type: string, props?: UriData) {
        this.type = type;
        this.id = props?.id;
    }

    static from(data: UriData): Uri {
        return new Uri(data.type, data);
    }

    toString() {
        return this.id ? `spotify:${this.type}:${this.id}` : `spotify:${this.type}`;
    }
}

export type Surface = {
    id: string;
    type: string;
}

/** Normalized views of Spotify's internal metadata. The original response remains in raw. */
export interface MetadataImage { fileId: string; size: string; width: number; height: number; url: string; }
export interface MetadataArtistRef { uri: string; name: string; }
export interface MetadataDisc { number: number; tracks: string[]; }
export interface MetadataDate { year: number; month: number; day: number; }
export interface MetadataAlbum {
    uri: string; name: string; image: string; images: MetadataImage[]; artists: MetadataArtistRef[];
    label: string; type: string; popularity: number; date: MetadataDate; discs: MetadataDisc[];
    raw: Record<string, any>;
}
export interface MetadataArtist {
    uri: string; name: string; image: string; images: MetadataImage[]; popularity: number;
    topTracks: Array<{ country: string; tracks: string[] }>;
    albums: string[]; singles: string[]; compilations: string[]; appearsOn: string[];
    raw: Record<string, any>;
}
export interface MetadataPlaylistItem { uri: string; addedBy: string; timestamp: string; itemId: string; }
export interface MetadataPlaylist {
    uri: string; revision: string; name: string; picture: string; description: string;
    ownerUsername: string; length: number; position: number; truncated: boolean;
    timestamp: string; createdAt: string; isUserCreated: boolean; canEditItems: boolean; canEditMetadata: boolean;
    items: MetadataPlaylistItem[]; raw: Record<string, any>;
}

const ALPHABET = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
const list = (value: unknown): any[] => Array.isArray(value) ? value : [];
const text = (value: unknown): string => typeof value === 'string' ? value : '';

/** Metadata uses 128-bit hexadecimal GIDs; extension consumers use Spotify base62 URIs. */
export function gidToUri(kind: 'track' | 'album' | 'artist', gid: unknown): string {
    if (typeof gid !== 'string' || !/^[0-9a-fA-F]{32}$/.test(gid)) return '';
    let value = BigInt(`0x${gid}`);
    let id = '';
    do { id = ALPHABET[Number(value % 62n)] + id; value /= 62n; } while (value > 0n);
    return `spotify:${kind}:${id.padStart(22, '0')}`;
}

function images(group: any): MetadataImage[] {
    return list(group?.image).filter(image => typeof image?.file_id === 'string' && /^[a-fA-F0-9]{40}$/.test(image.file_id)).map(image => ({
        fileId: image.file_id, size: text(image.size), width: Number(image.width) || 0,
        height: Number(image.height) || 0, url: `https://i.scdn.co/image/${image.file_id}`
    }));
}
function preferredImage(entries: MetadataImage[]): string { return (entries.find(image => image.size === 'LARGE') ?? entries[0])?.url ?? ''; }
function albumGroup(group: unknown): string[] { return list(group).flatMap(item => list(item?.album).map(album => gidToUri('album', album?.gid)).filter(Boolean)); }

export function parseMetadataAlbum(raw: Record<string, any>, requestedUri: string): MetadataAlbum {
    const artwork = images(raw.cover_group);
    return {
        uri: text(raw.canonical_uri) || requestedUri, name: text(raw.name), image: preferredImage(artwork), images: artwork,
        artists: list(raw.artist).map(artist => ({ uri: gidToUri('artist', artist?.gid), name: text(artist?.name) })),
        label: text(raw.label), type: text(raw.type), popularity: Number(raw.popularity) || 0,
        date: { year: Number(raw.date?.year) || 0, month: Number(raw.date?.month) || 0, day: Number(raw.date?.day) || 0 },
        discs: list(raw.disc).map(disc => ({ number: Number(disc?.number) || 0, tracks: list(disc?.track).map(track => gidToUri('track', track?.gid)).filter(Boolean) })), raw
    };
}

export function parseMetadataArtist(raw: Record<string, any>, requestedUri: string): MetadataArtist {
    const artwork = images(raw.portrait_group);
    return {
        uri: requestedUri, name: text(raw.name), image: preferredImage(artwork), images: artwork,
        popularity: Number(raw.popularity) || 0,
        topTracks: list(raw.top_track).map(group => ({ country: text(group?.country), tracks: list(group?.track).map(track => gidToUri('track', track?.gid)).filter(Boolean) })),
        albums: albumGroup(raw.album_group), singles: albumGroup(raw.single_group),
        compilations: albumGroup(raw.compilation_group), appearsOn: albumGroup(raw.appears_on_group), raw
    };
}

export function parseMetadataPlaylist(raw: Record<string, any>, requestedUri: string): MetadataPlaylist {
    const attributes = raw.attributes ?? {};
    const contents = raw.contents ?? {};
    const capabilities = raw.capabilities ?? {};
    return {
        uri: requestedUri, revision: text(raw.revision), name: text(attributes.name),
        // This is an opaque encoded picture value, NOT an image URL.
        picture: text(attributes.picture), description: text(attributes.description), ownerUsername: text(raw.ownerUsername),
        length: Number(raw.length) || 0, position: Number(contents.pos) || 0, truncated: Boolean(contents.truncated),
        timestamp: text(raw.timestamp), createdAt: text(raw.createdAt), isUserCreated: Boolean(raw.isUserCreated),
        canEditItems: Boolean(capabilities.canEditItems), canEditMetadata: Boolean(capabilities.canEditMetadata),
        items: list(contents.items).map(item => ({ uri: text(item?.uri), addedBy: text(item?.attributes?.addedBy), timestamp: text(item?.attributes?.timestamp), itemId: text(item?.attributes?.itemId) })), raw
    };
}
