package com.lenerd46.spotifyplus.beautifullyrics.sync;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.view.animation.DecelerateInterpolator;
import com.lenerd46.spotifyplus.beautifullyrics.entities.GradientTextView;

/** Touch-driven counterpart to the normal lyric scale, glow and sung colors. */
final class LyricsSyncWordVisual {
    enum State { IDLE, NEXT, HELD, SUNG }
    private final GradientTextView view;
    private final boolean background;
    private State state;
    private ValueAnimator animation;
    private float emphasis;
    private int color = 0xBEFFFFFF;

    LyricsSyncWordVisual(GradientTextView view, boolean background) {
        this.view = view;
        this.background = background;
    }

    void setState(State next) {
        if (state == next) return;
        boolean immediate = state == null;
        state = next;
        if (animation != null) animation.cancel();
        float fromEmphasis = emphasis;
        int fromColor = color;
        float targetEmphasis = next == State.HELD ? 1f : 0f;
        int targetColor = next == State.SUNG ? (background ? 0x48FFFFFF : 0x64FFFFFF)
                : next == State.IDLE ? 0xBEFFFFFF : Color.WHITE;
        if (immediate) { apply(targetColor, targetEmphasis); return; }
        ArgbEvaluator evaluator = new ArgbEvaluator();
        animation = ValueAnimator.ofFloat(0, 1);
        animation.setDuration(next == State.HELD ? 180 : 240);
        animation.setInterpolator(new DecelerateInterpolator());
        animation.addUpdateListener(value -> {
            float t = (float) value.getAnimatedValue();
            apply((int) evaluator.evaluate(t, fromColor, targetColor), fromEmphasis + (targetEmphasis - fromEmphasis) * t);
        });
        animation.start();
    }

    private void apply(int color, float emphasis) {
        this.color = color;
        this.emphasis = emphasis;
        float density = view.getResources().getDisplayMetrics().density;
        view.setTextColor(Color.WHITE);
        view.setGradientColors(new int[]{color, color});
        view.setProgress(100);
        view.setScaleX(1f + .03f * emphasis);
        view.setScaleY(1f + .03f * emphasis);
        view.setTranslationY(-2f * density * emphasis);
        // GradientTextView takes opacity as a percentage.
        view.updateShadow(35f * emphasis, 8f * density * emphasis);
    }

    void dispose() { if (animation != null) animation.cancel(); }
}
