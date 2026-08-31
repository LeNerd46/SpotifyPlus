import React, { useMemo } from 'react';
import { CommonViewProps } from 'spotifyplus/react';
import Animated, { SharedValue, clamp, getTimestamp, interpolate, interpolateColor, useAnimatedProps, useAnimatedStyle, useDerivedValue } from 'spotifyplus/react/reanimated';
import Spline from 'typescript-cubic-spline';
import { SyllableMetadata } from '../Types/lyrics-types';
import { createWorkletSpring, setWorkletSpring, updateWorkletSpring, WorkletSpringState } from './spring';
import { LyricsFontFamily } from '../theme/fonts';
import GradientText from '../Components/gradient-text';

interface Props extends CommonViewProps {
    syllable: SyllableMetadata;
    emphasized: boolean;
    isBackground?: boolean;
    playbackMs: SharedValue<number>;
    scaleSpline: Spline;
    yOffsetSpline: Spline;
    glowSpline: Spline;
}

type SplineSamples = {
    input: number[];
    output: number[];
};

type MotionValue = {
    progress: number;
    scale: number;
    yOffset: number;
    glow: number;
};

type ReadonlyMotion = {
    readonly value: MotionValue;
};

type MotionState = {
    lastTimestamp: number;
    lastPlaybackMs: number;
    scale: WorkletSpringState;
    yOffset: WorkletSpringState;
    glow: WorkletSpringState;
};

const ScaleDampingRatio = 0.6;
const ScaleFrequency = 0.7;
const YOffsetDampingRatio = 0.4;
const YOffsetFrequency = 1.25;
const GlowDampingRatio = 0.5;
const GlowFrequency = 1;
const SeekThresholdMs = 250;
const MaximumSpringDeltaSeconds = 0.064;
const TextGradient = { colors: ['#ffffffff', '#78ffffff'] };

const createMotionState = (
    scale: number,
    yOffset: number,
    glow: number,
): MotionState => ({
    lastTimestamp: -1,
    lastPlaybackMs: -1,
    scale: createWorkletSpring(scale),
    yOffset: createWorkletSpring(yOffset),
    glow: createWorkletSpring(glow),
});

const sampleSpline = (spline: Spline, samples = 32): SplineSamples => {
    const input: number[] = [];
    const output: number[] = [];
    for (let index = 0; index <= samples; index += 1) {
        const progress = index / samples;
        input.push(progress);
        output.push(spline.at(progress));
    }
    return { input, output };
};

const sampleSinEase = (samples = 32): SplineSamples => {
    const input: number[] = [];
    const output: number[] = [];
    for (let index = 0; index <= samples; index += 1) {
        const progress = index / samples;
        input.push(progress);
        output.push(Math.sin(progress * (Math.PI / 2)));
    }
    return { input, output };
};

interface EmphasizedLetterProps {
    char: string;
    index: number;
    letterCount: number;
    fontSize: number;
    playbackMs: SharedValue<number>;
    parentMotion: ReadonlyMotion;
    scaleSamples: SplineSamples;
    yOffsetSamples: SplineSamples;
    glowSamples: SplineSamples;
    sinEaseSamples: SplineSamples;
}

