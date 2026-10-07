package com.lenerd.spotifyplus.module.hooks;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import com.lenerd.spotifyplus.module.SpotifyCallback;
import com.lenerd.spotifyplus.module.SpotifyHook;
import com.lenerd.spotifyplus.module.fingerprint.FingerprintMapping;
import com.lenerd.spotifyplus.module.fingerprint.Fingerprints;
import com.lenerd.spotifyplus.module.scripting.ScriptContextMenu;
import com.lenerd.spotifyplus.module.scripting.SpotifyNativeBridge;
import org.json.JSONObject;
import org.json.JSONArray;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Spotify 9.1.82.2160 uses gzj items directly inside f1k, rather than item factories. */
public final class ContextMenuHook extends SpotifyHook {
    private static final FingerprintMapping<Class<?>> MENU_MODEL = FingerprintMapping
            .forClass("context-menu.model")
            .fingerprint(Fingerprints.namedClass("Spotify 9.1.82 name", "p.f1k", 500))
            .fingerprint(Fingerprints.dexClass("duplicate item invariant",
                    FindClass.create().matcher(ClassMatcher.create()
                            .usingStrings("ContextMenuViewModel cannot contain items with duplicate itemResId. id=")
                            .fieldCount(4)), 900))
            .validate(type -> type.getDeclaredFields().length == 4)
            .build();

    private final Map<String, CompletableFuture<Set<String>>> visibilityRequests = new ConcurrentHashMap<>();
    private Constructor<?> menuConstructor;
    private Constructor<?> itemConstructor;
    private Method startService;
    private Constructor<?> launcherConstructor;
    private Constructor<?> nowPlayingButtonConstructor;
    private final List<ScriptContextMenu> scriptMenus = new CopyOnWriteArrayList<>();
    private static final Map<Object, String> headerUris = Collections.synchronizedMap(new java.util.WeakHashMap<>());
    public static String uriForHeader(Object header) { return headerUris.get(header); }
    // Each rendered action owns its captured entity, including when two menus overlap.
    private final Map<String, Runnable> actions = Collections.synchronizedMap(new LinkedHashMap<>());

    private Class<?> type(String name) throws ClassNotFoundException { return classLoader.loadClass("p." + name); }
    private Object field(Object value, String name) throws Exception { return value.getClass().getField(name).get(value); }

    @Override protected void hookSetup() throws ClassNotFoundException, NoSuchMethodException {
        menuConstructor = resolve(MENU_MODEL).getDeclaredConstructor(type("gyj"), List.class, boolean.class);
        itemConstructor = type("gzj").getDeclaredConstructor(String.class, type("cnu"), type("dzj"), Integer.class,
                String.class, Integer.class, boolean.class, type("qc11"), type("fzj"));
        startService = ContextWrapper.class.getDeclaredMethod("startService", Intent.class);
        hook(menuConstructor);
        hook(startService);
        SpotifyNativeBridge.registerHandler("menu", this);
        // Capture activity-scoped launchers at construction, before any menu has been opened.
        launcherConstructor = type("vyj").getDeclaredConstructor(type("guz"),
                type("ad70"), type("ad70"), type("ad70"), type("ad70"), type("ad70"), type("ad70"), type("ad70"),
                type("u380"), type("ad70"), type("ydn"), type("vzj"), type("hdi"), type("kq5"), type("mcx"),
                type("fwu"), type("ad70"), type("ad70"), type("t661"), type("cm7"), boolean.class, type("p69"), type("vbz"));
        nowPlayingButtonConstructor = type("f1p").getDeclaredConstructor(Context.class);
        hook(launcherConstructor);
        hook(nowPlayingButtonConstructor);
    }

    @Override protected void afterHook(SpotifyCallback callback) {
        if (callback.getThrowable() != null) return;
        try {
            if (callback.getMember().equals(launcherConstructor)) {
                com.lenerd.spotifyplus.module.scripting.NativeMenuLauncher.captureLauncher(
                        (Context) callback.getArgs()[0], callback.getThisObject());
            } else if (callback.getMember().equals(nowPlayingButtonConstructor)) {
                com.lenerd.spotifyplus.module.scripting.NativeMenuLauncher.captureNowPlayingButton(
                        (Context) callback.getArgs()[0], (android.view.View) field(callback.getThisObject(), "b"));
            }
        } catch (Exception error) { logError(error); }
    }
    @Override protected void beforeHook(SpotifyCallback callback) {
        try {
            if (callback.getMember().equals(startService)) {
                Intent intent = (Intent) callback.getArgs()[0];
                if (intent == null || intent.getComponent() == null
                        || !intent.getComponent().getClassName().equals("com.spotify.radio.radio.formatlist.RadioFormatListService")) return;
                Runnable action = actions.get(intent.getStringExtra(".seed_uri"));
                if (action != null) { callback.returnAndSkip(null); action.run(); }
                return;
            }
            if (!callback.getMember().equals(menuConstructor) || currentActivity == null) return;
            List<?> original = (List<?>) callback.getArgs()[1];
            if (original == null || original.isEmpty()) return;
            for (Object item : original) if (String.valueOf(field(item, "a")).startsWith("spotifyplus:")) return;
            String uri = contextUri(original);
            Object header = callback.getArgs()[0];
            if (header != null && uri != null) headerUris.put(header, uri);
            Context activity = currentActivity;
            Object factory = type("ut7").getConstructor(Context.class).newInstance(activity);
            Object template = type("ut7").getMethod("a", String.class).invoke(factory, uri == null ? "spotify:track:0" : uri);
            List<Object> updated = new ArrayList<>();
            List<ScriptContextMenu> candidates = new ArrayList<>();
            for (ScriptContextMenu menu : scriptMenus) {
                if (menu.matchesUri(uri)) candidates.add(menu);
            }
            Set<String> visible = evaluateVisibility(candidates, uri);
            for (ScriptContextMenu menu : candidates) {
                if (!visible.contains(menu.id) || !scriptMenus.contains(menu)) continue;
                updated.add(item(factory, template, menu.title, () -> {
                    if (!scriptMenus.contains(menu)) return;
                    try {
                        SpotifyNativeBridge.sendJsonEvent("menu.press", new JSONObject().put("id", menu.id)
                                .put("scriptId", menu.scriptId).put("uri", uri == null ? JSONObject.NULL : uri));
                    } catch (Exception error) { logError(error); }
                }));
            }
            updated.addAll(original);
            callback.getArgs()[1] = updated;
        } catch (Exception error) { logError(error); }
    }

