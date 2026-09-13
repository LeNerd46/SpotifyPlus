package com.lenerd46.spotifyplus.theme;

import com.lenerd46.spotifyplus.hooks.ThemeHook;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.content.res.ColorStateList;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import android.widget.ImageView;
import android.graphics.drawable.BitmapDrawable;
import java.util.IdentityHashMap;
import java.util.Map;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.lenerd46.spotifyplus.beautifullyrics.entities.LyricsBackgroundView;

public final class AnimatedThemeBackground {
    private Activity activity;
    private LyricsBackgroundView view;
    private Bitmap artwork;
    private final ColorDrawable dimming = new ColorDrawable(AnimatedThemePalette.SCRIM);
    private Bitmap brightnessSample;
    private Canvas brightnessCanvas;
    private final int[] brightnessPixels = new int[16 * 32];
    private long brightnessSampleTime;
    private View contentRoot;
    private View player;
    private View navigation;
    private Drawable playerOriginal;
    private ColorStateList playerTint;
    private Drawable navigationOriginal;
    private FrostedPlayerDrawable glass;
    private final FrostedBackdrop frostedBackdrop = new FrostedBackdrop();
    private final Map<View, SurfaceBackground> surfaces = new IdentityHashMap<>();
    private final Map<View, Boolean> trackRows = new java.util.WeakHashMap<>();
    private final Map<Integer, String> resourceNames = new java.util.HashMap<>();
    private long surfacesInvalidatedAt;
    private final class SurfaceBackground {
        Drawable original;
        ColorStateList tint;
        final FrostedSurfaceDrawable animated;

        SurfaceBackground(View target, FrostedSurfaceDrawable.Surface surface) {
            animated = new FrostedSurfaceDrawable(AnimatedThemeBackground.this, target, surface);
            apply(target);
        }

        void apply(View target) {
            if (target.getBackground() != animated) {
                original = target.getBackground();
                tint = target.getBackgroundTintList();
                animated.setOriginal(original);
                replaceBackground(target, animated, tint);

                animated.setOriginal(original);
            } else {
                tint = target.getBackgroundTintList();
            }
        }

        void restore(View target) {
            animated.release();
            if (target.getBackground() == animated) replaceBackground(target, original, tint);
        }
    }
    private final Map<View, HeaderBackground> headers = new IdentityHashMap<>();
    private static final class HeaderBackground {
        final Drawable original;
        final ColorStateList tint;
        final FrostedPlayerDrawable animated;
        Drawable contentScrim;
        Drawable statusScrim;
        java.lang.reflect.Method setContentScrim;
        java.lang.reflect.Method setStatusScrim;
        HeaderBackground(AnimatedThemeBackground source, View target) {
            original = target.getBackground();
            tint = target.getBackgroundTintList();
            animated = new FrostedPlayerDrawable(source, target, true);
            if (target.getClass().getName().endsWith(".CollapsingToolbarLayout")) {
                try {
                    contentScrim = (Drawable) target.getClass().getMethod("getContentScrim").invoke(target);
                    statusScrim = (Drawable) target.getClass().getMethod("getStatusBarScrim").invoke(target);
                    setContentScrim = target.getClass().getMethod("setContentScrim", Drawable.class);
                    setStatusScrim = target.getClass().getMethod("setStatusBarScrim", Drawable.class);
                } catch (ReflectiveOperationException ignored) {}
            }
        }
        void scrims(View target, boolean restore) {
            try {
                if (setContentScrim != null) setContentScrim.invoke(target, restore ? contentScrim : null);
                if (setStatusScrim != null) setStatusScrim.invoke(target, restore ? statusScrim : null);
            } catch (ReflectiveOperationException ignored) {}
        }
    }
    private final Map<TextView, ColorStateList> navigationText = new IdentityHashMap<>();
    private final Map<ImageView, ColorStateList> navigationIcons = new IdentityHashMap<>();
    private final ColorStateList navigationColors = new ColorStateList(new int[][]{
            {-android.R.attr.state_enabled}, {android.R.attr.state_selected},
            {android.R.attr.state_activated}, {}},
            new int[]{0x80EBEDF2, AnimatedThemePalette.TEXT, AnimatedThemePalette.TEXT, 0xFFCCD3DF});
    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener = this::updateChrome;
    private final ViewTreeObserver.OnPreDrawListener drawListener = () -> {
        updateDimming();
        for (Map.Entry<View, HeaderBackground> entry : headers.entrySet()) {
            View target = entry.getKey();
            entry.getValue().scrims(target, false);
            if (target.getBackgroundTintList() != null) target.setBackgroundTintList(null);
            if (target.getBackground() != entry.getValue().animated) target.setBackground(entry.getValue().animated);
        }

        if (player != null && glass != null) {
            if (player.getBackgroundTintList() != null) player.setBackgroundTintList(null);
            if (player.getBackground() != glass) player.setBackground(glass);
        }
        long now = android.os.SystemClock.uptimeMillis();
        boolean refresh = now - surfacesInvalidatedAt >= 66;
        for (Map.Entry<View, SurfaceBackground> entry : surfaces.entrySet()) {
            View target = entry.getKey();
            entry.getValue().apply(target);
            if (refresh && target.isShown()) entry.getValue().animated.invalidateSelf();
        }
        if (refresh) surfacesInvalidatedAt = now;
        return true;
    };

