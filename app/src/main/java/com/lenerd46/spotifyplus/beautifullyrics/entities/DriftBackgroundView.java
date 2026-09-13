package com.lenerd46.spotifyplus.beautifullyrics.entities;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DriftBackgroundView extends View {
    private static final int SIZE = 64;
    private static final long FADE_MS = 1400;
    private static final ExecutorService PREPARER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shade = new Paint();
    private final Matrix matrix = new Matrix();
    private final int frameDelay;
    private BitmapShader current;
    private BitmapShader previous;
    private float colorStrength;
    private float previousColorStrength;
    private long fadeStart;
    private long lastFrame;
    private float phase;
    private volatile int generation;
    private boolean visible;
    private final Runnable tick = () -> {
        if (visible) invalidate();
    };

    public DriftBackgroundView(Context context, Bitmap artwork) {
        super(context);
        String quality = context.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE)
                .getString("lyric_background_quality", "high");
        frameDelay = "superLow".equalsIgnoreCase(quality) ? 66 : "low".equals(quality) ? 50 : 33;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        updateImage(artwork);
    }

    public void updateImage(Bitmap artwork) {
        if (artwork == null || artwork.isRecycled()) return;
        // Always own the input: the album ImageView and legacy renderer may still use it.
        Bitmap small = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(small);
        canvas.drawColor(Color.BLACK);
        canvas.drawBitmap(artwork, null, new android.graphics.Rect(0, 0, SIZE, SIZE), paint);
        int request = ++generation;
        WeakReference<DriftBackgroundView> reference = new WeakReference<>(this);
        PREPARER.execute(() -> prepare(reference, request, small));
    }

    private static void prepare(WeakReference<DriftBackgroundView> reference, int request, Bitmap small) {
        DriftBackgroundView target = reference.get();
        if (target == null || target.generation != request) {
            small.recycle();
            return;
        }
        int[] pixels = new int[SIZE * SIZE];
        int[] scratch = new int[pixels.length];
        small.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
        boolean recovered = SparseArtworkColors.recover(pixels, SIZE, SIZE);
        // Absolute chroma separates colorful/pastel artwork from earth tones and grayscale.
        // Measure before blurring, which would otherwise average distinct colors into gray.
        float chroma = 0;
        for (int pixel : pixels) {
            int max = Math.max(Color.red(pixel), Math.max(Color.green(pixel), Color.blue(pixel)));
            int min = Math.min(Color.red(pixel), Math.min(Color.green(pixel), Color.blue(pixel)));
            chroma += (max - min) / 255f;
        }
        float amount = Math.max(0f, Math.min(1f, (chroma / pixels.length - 0.10f) / 0.10f));
        // Recovered colors already have presence; avoid amplifying them into neon.
        final float strength = recovered ? 0.35f : amount * amount * (3f - 2f * amount);
        int radius = Math.round(5f - 2f * strength);
        // Three separable box passes approximate a Gaussian. Never run during animation.
        for (int pass = 0; pass < 3; pass++) {
            blur(pixels, scratch, true, radius);
            blur(scratch, pixels, false, radius);
        }
        if (strength > 0f) {
            float[] hsv = new float[3];
            for (int i = 0; i < pixels.length; i++) {
                Color.colorToHSV(pixels[i], hsv);
                // Keep the vivid lift, with room for the artwork's softer pastel tones.
                hsv[1] = Math.min(0.86f, hsv[1] * (1f + 1.3f * strength));
                // Lift colored midtones without turning black regions into a gray veil.
                hsv[2] += (1f - hsv[2]) * hsv[2] * 0.65f * strength;
                pixels[i] = Color.HSVToColor(hsv);
            }
        }
        small.setPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
        MAIN.post(() -> {
            DriftBackgroundView view = reference.get();
            if (view == null || view.generation != request) {
                small.recycle();
                return;
            }
            // Keep the dominant image if another cover arrives during a fade.
            if (view.previous == null || SystemClock.uptimeMillis() - view.fadeStart >= FADE_MS / 2) {
                view.previous = view.current;
                view.previousColorStrength = view.colorStrength;
            }
            view.current = new BitmapShader(small, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR);
            view.colorStrength = strength;
            view.fadeStart = SystemClock.uptimeMillis();
            view.invalidate();
        });
    }

    private static void blur(int[] input, int[] output, boolean horizontal, int radius) {
        final int diameter = radius * 2 + 1;
        for (int line = 0; line < SIZE; line++) {
            int r = 0, g = 0, b = 0;
            for (int i = -radius; i <= radius; i++) {
                int position = Math.max(0, Math.min(SIZE - 1, i));
                int color = input[horizontal ? line * SIZE + position : position * SIZE + line];
                r += Color.red(color);
                g += Color.green(color);
                b += Color.blue(color);
            }
            for (int i = 0; i < SIZE; i++) {
                output[horizontal ? line * SIZE + i : i * SIZE + line] = Color.rgb(r / diameter, g / diameter, b / diameter);
                int out = Math.max(0, i - radius);
                int in = Math.min(SIZE - 1, i + radius + 1);
                int a = input[horizontal ? line * SIZE + out : out * SIZE + line];
                int z = input[horizontal ? line * SIZE + in : in * SIZE + line];
                r += Color.red(z) - Color.red(a);
                g += Color.green(z) - Color.green(a);
                b += Color.blue(z) - Color.blue(a);
            }
        }
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (h > 0) shade.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0x55000000, 0x77000000, 0xBB000000},
                new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        if (visible && lastFrame != 0) phase += Math.min(100, now - lastFrame) / 1000f;
        lastFrame = now;
        canvas.drawColor(0xFF101014);
        float shadingStrength = colorStrength;
        if (current != null) {
            float fade = Math.min(1f, (now - fadeStart) / (float) FADE_MS);
            fade = fade * fade * (3f - 2f * fade);
            if (previous != null && fade < 1f) {
                shadingStrength = previousColorStrength + (colorStrength - previousColorStrength) * fade;
                drawArtwork(canvas, previous, 255);
                // Fade over an opaque old cover; no full-screen offscreen layer required.
                drawArtwork(canvas, current, Math.round(255 * fade));
            } else {
                previous = null;
                drawArtwork(canvas, current, 255);
            }
        }
        // Neutral albums retain the original shading; colorful ones lose the gloomy veil.
        shade.setAlpha(Math.round(255f * (1f - 0.55f * shadingStrength)));
        canvas.drawRect(0, 0, getWidth(), getHeight(), shade);
        if (visible) {
            removeCallbacks(tick);
            postDelayed(tick, frameDelay);
        }
    }

    private void drawArtwork(Canvas canvas, BitmapShader shader, int alpha) {
        paint.setShader(shader);
        for (int layer = 0; layer < 2; layer++) {
            float time = phase * (layer == 0 ? 0.10f : -0.075f);
            float scale = Math.max(getWidth(), getHeight()) / (float) SIZE
                    * (1.06f + 0.12f * (float) Math.sin(time * 1.7f + layer));
            matrix.setTranslate(-SIZE / 2f, -SIZE / 2f);
            matrix.postScale(scale, scale);
            matrix.postRotate((float) Math.toDegrees(time) + layer * 115f);
            matrix.postTranslate(getWidth() * (0.5f + 0.28f * (float) Math.sin(time * 1.3f + layer * 2)),
                    getHeight() * (0.5f + 0.24f * (float) Math.cos(time * 1.1f + layer * 3)));
            shader.setLocalMatrix(matrix);
            paint.setAlpha(layer == 0 ? alpha : Math.round(alpha * 0.38f));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }
        paint.setShader(null);
        paint.setAlpha(255);
    }

    @Override public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (visible == isVisible) return;
        visible = isVisible;
        lastFrame = 0;
        removeCallbacks(tick);
        if (visible) invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        visible = false;
        lastFrame = 0;
        removeCallbacks(tick);
        // Textures stay valid for reattachment; let GC retire them after display lists do.
        super.onDetachedFromWindow();
    }
}
