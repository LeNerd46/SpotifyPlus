package com.lenerd46.spotifyplus.theme;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.View;
import java.lang.ref.WeakReference;

public final class FrostedPlayerDrawable extends GradientDrawable implements Runnable {
    private final AnimatedThemeBackground source;
    private final boolean header;
    private final WeakReference<View> target;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private Shader sheen;
    private int sheenHeight;
    private int sheenTop;
    private int alpha = 255;

    public FrostedPlayerDrawable(AnimatedThemeBackground source, View target) {
        this(source, target, false);
    }

    public FrostedPlayerDrawable(AnimatedThemeBackground source, View target, boolean header) {
        this.header = header;

        super.setColor(0xFF151B26);
        this.source = source;
        this.target = new WeakReference<>(target);
    }

    @Override public void draw(Canvas canvas) {
        View owner = target.get();
        if (owner == null || getBounds().isEmpty()) return;
        rect.set(getBounds());
        float radius = header ? 0 : rect.height() / 2f;
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
        int saved = canvas.save();
        canvas.clipPath(clip);
        long now = SystemClock.uptimeMillis();
        paint.setShader(null);
        paint.setAlpha(alpha);
        source.drawFrostedBackdrop(canvas, owner, rect, paint, !header);
        if (!header) {
            if (sheen == null || sheenHeight != getBounds().height() || sheenTop != getBounds().top) {
                sheenHeight = getBounds().height();
                sheenTop = getBounds().top;
                sheen = new LinearGradient(0, rect.top, 0, rect.bottom,
                        new int[]{0x66445167, 0x99101722}, null, Shader.TileMode.CLAMP);
            }
            paint.setShader(sheen);
            canvas.drawRoundRect(rect, radius, radius, paint);
            paint.setShader(null);
            paint.setColor(0x55FFFFFF);
            paint.setAlpha(Math.round(0x55 * alpha / 255f));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(owner.getResources().getDisplayMetrics().density);
            canvas.drawRoundRect(rect, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        canvas.restoreToCount(saved);
        unscheduleSelf(this);
        if (owner.isShown() && owner.isAttachedToWindow()) scheduleSelf(this, now + 66);
    }

    @Override public void run() { invalidateSelf(); }
    @Override public void getOutline(Outline outline) {
        outline.setRoundRect(getBounds(), header ? 0 : getBounds().height() / 2f);
        outline.setAlpha(alpha / 255f);
    }
    @Override public void setAlpha(int alpha) { this.alpha = alpha; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) {

    }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    public void release() {
        unscheduleSelf(this);
    }
}
