import React, { useMemo } from 'react';
import { CommonViewProps } from 'spotifyplus/react';
import Animated, { SharedValue, clamp, getTimestamp, interpolate, interpolateColor, useAnimatedProps, useAnimatedStyle, useDerivedValue } from 'spotifyplus/react/reanimated';
import { LineVocal } from '../Types/lyrics-types';
import { createWorkletSpring, setWorkletSpring, updateWorkletSpring } from './spring';
import { LyricsFontFamily } from '../theme/fonts';
import GradientText from '../Components/gradient-text';

interface Props extends CommonViewProps {
    line: LineVocal;
    playbackMs: SharedValue<number>;
}

const LineView = ({ line, playbackMs, style }: Props) => {
    const fontSize = 36;
    const startMs = line.StartTime * 1000;
    const endMs = line.EndTime * 1000;
    const motionState = useMemo(() => ({ lastTimestamp: -1, lastPlaybackMs: -1, glow: createWorkletSpring(0), }), []);

    const motion = useDerivedValue(() => {
        'worklet';

        const progress = interpolate(playbackMs.value, [startMs, endMs], [0, 1], 'clamp',);
        const glowTarget = interpolate(progress, [0, 0.15, 0.6, 1], [0, 1, 1, 0], 'clamp',);

        const timestamp = getTimestamp();
        const playbackPosition = playbackMs.value;
        const playbackDelta = playbackPosition - motionState.lastPlaybackMs;
        const isDiscontinuity = motionState.lastTimestamp < 0 || playbackDelta < 0 || playbackDelta > 250;
        const deltaTime = clamp((timestamp - motionState.lastTimestamp) / 1000, 0, 0.064,);

        const glow = isDiscontinuity ? setWorkletSpring(motionState.glow, glowTarget) : updateWorkletSpring(motionState.glow, glowTarget, 0.5, 1, deltaTime, progress > 0 && progress < 1,);

        motionState.lastTimestamp = timestamp;
        motionState.lastPlaybackMs = playbackPosition;
        const opacity = playbackPosition < startMs ? 1 / 3 : playbackPosition >= endMs ? 0.6 : 1;

        return { progress, glow, opacity };
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

    const lineOpacityStyle = useAnimatedStyle(() => {
        'worklet';

        return { opacity: motion.value.opacity };
    });
    const gradientProps = useAnimatedProps(() => {
        'worklet';

        return { progress: clamp(motion.value.progress, 0, 1) };
    });

    const textAlign = line.OppositeAligned ? 'right' as const : 'left' as const;

    return (
        <Animated.View style={[{ position: 'relative', alignSelf: 'flex-start' }, style, lineOpacityStyle]} >
            <GradientText
                animatedProps={gradientProps}
                text={line.Text}
                textSizeSp={fontSize}
                textColor="#ffffff"
                textAlign={textAlign}
                gradientColors={['#ffffffff', '#78ffffff']}
                gradientDirection="topToBottom"
                gradientTransitionWidth={0.3}
                style={[{ fontFamily: LyricsFontFamily }, textStyle]}
            />
        </Animated.View>
    );
};

export default React.memo(LineView);
