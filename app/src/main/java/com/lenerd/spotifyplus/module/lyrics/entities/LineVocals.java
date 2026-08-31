package com.lenerd.spotifyplus.module.lyrics.entities;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Pair;
import android.util.TypedValue;
import com.google.android.flexbox.FlexboxLayout;
import com.lenerd.spotifyplus.module.SpotifyPlusSettings;
import com.lenerd.spotifyplus.module.Utils;
import com.lenerd.spotifyplus.module.lyrics.Spline;
import com.lenerd.spotifyplus.module.lyrics.Spring;
import com.lenerd.spotifyplus.module.lyrics.entities.lyrics.LineVocal;

import java.util.List;
import java.util.stream.Collectors;

public class LineVocals implements SyncableVocals {
    public final ActivityChangedSource activityChanged;

    public FlexboxLayout container;
    public GradientTextView lyricText;

    public final double startTime;
    public final double duration;

    private LyricState state;
    private boolean isSleeping = true;

    private final Spline glowSpline;
    private final Spring glowSpring;

    private Spline getSpline(List<Pair<Double, Double>> range) {
        return new Spline(range.stream().map(e -> e.first).collect(Collectors.toList()), range.stream().map(e -> e.second).collect(Collectors.toList()));
    }

    public LineVocals(FlexboxLayout container, LineVocal vocal, Activity activity, int fontSize) {
        this.container = container;
        activityChanged = new ActivityChangedSource();

        this.startTime = vocal.startTime;
        this.duration = vocal.endTime - vocal.startTime;

        List<Pair<Double, Double>> glowRange = List.of(
                Pair.create(0d, 0d),
                Pair.create(0.5d, 1d),
                Pair.create(0.925d, 1d),
                Pair.create(1d, 0d)
        );

        glowSpline = getSpline(glowRange);
        glowSpring = new Spring(0.0, 0.5, 1.0);

        lyricText = new GradientTextView(activity);
        lyricText.setText(vocal.text);
        lyricText.setLineState(true);

        lyricText.setTextColor(Color.WHITE);
        lyricText.setTextSize(fontSize);
        lyricText.setPadding(0, 0, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, activity.getResources().getDisplayMetrics()), 0);
        lyricText.setTypeface(Utils.loadTypeface(activity, SpotifyPlusSettings.activeFont));

        if (vocal.oppositeAligned) {
            lyricText.setTextAlignment(GradientTextView.TEXT_ALIGNMENT_TEXT_END);
        }

        container.addView(lyricText);
        setToGeneralState(false);
    }

    private void setToGeneralState(boolean state) {
        int timeScale = state ? 1 : 0;

        updateLiveTextState(timeScale, true);
        updateLiveTextVisuals(timeScale, 0);

        this.state = state ? LyricState.SUNG : LyricState.IDLE;
        evaluateClassState();
    }

    private void updateLiveTextState(double timeScale, boolean forceTo) {
        double glowAlpha = glowSpline.at(timeScale);

        if (forceTo) {
            glowSpring.set(glowAlpha);
        } else {
            glowSpring.finalPosition = glowAlpha;
        }
    }

    private boolean updateLiveTextVisuals(double timeScale, double deltaTime) {
        float glowAlpha = (float) glowSpring.update(deltaTime);

        if (SpotifyPlusSettings.lineGradient) {
            lyricText.updateShadow(glowAlpha * 0.5f, 4 + (8 * glowAlpha));
            lyricText.setProgress((float) (120 * timeScale));
        }

        return glowSpring.sleeping;
    }

    private void evaluateClassState() {
        if (state == LyricState.ACTIVE) {
            lyricText.setTextColor(Color.argb(255, 255, 255, 255));
            lyricText.setGradientColors(SpotifyPlusSettings.lineGradient ? new int[]{0xFFFFFFFF, 0x78FFFFFF} : new int[]{0xFFFFFFFF, 0xFFFFFFFF});
        } else if (state == LyricState.SUNG) {
            lyricText.setTextColor(Color.argb(100, 255, 255, 255));
            lyricText.updateShadow(0f, 0f);
        } else {
            lyricText.setTextColor(Color.argb(255, 255, 255, 255));
            lyricText.setGradientColors(new int[]{0xFFFFFFFF, 0x3CFFFFFF});
        }
    }

    @Override
    public void animate(double songTimestamp, double deltaTime, boolean isImmediate) {
        double relativeTime = songTimestamp - this.startTime;
        double timeScale = Math.max(0, Math.min((double) relativeTime / (double) this.duration, 1.0));

        boolean pastStart = relativeTime >= 0;
        boolean beforeEnd = relativeTime <= this.duration;
        boolean isActive = pastStart && beforeEnd;

        LyricState stateNow = isActive ? LyricState.ACTIVE : pastStart ? LyricState.SUNG : LyricState.IDLE;

        boolean stateChanged = stateNow != this.state;
        boolean shouldUpdateVisualState = stateChanged || isActive || isImmediate;

        if (stateChanged) {
            this.state = stateNow;
            evaluateClassState();

            if (this.state == LyricState.ACTIVE) {
                activityChanged.invoke(new ScrollInformation(container, isImmediate));
            }
        } else if (this.state == LyricState.ACTIVE && isImmediate) {
            activityChanged.invoke(new ScrollInformation(container, true));
        }

        if (shouldUpdateVisualState) {
            this.isSleeping = false;

            updateLiveTextState(timeScale, (isImmediate || (relativeTime < 0)));
        }

        if (!this.isSleeping) {
            boolean isSleeping = updateLiveTextVisuals(timeScale, deltaTime);

            if (isSleeping) {
                this.isSleeping = true;
            }
        }
    }
}