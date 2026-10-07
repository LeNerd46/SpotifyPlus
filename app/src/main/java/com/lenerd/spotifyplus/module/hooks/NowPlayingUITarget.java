package com.lenerd.spotifyplus.module.hooks;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.lenerd.spotifyplus.module.SpotifyCallback;
import com.lenerd.spotifyplus.module.SpotifyHook;
import com.lenerd.spotifyplus.module.Utils;
import com.lenerd.spotifyplus.module.scripting.UITargets;
import com.lenerd.spotifyplus.sdk.spotify.entities.SpotifyTrack;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import org.json.JSONObject;

/** The page's fragment container retains its ID and object identity, including when hidden. */
public final class NowPlayingUITarget extends SpotifyHook {
    private static final String ACTIVITY = "com.spotify.nowplaying.musicinstallation.NowPlayingActivity";
    private static final String LYRICS = "com.spotify.lyrics.fullscreenview.page.LyricsFullscreenPageActivity";
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Map<Activity, Mounted> instances = new IdentityHashMap<>();
    private static final java.util.concurrent.atomic.AtomicBoolean refreshPending = new java.util.concurrent.atomic.AtomicBoolean();
    private Method create, createLyrics, destroy;

    @Override protected void hookSetup() throws ClassNotFoundException, NoSuchMethodException {
        create = classLoader.loadClass(ACTIVITY).getDeclaredMethod("onCreate", Bundle.class);
        createLyrics = classLoader.loadClass(LYRICS).getDeclaredMethod("onCreate", Bundle.class);
        destroy = Activity.class.getDeclaredMethod("onDestroy");
        hook(create); hook(createLyrics); hook(destroy);
        UITargets.capability("nowPlaying.page", "replace", "overlay");
        UITargets.resourceSelector("nowPlaying.page", "nowPlaying.page", "com.spotify.music:id/content");
        UITargets.capability("lyrics.page", "replace", "overlay");
    }

    @Override protected void beforeHook(SpotifyCallback callback) { }
    @Override protected void afterHook(SpotifyCallback callback) {
        if (callback == null || callback.getThrowable() != null || !UITargets.supportsSpotify()) return;
        if (!(callback.getThisObject() instanceof Activity activity)) return;
        boolean lyrics = activity.getClass().getName().equals(LYRICS);
        if (!lyrics && !activity.getClass().getName().equals(ACTIVITY)) return;
        if (callback.getMember().equals(destroy)) {
            Mounted mounted = instances.remove(activity);
            if (mounted != null) mounted.close();
            return;
        }
        if (!(callback.getMember().equals(create) || callback.getMember().equals(createLyrics)) || instances.containsKey(activity)) return;
        int id = activity.getResources().getIdentifier("content", "id", "com.spotify.music");
        View original = activity.findViewById(id);
        if (lyrics) {
            ViewGroup content = activity.findViewById(android.R.id.content);
            // The full-screen ComposeView precedes the separate snackbar container.
            original = content != null && content.getChildCount() > 0 ? content.getChildAt(0) : null;
            if (original == null || !original.getClass().getName().equals("androidx.compose.ui.platform.ComposeView")) return;
        }
        // Keep the verified XML/Compose containers intact; skip other parent layouts.
        if (!(original instanceof ViewGroup) || !(original.getParent() instanceof FrameLayout parent)) return;
        Mounted mounted = new Mounted(activity, parent, original, lyrics ? "lyrics.page" : "nowPlaying.page");
        instances.put(activity, mounted);
        mounted.publish();
    }

    public static void refresh() {
        if (!refreshPending.compareAndSet(false, true)) return;
        main.post(() -> {
            refreshPending.set(false);
            for (Mounted mounted : instances.values()) mounted.publish();
        });
    }

    private static final class Mounted {
        final String id = "nowPlaying:" + UUID.randomUUID();
        final Activity activity;
        final FrameLayout parent, host;
        final View original;
        final String target;
        final ViewGroup.LayoutParams originalParams;
        final int index;
        final float translationZ;
        String lastContext;
        Mounted(Activity activity, FrameLayout parent, View original, String target) {
            this.activity = activity; this.parent = parent; this.original = original;
            this.target = target;
            originalParams = original.getLayoutParams(); index = parent.indexOfChild(original);
            translationZ = original.getTranslationZ();
            host = new FrameLayout(activity);
            host.setTranslationZ(translationZ);
            parent.removeView(original);
            original.setTranslationZ(0);
            host.addView(original, new FrameLayout.LayoutParams(-1, -1));
            parent.addView(host, index, originalParams);
        }
        void publish() {
            try {
                SpotifyTrack track = Utils.getTrack(classLoader);
                Object pageUri = Utils.playerState == null ? JSONObject.NULL
                        : Utils.playerState.getClass().getMethod("contextUri").invoke(Utils.playerState);
                JSONObject context = new JSONObject().put("uri", track == null ? JSONObject.NULL : track.uri)
                        .put("pageUri", pageUri).put("title", track == null ? JSONObject.NULL : track.title)
                        .put("subtitle", track == null ? JSONObject.NULL : track.artist);
                String serialized = context.toString();
                if (serialized.equals(lastContext)) return;
                lastContext = serialized;
                UITargets.open(id, target, host, original, context, true);
            } catch (Exception error) { logError(error); }
        }
        void close() {
            UITargets.close(id);
            if (original.getParent() instanceof ViewGroup group) group.removeView(original);
            parent.removeView(host);
            original.setVisibility(View.VISIBLE);
            original.setTranslationZ(translationZ);
            parent.addView(original, Math.min(index, parent.getChildCount()), originalParams);
        }
    }
    @Override public Object handle(String command, Object[] args) { return null; }
}
