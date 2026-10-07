package com.lenerd.spotifyplus.module;

import android.content.res.AssetManager;
import android.util.Log;
import com.lenerd.spotifyplus.sdk.spotify.entities.SpotifyAlbum;
import com.lenerd.spotifyplus.sdk.spotify.entities.SpotifyTrack;
import com.lenerd.spotifyplus.module.scripting.entities.PlatformData;
import org.luckypray.dexkit.DexKitBridge;
import java.lang.reflect.Method;
import java.util.Map;

/** Shared state and asset access required by the extension runtime. */
public final class Utils {
    private Utils() { }

    public static String token;
    public static String clientToken;
    public static String spotifyVersion;
    public static DexKitBridge bridge;
    public static volatile String MODULE_APK_PATH;
    public static volatile Object playerState;
    public static PlatformData platformData;
    private static Method hasTrackMethod;
    private static Method getContextTrack;

    public static AssetManager getModuleAssetManager() {
        try {
            AssetManager am = AssetManager.class.getDeclaredConstructor().newInstance();
            Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);

            int cookie = (Integer) addAssetPath.invoke(am, MODULE_APK_PATH);
            if (cookie == 0) throw new IllegalStateException("addAssetPath failed for " + MODULE_APK_PATH);

            return am;
        } catch (Exception e) {
            Log.e("SpotifyPlus", "Failed to create module AssetManager", e);
            return null;
        }
    }

    public static SpotifyTrack getTrack(ClassLoader classLoader) {
        Object state = playerState;
        if (state == null) return null;

        try {
            Method getTrackMethod = classLoader.loadClass("com.spotify.player.model.PlayerState").getMethod("track");
            Object wrapper = getTrackMethod.invoke(state);
            if (wrapper == null) return null;

            if (hasTrackMethod == null)
                hasTrackMethod = classLoader.loadClass("p.ibl0").getMethod("c");

            Object hasTrack = hasTrackMethod.invoke(wrapper);
            if (hasTrack == null) return null;

            if ((Boolean) hasTrack) {
                if (getContextTrack == null)
                    getContextTrack = classLoader.loadClass("p.ibl0").getMethod("b");

                Object ct = getContextTrack.invoke(wrapper);
                if (ct == null) return null;

                Class<?> contextClass = Class.forName("com.spotify.player.model.ContextTrack", false, classLoader);
                if (contextClass.isInstance(ct)) {
                    Object track = contextClass.cast(ct);

                    Method uriMethod = contextClass.getMethod("uri");
                    String uri = (String) uriMethod.invoke(track);

                    Method metadataMethod = contextClass.getMethod("metadata");
                    Map<String, String> metadata = (Map<String, String>) metadataMethod.invoke(track);
                    if (metadata == null) return null;

//                    Log.d("SpotifyPlus", "==== METADATA ====");
//                    metadata.forEach((key, value) -> Log.d("SpotifyPlus", key + " | " + value));
//                    Log.d("SpotifyPlus", "==================");

                    String title = metadata.get("title");
                    String artist = metadata.get("artist_name");
                    String album = metadata.get("album_title");
                    String color = metadata.get("extracted_color");
                    String imageId = metadata.get("image_large_url");
                    String trackNumber = metadata.get("album_track_number");
                    long duration = parsePlaybackNumber(metadata.get("duration"), 0);
                    long position = 0;
                    long timestamp = 0;
                    boolean saved = false;
                    if (metadata.containsKey("collection.in_collection")) {
                        String savedValue = metadata.get("collection.in_collection");
                        saved = Boolean.parseBoolean(savedValue);
                    }

                    SpotifyAlbum albumObj = new SpotifyAlbum(album, artist, null, imageId == null ? null : imageId.replace("spotify:image:", "https://i.scdn.co/image/"));
                    Method positionMethod = classLoader.loadClass("com.spotify.player.model.PlayerState").getMethod("positionAsOfTimestamp");
                    Object posOpt = positionMethod.invoke(state);
                    if (posOpt == null) {
                        return new SpotifyTrack(title, artist, new String[]{artist}, albumObj, uri, -1, color, -1, imageId, duration, saved, false, (int) parsePlaybackNumber(trackNumber, 0));
                    }

                    position = getCurrentPlaybackPosition();
                    timestamp = System.currentTimeMillis();

                    return new SpotifyTrack(title, artist, new String[]{artist}, albumObj, uri, position, color, timestamp, imageId, duration, saved, false, (int) parsePlaybackNumber(trackNumber, 0));
                }
            }
        } catch (Exception e) {
            Log.e("SpotifyPlus", e.getMessage(), e);
            return null;
        }

        return null;
    }

    private static long parsePlaybackNumber(String value, long fallback) {
        try { return Long.parseLong(value); } catch (Exception ignored) { return fallback; }
    }

    public static long getCurrentPlaybackPosition() {
        Object state = playerState;
        if (state == null) return -1;
        try {
            Class<?> model = state.getClass().getClassLoader().loadClass("com.spotify.player.model.PlayerState");
            Class<?> optional = state.getClass().getClassLoader().loadClass("p.ibl0");
            Object position = model.getMethod("positionAsOfTimestamp").invoke(state);
            if (!(Boolean) optional.getMethod("c").invoke(position)) return -1;
            long value = ((Number) optional.getMethod("b").invoke(position)).longValue();
            if ((Boolean) model.getMethod("isPlaying").invoke(state)
                    && !(Boolean) model.getMethod("isPaused").invoke(state)
                    && !(Boolean) model.getMethod("isBuffering").invoke(state)) {
                long timestamp = ((Number) model.getMethod("timestamp").invoke(state)).longValue();
                Object speed = model.getMethod("playbackSpeed").invoke(state);
                double rate = (Boolean) optional.getMethod("c").invoke(speed)
                        ? ((Number) optional.getMethod("b").invoke(speed)).doubleValue() : 1;
                value += (long) (Math.max(0, System.currentTimeMillis() - timestamp) * rate);
            }
            Object duration = model.getMethod("duration").invoke(state);
            if ((Boolean) optional.getMethod("c").invoke(duration))
                value = Math.min(value, ((Number) optional.getMethod("b").invoke(duration)).longValue());
            return Math.max(0, value);
        } catch (Exception error) { return -1; }
    }

}
