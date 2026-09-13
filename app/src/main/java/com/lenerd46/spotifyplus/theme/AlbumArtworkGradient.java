package com.lenerd46.spotifyplus.theme;

import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import de.robv.android.xposed.XposedHelpers;

public final class AlbumArtworkGradient extends GradientDrawable {
    private final int endpoint;
    private final boolean animated;

    public AlbumArtworkGradient(int artwork, int endpoint, boolean animated) {
        super(Orientation.TOP_BOTTOM, AlbumGradientColors.stops(artwork, endpoint, animated));
        this.endpoint = endpoint;
        this.animated = animated;
    }

    @Override public void setColorFilter(ColorFilter filter) {
        if (animated && filter instanceof PorterDuffColorFilter) {
            PorterDuffColorFilter tint = (PorterDuffColorFilter) filter;
            PorterDuff.Mode mode = (PorterDuff.Mode) XposedHelpers.getObjectField(tint, "mMode");
            if (mode == PorterDuff.Mode.DST_OVER) {
                int color = XposedHelpers.getIntField(tint, "mColor");
                setColors(AlbumGradientColors.stops(color, endpoint, true));
                super.setColorFilter(null);
                return;
            }
        }
        super.setColorFilter(filter);
    }
}
