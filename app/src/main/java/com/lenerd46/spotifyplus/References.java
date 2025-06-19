package com.lenerd46.spotifyplus;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.XModuleResources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import com.lenerd46.spotifyplus.beautifullyrics.entities.PlayerStateUpdatedListener;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
    public static WeakReference<Typeface> beautifulFont = new WeakReference<>(null);

    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static Method hasTrackMethod;
    private static Method getContextTrack;

    public static SpotifyTrack getTrackTitle(XC_LoadPackage.LoadPackageParam lpparam, DexKitBridge bridge) {
        if(playerState == null || playerState.get() == null) {
            XposedBridge.log("[SpotifyPlus] playerState is null");
            return null;
        }

        Object state = playerState.get();
        try {
            Object wrapper = XposedHelpers.callMethod(state, "track");

            var className = wrapper.getClass().getName();
            if(hasTrackMethod == null) {
                var clazz = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().className(className)));
                hasTrackMethod = bridge.findMethod(FindMethod.create().searchInClass(clazz).matcher(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).returnType(boolean.class).paramCount(0))).get(0).getMethodInstance(lpparam.classLoader);
            }

            boolean hasTrack = (Boolean) hasTrackMethod.invoke(wrapper);
            if(hasTrack) {
                if(getContextTrack == null) {
                    var clazz = bridge.findClass(FindClass.create().matcher(ClassMatcher.create().className(className)));
                    getContextTrack = bridge.findMethod(FindMethod.create().searchInClass(clazz).matcher(MethodMatcher.create().modifiers(Modifier.PUBLIC | Modifier.FINAL).paramCount(0).returnType(Object.class))).get(0).getMethodInstance(lpparam.classLoader);
                }

                Object ct = getContextTrack.invoke(wrapper);
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

    public static long getCurrentPlaybackPosition(DexKitBridge bridge, XC_LoadPackage.LoadPackageParam lpparam) {
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

    public static SharedPreferences getPreferences() {
        Activity activity = currentActivity.get();

        if(activity == null) return null;

        return activity.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
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

