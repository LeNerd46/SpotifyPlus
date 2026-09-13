package com.lenerd46.spotifyplus.hooks;

import com.lenerd46.spotifyplus.theme.ImmersiveArtworkColors;
import com.lenerd46.spotifyplus.theme.ImmersiveArtworkGeometry;

import android.graphics.LinearGradient;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.os.Handler;
import android.os.Looper;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lenerd46.spotifyplus.References;
import com.lenerd46.spotifyplus.SpotifyTrack;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

@OptIn(markerClass = UnstableApi.class)
public final class ImmersiveAnimatedArtwork extends SpotifyHook {
    public static final String PREFERENCE = "experiment_immersive_animated_art";
    private static final int TAG = 0x53504941;
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(20)).build();
    private static final java.util.concurrent.ExecutorService LOOKUPS = Executors.newFixedThreadPool(2);
    private static final Map<String, String> CACHE = new LinkedHashMap<>();

    private static boolean enabled() {
        android.content.SharedPreferences prefs = References.getPreferences();
        return prefs != null && prefs.getBoolean(PREFERENCE, false) && prefs.getBoolean("experiment_animated_art", true);
    }

    @Override
    protected void hook() {
        XposedBridge.hookAllMethods(LayoutInflater.class, "inflate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!enabled() || !(param.args[0] instanceof Integer) || !(param.getResult() instanceof View)) return;
                View root = (View) param.getResult();
                String name;
                try {
                    name = root.getResources().getResourceEntryName((Integer) param.args[0]);
                } catch (Exception ignored) {
                    return;
                }
                boolean album = name.equals("expanded_header") || name.equals("creative_work_header_layout");
                if (!album && !name.equals("square_cover_art_content")) return;
                root.post(() -> {
                    if (root.isAttachedToWindow()) attach(root, album);
                    else root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(View view) {
                            view.removeOnAttachStateChangeListener(this);
                            view.post(() -> attach(view, album));
                        }

                        @Override
                        public void onViewDetachedFromWindow(View view) {
                        }
                    });
                });
            }
        });
    }

    private void attach(View root, boolean album) {
        View image = find(root, album ? "cwp_header_media_slot" : "image");
        if (image == null && album) image = find(root, "artwork_with_shadow");
        if (image == null && album) image = find(root, "cwp_header_artwork");
        if (image == null && album) image = find(root, "encore_artwork");
        if (image == null || image.getTag(TAG) != null) return;

        ViewGroup host = null;
        if (album) {
            View background = find(root, "cwp_header_artwork_background");
            if (background == null) background = find(root, "artwork_background");
            if (background != null && background.getParent() instanceof ViewGroup)
                host = (ViewGroup) background.getParent();
        } else {
            for (android.view.ViewParent p = image.getParent(); p instanceof ViewGroup; p = p.getParent()) {
                ViewGroup group = (ViewGroup) p;
                if (group.getClass().getName().equals("com.spotify.nowplaying.scroll.view.PeekScrollView") || group.getClass().getName().equals("com.spotify.nowplaying.scroll.view.NowPlayingScrollView")) {
                    if (group.getParent() instanceof ViewGroup) host = (ViewGroup) group.getParent();
                    break;
                }
            }
        }
        if (host == null) return;
        Surface surface = new Surface(host, image, album);
        image.setTag(TAG, surface);
        surface.start();
    }

    private static View find(View root, String name) {
        int id = root.getResources().getIdentifier(name, "id", "com.spotify.music");
        return id == 0 ? null : root.findViewById(id);
    }

    private static String text(View view) {
        if (view instanceof TextView) return ((TextView) view).getText().toString().trim();
        if (view != null && view.getAccessibilityNodeProvider() != null) {
            String value = virtualText(view.getAccessibilityNodeProvider(), AccessibilityNodeProvider.HOST_VIEW_ID, new java.util.HashSet<>(), 0);
            if (!value.isEmpty()) return value;
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                String value = text(group.getChildAt(i));
                if (!value.isEmpty()) return value;
            }
        }

        return "";
    }

    private static String virtualText(AccessibilityNodeProvider provider, int id, java.util.Set<Integer> visited, int depth) {
        if (depth > 8 || visited.size() >= 64 || !visited.add(id)) return "";
        AccessibilityNodeInfo node = provider.createAccessibilityNodeInfo(id);
        if (node == null) return "";

        try {
            if (node.getText() != null && node.getText().length() > 0) return node.getText().toString().trim();
            for (int i = 0; i < node.getChildCount(); i++) {
                // Query local virtual nodes directly; getChild() requires a remote accessibility connection.
                long child = (Long) XposedHelpers.callMethod(node, "getChildId", i);
                String value = virtualText(provider, (int) (child >> 32), visited, depth + 1);
                if (!value.isEmpty()) return value;
            }
        } catch (RuntimeException error) {
            XposedBridge.log("[SpotifyPlus] Album creator semantics: " + error);
        } finally {
            node.recycle();
        }

        return "";
    }

    private static Drawable artworkDrawable(View view) {
        if (view instanceof ImageView) return ((ImageView) view).getDrawable();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Drawable drawable = artworkDrawable(group.getChildAt(i));
                if (drawable != null) return drawable;
            }
        }
        return null;
    }

    private final class Surface implements View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
        final ViewGroup host;
        final View image;
        final boolean album;
        final FrameLayout layer;
        final TextureView texture;
        final View fade;
        float videoAspect = 1f;
        final Handler main = new Handler(Looper.getMainLooper());
        boolean attached;
        final Runnable removeLayer = this::removeDetachedLayer;
        float appliedAspect;
        int appliedBackground;
        int artworkColor = 0xff121212;
        final Map<View, Drawable> backgrounds = new IdentityHashMap<>();
        final Map<View, Float> alphas = new IdentityHashMap<>();
        ExoPlayer player;
        String key = "";
        int generation;
        boolean ready;
        long retryAt;
        String retryKey = "";
        int lookupAttempts;
        View mediaSlot;
        int originalMediaHeight;
        final Runnable heartbeat = this::tick;

        Surface(ViewGroup host, View image, boolean album) {
            this.host = host;
            this.image = image;
            this.album = album;
            layer = new FrameLayout(host.getContext()) {
                final Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);

                @Override
                public void draw(Canvas canvas) {
                    if (!album || getHeight() <= 0) {
                        super.draw(canvas);
                        return;
                    }
                    // Fade the entire decorative layer, including its color fill, into the live page below.
                    int save = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
                    super.draw(canvas);
                    mask.setShader(new LinearGradient(0, getHeight() * .60f, 0, getHeight(), 0xff000000, 0x00000000, Shader.TileMode.CLAMP));
                    mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
                    canvas.drawRect(0, 0, getWidth(), getHeight(), mask);
                    canvas.restoreToCount(save);
                }
            };
            // Spotify clones this header's ConstraintSet when applying window insets.
            layer.setId(View.generateViewId());
            layer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            layer.setClickable(false);
            layer.setAlpha(0f);
            texture = new TextureView(host.getContext());
            layer.addView(texture, new FrameLayout.LayoutParams(-1, -1));
            fade = new View(host.getContext()) {
                final Paint paint = new Paint();

                @Override
                protected void onDraw(Canvas canvas) {
                    // Transparent at the top; opaque at the controls/list boundary.
                    int base = backgroundColor();
                    float artworkBottom = Math.min(getHeight(), texture.getHeight());
                    paint.setShader(new LinearGradient(0, 0, 0, Math.max(1, artworkBottom), new int[]{0x22000000, 0, base & 0xffffff, base | 0xff000000}, new float[]{0, .35f, .66f, 1f}, Shader.TileMode.CLAMP));
                    canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
                }
            };
            layer.addView(fade, new FrameLayout.LayoutParams(-1, -1));
        }

        void start() {
            image.addOnAttachStateChangeListener(this);
            if (image.isAttachedToWindow()) onViewAttachedToWindow(image);
        }

        void removeDetachedLayer() {
            if (!attached && layer.getParent() == host) host.removeView(layer);
        }

        @Override
        public void onViewAttachedToWindow(View view) {
            attached = true;
            main.removeCallbacks(removeLayer);
            if (layer.getParent() == null) host.addView(layer, 0, new ViewGroup.LayoutParams(1, 1));
            host.getViewTreeObserver().addOnPreDrawListener(this);
            host.removeCallbacks(heartbeat);
            host.post(heartbeat);
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            attached = false;
            host.removeCallbacks(heartbeat);
            if (host.getViewTreeObserver().isAlive()) host.getViewTreeObserver().removeOnPreDrawListener(this);
            reset();
            // Removing a sibling here corrupts ViewGroup's in-progress child traversal.
            // Use the main queue (a detached View.post can wait until the next attachment).
            main.post(removeLayer);
        }

        @Override
        public boolean onPreDraw() {
            int width = host.getWidth();
            int height = album ? host.getHeight() : Math.min(host.getHeight(), host.getRootView().getHeight());
            width = Math.min(width, host.getRootView().getWidth());
            if (layer.getWidth() != width || layer.getHeight() != height) {
                layer.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                layer.layout(0, 0, width, height);
            }
            layoutVideo(width, height);
            if (ready) {
                // Native binding and theme hooks may update these while artwork is playing.
                for (Map.Entry<View, Drawable> entry : backgrounds.entrySet()) {
                    View view = entry.getKey();
                    if (view.getBackground() != null) {
                        entry.setValue(view.getBackground());
                        view.setBackground(null);
                    }
                }
                for (View v : alphas.keySet()) if (v.getAlpha() != 0f) v.setAlpha(0f);
            }
            return true;
        }

        int backgroundColor() {
            return artworkColor;
        }

        void sampleArtworkColor() {
            Bitmap sample = null;
            try {
                // After the first frame, sample the actual video. This also supports hardware-backed covers.
                if (player != null && texture.isAvailable()) sample = texture.getBitmap(32, 32);
                Drawable drawable = artworkDrawable(image);
                if (sample == null && drawable != null) {
                    sample = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
                    Rect bounds = new Rect(drawable.getBounds());
                    try {
                        drawable.setBounds(0, 0, 32, 32);
                        drawable.draw(new Canvas(sample));
                    } finally {
                        drawable.setBounds(bounds);
                    }
                }
                if (sample != null) {
                    int[] pixels = new int[1024];
                    sample.getPixels(pixels, 0, 32, 0, 0, 32, 32);
                    artworkColor = ImmersiveArtworkColors.background(pixels);
                }
            } catch (RuntimeException error) {
                XposedBridge.log("[SpotifyPlus] Artwork color sample: " + error);
            } finally {
                if (sample != null) sample.recycle();
            }
        }

        void layoutVideo(int width, int height) {
            if (width <= 0 || height <= 0) return;
            int videoHeight = ImmersiveArtworkGeometry.height(width, height, videoAspect);
            boolean resized = texture.getWidth() != width || texture.getHeight() != videoHeight;
            if (resized) {
                texture.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(videoHeight, View.MeasureSpec.EXACTLY));
                texture.layout(0, 0, width, videoHeight);
            }
            if (resized || appliedAspect != videoAspect) {
                float[] scale = album ? ImmersiveArtworkGeometry.scale(width, videoHeight, videoAspect) : ImmersiveArtworkGeometry.playerScale(width, videoHeight, videoAspect);
                Matrix transform = new Matrix();
                transform.setScale(scale[0], scale[1], width / 2f, videoHeight / 2f);
                texture.setTransform(transform);
                appliedAspect = videoAspect;
                fade.invalidate();
            }
            int base = backgroundColor();
            if (appliedBackground != base) {
                layer.setBackgroundColor(base);
                appliedBackground = base;
                fade.invalidate();
            }
        }

        void tick() {
            if (!image.isAttachedToWindow()) return;
            try {
                Rect visible = new Rect();
                boolean shown = enabled() && host.hasWindowFocus() && image.isShown() && image.getGlobalVisibleRect(visible) && host.getWindowVisibility() == View.VISIBLE;
                if (!album && shown) {
                    int[] location = new int[2];
                    host.getLocationOnScreen(location);
                    shown = Math.abs(visible.centerX() - location[0] - host.getWidth() / 2) < image.getWidth() * .2f;
                }
                if (!shown) {
                    reset();
                    return;
                }
                String title;
                String artist;
                if (album) {
                    title = text(find(host, "cwp_header_title"));
                    if (title.isEmpty()) title = text(find(host, "title"));
                    artist = text(find(host, "cwp_header_creatorsRow"));
                    if (artist.isEmpty()) artist = text(find(host, "creator"));
                } else {
                    SpotifyTrack track = References.getTrackTitle(lpparm, bridge);
                    if (track == null) {
                        reset();
                        return;
                    }
                    title = track.album;
                    artist = track.artist;
                }
                if (title == null || artist == null || title.isEmpty() || artist.isEmpty()) {
                    reset();
                    return;
                }
                String next = title + "\n" + artist;
                if (!next.equals(retryKey)) {
                    retryKey = next;
                    lookupAttempts = 0;
                    retryAt = 0;
                }
                if (next.equals(key) && (player != null || lookupAttempts >= 3 || android.os.SystemClock.uptimeMillis() < retryAt))
                    return;
                reset();
                key = next;
                lookupAttempts++;
                retryAt = Long.MAX_VALUE; // one in-flight request per surface
                sampleArtworkColor();
                if (album) XposedBridge.log("[SpotifyPlus] Album animation lookup: " + next.replace('\n', ' '));
                int token = generation;
                final String albumTitle = title, albumArtist = artist;
                LOOKUPS.execute(() -> {
                    String url = lookup(albumTitle, albumArtist);
                    host.post(() -> {
                        if (generation != token || !image.isAttachedToWindow() || !enabled()) return;
                        retryAt = android.os.SystemClock.uptimeMillis() + 30000;
                        if (!host.hasWindowFocus()) {
                            key = "";
                            return;
                        }
                        if (!url.isEmpty()) {
                            try {
                                play(url);
                            } catch (Exception error) {
                                String failed = key;
                                reset();
                                key = failed;
                                XposedBridge.log("[SpotifyPlus] Immersive artwork player: " + error);
                            }
                        } else if (album)
                            XposedBridge.log("[SpotifyPlus] No album animation found (attempt " + lookupAttempts + "): " + albumTitle);
                    });
                });
            } catch (Throwable error) {
                reset();
                XposedBridge.log("[SpotifyPlus] Immersive artwork: " + error);
            } finally {
                host.postDelayed(heartbeat, 500);
            }
        }

        void play(String url) {
            DefaultTrackSelector selector = new DefaultTrackSelector(host.getContext().getApplicationContext());
            selector.setParameters(selector.buildUponParameters().setMaxVideoSize(1920, 1920).setMaxVideoBitrate(4_000_000));
            player = new ExoPlayer.Builder(host.getContext().getApplicationContext()).setTrackSelector(selector).setLoadControl(new DefaultLoadControl.Builder().setBufferDurationsMs(2000, 6000, 500, 1000).setTargetBufferBytes(8 * 1024 * 1024).build()).build();
            player.setVolume(0);
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
            player.setVideoTextureView(texture);
            player.addListener(new Player.Listener() {
                @Override
                public void onVideoSizeChanged(VideoSize size) {
                    if (size.height > 0 && size.width > 0) {
                        videoAspect = size.width * size.pixelWidthHeightRatio / size.height;
                        layoutVideo(layer.getWidth(), layer.getHeight());
                    }
                }

                @Override
                public void onRenderedFirstFrame() {
                    reveal();
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    String failed = key;
                    reset();
                    key = failed; // Keep static artwork, do not retry a broken stream every heartbeat.
                    XposedBridge.log("[SpotifyPlus] Immersive artwork playback: " + error.getErrorCodeName());
                }
            });
            player.setMediaItem(MediaItem.fromUri(url));
            player.prepare();
            player.play();
        }

        void reveal() {
            if (ready || player == null) return;
            sampleArtworkColor();
            alphas.put(image, image.getAlpha());
            if (album) {
                mediaSlot = find(host, "cwp_header_media_slot");
                if (mediaSlot == null) mediaSlot = find(host, "artwork_with_shadow");
                if (mediaSlot != null) {
                    // Hide both static and native animated children without collapsing their spacer.
                    alphas.putIfAbsent(mediaSlot, mediaSlot.getAlpha());
                    ViewGroup.LayoutParams params = mediaSlot.getLayoutParams();
                    originalMediaHeight = params.height;
                    params.height = Math.max(mediaSlot.getHeight(), Math.round(host.getRootView().getHeight() * .46f));
                    mediaSlot.setLayoutParams(params);
                }
                View background = find(host, "cwp_header_artwork_background");
                if (background == null) background = find(host, "artwork_background");
                if (background != null) alphas.put(background, background.getAlpha());
                View shadow = find(host, "artwork_shadow");
                if (shadow != null) alphas.put(shadow, shadow.getAlpha());
                if (host.getBackground() != null) backgrounds.put(host, host.getBackground());
            } else {
                for (android.view.ViewParent p = image.getParent(); p instanceof View && p != host; p = p.getParent()) {
                    View view = (View) p;
                    if (view.getBackground() != null) backgrounds.put(view, view.getBackground());
                }
            }
            ready = true;
            if (album) XposedBridge.log("[SpotifyPlus] Album animation displayed");
            onPreDraw();
            layer.setAlpha(1f);
        }

        void reset() {
            generation++;
            key = "";
            ready = false;
            layer.setAlpha(0f);
            if (mediaSlot != null) {
                ViewGroup.LayoutParams params = mediaSlot.getLayoutParams();
                params.height = originalMediaHeight;
                mediaSlot.setLayoutParams(params);
                mediaSlot = null;
            }
            for (Map.Entry<View, Float> entry : alphas.entrySet()) entry.getKey().setAlpha(entry.getValue());
            for (Map.Entry<View, Drawable> entry : backgrounds.entrySet())
                entry.getKey().setBackground(entry.getValue());
            alphas.clear();
            backgrounds.clear();
            if (player != null) {
                player.release();
                player = null;
            }
        }
    }

    private static String lookup(String title, String artist) {
        String key = title + "\n" + artist;
        synchronized (CACHE) {
            if (CACHE.containsKey(key)) return CACHE.get(key);
        }
        String result = "";
        try {
            String term = URLEncoder.encode(title + " " + artist, StandardCharsets.UTF_8.name());
            try (Response response = CLIENT.newCall(new Request.Builder().url("https://itunes.apple.com/search?country=us&entity=album&limit=25&term=" + term).build()).execute()) {
                if (!response.isSuccessful() || response.body() == null) return "";
                JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
                for (JsonElement item : json.getAsJsonArray("results")) {
                    JsonObject match = item.getAsJsonObject();
                    // Never animate a different album just because it was the first search result.
                    if (!normalize(title).equals(normalize(match.get("collectionName").getAsString())) || !normalize(artist).equals(normalize(match.get("artistName").getAsString())))
                        continue;
                    try (Response page = CLIENT.newCall(new Request.Builder().url(match.get("collectionViewUrl").getAsString()).build()).execute()) {
                        if (!page.isSuccessful() || page.body() == null) continue;
                        Element video = Jsoup.parse(page.body().string()).selectFirst("amp-ambient-video");
                        if (video != null && video.attr("src").startsWith("https://")) result = video.attr("src");
                    }
                    if (!result.isEmpty()) break;
                }
            }
            synchronized (CACHE) {
                if (CACHE.size() >= 64) CACHE.remove(CACHE.keySet().iterator().next());
                // A temporary network/metadata miss must not disable this album for the entire session.
                if (!result.isEmpty()) CACHE.put(key, result);
            }
        } catch (Exception error) {
            XposedBridge.log("[SpotifyPlus] Immersive artwork lookup: " + error);
        }
        return result;
    }

    private static String normalize(String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
