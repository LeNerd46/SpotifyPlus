import type React from "react";

export const ANIMATED_PAYLOAD_KEY = "__spotifyPlusAnimated" as const;
export const SHARED_VALUE_KEY = "__spotifyPlusSharedValue" as const;
export const WORKLET_METADATA_KEY = "__spotifyPlusWorklet" as const;
export const ANIMATION_MARKER_KEY = "__spotifyPlusAnimation" as const;

export type AnimatablePrimitive = number | string;
export type AnimatableValue = AnimatablePrimitive | AnimatablePrimitive[];
export type ShareablePrimitive = string | number | boolean | null | undefined | bigint;
export type ShareableValue =
    | ShareablePrimitive
    | ShareableValue[]
    | { readonly [key: string]: ShareableValue }
    | SharedValue<unknown>
    | WorkletFunction;

export enum ReduceMotion {
    System = "system",
    Always = "always",
    Never = "never",
}

export type ReduceMotionSetting = ReduceMotion | "system" | "always" | "never";

export type AnimationCallback = (finished?: boolean, current?: AnimatableValue) => void;

export interface WorkletMetadata {
    readonly version: 2;
    readonly hash: string;
    readonly code: string;
    readonly closure: Readonly<Record<string, unknown>>;
    readonly globals?: Readonly<Record<string, string>>;
    readonly location?: string;
    readonly sourceMap?: string;
}

export interface WorkletizedFunction {
    readonly [WORKLET_METADATA_KEY]?: WorkletMetadata;
    readonly __workletHash?: string | number;
    readonly __closure?: Readonly<Record<string, unknown>>;
    readonly __initData?: {
        readonly code?: string;
        readonly location?: string;
        readonly sourceMap?: string;
        readonly globals?: Readonly<Record<string, string>>;
    };
}

export type WorkletFunction<T extends (...args: any[]) => any = (...args: any[]) => any> = T & WorkletizedFunction;
export type WorkletFunctionLike<T extends (...args: any[]) => any> = T | WorkletFunction<T>;

export interface SerializedWorklet<T extends (...args: any[]) => any = (...args: any[]) => any> {
    readonly metadata: WorkletMetadata;
    /** Used only by JavaScript/testing adapters. Native adapters serialize metadata instead. */
    readonly callable: WorkletFunction<T>;
}

export interface AnimationDefinition<Value = AnimatableValue> {
    readonly [ANIMATION_MARKER_KEY]: true;
    readonly type: string;
    readonly toValue?: Value;
    readonly config?: Readonly<Record<string, unknown>>;
    readonly children?: readonly AnimationDefinition<Value>[];
    readonly callback?: SerializedWorklet<AnimationCallback>;
}

export type Animation<Value = AnimatableValue> = AnimationDefinition<Value>;

export interface SharedValue<Value = unknown> {
    readonly [SHARED_VALUE_KEY]: SharedValueMarker;
    get value(): Value;
    set value(next: Value | Animation<Value>);
    get(): Value;
    set(next: Value | Animation<Value> | ((current: Value) => Value)): void;
    modify(modifier?: (current: Value) => Value, forceUpdate?: boolean): void;
    addListener(listenerId: number, listener: (value: Value) => void): void;
    removeListener(listenerId: number): void;
}

export interface DerivedValue<Value = unknown> {
    readonly [SHARED_VALUE_KEY]: SharedValueMarker & { readonly derived: true };
    readonly value: Value;
    get(): Value;
    addListener(listenerId: number, listener: (value: Value) => void): void;
    removeListener(listenerId: number): void;
}

export type NativeAnimatedNodeLike<Value = unknown> = SharedValue<Value> | DerivedValue<Value>;
export type AnimatedResolvedValue<Value> = Value extends SharedValue<infer Result>
    ? Result
    : Value extends DerivedValue<infer Result>
        ? Result
        : Value;

export type ExtrapolationType = "identity" | "clamp" | "extend";
export type Extrapolate = ExtrapolationType;

export enum Extrapolation {
    IDENTITY = "identity",
    CLAMP = "clamp",
    EXTEND = "extend",
}

export interface ExtrapolationConfig {
    extrapolateLeft?: ExtrapolationType;
    extrapolateRight?: ExtrapolationType;
}

export type InterpolateOptions = ExtrapolationType | ExtrapolationConfig;

export interface InterpolationOptions extends ExtrapolationConfig {
    extrapolate?: ExtrapolationType;
}

export type EasingFunction = ((value: number) => number) & {
    readonly __spotifyPlusEasing?: Readonly<Record<string, unknown>>;
};

