import {
    ANIMATION_MARKER_KEY,
    ReduceMotion,
    type AnimatableValue,
    type Animation,
    type AnimationCallback,
    type AnimationDefinition,
    type ClampConfig,
    type DecayConfig,
    type EasingFunction,
    type ReduceMotionSetting,
    type SerializedWorklet,
    type SpringConfig,
    type TimingConfig,
    type WorkletFunctionLike,
} from "./types";
import { serializeWorklet } from "./worklets";

function clampUnit(value: number) {
    return Math.min(1, Math.max(0, value));
}

function easing(
    type: string,
    evaluator: (value: number) => number,
    config: Readonly<Record<string, unknown>> = {},
): EasingFunction {
    const fn = ((value: number) => evaluator(clampUnit(value))) as EasingFunction;
    Object.defineProperty(fn, "__spotifyPlusEasing", {
        value: { type, ...config },
        enumerable: true,
    });
    return fn;
}

function normalizeEasing(value: EasingFunction | undefined) {
    if (!value) {
        return Easing.inOut(Easing.quad).__spotifyPlusEasing;
    }

    if (value.__spotifyPlusEasing) {
        return value.__spotifyPlusEasing;
    }

    return serializeWorklet(value, "withTiming(config.easing)");
}

export const Easing = {
    linear: easing("linear", value => value),
    ease: easing("bezier", value => value * value * (3 - 2 * value), {
        x1: 0.42,
        y1: 0,
        x2: 1,
        y2: 1,
    }),
    quad: easing("quad", value => value * value),
    cubic: easing("cubic", value => value * value * value),
    poly(power: number) {
        return easing("poly", value => value ** power, { power });
    },
    sin: easing("sin", value => 1 - Math.cos((value * Math.PI) / 2)),
    circle: easing("circle", value => 1 - Math.sqrt(1 - value * value)),
    exp: easing("exp", value => value === 0 ? 0 : 2 ** (10 * (value - 1))),
    elastic(bounciness = 1) {
        return easing(
            "elastic",
            value => 1 - Math.cos((value * Math.PI) / 2) ** 3 * Math.cos(value * bounciness * Math.PI),
            { bounciness },
        );
    },
    back(overshoot = 1.70158) {
        return easing("back", value => value * value * ((overshoot + 1) * value - overshoot), { overshoot });
    },
    bounce: easing("bounce", value => {
        if (value < 1 / 2.75) {
            return 7.5625 * value * value;
        }
        if (value < 2 / 2.75) {
            const shifted = value - 1.5 / 2.75;
            return 7.5625 * shifted * shifted + 0.75;
        }
        if (value < 2.5 / 2.75) {
            const shifted = value - 2.25 / 2.75;
            return 7.5625 * shifted * shifted + 0.9375;
        }
        const shifted = value - 2.625 / 2.75;
        return 7.5625 * shifted * shifted + 0.984375;
    }),
    bezier(x1: number, y1: number, x2: number, y2: number) {
        return easing("bezier", value => cubicBezierAt(value, x1, y1, x2, y2), { x1, y1, x2, y2 });
    },
    bezierFn(x1: number, y1: number, x2: number, y2: number) {
        return Easing.bezier(x1, y1, x2, y2);
    },
    steps(count: number, roundToNextStep = false) {
        if (!Number.isFinite(count) || count <= 0) {
            throw new RangeError("Easing.steps count must be a positive finite number.");
        }
        const safeCount = Math.floor(count);
        return easing(
            "steps",
            value => (roundToNextStep ? Math.ceil(value * safeCount) : Math.floor(value * safeCount)) / safeCount,
            { count: safeCount, roundToNextStep },
        );
    },
    in(value: EasingFunction) {
        return easing("in", progress => value(progress), { easing: value.__spotifyPlusEasing ?? value });
    },
    out(value: EasingFunction) {
        return easing("out", progress => 1 - value(1 - progress), { easing: value.__spotifyPlusEasing ?? value });
    },
    inOut(value: EasingFunction) {
        return easing(
            "inOut",
            progress => progress < 0.5 ? value(progress * 2) / 2 : 1 - value((1 - progress) * 2) / 2,
            { easing: value.__spotifyPlusEasing ?? value },
        );
    },
};

function cubicBezierAt(value: number, x1: number, y1: number, x2: number, y2: number) {
    const coordinate = (time: number, first: number, second: number) => {
        const inverse = 1 - time;
        return 3 * inverse * inverse * time * first
            + 3 * inverse * time * time * second
            + time ** 3;
    };
    let low = 0;
    let high = 1;
    let time = value;
    for (let iteration = 0; iteration < 14; iteration++) {
        time = (low + high) / 2;
        if (coordinate(time, x1, x2) < value) {
            low = time;
        } else {
            high = time;
        }
    }
    return coordinate(time, y1, y2);
}

function animation<Value>(
    type: string,
    toValue?: Value,
    config?: Readonly<Record<string, unknown>>,
    children?: readonly AnimationDefinition<Value>[],
    callback?: WorkletFunctionLike<AnimationCallback>,
): Animation<Value> {
    return {
        [ANIMATION_MARKER_KEY]: true,
        type,
        ...(toValue !== undefined ? { toValue } : {}),
        ...(config ? { config } : {}),
        ...(children ? { children } : {}),
        ...(callback ? { callback: serializeWorklet(callback, `${type} callback`) } : {}),
    };
}

