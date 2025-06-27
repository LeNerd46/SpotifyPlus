package com.lenerd46.spotifyplus.beautifullyrics.entities.lyrics;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Pair;
import com.google.android.flexbox.FlexboxLayout;
import com.lenerd46.spotifyplus.References;
import com.lenerd46.spotifyplus.beautifullyrics.entities.*;
import de.robv.android.xposed.XposedBridge;

import java.util.List;
import java.util.stream.Collectors;

public class LineVocals implements SyncableVocals {
    public final ActivityChangedSource activityChanged;

    public FlexboxLayout container;
    public GradientTextView lyricText;

    public final double startTime;
    public final double duration;

    private boolean active;
    private LyricState state;
    private boolean isSleeping = true;

    private Spline glowSpline;
    private final Spring glowSpring;
    private final SharedPreferences prefs;

    private final List<Pair<Double, Double>> glowRange = List.of(
            Pair.create(0d, 0d),
            Pair.create(0.5d, 1d),
            Pair.create(0.925d, 1d),
            Pair.create(1d, 0d)
    );

    private Spline getSpline(List<Pair<Double, Double>> range) {
        return new Spline(range.stream().map(e -> e.first).collect(Collectors.toList()), range.stream().map(e -> e.second).collect(Collectors.toList()));
    }

    public LineVocals(FlexboxLayout container, LineVocal vocal, boolean isRomanized, Activity activity) {
        this.container = container;
        activityChanged = new ActivityChangedSource();
        prefs = activity.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);

        this.startTime = vocal.startTime;
        this.duration = vocal.endTime - vocal.startTime;

        glowSpline = getSpline(glowRange);
        glowSpring = new Spring(0.0, 0.5, 1.0);

        lyricText = new GradientTextView(activity);
        lyricText.setText(isRomanized ? vocal.romanizedText : vocal.text);
        lyricText.setLineState(true);

        lyricText.setTextColor(Color.WHITE);
        lyricText.setTextSize(30f);
        lyricText.setPadding(0, 0, 1, 0);
        lyricText.setTypeface(References.beautifulFont.get());

        if(vocal.oppositeAligned) {
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

        if(forceTo) {
            glowSpring.set(glowAlpha);
        } else {
            glowSpring.finalPosition = glowAlpha;
        }
    }

    private boolean updateLiveTextVisuals(double timeScale, double deltaTime) {
        float glowAlpha = (float)glowSpring.update(deltaTime);

        lyricText.updateShadow(glowAlpha * 0.5f, 4 + (8 * glowAlpha));
        lyricText.setProgress((float)(120 * timeScale));

        return glowSpring.sleeping;
    }

    private void evaluateClassState() {
        if(state == LyricState.ACTIVE) {
            lyricText.setTextColor(Color.argb(255, 255, 255, 255));
            lyricText.setGradientColors(prefs.getBoolean("lyric_enable_line_gradient", true) ? new int[] { 0xFFFFFFFF, 0x78FFFFFF } : new int[] { 0xFFFFFFFF, 0xFFFFFFFF});
        } else if(state == LyricState.SUNG) {
            lyricText.setTextColor(Color.argb(100, 255, 255, 255));
            lyricText.updateShadow(0f, 0f);

//            updateLiveTextVisuals(0, 1.0 / 60);
        } else {
            lyricText.setTextColor(Color.argb(255, 255, 255, 255));
            lyricText.setGradientColors(new int[] { 0xFFFFFFFF, 0x3CFFFFFF});

//            updateLiveTextVisuals(0, 1.0 / 60);
        }
    }

    @Override
    public void animate(double songTimestamp, double deltaTime, boolean isImmediate) {
        double relativeTime = songTimestamp - this.startTime;
        double timeScale = Math.max(0, Math.min((double)relativeTime / (double)this.duration, 1.0));

        boolean pastStart = relativeTime >= 0;
        boolean beforeEnd = relativeTime <= this.duration;
        boolean isActive = pastStart && beforeEnd;

        LyricState stateNow = isActive ? LyricState.ACTIVE : pastStart ? LyricState.SUNG : LyricState.IDLE;

        boolean stateChanged = stateNow != this.state;
        boolean shouldUpdateVisualState = stateChanged || isActive || isImmediate;

        if(stateChanged) {
            this.state = stateNow;
            evaluateClassState();

            if(this.state == LyricState.ACTIVE) {
                activityChanged.invoke(new ScrollInformation(container, isImmediate));
            }
        } else if(this.state == LyricState.ACTIVE && isImmediate) {
            activityChanged.invoke(new ScrollInformation(container, true));
        }

        if(shouldUpdateVisualState) {
            this.isSleeping = false;

            updateLiveTextState(timeScale, (isImmediate || (relativeTime < 0)));
        }

        if(!this.isSleeping) {
            boolean isSleeping = updateLiveTextVisuals(timeScale, deltaTime);

            if(isSleeping) {
                this.isSleeping = true;

                if(!this.active) {
                    evaluateClassState();
                }
            }
        }
    }

    @Override
    public boolean isActive() {
        return this.active;
    }
}