export interface TimingConfig {
    duration?: number;
    easing?: EasingFunction;
    reduceMotion?: ReduceMotionSetting;
}

export type SpringConfig = {
    mass?: number;
    damping?: number;
    stiffness?: number;
    overshootClamping?: boolean;
    energyThreshold?: number;
    velocity?: number;
    reduceMotion?: ReduceMotionSetting;
} | {
    duration?: number;
    dampingRatio?: number;
    clamp?: { min?: number; max?: number };
    mass?: never;
    damping?: never;
    stiffness?: never;
    reduceMotion?: ReduceMotionSetting;
};

export interface DecayConfig {
    velocity?: number;
    deceleration?: number;
    clamp?: readonly [number, number];
    velocityFactor?: number;
    rubberBandEffect?: boolean;
    rubberBandFactor?: number;
    reduceMotion?: ReduceMotionSetting;
}

export interface ClampConfig {
    min?: number;
    max?: number;
}

export interface FrameInfo {
    timestamp: number;
    timeSincePreviousFrame: number | null;
    timeSinceFirstFrame: number;
}

export interface FrameCallback {
    setActive(active: boolean): void;
    readonly isActive: boolean;
}

export interface AnimatedRuntimeScope {
    readonly scriptId: string;
    readonly generation: string | number;
    readonly surfaceId?: string | number;
}

export type AnimatedPayloadKind = "style" | "props" | "event";

export interface AnimatedPayloadMarker {
    readonly version: 2;
    readonly kind: AnimatedPayloadKind;
    readonly scriptId: string;
    readonly generation: string | number;
    readonly runtimeId: string;
    readonly id: number;
}

export interface SharedValueMarker {
    readonly version: 2;
    readonly scriptId: string;
    readonly generation: string | number;
    readonly runtimeId: string;
    readonly id: number;
    readonly derived?: boolean;
}

export interface SpotifyPlusClipStyle {
    clipLeft?: number | string;
    clipRight?: number | string;
    clipTop?: number | string;
    clipBottom?: number | string;
}

export type AnimatedStyle<Value extends object = Record<string, unknown>> = Value & SpotifyPlusClipStyle;

export interface AnimatedStylePayload<Value extends object = Record<string, unknown>> {
    readonly [ANIMATED_PAYLOAD_KEY]: AnimatedPayloadMarker & { readonly kind: "style" };
    readonly initialValue: AnimatedStyle<Value>;
}

export interface AnimatedPropsPayload<Value extends object = Record<string, unknown>> {
    readonly [ANIMATED_PAYLOAD_KEY]: AnimatedPayloadMarker & { readonly kind: "props" };
    readonly initialValue: Value;
}

export interface AnimatedEventHandler<Event = unknown> {
    (event: Event): void;
    readonly [ANIMATED_PAYLOAD_KEY]: AnimatedPayloadMarker & { readonly kind: "event" };
    readonly eventNames: readonly string[];
}

export type DependencyList = readonly unknown[];

export type AnimatedPropAdapter<Props extends Record<string, unknown> = Record<string, unknown>> =
    WorkletFunctionLike<(props: Props) => void> & {
        readonly nativeProps: readonly string[];
    };

export interface AnimatedRef<Component = unknown> {
    (component?: Component | null): number | null;
    current: Component | null;
    readonly __animatedRefId: number;
    getTag(): number | null;
}

export interface MeasuredDimensions {
    x: number;
    y: number;
    width: number;
    height: number;
    pageX: number;
    pageY: number;
}

export interface ScrollToOptions {
    x?: number;
    y?: number;
    animated?: boolean;
}

export interface NativeComponentRefLike {
    readonly nodeId?: number;
    readonly id?: number;
    getNativeNodeId?(): number | null;
}

export type AnimatedComponentProps<Props, Ref = NativeComponentRefLike> = Omit<Props, "style"> & {
    style?: Props extends { style?: infer Style } ? Style | AnimatedStylePayload<any> | readonly (Style | AnimatedStylePayload<any> | false | null | undefined)[] : AnimatedStylePayload<any>;
    animatedProps?: AnimatedPropsPayload<any>;
    entering?: LayoutAnimation;
    exiting?: LayoutAnimation;
    layout?: LayoutAnimation;
    sharedTransitionTag?: string;
    sharedTransitionStyle?: LayoutAnimation;
    ref?: React.Ref<Ref>;
};

export interface NativeMutableRequest<Value = unknown> {
    readonly id: number;
    readonly initial: Value;
}

