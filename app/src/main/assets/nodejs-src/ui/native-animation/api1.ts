import React from "react";
import {
    cancelAnimatedValue,
    removeAnimatedProps,
    setAnimatedProps,
    updateAnimatedValue,
    type NativeComponentRef,
} from "../renderer";

const EXPRESSION = Symbol.for("spotifyplus.animated.expression");
const ANIMATED_STYLE = Symbol.for("spotifyplus.animated.style");
const ANIMATED_PROPS = Symbol.for("spotifyplus.animated.props");
const useAnimatedBindingEffect = React.useLayoutEffect ?? React.useEffect;

export type Extrapolate = "extend" | "clamp" | "identity";
export type AnimatedPrimitive = number | string | boolean | null;
export type AnimatedJSON = AnimatedPrimitive | AnimatedJSON[] | { [key: string]: AnimatedJSON };
export type AnimatedExpr =
    | AnimatedJSON
    | { $a: "value"; id: number; initial: AnimatedJSON }
    | { $a: "derived"; id: number; expr: AnimatedExpr }
    | { $a: "const"; value: AnimatedJSON }
    | { $a: "playbackClock"; unit?: "ms" | "seconds"; offset?: number }
    | { $a: "add" | "sub" | "mul" | "div" | "mod"; values: AnimatedExpr[] }
    | { $a: "clamp"; input: AnimatedExpr; min: number; max: number }
    | {
        $a: "interpolate" | "interpolateColor";
        input: AnimatedExpr;
        inputRange: number[];
        outputRange: Array<number | string>;
        extrapolateLeft?: Extrapolate;
        extrapolateRight?: Extrapolate;
    };

export type TimingConfig = {
    duration?: number;
    delay?: number;
    easing?: "linear" | "ease" | "easeIn" | "easeOut" | "easeInOut";
};

export type SpringConfig = {
    stiffness?: number;
    damping?: number;
    mass?: number;
    velocity?: number;
    overshootClamping?: boolean;
    restSpeedThreshold?: number;
    restDisplacementThreshold?: number;
    delay?: number;
};

export type InterpolateOptions = {
    extrapolate?: Extrapolate;
    extrapolateLeft?: Extrapolate;
    extrapolateRight?: Extrapolate;
};

export type AnimatedStylePayload<T extends Record<string, any> = Record<string, any>> = {
    readonly [ANIMATED_STYLE]: true;
    readonly id: number;
    readonly value: T;
};

export type AnimatedPropsPayload<T extends Record<string, any> = Record<string, any>> = {
    readonly [ANIMATED_PROPS]: true;
    readonly id: number;
    readonly value: T;
};

export type NativeAnimatedNodeLike<T = any> = AnimatedExpression<T> | SharedValue<T> | DerivedValue<T>;
export type AnimatedResolvedValue<T> = T extends AnimatedExpression<infer U> ? AnimatedResolvedValue<U> : T;

export type AnimationDescriptor<T = any> = {
    readonly __spotifyPlusAnimation: true;
    readonly node: Record<string, any>;
    readonly toValue?: T;
};

let nextAnimatedId = 1;

function createAnimatedId() {
    return nextAnimatedId++;
}

function isPlainObject(value: unknown): value is Record<string, any> {
    return !!value && typeof value === "object" && !Array.isArray(value);
}

function isJSONCompatible(value: unknown): value is AnimatedJSON {
    if (value === null) return true;
    const type = typeof value;
    if (type === "string" || type === "number" || type === "boolean") return true;
    if (Array.isArray(value)) return value.every(isJSONCompatible);
    if (!isPlainObject(value)) return false;
    return Object.values(value).every(isJSONCompatible);
}

function normalizeStatic(value: unknown): AnimatedJSON {
    if (value === undefined) return null;
    if (isJSONCompatible(value)) return value;
    return String(value);
}

export class AnimatedExpression<T = any> {
    readonly [EXPRESSION] = true;

    constructor(readonly node: AnimatedExpr) { }
}

export class SharedValue<T = any> extends AnimatedExpression<T> {
    private current: T;

    constructor(readonly id: number, initial: T) {
        super({ $a: "value", id, initial: normalizeStatic(initial) });
        this.current = initial;
    }

    get value(): T {
        return new AnimatedExpression<T>({ $a: "value", id: this.id, initial: normalizeStatic(this.current) }) as unknown as T;
    }

