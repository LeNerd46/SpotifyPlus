package com.lenerd.spotifyplus.module.scripting;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import com.lenerd.spotifyplus.module.SpotifyHook;
import org.json.JSONObject;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Native UI launch paths verified against Spotify 9.1.82.2160. */
public final class NativeMenuLauncher {
    private static final Map<Activity, WeakReference<Object>> launchers = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Activity, WeakReference<View>> playerButtons = Collections.synchronizedMap(new WeakHashMap<>());

    private NativeMenuLauncher() { }

    private static Activity activity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            Context next = ((ContextWrapper) context).getBaseContext();
            if (next == context) break;
            context = next;
        }
        return null;
    }
    public static void captureLauncher(Context context, Object launcher) {
        Activity owner = activity(context);
        if (owner != null) launchers.put(owner, new WeakReference<>(launcher));
    }
    public static void captureNowPlayingButton(Context context, View button) {
        Activity owner = activity(context);
        if (owner != null) playerButtons.put(owner, new WeakReference<>(button));
    }

    public static Object execute(ClassLoader loader, String operation, JSONObject args) throws Exception {
        // Validate before scheduling, including direct callers of the native request bridge.
        String uri = operation.equals("menu.open") ? args.getString("uri") : null;
        if (uri != null && !uri.matches("spotify:(track|album|artist|playlist):[A-Za-z0-9]+")) {
            throw new IllegalArgumentException("Context menu requires a track, album, artist or playlist URI");
        }
        String contextUri = args.has("contextUri") ? args.getString("contextUri") : null;
        if (contextUri != null && (!contextUri.startsWith("spotify:") || contextUri.length() > 2048)) {
            throw new IllegalArgumentException("Invalid Spotify context URI");
        }
        FutureTask<Object> task = new FutureTask<>(() -> {
            Activity owner = foregroundActivity();
            if (owner == null) {
                throw new IllegalStateException("Spotify must have an active foreground activity");
            }
            if (operation.equals("side.open")) {
                View drawer = findDrawer(owner.getWindow().getDecorView(), loader.loadClass("p.z711"));
                if (drawer == null) throw new IllegalStateException("Spotify's side drawer is not available on this screen");
                drawer.getClass().getMethod("w", boolean.class).invoke(drawer, true);
            } else if (operation.equals("menu.openNowPlaying")) {
                WeakReference<View> reference = playerButtons.get(owner);
                View button = reference == null ? null : reference.get();
                // A React replacement may retain this button off-screen; its native listener stays live.
                if (button == null || !button.isEnabled() || !button.hasOnClickListeners() || !button.performClick()) {
                    throw new IllegalStateException("Spotify's Now Playing menu is not ready");
                }
            } else if (operation.equals("menu.open")) {
                WeakReference<Object> reference = launchers.get(owner);
                Object launcher = reference == null ? null : reference.get();
                if (launcher == null) throw new IllegalStateException("Spotify's context menu launcher is not ready");
                Object interaction = loader.loadClass("p.zt40").getConstructor(String.class)
                        .newInstance(UUID.randomUUID().toString());
                Object logging = loader.loadClass("p.dv40").getConstructor(loader.loadClass("p.zt40"), loader.loadClass("p.qrl0"))
                        .newInstance(interaction, null);
                // Use the same Kotlin default mask as tyj.a. Defaults enable native menu actions.
                Class<?> optionsClass = loader.loadClass("p.ryj");
                Constructor<?> defaults = optionsClass.getConstructor(String.class, boolean.class, boolean.class,
                        boolean.class, boolean.class, boolean.class, boolean.class, loader.loadClass("p.w0y"),
                        boolean.class, boolean.class, int.class, boolean.class, boolean.class, boolean.class,
                        boolean.class, boolean.class, loader.loadClass("p.w500"), boolean.class, boolean.class,
                        loader.loadClass("p.juv0"), boolean.class, String.class, loader.loadClass("p.w500"),
                        loader.loadClass("p.jf20"), boolean.class, int.class);
                Object options = defaults.newInstance(null, false, false, false, false, false, false, null,
                        false, false, 0, false, false, false, false, false, null, false, false, null,
                        false, null, null, null, false, 1073741823);
                launcher.getClass().getMethod("b", String.class, String.class, loader.loadClass("p.dv40"), optionsClass)
                        .invoke(launcher, uri, contextUri, logging, options);
            } else throw new IllegalArgumentException("Unknown UI operation: " + operation);
            return JSONObject.NULL;
        });
        if (Looper.myLooper() == Looper.getMainLooper()) task.run();
        else new Handler(Looper.getMainLooper()).post(task);
        try { return task.get(10, TimeUnit.SECONDS); }
        finally { task.cancel(false); } // A timed-out task must not open UI later.
    }

    private static boolean foreground(Activity owner) {
        return owner != null && !owner.isFinishing() && !owner.isDestroyed()
                && owner.getWindow().getDecorView().hasWindowFocus();
    }
    private static Activity foregroundActivity() {
        // currentActivity tracks Main; NowPlayingActivity is a separate activity.
        if (foreground(SpotifyHook.currentActivity)) return SpotifyHook.currentActivity;
        ArrayList<Activity> owners = new ArrayList<>();
        synchronized (playerButtons) { owners.addAll(playerButtons.keySet()); }
        synchronized (launchers) { owners.addAll(launchers.keySet()); }
        for (Activity owner : owners) if (foreground(owner)) return owner;
        return null;
    }

    private static View findDrawer(View view, Class<?> type) {
        if (type.isInstance(view)) return view;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View drawer = findDrawer(group.getChildAt(i), type);
                if (drawer != null) return drawer;
            }
        }
        return null;
    }
}
