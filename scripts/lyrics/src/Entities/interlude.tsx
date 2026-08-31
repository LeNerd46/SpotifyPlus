import { useMemo } from 'react';
import { CurveInterpolator } from 'curve-interpolator';
import Spline from 'typescript-cubic-spline';
import {
    CommonViewProps,
    View,
} from 'spotifyplus/react';
import Animated, {
    SharedValue,
    clamp,
    getTimestamp,
    interpolate,
    useAnimatedStyle,
    useDerivedValue,
} from 'spotifyplus/react/reanimated';
import { Interlude } from '../Types/lyrics-types';
import {
    createWorkletSpring,
    setWorkletSpring,
    updateWorkletSpring,
} from './spring';

interface Props extends CommonViewProps {
    metadata: Interlude;
    oppositeAligned?: boolean;
    playbackMs: SharedValue<number>;
}

type Point = [number, number];

type Samples = {
    input: number[];
    output: number[];
};

const PulseInterval = 2.25;
const DownPulse = 0.95;
const UpPulse = 1.05;
const DotStep = 0.925 / 3;
const DotSize = 20;
const DotSpacing = 6;
const InterludeHorizontalPadding = 60;
const InterludeWidth = ((DotSize + DotSpacing) * 3) + InterludeHorizontalPadding;

const MainScaleRange: Point[] = [
    [0, 0],
    [0.2, 1.05],
    [-0.075, 1.15],
    [0, 0],
];

const MainYOffsetRange: Point[] = [
    [0, 1 / 100],
    [0.9, -(1 / 60)],
    [1, 0],
];

const MainOpacityRange: Point[] = [
    [0, 0],
    [0.5, 1],
    [-0.075, 1],
    [0, 0],
];

const DotScaleSpline = new Spline([0, 0.7, 1], [0.75, 1.05, 0.75]);
const DotYOffsetSpline = new Spline([0, 0.9, 1], [0.125, -0.2, 0.125]);
const DotGlowSpline = new Spline([0, 0.6, 1], [0, 1, 1]);
const DotOpacitySpline = new Spline([0, 0.6, 1], [0.35, 1, 1]);

const sampleSpline = (spline: Spline, samples = 32, multiplier = 1): Samples => {
    const input: number[] = [];
    const output: number[] = [];
    for (let index = 0; index <= samples; index += 1) {
        const progress = index / samples;
        input.push(progress);
        output.push(spline.at(progress) * multiplier);
    }
    return { input, output };
};

const sampleCurvePoint = (
    curve: CurveInterpolator,
    samples = 64,
    multiplier = 1,
): Samples => {
    const input: number[] = [];
    const output: number[] = [];
    for (let index = 0; index <= samples; index += 1) {
        const progress = index / samples;
        const point = curve.getPointAt(progress);
        input.push(progress);
        output.push((point[1] ?? 0) * multiplier);
    }
    return { input, output };
};

const pointY = (point: unknown): number | undefined => {
    if (!point || typeof point !== 'object') return undefined;
    const value = (point as { 1?: unknown })[1];
    return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
};

const sampleCurveIntersections = (
    curve: CurveInterpolator,
    samples = 96,
    fallback = 1,
    edgeValues?: { start?: number; end?: number },
): Samples => {
    const input: number[] = [];
    const output: number[] = [];
    for (let index = 0; index <= samples; index += 1) {
        const progress = index / samples;
        let value = fallback;
        if (index === 0 && edgeValues?.start !== undefined) {
            value = edgeValues.start;
        } else if (index === samples && edgeValues?.end !== undefined) {
            value = edgeValues.end;
        } else {
            const intersections = curve.getIntersects(progress, 0, 0);
            const points = Array.isArray(intersections) ? intersections : [intersections];
            value = pointY(points[points.length - 1]) ?? fallback;
        }
        input.push(progress);
        output.push(value);
    }
    return { input, output };
};

const createMainScalePoints = (duration: number): Point[] => {
    const points = MainScaleRange.map(([time, value]) => [time, value] as Point);
    points[2] = [points[2][0] + duration, points[2][1]];
    points[3] = [points[3][0] + duration, points[3][1]];

    const startPoint = points[1];
    const endPoint = points[2];
    const deltaTime = endPoint[0] - startPoint[0];
    for (let index = Math.floor(deltaTime / PulseInterval); index > 0; index -= 1) {
        const time = startPoint[0] + (index * PulseInterval);
        const value = index % 2 === 0 ? UpPulse : DownPulse;
        points.splice(2, 0, [time, value]);
    }
    return points.map(([time, value]) => [time / duration, value]);
};

const createMainOpacityPoints = (duration: number): Point[] => {
    const points = MainOpacityRange.map(([time, value]) => [time, value] as Point);
    points[2] = [points[2][0] + duration, points[2][1]];
    points[3] = [duration, points[3][1]];
    return points.map(([time, value]) => [time / duration, value]);
};

