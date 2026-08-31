package com.lenerd.spotifyplus.module.lyrics;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class AnimatedBackgroundView extends View {
    private static float DOWNSAMPLE_FACTOR = 0.12f;

    private static final int BLUR_RADIUS = 20;
    private static final int BLUR_PASSES = 1;

    private static int BLOB_COUNT = 16;
    private static final long TRANSITION_DURATION_MS = 1000L;
    private static final int BUFFER_COUNT = 3;

    private static final int PALETTE_COLORFUL = 0;
    private static final int PALETTE_DARK_MUTED = 1;
    private static final int PALETTE_BRIGHT_NEUTRAL = 2;

    private int paletteMode = PALETTE_COLORFUL;

    private final HandlerThread renderThread;
    private Handler renderHandler;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isRendering = new AtomicBoolean(false);
    private final Object lock = new Object();

    private final Random random = new Random();

    private final Bitmap[] buffers = new Bitmap[BUFFER_COUNT];
    private final Canvas[] canvases = new Canvas[BUFFER_COUNT];
    private int renderHeadIndex = 0;
    private Bitmap renderedBitmap;

    private int offW = 1;
    private int offH = 1;

    private Bitmap sourceImage;
    private int baseColor = 0xFF101010;

    private List<Blob> blobs = new ArrayList<>();
    private final Paint blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint drawPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private long startTimeMs;
    private boolean isTransitioning = false;
    private Bitmap previousBitmap;
    private long transitionStartMs;
    private long lastFrameTimeNanos = 0;

    private float animationSpeedMultiplier = 1.0f;
    private float breathingFrequency = 1.0f;

    private int[] blurBufA;
    private int[] blurBufB;
    private Choreographer.FrameCallback frameCallback = null;

    private boolean debugOverlayEnabled = false;

    private float uiFps = 0f;
    private float renderFps = 0f;
    private float avgRenderMs = 0f;
    private float worstRenderMs = 0f;
    private int jankyUiFrames = 0;
    private int totalUiFrames = 0;
    private long lastMetricsSecondMs = 0;
    private int renderCountThisSecond = 0;

    private final Paint overlayTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public AnimatedBackgroundView(Context ctx, Bitmap bitmap, ViewGroup root) {
        super(ctx);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        if (bitmap != null) {
            this.sourceImage = Bitmap.createScaledBitmap(bitmap, 100, 100, true);
        } else {
            this.sourceImage = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }

        SharedPreferences prefs = ctx.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
        String quality = prefs.getString("lyric_background_quality", "high");

        switch (quality) {
            case "high":
                DOWNSAMPLE_FACTOR = 0.12f;
                BLOB_COUNT = 16;
                break;
            case "mid":
                DOWNSAMPLE_FACTOR = 0.06f;
                BLOB_COUNT = 10;
                break;
            case "low":
                DOWNSAMPLE_FACTOR = 0.04f;
                BLOB_COUNT = 6;
                break;
            case "superlow":
                DOWNSAMPLE_FACTOR = 0.02f;
                BLOB_COUNT = 4;
                break;
        }

        renderThread = new HandlerThread("FluidBG");
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());

        overlayBgPaint.setColor(0x88000000);
        overlayTextPaint.setColor(Color.WHITE);
        overlayTextPaint.setTextSize(dp(11));
        overlayTextPaint.setFakeBoldText(true);

        startTimeMs = SystemClock.elapsedRealtime();
        lastMetricsSecondMs = startTimeMs;

        blobPaint.setXfermode(null);
        blobPaint.setStyle(Paint.Style.FILL);

        startTimeMs = SystemClock.elapsedRealtime();

        frameCallback = frameTimeNanos -> {
            if (getWindowToken() == null) return;

            long dt = (lastFrameTimeNanos == 0)
                    ? 16_000_000
                    : (frameTimeNanos - lastFrameTimeNanos);

            lastFrameTimeNanos = frameTimeNanos;
            updateUiMetrics(dt);

            renderHandler.post(() -> renderFrame(dt));
            Choreographer.getInstance().postFrameCallback(frameCallback);
        };

        renderHandler.post(this::internalRebuildResources);
    }

    public void updateImage(Bitmap newImage) {
        if (newImage == null || newImage.isRecycled()) return;

        Bitmap smallCopy = Bitmap.createScaledBitmap(newImage, 100, 100, true);

        synchronized (lock) {
            if (renderedBitmap != null) {
                previousBitmap = renderedBitmap;
                transitionStartMs = SystemClock.elapsedRealtime();
                isTransitioning = true;
            }
            sourceImage = smallCopy;
        }

        renderHandler.post(this::internalRebuildResources);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        allocateBuffersIfNeeded(getWidth(), getHeight());
        renderHandler.post(this::internalRebuildResources);
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        renderHandler.removeCallbacksAndMessages(null);
        renderThread.quitSafely();

        synchronized (lock) {
            for (int i = 0; i < BUFFER_COUNT; i++) {
                if (buffers[i] != null) {
                    buffers[i].recycle();
                }
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        allocateBuffersIfNeeded(w, h);
        renderHandler.post(this::internalRebuildResources);
    }

    private void allocateBuffersIfNeeded(int vw, int vh) {
        if (vw <= 0 || vh <= 0) return;

        int targetW = Math.max(1, Math.round(vw * DOWNSAMPLE_FACTOR));
        int targetH = Math.max(1, Math.round(vh * DOWNSAMPLE_FACTOR));

        if (buffers[0] != null
                && buffers[0].getWidth() == targetW
                && buffers[0].getHeight() == targetH) {
            return;
        }

        synchronized (lock) {
            for (int i = 0; i < BUFFER_COUNT; i++) {
                if (buffers[i] != null) {
                    buffers[i].recycle();
                }
                buffers[i] = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888);
                canvases[i] = new Canvas(buffers[i]);
            }

            offW = targetW;
            offH = targetH;
            blurBufA = new int[offW * offH];
            blurBufB = new int[offW * offH];
        }
    }

    private void internalRebuildResources() {
        if (sourceImage == null) return;

        PaletteInfo paletteInfo = analyzePalette(sourceImage);
        paletteMode = paletteInfo.mode;

        animationSpeedMultiplier = 1.0f;
        breathingFrequency = 1.0f;

        List<Integer> blobColors = extractDominantColors(sourceImage, BLOB_COUNT, paletteMode);

        if (blobColors.isEmpty()) {
            long r = 0;
            long g = 0;
            long blue = 0;
            long count = 0;
            int w = sourceImage.getWidth();
            int h = sourceImage.getHeight();

            for (int x = 0; x < w; x += 10) {
                for (int y = 0; y < h; y += 10) {
                    int c = sourceImage.getPixel(x, y);
                    r += Color.red(c);
                    g += Color.green(c);
                    blue += Color.blue(c);
                    count++;
                }
            }

            int avg = (count == 0)
                    ? 0xFF101010
                    : Color.rgb((int) (r / count), (int) (g / count), (int) (blue / count));

            baseColor = buildBaseColor(avg);
            blobColors = new ArrayList<>();
            for (int i = 0; i < BLOB_COUNT; i++) {
                blobColors.add(avg);
            }
        } else {
            baseColor = buildBaseColor(blobColors.get(0));
        }

        List<Blob> newBlobs = new ArrayList<>();

        for (int i = 0; i < BLOB_COUNT; i++) {
            int rawColor = blobColors.get(i);
            int processedColor = boostColorForVividness(rawColor);

            float originX = 0.5f + (random.nextFloat() - 0.5f) * 0.8f;
            float originY = 0.5f + (random.nextFloat() - 0.5f) * 0.8f;
            float radius = 0.35f + random.nextFloat() * 0.4f;

            float vx = (random.nextFloat() - 0.5f) * 0.003f;
            float vy = (random.nextFloat() - 0.5f) * 0.003f;

            newBlobs.add(new Blob(originX, originY, radius, processedColor, vx, vy));
        }

        blobs = newBlobs;
    }

    private PaletteInfo analyzePalette(Bitmap bitmap) {
        PaletteInfo info = new PaletteInfo();
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        float[] hsv = new float[3];

        double sumS = 0.0;
        double sumV = 0.0;
        int count = 0;

        for (int x = 0; x < w; x += 4) {
            for (int y = 0; y < h; y += 4) {
                int c = bitmap.getPixel(x, y);
                Color.colorToHSV(c, hsv);
                sumS += hsv[1];
                sumV += hsv[2];
                count++;
            }
        }

        if (count == 0) {
            info.avgS = 0f;
            info.avgV = 0f;
            info.mode = PALETTE_DARK_MUTED;
            return info;
        }

        info.avgS = (float) (sumS / count);
        info.avgV = (float) (sumV / count);

        if (info.avgV >= 0.75f && info.avgS <= 0.25f) {
            info.mode = PALETTE_BRIGHT_NEUTRAL;
        } else if (info.avgV < 0.30f || info.avgS < 0.18f) {
            info.mode = PALETTE_DARK_MUTED;
        } else {
            info.mode = PALETTE_COLORFUL;
        }

        return info;
    }

    private List<Integer> extractDominantColors(Bitmap bitmap, int needed, int paletteMode) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        HueBucket[] buckets = new HueBucket[36];
        float[] hsv = new float[3];

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int c = bitmap.getPixel(x, y);
                int a = Color.alpha(c);
                if (a < 64) continue;

                Color.colorToHSV(c, hsv);
                float hDeg = hsv[0];
                float s = hsv[1];
                float v = hsv[2];

                if (v < 0.03f) continue;

                if (paletteMode == PALETTE_COLORFUL) {
                    if (s < 0.18f || v < 0.18f) continue;
                } else if (paletteMode == PALETTE_DARK_MUTED) {
                    if (v < 0.05f && s < 0.05f) continue;
                } else {
                    if (v < 0.50f && s < 0.08f) continue;
                }

                int bin = (int) (hDeg / 10f);
                if (bin < 0) bin = 0;
                if (bin > 35) bin = 35;

                HueBucket bucket = buckets[bin];
                if (bucket == null) {
                    bucket = new HueBucket();
                    bucket.hueCenter = bin * 10f + 5f;
                    buckets[bin] = bucket;
                }

                bucket.sumR += Color.red(c);
                bucket.sumG += Color.green(c);
                bucket.sumB += Color.blue(c);
                bucket.sumS += s;
                bucket.sumV += v;
                bucket.count++;
            }
        }

        List<HueBucket> list = new ArrayList<>();
        for (HueBucket bucket : buckets) {
            if (bucket == null || bucket.count == 0) continue;

            float meanS = bucket.sumS / bucket.count;
            float meanV = bucket.sumV / bucket.count;
            float vividness = meanS * 0.7f + meanV * 0.3f;

            if (paletteMode == PALETTE_COLORFUL) {
                bucket.score = bucket.count * vividness;
            } else if (paletteMode == PALETTE_DARK_MUTED) {
                bucket.score = bucket.count * (0.7f + vividness * 0.3f);
            } else {
                bucket.score = bucket.count * (1.0f + vividness * 0.2f);
            }

            list.add(bucket);
        }

        if (list.isEmpty()) {
            List<Integer> fallback = new ArrayList<>();
            fallback.add(bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() / 2));
            return fallback;
        }

        Collections.sort(list, (o1, o2) -> Float.compare(o2.score, o1.score));

        final float MIN_MAIN_HUE_DIST = 22f;
        List<HueBucket> mainBuckets = new ArrayList<>();
        mainBuckets.add(list.get(0));

        for (int i = 1; i < list.size() && mainBuckets.size() < 3; i++) {
            HueBucket candidate = list.get(i);
            boolean farEnough = true;

            for (HueBucket existing : mainBuckets) {
                float dh = Math.abs(candidate.hueCenter - existing.hueCenter);
                if (dh > 180f) dh = 360f - dh;
                if (dh < MIN_MAIN_HUE_DIST) {
                    farEnough = false;
                    break;
                }
            }

            if (farEnough) {
                mainBuckets.add(candidate);
            }
        }

        int mainCount = mainBuckets.size();

        if (mainCount == 1) {
            HueBucket b = mainBuckets.get(0);
            int avgR = (int) (b.sumR / b.count);
            int avgG = (int) (b.sumG / b.count);
            int avgB = (int) (b.sumB / b.count);
            int color = Color.rgb(avgR, avgG, avgB);

            List<Integer> only = new ArrayList<>(needed);
            for (int i = 0; i < needed; i++) {
                only.add(color);
            }
            return only;
        }

        float[] weights;
        if (mainCount == 2) {
            if (paletteMode == PALETTE_DARK_MUTED) {
                weights = new float[]{0.70f, 0.30f};
            } else if (paletteMode == PALETTE_BRIGHT_NEUTRAL) {
                weights = new float[]{0.75f, 0.25f};
            } else {
                weights = new float[]{0.65f, 0.35f};
            }
        } else {
            if (paletteMode == PALETTE_DARK_MUTED) {
                weights = new float[]{0.60f, 0.25f, 0.15f};
            } else if (paletteMode == PALETTE_BRIGHT_NEUTRAL) {
                weights = new float[]{0.70f, 0.18f, 0.12f};
            } else {
                weights = new float[]{0.55f, 0.25f, 0.20f};
            }
        }

        int[] counts = new int[mainCount];
        int primaryMin = Math.max(needed / 3, 4);
        int accentMin = 2;

        int assigned = 0;
        for (int i = 0; i < mainCount; i++) {
            int minCount = (i == 0) ? primaryMin : accentMin;
            int c = Math.round(weights[i] * needed);

            if (c < minCount) c = minCount;
            if (c > needed) c = needed;

            counts[i] = c;
            assigned += c;
        }

        if (assigned > needed) {
            int excess = assigned - needed;

            for (int i = mainCount - 1; i >= 0 && excess > 0; i--) {
                int minCount = (i == 0) ? primaryMin : accentMin;
                int reducible = counts[i] - minCount;
                if (reducible <= 0) continue;

                int delta = Math.min(reducible, excess);
                counts[i] -= delta;
                excess -= delta;
            }

            assigned = needed;
        }

        if (assigned < needed) {
            counts[0] += (needed - assigned);
        }

        List<Integer> out = new ArrayList<>(needed);
        for (int i = 0; i < mainCount; i++) {
            HueBucket b = mainBuckets.get(i);
            int avgR = (int) (b.sumR / b.count);
            int avgG = (int) (b.sumG / b.count);
            int avgB = (int) (b.sumB / b.count);
            int color = Color.rgb(avgR, avgG, avgB);

            for (int j = 0; j < counts[i]; j++) {
                out.add(color);
            }
        }

        Collections.shuffle(out, random);
        return out;
    }

    private int boostColorForVividness(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);

        if (paletteMode == PALETTE_COLORFUL) {
            hsv[1] = clampFloat(hsv[1] * 1.10f, 0.45f, 0.90f);

            float v = hsv[2];
            v = 0.50f + v * 0.30f;
            hsv[2] = clampFloat(v, 0.55f, 0.80f);
        } else if (paletteMode == PALETTE_DARK_MUTED) {
            hsv[1] = clampFloat(hsv[1] * 0.9f, 0.08f, 0.45f);

            float v = hsv[2];
            v = 0.18f + v * 0.24f;
            hsv[2] = clampFloat(v, 0.12f, 0.46f);
        } else {
            hsv[1] = clampFloat(hsv[1] * 1.1f, 0.05f, 0.35f);

            float v = hsv[2];
            v = 0.78f + v * 0.17f;
            hsv[2] = clampFloat(v, 0.78f, 0.97f);
        }


        return Color.HSVToColor(hsv);
    }

    private int buildBaseColor(int seedColor) {
        float[] hsv = new float[3];
        Color.colorToHSV(seedColor, hsv);

        if (paletteMode == PALETTE_COLORFUL) {
            hsv[1] *= 0.35f;

            float v = hsv[2];
            v = 0.13f + v * 0.19f;
            hsv[2] = clampFloat(v, 0.13f, 0.32f);
        } else if (paletteMode == PALETTE_DARK_MUTED) {
            hsv[1] *= 0.30f;

            float v = hsv[2];
            v = 0.03f + v * 0.18f;
            hsv[2] = clampFloat(v, 0.03f, 0.24f);
        } else {
            hsv[1] = Math.min(hsv[1] * 0.3f, 0.10f);

            float v = hsv[2];
            v = 0.85f + v * 0.10f;
            hsv[2] = clampFloat(v, 0.85f, 0.99f);
        }

        return Color.HSVToColor(hsv);
    }

    private void renderFrame(long dtNanos) {
        if (!isRendering.compareAndSet(false, true)) return;

        long renderStartNs = System.nanoTime();

        try {
            if (offW <= 0 || offH <= 0) return;

            int index = (renderHeadIndex + 1) % BUFFER_COUNT;
            Bitmap buffer = buffers[index];
            Canvas canvas = canvases[index];
            if (buffer == null) return;

            canvas.drawColor(baseColor);

            float dt = dtNanos / 1_000_000_000f;
            float time = (SystemClock.elapsedRealtime() - startTimeMs) / 1000f;
            float frameScale = dt * 60f;

            float safeMin = -0.3f;
            float safeMax = 1.3f;
            float targetSpeed = 0.0025f * animationSpeedMultiplier;

            for (int i = 0; i < blobs.size(); i++) {
                Blob b = blobs.get(i);

                b.vx += (random.nextFloat() - 0.5f) * 0.0005f * frameScale;
                b.vy += (random.nextFloat() - 0.5f) * 0.0005f * frameScale;

                if (b.x < safeMin) {
                    b.vx += 0.0002f * frameScale;
                } else if (b.x > safeMax) {
                    b.vx -= 0.0002f * frameScale;
                }

                if (b.y < safeMin) {
                    b.vy += 0.0002f * frameScale;
                } else if (b.y > safeMax) {
                    b.vy -= 0.0002f * frameScale;
                }

                float currentSpeed = (float) Math.hypot(b.vx, b.vy);
                if (currentSpeed > 0.00001f) {
                    float newSpeed = currentSpeed * 0.95f + targetSpeed * 0.05f;
                    float scale = newSpeed / currentSpeed;
                    b.vx *= scale;
                    b.vy *= scale;
                }

                b.x += b.vx * frameScale;
                b.y += b.vy * frameScale;

                float drawX = b.x * offW;
                float drawY = b.y * offH;

                float breathe = (float) Math.sin(
                        time * (0.5f * breathingFrequency + (i % 4) * 0.1f) + i
                ) * 0.08f;

                float radiusPx = (b.radius + breathe) * Math.max(offW, offH);

                int opaqueColor = b.color;
                int transparentColor = opaqueColor & 0x00FFFFFF;

                RadialGradient shader = new RadialGradient(
                        drawX,
                        drawY,
                        radiusPx,
                        new int[]{opaqueColor, transparentColor},
                        null,
                        Shader.TileMode.CLAMP
                );

                blobPaint.setShader(shader);
                blobPaint.setAlpha(190);
                canvas.drawCircle(drawX, drawY, radiusPx, blobPaint);
            }

            fastBoxBlurOpaque(buffer, BLUR_RADIUS, BLUR_PASSES);

            synchronized (lock) {
                renderedBitmap = buffer;
                renderHeadIndex = index;
            }

            long renderEndNs = System.nanoTime();
            updateRenderMetrics((renderEndNs - renderStartNs) / 1_000_000f);

            mainHandler.post(this::postInvalidateOnAnimation);
        } finally {
            isRendering.set(false);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Bitmap current;
        Bitmap previous;
        long transitionStart;

        synchronized (lock) {
            current = renderedBitmap;
            previous = previousBitmap;
            transitionStart = transitionStartMs;
        }

        if (current == null || current.isRecycled()) {
            canvas.drawColor(baseColor);
            if (debugOverlayEnabled) {
                drawDebugOverlay(canvas);
            }
            return;
        }

        Rect dst = new Rect(0, 0, getWidth(), getHeight());

        if (isTransitioning && previous != null && !previous.isRecycled()) {
            float progress = Math.min(
                    (SystemClock.elapsedRealtime() - transitionStart) / (float) TRANSITION_DURATION_MS,
                    1f
            );

            drawPaint.setAlpha((int) ((1f - progress) * 255));
            canvas.drawBitmap(previous, null, dst, drawPaint);

            drawPaint.setAlpha((int) (progress * 255));
            canvas.drawBitmap(current, null, dst, drawPaint);

            if (progress >= 1f) {
                isTransitioning = false;
                synchronized (lock) {
                    previousBitmap = null;
                }
            }
        } else {
            drawPaint.setAlpha(255);
            canvas.drawBitmap(current, null, dst, drawPaint);
        }

        if (debugOverlayEnabled) {
            drawDebugOverlay(canvas);
        }
    }

    private void fastBoxBlurOpaque(Bitmap srcDst, int radius, int passes) {
        if (blurBufA == null || blurBufA.length < offW * offH) return;

        srcDst.getPixels(blurBufA, 0, offW, 0, 0, offW, offH);

        for (int i = 0; i < passes; i++) {
            boxBlurHorizontal(blurBufA, blurBufB, offW, offH, radius);
            boxBlurVertical(blurBufB, blurBufA, offW, offH, radius);
        }

        srcDst.setPixels(blurBufA, 0, offW, 0, 0, offW, offH);
    }

    private static void boxBlurHorizontal(int[] src, int[] dst, int w, int h, int r) {
        final int div = r * 2 + 1;

        for (int y = 0; y < h; y++) {
            int tr = 0;
            int tg = 0;
            int tb = 0;
            int yi = y * w;

            for (int x = -r; x <= r; x++) {
                int c = src[yi + clamp(x, 0, w - 1)];
                tr += (c >> 16) & 0xFF;
                tg += (c >> 8) & 0xFF;
                tb += c & 0xFF;
            }

            for (int x = 0; x < w; x++) {
                dst[yi + x] = 0xFF000000
                        | ((tr / div) << 16)
                        | ((tg / div) << 8)
                        | (tb / div);

                int cOut = src[yi + clamp(x - r, 0, w - 1)];
                int cIn = src[yi + clamp(x + r + 1, 0, w - 1)];

                tr += ((cIn >> 16) & 0xFF) - ((cOut >> 16) & 0xFF);
                tg += ((cIn >> 8) & 0xFF) - ((cOut >> 8) & 0xFF);
                tb += (cIn & 0xFF) - (cOut & 0xFF);
            }
        }
    }

    private static void boxBlurVertical(int[] src, int[] dst, int w, int h, int r) {
        final int div = r * 2 + 1;

        for (int x = 0; x < w; x++) {
            int tr = 0;
            int tg = 0;
            int tb = 0;

            for (int y = -r; y <= r; y++) {
                int c = src[clamp(y, 0, h - 1) * w + x];
                tr += (c >> 16) & 0xFF;
                tg += (c >> 8) & 0xFF;
                tb += c & 0xFF;
            }

            for (int y = 0; y < h; y++) {
                dst[y * w + x] = 0xFF000000
                        | ((tr / div) << 16)
                        | ((tg / div) << 8)
                        | (tb / div);

                int cOut = src[clamp(y - r, 0, h - 1) * w + x];
                int cIn = src[clamp(y + r + 1, 0, h - 1) * w + x];

                tr += ((cIn >> 16) & 0xFF) - ((cOut >> 16) & 0xFF);
                tg += ((cIn >> 8) & 0xFF) - ((cOut >> 8) & 0xFF);
                tb += (cIn & 0xFF) - (cOut & 0xFF);
            }
        }
    }

    private void drawDebugOverlay(Canvas canvas) {
        String line1 = String.format("UI %.1f fps", uiFps);
        String line2 = String.format("BG %.1f fps", renderFps);
        String line3 = String.format("Render %.2f ms", avgRenderMs);
        String line4 = String.format("Jank %.1f%%", getUiJankPercent());

        float pad = dp(8);
        float lineH = dp(14);
        float boxW = dp(120);
        float boxH = pad * 2 + lineH * 4;
        float margin = dp(8);

        float right = canvas.getWidth() - margin;
        float bottom = canvas.getHeight() - margin;
        float left = right - boxW;
        float top = bottom - boxH;

        canvas.drawRoundRect(left, top, right, bottom, dp(10), dp(10), overlayBgPaint);

        float tx = left + pad;
        float ty = top + pad + lineH - dp(2);

        canvas.drawText(line1, tx, ty, overlayTextPaint);
        canvas.drawText(line2, tx, ty + lineH, overlayTextPaint);
        canvas.drawText(line3, tx, ty + lineH * 2, overlayTextPaint);
        canvas.drawText(line4, tx, ty + lineH * 3, overlayTextPaint);
    }

    private void updateUiMetrics(long dtNs) {
        if (dtNs <= 0) return;

        float instantFps = 1_000_000_000f / dtNs;
        uiFps = (uiFps == 0f) ? instantFps : (uiFps * 0.9f + instantFps * 0.1f);

        totalUiFrames++;
        if (dtNs > 20_000_000L) {
            jankyUiFrames++;
        }
    }

    private void updateRenderMetrics(float renderMs) {
        avgRenderMs = (avgRenderMs == 0f) ? renderMs : (avgRenderMs * 0.9f + renderMs * 0.1f);
        if (renderMs > worstRenderMs) {
            worstRenderMs = renderMs;
        }

        renderCountThisSecond++;

        long nowMs = SystemClock.elapsedRealtime();
        long dt = nowMs - lastMetricsSecondMs;
        if (dt >= 1000L) {
            renderFps = renderCountThisSecond * (1000f / dt);
            renderCountThisSecond = 0;
            lastMetricsSecondMs = nowMs;
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    public float getUiJankPercent() {
        if (totalUiFrames == 0) return 0f;
        return (jankyUiFrames * 100f) / totalUiFrames;
    }

    private static int clamp(int v, int lo, int hi) {
        return (v < lo) ? lo : Math.min(v, hi);
    }

    private static float clampFloat(float v, float lo, float hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    private static class PaletteInfo {
        float avgS;
        float avgV;
        int mode;
    }

    private static class HueBucket {
        long sumR;
        long sumG;
        long sumB;
        float sumS;
        float sumV;
        int count;
        float hueCenter;
        float score;
    }

    private static class Blob {
        float x;
        float y;
        float vx;
        float vy;
        float radius;
        int color;

        Blob(float x, float y, float radius, int color, float vx, float vy) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.color = color;
            this.vx = vx;
            this.vy = vy;
        }
    }
}