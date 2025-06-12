package com.lenerd46.spotifyplus.entities;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import androidx.core.content.res.ResourcesCompat;
import com.google.android.flexbox.FlexboxLayout;
import com.lenerd46.spotifyplus.entities.lyrics.SyllableMetadata;
import de.robv.android.xposed.XposedBridge;
import org.mozilla.javascript.Evaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SyllableVocals implements SyncableVocals {
    public final FlexboxLayout container;

    public final double startTime;
    public final double duration;
    public final List<AnimatedSyllable> syllables;
    public final boolean isBackground;
    private final Activity activity;

    public boolean active;
    private LyricState state;
    private boolean isSleeping;


    public final ActivityChangedSource activityChanged;

    public SyllableVocals(FlexboxLayout lineContainer, List<SyllableMetadata> syllables, boolean isBackground, boolean isRomanized, boolean oppositeAligned, Activity activity) {
        this.container = lineContainer;
        List<View> views = new ArrayList<>();
        this.activity = activity;
        this.syllables = new ArrayList<>();
        activityChanged = new ActivityChangedSource();

        active = false;
        this.isBackground = isBackground;

        startTime = syllables.get(0).startTime;
        duration = syllables.get(syllables.size() - 1).endTime - startTime + 0.3d;

        List<List<SyllableMetadata>> syllableGroups = new ArrayList<>();
        List<SyllableMetadata> currentGroup = new ArrayList<>();

        List<View> visualElements = new ArrayList<>();

        // Go through and create our syllable groups
        for(var syllableMetadata : syllables) {
            currentGroup.add(syllableMetadata);

            if(!syllableMetadata.isPartOfWord) {
                syllableGroups.add(currentGroup);
                currentGroup = new ArrayList<>();
            }
        }

        if(!currentGroup.isEmpty()) {
            syllableGroups.add(currentGroup);
        }

        // Go through and start building our visuals
        for(var syllableGroup : syllableGroups) {
            int syllableCount = syllableGroup.size();
            boolean isInWordGroup = syllableCount > 1;

            int index = 0;

            LinearLayout wordGroup = null;
            boolean firstSyllable = true;

            for(var syllableMetadata : syllableGroup) {
                boolean isEmphasized = syllableMetadata.endTime - syllableMetadata.startTime >= 1 && (isRomanized ? syllableMetadata.romanizedText.length() <= 12 : syllableMetadata.text.length() <= 12);

                GradientTextView textView = new GradientTextView(activity);

                List<AnimatedLetter> letters = new ArrayList<>();
                LinearLayout emphasisGroup = new LinearLayout(activity);
                emphasisGroup.setOrientation(LinearLayout.HORIZONTAL);

                if(!syllableMetadata.isPartOfWord) {
                    if(!isEmphasized) {
                        if(wordGroup != null) {
                            if(isBackground) { backgroundLyricLabelStyle(textView); }  else { lyricLabelStyle(textView); }
                            textView.setText(isRomanized ? syllableMetadata.romanizedText : syllableMetadata.text);

                            wordGroup.addView(textView);
                            views.add(wordGroup);

                            wordGroup = null;
                        } else {
                            if(isBackground) { backgroundLyricLabelStyle(textView); }  else { lyricLabelStyle(textView); }
                            textView.setText(isRomanized ? syllableMetadata.romanizedText : syllableMetadata.text);

                            views.add(textView);
                        }
                    } else {
                        List<String> letterTexts = (isRomanized ? syllableMetadata.romanizedText : syllableMetadata.text).chars().mapToObj(c -> String.valueOf((char) c)).collect(Collectors.toList());
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        params.setMargins(0, 0, 2, 0);
                        emphasisGroup.setLayoutParams(params);

                        double relativeTimestep = 1d / letterTexts.size();

                        letters.clear();
                        double relativeTimestamp = 0;

                        for(var letter : letterTexts) {
                            GradientTextView letterView = new GradientTextView(activity);
                            letterView.setText(letter);

                            if(isBackground) { backgroundEmphasizedLyricLabelStyle(letterView); }  else { emphasizedLyricLabelStyle(letterView); }

                            emphasisGroup.addView(letterView);

                            LiveText liveText = new LiveText(letterView, createSprings());
                            AnimatedLetter animatedLetter = new AnimatedLetter(relativeTimestamp, relativeTimestep, 1 - relativeTimestamp, liveText);
                            letters.add(animatedLetter);

                            relativeTimestamp += relativeTimestep;
                        }

                        if(wordGroup != null) {
                            wordGroup.addView(emphasisGroup);
                            views.add(wordGroup);
                        } else {
                            views.add(emphasisGroup);
                        }
                    }
                } else {
                    if(!isEmphasized) {
                        if(wordGroup == null) { wordGroup = new LinearLayout(activity); wordGroup.setOrientation(LinearLayout.HORIZONTAL); }

                        if(isBackground) { backgroundEmphasizedLyricLabelStyle(textView); } else { emphasizedLyricLabelStyle(textView); }
                        textView.setText(isRomanized ? syllableMetadata.romanizedText : syllableMetadata.text);

                        wordGroup.addView(textView);
                    } else {
                        List<String> letterTexts = (isRomanized ? syllableMetadata.romanizedText : syllableMetadata.text).chars().mapToObj(c -> String.valueOf((char) c)).collect(Collectors.toList());
                        double relativeTimestep = 1d / letterTexts.size();

                        letters.clear();
                        double relativeTimestamp = 0;

                        for(var letter : letterTexts) {
                            GradientTextView letterView = new GradientTextView(activity);
                            letterView.setText(letter);
                            if(isBackground) { backgroundEmphasizedLyricLabelStyle(letterView); }  else { backgroundLyricLabelStyle(letterView); }

                            emphasisGroup.addView(letterView);

                            LiveText liveText = new LiveText(letterView, createSprings());
                            AnimatedLetter animatedLetter = new AnimatedLetter(relativeTimestamp, relativeTimestep, 1 - relativeTimestamp, liveText);
                            letters.add(animatedLetter);

                            relativeTimestamp += relativeTimestep;
                        }

                        if(wordGroup == null) { wordGroup = new LinearLayout(activity); wordGroup.setOrientation(LinearLayout.HORIZONTAL); }
                        wordGroup.addView(emphasisGroup);
                    }
                }

                double relativeStart = syllableMetadata.startTime - startTime;
                double relativeEnd = syllableMetadata.endTime - startTime;

                double relativeStartScale = relativeStart / duration;
                double relativeEndScale = relativeEnd / duration;

                double duration = relativeEnd - relativeStart;
                double durationScale = relativeEndScale - relativeStartScale;

                LiveText liveText = new LiveText(isEmphasized ? emphasisGroup : textView, createSprings());

                if(isEmphasized) {
                    AnimatedSyllable animatedSyllable = new AnimatedSyllable(relativeStart, duration, relativeStartScale, durationScale, liveText, "Letters", letters);
                    this.syllables.add(animatedSyllable);
                } else {
                    AnimatedSyllable animatedSyllable = new AnimatedSyllable(relativeStart, duration, relativeStartScale, durationScale, liveText, "Syllable", null);
                    this.syllables.add(animatedSyllable);
                }

                index++;
            }
        }

        views.forEach(this.container::addView);
        setToGeneralState(false);
    }

    private Springs createSprings() {
        return new Springs(new Spring(0, 0.6d, 0.7d), new Spring(0, 0.4d, 1.25d), new Spring(0, 0.5d, 1d));
    }

    private void setToGeneralState(boolean state) {
        double timeScale = state ? 1 : 0;

        for(var syllable : syllables) {
            updateLiveTextState(syllable.liveText, timeScale, timeScale, true);
            updateLiveTextVisuals(syllable.liveText, false, timeScale, 0);

            if(syllable.type.equals("Letters")) {
                for(var letter : syllable.letters) {
                    updateLiveTextState(letter.liveText, timeScale, timeScale, true);
                    updateLiveTextVisuals(letter.liveText, true, timeScale, 0);
                }
            }
        }

        this.state = state ? LyricState.SUNG : LyricState.IDLE;
    }

    private void evaluateClassState() {
        try {
            if(this.state == LyricState.ACTIVE) {
                for(var syllable : this.syllables) {
                    if(syllable.type.equals("Letters")) {
                        for(var letter : syllable.letters) {
                            GradientTextView text = (GradientTextView) letter.liveText.object;
                            text.setTextColor(Color.argb(255, 255, 255, 255));
                        }
                    } else {
                        GradientTextView text = (GradientTextView) syllable.liveText.object;
                        text.setTextColor(Color.argb(255, 255, 255, 255));
                    }
                }
            } else if(this.state == LyricState.SUNG) {
                for(var syllable : this.syllables) {
                    if(syllable.type.equals("Letters")) {
                        for(var letter : syllable.letters) {
                            GradientTextView text = (GradientTextView) letter.liveText.object;
                            text.setTextColor(Color.argb(120, 224, 224, 224));
                            text.updateShadow(0f, 0f);
                        }
                    } else {
                        GradientTextView text = (GradientTextView) syllable.liveText.object;
                        text.setTextColor(Color.argb(120, 224, 224, 224));
                        text.updateShadow(0f, 0f);
                    }
                }
            } else {
                for(var syllable : this.syllables) {
                    if(syllable.type.equals("Letters")) {
                        for(var letter : syllable.letters) {
                            GradientTextView text = (GradientTextView) letter.liveText.object;
                            text.setTextColor(Color.argb(90, 255, 255, 255));
                            text.updateShadow(0f, 0f);
                            text.updateShadow(0f, 0f);
                        }
                    } else {
                        GradientTextView text = (GradientTextView) syllable.liveText.object;
                        text.setTextColor(Color.argb(90, 255, 255, 255));
                        text.updateShadow(0f, 0f);
                    }
                }
            }
        } catch(Exception e) {
            XposedBridge.log(e);
        }
    }

    public void updateLiveTextState(LiveText liveText, double timeScale, double glowTimeScale, boolean forceTo) {
        Spline scaleSpline = getSpline(scaleRange);
        Spline yOffsetSpline = getSpline(yOffsetRange);
        Spline glowSpline = getSpline(glowRange);

        double scale = scaleSpline.at(timeScale);
        double yOffset = yOffsetSpline.at(timeScale);
        double glow = glowSpline.at(glowTimeScale);

        if(forceTo) {
            liveText.springs.scale.set(scale);
            liveText.springs.yOffset.set(yOffset);
            liveText.springs.glow.set(glow);
        } else {
            liveText.springs.scale.finalPosition = scale;
            liveText.springs.yOffset.finalPosition = yOffset;
            liveText.springs.glow.finalPosition = glow;
        }
    }

    private boolean updateLiveTextVisuals(LiveText liveText, boolean isEmphasized, double timeScale, double deltaTime) {
        double scale = liveText.springs.scale.update(deltaTime);
        double yOffset = liveText.springs.yOffset.update(deltaTime) * 100;
        double glow = Math.abs(liveText.springs.glow.update(deltaTime));

        float gradientProgress = (int)Math.round(-20 + 120 * timeScale);
        float shadowRadius = 4 * (2 * (float)glow * (isEmphasized ? 3f : 1f));
        float shadowOpacity = (float)Math.max(0, Math.min(1, glow * (isEmphasized ? 1f : 0.35f)));

        if(liveText.object instanceof GradientTextView) {
            if(Double.isNaN(scale) || Double.isInfinite(scale)) { return false; }

//            XposedBridge.log("[SpotifyPlus] Scale: " + scale + " YOffset: " + yOffset + " Glow: " + glow + " Gradient: " + gradientProgress);

            GradientTextView textView = (GradientTextView)liveText.object;
            textView.setScaleX((float)scale);
            textView.setScaleY((float)scale);

            textView.setTranslationY((float)yOffset * (isEmphasized ? 2f : 1f));
            textView.setProgress(gradientProgress);

//            XposedBridge.log("[SpotifyPlus] Shadow Opacity: " + shadowOpacity + " Shadow Radius: " + shadowRadius);
            textView.updateShadow(shadowOpacity, shadowRadius);
        }

        return liveText.springs.scale.sleeping && liveText.springs.yOffset.sleeping && liveText.springs.glow.sleeping;
    }

    private Spline getSpline(List<Map.Entry<Double, Double>> range) {
        return new Spline(range.stream().map(Map.Entry::getKey).collect(Collectors.toList()), range.stream().map(Map.Entry::getValue).collect(Collectors.toList()));
    }

    @Override
    public void animate(double songTimestamp, double deltaTime, boolean isImmediate) {
        double relativeTime = songTimestamp - this.startTime;

        boolean pastStart = relativeTime >= 0;
        boolean beforeEnd = relativeTime <= this.duration;
        boolean isActive = pastStart && beforeEnd;
        this.active = isActive;

        LyricState stateNow = isActive ? LyricState.ACTIVE : pastStart ? LyricState.SUNG : LyricState.IDLE;

        boolean stateChanged = stateNow != this.state;
        boolean shouldUpdateVisualState = stateChanged || isActive || isImmediate;

        if(stateChanged) {
            this.state = stateNow;
            evaluateClassState();

            // Trigger scrolling event
            activityChanged.invoke(container);
        }

        this.isSleeping = !shouldUpdateVisualState;
        boolean isMoving = this.isSleeping == false;

        if(shouldUpdateVisualState || isMoving) {
            double timeScale = Math.max(0, Math.min((double)relativeTime / (double)this.duration, 1));
            boolean isSleeping = true;
//            XposedBridge.log("[SpotifyPlus] Time Scale: " + timeScale + " Relative Time: " + relativeTime + " Song Timestamp: " + songTimestamp + " Delta Time: " + deltaTime + " Is Immediate: " + isImmediate + " Start Time: " + this.startTime + " Duration: " + this.duration);

            for(var syllable : this.syllables) {
                double syllableTimeScale = Math.max(0, Math.min((double)(timeScale - syllable.startScale) / (double)syllable.durationScale, 1));

                if(syllable.type.equals("Letters")) {
                    double timeAlpha = Math.sin(syllableTimeScale * (Math.PI / 2));

                    for(var letter : syllable.letters) {
                        double letterTime = timeAlpha - letter.start;
                        double letterTimeScale = Math.max(0, Math.min(letterTime / letter.duration, 1));
                        double glowTimeScale = Math.max(0, Math.min(letterTime / letter.glowDuration, 1));

                        if(shouldUpdateVisualState) {
                            updateLiveTextState(letter.liveText, letterTimeScale, glowTimeScale, isImmediate);
                        }

                        if(isMoving) {
                            boolean letterIsSleeping = updateLiveTextVisuals(letter.liveText, true, letterTimeScale, deltaTime);

                            if(!letterIsSleeping) {
                                isSleeping = false;
                            }
                        }
                    }
                }

                if(shouldUpdateVisualState) {
                    updateLiveTextState(syllable.liveText, syllableTimeScale, syllableTimeScale, isImmediate);
                }

                if(isMoving) {
                    boolean syllableIsSleeping = updateLiveTextVisuals(syllable.liveText, false, syllableTimeScale, deltaTime);

                    if(!syllableIsSleeping) {
                        isSleeping = false;
                    }
                }
            }

            if(isSleeping) {
                this.isSleeping = true;

                if(!isActive) {
                    evaluateClassState();
                }
            }
        }
    }

    @Override
    public boolean isActive() {
        return active;
    }

    private final List<Map.Entry<Double, Double>> scaleRange = List.of(
            Map.entry(0d, 1d), // Lowest
            Map.entry(0.7d, 1.025d), // Highest
            Map.entry(1d, 1d) // Resting
    );

    private final List<Map.Entry<Double, Double>> yOffsetRange = List.of(
            Map.entry(0d, 1d / 100d), // Lowest
            Map.entry(0.9d, -(1d / 60d)), // Highest
            Map.entry(1d, 0d) // Resting
    );

    private final List<Map.Entry<Double, Double>> glowRange = List.of(
            Map.entry(0d, 0d), // Lowest
            Map.entry(0.15d, 1d), // Highest
            Map.entry(0.6d, 1d), // Sustain
            Map.entry(1d, 0d) // Resting
    );

    private void lyricLabelStyle(GradientTextView text) {
        text.setTextColor(0xFFFFFFFF);
        text.setTextSize(26f);
        text.setPadding(0,0,1,0);

        FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(FlexboxLayout.LayoutParams.WRAP_CONTENT, FlexboxLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, dpToPx(5), 0);
        text.setLayoutParams(params);
    }

    private void backgroundLyricLabelStyle(GradientTextView text) {
        text.setTextColor(0xFFFFFFFF);
        text.setTextSize(16f);
        text.setPadding(0,0,dpToPx(1),0);

        FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(FlexboxLayout.LayoutParams.WRAP_CONTENT, FlexboxLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, dpToPx(5), 0);
        text.setLayoutParams(params);
    }

    private void emphasizedLyricLabelStyle(GradientTextView text) {
        text.setTextColor(0xFFFFFFFF);
        text.setTextSize(26f);
    }

    private void backgroundEmphasizedLyricLabelStyle(GradientTextView text) {
        text.setTextColor(0xFFFFFFFF);
        text.setTextSize(16f);
    }

    int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                activity.getResources().getDisplayMetrics()
        );
    }

}