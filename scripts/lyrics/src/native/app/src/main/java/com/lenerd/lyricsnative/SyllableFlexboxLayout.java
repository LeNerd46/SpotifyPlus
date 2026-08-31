package com.lenerd.lyricsnative;

import android.annotation.SuppressLint;
import android.content.Context;
import com.google.android.flexbox.FlexboxLayout;

public class SyllableFlexboxLayout extends FlexboxLayout {
    public double startTime;
    public double duration;
    GradientText.LyricState state = GradientText.LyricState.IDLE;

    public SyllableFlexboxLayout(Context context) {
        super(context);
    }

    public void animateLine(double timestamp, double deltaTime, boolean isImmediate) {
        double relativeTime = timestamp - this.startTime;

        boolean pastStart = relativeTime >= 0;
        boolean beforeEnd = relativeTime <= this.duration;
        boolean isActive = pastStart && beforeEnd;

        GradientText.LyricState stateNow = isActive ? GradientText.LyricState.ACTIVE : pastStart ? GradientText.LyricState.SUNG : GradientText.LyricState.IDLE;

        boolean stateChanged = stateNow != this.state;
        boolean shouldUpdateVisualState = stateChanged || isActive || isImmediate;

        if(stateChanged) {
            this.state = stateNow;

            // Scroll
        }

        boolean isSleeping1 = !shouldUpdateVisualState;
        boolean isMoving = !isSleeping1;

        if(shouldUpdateVisualState || isMoving) {
            double timeScale = Math.max(0, Math.min((double)relativeTime / (double)this.duration, 1));
            boolean isSleeping = true;

            for(int i = 0; i < this.getChildCount(); i++) {
                GradientTextView syllable = (GradientTextView) this.getChildAt(i);

                double syllableTimeScale = Math.max(0, Math.min((double)(timeScale - syllable.startScale) / (double)syllable.durationScale, 1));

                if(shouldUpdateVisualState) {

                }
            }
        }
    }
}
