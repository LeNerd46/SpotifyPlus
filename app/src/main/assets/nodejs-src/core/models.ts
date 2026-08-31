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
export type ShouldAddCallback = (uri: string, contextUri: string) => boolean;

export class ContextMenu {
    private readonly registerThing?: ContextMenuRegister;

    public name: string;
    readonly onClick: OnClickCallback;
    readonly shouldAdd?: ShouldAddCallback;
    // Icon
    public disabled: boolean;

    constructor(name: string, onClick: OnClickCallback, shouldAdd?: ShouldAddCallback, disabled?: boolean, registerThing?: ContextMenuRegister) {
        this.name = name;
        this.onClick = onClick;
        this.shouldAdd = shouldAdd;
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