    set value(next: T | AnimationDescriptor<T> | AnimatedExpression<T>) {
        if (isAnimationDescriptor(next)) {
            this.current = (next.toValue ?? this.current) as T;
            updateAnimatedValue(this.id, normalizeStatic(this.current), next.node);
            return;
        }

        this.current = resolveAssignedValue(next) as T;
        updateAnimatedValue(this.id, serializeAnimatedNode(next), undefined);
    }

    get(): T {
        return this.current;
    }

    set(next: T | AnimationDescriptor<T> | AnimatedExpression<T>) {
        this.value = next;
    }
}

export class DerivedValue<T = any> extends AnimatedExpression<T> {
    constructor(readonly id: number, expr: AnimatedExpr) {
        super({ $a: "derived", id, expr });
    }

    get value(): T {
        return this as unknown as T;
    }
}

export function isAnimatedNodeLike(value: unknown): value is NativeAnimatedNodeLike {
    return !!value && typeof value === "object" && EXPRESSION in value;
}

function isAnimationDescriptor(value: unknown): value is AnimationDescriptor {
    return !!value && typeof value === "object" && (value as any).__spotifyPlusAnimation === true;
}

function resolveAssignedValue(value: unknown): unknown {
    if (value instanceof AnimatedExpression) return value.node;
    return value;
}

export function serializeAnimatedNode(value: unknown): any {
    if (value instanceof SharedValue) return value.node;
    if (value instanceof AnimatedExpression) return value.node;
    if (Array.isArray(value)) return value.map(serializeAnimatedNode);
    if (isPlainObject(value)) {
        const output: Record<string, any> = {};
        for (const [key, child] of Object.entries(value)) output[key] = serializeAnimatedNode(child);
        return output;
    }
    return normalizeStatic(value);
}

function expr<T = any>(node: AnimatedExpr): AnimatedExpression<T> {
    return new AnimatedExpression<T>(node);
}

function valueExpr(value: unknown): AnimatedExpr {
    return serializeAnimatedNode(value) as AnimatedExpr;
}

export function useSharedValue<T = number>(initial: T): SharedValue<T> {
    const ref = React.useRef<SharedValue<T> | null>(null);
    if (ref.current == null) ref.current = new SharedValue<T>(createAnimatedId(), initial);
    return ref.current;
}

export function useDerivedValue<T = any>(factory: () => T, deps?: React.DependencyList): DerivedValue<AnimatedResolvedValue<T>> {
    const idRef = React.useRef<number>(0);
    if (idRef.current === 0) idRef.current = createAnimatedId();
    const serialized = React.useMemo(() => valueExpr(factory()), deps ?? []);
    return React.useMemo(() => new DerivedValue<AnimatedResolvedValue<T>>(idRef.current, serialized), [serialized]);
}

export function useAnimatedStyle<T extends Record<string, any>>(factory: () => T, deps?: React.DependencyList): AnimatedStylePayload<T> {
    const idRef = React.useRef<number>(0);
    if (idRef.current === 0) idRef.current = createAnimatedId();
    const value = React.useMemo(factory, deps ?? []);
    return React.useMemo(() => ({ [ANIMATED_STYLE]: true, id: idRef.current, value }), [value]);
}

export function useAnimatedProps<T extends Record<string, any>>(factory: () => T, deps?: React.DependencyList): AnimatedPropsPayload<T> {
    const idRef = React.useRef<number>(0);
    if (idRef.current === 0) idRef.current = createAnimatedId();
    const value = React.useMemo(factory, deps ?? []);
    return React.useMemo(() => ({ [ANIMATED_PROPS]: true, id: idRef.current, value }), [value]);
}

export function playbackClock(options?: { unit?: "ms" | "seconds"; offset?: number }) {
    return expr<number>({
        $a: "playbackClock",
        unit: options?.unit ?? "ms",
        ...(options?.offset !== undefined ? { offset: options.offset } : {}),
    });
}

export function interpolate(
    value: unknown,
    inputRange: number[],
    outputRange: number[],
    options?: InterpolateOptions,
) {
    return expr<number>({
        $a: "interpolate",
        input: valueExpr(value),
        inputRange,
        outputRange,
        extrapolateLeft: options?.extrapolateLeft ?? options?.extrapolate,
        extrapolateRight: options?.extrapolateRight ?? options?.extrapolate,
    });
}

export function interpolateColor(
    value: unknown,
    inputRange: number[],
    outputRange: Array<string | number>,
    options?: InterpolateOptions,
) {
    return expr<string | number>({
        $a: "interpolateColor",
        input: valueExpr(value),
        inputRange,
        outputRange,
        extrapolateLeft: options?.extrapolateLeft ?? options?.extrapolate,
        extrapolateRight: options?.extrapolateRight ?? options?.extrapolate,
    });
}