    public void show(Activity next) {
        if (activity == next && view != null) return;
        hide();
        View content = next.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout)) return;
        Bitmap image = artwork;
        if (image == null) {
            image = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
            image.setPixels(new int[]{ThemeHook.BACKGROUND, ThemeHook.ACCENT,
                    ThemeHook.SURFACE, ThemeHook.BACKGROUND_HIGHLIGHT}, 0, 2, 0, 0, 2, 2);
        }
        view = new LyricsBackgroundView(next, image);
        if (artwork == null) image.recycle();
        dimming.setColor(AnimatedThemePalette.SCRIM);
        view.setForeground(dimming);
        view.setClickable(false);
        view.setFocusable(false);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        ((FrameLayout) content).addView(view, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activity = next;
        contentRoot = content;
        content.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        content.getViewTreeObserver().addOnPreDrawListener(drawListener);
        updateChrome();
    }

    private void updateDimming() {
        if (view == null || view.getChildCount() == 0 || view.getWidth() == 0 || view.getHeight() == 0) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - brightnessSampleTime < 250) return;
        brightnessSampleTime = now;
        if (brightnessSample == null) {
            brightnessSample = Bitmap.createBitmap(16, 32, Bitmap.Config.ARGB_8888);
            brightnessCanvas = new Canvas(brightnessSample);
        }
        brightnessSample.eraseColor(0xFF000000);
        int saved = brightnessCanvas.save();
        brightnessCanvas.scale(16f / view.getWidth(), 32f / view.getHeight());

        view.getChildAt(0).draw(brightnessCanvas);
        brightnessCanvas.restoreToCount(saved);
        brightnessSample.getPixels(brightnessPixels, 0, 16, 0, 0, 16, 32);
        int needed = AnimatedThemePalette.SCRIM >>> 24;
        for (int pixel : brightnessPixels) needed = Math.max(needed, AnimatedThemePalette.scrimAlphaFor(pixel));
        int current = dimming.getColor() >>> 24;

        int next = needed >= current ? needed : Math.max(needed, current - 8);
        if (next != current) dimming.setColor(next << 24);
    }

    public void drawFrostedBackdrop(Canvas canvas, View target, android.graphics.RectF bounds,
                             Paint paint, boolean elevated) {
        frostedBackdrop.draw(canvas, view, target, bounds, paint, elevated);
    }

    private View find(String name) {
        int id = activity.getResources().getIdentifier(name, "id", activity.getPackageName());
        return id == 0 ? null : contentRoot.findViewById(id);
    }

    private void updateChrome() {
        if (contentRoot == null) return;
        View nextPlayer = find("now_playing_bar_layout");
        if (nextPlayer != player) {
            restorePlayer();
            player = nextPlayer;
            if (player != null) {
                playerOriginal = player.getBackground();
                playerTint = player.getBackgroundTintList();
                glass = new FrostedPlayerDrawable(this, player);
                player.setBackgroundTintList(null);
                player.setBackground(glass);
            }
        }
        View nextNavigation = find("navigation_bar");
        if (nextNavigation != navigation) {
            restoreNavigation();
            navigation = nextNavigation;
            if (navigation != null) {
                navigationOriginal = navigation.getBackground();
                navigation.setBackgroundColor(0xB30A0E16);
            }
        }
        if (navigation != null) tintNavigation(navigation);

        boolean albumOrPlaylist = find("cwp_header_artwork_background") != null
                || find("artwork_background") != null || find("header_layout") != null;
        if (albumOrPlaylist) {
            for (String name : new String[]{"toolbar", "toolbar_wrapper", "cwp_header_toolbar"}) {
                View header = find(name);
                if (header != null) {
                    if (!headers.containsKey(header)) headers.put(header, new HeaderBackground(this, header));

                }
            }
        }
        java.util.Iterator<Map.Entry<View, HeaderBackground>> iterator = headers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<View, HeaderBackground> entry = iterator.next();
            if (!entry.getKey().isAttachedToWindow()) {
                restoreHeader(entry.getKey(), entry.getValue());
                iterator.remove();
            }
        }
        Map<View, FrostedSurfaceDrawable.Surface> desired = new IdentityHashMap<>();
        discoverSurfaces(contentRoot, desired, false);

        desired.entrySet().removeIf(entry -> entry.getValue() == FrostedSurfaceDrawable.Surface.TRACK
                && hasListSurfaceAncestor(entry.getKey(), desired));
        java.util.Iterator<Map.Entry<View, SurfaceBackground>> surfaceIterator = surfaces.entrySet().iterator();
        while (surfaceIterator.hasNext()) {
            Map.Entry<View, SurfaceBackground> entry = surfaceIterator.next();
            if (!entry.getKey().isAttachedToWindow() || desired.get(entry.getKey()) != entry.getValue().animated.surface()) {
                entry.getValue().restore(entry.getKey());
                surfaceIterator.remove();
            }
        }
        for (Map.Entry<View, FrostedSurfaceDrawable.Surface> entry : desired.entrySet()) {
            if (!surfaces.containsKey(entry.getKey())) surfaces.put(entry.getKey(), new SurfaceBackground(entry.getKey(), entry.getValue()));
        }
    }

    public void registerTrackRow(View row) {
        trackRows.put(row, Boolean.TRUE);
    }

    private String resourceName(View target) {
        int id = target.getId();
        if (id == View.NO_ID) return "";
        String name = resourceNames.get(id);
        if (name == null) {
            try { name = target.getResources().getResourceEntryName(id); }
            catch (android.content.res.Resources.NotFoundException ignored) { name = ""; }
            resourceNames.put(id, name);
        }
        return name;
    }

    private void discoverSurfaces(View target, Map<View, FrostedSurfaceDrawable.Surface> desired, boolean inRow) {
        if (target == view || target == player || target == navigation || target.getVisibility() != View.VISIBLE) return;
        String name = resourceName(target);
        String type = target.getClass().getName();
        FrostedSurfaceDrawable.Surface surface = null;
        boolean row = trackRows.containsKey(target) || name.equals("row_root")
                || name.equals("track_row_queue_root") || hasDirectChild(target, "guide_row_start");
        if (row && hasDirectChild(target, "content_column")) {
            View list = recyclerAncestor(target);
            if (list != null) desired.put(list, FrostedSurfaceDrawable.Surface.LIST);
        } else if (row && !inRow) {
            surface = FrostedSurfaceDrawable.Surface.TRACK;
        } else if (name.equals("filter_chips_container")) {

            surface = FrostedSurfaceDrawable.Surface.STRIP;
        } else if (name.equals("button_play_and_pause")
                || (name.equals("header_play_button") && type.contains("ComposeView"))
                || type.equals("com.spotify.encoreconsumermobile.elements.chipbutton.ChipButtonView")
                || (!inRow && (type.equals("com.spotify.encoreconsumermobile.elements.smartshufflebutton.SmartShuffleButtonView")
                    || type.equals("com.spotify.encoreconsumermobile.elements.downloadbutton.DownloadButtonView")
                    || type.equals("com.spotify.encoreconsumermobile.elements.roundsharebutton.RoundShareButtonView"))
                    && hasActionRowAncestor(target))) {
            surface = FrostedSurfaceDrawable.Surface.CONTROL;
        }
        if (surface != null) desired.put(target, surface);

        if (surface != null && surface != FrostedSurfaceDrawable.Surface.TRACK) return;
        if (target instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) target;
            for (int i = 0; i < group.getChildCount(); i++) discoverSurfaces(group.getChildAt(i), desired, inRow || row);
        }
    }

    private boolean hasDirectChild(View target, String name) {
        if (!(target instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) target;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (resourceName(group.getChildAt(i)).equals(name)) return true;
        }
        return false;
    }

    private boolean hasActionRowAncestor(View target) {
        for (android.view.ViewParent parent = target.getParent(); parent instanceof View; parent = parent.getParent()) {
            if (resourceName((View) parent).equals("action_row")) return true;
        }
        return false;
    }

    private static View recyclerAncestor(View target) {
        for (android.view.ViewParent parent = target.getParent(); parent instanceof View; parent = parent.getParent()) {
            for (Class<?> type = parent.getClass(); type != null; type = type.getSuperclass()) {
                if (type.getName().equals("androidx.recyclerview.widget.RecyclerView")) return (View) parent;
            }
        }
        return null;
    }

    private static boolean hasListSurfaceAncestor(View target, Map<View, FrostedSurfaceDrawable.Surface> desired) {
        for (android.view.ViewParent parent = target.getParent(); parent instanceof View; parent = parent.getParent()) {
            if (desired.get(parent) == FrostedSurfaceDrawable.Surface.LIST) return true;
        }
        return false;
    }

    private static void replaceBackground(View target, Drawable drawable, ColorStateList tint) {
        int left = target.getPaddingLeft(), top = target.getPaddingTop();
        int right = target.getPaddingRight(), bottom = target.getPaddingBottom();
        target.setBackground(drawable);
        if (target.getBackgroundTintList() != tint) target.setBackgroundTintList(tint);
        target.setPadding(left, top, right, bottom);
    }

    private void restoreHeader(View target, HeaderBackground background) {
        target.setBackground(background.original);
        target.setBackgroundTintList(background.tint);
        background.scrims(target, true);
        background.animated.release();
    }

    private void tintNavigation(View node) {
        if (node instanceof TextView) {
            TextView text = (TextView) node;
            if (!navigationText.containsKey(text)) navigationText.put(text, text.getTextColors());
            if (text.getTextColors() != navigationColors) text.setTextColor(navigationColors);
        }
        if (node instanceof ImageView && !(((ImageView) node).getDrawable() instanceof BitmapDrawable)) {
            ImageView icon = (ImageView) node;
            if (!navigationIcons.containsKey(icon)) navigationIcons.put(icon, icon.getImageTintList());
            if (icon.getImageTintList() != navigationColors) icon.setImageTintList(navigationColors);
        }
        if (node instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) node;
            for (int i = 0; i < group.getChildCount(); i++) tintNavigation(group.getChildAt(i));
        }
    }

    private void restorePlayer() {
        if (player != null) {
            player.setBackground(playerOriginal);
            player.setBackgroundTintList(playerTint);
        }
        if (glass != null) glass.release();
        glass = null;
        player = null;
        playerOriginal = null;
        playerTint = null;
    }

    private void restoreNavigation() {
        if (navigation != null) navigation.setBackground(navigationOriginal);
        for (Map.Entry<TextView, ColorStateList> entry : navigationText.entrySet()) entry.getKey().setTextColor(entry.getValue());
        for (Map.Entry<ImageView, ColorStateList> entry : navigationIcons.entrySet()) entry.getKey().setImageTintList(entry.getValue());
        navigationText.clear();
        navigationIcons.clear();
        navigation = null;
        navigationOriginal = null;
    }

    public void updateImage(Bitmap image) {
        if (artwork == null) artwork = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        new Canvas(artwork).drawBitmap(image, null, new Rect(0, 0, 100, 100),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        if (view != null) view.updateImage(artwork);
    }

    public void hide(Activity owner) {
        if (activity == owner) hide();
    }

    public void hide() {
        if (contentRoot != null && contentRoot.getViewTreeObserver().isAlive()) {
            contentRoot.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
            contentRoot.getViewTreeObserver().removeOnPreDrawListener(drawListener);
        }
        restorePlayer();
        restoreNavigation();
        for (Map.Entry<View, HeaderBackground> entry : headers.entrySet()) restoreHeader(entry.getKey(), entry.getValue());
        headers.clear();
        for (Map.Entry<View, SurfaceBackground> entry : surfaces.entrySet()) entry.getValue().restore(entry.getKey());
        surfaces.clear();
        resourceNames.clear();
        frostedBackdrop.release();
        surfacesInvalidatedAt = 0;
        if (brightnessSample != null) brightnessSample.recycle();
        brightnessSample = null;
        brightnessCanvas = null;
        brightnessSampleTime = 0;
        contentRoot = null;
        if (view != null && view.getParent() instanceof ViewGroup) {

            ((ViewGroup) view.getParent()).removeView(view);
        }
        view = null;
        activity = null;
    }
}
