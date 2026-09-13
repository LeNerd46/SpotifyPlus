package com.lenerd46.spotifyplus.hooks;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.FrameLayout;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.AspectRatioFrameLayout;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lenerd46.spotifyplus.References;
import com.lenerd46.spotifyplus.SpotifyTrack;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class AnimatedAlbumArtwork extends SpotifyHook {
    private static final int TAG_OVERLAY = 0x53504C55;
    private static final int TAG_TEXTURE = 0x53505458;
    private static final int TAG_TRACKER = 0x53505452;
    private static final int TAG_ARF = 0x53504152;
    private static final int TAG_LAST_URI = 0x53504C54;

    private static final Object PLAYER_LOCK = new Object();
    private static volatile ExoPlayer activePlayer;
    private static final OkHttpClient ARTWORK_CLIENT = new OkHttpClient();
    private volatile FrameLayout activeOverlay;
    private volatile TextureView activeTextureView;
    private static final java.util.concurrent.atomic.AtomicInteger GEN = new java.util.concurrent.atomic.AtomicInteger();

    @Override
    protected void hook() {
        new ImmersiveAnimatedArtwork().init(lpparm, bridge);
        XposedBridge.hookAllMethods(LayoutInflater.class, "inflate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!(param.args[0] instanceof Integer))
                    return;

                LayoutInflater inflater = (LayoutInflater) param.thisObject;
                Context ctx = inflater.getContext();

                int layoutId = (Integer) param.args[0];
                String layoutName;
                try {
                    layoutName = ctx.getResources().getResourceName(layoutId);
                } catch (Throwable t) {
                    return;
                }

                if (!layoutName.endsWith("layout/square_cover_art_content"))
                    return;

                if (layoutName.endsWith("layout/square_cover_art_content")) {
                    View root = (View) param.getResult();

                    root.post(() -> {
                        View image = findByIdName(root, "com.spotify.music:id/image");
                        if (!(image instanceof View)) {
                            XposedBridge.log("[SpotifyPlus] cover image not found");
                            return;
                        }

                        if (!(image.getParent() instanceof ViewGroup)) {
                            XposedBridge.log("[SpotifyPlus] cover parent not ViewGroup");
                            return;
                        }

                        SharedPreferences prefs = References.getPreferences();
                        if (!prefs.getBoolean("experiment_animated_art", true)
                                || prefs.getBoolean(ImmersiveAnimatedArtwork.PREFERENCE, false))
                            return;

                        attachOverlay((ViewGroup) image.getParent(), image);

                        // View overlay = (View) image.getTag(TAG_OVERLAY);
                        // overlay.setBackgroundColor(0x55FF0000); // translucent red
                    });
                }

            }
        });
    }

    private static View findByIdName(View root, String idName) {
        if (root == null)
            return null;
        int id = root.getId();
        if (id != View.NO_ID) {
            try {
                if (idName.equals(root.getContext().getResources().getResourceName(id)))
                    return root;
            } catch (Throwable ignored) {
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View v = findByIdName(vg.getChildAt(i), idName);
                if (v != null)
                    return v;
            }
        }
        return null;
    }

    @OptIn(markerClass = UnstableApi.class)
    private void attachOverlay(ViewGroup parent, View image) {
        SpotifyTrack track = References.getTrackTitle(lpparm, bridge);
        if (track == null) {
            XposedBridge.log("[SpotifyPlus] Failed to get current track");
            return;
        }

        Object existing = image.getTag(TAG_OVERLAY);
        if (existing instanceof View)
            return;

        FrameLayout overlay = new FrameLayout(parent.getContext());
        overlay.setClipToPadding(false);
        overlay.setClipChildren(false);

        overlay.setClickable(false);
        overlay.setFocusable(false);
        overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        Context context = parent.getContext();

        AspectRatioFrameLayout arf = new AspectRatioFrameLayout(context);
        arf.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
        arf.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextureView textureView = new TextureView(context);
        textureView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        arf.addView(textureView);
        overlay.addView(arf);

        overlay.setTag(TAG_ARF, arf);
        overlay.setTag(TAG_TEXTURE, textureView);

        int idx = parent.indexOfChild(image);
        parent.addView(overlay, Math.min(idx + 1, parent.getChildCount()), new ViewGroup.LayoutParams(1, 1));

        Runnable sync = () -> {
            int w = image.getWidth();
            int h = image.getHeight();
            if (w <= 0 || h <= 0)
                return;

            ViewGroup.LayoutParams p = overlay.getLayoutParams();
            if (p.width != w || p.height != h) {
                p.width = w;
                p.height = h;
                overlay.setLayoutParams(p);
            }

            overlay.setX(image.getX());
            overlay.setY(image.getY());

            overlay.setScaleX(image.getScaleX());
            overlay.setScaleY(image.getScaleY());
            overlay.setPivotX(image.getPivotX());
            overlay.setPivotY(image.getPivotY());
            overlay.setRotation(image.getRotation());
            overlay.setAlpha(image.getAlpha());

            overlay.invalidateOutline();
        };

        image.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (!image.getViewTreeObserver().isAlive())
                    return true;
                image.getViewTreeObserver().removeOnPreDrawListener(this);
                sync.run();
                return true;
            }
        });

        image.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orR, ob) -> sync.run());
        parent.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orR, ob) -> sync.run());

        image.post(() -> {
            float radius = image.getWidth() * 0.02f;
            overlay.setClipToOutline(true);
            overlay.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            overlay.invalidateOutline();
        });

        image.setTag(TAG_OVERLAY, overlay);

        Runnable tryActivate = () -> maybeActivate(image, overlay, textureView);
        image.postDelayed(tryActivate, 60);
        image.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orR, ob) -> image.post(tryActivate));

        startHeartbeat(image, overlay, textureView);
    }

    @OptIn(markerClass = UnstableApi.class)
    private ExoPlayer getOrCreatePlayer(Context context, TextureView textureView, FrameLayout overlay) {
        synchronized (PLAYER_LOCK) {
            if (activePlayer == null) {
                Context application = context.getApplicationContext();
                DefaultTrackSelector selector = new DefaultTrackSelector(application);
                selector.setParameters(selector.buildUponParameters()
                        .setMaxVideoSize(1280, 1280).setMaxVideoBitrate(2_500_000));
                activePlayer = new ExoPlayer.Builder(application)
                        .setTrackSelector(selector)
                        .setLoadControl(new DefaultLoadControl.Builder()
                                .setBufferDurationsMs(2000, 6000, 500, 1000)
                                .setTargetBufferBytes(8 * 1024 * 1024)
                                .setPrioritizeTimeOverSizeThresholds(false).build())
                        .build();
                activePlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
                activePlayer.setVolume(0f);
                activePlayer.setPlayWhenReady(true);

                activePlayer.addListener(new Player.Listener() {
                    @Override
                    public void onPlayerError(PlaybackException error) {
                        XposedBridge.log("[SpotifyPlus] ExoPlayer error: " + error);
                    }

                    @Override
                    public void onVideoSizeChanged(VideoSize vs) {
                        FrameLayout o = activeOverlay;
                        if (o == null || vs.height <= 0 || vs.width <= 0)
                            return;

                        Object tag = o.getTag(TAG_ARF);
                        if (!(tag instanceof AspectRatioFrameLayout))
                            return;

                        float aspect = (vs.width * vs.pixelWidthHeightRatio) / (float) vs.height;
                        ((AspectRatioFrameLayout) tag).setAspectRatio(aspect);
                    }
                });
            }

            if (activeTextureView != null && activeTextureView != textureView) {
                try {
                    activePlayer.clearVideoSurface();
                } catch (Throwable ignored) {
                }
            }

            if (activeOverlay != null && activeOverlay != overlay) {
                // A returning carousel page must reload its own track, not the last owner's video.
                activeOverlay.setTag(TAG_LAST_URI, null);
            }
            activeOverlay = overlay;
            if (activeTextureView != textureView) {
                activeTextureView = textureView;
                activePlayer.setVideoTextureView(textureView);
            }

            return activePlayer;
        }
    }

    private static boolean isCenteredAndMostlyVisible(View v) {
        if (!v.isShown())
            return false;

        Rect r = new Rect();
        if (!v.getGlobalVisibleRect(r))
            return false;

        int vw = v.getWidth(), vh = v.getHeight();
        if (vw <= 0 || vh <= 0)
            return false;

        float visibleArea = (r.width() * r.height()) / (float) (vw * vh);
        if (visibleArea < 0.85f)
            return false; // require mostly visible

        View root = v.getRootView();
        int screenCx = root.getWidth() / 2;
        int viewCx = r.centerX();

        return Math.abs(viewCx - screenCx) < (vw * 0.20f);
    }

    private void maybeActivate(View image, FrameLayout overlay, TextureView tv) {
        if (!isCenteredAndMostlyVisible(image) || image.getWindowVisibility() != View.VISIBLE
                || !References.getPreferences().getBoolean("experiment_animated_art", true)
                || References.getPreferences().getBoolean(ImmersiveAnimatedArtwork.PREFERENCE, false)) {
            releasePlayer(overlay);
            return;
        }

        SpotifyTrack track = References.getTrackTitle(lpparm, bridge);
        if (track == null)
            return;

        Object last = overlay.getTag(TAG_LAST_URI);
        String lastUri = (last instanceof String) ? (String) last : null;

        if (track.uri != null && track.uri.equals(lastUri)) {
            getOrCreatePlayer(image.getContext(), tv, overlay); // still ensure surface is attached
            return;
        }

        overlay.setTag(TAG_LAST_URI, track.uri);

        ExoPlayer p = getOrCreatePlayer(image.getContext(), tv, overlay);
        overlay.bringToFront();

        int gen = GEN.incrementAndGet();
        ARTWORK_CLIENT.dispatcher().cancelAll();

        try {
            p.stop();
            p.clearMediaItems();
        } catch (Throwable ignored) {
        }

        fetchAndPlayForActive(gen, overlay, track);
    }

    private void fetchAndPlayForActive(int gen, FrameLayout overlay, SpotifyTrack track) {
        OkHttpClient client = ARTWORK_CLIENT;

        final String term = track.title + " " + track.artist;
        String enc = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ? URLEncoder.encode(term, StandardCharsets.UTF_8)
                : URLEncoder.encode(term);

        Request request = new Request.Builder()
                .url("https://itunes.apple.com/search?country=us&media=music&limit=1&term=" + enc).get().build();

        new Thread(() -> {
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null)
                    return;

                String json = response.body().string();
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                String url = root.get("results").getAsJsonArray().get(0).getAsJsonObject()
                        .get("collectionViewUrl").getAsString();
                if (GEN.get() != gen) return;

                Request appleMusicRequest = new Request.Builder().url(url).get().build();
                try (Response appleResponse = client.newCall(appleMusicRequest).execute()) {
                    if (!appleResponse.isSuccessful() || appleResponse.body() == null)
                        return;

                    Document doc = Jsoup.parse(appleResponse.body().string());
                    Element video = doc.selectFirst("amp-ambient-video");
                    if (video == null)
                        return;

                    String src = video.attr("src");
                    new Handler(Looper.getMainLooper()).post(() -> {
                        // stale request? ignore
                        if (GEN.get() != gen)
                            return;
                        if (overlay != activeOverlay)
                            return;

                        if (activePlayer == null)
                            return;
                        activePlayer.setMediaItem(MediaItem.fromUri(src));
                        activePlayer.prepare();
                        activePlayer.play();
                    });
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }).start();
    }

    private void startHeartbeat(View image, FrameLayout overlay, TextureView tv) {
        Runnable heartbeat = new Runnable() {
            @Override
            public void run() {
                if (!overlay.isAttachedToWindow()) {
                    releasePlayer(overlay);
                    return;
                }
                maybeActivate(image, overlay, tv);
                overlay.postOnAnimationDelayed(this, 500);
            }
        };
        overlay.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                overlay.removeCallbacks(heartbeat);
                overlay.postOnAnimationDelayed(heartbeat, 500);
            }
            @Override public void onViewDetachedFromWindow(View v) {
                overlay.removeCallbacks(heartbeat);
                releasePlayer(overlay);
            }
        });
        overlay.postOnAnimationDelayed(heartbeat, 500);
    }

    private void releasePlayer(FrameLayout owner) {
        synchronized (PLAYER_LOCK) {
            if (activeOverlay != owner) return;
            GEN.incrementAndGet();
            ARTWORK_CLIENT.dispatcher().cancelAll();
            owner.setTag(TAG_LAST_URI, null);
            ExoPlayer player = activePlayer;
            activePlayer = null;
            activeOverlay = null;
            activeTextureView = null;
            if (player != null) player.release();
        }
    }

}
