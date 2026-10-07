package com.lenerd.spotifyplus.module.scripting;

import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native target instances. Access is confined to the Android UI thread. */
public final class UITargets {
    public interface Parts {
        View create(String id) throws Exception;
        java.util.concurrent.CompletionStage<Void> invoke(String id) throws Exception;
    }
    private static final Map<String, Entry> targets = new LinkedHashMap<>();
    private static final Map<String, JSONArray> capabilities = new LinkedHashMap<>();
    private static final Map<String, JSONArray> selectors = new LinkedHashMap<>();
    private static volatile String spotifyVersion = "unknown";
    public static void initialize(android.content.Context context) {
        try { spotifyVersion = context.getPackageManager().getPackageInfo("com.spotify.music", 0).versionName; }
        catch (Exception error) { android.util.Log.e("SpotifyPlus", "Cannot read Spotify version for UI adapters", error); }
    }
    public static boolean supportsSpotify() { return "9.1.82.2160".equals(spotifyVersion); }
    private UITargets() { }

    private static final class Entry {
        final String id, name;
        final ViewGroup root;
        final View original;
        JSONObject context;
        String surfaceId;
        boolean fill;
        Parts parts;
        Entry(String id, String name, ViewGroup root, View original, JSONObject context) {
            this.id = id; this.name = name; this.root = root; this.original = original; this.context = context;
        }
    }

    public static synchronized void capability(String name, String... operations) {
        capabilities.put(name, new JSONArray(java.util.Arrays.asList(operations)));
    }

    public static synchronized void resourceSelector(String name, String screen, String resourceId) {
        try { selectors.put(name, new JSONArray().put(new JSONObject().put("screen", screen).put("resourceId", resourceId))); }
        catch (Exception error) { throw new IllegalArgumentException(error); }
    }

    public static void open(String id, String name, ViewGroup root, View original, JSONObject context) {
        open(id, name, root, original, context, false);
    }

