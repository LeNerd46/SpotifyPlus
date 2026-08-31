import type { SpotifyPlusApi } from '../loader/script-api';

declare global {
    var __spotifyplus_api__: SpotifyPlusApi | undefined;
}

export function getSpotifyPlusApi(): SpotifyPlusApi {
    const api = globalThis.__spotifyplus_api__;
    if (!api) throw new Error('SpotifyPlus API is not available in this environment!');

    return api;
}