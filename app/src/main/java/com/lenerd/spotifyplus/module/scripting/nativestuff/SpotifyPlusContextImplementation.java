package com.lenerd.spotifyplus.module.scripting.nativestuff;

import com.lenerd.spotifyplus.module.scripting.SpotifyNativeBridge;
import com.lenerd.spotifyplus.sdk.spotify.SpotifyPlusContext;
import com.lenerd.spotifyplus.sdk.spotify.SpotifyPlusPlayer;

public class SpotifyPlusContextImplementation implements SpotifyPlusContext {
    private final SpotifyNativeBridge bridge;

    public SpotifyPlusContextImplementation(SpotifyNativeBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public SpotifyPlusPlayer getPlayer() {
        return new SpotifyPlusPlayerImplementation(bridge);
    }
}