function op(name: "add" | "sub" | "mul" | "div" | "mod", values: unknown[]) {
    return expr<number>({ $a: name, values: values.map(valueExpr) });
}

export const add = (...values: unknown[]) => op("add", values);
export const subtract = (...values: unknown[]) => op("sub", values);
export const multiply = (...values: unknown[]) => op("mul", values);
export const divide = (...values: unknown[]) => op("div", values);
export const modulo = (...values: unknown[]) => op("mod", values);
export const clamp = (input: unknown, min: number, max: number) => expr<number>({ $a: "clamp", input: valueExpr(input), min, max });

function animation<T>(node: Record<string, any>, toValue?: T): AnimationDescriptor<T> {
    return { __spotifyPlusAnimation: true, node, toValue };
}

export function withTiming<T = number>(toValue: T | AnimatedExpression<T>, config: TimingConfig = {}) {
    return animation<T>({
        $anim: "timing",
        toValue: valueExpr(toValue),
        duration: config.duration ?? 300,
        delay: config.delay ?? 0,
        easing: config.easing ?? "easeInOut",
    }, toValue instanceof AnimatedExpression ? undefined : toValue);
}

export function withSpring<T = number>(toValue: T | AnimatedExpression<T>, config: SpringConfig = {}) {
    return animation<T>({
        $anim: "spring",
        toValue: valueExpr(toValue),
        stiffness: config.stiffness ?? 180,
        damping: config.damping ?? 22,
        mass: config.mass ?? 1,
        velocity: config.velocity ?? 0,
        overshootClamping: config.overshootClamping === true,
        restSpeedThreshold: config.restSpeedThreshold ?? 0.05,
        restDisplacementThreshold: config.restDisplacementThreshold ?? 0.05,
        delay: config.delay ?? 0,
    }, toValue instanceof AnimatedExpression ? undefined : toValue);
}

export function withDelay<T = any>(delayMs: number, child: AnimationDescriptor<T>) {
    return animation<T>({ $anim: "delay", delayMs, animation: child.node }, child.toValue);
}

export function withSequence<T = any>(...animations: AnimationDescriptor<T>[]) {
    return animation<T>({ $anim: "sequence", animations: animations.map(item => item.node) }, animations[animations.length - 1]?.toValue);
}

export function cancelAnimation(sharedValue: SharedValue<any>) {
    cancelAnimatedValue(sharedValue.id);
}

export function runOnUI<T extends (...args: any[]) => any>(fn: T): T {
    return ((...args: Parameters<T>) => fn(...args)) as T;
}

function isAnimatedStylePayload(value: unknown): value is AnimatedStylePayload {
    return !!value && typeof value === "object" && ANIMATED_STYLE in value;
}

function isAnimatedPropsPayload(value: unknown): value is AnimatedPropsPayload {
    return !!value && typeof value === "object" && ANIMATED_PROPS in value;
}

function flattenStyle(style: any): Record<string, any> {
    if (!style) return {};
    if (Array.isArray(style)) {
        const output: Record<string, any> = {};
        for (const entry of style) Object.assign(output, flattenStyle(entry));
        return output;
    }
    return isPlainObject(style) ? { ...style } : {};
}

function normalizeTransformKey(key: string) {
    switch (key) {
        case "opacity":
            return "alpha";
        case "translateX":
            return "translationX";
        case "translateY":
            return "translationY";
        case "translateZ":
            return "translationZ";
        case "rotate":
            return "rotation";
        case "rotateX":
            return "rotationX";
        case "rotateY":
            return "rotationY";
        default:
            return key;
    }
}

function styleToNativeProps(style: any): Record<string, any> {
    const input = flattenStyle(style);
    const output: Record<string, any> = {};

    for (const [key, value] of Object.entries(input)) {
        if (key === "transform" && Array.isArray(value)) {
            for (const item of value) {
                if (!isPlainObject(item)) continue;
                for (const [transformKey, transformValue] of Object.entries(item)) {
                    output[normalizeTransformKey(transformKey)] = transformValue;
                }
            }
            continue;
        }

        output[normalizeTransformKey(key)] = value;
    }

    return output;
}

