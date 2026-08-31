package com.lenerd.spotifyplus.sdk;

import com.lenerd.spotifyplus.sdk.spotify.SpotifyPlusContext;

public interface SpotifyPlusPlugin {
    void register(SpotifyPlusRegistry registry, SpotifyPlusContext context);
}
