package com.lenerd46.spotifyplus.beautifullyrics.entities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import jp.wasabeef.blurry.Blurry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class AnimatedBackgroundView extends View {
    private final HandlerThread renderThread;
    private final Handler renderHandler;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isRendering = new AtomicBoolean(false);
    private final Object renderLock = new Object();

    private Bitmap renderedBitmap;

    private long startTimeMs;
    private List<Blob> blobs;
    private Bitmap sourceImage;
    private volatile boolean blurred = true;
    private volatile boolean isTransitioning;
    private Bitmap previousBitmap;
    private long transitionStartMs;
    private static final long TRANSITION_DURATION_MS = 2000;
    private static final float DOWNSAMPLE_FACTOR = 0.1f;
    private final Context context;
    private final ViewGroup root;

    public AnimatedBackgroundView(Context context, Bitmap bitmap, ViewGroup root) {
        super(context);
        this.context = context;
        this.root = root;

        sourceImage = bitmap;
        renderThread = new HandlerThread("BGAnim");
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());

        initBlobsAndData();
        startTimeMs = SystemClock.elapsedRealtime();
        scheduleNextFrame();
    }

    public void updateImage(Bitmap newImage) {
        sourceImage = newImage;
        scheduleNextFrame();
    }

    private void scheduleNextFrame() {
        renderHandler.removeCallbacks(renderRunnable);
        renderHandler.postDelayed(renderRunnable, 33);
    }

    private void initBlobsAndData() {
        blobs = new ArrayList<>();

        float w = sourceImage.getWidth();
        float h = sourceImage.getHeight();

        blobs.add(new Blob(w * 0.2f, h * 0.2f, 100, 1.1f, 0.1f, false)); // Top Left
        blobs.add(new Blob(w * 0.8f, h * 0.2f, 90, 1.2f, 0.3f, true)); // Top Right
        blobs.add(new Blob(w * 0.2f, h * 0.8f, 150, 1.3f, 0.5f, false)); // Bottom Left
        blobs.add(new Blob(w * 0.8f, h * 0.8f, 80, 1.1f, 0.8f, true)); // Bottom Right
        blobs.add(new Blob(w * 0.5f, h * 0.5f, 55, 1.8f, 0.2f, false)); // Center
        blobs.add(new Blob(w * 0.5f, h * 0.1f, 70, 1.1f, 0.4f, true)); // Middle Top
        // blobs.add(new Blob(w * 0.1f, h * 0.5f, 800, 1.3f, 0.6f, false)); // Middle Left - this was the really big one that sometimes goes away
        blobs.add(new Blob(w * 0.9f, h * 0.5f, 70, 1.2f, 0.7f, true)); // Middle Right
    }

    private final Runnable renderRunnable = new Runnable() {
        @Override
        public void run() {
            if(!isRendering.compareAndSet(false, true)) return;

            try {
                long now = SystemClock.elapsedRealtime();
                float t = (now - startTimeMs) / 10000f * 1.5f;
                int w = sourceImage.getWidth();
                int h = sourceImage.getHeight();

                Bitmap offBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(offBmp);

                for(int i = 0; i < blobs.size(); i++) {
                    Blob blob = blobs.get(i);

                    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    paint.setStyle(Paint.Style.FILL_AND_STROKE);
                    paint.setShader(distortImage(sourceImage, blob, t, i));
                    Path path = createBlobPath(blob, t, i);
                    canvas.drawPath(path, paint);
                }

                if(blurred) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ColorMatrix matrix = new ColorMatrix();
                        matrix.setSaturation(2f);

                        RenderEffect color = RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(matrix));
                        RenderEffect blur = RenderEffect.createBlurEffect(400f, 400f, Shader.TileMode.CLAMP);

                        setRenderEffect(RenderEffect.createChainEffect(color, blur));
                    } else {
                        offBmp = blurBitmapOld(offBmp);
                    }
                }

                Bitmap toShow = offBmp;
                if(isTransitioning && previousBitmap != null) {
                    long elapsed = now - transitionStartMs;
                    float progress = Math.min(elapsed / (float)TRANSITION_DURATION_MS, 1f);
                    toShow = blendBitmaps(previousBitmap, offBmp, progress);

                    if(progress >= 1f) {
                        isTransitioning = false;
                        previousBitmap.recycle();
                        previousBitmap = null;
                    }
                }

                synchronized(renderLock) {
                    if(renderedBitmap != null) renderedBitmap.recycle();

                    renderedBitmap = toShow;
                }

                mainHandler.post(() -> invalidate());
            } catch(Throwable t) {
            } finally {
                isRendering.set(false);
                scheduleNextFrame();
            }
        }
    };

    @SuppressLint("NewApi")
    private Bitmap blurBitmapOld(Bitmap input) {
        Bitmap output = Bitmap.createBitmap(input.getWidth(), input.getHeight(), Bitmap.Config.ARGB_8888);

        RenderScript rs = RenderScript.create(getContext());
        Allocation allocIn = Allocation.createFromBitmap(rs, input);
        Allocation allocOut = Allocation.createFromBitmap(rs, output);

        ScriptIntrinsicBlur blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        blur.setRadius(25f);
        blur.setInput(allocIn);
        blur.forEach(allocOut);

        allocOut.copyTo(output);
        allocIn.destroy();
        allocOut.destroy();
        blur.destroy();
        rs.destroy();

        return output;
    }

    private Bitmap blendBitmaps(Bitmap a, Bitmap b, float t) {
        int w = a.getWidth(), h = a.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // draw A fading out
        p.setAlpha((int)((1 - t) * 255));
        c.drawBitmap(a, 0, 0, p);
        // draw B fading in
        p.setAlpha((int)(t * 255));
        c.drawBitmap(b, 0, 0, p);

        return out;
    }

    private Path createBlobPath(Blob blob, float time, int index) {
        final int numPoints = 12;
        final float angleStep = 360f / numPoints;

        Path path = new Path();
        for(int i = 0; i < numPoints; i++) {
            float angleRad = (float)Math.toRadians(i * angleStep);
            float offset = (float)(Math.sin(angleRad * 3 + time * 2 + index) * blob.radius / 5 * 0.05f);

            float x = blob.centerX + (blob.radius + offset) * (float)Math.cos(angleRad) * 3;
            float y = blob.centerY + (blob.radius + offset) * (float)Math.sin(angleRad) * 3;

            if(i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }

        path.close();
        return path;
    }

    private Shader distortImage(Bitmap image, Blob blob, float time, int index) {
        float sx = blob.scale + 0.2f * (float)Math.sin(time + index);
        float sy = blob.scale + 0.2f * 0.6f * (float)Math.cos(time + index);
        float rot = (blob.rotation + time * 0.3f * index) * (blob.opposite ? -1 : 1);
        float skewX = 0.6f * (float)Math.sin(time * (0.5f + index * 0.1f));
        float skewY = 0.4f * (float)Math.cos(time * (0.3f + index * 0.1f));

        Matrix m = new Matrix();
        m.postRotate(rot);
        m.postScale(sx, sy);
        m.postTranslate(blob.centerX, blob.centerY);
        m.postSkew(skewX, skewY);

        BitmapShader shader = new BitmapShader(image, Shader.TileMode.MIRROR, Shader.TileMode.REPEAT);
        shader.setLocalMatrix(m);

        return shader;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Bitmap bmp;
        synchronized(renderLock) {
            bmp = renderedBitmap;
        }

        if(bmp != null) {
            Rect dst = new Rect(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(bmp, null, dst, null);
        } else {
            canvas.drawColor(Color.TRANSPARENT);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        renderHandler.removeCallbacksAndMessages(null);
        renderThread.quitSafely();

        synchronized (renderLock) {
            if(renderedBitmap != null) renderedBitmap.recycle();
            if(previousBitmap != null) previousBitmap.recycle();
        }
    }
}
