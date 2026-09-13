package com.lenerd46.spotifyplus.theme;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.view.View;
import java.lang.ref.WeakReference;

public final class FrostedSurfaceDrawable extends GradientDrawable implements Drawable.Callback {
    public enum Surface { TRACK, LIST, CONTROL, STRIP }

    private final AnimatedThemeBackground source;
    private final WeakReference<View> target;
    private final Surface surface;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private Drawable original;
    private int opacity = 255;

    public FrostedSurfaceDrawable(AnimatedThemeBackground source, View target, Surface surface) {
        this.source = source;
        this.target = new WeakReference<>(target);
        this.surface = surface;
        super.setColor(0xFF151B26);
    }

    public void setOriginal(Drawable drawable) {
        if (original != null) original.setCallback(null);
        original = drawable;
        if (original != null) {
            original.setCallback(this);
            original.setBounds(getBounds());
            original.setState(getState());
        }
    }

    private float radius(View owner) {
        return surface == Surface.CONTROL ? rect.height() / 2f : 0f;
    }

    public Surface surface() { return surface; }

    private boolean isControl() { return surface == Surface.CONTROL || surface == Surface.STRIP; }

    @Override public void draw(Canvas canvas) {
        View owner = target.get();
        if (owner == null || getBounds().isEmpty()) return;
        rect.set(getBounds());
        float radius = radius(owner);
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        int saved = canvas.save();
        canvas.clipPath(clip);
        paint.setAlpha(opacity);
        source.drawFrostedBackdrop(canvas, owner, rect, paint, isControl());
        paint.setColor(surface == Surface.LIST ? AnimatedThemePalette.LIST_GLASS
                : surface == Surface.TRACK ? AnimatedThemePalette.TRACK_GLASS : AnimatedThemePalette.CONTROL_GLASS);
        paint.setAlpha(Math.round((paint.getColor() >>> 24) * opacity / 255f));
        canvas.drawRoundRect(rect, radius, radius, paint);
        if (original != null) {

            int layer = canvas.saveLayerAlpha(rect, Math.round(opacity *
                    (isControl() ? 0.86f : 0.35f)));
            original.draw(canvas);
            canvas.restoreToCount(layer);
        }
        if (surface == Surface.CONTROL) {
            paint.setColor(0x28FFFFFF);
            paint.setAlpha(Math.round(0x28 * opacity / 255f));
            paint.setStyle(Paint.Style.STROKE);
            float stroke = owner.getResources().getDisplayMetrics().density;
            paint.setStrokeWidth(stroke);
            rect.inset(stroke / 2f, stroke / 2f);
            canvas.drawRoundRect(rect, Math.max(0, radius - stroke / 2f), Math.max(0, radius - stroke / 2f), paint);
            paint.setStyle(Paint.Style.FILL);
        }
        canvas.restoreToCount(saved);
    }

    @Override protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        if (original != null) original.setBounds(bounds);
    }
    @Override public boolean isStateful() { return original != null && original.isStateful(); }
    @Override protected boolean onStateChange(int[] state) {
        boolean changed = original != null && original.setState(state);
        if (changed) invalidateSelf();
        return changed;
    }
    @Override protected boolean onLevelChange(int level) { return original != null && original.setLevel(level); }
    @Override public void setHotspot(float x, float y) { if (original != null) original.setHotspot(x, y); }
    @Override public void setHotspotBounds(int l, int t, int r, int b) {
        if (original != null) original.setHotspotBounds(l, t, r, b);
    }
    @Override public void jumpToCurrentState() { if (original != null) original.jumpToCurrentState(); }
    @Override public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (original != null) changed |= original.setVisible(visible, restart);
        return changed;
    }
    @Override public boolean getPadding(Rect padding) {
        return original != null ? original.getPadding(padding) : super.getPadding(padding);
    }
    @Override public void getOutline(Outline outline) {
        View owner = target.get();
        if (owner == null) return;
        rect.set(getBounds());
        outline.setRoundRect(getBounds(), radius(owner));
        outline.setAlpha(opacity / 255f);
    }
    @Override public void setAlpha(int alpha) { opacity = alpha; invalidateSelf(); }
    @Override public int getAlpha() { return opacity; }
    @Override public void setTintList(ColorStateList tint) {
        if (original != null) original.setTintList(tint);
    }
    @Override public void setTintMode(PorterDuff.Mode mode) {
        if (original != null) original.setTintMode(mode);
    }
    @Override public void setColor(int color) {
        super.setColor(color);
        if (original instanceof GradientDrawable) ((GradientDrawable) original).setColor(color);
    }
    @Override public void setColor(ColorStateList colors) {
        super.setColor(colors);
        if (original instanceof GradientDrawable) ((GradientDrawable) original).setColor(colors);
    }
    @Override public void setColors(int[] colors) {
        super.setColors(colors);
        if (original instanceof GradientDrawable) ((GradientDrawable) original).setColors(colors);
    }
    @Override public void setColorFilter(ColorFilter filter) {

        if (original != null) original.setColorFilter(filter);
    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public void invalidateDrawable(Drawable who) { invalidateSelf(); }
    @Override public void scheduleDrawable(Drawable who, Runnable what, long when) { scheduleSelf(what, when); }
    @Override public void unscheduleDrawable(Drawable who, Runnable what) { unscheduleSelf(what); }
    public void release() { setOriginal(null); }
}
