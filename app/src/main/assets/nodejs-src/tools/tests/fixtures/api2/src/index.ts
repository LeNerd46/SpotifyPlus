import {
    interpolate as mix,
    useAnimatedStyle,
} from "spotifyplus/react/reanimated";

const progress = { value: 0.5 };

export const style = useAnimatedStyle(() => ({
    opacity: mix(progress.value, [0, 1], [0, 1]),
}));