const EmphasizedLetter = ({ char, index, letterCount, fontSize, playbackMs, parentMotion, scaleSamples, yOffsetSamples, glowSamples, sinEaseSamples, }: EmphasizedLetterProps) => {
    const letterStart = index / letterCount;
    const letterEnd = (index + 1) / letterCount;
    const motionState = useMemo(() => createMotionState(scaleSamples.output[0] ?? 1, yOffsetSamples.output[0] ?? 0, glowSamples.output[0] ?? 0,), [glowSamples, scaleSamples, yOffsetSamples],);

    const motion = useDerivedValue(() => {
        'worklet';

        const parentProgress = parentMotion.value?.progress ?? 0;
        const timeAlpha = interpolate(parentProgress, sinEaseSamples.input, sinEaseSamples.output, 'clamp',);
        const progress = interpolate(timeAlpha, [letterStart, letterEnd], [0, 1], 'clamp',);
        const glowProgress = interpolate(timeAlpha, [letterStart, 1], [0, 1], 'clamp',);

        const scaleTarget = interpolate(progress, scaleSamples.input, scaleSamples.output, 'clamp',);
        const yOffsetTarget = interpolate(progress, yOffsetSamples.input, yOffsetSamples.output, 'clamp',);
        const glowTarget = interpolate(glowProgress, glowSamples.input, glowSamples.output, 'clamp',);

        const timestamp = getTimestamp();
        const playbackPosition = playbackMs.value;
        const playbackDelta = playbackPosition - motionState.lastPlaybackMs;
        const isDiscontinuity = motionState.lastTimestamp < 0 || playbackDelta < 0 || playbackDelta > SeekThresholdMs;
        const deltaTime = clamp((timestamp - motionState.lastTimestamp) / 1000, 0, MaximumSpringDeltaSeconds,);

        const scale = isDiscontinuity ? setWorkletSpring(motionState.scale, scaleTarget) : updateWorkletSpring(motionState.scale, scaleTarget, ScaleDampingRatio, ScaleFrequency, deltaTime, parentProgress > 0 && parentProgress < 1,);
        const yOffset = isDiscontinuity ? setWorkletSpring(motionState.yOffset, yOffsetTarget) : updateWorkletSpring(motionState.yOffset, yOffsetTarget, YOffsetDampingRatio, YOffsetFrequency, deltaTime, parentProgress > 0 && parentProgress < 1,);
        const glow = isDiscontinuity ? setWorkletSpring(motionState.glow, glowTarget) : updateWorkletSpring(motionState.glow, glowTarget, GlowDampingRatio, GlowFrequency, deltaTime, parentProgress > 0 && parentProgress < 1);

        motionState.lastTimestamp = timestamp;
        motionState.lastPlaybackMs = playbackPosition;
        return { progress, scale, yOffset, glow };
    });

    const letterStyle = useAnimatedStyle(() => {
        'worklet';

        const restingYOffset = interpolate(motion.value.progress, [0, 1], [yOffsetSamples.output[0] ?? 0, yOffsetSamples.output[yOffsetSamples.output.length - 1] ?? 0,], 'clamp',);
        const extraYOffset = (motion.value.yOffset - restingYOffset) * 3;

        return { transform: [{ translateY: Math.min(extraYOffset, 0), }, { scale: motion.value.scale }] };
    });
    const letterTextStyle = useAnimatedStyle(() => {
        'worklet';

        const shadowRadius = 24 * motion.value.glow;
        const shadowOpacity = clamp(motion.value.glow, 0, 1);
        return {
            textShadowColor: interpolateColor(shadowOpacity, [0, 1], ['#00ffffff', '#ffffffff']),
            textShadowRadius: shadowRadius,
            textShadowOffset: { width: 0, height: 0 }
        };
    });
    const gradientProps = useAnimatedProps(() => {
        'worklet';

        return { progress: clamp(motion.value.progress, 0, 1) };
    });

    return (
        <Animated.View style={[{ position: 'relative', alignSelf: 'flex-start' }, letterStyle]} >
            <GradientText
                animatedProps={gradientProps}
                text={char}
                textSizeSp={fontSize}
                textColor="#ffffff"
                gradientColors={TextGradient.colors}
                gradientDirection="leftToRight"
                gradientTransitionWidth={0.3}
                style={[{ fontFamily: LyricsFontFamily }, letterTextStyle]}
            />
        </Animated.View>
    );
};

