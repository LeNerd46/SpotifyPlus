package com.lenerd46.spotifyplus.beautifullyrics.entities.interludes;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.widget.LinearLayout;
import com.google.android.flexbox.FlexboxLayout;
import com.lenerd46.spotifyplus.beautifullyrics.curveinterpolator.CurveInterpolator;
import com.lenerd46.spotifyplus.beautifullyrics.curveinterpolator.Vector;
import com.lenerd46.spotifyplus.beautifullyrics.entities.LyricState;
import com.lenerd46.spotifyplus.beautifullyrics.entities.Spline;
import com.lenerd46.spotifyplus.beautifullyrics.entities.Spring;
import com.lenerd46.spotifyplus.beautifullyrics.entities.SyncableVocals;
import com.lenerd46.spotifyplus.beautifullyrics.entities.lyrics.Interlude;
import com.mikhaellopez.circleview.CircleView;
import de.robv.android.xposed.XposedBridge;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InterludeVisual implements SyncableVocals {
    private final LinearLayout container;

    private final double startTime;
    private final double duration;
    private final List<AnimatedDot> dots;
    private final MainLiveText liveText;

    private LyricState state;
    private boolean isSleeping;

    public final MainAnimations mainAnimations;
    public final DotAnimations dotAnimations;

    private static final double PULSE_INTERVAL = 2.25d;
    private static final double DOWN_PULSE = 0.90d;
    private static final double UP_PULSE = 1.05d;

    private final Spline scaleSpline;
    private final Spline yOffsetSpline;
    private final Spline glowSpline;
    private final Spline opacitySpline;

    private final CurveInterpolator mainScaleSpline;
    private final CurveInterpolator mainOpacitySpline;
    private final CurveInterpolator mainYOffsetSpline;

    private MainSprings createMainSprings() {
        Spring scale = new Spring(0, mainAnimations.scaleDamping, mainAnimations.scaleFrequency);
        Spring yOffset = new Spring(0, mainAnimations.yOffsetDamping, mainAnimations.yOffsetFrequency);
        Spring opacity = new Spring(0, mainAnimations.yOffsetDamping, mainAnimations.yOffsetFrequency);

        return new MainSprings(scale, yOffset, opacity);
    }

    private DotSprings createDotSprings() {
        Spring scale = new Spring(0, dotAnimations.scaleDamping, dotAnimations.scaleFrequency);
        Spring yOffset = new Spring(0, dotAnimations.yOffsetDamping, dotAnimations.yOffsetFrequency);
        Spring glow = new Spring(0, dotAnimations.glowDamping, dotAnimations.glowFrequency);
        Spring opacity = new Spring(0, dotAnimations.glowDamping, dotAnimations.glowFrequency);

        return new DotSprings(scale, yOffset, glow, opacity);
    }

    private Spline getSpline(List<Double> times, List<Double> values) {
        return new Spline(times, values);
    }

    private void initializeAnimations() {
        mainAnimations.baseScaleRange.add(new AbstractMap.SimpleEntry<>(0d, 0d));
        mainAnimations.baseScaleRange.add(new AbstractMap.SimpleEntry<>(0.2d, 1.05d));
        mainAnimations.baseScaleRange.add(new AbstractMap.SimpleEntry<>(-0.075d, 1.15d));
        mainAnimations.baseScaleRange.add(new AbstractMap.SimpleEntry<>(-0d, 0d));

        mainAnimations.opacityRange.add(new AbstractMap.SimpleEntry<>(0d, 0d));
        mainAnimations.opacityRange.add(new AbstractMap.SimpleEntry<>(0.5d, 1d));
        mainAnimations.opacityRange.add(new AbstractMap.SimpleEntry<>(-0.075d, 1d));
        mainAnimations.opacityRange.add(new AbstractMap.SimpleEntry<>(-0d, 0d));

        mainAnimations.yOffsetRange.add(new AbstractMap.SimpleEntry<>(0d, 1d / 100d));
        mainAnimations.yOffsetRange.add(new AbstractMap.SimpleEntry<>(0.9d, -(1d / 60d)));
        mainAnimations.yOffsetRange.add(new AbstractMap.SimpleEntry<>(1d, 0d));

        dotAnimations.scaleRange.add(new AbstractMap.SimpleEntry<>(0d, 0.75d));
        dotAnimations.scaleRange.add(new AbstractMap.SimpleEntry<>(0.7d, 1.05d));
        dotAnimations.scaleRange.add(new AbstractMap.SimpleEntry<>(1d, 1d));

        dotAnimations.yOffsetRange.add(new AbstractMap.SimpleEntry<>(0d, 0.125d));
        dotAnimations.yOffsetRange.add(new AbstractMap.SimpleEntry<>(0.9d, -0.2d));
        dotAnimations.yOffsetRange.add(new AbstractMap.SimpleEntry<>(1d, 0d));

        dotAnimations.glowRange.add(new AbstractMap.SimpleEntry<>(0d, 0d));
        dotAnimations.glowRange.add(new AbstractMap.SimpleEntry<>(0.6d, 1d));
        dotAnimations.glowRange.add(new AbstractMap.SimpleEntry<>(1d, 1d));

        dotAnimations.opacityRange.add(new AbstractMap.SimpleEntry<>(0d, 0.35d));
        dotAnimations.opacityRange.add(new AbstractMap.SimpleEntry<>(0.6d, 1d));
        dotAnimations.opacityRange.add(new AbstractMap.SimpleEntry<>(1d, 1d));
    }

    public InterludeVisual(FlexboxLayout lineContainer, Interlude interludeMetadata, Activity activity) {
        LinearLayout container = new LinearLayout(lineContainer.getContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setVisibility(LinearLayout.GONE);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        container.setLayoutParams(params);

        // Style
        this.container = container;
        dots = new ArrayList<>();

        mainAnimations = new MainAnimations(0.4d, 1.25d, 0.7d, 5d);
        dotAnimations = new DotAnimations(0.4d, 1.25d, 0.6d, 0.7d, 0.5d, 1d);
        initializeAnimations();

        List<List<Double>> points = new ArrayList<>();
        for(var metadata : mainAnimations.yOffsetRange) {
            points.add(List.of(metadata.getKey(), metadata.getValue()));
        }

        scaleSpline = getSpline(dotAnimations.scaleRange.stream().map(Map.Entry::getKey).collect(Collectors.toList()), dotAnimations.scaleRange.stream().map(Map.Entry::getValue).collect(Collectors.toList()));
        yOffsetSpline = getSpline(dotAnimations.yOffsetRange.stream().map(Map.Entry::getKey).collect(Collectors.toList()), dotAnimations.yOffsetRange.stream().map(Map.Entry::getValue).collect(Collectors.toList()));
        glowSpline = getSpline(dotAnimations.glowRange.stream().map(Map.Entry::getKey).collect(Collectors.toList()), dotAnimations.glowRange.stream().map(Map.Entry::getValue).collect(Collectors.toList()));
        opacitySpline = getSpline(dotAnimations.opacityRange.stream().map(Map.Entry::getKey).collect(Collectors.toList()), dotAnimations.opacityRange.stream().map(Map.Entry::getValue).collect(Collectors.toList()));

        List<Vector> yCtrl = new ArrayList<>();
        for(int i = 0; i < mainAnimations.yOffsetRange.size(); i++) {
            double t = mainAnimations.yOffsetRange.get(i).getKey();
            double v = mainAnimations.yOffsetRange.get(i).getValue();

            yCtrl.add(new Vector(List.of(t, v)));
        }

        mainYOffsetSpline = new CurveInterpolator(yCtrl.toArray(new Vector[0]));

        liveText = new MainLiveText(container, createMainSprings());

        startTime = interludeMetadata.time.startTime;
        duration = interludeMetadata.time.endTime - startTime;

        // Create our splines
        List<TimeValue> scaleRange = mainAnimations.baseScaleRange.stream().map(point -> new TimeValue(point.getKey(), point.getValue())).collect(Collectors.toList());
        List<TimeValue> opacityRange = mainAnimations.opacityRange.stream().map(point -> new TimeValue(point.getKey(), point.getValue())).collect(Collectors.toList());

        scaleRange.set(2, new TimeValue(scaleRange.get(2).time + duration, scaleRange.get(2).value));
        scaleRange.set(3, new TimeValue(scaleRange.get(3).time + duration, scaleRange.get(3).value));

        opacityRange.set(2, new TimeValue(opacityRange.get(2).time, opacityRange.get(2).value));
        opacityRange.set(3, new TimeValue(opacityRange.get(3).time, opacityRange.get(3).value));

        TimeValue startPoint = scaleRange.get(1);
        TimeValue endPoint = scaleRange.get(2);
        double deltaTime = endPoint.time - startPoint.time;

        for(double i = Math.floor(deltaTime / PULSE_INTERVAL); i > 0; i--) {
            double t = startPoint.time + (i  * PULSE_INTERVAL);
            double value = (i % 2 == 0) ? UP_PULSE : DOWN_PULSE;

            scaleRange.add(2, new TimeValue(t, value));
        }

        for(TimeValue tv : scaleRange) {
            tv.time /= duration;
        }

        for(TimeValue tv : opacityRange) {
            tv.time /= duration;
        }

        List<Double> scaleXs = scaleRange.stream().map(tv -> tv.time).collect(Collectors.toList());
        List<Double> scaleYs = scaleRange.stream().map(tv -> tv.value).collect(Collectors.toList());

        List<Double> opacityXs = opacityRange.stream().map(tv -> tv.time).collect(Collectors.toList());
        List<Double> opacityYs = opacityRange.stream().map(tv -> tv.value).collect(Collectors.toList());

        List<Vector> ctrl = new ArrayList<>();
        for(int i = 0; i < scaleXs.size(); i++) {
            double x = scaleXs.get(i);
            double y = scaleYs.get(i);

            ctrl.add(new Vector(List.of(x, y)));
        }

        List<Vector> oCtrl = new ArrayList<>();
        for(int i = 0; i < opacityXs.size(); i++) {
            double x = opacityXs.get(i);
            double y = opacityYs.get(i);

            oCtrl.add(new Vector(List.of(x, y)));
        }

        mainScaleSpline = new CurveInterpolator(ctrl.toArray(new Vector[0]));
        mainOpacitySpline = new CurveInterpolator(oCtrl.toArray(new Vector[0]));

        // Go through and create all our dots
        double dotStep = 0.92d / 3d;
        double startTime = 0d;

        try {
            for(int i = 0; i < 3; i++) {
                CircleView dot = new CircleView(activity, null);
                dot.setCircleColor(Color.WHITE);
                dot.setShadowEnable(true);
                dot.setShadowColor(Color.WHITE);
                dot.setShadowGravity(CircleView.ShadowGravity.CENTER);

                int sizeInPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28, activity.getResources().getDisplayMetrics());
//                int marginPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, activity.getResources().getDisplayMetrics());
                LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(sizeInPx, sizeInPx);
//                dotParams.setMargins(0, 0, marginPx, 0);
                dotParams.width = sizeInPx;
                dotParams.height = sizeInPx;
                dot.setLayoutParams(dotParams);
                // Style

                dots.add(new AnimatedDot(startTime, dotStep, 1 - startTime, new DotLiveText(dot, createDotSprings())));

                container.addView(dot);
                startTime += dotStep;
            }

            setToGeneralState(false);
            lineContainer.addView(container);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private void updateLiveDotState(DotLiveText liveText, double timeScale, double glowTimeScale, boolean forceTo) {
        double scale = scaleSpline.at(timeScale);
        double yOffset = yOffsetSpline.at(timeScale);
        double glowAlpha = glowSpline.at(timeScale);
        double opacity = opacitySpline.at(timeScale);

        if(forceTo) {
            liveText.springs.scale.set(scale);
            liveText.springs.yOffset.set(yOffset);
            liveText.springs.glow.set(glowAlpha);
            liveText.springs.opacity.set(opacity);
        } else {
            liveText.springs.scale.finalPosition = scale;
            liveText.springs.yOffset.finalPosition = yOffset;
            liveText.springs.glow.finalPosition = glowAlpha;
            liveText.springs.opacity.finalPosition = opacity;
        }
    }

    private boolean updateLiveDotVisuals(DotLiveText liveText, double deltaTime) {
        double scale = liveText.springs.scale.update(deltaTime);
        double yOffset = liveText.springs.yOffset.update(deltaTime) * 36;
        double glowAlpha = liveText.springs.glow.update(deltaTime);
        double opacity = liveText.springs.opacity.update(deltaTime);

        if(Double.isNaN(scale) || Double.isInfinite(scale)) return false;

        liveText.object.post(() -> {
            liveText.object.setScaleX((float)scale);
            liveText.object.setScaleY((float)scale);

            liveText.object.setTranslationY((float)yOffset);

            liveText.object.setShadowColor(Color.argb(255 * Math.max(0f, Math.min(1f, (float)glowAlpha)), 255, 255, 255));
            liveText.object.setAlpha((float)opacity);
        });

        return liveText.springs.scale.sleeping && liveText.springs.yOffset.sleeping && liveText.springs.glow.sleeping && liveText.springs.opacity.sleeping;
    }

    private void updateLiveMainState(MainLiveText liveText, double timeScale, boolean forceTo) {
        var yOffset = mainYOffsetSpline.getPointAt(timeScale).number.get(1);

        var scaleIntersections = mainScaleSpline.getIntersects(timeScale, 0, 0, -3284324);
        var opacityIntersections = mainOpacitySpline.getIntersects(timeScale, 0, 0, -3284324);

        double scale = (scaleIntersections.isEmpty()) ? 1 : scaleIntersections.get(scaleIntersections.size() - 1).number.get(1);
        double opacity = (opacityIntersections.isEmpty()) ? 1 : opacityIntersections.get(opacityIntersections.size() - 1).number.get(1);

        if(forceTo) {
            liveText.springs.scale.set(scale);
            liveText.springs.yOffset.set(yOffset);
            liveText.springs.opacity.set(opacity);
        } else {
            liveText.springs.scale.finalPosition = scale;
            liveText.springs.yOffset.finalPosition = yOffset;
            liveText.springs.opacity.finalPosition = opacity;
    }
    }

    private boolean updateLiveMainVisuals(MainLiveText liveText, double deltaTime) {
        double scale = liveText.springs.scale.update(deltaTime);
        double yOffset = liveText.springs.yOffset.update(deltaTime) * 25;
        double opacity = liveText.springs.opacity.update(deltaTime);

        liveText.object.setScaleX((float)scale);
        liveText.object.setScaleY((float)scale);

        liveText.object.setTranslationY((float)yOffset);
        liveText.object.setAlpha((float)opacity);

        return liveText.springs.scale.sleeping && liveText.springs.yOffset.sleeping && liveText.springs.opacity.sleeping;
    }

    private void setToGeneralState(boolean state) {
        double timeScale = state ? 1 : 0;

        for(var dot : dots) {
            updateLiveDotState(dot.liveText, timeScale, timeScale, true);
            updateLiveDotVisuals(dot.liveText, 0);
        }

        updateLiveMainState(liveText, timeScale, true);
        updateLiveMainVisuals(liveText, 0);

        this.state = state ? LyricState.SUNG : LyricState.IDLE;
    }

    @Override
    public void animate(double songTimestamp, double deltaTime, boolean isImmediate) {
        double relativeTime = songTimestamp - this.startTime;
        double timeScale = Math.max(0, Math.min((double)relativeTime / (double)this.duration, 1));

        boolean pastStart = relativeTime >= 0;
        boolean beforeEnd = relativeTime <= this.duration;
        boolean isActive = pastStart && beforeEnd;

        LyricState stateNow = isActive ? LyricState.ACTIVE : pastStart ? LyricState.SUNG : LyricState.IDLE;

        boolean stateChanged = stateNow != this.state;
        boolean shouldUpdateVisualState = stateChanged || isActive || isImmediate;

        if(stateChanged) {
            LyricState oldState = state;
            this.state = stateNow;

            Handler mainHandler = new Handler(Looper.getMainLooper());

            mainHandler.post(() -> {
                if(this.state == LyricState.ACTIVE) {
                    container.setVisibility(LinearLayout.VISIBLE);
                } else {
                    container.animate().scaleX(0f).scaleY(0f).setDuration(100).withEndAction(() -> container.setVisibility(LinearLayout.GONE));
                }
            });
        }

        if(shouldUpdateVisualState) {
            this.isSleeping = false;
        }

        boolean isMoving = !this.isSleeping;
        if(shouldUpdateVisualState || isMoving) {
            boolean isSleeping = true;

            for(var dot : dots) {
                double dotTimeScale = Math.max(0, Math.min((double)(timeScale - dot.start) / (double)dot.duration, 1));

                if(shouldUpdateVisualState) {
                    updateLiveDotState(dot.liveText, dotTimeScale, dotTimeScale, isImmediate);
                }

                if(isMoving) {
                    boolean dotIsSleeping = updateLiveDotVisuals(dot.liveText, deltaTime);

                    if(!dotIsSleeping) {
                        isSleeping = false;
                    }
                }
            }

            if(shouldUpdateVisualState) {
                updateLiveMainState(liveText, timeScale, isImmediate);
            }

            if(isMoving) {
                boolean mainIsSleeping = updateLiveMainVisuals(liveText, deltaTime);

                if(!mainIsSleeping) {
                    isSleeping = false;
                }
            }

            if(isSleeping) {
                this.isSleeping = true;
            }
        }
    }

    @Override
    public boolean isActive() {
        return this.state == LyricState.ACTIVE;
    }
}