export interface NativeMutableWrite<Value = unknown> {
    readonly id: number;
    readonly value: Value | Animation<Value>;
}

export type WorkletRegistrationKind =
    | "derived"
    | "style"
    | "props"
    | "reaction"
    | "frame"
    | "event"
    | "gesture";

export interface NativeWorkletRegistration {
    readonly id: number;
    readonly kind: WorkletRegistrationKind;
    readonly worklets: Readonly<Record<string, SerializedWorklet>>;
    readonly targetMutableIds?: readonly number[];
    readonly eventNames?: readonly string[];
    readonly dependencies?: readonly unknown[];
    readonly options?: Readonly<Record<string, unknown>>;
}

export interface NativeViewBinding {
    readonly viewTag: number;
    readonly styleIds: readonly number[];
    readonly propsIds: readonly number[];
    readonly eventIds: Readonly<Record<string, number>>;
    readonly entering?: SerializedLayoutAnimation;
    readonly exiting?: SerializedLayoutAnimation;
    readonly layout?: SerializedLayoutAnimation;
}

export type NativeSourceKind =
    | "playbackClock"
    | "sensor"
    | "keyboard"
    | "reducedMotion"
    | "frameTimestamp"
    | "scrollOffset";

export interface NativeSourceRegistration {
    readonly id: number;
    readonly kind: NativeSourceKind;
    readonly targetMutableIds: Readonly<Record<string, number>>;
    readonly config?: Readonly<Record<string, unknown>>;
}

export interface NativeScrollCommand extends ScrollToOptions {
    readonly viewTag: number;
}

export interface WorkletRuntimeHandle {
    readonly id: string | number;
    readonly name: string;
}

export interface NativeAnimationCapabilities {
    readonly worklets?: boolean;
    readonly workletGlobals?: boolean;
    readonly viewBindings?: boolean;
    readonly events?: boolean;
    readonly gestures?: boolean;
    readonly frameCallbacks?: boolean;
    readonly playbackClock?: boolean;
    readonly sensors?: boolean;
    readonly keyboard?: boolean;
    readonly layoutAnimations?: boolean;
    readonly cssAnimations?: boolean;
    readonly customRuntimes?: boolean;
    readonly measure?: boolean;
    readonly scroll?: boolean;
    readonly commands?: boolean;
    readonly setNativeProps?: boolean;
}

export interface NativeAnimationAdapter {
    /**
     * Adapter implementations may be shared by every script, but must key all
     * state by the supplied immutable scope plus the request ID. Registration
     * and view-binding calls may arrive in either order. All teardown calls are
     * idempotent so React unmount and script-generation disposal can overlap.
     */
    readonly name: string;
    readonly capabilities?: NativeAnimationCapabilities;
    installWorkletGlobals?(scope: AnimatedRuntimeScope, manifest: WorkletGlobalsManifest): void;
    createMutable<Value>(scope: AnimatedRuntimeScope, request: NativeMutableRequest<Value>): void;
    readMutable?<Value>(scope: AnimatedRuntimeScope, id: number): Value | undefined;
    writeMutable<Value>(scope: AnimatedRuntimeScope, request: NativeMutableWrite<Value>): void;
    subscribeMutable?<Value>(
        scope: AnimatedRuntimeScope,
        id: number,
        listener: (value: Value) => void,
    ): () => void;
    releaseMutable?(scope: AnimatedRuntimeScope, id: number): void;
    registerWorklet(scope: AnimatedRuntimeScope, registration: NativeWorkletRegistration): void;
    updateWorklet?(scope: AnimatedRuntimeScope, registration: NativeWorkletRegistration): void;
    unregisterWorklet(scope: AnimatedRuntimeScope, id: number): void;
    setWorkletActive?(scope: AnimatedRuntimeScope, id: number, active: boolean): void;
    bindView?(scope: AnimatedRuntimeScope, binding: NativeViewBinding): void;
    unbindView?(scope: AnimatedRuntimeScope, viewTag: number): void;
    scheduleOnUI(scope: AnimatedRuntimeScope, worklet: SerializedWorklet, args: readonly unknown[]): void;
    executeOnUISync?<Result>(
        scope: AnimatedRuntimeScope,
        worklet: SerializedWorklet<(...args: any[]) => Result>,
        args: readonly unknown[],
    ): Result;
    scheduleOnRN(scope: AnimatedRuntimeScope, fn: (...args: any[]) => unknown, args: readonly unknown[]): void;
    cancelAnimation(scope: AnimatedRuntimeScope, mutableId: number): void;
    getTimestamp?(scope: AnimatedRuntimeScope): number;
    resolveViewTag?(scope: AnimatedRuntimeScope, ref: unknown): number | null;
    measure?(scope: AnimatedRuntimeScope, viewTag: number): MeasuredDimensions | null;
    scrollTo?(scope: AnimatedRuntimeScope, command: NativeScrollCommand): void;
    dispatchCommand?(scope: AnimatedRuntimeScope, viewTag: number, command: string, args: readonly unknown[]): void;
    setNativeProps?(scope: AnimatedRuntimeScope, viewTag: number, props: Readonly<Record<string, unknown>>): void;
    getViewProp?<Value>(scope: AnimatedRuntimeScope, viewTag: number, propName: string): Value;
    registerSource?(scope: AnimatedRuntimeScope, registration: NativeSourceRegistration): void;
    unregisterSource?(scope: AnimatedRuntimeScope, id: number): void;
    getReducedMotion?(scope: AnimatedRuntimeScope): boolean;
    subscribeReducedMotion?(scope: AnimatedRuntimeScope, listener: (reduced: boolean) => void): () => void;
    setReducedMotionOverride?(scope: AnimatedRuntimeScope, value: ReduceMotionSetting | null): void;
    configureLayoutAnimations?(scope: AnimatedRuntimeScope, enabled: boolean): void;
    createWorkletRuntime?(scope: AnimatedRuntimeScope, name: string, initializer?: SerializedWorklet): WorkletRuntimeHandle;
    releaseWorkletRuntime?(scope: AnimatedRuntimeScope, runtime: WorkletRuntimeHandle): void;
    scheduleOnRuntime?(
        scope: AnimatedRuntimeScope,
        runtime: WorkletRuntimeHandle,
        worklet: SerializedWorklet,
        args: readonly unknown[],
    ): void;
    disposeRuntimeScope?(scope: AnimatedRuntimeScope): void;
}

