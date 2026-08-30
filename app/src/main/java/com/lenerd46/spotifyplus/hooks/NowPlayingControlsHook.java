package com.lenerd46.spotifyplus.hooks;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.lenerd46.spotifyplus.References;
import com.lenerd46.spotifyplus.SpotifyTrack;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NowPlayingControlsHook extends SpotifyHook {
    private static final String PEEK_SCROLL_VIEW = "com.spotify.nowplaying.scroll.view.PeekScrollView";
    private static final int WATCH_TAG = 0x53504C57;
    private static final int TRANSPORT_TAG = 0x53504C54;
    private static final int ACTIONS_TAG = 0x53504C41;
    private final Map<View, LyricsState> states = new WeakHashMap<>();
    private final ExecutorService artworkLoader = Executors.newSingleThreadExecutor();
    private volatile Bitmap cachedArtwork;
    private volatile String cachedArtworkUri;
    private volatile Drawable cachedArtworkSource;

    @Override
    protected void hook() {
        try {
            Class<?> peekScrollView = lpparm.classLoader.loadClass(PEEK_SCROLL_VIEW);
            XposedBridge.hookAllConstructors(peekScrollView, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if(!(param.thisObject instanceof View)) return;
                    watchNowPlayingLayout((View) param.thisObject);
                }
            });
            XposedBridge.log("[SpotifyPlus][NowPlayingControls] Custom Now Playing control layout enabled");
        } catch(Throwable throwable) {
            XposedBridge.log("[SpotifyPlus][NowPlayingControls] Could not enable the custom control layout");
            XposedBridge.log(throwable);
        }
    }

    private void watchNowPlayingLayout(View root) {
        if(root.getTag(WATCH_TAG) != null) return;
        root.setTag(WATCH_TAG, Boolean.TRUE);
        Handler handler = new Handler(Looper.getMainLooper());
        View.OnLayoutChangeListener listener = (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> applyLayout(root);
        root.addOnLayoutChangeListener(listener);
        for(int attempt = 0; attempt < 20; attempt++) handler.postDelayed(() -> applyLayout(root), attempt * 100L);
        Runnable refresh = new Runnable() {
            @Override
            public void run() {
                if(!root.isAttachedToWindow()) return;
                applyLayout(root);
                root.postDelayed(this, 1000L);
            }
        };
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                view.removeCallbacks(refresh);
                view.post(refresh);
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                view.removeCallbacks(refresh);
            }
        });
        root.postDelayed(refresh, 1000L);
    }

    private void applyLayout(View root) {
        try {
            arrangeTransportControls(root);
            arrangeBottomActions(root);
            LyricsState state = states.get(root);
            arrangeLandscape(root, state == null ? state(root) : state);
            if(state != null && state.open) suppressStickyHeader(root, state);
        } catch(Throwable throwable) {
            XposedBridge.log("[SpotifyPlus][NowPlayingControls] Layout pass failed");
            XposedBridge.log(throwable);
        }
    }

    private void arrangeTransportControls(View root) {
        ViewGroup container = findGroup(root, "playback_controls_container");
        if(container == null || container.getTag(TRANSPORT_TAG) != null) return;
        View playPause = find(root, "nowplaying_elements_playpause_button");
        View previous = findByDescription(root, string(root, "np_content_desc_prev"));
        View next = findByDescription(root, string(root, "np_content_desc_next"));
        if(playPause == null || previous == null || next == null || previous.getParent() != container || playPause.getParent() != container || next.getParent() != container) return;
        container.setTag(TRANSPORT_TAG, Boolean.TRUE);
        container.removeAllViews();
        container.setPadding(dp(root, 12), dp(root, 6), dp(root, 12), dp(root, 6));
        container.setBackground(capsule(root, 0x52242424));
        ViewGroup.MarginLayoutParams containerParams = margins(container.getLayoutParams());
        containerParams.height = dp(root, 78);
        containerParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        containerParams.setMargins(dp(root, 40), dp(root, 8), dp(root, 40), dp(root, 8));
        container.setLayoutParams(containerParams);
        LinearLayout row = new LinearLayout(root.getContext());
        row.setGravity(Gravity.CENTER);
        row.setOrientation(LinearLayout.HORIZONTAL);
        container.addView(row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        addCell(row, previous, dp(root, 44));
        addCell(row, playPause, dp(root, 60));
        addCell(row, next, dp(root, 44));
    }

    private void arrangeBottomActions(View root) {
        ViewGroup trackInfo = findGroup(root, "track_info_feedback_container");
        View negativeFeedback = trackInfo == null ? null : findByDescription(trackInfo, string(root, "np_content_desc_ban"));
        if(negativeFeedback != null) negativeFeedback.setVisibility(View.GONE);
        ViewGroup footer = findGroup(root, "revised_template_overlay_footer");
        ViewGroup accessory = findGroup(root, "accessory_row");
        if(footer == null || accessory == null || footer.getTag(ACTIONS_TAG) != null) return;
        View share = findByDescription(accessory, string(root, "np_content_desc_share"));
        View queueIcon = find(root, "queue_button");
        View queue = queueIcon == null ? null : directChildOf(queueIcon, accessory);
        share = share == null ? null : directChildOf(share, accessory);
        if(share == null || queue == null) return;
        detach(share);
        detach(queue);
        LyricsState state = state(root);
        footer.setTag(ACTIONS_TAG, Boolean.TRUE);
        footer.removeAllViews();
        footer.setPadding(0, 0, 0, 0);
        footer.setBackground(null);
        ViewGroup.MarginLayoutParams footerParams = margins(footer.getLayoutParams());
        footerParams.height = dp(root, 68);
        footerParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        footerParams.setMargins(0, dp(root, 12), 0, dp(root, 8));
        footer.setLayoutParams(footerParams);
        FrameLayout holder = new FrameLayout(root.getContext());
        footer.addView(holder, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout row = new LinearLayout(root.getContext());
        row.setGravity(Gravity.CENTER);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(root, 8), dp(root, 4), dp(root, 8), dp(root, 4));
        row.setBackground(capsule(root, 0x52242424));
        holder.addView(row, new FrameLayout.LayoutParams(dp(root, 200), dp(root, 60), Gravity.CENTER));
        ImageView lyrics = new ImageView(root.getContext());
        lyrics.setImageDrawable(lyricsIcon(root));
        lyrics.setContentDescription("Lyrics");
        lyrics.setPadding(dp(root, 12), dp(root, 12), dp(root, 12), dp(root, 12));
        lyrics.setOnClickListener(view -> toggleLyrics(root));
        state.lyricsButton = lyrics;
        state.transport = findGroup(root, "playback_controls_container");
        state.footer = footer;
        state.actionRow = row;
        addCell(row, lyrics, dp(root, 48));
        addCell(row, share, dp(root, 48));
        addCell(row, queue, dp(root, 48));
    }

    private void arrangeLandscape(View root, LyricsState state) {
        if(root.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
            restorePortraitLayout(state);
            captureArtwork(root, state);
            return;
        }
        ViewGroup overlay = findGroup(root, "revised_template_overlay");
        if(overlay == null || root.getWidth() == 0 || root.getHeight() == 0) return;
        loadCurrentArtwork(root, state);
        if(state.landscapePanel == null) {
            FrameLayout panel = new FrameLayout(root.getContext());
            panel.setBackground(panel(root));
            overlay.addView(panel, 0);
            state.landscapePanel = panel;
        }
        if(state.landscapeCover == null) {
            ImageView cover = new ImageView(root.getContext());
            cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setClipToOutline(true);
            cover.setElevation(dp(root, 12));
            cover.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(root, 12));
                }
            });
            overlay.addView(cover, 0);
            state.landscapeCover = cover;
        }
        if(state.landscapeMetadata == null) {
            LinearLayout metadata = new LinearLayout(root.getContext());
            metadata.setOrientation(LinearLayout.VERTICAL);
            metadata.setGravity(Gravity.CENTER);
            TextView title = new TextView(root.getContext());
            title.setTextColor(Color.WHITE);
            title.setTextSize(18f);
            title.setGravity(Gravity.CENTER);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            TextView artist = new TextView(root.getContext());
            artist.setTextColor(0xFFB3B3B3);
            artist.setTextSize(14f);
            artist.setGravity(Gravity.CENTER);
            artist.setSingleLine(true);
            artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
            metadata.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            metadata.addView(artist, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            overlay.addView(metadata);
            state.landscapeMetadata = metadata;
            state.landscapeTitle = title;
            state.landscapeArtist = artist;
        }
        if(cachedArtwork != null && state.landscapeCover.getDrawable() == null) state.landscapeCover.setImageBitmap(cachedArtwork);
        int coverSize = Math.min(dp(root, 252), root.getHeight() - dp(root, 76));
        ViewGroup.LayoutParams coverParams = state.landscapeCover.getLayoutParams();
        coverParams.width = coverSize;
        coverParams.height = coverSize;
        state.landscapeCover.setLayoutParams(coverParams);
        state.landscapeCover.setX(dp(root, 30));
        float coverTop = Math.max(dp(root, 34), (root.getHeight() - coverSize - dp(root, 58)) / 2f);
        state.landscapeCover.setY(coverTop);
        state.landscapeCover.setVisibility(state.open ? View.INVISIBLE : View.VISIBLE);
        ViewGroup.LayoutParams metadataParams = state.landscapeMetadata.getLayoutParams();
        metadataParams.width = coverSize;
        metadataParams.height = dp(root, 54);
        state.landscapeMetadata.setLayoutParams(metadataParams);
        state.landscapeMetadata.setX(dp(root, 30));
        state.landscapeMetadata.setY(coverTop + coverSize + dp(root, 8));
        state.landscapeMetadata.setVisibility(state.open ? View.INVISIBLE : View.VISIBLE);
        int rightStart = Math.max(coverSize + dp(root, 72), Math.round(root.getWidth() * 0.42f));
        int rightWidth = root.getWidth() - rightStart - dp(root, 28);
        View nativeTitle = find(root, "track_info_view_title");
        View nativeArtist = find(root, "track_info_view_subtitle");
        syncMetadata(state.landscapeTitle, nativeTitle);
        syncMetadata(state.landscapeArtist, nativeArtist);
        hideInLandscape(state, nativeTitle);
        hideInLandscape(state, nativeArtist);
        ViewGroup.LayoutParams panelParams = state.landscapePanel.getLayoutParams();
        panelParams.width = rightWidth + dp(root, 24);
        panelParams.height = Math.max(dp(root, 248), root.getHeight() - dp(root, 126));
        state.landscapePanel.setLayoutParams(panelParams);
        state.landscapePanel.setX(rightStart - dp(root, 12));
        state.landscapePanel.setY(dp(root, 92));
        state.landscapePanel.setVisibility(state.open ? View.INVISIBLE : View.VISIBLE);
        moveIntoPane(root, find(root, "lyrics_element"), state, rightStart, dp(root, 108), rightWidth - dp(root, 64), dp(root, 50));
        moveIntoPane(root, find(root, "track_info_feedback_container"), state, rightStart + rightWidth - dp(root, 56), dp(root, 104), dp(root, 56), dp(root, 56));
        moveIntoPane(root, find(root, "track_seekbar"), state, rightStart, dp(root, 158), rightWidth, dp(root, 48));
        int controlsWidth = Math.min(rightWidth, dp(root, 430));
        int controlsLeft = rightStart + Math.max(0, (rightWidth - controlsWidth) / 2);
        moveIntoPane(root, state.transport, state, controlsLeft, dp(root, 210), controlsWidth, dp(root, 70));
        moveIntoPane(root, state.footer, state, controlsLeft, dp(root, 282), controlsWidth, dp(root, 58));
        if(state.transport != null) state.transport.setBackground(null);
        if(state.actionRow != null) state.actionRow.setBackground(null);
    }

    private void moveIntoPane(View root, View view, LyricsState state, int desiredLeft, int desiredTop, int width, int height) {
        if(view == null || view.getWidth() == 0) return;
        if(!state.originalWidths.containsKey(view)) state.originalWidths.put(view, view.getLayoutParams().width);
        if(!state.originalHeights.containsKey(view)) state.originalHeights.put(view, view.getLayoutParams().height);
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = width;
        if(height >= 0) params.height = height;
        view.setLayoutParams(params);
        int[] rootLocation = new int[2];
        int[] viewLocation = new int[2];
        root.getLocationInWindow(rootLocation);
        view.getLocationInWindow(viewLocation);
        float baseLeft = viewLocation[0] - rootLocation[0] - view.getTranslationX();
        float baseTop = viewLocation[1] - rootLocation[1] - view.getTranslationY();
        view.setTranslationX(desiredLeft - baseLeft);
        if(desiredTop >= 0) view.setTranslationY(desiredTop - baseTop);
    }

    private void restorePortraitLayout(LyricsState state) {
        if(state == null) return;
        if(state.landscapeCover != null) state.landscapeCover.setVisibility(View.GONE);
        if(state.landscapeMetadata != null) state.landscapeMetadata.setVisibility(View.GONE);
        if(state.landscapePanel != null) state.landscapePanel.setVisibility(View.GONE);
        if(state.transport != null) state.transport.setBackground(capsule(state.transport, 0x52242424));
        if(state.actionRow != null) state.actionRow.setBackground(capsule(state.actionRow, 0x52242424));
        for(Map.Entry<View, Integer> entry : state.originalVisibilities.entrySet()) entry.getKey().setVisibility(entry.getValue());
        state.originalVisibilities.clear();
        for(Map.Entry<View, Integer> entry : state.originalWidths.entrySet()) {
            View view = entry.getKey();
            if(view.getLayoutParams() != null) {
                ViewGroup.LayoutParams params = view.getLayoutParams();
                params.width = entry.getValue();
                Integer height = state.originalHeights.get(view);
                if(height != null) params.height = height;
                view.setLayoutParams(params);
            }
            view.setTranslationX(0f);
            view.setTranslationY(0f);
        }
        state.originalWidths.clear();
        state.originalHeights.clear();
    }

    private void hideInLandscape(LyricsState state, View view) {
        if(view == null) return;
        if(!state.originalVisibilities.containsKey(view)) state.originalVisibilities.put(view, view.getVisibility());
        view.setVisibility(View.INVISIBLE);
    }

    private void syncMetadata(TextView target, View source) {
        TextView sourceText = findTextView(source);
        if(target != null && sourceText != null) target.setText(sourceText.getText());
    }

    private TextView findTextView(View view) {
        if(view instanceof TextView) return (TextView)view;
        if(!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup)view;
        for(int index = 0; index < group.getChildCount(); index++) {
            TextView text = findTextView(group.getChildAt(index));
            if(text != null) return text;
        }
        return null;
    }

    private void captureArtwork(View root, LyricsState state) {
        View visibleArtwork = findArtwork(find(root, "track_carousel"), root);
        ImageView artwork = visibleArtwork instanceof ImageView ? (ImageView)visibleArtwork : cachedArtwork == null ? findArtworkImage(find(root, "track_carousel")) : null;
        if(artwork == null || artwork.getDrawable() == null) return;
        Drawable source = artwork.getDrawable();
        if(source == cachedArtworkSource) return;
        Bitmap snapshot = drawableBitmap(source, artwork.getWidth(), artwork.getHeight());
        if(snapshot == null) return;
        cachedArtworkSource = source;
        cachedArtwork = snapshot;
        if(state.landscapeCover != null) state.landscapeCover.setImageBitmap(snapshot);
    }

    private void loadCurrentArtwork(View root, LyricsState state) {
        SpotifyTrack track = References.getTrackTitle(lpparm, bridge);
        if(track == null || track.uri == null || track.imageId == null || track.imageId.isEmpty() || track.uri.equals(state.landscapeArtworkUri) || track.uri.equals(cachedArtworkUri)) return;
        state.landscapeArtworkUri = track.uri;
        String requestedUri = track.uri;
        String artworkUrl = track.imageId.startsWith("http") ? track.imageId : "https://i.scdn.co/image/" + track.imageId;
        artworkLoader.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection)new URL(artworkUrl).openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                try(InputStream input = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if(bitmap == null) return;
                    root.post(() -> {
                        if(!requestedUri.equals(state.landscapeArtworkUri) || state.landscapeCover == null) return;
                        cachedArtwork = bitmap;
                        cachedArtworkUri = requestedUri;
                        state.landscapeCover.setImageBitmap(bitmap);
                    });
                }
            } catch(Throwable throwable) {
                XposedBridge.log("[SpotifyPlus][NowPlayingControls] Could not load landscape artwork");
                XposedBridge.log(throwable);
            } finally {
                if(connection != null) connection.disconnect();
            }
        });
    }

    private Bitmap drawableBitmap(Drawable drawable, int viewWidth, int viewHeight) {
        if(drawable instanceof BitmapDrawable && ((BitmapDrawable)drawable).getBitmap() != null) return ((BitmapDrawable)drawable).getBitmap().copy(Bitmap.Config.ARGB_8888, false);
        int width = viewWidth > 0 ? viewWidth : drawable.getIntrinsicWidth();
        int height = viewHeight > 0 ? viewHeight : drawable.getIntrinsicHeight();
        if(width <= 0 || height <= 0) return null;
        float scale = Math.min(1f, 768f / Math.max(width, height));
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Rect originalBounds = new Rect(drawable.getBounds());
        drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        drawable.draw(canvas);
        drawable.setBounds(originalBounds);
        return bitmap;
    }

    private ImageView findArtworkImage(View view) {
        if(view == null) return null;
        ImageView best = view instanceof ImageView && ((ImageView)view).getDrawable() != null ? (ImageView)view : null;
        long bestArea = best == null ? 0 : (long)Math.max(best.getWidth(), best.getDrawable().getIntrinsicWidth()) * Math.max(best.getHeight(), best.getDrawable().getIntrinsicHeight());
        if(!(view instanceof ViewGroup)) return best;
        ViewGroup group = (ViewGroup)view;
        for(int index = 0; index < group.getChildCount(); index++) {
            ImageView candidate = findArtworkImage(group.getChildAt(index));
            if(candidate == null) continue;
            long area = (long)Math.max(candidate.getWidth(), candidate.getDrawable().getIntrinsicWidth()) * Math.max(candidate.getHeight(), candidate.getDrawable().getIntrinsicHeight());
            if(area > bestArea) {
                best = candidate;
                bestArea = area;
            }
        }
        return best;
    }

    private LyricsState state(View root) {
        LyricsState existing = states.get(root);
        if(existing != null) return existing;
        LyricsState created = new LyricsState();
        created.hideControls = () -> setControlVisibility(created, false, true);
        states.put(root, created);
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                if(!created.open) return;
                created.open = false;
                created.handler.removeCallbacks(created.hideControls);
                if(created.artwork != null) created.artwork.setAlpha(1f);
                BeautifulLyricsHook.removeEmbedded(false);
            }
        });
        return created;
    }

    private void toggleLyrics(View root) {
        LyricsState state = state(root);
        if(state.transitioning) return;
        if(state.open) {
            closeLyrics(root, true);
            return;
        }
        Activity activity = References.currentActivity;
        ViewGroup overlay = findGroup(root, "revised_template_overlay");
        if(activity == null || overlay == null) return;
        boolean opened = BeautifulLyricsHook.showEmbedded(activity, overlay, () -> closeLyrics(root, true), () -> showControls(root));
        if(!opened) return;
        state.open = true;
        hideForLyrics(state, find(root, "player_overlay_header"));
        hideForLyrics(state, find(root, "lyrics_element"));
        hideForLyrics(state, find(root, "buttons_scroll_bar"));
        hideForLyrics(state, find(root, "track_info_feedback_container"));
        hideForLyrics(state, find(root, "track_seekbar"));
        suppressStickyHeader(root, state);
        if(root.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) setControlVisibility(state, false, false);
        else showControls(root);
        animateArtworkIntoLyrics(root, state);
    }

    private void closeLyrics(View root, boolean animated) {
        LyricsState state = state(root);
        if(!state.open || state.transitioning) return;
        state.open = false;
        state.handler.removeCallbacks(state.hideControls);
        setControlVisibility(state, true, false);
        restoreNativeViews(state);
        Runnable finish = () -> BeautifulLyricsHook.removeEmbedded(false);
        if(!animated || !animateArtworkOutOfLyrics(root, state, finish)) finish.run();
    }

    private void restoreNativeViews(LyricsState state) {
        for(Map.Entry<View, Integer> entry : state.hiddenViews.entrySet()) {
            View view = entry.getKey();
            view.animate().cancel();
            view.setVisibility(entry.getValue());
            if(entry.getValue() == View.VISIBLE) {
                view.setAlpha(0f);
                view.animate().alpha(1f).setDuration(220).start();
            } else view.setAlpha(1f);
        }
        state.hiddenViews.clear();
    }

    private void animateArtworkIntoLyrics(View root, LyricsState state) {
        ViewGroup overlay = findGroup(root, "revised_template_overlay");
        View artwork = findArtwork(find(root, "track_carousel"), root);
        ImageView cover = BeautifulLyricsHook.getEmbeddedCover();
        if(overlay == null || artwork == null || cover == null || artwork.getWidth() == 0 || artwork.getHeight() == 0) {
            return;
        }
        state.transitioning = true;
        state.artwork = artwork;
        state.embeddedCover = cover;
        cover.setAlpha(0f);
        overlay.post(() -> {
            if(!state.open || !artwork.isAttachedToWindow() || !cover.isAttachedToWindow()) {
                state.transitioning = false;
                cover.setAlpha(1f);
                return;
            }
            ImageView snapshot = artworkSnapshot(artwork);
            if(snapshot == null) {
                state.transitioning = false;
                cover.setAlpha(1f);
                return;
            }
            int[] overlayLocation = new int[2];
            int[] artworkLocation = new int[2];
            int[] coverLocation = new int[2];
            overlay.getLocationInWindow(overlayLocation);
            artwork.getLocationInWindow(artworkLocation);
            cover.getLocationInWindow(coverLocation);
            overlay.addView(snapshot, new ViewGroup.LayoutParams(artwork.getWidth(), artwork.getHeight()));
            snapshot.setPivotX(0f);
            snapshot.setPivotY(0f);
            snapshot.setX(artworkLocation[0] - overlayLocation[0]);
            snapshot.setY(artworkLocation[1] - overlayLocation[1]);
            snapshot.setElevation(dp(root, 32));
            artwork.setAlpha(0f);
            float targetScaleX = cover.getWidth() / (float) artwork.getWidth();
            float targetScaleY = cover.getHeight() / (float) artwork.getHeight();
            snapshot.animate().x(coverLocation[0] - overlayLocation[0]).y(coverLocation[1] - overlayLocation[1]).scaleX(targetScaleX).scaleY(targetScaleY).setDuration(460).setInterpolator(new DecelerateInterpolator()).withLayer().withEndAction(() -> {
                detach(snapshot);
                cover.setAlpha(1f);
                state.transitioning = false;
            }).start();
        });
    }

    private boolean animateArtworkOutOfLyrics(View root, LyricsState state, Runnable finish) {
        ViewGroup overlay = findGroup(root, "revised_template_overlay");
        View artwork = state.artwork;
        ImageView cover = state.embeddedCover;
        if(overlay == null || artwork == null || cover == null || artwork.getWidth() == 0 || artwork.getHeight() == 0 || !cover.isAttachedToWindow()) return false;
        ImageView snapshot = artworkSnapshot(cover);
        if(snapshot == null) return false;
        state.transitioning = true;
        int[] overlayLocation = new int[2];
        int[] artworkLocation = new int[2];
        int[] coverLocation = new int[2];
        overlay.getLocationInWindow(overlayLocation);
        artwork.getLocationInWindow(artworkLocation);
        cover.getLocationInWindow(coverLocation);
        overlay.addView(snapshot, new ViewGroup.LayoutParams(artwork.getWidth(), artwork.getHeight()));
        snapshot.setPivotX(0f);
        snapshot.setPivotY(0f);
        snapshot.setX(coverLocation[0] - overlayLocation[0]);
        snapshot.setY(coverLocation[1] - overlayLocation[1]);
        snapshot.setScaleX(cover.getWidth() / (float) artwork.getWidth());
        snapshot.setScaleY(cover.getHeight() / (float) artwork.getHeight());
        snapshot.setElevation(dp(root, 32));
        cover.setAlpha(0f);
        snapshot.animate().x(artworkLocation[0] - overlayLocation[0]).y(artworkLocation[1] - overlayLocation[1]).scaleX(1f).scaleY(1f).setDuration(460).setInterpolator(new DecelerateInterpolator()).withLayer().withEndAction(() -> {
            detach(snapshot);
            artwork.setAlpha(1f);
            state.artwork = null;
            state.embeddedCover = null;
            state.transitioning = false;
            finish.run();
        }).start();
        return true;
    }

    private ImageView artworkSnapshot(View artwork) {
        try {
            float bitmapScale = Math.min(1f, 512f / Math.max(artwork.getWidth(), artwork.getHeight()));
            int bitmapWidth = Math.max(1, Math.round(artwork.getWidth() * bitmapScale));
            int bitmapHeight = Math.max(1, Math.round(artwork.getHeight() * bitmapScale));
            Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(bitmapWidth / (float) artwork.getWidth(), bitmapHeight / (float) artwork.getHeight());
            artwork.draw(canvas);
            ImageView snapshot = new ImageView(artwork.getContext());
            snapshot.setImageBitmap(bitmap);
            snapshot.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return snapshot;
        } catch(Throwable throwable) {
            XposedBridge.log("[SpotifyPlus][NowPlayingControls] Could not capture the Now Playing artwork");
            XposedBridge.log(throwable);
            return null;
        }
    }

    private View findArtwork(View view, View root) {
        if(view == null) return null;
        View best = null;
        long bestArea = 0;
        if(view instanceof ImageView && view.getVisibility() == View.VISIBLE && view.getWidth() >= dp(root, 160) && view.getHeight() >= dp(root, 160)) {
            float ratio = view.getWidth() / (float) view.getHeight();
            if(ratio > 0.8f && ratio < 1.2f) {
                best = view;
                bestArea = visibleArea(view);
            }
        }
        if(!(view instanceof ViewGroup)) return best;
        ViewGroup group = (ViewGroup) view;
        for(int index = 0; index < group.getChildCount(); index++) {
            View candidate = findArtwork(group.getChildAt(index), root);
            if(candidate == null) continue;
            long area = visibleArea(candidate);
            if(area > bestArea) {
                best = candidate;
                bestArea = area;
            }
        }
        return best;
    }

    private long visibleArea(View view) {
        Rect visibleBounds = new Rect();
        return view.isShown() && view.getGlobalVisibleRect(visibleBounds) ? (long) visibleBounds.width() * visibleBounds.height() : 0;
    }

    private void hideForLyrics(LyricsState state, View view) {
        if(view == null || state.hiddenViews.containsKey(view)) return;
        state.hiddenViews.put(view, view.getVisibility());
        if(view.getVisibility() != View.VISIBLE) return;
        view.animate().alpha(0f).setDuration(180).withEndAction(() -> {
            if(state.open) view.setVisibility(View.INVISIBLE);
        }).start();
    }

    private void suppressStickyHeader(View root, LyricsState state) {
        View hierarchyRoot = root;
        while(hierarchyRoot.getParent() instanceof View) hierarchyRoot = (View) hierarchyRoot.getParent();
        View stickyHeader = find(hierarchyRoot, "revised_template_sticky_header");
        if(stickyHeader == null) return;
        if(!state.hiddenViews.containsKey(stickyHeader)) state.hiddenViews.put(stickyHeader, stickyHeader.getVisibility());
        stickyHeader.animate().cancel();
        stickyHeader.setAlpha(0f);
        stickyHeader.setVisibility(View.INVISIBLE);
    }

    private void showControls(View root) {
        LyricsState state = state(root);
        if(!state.open) return;
        if(root.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setControlVisibility(state, false, false);
            return;
        }
        state.handler.removeCallbacks(state.hideControls);
        setControlVisibility(state, true, false);
        state.handler.postDelayed(state.hideControls, 2800L);
    }

    private void setControlVisibility(LyricsState state, boolean visible, boolean animate) {
        for(View control : new View[]{state.transport, state.footer}) {
            if(control == null) continue;
            control.animate().cancel();
            if(visible) {
                control.setVisibility(View.VISIBLE);
                if(animate) {
                    control.animate().alpha(1f).setDuration(180).start();
                } else control.setAlpha(1f);
            } else {
                if(!animate) {
                    control.setAlpha(0f);
                    control.setVisibility(View.INVISIBLE);
                    continue;
                }
                control.animate().alpha(0f).setDuration(320).withEndAction(() -> {
                    if(state.open) control.setVisibility(View.INVISIBLE);
                }).start();
                state.handler.postDelayed(() -> {
                    if(state.open && control.getAlpha() < 0.2f) control.setVisibility(View.INVISIBLE);
                }, 380L);
            }
        }
        if(visible) BeautifulLyricsHook.setEmbeddedControlOcclusion(state.transport, true);
        else if(animate) state.handler.postDelayed(() -> {
            if(state.open) BeautifulLyricsHook.setEmbeddedControlOcclusion(null, false);
        }, 340L);
        else BeautifulLyricsHook.setEmbeddedControlOcclusion(null, false);
    }

    private Drawable lyricsIcon(View root) {
        int size = dp(root, 24);
        float scale = size / 24f;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(dp(root, 2));
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(3f * scale, 6f * scale, 15f * scale, 6f * scale, paint);
        canvas.drawLine(3f * scale, 11f * scale, 12f * scale, 11f * scale, paint);
        canvas.drawLine(3f * scale, 16f * scale, 10f * scale, 16f * scale, paint);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(17f * scale, 5f * scale, 17f * scale, 16f * scale, paint);
        canvas.drawLine(17f * scale, 5f * scale, 22f * scale, 4f * scale, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(14.5f * scale, 17f * scale, 2.5f * scale, paint);
        return new BitmapDrawable(root.getResources(), bitmap);
    }

    private void addCell(LinearLayout row, View button, int size) {
        FrameLayout cell = new FrameLayout(row.getContext());
        cell.setClipChildren(false);
        cell.setClipToPadding(false);
        row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size, Gravity.CENTER);
        cell.addView(button, params);
        button.setVisibility(View.VISIBLE);
    }

    private GradientDrawable capsule(View root, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(root, 100));
        drawable.setStroke(dp(root, 1), Color.argb(48, 255, 255, 255));
        return drawable;
    }

    private GradientDrawable panel(View root) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0x34161616);
        drawable.setCornerRadius(dp(root, 28));
        drawable.setStroke(dp(root, 1), Color.argb(28, 255, 255, 255));
        return drawable;
    }

    private ViewGroup.MarginLayoutParams margins(ViewGroup.LayoutParams params) {
        if(params instanceof ViewGroup.MarginLayoutParams) return (ViewGroup.MarginLayoutParams) params;
        if(params != null) return new ViewGroup.MarginLayoutParams(params);
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private View find(View root, String name) {
        int id = root.getResources().getIdentifier(name, "id", "com.spotify.music");
        return id == 0 ? null : root.findViewById(id);
    }

    private ViewGroup findGroup(View root, String name) {
        View view = find(root, name);
        return view instanceof ViewGroup ? (ViewGroup) view : null;
    }

    private String string(View root, String name) {
        int id = root.getResources().getIdentifier(name, "string", "com.spotify.music");
        return id == 0 ? null : root.getResources().getString(id);
    }

    private View findByDescription(View view, String description) {
        if(description == null) return null;
        CharSequence current = view.getContentDescription();
        if(current != null && description.contentEquals(current)) return view;
        if(!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for(int index = 0; index < group.getChildCount(); index++) {
            View match = findByDescription(group.getChildAt(index), description);
            if(match != null) return match;
        }
        return null;
    }

    private View findByClassName(View view, String simpleName) {
        if(view.getClass().getSimpleName().equals(simpleName)) return view;
        if(!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for(int index = 0; index < group.getChildCount(); index++) {
            View match = findByClassName(group.getChildAt(index), simpleName);
            if(match != null) return match;
        }
        return null;
    }

    private View directChildOf(View view, ViewGroup ancestor) {
        View current = view;
        while(current != null && current.getParent() != ancestor) {
            ViewParent parent = current.getParent();
            if(!(parent instanceof View)) return null;
            current = (View) parent;
        }
        return current != null && current.getParent() == ancestor ? current : null;
    }

    private void detach(View view) {
        if(view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
    }

    private int dp(View root, int value) {
        return Math.round(value * root.getResources().getDisplayMetrics().density);
    }

    private static class LyricsState {
        final Handler handler = new Handler(Looper.getMainLooper());
        final Map<View, Integer> hiddenViews = new LinkedHashMap<>();
        View transport;
        View footer;
        LinearLayout actionRow;
        View lyricsButton;
        View artwork;
        ImageView embeddedCover;
        ImageView landscapeCover;
        FrameLayout landscapePanel;
        LinearLayout landscapeMetadata;
        TextView landscapeTitle;
        TextView landscapeArtist;
        String landscapeArtworkUri;
        final Map<View, Integer> originalWidths = new WeakHashMap<>();
        final Map<View, Integer> originalHeights = new WeakHashMap<>();
        final Map<View, Integer> originalVisibilities = new WeakHashMap<>();
        boolean open;
        boolean transitioning;
        Runnable hideControls;
    }
}
