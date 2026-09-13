package com.lenerd46.spotifyplus.hooks;

import com.lenerd46.spotifyplus.References;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class SpotifyUserHook {
    private static boolean initialized;
    private static boolean canonicalUsernameCaptured;

    private SpotifyUserHook() {
    }

    public static synchronized void init(ClassLoader classLoader) {
        if (initialized) return;
        initialized = true;
        References.spotifyClassLoader = classLoader;

        try {
            Class<?> storedCredentials = XposedHelpers.findClass("com.spotify.connectivity.auth.CredentialsStorage$StoredCredentialsAndUsername", classLoader);
            XposedBridge.hookAllConstructors(storedCredentials, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length > 1 && param.args[1] instanceof String username) {
                        remember(username, true);
                    }
                }
            });
        } catch (Throwable error) {
            XposedBridge.log("[SpotifyPlus] Could not hook stored Spotify credentials: " + error);
        }

        try {
            Class<?> credentials = XposedHelpers.findClass(
                    "com.spotify.authentication.credentials.SerializableCredentials", classLoader);
            XposedBridge.hookAllConstructors(credentials, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length > 0 && param.args[0] instanceof String username) {
                        remember(username, false);
                    }
                }
            });
        } catch (Throwable error) {
            XposedBridge.log("[SpotifyPlus] Could not hook Spotify credentials: " + error);
        }
    }

    private static synchronized void remember(String username, boolean canonical) {
        if (username == null || username.isBlank()) return;
        if (!canonical && canonicalUsernameCaptured) return;
        if (canonical) canonicalUsernameCaptured = true;
        if (!username.equals(References.spotifyUsername)) {
            References.spotifyUsername = username;
            com.lenerd46.spotifyplus.Utils.clearSpotifyUserCache();
        }
    }
}