    private Object item(Object factory, Object template, String title, Runnable action) throws Exception {
        String marker = "spotifyplus:" + UUID.randomUUID();
        Object originalAction = field(template, "i");
        Object click = type("di00").getConstructor(int.class, Object.class, Object.class).newInstance(11, factory, marker);
        Object nativeAction = type("fzj").getConstructor(type("p7g1"), int.class, type("ezj"), type("w500"))
                .newInstance(field(originalAction, "a"), field(originalAction, "b"), field(originalAction, "c"), click);
        synchronized (actions) {
            // Bound references to dismissed menus. Most recent menus remain independently clickable.
            while (actions.size() >= 512) actions.remove(actions.keySet().iterator().next());
            actions.put(marker, action);
        }
        return itemConstructor.newInstance(marker, field(template, "b"), field(template, "c"), null,
                title, null, true, null, nativeAction);
    }

    private String contextUri(List<?> items) throws Exception {
        for (Object item : items) {
            if ("radio_go_to_station".equals(field(item, "a"))) {
                Object click = field(field(item, "i"), "d");
                if (click.getClass().getName().equals("p.di00")) return (String) field(click, "c");
            }
        }
        for (Object item : items) {
            String id = String.valueOf(field(item, "a"));
            if (!List.of("share", "add_to_playlist", "queue_track", "queue_album").contains(id)) continue;
            String uri = actionUri(field(field(item, "i"), "d"), 4, new IdentityHashMap<>());
            if (uri != null) return uri;
        }
        return null;
    }
    // Reads the selected action's captured data; it does not discover hook classes or methods.
    private String actionUri(Object value, int depth, IdentityHashMap<Object, Boolean> seen) throws Exception {
        if (value instanceof String text && text.matches("spotify:(track|album|artist|playlist|episode|show):[^:]+")) return text;
        if (value == null || depth == 0 || !value.getClass().getName().startsWith("p.") || seen.put(value, true) != null) return null;
        for (Field field : value.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
            field.setAccessible(true);
            String uri = actionUri(field.get(value), depth - 1, seen);
            if (uri != null) return uri;
        }
        return null;
    }

    private Set<String> evaluateVisibility(List<ScriptContextMenu> menus, String uri) throws Exception {
        Set<String> fallback = new HashSet<>();
        JSONArray ids = new JSONArray();
        for (ScriptContextMenu menu : menus) {
            if (!menu.hasCallback && !menu.disabled) fallback.add(menu.id);
            ids.put(menu.id);
        }
        if (menus.isEmpty()) return fallback;
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<Set<String>> result = new CompletableFuture<>();
        visibilityRequests.put(requestId, result);
        try {
            // The modern hook only exposes the selected entity; use it for both legacy URI arguments.
            SpotifyNativeBridge.sendJsonEvent("menu.visibility", new JSONObject().put("requestId", requestId)
                    .put("ids", ids).put("uri", uri == null ? "" : uri).put("contextUri", uri == null ? "" : uri));
            // One bounded wait for the entire menu, never one wait per extension.
            return result.get(150, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (java.util.concurrent.TimeoutException error) {
            log("Context menu visibility timed out; hiding conditional items");
            return fallback;
        } finally {
            visibilityRequests.remove(requestId);
        }
    }

    @Override public Object handle(String command, Object[] args) {
        try {
            if (command.equals("register")) {
                ScriptContextMenu menu;
                if (args[0] instanceof JSONObject data) {
                    Set<String> types = null;
                    if (data.has("types")) {
                        types = new HashSet<>();
                        JSONArray values = data.getJSONArray("types");
                        for (int i = 0; i < values.length(); i++) {
                            String value = values.getString(i);
                            if (!Set.of("track", "artist", "album", "playlist").contains(value))
                                throw new IllegalArgumentException("Invalid context menu type: " + value);
                            types.add(value);
                        }
                    }
                    menu = new ScriptContextMenu(data.getString("id"), data.getString("scriptId"), data.getString("title"),
                            types, data.optBoolean("hasCallback"), data.optBoolean("disabled"));
                } else {
                    menu = new ScriptContextMenu((String) args[0], (String) args[1], (String) args[2]);
                }
                scriptMenus.removeIf(existing -> existing.id.equals(menu.id) && existing.scriptId.equals(menu.scriptId));
                scriptMenus.add(menu);
            } else if (command.equals("visibilityResult")) {
                JSONObject data = (JSONObject) args[0];
                CompletableFuture<Set<String>> pending = visibilityRequests.get(data.getString("requestId"));
                if (pending != null) {
                    Set<String> visible = new HashSet<>();
                    JSONArray values = data.getJSONArray("visible");
                    for (int i = 0; i < values.length(); i++) visible.add(values.getString(i));
                    pending.complete(visible);
                }
            } else if (command.equals("unregisterScript")) scriptMenus.removeIf(menu -> menu.scriptId.equals(args[0]));
        } catch (Exception error) { logError(error); }
        return null;
    }
}
