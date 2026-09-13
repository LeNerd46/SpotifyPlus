package com.lenerd46.spotifyplus.beautifullyrics.entities;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.widget.FrameLayout;

public final class LyricsBackgroundView extends FrameLayout {
    public static final String DRIFT_PREFERENCE = "experiment_drift_background";
    private final SharedPreferences preferences;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
        if (DRIFT_PREFERENCE.equals(key)) selectStyle();
    };
    private Bitmap artwork;
    private View renderer;
    private boolean drift;

    public LyricsBackgroundView(Context context, Bitmap image) {
        super(context);
        preferences = context.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
        updateImage(image);
    }

    public void updateImage(Bitmap image) {
        if (image == null || image.isRecycled()) return;
        Bitmap copy = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        new Canvas(copy).drawBitmap(image, null, new Rect(0, 0, 100, 100), new Paint(Paint.FILTER_BITMAP_FLAG));
        if (artwork != null) artwork.recycle();
        artwork = copy;
        if (renderer == null) selectStyle();
        else if (renderer instanceof DriftBackgroundView) ((DriftBackgroundView) renderer).updateImage(artwork);
        else ((AnimatedBackgroundView) renderer).updateImage(artwork);
    }

    private void selectStyle() {
        boolean selected = preferences.getBoolean(DRIFT_PREFERENCE, false);
        if (renderer != null && selected == drift) return;
        removeAllViews();
        drift = selected;
        renderer = drift ? new DriftBackgroundView(getContext(), artwork)
                : new AnimatedBackgroundView(getContext(), artwork, this);
        addView(renderer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        preferences.registerOnSharedPreferenceChangeListener(listener);
        selectStyle();
    }

    @Override protected void onDetachedFromWindow() {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
        // The legacy renderer shuts its thread down on detach, so recreate it on attach.
        removeAllViews();
        renderer = null;
        super.onDetachedFromWindow();
    }
}
