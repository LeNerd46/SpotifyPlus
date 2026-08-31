import React from "react";
import { Easing } from "./animations";
import {
    ReduceMotion,
    type EasingFunction,
    type LayoutAnimation,
    type ReduceMotionSetting,
    type WorkletFunctionLike,
} from "./types";
import { serializeWorklet } from "./worklets";

export type LayoutAnimationCallback = (finished: boolean) => void;

export class LayoutAnimationBuilder implements LayoutAnimation {
    readonly __spotifyPlusLayoutAnimation = true as const;

    constructor(
        readonly name: string,
        readonly config: Readonly<Record<string, unknown>> = {},
    ) { }

    private withConfig(next: Readonly<Record<string, unknown>>) {
        return new LayoutAnimationBuilder(this.name, { ...this.config, ...next });
    }

    duration(durationMs: number) {
        return this.withConfig({ duration: durationMs });
    }

    delay(delayMs: number) {
        return this.withConfig({ delay: delayMs });
    }

    randomDelay(maxDelayMs = 1000) {
        return this.withConfig({ randomDelay: maxDelayMs });
    }

    easing(value: EasingFunction) {
        return this.withConfig({ easing: value.__spotifyPlusEasing ?? serializeWorklet(value, `${this.name}.easing`) });
    }

    easingX(value: EasingFunction) {
        return this.withConfig({ easingX: value.__spotifyPlusEasing ?? serializeWorklet(value, `${this.name}.easingX`) });
    }

    easingY(value: EasingFunction) {
        return this.withConfig({ easingY: value.__spotifyPlusEasing ?? serializeWorklet(value, `${this.name}.easingY`) });
    }

    easingWidth(value: EasingFunction) {
        return this.withConfig({ easingWidth: value.__spotifyPlusEasing ?? serializeWorklet(value, `${this.name}.easingWidth`) });
    }

    easingHeight(value: EasingFunction) {
        return this.withConfig({ easingHeight: value.__spotifyPlusEasing ?? serializeWorklet(value, `${this.name}.easingHeight`) });
    }

    springify(durationMs?: number) {
        return this.withConfig({ animation: "spring", ...(durationMs === undefined ? {} : { duration: durationMs }) });
    }

    damping(value: number) {
        return this.withConfig({ damping: value });
    }

    dampingRatio(value: number) {
        return this.withConfig({ dampingRatio: value });
    }

    mass(value: number) {
        return this.withConfig({ mass: value });
    }

    stiffness(value: number) {
        return this.withConfig({ stiffness: value });
    }

    overshootClamping(value = true) {
        return this.withConfig({ overshootClamping: value });
    }

    energyThreshold(value: number) {
        return this.withConfig({ energyThreshold: value });
    }

    rotate(degrees: string | number) {
        return this.withConfig({ rotate: degrees });
    }

    perspective(value: number) {
        return this.withConfig({ perspective: value });
    }

    reverse(value = true) {
        return this.withConfig({ reverse: value });
    }

    entering(value: LayoutAnimation) {
        return this.withConfig({ entering: value });
    }

    exiting(value: LayoutAnimation) {
        return this.withConfig({ exiting: value });
    }

    reduceMotion(value: ReduceMotionSetting) {
        return this.withConfig({ reduceMotion: value });
    }

    withInitialValues(values: Readonly<Record<string, unknown>>) {
        return this.withConfig({ initialValues: values });
    }

    withCallback(callback: WorkletFunctionLike<LayoutAnimationCallback>) {
        return this.withConfig({ callback: serializeWorklet(callback, `${this.name}.withCallback`) });
    }

    build() {
        return this;
    }
}

function preset(name: string) {
    return new LayoutAnimationBuilder(name, { reduceMotion: ReduceMotion.System });
}

export class Keyframe extends LayoutAnimationBuilder {
    constructor(definitions: Readonly<Record<string, Readonly<Record<string, unknown>>>>) {
        super("Keyframe", { definitions });
    }
}

export const BounceIn = preset("BounceIn");
export const BounceInDown = preset("BounceInDown");
export const BounceInLeft = preset("BounceInLeft");
export const BounceInRight = preset("BounceInRight");
export const BounceInUp = preset("BounceInUp");
export const BounceOut = preset("BounceOut");
export const BounceOutDown = preset("BounceOutDown");
export const BounceOutLeft = preset("BounceOutLeft");
export const BounceOutRight = preset("BounceOutRight");
export const BounceOutUp = preset("BounceOutUp");