export function isAnimation(value: unknown): value is AnimationDefinition {
    return value !== null
        && typeof value === "object"
        && (value as Partial<AnimationDefinition>)[ANIMATION_MARKER_KEY] === true;
}

export function withTiming<Value extends AnimatableValue>(
    toValue: Value,
    config: TimingConfig = {},
    callback?: WorkletFunctionLike<AnimationCallback>,
): Value {
    return animation(
        "timing",
        toValue,
        {
            duration: config.duration ?? 300,
            easing: normalizeEasing(config.easing),
            reduceMotion: config.reduceMotion ?? ReduceMotion.System,
        },
        undefined,
        callback,
    ) as unknown as Value;
}

export function withSpring<Value extends AnimatableValue>(
    toValue: Value,
    config: SpringConfig = {},
    callback?: WorkletFunctionLike<AnimationCallback>,
): Value {
    return animation(
        "spring",
        toValue,
        {
            ...config,
            reduceMotion: config.reduceMotion ?? ReduceMotion.System,
        },
        undefined,
        callback,
    ) as unknown as Value;
}

export function withDecay(
    config: DecayConfig = {},
    callback?: WorkletFunctionLike<AnimationCallback>,
): number {
    return animation<number>(
        "decay",
        undefined,
        {
            velocity: config.velocity ?? 0,
            deceleration: config.deceleration ?? 0.998,
            velocityFactor: config.velocityFactor ?? 1,
            rubberBandEffect: config.rubberBandEffect ?? false,
            rubberBandFactor: config.rubberBandFactor ?? 0.6,
            clamp: config.clamp,
            reduceMotion: config.reduceMotion ?? ReduceMotion.System,
        },
        undefined,
        callback,
    ) as unknown as number;
}

export function withDelay<Value extends AnimatableValue>(
    delayMs: number,
    delayedAnimation: Value,
    reduceMotion: ReduceMotionSetting = ReduceMotion.System,
): Value {
    const descriptor = delayedAnimation as unknown as Animation<Value>;
    return animation("delay", descriptor.toValue, { delayMs, reduceMotion }, [descriptor]) as unknown as Value;
}

export function withRepeat<Value extends AnimatableValue>(
    repeatedAnimation: Value,
    numberOfReps = 2,
    reverse = false,
    callback?: WorkletFunctionLike<AnimationCallback>,
    reduceMotion: ReduceMotionSetting = ReduceMotion.System,
): Value {
    const descriptor = repeatedAnimation as unknown as Animation<Value>;
    return animation(
        "repeat",
        descriptor.toValue,
        { numberOfReps, reverse, reduceMotion },
        [descriptor],
        callback,
    ) as unknown as Value;
}

export function withSequence<Value extends AnimatableValue>(...animations: Value[]): Value;
export function withSequence<Value extends AnimatableValue>(
    reduceMotion: ReduceMotionSetting,
    ...animations: Value[]
): Value;
export function withSequence<Value extends AnimatableValue>(
    first: Value | ReduceMotionSetting | undefined,
    ...rest: Value[]
): Value {
    if (first === undefined) {
        return animation<Value>("sequence", undefined, { reduceMotion: ReduceMotion.System }, []) as unknown as Value;
    }
    const hasReduceMotion = typeof first === "string";
    const values = hasReduceMotion ? rest : [first as Value, ...rest];
    const children = values.map(value => value as unknown as Animation<Value>);
    const reduceMotion = hasReduceMotion ? first as ReduceMotionSetting : ReduceMotion.System;
    return animation(
        "sequence",
        children[children.length - 1]?.toValue,
        { reduceMotion },
        children,
    ) as unknown as Value;
}

export function withClamp<Value extends AnimatableValue>(config: ClampConfig, clampedAnimation: Value): Value {
    const descriptor = clampedAnimation as unknown as Animation<Value>;
    return animation("clamp", descriptor.toValue, { ...config }, [descriptor]) as unknown as Value;
}

export interface CustomAnimationState<Value extends AnimatableValue> {
    current: Value;
    callback?: SerializedWorklet<AnimationCallback>;
    onStart?: WorkletFunctionLike<(
        animation: CustomAnimationState<Value>,
        value: Value,
        now: number,
        previousAnimation: CustomAnimationState<Value> | null,
    ) => void>;
    onFrame: WorkletFunctionLike<(animation: CustomAnimationState<Value>, now: number) => boolean>;
}

export function defineAnimation<Value extends AnimatableValue>(
    startingValue: Value,
    factory: WorkletFunctionLike<() => CustomAnimationState<Value>>,
): Animation<Value> {
    return animation("custom", startingValue, {
        factory: serializeWorklet(factory, "defineAnimation"),
    });
}

export function withCustomAnimation<Value extends AnimatableValue>(
    startingValue: Value,
    factory: WorkletFunctionLike<() => CustomAnimationState<Value>>,
): Animation<Value> {
    return defineAnimation(startingValue, factory);
}
