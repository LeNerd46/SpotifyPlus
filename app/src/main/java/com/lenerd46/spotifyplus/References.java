package com.lenerd46.spotifyplus;

import android.app.Activity;
import android.media.MediaPlayer;
import android.util.Log;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class References {
    public static WeakReference<Activity> currentActivity = new WeakReference<>(null);
    public static WeakReference<Object> playerState = new WeakReference<>(null);
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
                    long position = 0;

                    Object posOpt = XposedHelpers.callMethod(state, "positionAsOfTimestamp");
                    Matcher m = DIGITS.matcher(posOpt.toString());
                    if(m.find()) {
                        long basePos = Long.parseLong(m.group());
                        long ts = (Long) XposedHelpers.callMethod(state, "timestamp");
                        position = basePos + (System.currentTimeMillis() - ts);
                    }

                    return new SpotifyTrack(title, artist, album, uri, position, color);
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
}

