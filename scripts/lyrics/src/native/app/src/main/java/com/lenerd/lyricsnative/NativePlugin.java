package com.lenerd.lyricsnative;

import com.lenerd.spotifyplus.sdk.SpotifyPlusPlugin;
import com.lenerd.spotifyplus.sdk.SpotifyPlusRegistry;
import com.lenerd.spotifyplus.sdk.spotify.SpotifyPlusContext;

public class NativePlugin implements SpotifyPlusPlugin {
    @Override
    public void register(SpotifyPlusRegistry registry, SpotifyPlusContext context) {
        registry.registerComponent(new LyricsView());
        registry.registerComponent(new SyllableLine());
        registry.registerComponent(new GradientText());
        registry.registerComponent(new AnimatedBackground());
    }
}
