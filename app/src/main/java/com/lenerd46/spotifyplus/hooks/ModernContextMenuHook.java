package com.lenerd46.spotifyplus.hooks;

import com.lenerd46.spotifyplus.player.NextUpQueue;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import com.lenerd46.spotifyplus.R;
import com.lenerd46.spotifyplus.References;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

import java.lang.reflect.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class ModernContextMenuHook extends SpotifyHook {
    private static final String LYRICS = "spotifyplus_open_lyrics";
    private static final String LAST_FM = "spotifyplus_open_last_fm";
    private static final String NEXT = "queue_play_next_track";
    private final Map<Object, Runnable> callbacks = Collections.synchronizedMap(new WeakHashMap<>());
    private final Set<Class<?>> hookedCallbacks = new HashSet<>();
    private Class<?> itemType;
    private Constructor<?> itemConstructor;
    private List<Field> itemFields;
    private Object unit;

    static ClassMatcher itemMatcher() {
        return ClassMatcher.create().usingStrings("Exactly one title property must be populated.",
                "Exactly one icon property must be populated.")
                .addMethod(MethodMatcher.create().name("<init>").paramTypes("java.lang.String", null, null,
                        "java.lang.Integer", "java.lang.String", "java.lang.Integer", "boolean", null, null));
    }

    @Override protected void hook() {
        try {
            itemType = bridge.findClass(FindClass.create().matcher(itemMatcher())).single().getInstance(lpparm.classLoader);
            itemFields = fields(itemType);
            if (itemFields.size() != 9) throw new IllegalStateException("Unexpected context-menu item fields");
            itemConstructor = itemType.getDeclaredConstructor(itemFields.stream().map(Field::getType).toArray(Class<?>[]::new));
            itemConstructor.setAccessible(true);
            Class<?> unitType = bridge.findClass(FindClass.create().matcher(ClassMatcher.create()
                    .usingStrings("kotlin.Unit").fieldCount(1).addMethod(MethodMatcher.create().name("toString"))))
                    .single().getInstance(lpparm.classLoader);
            unit = Arrays.stream(unitType.getDeclaredFields()).filter(f -> Modifier.isStatic(f.getModifiers()) && f.getType() == unitType)
                    .findFirst().orElseThrow().get(null);
            Class<?> menu = bridge.findClass(FindClass.create().matcher(ClassMatcher.create()
                    .usingStrings("ContextMenuViewModel cannot contain items with duplicate itemResId. id=")))
                    .single().getInstance(lpparm.classLoader);
            Constructor<?> constructor = Arrays.stream(menu.getDeclaredConstructors())
                    .filter(c -> c.getParameterCount() == 3 && c.getParameterTypes()[1] == List.class && c.getParameterTypes()[2] == boolean.class)
                    .findFirst().orElseThrow();
            XposedBridge.hookMethod(constructor, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    try { extendMenu(param); }
                    catch (Throwable t) { XposedBridge.log("[SpotifyPlus][ContextMenu] Keeping native menu: " + t); }
                }
            });
            XposedBridge.log("[SpotifyPlus][ContextMenu] Data-item adapter: " + itemType.getName());
        } catch (Throwable t) {
            XposedBridge.log("[SpotifyPlus][ContextMenu] Data-item discovery failed: " + t);
        }
    }

    private void extendMenu(XC_MethodHook.MethodHookParam param) throws Exception {
        List<?> original = (List<?>) param.args[1];
        if (original == null || original.isEmpty()) return;
        Object template = null, queueItem = null;
        Set<String> ids = new HashSet<>();
        for (Object item : original) {
            if (!itemType.isInstance(item)) continue;
            String id = (String) itemFields.get(0).get(item);
            ids.add(id);
            if (template == null) template = item;
            if ("queue_track".equals(id)) queueItem = item;
        }
        if (template == null) return;
        if (queueItem != null) template = queueItem;
        Object track = findSingleTrack(queueItem, 4, new IdentityHashMap<>());
        String uri = track == null ? null : (String) XposedHelpers.callMethod(track, "uri");
        Object header = param.args[0];
        List<Field> headerText = header == null ? Collections.emptyList()
                : fields(header.getClass()).stream().filter(f -> f.getType() == String.class).toList();
        String title = headerText.size() == 2 ? (String) headerText.get(0).get(header) : "";
        String subtitle = headerText.size() == 2 ? (String) headerText.get(1).get(header) : "";
        String artist = subtitle == null ? "" : subtitle.split(" • ")[0];
        if (uri != null && headerText.size() == 2) NewContextMenuHook.updateLastFmHeader(header, headerText.get(0), headerText.get(1), uri);
        ArrayList<Object> result = new ArrayList<>(original);
        if (!ids.contains(LYRICS)) result.add(0, item(template, LYRICS, References.getString(R.string.lyrics_button), R.drawable.music_note, () -> {
            Activity activity = References.currentActivity;
            if (activity != null) activity.runOnUiThread(() -> BeautifulLyricsHook.showOverlay(activity, false));
        }));
        if (uri != null && !ids.contains(LAST_FM)) result.add(0, item(template, LAST_FM, References.getString(R.string.open_song_lastfm), R.drawable.lastfm, () -> {
            Activity activity = References.currentActivity;
            if (activity != null) activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.last.fm/music/"
                    + URLEncoder.encode(artist, StandardCharsets.UTF_8) + "/_/" + URLEncoder.encode(title == null ? "" : title, StandardCharsets.UTF_8))));
        }));
        if (track != null && !ids.contains(NEXT)) {
            Object action = itemFields.get(8).get(queueItem);
            Object callback = fields(action.getClass()).get(3).get(action);
            NextUpQueue queue = new NextUpQueue(lpparm.classLoader);
            NextUpQueue.NextUpAction next = queue.resolve(new Object[]{callback}, track, 4);
            if (next != null) result.add(Math.max(0, result.indexOf(queueItem)), item(queueItem, NEXT, References.getString(R.string.play_next_button), R.drawable.reorder,
                    () -> queue.insertAtTopOfNextUp(next)));
        }
        param.args[1] = result;
    }

    private Object item(Object template, String id, String title, int icon, Runnable onClick) throws Exception {
        Object[] values = values(template, itemFields);
        values[0] = id;
        values[1] = null;
        Drawable drawable = References.modResources.getDrawable(icon);
        Constructor<?> outer = onlyConstructor(itemFields.get(2).getType(), 1);
        Constructor<?> inner = onlyConstructor(outer.getParameterTypes()[0], 1);
        if (!inner.getParameterTypes()[0].isAssignableFrom(LayerDrawable.class)) throw new IllegalStateException("Unsupported menu drawable wrapper");
        inner.setAccessible(true);
        values[2] = outer.newInstance(inner.newInstance(new LayerDrawable(new Drawable[]{drawable})));
        values[3] = null;
        values[4] = title;
        values[5] = null;
        values[6] = true;
        values[7] = null;
        Object action = values[8];
        List<Field> actionFields = fields(action.getClass());
        if (actionFields.size() != 4 || actionFields.get(1).getType() != int.class) throw new IllegalStateException("Unexpected menu action layout");
        Object[] actionValues = values(action, actionFields);
        Object originalCallback = actionValues[3];
        // R8 narrows this field to the concrete Kotlin Lambda base in 9.1.82.
        // A clone of Spotify's own lambda satisfies that cast; only this instance is intercepted.
        Object callback = cloneCallback(originalCallback);
        callbacks.put(callback, onClick);
        if (hookedCallbacks.add(callback.getClass())) XposedBridge.hookAllMethods(callback.getClass(), "invoke", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Runnable runnable = callbacks.get(param.thisObject);
                if (runnable == null) return;
                param.setResult(unit);
                try { runnable.run(); } catch (Throwable t) { XposedBridge.log(t); }
            }
        });
        actionValues[3] = callback;
        values[8] = onlyConstructor(action.getClass(), 4).newInstance(actionValues);
        return itemConstructor.newInstance(values);
    }

    private static Object cloneCallback(Object template) throws Exception {
        List<Field> fields = fields(template.getClass());
        Object[] values = values(template, fields);
        List<Constructor<?>> candidates = Arrays.stream(template.getClass().getDeclaredConstructors())
                .filter(c -> c.getParameterCount() == values.length && accepts(c.getParameterTypes(), values)).toList();
        if (candidates.size() != 1) throw new IllegalStateException("Cannot uniquely clone menu callback " + template.getClass());
        Constructor<?> constructor = candidates.get(0);
        constructor.setAccessible(true);
        return constructor.newInstance(values);
    }

    private static boolean accepts(Class<?>[] types, Object[] values) {
        for (int i = 0; i < values.length; i++) {
            Class<?> type = types[i] == int.class ? Integer.class : types[i] == boolean.class ? Boolean.class : types[i];
            if (values[i] == null ? type.isPrimitive() : !type.isInstance(values[i])) return false;
        }
        return true;
    }

    private static Constructor<?> onlyConstructor(Class<?> type, int count) {
        List<Constructor<?>> found = Arrays.stream(type.getDeclaredConstructors()).filter(c -> c.getParameterCount() == count).toList();
        if (found.size() != 1) throw new IllegalStateException("Ambiguous constructor in " + type);
        found.get(0).setAccessible(true);
        return found.get(0);
    }

    private static List<Field> fields(Class<?> type) {
        List<Field> fields = Arrays.stream(type.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers())).toList();
        fields.forEach(f -> f.setAccessible(true));
        return fields;
    }

    private static Object[] values(Object owner, List<Field> fields) throws IllegalAccessException {
        Object[] values = new Object[fields.size()];
        for (int i = 0; i < values.length; i++) values[i] = fields.get(i).get(owner);
        return values;
    }

    private static Object findSingleTrack(Object value, int depth, IdentityHashMap<Object, Boolean> seen) throws IllegalAccessException {
        if (value == null || depth < 0 || seen.put(value, true) != null) return null;
        if (value instanceof List<?> list) {
            if (list.size() != 1 || list.get(0) == null) return null;
            try {
                Object track = list.get(0);
                return String.valueOf(XposedHelpers.callMethod(track, "uri")).startsWith("spotify:track:") ? track : null;
            } catch (Throwable ignored) { return null; }
        }
        String name = value.getClass().getName();
        if (name.startsWith("java.") || name.startsWith("android.") || name.startsWith("kotlin.")) return null;
        for (Field field : fields(value.getClass())) {
            if (field.getType().isPrimitive()) continue;
            Object track = findSingleTrack(field.get(value), depth - 1, seen);
            if (track != null) return track;
        }
        return null;
    }
}