const SyllableView = ({ syllable, emphasized, isBackground = false, playbackMs, scaleSpline, yOffsetSpline, glowSpline, }: Props) => {
    const text = `${syllable.Text}${syllable.IsPartOfWord ? '' : ' '}`;
    const fontSize = isBackground ? 18 : 36;
    const startMs = syllable.StartTime * 1000;
    const endMs = syllable.EndTime * 1000;

    const scaleSamples = useMemo(() => sampleSpline(scaleSpline), [scaleSpline]);
    const yOffsetSamples = useMemo(() => {
        const samples = sampleSpline(yOffsetSpline);
        return {
            input: samples.input,
            output: samples.output.map(value => value * fontSize),
        };
    }, [fontSize, yOffsetSpline]);

    const glowSamples = useMemo(() => sampleSpline(glowSpline), [glowSpline]);
    const sinEaseSamples = useMemo(() => sampleSinEase(), []);

    const motionState = useMemo(() => createMotionState(scaleSamples.output[0] ?? 1, yOffsetSamples.output[0] ?? 0, glowSamples.output[0] ?? 0), [glowSamples, scaleSamples, yOffsetSamples]);
    const motion = useDerivedValue(() => {
        'worklet';

        const progress = interpolate(playbackMs.value, [startMs, endMs], [0, 1], 'clamp',);
        const scaleTarget = interpolate(progress, scaleSamples.input, scaleSamples.output, 'clamp',);
        const yOffsetTarget = interpolate(progress, yOffsetSamples.input, yOffsetSamples.output, 'clamp',);
        const glowTarget = interpolate(progress, glowSamples.input, glowSamples.output, 'clamp',);

        const timestamp = getTimestamp();
        const playbackPosition = playbackMs.value;
        const playbackDelta = playbackPosition - motionState.lastPlaybackMs;
        const isDiscontinuity = motionState.lastTimestamp < 0 || playbackDelta < 0 || playbackDelta > SeekThresholdMs;
        const deltaTime = clamp((timestamp - motionState.lastTimestamp) / 1000, 0, MaximumSpringDeltaSeconds);

        const scale = isDiscontinuity ? setWorkletSpring(motionState.scale, scaleTarget) : updateWorkletSpring(motionState.scale, scaleTarget, ScaleDampingRatio, ScaleFrequency, deltaTime, progress > 0 && progress < 1,);
        const yOffset = isDiscontinuity ? setWorkletSpring(motionState.yOffset, yOffsetTarget) : updateWorkletSpring(motionState.yOffset, yOffsetTarget, YOffsetDampingRatio, YOffsetFrequency, deltaTime, progress > 0 && progress < 1,);
        const glow = isDiscontinuity ? setWorkletSpring(motionState.glow, glowTarget) : updateWorkletSpring(motionState.glow, glowTarget, GlowDampingRatio, GlowFrequency, deltaTime, progress > 0 && progress < 1,);

        motionState.lastTimestamp = timestamp;
        motionState.lastPlaybackMs = playbackPosition;
        return { progress, scale, yOffset, glow };
    });

    const wordStyle = useAnimatedStyle(() => {
        'worklet';

        return {
            transform: [
                { translateY: motion.value.yOffset * 1.5 },
                { scale: motion.value.scale },
            ],
        };
    });
    const textStyle = useAnimatedStyle(() => {
        'worklet';

        const shadowRadius = 8 * motion.value.glow;
        const shadowOpacity = clamp(motion.value.glow * 0.35, 0, 1);

        return {
            textShadowColor: interpolateColor(shadowOpacity, [0, 1], ['#00ffffff', '#ffffffff']),
            textShadowRadius: shadowRadius,
            textShadowOffset: { width: 0, height: 0 }
        };
    });
    const gradientProps = useAnimatedProps(() => {
        'worklet';

        return {
            progress: clamp(motion.value.progress, 0, 1),
        };
    });

    if (emphasized) {
        const chars = [...syllable.Text];
        return (
            <Animated.View style={[{ flexDirection: 'row', position: 'relative', alignSelf: 'flex-start', marginRight: syllable.IsPartOfWord ? 0 : 5 }, wordStyle]} >
                {chars.map((char, index) => (
                    <EmphasizedLetter
                        key={`${char}-${index}`}
                        char={char}
                        index={index}
                        letterCount={chars.length}
                        fontSize={fontSize}
                        playbackMs={playbackMs}
                        parentMotion={motion}
                        scaleSamples={scaleSamples}
                        yOffsetSamples={yOffsetSamples}
                        glowSamples={glowSamples}
                        sinEaseSamples={sinEaseSamples}
                    />
                ))}
            </Animated.View>
        );
    }

    return (
        <Animated.View style={[{ position: 'relative', alignSelf: 'flex-start', }, wordStyle,]} >
            <GradientText
                animatedProps={gradientProps}
                text={text}
                textSizeSp={fontSize}
                textColor="#ffffff"
                gradientColors={TextGradient.colors}
                gradientDirection="leftToRight"
                gradientTransitionWidth={0.3}
                style={[{ fontFamily: LyricsFontFamily }, textStyle]}
            />
        </Animated.View>
    );
};

export default SyllableView;