export interface WorkletGlobalsManifest {
    readonly version: 2;
    readonly exports: readonly string[];
}

export enum SensorType {
    ACCELEROMETER = 1,
    GYROSCOPE = 2,
    GRAVITY = 3,
    MAGNETIC_FIELD = 4,
    ROTATION = 5,
    USER_ACCELERATION = 6,
}

export enum IOSReferenceFrame {
    XArbitraryZVertical = 1,
    XArbitraryCorrectedZVertical = 2,
    XMagneticNorthZVertical = 4,
    XTrueNorthZVertical = 8,
    Auto = 0,
}

export interface SensorConfig {
    interval?: number | "auto";
    adjustToInterfaceOrientation?: boolean;
    iosReferenceFrame?: IOSReferenceFrame;
}

export interface SensorValue3D {
    x: number;
    y: number;
    z: number;
    interfaceOrientation: number;
}

export interface SensorValueRotation extends SensorValue3D {
    qw: number;
    qx: number;
    qy: number;
    qz: number;
    yaw: number;
    pitch: number;
    roll: number;
}

export type SensorValue = SensorValue3D | SensorValueRotation;

export interface AnimatedSensor<Value extends SensorValue = SensorValue> {
    readonly sensor: SharedValue<Value>;
    readonly isAvailable: boolean;
    readonly config: SensorConfig;
    unregister(): void;
}

export enum KeyboardState {
    UNKNOWN = 0,
    OPENING = 1,
    OPEN = 2,
    CLOSING = 3,
    CLOSED = 4,
}

export interface AnimatedKeyboardOptions {
    isStatusBarTranslucentAndroid?: boolean;
    isNavigationBarTranslucentAndroid?: boolean;
}

export interface AnimatedKeyboardInfo {
    readonly height: SharedValue<number>;
    readonly state: SharedValue<KeyboardState>;
}

export interface PlaybackClockOptions {
    unit?: "ms" | "seconds";
    offset?: number;
}

export interface LayoutAnimation {
    readonly __spotifyPlusLayoutAnimation: true;
    readonly name: string;
    readonly config: Readonly<Record<string, unknown>>;
}

export type SerializedLayoutAnimation = LayoutAnimation;

export interface AnimatedHostComponents {
    readonly View?: React.ComponentType<any>;
    readonly Text?: React.ComponentType<any>;
    readonly Image?: React.ComponentType<any>;
    readonly ScriptView?: React.ComponentType<any>;
    readonly RenderView?: React.ComponentType<any>;
    readonly CanvasView?: React.ComponentType<any>;
    readonly ScrollView?: React.ComponentType<any>;
    readonly FlatList?: React.ComponentType<any>;
}
