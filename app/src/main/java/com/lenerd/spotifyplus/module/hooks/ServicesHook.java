package com.lenerd.spotifyplus.module.hooks;

import com.lenerd.spotifyplus.module.SpotifyCallback;
import com.lenerd.spotifyplus.module.SpotifyHook;

/** Captures the authenticated Retrofit factory at startup on Spotify 9.1.82.2160. */
public final class ServicesHook extends SpotifyHook {
    public static volatile Object retrofit;
    @Override protected void hookSetup() throws ClassNotFoundException, NoSuchMethodException {
        hook(classLoader.loadClass("com.spotify.connectivity.httpretrofit.RetrofitMaker")
                .getDeclaredConstructor(classLoader.loadClass("p.oow0"), classLoader.loadClass("p.oow0"), classLoader.loadClass("p.row0")));
    }

    @Override protected void beforeHook(SpotifyCallback callback) { }
    @Override protected void afterHook(SpotifyCallback callback) {
        if (callback.getThrowable() == null) retrofit = callback.getThisObject();
    }
    @Override public Object handle(String command, Object[] args) { return null; }
}
