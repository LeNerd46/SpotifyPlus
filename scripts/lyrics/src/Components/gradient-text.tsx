import Animated from 'spotifyplus/react/reanimated';
import { ColorValue, CommonViewProps, createNativeComponent, } from 'spotifyplus/react';

export type GradientTextDirection = | 'leftToRight' | 'rightToLeft' | 'topToBottom' | 'bottomToTop';

export interface GradientTextProps extends CommonViewProps {
    text: string;
    textSizeSp: number;
    textAlign?: 'left' | 'center' | 'right';
    textColor?: ColorValue;
    gradientColors: readonly ColorValue[];
    gradientDirection?: GradientTextDirection;
    gradientTransitionWidth?: number;
    progress?: number;
}

const NativeGradientText = createNativeComponent<GradientTextProps>('GradientText');

const GradientText = Animated.createAnimatedComponent(NativeGradientText);

export default GradientText;
