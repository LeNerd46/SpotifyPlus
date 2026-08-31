import React from 'react';
import {
    Pressable,
    ScrollView,
    Text,
    View,
} from 'spotifyplus/react';
import Animated, {
    Easing,
    cancelAnimation,
    interpolate,
    useAnimatedStyle,
    useSharedValue,
    withRepeat,
    withSpring,
    withTiming,
} from 'spotifyplus/react/reanimated';

export default function AnimationTest() {
    const opacity = useSharedValue(0);
    const y = useSharedValue(24);
    const scale = useSharedValue(0.96);
    const spin = useSharedValue(0);

    const replay = React.useCallback(() => {
        opacity.value = 0;
        y.value = 28;
        scale.value = 0.94;
        opacity.value = withTiming(1, { duration: 350 });
        y.value = withSpring(0);
        scale.value = withSpring(1);
    }, [opacity, scale, y]);

    React.useEffect(() => {
        opacity.value = withTiming(1, {
            duration: 450,
            easing: Easing.out(Easing.cubic),
        });
        y.value = withSpring(0, {
            stiffness: 120,
            damping: 14,
        });
        scale.value = withSpring(1, {
            stiffness: 120,
            damping: 12,
        });
        spin.value = withRepeat(
            withTiming(1, {
                duration: 1800,
                easing: Easing.linear,
            }),
            -1,
            false,
        );

        return () => {
            cancelAnimation(opacity);
            cancelAnimation(y);
            cancelAnimation(scale);
            cancelAnimation(spin);
        };
    }, [opacity, scale, spin, y]);

    const cardStyle = useAnimatedStyle(() => {
        'worklet';
        return {
            opacity: opacity.value,
            transform: [
                { translateY: y.value },
                { scale: scale.value },
            ],
        };
    });

    const spinnerStyle = useAnimatedStyle(() => {
        'worklet';
        return {
            transform: [{
                rotate: `${interpolate(spin.value, [0, 1], [0, 360])}deg`,
            }],
        };
    });

    return (
        <ScrollView
            style={{
                flex: 1,
                backgroundColor: '#0b0b0b',
            }}
            contentContainerStyle={{
                padding: 20,
                gap: 14,
            }}
        >
            <Animated.View style={cardStyle}>
                <View
                    style={{
                        padding: 18,
                        borderRadius: 24,
                        backgroundColor: '#181818',
                        borderWidth: 1,
                        borderColor: '#2a2a2a',
                    }}
                >
                    <Text
                        style={{
                            color: '#ffffff',
                            fontSize: 28,
                            fontWeight: 'bold',
                            marginBottom: 6,
                        }}
                    >
                        Animated works
                    </Text>
                    <Text
                        style={{
                            color: '#b3b3b3',
                            fontSize: 15,
                            lineHeight: 21,
                        }}
                    >
                        This card fades, slides, scales, and spins entirely from UI worklets.
                    </Text>
                </View>
            </Animated.View>

            <Animated.View
                style={[
                    {
                        width: 72,
                        height: 72,
                        borderRadius: 36,
                        backgroundColor: '#1ed760',
                        alignSelf: 'center',
                    },
                    spinnerStyle,
                ]}
            />

            <Pressable
                onPress={replay}
                style={({ pressed }) => ({
                    padding: 14,
                    borderRadius: 18,
                    backgroundColor: pressed ? '#169c46' : '#1ed760',
                    alignItems: 'center',
                })}
            >
                <Text
                    style={{
                        color: '#000000',
                        fontWeight: 'bold',
                        fontSize: 16,
                    }}
                >
                    Replay
                </Text>
            </Pressable>
        </ScrollView>
    );
}