export const FadeIn = preset("FadeIn");
export const FadeInDown = preset("FadeInDown");
export const FadeInDownBig = preset("FadeInDownBig");
export const FadeInLeft = preset("FadeInLeft");
export const FadeInLeftBig = preset("FadeInLeftBig");
export const FadeInRight = preset("FadeInRight");
export const FadeInRightBig = preset("FadeInRightBig");
export const FadeInUp = preset("FadeInUp");
export const FadeInUpBig = preset("FadeInUpBig");
export const FadeOut = preset("FadeOut");
export const FadeOutDown = preset("FadeOutDown");
export const FadeOutDownBig = preset("FadeOutDownBig");
export const FadeOutLeft = preset("FadeOutLeft");
export const FadeOutLeftBig = preset("FadeOutLeftBig");
export const FadeOutRight = preset("FadeOutRight");
export const FadeOutRightBig = preset("FadeOutRightBig");
export const FadeOutUp = preset("FadeOutUp");
export const FadeOutUpBig = preset("FadeOutUpBig");

export const FlipInEasyX = preset("FlipInEasyX");
export const FlipInEasyY = preset("FlipInEasyY");
export const FlipInXDown = preset("FlipInXDown");
export const FlipInXUp = preset("FlipInXUp");
export const FlipInYLeft = preset("FlipInYLeft");
export const FlipInYRight = preset("FlipInYRight");
export const FlipOutEasyX = preset("FlipOutEasyX");
export const FlipOutEasyY = preset("FlipOutEasyY");
export const FlipOutXDown = preset("FlipOutXDown");
export const FlipOutXUp = preset("FlipOutXUp");
export const FlipOutYLeft = preset("FlipOutYLeft");
export const FlipOutYRight = preset("FlipOutYRight");

export const LightSpeedInLeft = preset("LightSpeedInLeft");
export const LightSpeedInRight = preset("LightSpeedInRight");
export const LightSpeedOutLeft = preset("LightSpeedOutLeft");
export const LightSpeedOutRight = preset("LightSpeedOutRight");
export const PinwheelIn = preset("PinwheelIn");
export const PinwheelOut = preset("PinwheelOut");
export const RollInLeft = preset("RollInLeft");
export const RollInRight = preset("RollInRight");
export const RollOutLeft = preset("RollOutLeft");
export const RollOutRight = preset("RollOutRight");

export const RotateInDownLeft = preset("RotateInDownLeft");
export const RotateInDownRight = preset("RotateInDownRight");
export const RotateInUpLeft = preset("RotateInUpLeft");
export const RotateInUpRight = preset("RotateInUpRight");
export const RotateOutDownLeft = preset("RotateOutDownLeft");
export const RotateOutDownRight = preset("RotateOutDownRight");
export const RotateOutUpLeft = preset("RotateOutUpLeft");
export const RotateOutUpRight = preset("RotateOutUpRight");

export const SlideInDown = preset("SlideInDown");
export const SlideInLeft = preset("SlideInLeft");
export const SlideInRight = preset("SlideInRight");
export const SlideInUp = preset("SlideInUp");
export const SlideOutDown = preset("SlideOutDown");
export const SlideOutLeft = preset("SlideOutLeft");
export const SlideOutRight = preset("SlideOutRight");
export const SlideOutUp = preset("SlideOutUp");

export const StretchInX = preset("StretchInX");
export const StretchInY = preset("StretchInY");
export const StretchOutX = preset("StretchOutX");
export const StretchOutY = preset("StretchOutY");

export const ZoomIn = preset("ZoomIn");
export const ZoomInDown = preset("ZoomInDown");
export const ZoomInEasyDown = preset("ZoomInEasyDown");
export const ZoomInEasyUp = preset("ZoomInEasyUp");
export const ZoomInLeft = preset("ZoomInLeft");
export const ZoomInRight = preset("ZoomInRight");
export const ZoomInRotate = preset("ZoomInRotate");
export const ZoomInUp = preset("ZoomInUp");
export const ZoomOut = preset("ZoomOut");
export const ZoomOutDown = preset("ZoomOutDown");
export const ZoomOutEasyDown = preset("ZoomOutEasyDown");
export const ZoomOutEasyUp = preset("ZoomOutEasyUp");
export const ZoomOutLeft = preset("ZoomOutLeft");
export const ZoomOutRight = preset("ZoomOutRight");
export const ZoomOutRotate = preset("ZoomOutRotate");
export const ZoomOutUp = preset("ZoomOutUp");