    public static void open(String id, String name, ViewGroup root, View original, JSONObject context, boolean fill) {
        assertMain();
        Entry entry = targets.get(id);
        if (entry == null) {
            entry = new Entry(id, name, root, original, context);
            targets.put(id, entry);
        } else entry.context = context;
        entry.fill = fill;
        try {
            context.put("instanceId", id);
            SpotifyNativeBridge.sendJsonEvent("ui.target", new JSONObject().put("id", id).put("target", name)
                    .put("context", context).put("layout", fill ? "fill" : "intrinsic")
                    .put("nativeContent", original != null).put("selectors", selectors.get(name)).put("operations", capabilities.get(name)));
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    public static void close(String id) {
        assertMain();
        Entry entry = targets.get(id);
        if (entry == null) return;
        if (entry.surfaceId != null) {
            try { execute("ui.detach", new JSONObject().put("instanceId", id).put("surfaceId", entry.surfaceId)); }
            catch (Exception error) { throw new IllegalStateException(error); }
        }
        targets.remove(id);
        try { SpotifyNativeBridge.sendJsonEvent("ui.close", new JSONObject().put("id", id)); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    public static void parts(String id, Parts parts) {
        assertMain();
        Entry entry = targets.get(id);
        if (entry != null) entry.parts = parts;
    }

    public static View createPart(String surfaceId, String partId) {
        assertMain();
        for (Entry entry : targets.values()) {
            if (!surfaceId.equals(entry.surfaceId) || entry.parts == null) continue;
            try { return entry.parts.create(partId); }
            catch (Exception error) { throw new IllegalStateException("Cannot mount native part", error); }
        }
        throw new IllegalStateException("Native part is unavailable on this surface");
    }

    public static void renderFailed(String surfaceId, String error) {
        assertMain();
        for (Entry entry : targets.values()) {
            if (!surfaceId.equals(entry.surfaceId)) continue;
            try {
                execute("ui.detach", new JSONObject().put("instanceId", entry.id).put("surfaceId", surfaceId));
                SpotifyNativeBridge.sendJsonEvent("ui.failed", new JSONObject().put("id", entry.id)
                        .put("surfaceId", surfaceId).put("error", error));
            } catch (Exception failure) { android.util.Log.e("SpotifyPlus", "Cannot restore UI target", failure); }
            return;
        }
    }

    public static Object execute(String operation, JSONObject args) throws Exception {
        assertMain();
        switch (operation) {
            case "ui.invokeAction": {
                Entry entry = targets.get(args.getString("instanceId"));
                if (entry == null || !entry.root.isAttachedToWindow() || entry.parts == null)
                    throw new IllegalStateException("Native action instance has closed");
                return entry.parts.invoke(args.getString("partId"));
            }
            case "ui.snapshot": {
                JSONArray result = new JSONArray();
                for (Entry entry : targets.values()) result.put(new JSONObject().put("id", entry.id).put("target", entry.name)
                        .put("context", entry.context).put("layout", entry.fill ? "fill" : "intrinsic")
                        .put("selectors", selectors.get(entry.name))
                        .put("nativeContent", entry.original != null)
                        .put("operations", capabilities.get(entry.name)));
                return result;
            }
            case "ui.list": {
                JSONArray result = new JSONArray();
                for (String name : capabilities.keySet()) result.put(inspect(name));
                return result;
            }
            case "ui.inspect": {
                Object target = args.get("target");
                if (target instanceof JSONObject selector && selector.has("screen")
                        && (selector.has("resourceId") != selector.has("composeTag"))) {
                    for (Map.Entry<String, JSONArray> aliases : selectors.entrySet()) {
                        for (int i = 0; i < aliases.getValue().length(); i++) {
                            JSONObject alias = aliases.getValue().getJSONObject(i);
                            String key = selector.has("resourceId") ? "resourceId" : "composeTag";
                            if (alias.optString("screen").equals(selector.getString("screen")) && alias.has(key)
                                    && alias.getString(key).equals(selector.getString(key))) return inspect(aliases.getKey());
                        }
                    }
                }
                if (!(target instanceof String)) return new JSONObject().put("name", target.toString()).put("available", false)
                        .put("operations", new JSONArray()).put("instances", 0).put("conflicts", new JSONArray())
                        .put("reason", "This selector has no verified native adapter");
                return inspect((String) target);
            }
            case "ui.attach": {
                Entry entry = targets.get(args.getString("instanceId"));
                if (entry == null) throw new IllegalStateException("UI target instance has closed");
                if (entry.surfaceId != null) throw new IllegalStateException("UI target already has a React host");
                String surfaceId = "ui:" + UUID.randomUUID();
                entry.surfaceId = surfaceId;
                SpotifyNativeBridge.attachSurfaceHost(surfaceId, entry.root, entry.original == null ? null : () -> entry.original, !entry.fill);
                return new JSONObject().put("surfaceId", surfaceId);
            }
            case "ui.detach": {
                Entry entry = targets.get(args.getString("instanceId"));
                String surfaceId = args.getString("surfaceId");
                if (entry == null || !surfaceId.equals(entry.surfaceId)) return null;
                SpotifyNativeBridge.detachSurfaceHost(surfaceId);
                entry.surfaceId = null;
                if (entry.original == null) return null;
                entry.original.setVisibility(View.VISIBLE);
                NativeContentMover.move(entry.original, entry.root,
                        entry.fill ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT);
                return null;
            }
            default: throw new IllegalArgumentException("Unknown UI operation: " + operation);
        }
    }

    private static JSONObject inspect(String name) throws Exception {
        JSONArray operations = supportsSpotify() ? capabilities.get(name) : null;
        int count = 0;
        for (Entry entry : targets.values()) if (entry.name.equals(name)) count++;
        JSONObject result = new JSONObject().put("name", name).put("available", operations != null)
                .put("operations", operations == null ? new JSONArray() : operations)
                .put("instances", count).put("conflicts", new JSONArray());
        if (operations == null) result.put("reason", "No verified adapter for " + name + " on Spotify " + spotifyVersion);
        return result;
    }

    private static void assertMain() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("UI targets require Android's main thread");
    }
}
