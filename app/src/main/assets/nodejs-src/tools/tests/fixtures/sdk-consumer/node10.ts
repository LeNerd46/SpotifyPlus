import Animated, {
    useAnimatedStyle,
    useDerivedValue,
    useSharedValue,
    withSpring,
} from "spotifyplus/react/reanimated";
import ClassicAnimated from "spotifyplus/react/animated";
import {
    View,
    type CommonViewProps,
} from "spotifyplus/react";

const progress = useSharedValue(0);
const classicProgress = new ClassicAnimated.Value(0);
const springProgress = useDerivedValue(() => withSpring(progress.value));
const style = useAnimatedStyle(() => ({
    opacity: springProgress.value,
}));
const props: CommonViewProps = {};

void Animated.View;
void ClassicAnimated.View;
void classicProgress;
void View;
void props;
void style;
