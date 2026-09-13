package com.lenerd46.spotifyplus.theme;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

public final class FrostedBackdrop {
    private Bitmap tracks, controls;
    private Canvas sampleCanvas;
    private int[] pixels, scratch;
    private final int[] sourcePosition = new int[2], targetPosition = new int[2];
    private final RectF destination = new RectF();
    private long sampledAt;

    public void draw(Canvas canvas, View source, View target, RectF bounds, Paint paint, boolean elevated) {
        if (source == null || source.getWidth() == 0 || source.getHeight() == 0) return;
        float density = source.getResources().getDisplayMetrics().density;
        int width = Math.max(2, Math.round(source.getWidth() / (12f * density)));
        int height = Math.max(2, Math.round(source.getHeight() / (12f * density)));
        if (tracks == null || tracks.getWidth() != width || tracks.getHeight() != height) {
            release();
            tracks = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            controls = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            sampleCanvas = new Canvas(tracks);
            pixels = new int[width * height];
            scratch = new int[pixels.length];
        }
        long now = SystemClock.uptimeMillis();
        if (sampledAt == 0 || now - sampledAt >= 66) {
            tracks.eraseColor(0xFF151B26);
            int saved = sampleCanvas.save();
            sampleCanvas.scale(width / (float) source.getWidth(), height / (float) source.getHeight());
            source.draw(sampleCanvas);
            sampleCanvas.restoreToCount(saved);
            tracks.getPixels(pixels, 0, width, 0, 0, width, height);
            BackdropBlur.apply(pixels, scratch, width, height, 4);
            tracks.setPixels(pixels, 0, width, 0, 0, width, height);

            BackdropBlur.apply(pixels, scratch, width, height, 8);
            controls.setPixels(pixels, 0, width, 0, 0, width, height);
            sampledAt = now;
        }
        source.getLocationOnScreen(sourcePosition);
        target.getLocationOnScreen(targetPosition);
        float left = sourcePosition[0] - targetPosition[0];
        float top = sourcePosition[1] - targetPosition[1];
        destination.set(left, top, left + source.getWidth(), top + source.getHeight());
        int saved = canvas.save();
        canvas.clipRect(bounds);

        canvas.drawBitmap(elevated ? controls : tracks, null, destination, paint);
        canvas.restoreToCount(saved);
    }

    public void release() {
        if (tracks != null) tracks.recycle();
        if (controls != null) controls.recycle();
        tracks = controls = null;
        sampleCanvas = null;
        pixels = scratch = null;
        sampledAt = 0;
    }
}
