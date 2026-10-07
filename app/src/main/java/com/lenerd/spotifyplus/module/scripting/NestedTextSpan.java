package com.lenerd.spotifyplus.module.scripting;

import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

/** One inherited style at a leaf of a nested React Text tree. */
final class NestedTextSpan extends MetricAffectingSpan {
    private final Typeface typeface;
    private final float size;
    private final Integer color;
    private final Integer background;
    private final String decoration;

    NestedTextSpan(Typeface typeface, float size, Integer color, Integer background, String decoration) {
        this.typeface = typeface;
        this.size = size;
        this.color = color;
        this.background = background;
        this.decoration = decoration;
    }

    @Override public void updateMeasureState(TextPaint paint) { apply(paint); }
    @Override public void updateDrawState(TextPaint paint) { apply(paint); }

    private void apply(TextPaint paint) {
        paint.setTypeface(typeface);
        if (size > 0) paint.setTextSize(size);
        if (color != null) paint.setColor(color);
        if (background != null) paint.bgColor = background;
        paint.setUnderlineText(decoration.contains("underline"));
        paint.setStrikeThruText(decoration.contains("line-through"));
    }
}
