package com.lenerd46.spotifyplus.entities;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatTextView;
import de.robv.android.xposed.XposedBridge;

public class GradientTextView extends AppCompatTextView {
    private int[] gradientColors = { 0xFFFFFFFF, 0x80FFFFFF };
    private float progress = 0f;

    private float shadowOpacity = 0f;
    private float shadowRadius = 0f;
    private boolean isLine = false;

    public GradientTextView(Context context) {
        super(context);
    }

    public GradientTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public GradientTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Paint paint = getPaint();
        float width = paint.measureText(getText().toString());
        float height = getHeight();

        final float fadeWidth = 0.5f;
        final float fadeWidthLine = 0.5f;
        float gradientProgress = Math.max(0f, Math.min(progress, 1f));

        float startFade = Math.max(0f, gradientProgress - (isLine ? fadeWidthLine : fadeWidth) / 2f);
        float endFade = Math.min(1f, gradientProgress + (isLine ? fadeWidthLine : fadeWidth) / 2f);

        Shader textShader;

        if(!isLine) {
            if(gradientProgress <= 0f) {
                textShader = new LinearGradient(0, 0, width, 0, new int[] { gradientColors[1], gradientColors[1] }, null, Shader.TileMode.CLAMP);
            } else if(gradientProgress >= 1f) {
                textShader = new LinearGradient(0, 0, width, 0, new int[] { gradientColors[0], gradientColors[0] }, null, Shader.TileMode.CLAMP);
            } else {
                textShader = new LinearGradient(0, 0, width, 0, gradientColors, new float[] { startFade, endFade }, Shader.TileMode.CLAMP);
            }
        } else {
            if(gradientProgress <= 0f) {
                textShader = new LinearGradient(0, 0, 0, height, new int[] { gradientColors[1], gradientColors[1] }, null, Shader.TileMode.CLAMP);
            } else if(gradientProgress >= 1f) {
                textShader = new LinearGradient(0, 0, 0, height, new int[] { gradientColors[0], gradientColors[0] }, null, Shader.TileMode.CLAMP);
            } else {
                textShader = new LinearGradient(0, 0, 0, height, gradientColors, new float[] { startFade, endFade }, Shader.TileMode.CLAMP);
            }
        }

        paint.setShader(textShader);
        paint.setShadowLayer(shadowRadius, 0f, 0f, Color.argb(255 * (shadowOpacity / 100f), 255, 255, 255));

        super.onDraw(canvas);
    }

    public void setGradientColors(int[] gradientColors) {
        this.gradientColors = gradientColors;
        invalidate();
    }

    public void setLineState(boolean isLine) {
        this.isLine = isLine;
    }

    public void updateShadow(float opacity, float radius) {
        this.shadowOpacity = opacity;
        this.shadowRadius = radius;

        invalidate();
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(progress / 100f, 1f));
        invalidate();
    }
}