export const Layout = preset("Layout");
export const LinearTransition = preset("LinearTransition");
export const SequencedTransition = preset("SequencedTransition");
export const FadingTransition = preset("FadingTransition");
export const JumpingTransition = preset("JumpingTransition");
export const CurvedTransition = preset("CurvedTransition");
export const EntryExitTransition = preset("EntryExitTransition");
export const SharedTransition = preset("SharedTransition");

export interface LayoutAnimationConfigProps {
    children?: React.ReactNode;
    skipEntering?: boolean;
    skipExiting?: boolean;
}

export interface LayoutAnimationBoundary {
    readonly skipEntering: boolean;
    readonly skipExiting: boolean;
}

const LayoutAnimationBoundaryContext = React.createContext<LayoutAnimationBoundary>({
    skipEntering: false,
    skipExiting: false,
});

export function LayoutAnimationConfig({
    children,
    skipEntering = false,
    skipExiting = false,
}: LayoutAnimationConfigProps) {
    return React.createElement(
        LayoutAnimationBoundaryContext.Provider,
        { value: { skipEntering, skipExiting } },
        children,
    );
}

export function useLayoutAnimationBoundary() {
    return React.useContext(LayoutAnimationBoundaryContext);
}

export type CSSKeyframeSelector = "from" | "to" | `${number}%`;
export type CSSAnimationKeyframes = Readonly<Partial<Record<CSSKeyframeSelector, Readonly<Record<string, unknown>>>>>;

export interface CSSKeyframesRule {
    readonly __spotifyPlusCSSKeyframes: true;
    readonly frames: CSSAnimationKeyframes;
}

export interface CSSAnimationProperties {
    animationName?: CSSKeyframesRule | string | readonly (CSSKeyframesRule | string)[];
    animationDuration?: number | string | readonly (number | string)[];
    animationDelay?: number | string | readonly (number | string)[];
    animationTimingFunction?: CSSTimingFunction | readonly CSSTimingFunction[];
    animationIterationCount?: number | "infinite" | readonly (number | "infinite")[];
    animationDirection?: "normal" | "reverse" | "alternate" | "alternate-reverse";
    animationFillMode?: "none" | "forwards" | "backwards" | "both";
    animationPlayState?: "running" | "paused";
}

export interface CSSTransitionProperties {
    transitionProperty?: string | readonly string[] | "all" | "none";
    transitionDuration?: number | string | readonly (number | string)[];
    transitionDelay?: number | string | readonly (number | string)[];
    transitionTimingFunction?: CSSTimingFunction | readonly CSSTimingFunction[];
    transitionBehavior?: "normal" | "allow-discrete";
}

export type CSSProperties = CSSAnimationProperties & CSSTransitionProperties & Readonly<Record<string, unknown>>;

export interface CSSTimingFunction {
    readonly type: "linear" | "cubicBezier" | "steps";
    readonly values: readonly number[];
    readonly stepPosition?: "jump-start" | "jump-end" | "jump-none" | "jump-both" | "start" | "end";
}

export function createKeyframes(frames: CSSAnimationKeyframes): CSSKeyframesRule {
    return { __spotifyPlusCSSKeyframes: true, frames };
}

export function cubicBezier(x1: number, y1: number, x2: number, y2: number): CSSTimingFunction {
    return { type: "cubicBezier", values: [x1, y1, x2, y2] };
}

export function linear(...points: number[]): CSSTimingFunction {
    return { type: "linear", values: points.length === 0 ? [0, 1] : points };
}

export function steps(
    count: number,
    position: CSSTimingFunction["stepPosition"] = "end",
): CSSTimingFunction {
    return { type: "steps", values: [count], stepPosition: position };
}

export const CSS = {
    keyframes: createKeyframes,
    cubicBezier,
    linear,
    steps,
};

// Common web timing presets are useful even when native CSS execution is not available yet.
export const CSSAnimationEasing = {
    linear: linear(0, 1),
    ease: cubicBezier(0.25, 0.1, 0.25, 1),
    easeIn: cubicBezier(0.42, 0, 1, 1),
    easeOut: cubicBezier(0, 0, 0.58, 1),
    easeInOut: cubicBezier(0.42, 0, 0.58, 1),
};

export const DefaultLayoutEasing = Easing.inOut(Easing.quad);
