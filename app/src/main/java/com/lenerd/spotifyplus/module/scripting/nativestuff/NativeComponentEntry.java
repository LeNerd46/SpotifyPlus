package com.lenerd.spotifyplus.module.scripting.nativestuff;

import com.lenerd.spotifyplus.sdk.SpotifyPlusComponent;
import com.lenerd.spotifyplus.sdk.spotify.SpotifyPlusContext;

public class NativeComponentEntry {
    public final SpotifyPlusComponent<?> component;
    public final SpotifyPlusContext context;

    public NativeComponentEntry(SpotifyPlusComponent<?> component, SpotifyPlusContext context) {
        this.component = component;
        this.context = context;
    }
}
