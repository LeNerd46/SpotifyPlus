package com.lenerd.spotifyplus.module.hooks;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import com.lenerd.spotifyplus.module.SpotifyCallback;
import com.lenerd.spotifyplus.module.SpotifyHook;
import com.lenerd.spotifyplus.module.scripting.SpotifyNativeBridge;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ReactManager extends SpotifyHook {
    private static final Map<String, SurfaceEntry> surfaces = new HashMap<>();

    private static class SurfaceEntry {
        final ViewGroup root;

        SurfaceEntry(ViewGroup root) {
            this.root = root;
        }
    }

    @Override
    protected void hookSetup() throws NoSuchMethodException, ClassNotFoundException, NoSuchFieldException {
        SpotifyNativeBridge.registerHandler("react", this);
    }

    @Override
    protected void beforeHook(SpotifyCallback callback) {
    }

    @Override
    protected void afterHook(SpotifyCallback callback) {
    }

    @Override
    public Object handle(String command, Object[] args) {
        if (command.equals("commit")) {
            try {
                String surfaceId = (String) args[0];
                applyCommit(surfaceId, (JSONArray) args[1]);
            } catch (Exception e) {
                logError(e);
            }
        }

        return null;
    }

    public static void registerSurface(String surfaceId, ViewGroup root) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                SurfaceEntry existing = surfaces.get(surfaceId);
                if (existing != null && existing.root == root) {
                    SpotifyNativeBridge.attachSurfaceHost(surfaceId, root);
                } else {
                    if (existing != null) SpotifyNativeBridge.detachSurfaceHost(surfaceId);
                    SpotifyNativeBridge.attachSurfaceHost(surfaceId, root);
                    surfaces.put(surfaceId, new SurfaceEntry(root));
                }

                JSONObject json = new JSONObject();
                json.put("id", surfaceId);
                json.put("type", surfaceId);

                SpotifyNativeBridge.sendEvent("react.surfaceEvent", json.toString());
            } catch (Exception e) {
                logError(e);
            }
        });
    }

    public static void registerSurfaceSilent(String surfaceId, ViewGroup root) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                SurfaceEntry existing = surfaces.get(surfaceId);
                if (existing != null && existing.root == root) {
                    SpotifyNativeBridge.attachSurfaceHost(surfaceId, root);
                    return;
                }
                if (existing != null) SpotifyNativeBridge.detachSurfaceHost(surfaceId);
                SpotifyNativeBridge.attachSurfaceHost(surfaceId, root);
                surfaces.put(surfaceId, new SurfaceEntry(root));
            } catch (Exception e) {
                logError(e);
            }
        });
    }

    public static void unregisterSurface(String surfaceId) {
        new Handler(Looper.getMainLooper()).post(() -> {
            SurfaceEntry existing = surfaces.remove(surfaceId);
            if (existing != null) SpotifyNativeBridge.detachSurfaceHost(surfaceId);
        });
    }

    private void applyCommit(String surfaceId, JSONArray ops) {
        SurfaceEntry entry = surfaces.get(surfaceId);
        if (entry == null) {
            Log.w("SpotifyPlus", "No surface registered for " + surfaceId);
            return;
        }

        SpotifyNativeBridge.applySurfaceOps(surfaceId, ops);
    }
}