function splitStyle(style: any, animatedOut: Record<string, any>): any {
    if (!Array.isArray(style)) {
        if (isAnimatedStylePayload(style)) {
            Object.assign(animatedOut, styleToNativeProps(style.value));
            return undefined;
        }
        return style;
    }

    const regular: any[] = [];
    for (const entry of style) {
        if (isAnimatedStylePayload(entry)) {
            Object.assign(animatedOut, styleToNativeProps(entry.value));
        } else {
            regular.push(entry);
        }
    }

    return regular.length === 0 ? undefined : regular;
}

function serializeAnimatedProps(props: Record<string, any>) {
    const output: Record<string, any> = {};
    for (const [key, value] of Object.entries(props)) output[key] = serializeAnimatedNode(value);
    return output;
}

function getNodeId(ref: any): number | null {
    const raw = ref?.nodeId ?? ref?.id ?? (typeof ref?.getNativeNodeId === "function" ? ref.getNativeNodeId() : null);
    return typeof raw === "number" && Number.isFinite(raw) ? raw : null;
}

export type AnimatedComponentProps<P> = P & {
    animatedProps?: AnimatedPropsPayload | Record<string, any>;
    ref?: React.Ref<NativeComponentRef>;
};

export function createAnimatedComponent<P extends { style?: any }>(
    Component: React.ComponentType<P>,
): React.ComponentType<AnimatedComponentProps<P>> {
    const AnimatedComponent = React.forwardRef<NativeComponentRef, AnimatedComponentProps<P>>((props, forwardedRef) => {
        const hostRef = React.useRef<any>(null);
        const { animatedProps, style, ...rest } = props as AnimatedComponentProps<P> & { style?: any };
        const animatedNativeProps: Record<string, any> = {};
        const regularStyle = splitStyle(style, animatedNativeProps);

        if (isAnimatedPropsPayload(animatedProps)) {
            Object.assign(animatedNativeProps, animatedProps.value);
        } else if (isPlainObject(animatedProps)) {
            Object.assign(animatedNativeProps, animatedProps);
        }

        const serialized = React.useMemo(() => serializeAnimatedProps(animatedNativeProps), [JSON.stringify(animatedNativeProps)]);

        React.useImperativeHandle(forwardedRef, () => hostRef.current, []);

        useAnimatedBindingEffect(() => {
            const nodeId = getNodeId(hostRef.current);
            if (nodeId == null) return;

            if (Object.keys(serialized).length === 0) {
                removeAnimatedProps(nodeId);
                return;
            }

            setAnimatedProps(nodeId, serialized);
            return () => removeAnimatedProps(nodeId);
        }, [serialized]);

        return React.createElement(Component as any, { ...(rest as any), style: regularStyle, ref: hostRef });
    });

    AnimatedComponent.displayName = `Animated.${(Component as any).displayName ?? Component.name ?? "Component"}`;
    return AnimatedComponent as unknown as React.ComponentType<AnimatedComponentProps<P>>;
}

export const API_VERSION = 1 as const;

export interface AnimatedAPI1HostComponents {
    readonly View?: React.ComponentType<any>;
    readonly Text?: React.ComponentType<any>;
    readonly Image?: React.ComponentType<any>;
    readonly ScriptView?: React.ComponentType<any>;
    readonly RenderView?: React.ComponentType<any>;
    readonly CanvasView?: React.ComponentType<any>;
    readonly ScrollView?: React.ComponentType<any>;
    readonly FlatList?: React.ComponentType<any>;
}

const API1_CORE = {
    API_VERSION,
    AnimatedExpression,
    SharedValue,
    DerivedValue,
    isAnimatedNodeLike,
    serializeAnimatedNode,
    useSharedValue,
    useDerivedValue,
    useAnimatedStyle,
    useAnimatedProps,
    playbackClock,
    interpolate,
    interpolateColor,
    add,
    subtract,
    multiply,
    divide,
    modulo,
    clamp,
    withTiming,
    withSpring,
    withDelay,
    withSequence,
    cancelAnimation,
    runOnUI,
    createAnimatedComponent,
};

/** Source-backed facade for scripts whose manifest explicitly requests API 1. */
export function createAnimatedAPI1(hosts: AnimatedAPI1HostComponents = {}) {
    const components = Object.fromEntries(
        Object.entries(hosts).map(([name, Component]) => [name, createAnimatedComponent(Component as React.ComponentType<any>)]),
    );
    return Object.freeze({ ...API1_CORE, ...components });
}

export const AnimatedAPI1 = Object.freeze(API1_CORE);
export default AnimatedAPI1;
