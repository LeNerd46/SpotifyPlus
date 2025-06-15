package com.lenerd46.spotifyplus;

import android.app.Activity;
import android.util.Log;
import com.lenerd46.spotifyplus.beautifullyrics.entities.PlayerStateUpdatedListener;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class References {
    public static WeakReference<Activity> currentActivity = new WeakReference<>(null);
    public static WeakReference<Object> playerState = new WeakReference<>(null);
    public static WeakReference<Object> playerStateWrapper = new WeakReference<>(null);
    public static WeakReference<String> accessToken = new WeakReference<>(null);

    private static final Pattern DIGITS = Pattern.compile("\\d+");
    public static SpotifyTrack getTrackTitle(XC_LoadPackage.LoadPackageParam lpparam) {
        if(playerState == null || playerState.get() == null) return null;

        Object state = playerState.get();
        try {
            Object wrapper = XposedHelpers.callMethod(state, "track");

            boolean hasTrack = (Boolean) XposedHelpers.callMethod(wrapper, "d");
            if(hasTrack) {
                Object ct = XposedHelpers.callMethod(wrapper, "c");
                Class<?> contextClass = XposedHelpers.findClass("com.spotify.player.model.ContextTrack", lpparam.classLoader);
                if(contextClass.isInstance(ct)) {
                    Object track = contextClass.cast(ct);

                    String uri = (String) XposedHelpers.callMethod(track, "uri");

                    @SuppressWarnings("unchecked")
                    Map<String, String> md = (Map<String, String>) XposedHelpers.callMethod(track, "metadata");

                    String title = md.get("title");
                    String artist = md.get("artist_name");
                    String album = md.get("album_title");
                    String color = md.get("extracted_color");
                    String imageId = md.get("image_large_url");
                    long position = 0;
                    long timestamp = 0;

                    Object posOpt = XposedHelpers.callMethod(state, "positionAsOfTimestamp");
                    Matcher m = DIGITS.matcher(posOpt.toString());
                    if(m.find()) {
                        long basePos = Long.parseLong(m.group());
                        timestamp = (Long) XposedHelpers.callMethod(state, "timestamp");
                        position = basePos + (System.currentTimeMillis() - timestamp);
                    }

//                    long duration = (Long) XposedHelpers.callMethod(state, "duration");

                    return new SpotifyTrack(title, artist, album, uri, position, color, timestamp, imageId, 0);
                } else {
                    XposedBridge.log("[SpotifyPlus] ContextTrack not found!");
                    return null;
                }
            } else {
                XposedBridge.log("[SpotifyPlus] No track found");
                return null;
            }
        } catch(Exception e) {
            Log.e("SpotifyPlus", "Error getting track information", e);
            return null;
        }
    }

    public static long getCurrentPlaybackPosition() {
        Object wrapper = References.playerStateWrapper == null ? null : References.playerStateWrapper.get();
        if(wrapper == null) return -1;

        Object state;
        try {
            state = XposedHelpers.callMethod(wrapper, "getState");
            if(state == null) return -1;
        } catch(Throwable t) { return -1; }

        try {
            Object progress = XposedHelpers.getObjectField(state, "c");

            Class<?> clazz = progress.getClass();
            if(!clazz.getName().startsWith("p.")) return -1;

            try {
                Object positionObj = XposedHelpers.getObjectField(progress, "a");
                if(positionObj instanceof Long) {
                    return (long)positionObj;
                }
            } catch (Throwable t) {
                XposedBridge.log("[SpotifyPlus] Could not get 'a' field from" + clazz.getName());
            }
        } catch(Throwable t) {}

        return -1;
    }

    private static final List<PlayerStateUpdatedListener> listeners = new ArrayList<>();

    public static void registerPlayerStateListener(PlayerStateUpdatedListener listener) {
        listeners.add(listener);
    }

    public static void unregisterPlayerStateListener(PlayerStateUpdatedListener listener) {
        listeners.remove(listener);
    }

    public static void notifyPlayerStateChanged(Object playerState) {
        for(PlayerStateUpdatedListener listener : listeners) {
            listener.onPlayerStateUpdated(playerState);
        }
    }
}