const InterludeView = ({ metadata, playbackMs, oppositeAligned = true }: Props) => {
    const startMs = metadata.StartTime * 1000;
    const endMs = metadata.EndTime * 1000;
    const duration = Math.max(metadata.EndTime - metadata.StartTime, 0.001);

    const dotScaleSamples = useMemo(() => sampleSpline(DotScaleSpline), []);
    const dotYOffsetSamples = useMemo(() => sampleSpline(DotYOffsetSpline, 32, DotSize), []);
    const dotGlowSamples = useMemo(() => sampleSpline(DotGlowSpline), []);
    const dotOpacitySamples = useMemo(() => sampleSpline(DotOpacitySpline), []);
    const mainScaleSamples = useMemo(() => {
        const curve = new CurveInterpolator(createMainScalePoints(duration));
        return sampleCurveIntersections(curve, 96, 1, {
            start: 0,
            end: 0,
        });
    }, [duration]);
    const mainYOffsetSamples = useMemo(() => {
        const curve = new CurveInterpolator(MainYOffsetRange);
        return sampleCurvePoint(curve, 64, DotSize);
    }, []);
    const mainOpacitySamples = useMemo(() => {
        const curve = new CurveInterpolator(createMainOpacityPoints(duration));
        return sampleCurveIntersections(curve, 96, 1, {
            start: 0,
            end: 0,
        });
    }, [duration]);
    const mainMotionState = useMemo(() => ({
        lastTimestamp: -1,
        lastPlaybackMs: -1,
        scale: createWorkletSpring(mainScaleSamples.output[0] ?? 0),
        yOffset: createWorkletSpring(mainYOffsetSamples.output[0] ?? 0),
        opacity: createWorkletSpring(mainOpacitySamples.output[0] ?? 0),
    }), [mainOpacitySamples, mainScaleSamples, mainYOffsetSamples]);
    const mainMotion = useDerivedValue(() => {
        'worklet';
        const progress = interpolate(
            playbackMs.value,
            [startMs, endMs],
            [0, 1],
            'clamp',
        );
        const scaleTarget = interpolate(
            progress,
            mainScaleSamples.input,
            mainScaleSamples.output,
            'clamp',
        );
        const yOffsetTarget = interpolate(
            progress,
            mainYOffsetSamples.input,
            mainYOffsetSamples.output,
            'clamp',
        );
        const opacityTarget = interpolate(
            progress,
            mainOpacitySamples.input,
            mainOpacitySamples.output,
            'clamp',
        );
        const timestamp = getTimestamp();
        const playbackPosition = playbackMs.value;
        const playbackDelta = playbackPosition - mainMotionState.lastPlaybackMs;
        const isDiscontinuity = mainMotionState.lastTimestamp < 0
            || playbackDelta < 0
            || playbackDelta > 250;
        const deltaTime = clamp(
            (timestamp - mainMotionState.lastTimestamp) / 1000,
            0,
            0.064,
        );
        const keepAwake = progress > 0 && progress < 1;
        const scale = isDiscontinuity
            ? setWorkletSpring(mainMotionState.scale, scaleTarget)
            : updateWorkletSpring(
                mainMotionState.scale,
                scaleTarget,
                0.7,
                5,
                deltaTime,
                keepAwake,
            );
        const yOffset = isDiscontinuity
            ? setWorkletSpring(mainMotionState.yOffset, yOffsetTarget)
            : updateWorkletSpring(
                mainMotionState.yOffset,
                yOffsetTarget,
                0.4,
                1.25,
                deltaTime,
                keepAwake,
            );
        const opacity = isDiscontinuity
            ? setWorkletSpring(mainMotionState.opacity, opacityTarget)
            : updateWorkletSpring(
                mainMotionState.opacity,
                opacityTarget,
                0.4,
                1.25,
                deltaTime,
                keepAwake,
            );

        mainMotionState.lastTimestamp = timestamp;
        mainMotionState.lastPlaybackMs = playbackPosition;
        return {
            progress,
            scale,
            yOffset,
            opacity,
        };
    });

    const mainStyle = useAnimatedStyle(() => {
        'worklet';
        const currentMotion = mainMotion.value;
        if (!currentMotion) {
            return {
                display: 'none',
                opacity: 0,
                transform: [
                    { translateY: 0 },
                    { scale: 0 },
                ],
            };
        }
        const isActive = interpolate(
            playbackMs.value,
            [startMs - 1, startMs, endMs, endMs + 1],
            [0, 1, 1, 0],
            'clamp',
        );
        const opacity = Math.sin(currentMotion.opacity * (Math.PI / 2));
        return {
            display: isActive > 0 ? 'flex' : 'none',
            opacity,
            transform: [
                { translateY: currentMotion.yOffset },
                { scale: currentMotion.scale },
            ],
        };
    });

    const Dot = ({ index }: { index: number }) => {
        const dotStart = index * DotStep;
        const motionState = useMemo(() => ({
            lastTimestamp: -1,
            lastPlaybackMs: -1,
            scale: createWorkletSpring(dotScaleSamples.output[0] ?? 0.75),
            yOffset: createWorkletSpring(dotYOffsetSamples.output[0] ?? 0),
            glow: createWorkletSpring(dotGlowSamples.output[0] ?? 0),
            opacity: createWorkletSpring(dotOpacitySamples.output[0] ?? 0.35),
        }), []);
        const dotMotion = useDerivedValue(() => {
            'worklet';
            const progress = interpolate(
                playbackMs.value,
                [startMs, endMs],
                [0, 1],
                'clamp',
            );
            const dotProgress = clamp((progress - dotStart) / DotStep, 0, 1);
            const scaleTarget = interpolate(
                dotProgress,
                dotScaleSamples.input,
                dotScaleSamples.output,
                'clamp',
            );
            const yOffsetTarget = interpolate(
                dotProgress,
                dotYOffsetSamples.input,
                dotYOffsetSamples.output,
                'clamp',
            );
            const glowTarget = interpolate(
                dotProgress,
                dotGlowSamples.input,
                dotGlowSamples.output,
                'clamp',
            );
            const opacityTarget = interpolate(
                dotProgress,
                dotOpacitySamples.input,
                dotOpacitySamples.output,
                'clamp',
            );
            const timestamp = getTimestamp();
            const playbackPosition = playbackMs.value;
            const playbackDelta = playbackPosition - motionState.lastPlaybackMs;
            const isDiscontinuity = motionState.lastTimestamp < 0
                || playbackDelta < 0
                || playbackDelta > 250;
            const deltaTime = clamp(
                (timestamp - motionState.lastTimestamp) / 1000,
                0,
                0.064,
            );
            let scale: number;
            if (dotProgress >= 1 || isDiscontinuity) {
                scale = setWorkletSpring(motionState.scale, scaleTarget);
            } else {
                scale = updateWorkletSpring(
                    motionState.scale,
                    scaleTarget,
                    0.6,
                    0.7,
                    deltaTime,
                    progress > 0 && progress < 1,
                );
            }
            let yOffset: number;
            if (dotProgress >= 1 || isDiscontinuity) {
                yOffset = setWorkletSpring(motionState.yOffset, yOffsetTarget);
            } else {
                yOffset = updateWorkletSpring(
                    motionState.yOffset,
                    yOffsetTarget,
                    0.4,
                    1.25,
                    deltaTime,
                    progress > 0 && progress < 1,
                );
            }
            const glow = isDiscontinuity
                ? setWorkletSpring(motionState.glow, glowTarget)
                : updateWorkletSpring(
                    motionState.glow,
                    glowTarget,
                    0.5,
                    1,
                    deltaTime,
                    progress > 0 && progress < 1,
                );
            const opacity = isDiscontinuity
                ? setWorkletSpring(motionState.opacity, opacityTarget)
                : updateWorkletSpring(
                    motionState.opacity,
                    opacityTarget,
                    0.5,
                    1,
                    deltaTime,
                    progress > 0 && progress < 1,
                );

            motionState.lastTimestamp = timestamp;
            motionState.lastPlaybackMs = playbackPosition;
            return {
                dotProgress,
                scale,
                yOffset,
                glow,
                opacity,
            };
        });
        const dotStyle = useAnimatedStyle(() => {
            'worklet';
            const currentMotion = dotMotion.value;
            if (!currentMotion) {
                return {
                    opacity: 0,
                    transform: [
                        { translateY: 0 },
                        { scale: 0.75 },
                    ],
                };
            }
            const glow = clamp(currentMotion.glow, 0, 1);
            return {
                opacity: currentMotion.opacity,
                transform: [
                    { translateY: currentMotion.yOffset },
                    { scale: currentMotion.scale },
                ],
                shadowColor: 'white',
                shadowOpacity: glow,
                shadowRadius: glow * 10,
                elevation: glow * 10,
            };
        });

        return (
            <View
                style={{
                    width: DotSize + DotSpacing,
                    height: DotSize + 28,
                    alignItems: 'center',
                    justifyContent: 'center',
                    overflow: 'visible',
                }}
            >
                <Animated.View
                    style={[
                        {
                            width: DotSize,
                            height: DotSize,
                            borderRadius: DotSize / 2,
                            backgroundColor: 'white',
                        },
                        dotStyle,
                    ]}
                />
            </View>
        );
    };

    const alignmentStyle = oppositeAligned
        ? {
            width: InterludeWidth,
            paddingRight: 25,
            paddingLeft: 35,
            paddingVertical: 12,
            flexDirection: 'row' as const,
            alignItems: 'center' as const,
            overflow: 'visible' as const,
        }
        : {
            width: InterludeWidth,
            paddingLeft: 25,
            paddingRight: 35,
            paddingVertical: 12,
            flexDirection: 'row' as const,
            alignItems: 'center' as const,
            overflow: 'visible' as const,
        };

    return (
        <Animated.View style={[alignmentStyle, mainStyle]}>
            <Dot index={0} />
            <Dot index={1} />
            <Dot index={2} />
        </Animated.View>
    );
};

export default InterludeView;
