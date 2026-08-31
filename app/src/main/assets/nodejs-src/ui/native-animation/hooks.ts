import React from "react";
import { isAnimation } from "./animations";
import { createGestureDetector, Gesture, type GestureDetectorComponent } from "./gesture";
import { useLayoutAnimationBoundary } from "./layout";
import { AnimatedRuntime, DerivedValueView, MutableValue } from "./runtime";
import {
    ANIMATED_PAYLOAD_KEY,
    SHARED_VALUE_KEY,
    KeyboardState,
    ReduceMotion,
    SensorType,
    type AnimatedComponentProps,
    type AnimatedEventHandler,
    type AnimatedHostComponents,
    type AnimatedKeyboardInfo,
    type AnimatedKeyboardOptions,
    type AnimatedPayloadKind,
    type AnimatedPayloadMarker,
    type AnimatedPropAdapter,
    type AnimatedPropsPayload,
    type AnimatedRef,
    type AnimatedSensor,
    type AnimatedStyle,
    type AnimatedStylePayload,
    type DependencyList,
    type DerivedValue,
    type FrameCallback,
    type FrameInfo,
    type LayoutAnimation,
    type NativeAnimationAdapter,
    type NativeWorkletRegistration,
    type PlaybackClockOptions,
    type ReduceMotionSetting,
    type SensorConfig,
    type SensorValue,
    type SensorValue3D,
    type SensorValueRotation,
    type SharedValue,
    type WorkletFunctionLike,
    type WorkletRuntimeHandle,
} from "./types";
import {
    UnsupportedPlatformError,
    createInternalWorklet,
    serializeWorklet,
    validateWorklet,
} from "./worklets";

const useAnimatedBindingEffect = React.useLayoutEffect ?? React.useEffect;

function marker(runtime: AnimatedRuntime, kind: AnimatedPayloadKind, id: number): AnimatedPayloadMarker {
    return {
        version: 2,
        kind,
        scriptId: runtime.scope.scriptId,
        generation: runtime.scope.generation,
        runtimeId: runtime.runtimeId,
        id,
    };
}

function getPayloadMarker(value: unknown): AnimatedPayloadMarker | null {
    if ((typeof value !== "object" && typeof value !== "function") || value === null) {
        return null;
    }
    const candidate = (value as Record<string, unknown>)[ANIMATED_PAYLOAD_KEY];
    if (!candidate || typeof candidate !== "object") {
        return null;
    }
    const payload = candidate as Partial<AnimatedPayloadMarker>;
    return payload.version === 2
        && (payload.kind === "style" || payload.kind === "props" || payload.kind === "event")
        && typeof payload.scriptId === "string"
        && (typeof payload.generation === "string" || typeof payload.generation === "number")
        && typeof payload.runtimeId === "string"
        && typeof payload.id === "number"
        ? payload as AnimatedPayloadMarker
        : null;
}

export function isAnimatedStylePayload(value: unknown): value is AnimatedStylePayload {
    return getPayloadMarker(value)?.kind === "style";
}

export function isAnimatedPropsPayload(value: unknown): value is AnimatedPropsPayload {
    return getPayloadMarker(value)?.kind === "props";
}

export function isAnimatedEventHandler(value: unknown): value is AnimatedEventHandler {
    return getPayloadMarker(value)?.kind === "event";
}

export function isAnimatedNodeLike(value: unknown): value is SharedValue | DerivedValue {
    if ((typeof value !== "object" && typeof value !== "function") || value === null) {
        return false;
    }
    const marker = (value as Record<string, unknown>)[SHARED_VALUE_KEY];
    if (!marker || typeof marker !== "object") {
        return false;
    }
    const candidate = marker as Record<string, unknown>;
    return candidate.version === 2
        && typeof candidate.scriptId === "string"
        && (typeof candidate.generation === "string" || typeof candidate.generation === "number")
        && typeof candidate.runtimeId === "string"
        && typeof candidate.id === "number";
}

function useStableId(runtime: AnimatedRuntime) {
    const id = React.useRef<number | null>(null);
    if (id.current === null) {
        id.current = runtime.allocateId();
    }
    return id.current;
}

function useMutableLifecycle<Value>(mutable: MutableValue<Value>) {
    const mounted = React.useRef(false);
    React.useEffect(() => {
        mounted.current = true;
        return () => {
            mounted.current = false;
            const release = () => {
                if (!mounted.current) {
                    mutable.dispose();
                }
            };
            if (typeof queueMicrotask === "function") {
                queueMicrotask(release);
            } else {
                Promise.resolve().then(release);
            }
        };
    }, [mutable]);
}

function useRuntimeMutable<Value>(runtime: AnimatedRuntime, initial: Value) {
    const ref = React.useRef<MutableValue<Value> | null>(null);
    if (ref.current === null) {
        ref.current = runtime.makeMutable(initial);
    }
    useMutableLifecycle(ref.current);
    return ref.current;
}

function initialWorkletValue<Value>(worklet: () => Value, apiName: string) {
    try {
        return worklet();
    } catch (error) {
        const reason = error instanceof Error ? error.message : String(error);
        throw new Error(`${apiName} could not evaluate its initial value on the script thread: ${reason}`);
    }
}

function initialDerivedValue<Value>(worklet: () => Value, apiName: string): Value {
    const initialValue = initialWorkletValue(worklet, apiName);
    if (isAnimation(initialValue)) {
        return initialValue.toValue as Value;
    }
    return initialValue;
}

function useWorkletRegistration(
    runtime: AnimatedRuntime,
    registration: NativeWorkletRegistration,
    dependencies: React.DependencyList,
) {
    React.useEffect(() => {
        runtime.registerWorklet(registration);
        return () => runtime.unregisterWorklet(registration.id);
    }, [runtime, registration.id, ...dependencies]);
}

export function makeMutableForRuntime<Value>(runtime: AnimatedRuntime, initial: Value): SharedValue<Value> {
    return runtime.makeMutable(initial);
}

export function useSharedValueForRuntime<Value>(runtime: AnimatedRuntime, initial: Value): SharedValue<Value> {
    return useRuntimeMutable(runtime, initial);
}

export function useDerivedValueForRuntime<Value>(
    runtime: AnimatedRuntime,
    updater: WorkletFunctionLike<() => Value>,
    dependencies: DependencyList = [],
): DerivedValue<Value> {
    const worklet = serializeWorklet(updater, "useDerivedValue");
    const mutable = useRuntimeMutable(runtime, initialDerivedValue(updater, "useDerivedValue"));
    const registrationId = useStableId(runtime);
    useWorkletRegistration(runtime, {
        id: registrationId,
        kind: "derived",
        worklets: { updater: worklet },
        targetMutableIds: [mutable.id],
        dependencies,
    }, [updater, ...dependencies]);
    return React.useMemo(() => new DerivedValueView(mutable), [mutable]);
}

export function useAnimatedStyleForRuntime<Value extends object>(
    runtime: AnimatedRuntime,
    updater: WorkletFunctionLike<() => AnimatedStyle<Value>>,
    dependencies: DependencyList = [],
): AnimatedStylePayload<Value> {
    const worklet = serializeWorklet(updater, "useAnimatedStyle");
    const id = useStableId(runtime);
    const initialValue = initialWorkletValue(updater, "useAnimatedStyle");
    useWorkletRegistration(runtime, {
        id,
        kind: "style",
        worklets: { updater: worklet },
        dependencies,
    }, [updater, ...dependencies]);
    return React.useMemo(() => ({
        [ANIMATED_PAYLOAD_KEY]: marker(runtime, "style", id) as AnimatedPayloadMarker & { kind: "style" },
        initialValue,
    }), [runtime, id, initialValue]);
}

export function useAnimatedPropsForRuntime<Value extends object>(
    runtime: AnimatedRuntime,
    updater: WorkletFunctionLike<() => Value>,
    dependencies: DependencyList = [],
    adapters: readonly AnimatedPropAdapter<any>[] = [],
): AnimatedPropsPayload<Value> {
    const effectiveUpdater = adapters.length === 0
        ? updater
        : createInternalWorklet(
            () => {
                const props = updater();
                for (const adapter of adapters) {
                    adapter(props);
                }
                return props;
            },
            { adapters, updater },
            "spotifyplus:useAnimatedProps:adapters",
        );
    const worklet = serializeWorklet(effectiveUpdater, "useAnimatedProps");
    const id = useStableId(runtime);
    const initialValue = initialWorkletValue(effectiveUpdater, "useAnimatedProps");
    useWorkletRegistration(runtime, {
        id,
        kind: "props",
        worklets: { updater: worklet },
        dependencies,
    }, [updater, ...adapters, ...dependencies]);
    return React.useMemo(() => ({
        [ANIMATED_PAYLOAD_KEY]: marker(runtime, "props", id) as AnimatedPayloadMarker & { kind: "props" },
        initialValue,
    }), [runtime, id, initialValue]);
}

export function createAnimatedPropAdapter<Props extends Record<string, unknown>>(
    adapter: WorkletFunctionLike<(props: Props) => void>,
    nativeProps: readonly string[] = [],
): AnimatedPropAdapter<Props> {
    validateWorklet(adapter, "createAnimatedPropAdapter");
    Object.defineProperty(adapter, "nativeProps", {
        value: Object.freeze([...nativeProps]),
        enumerable: true,
    });
    return adapter as AnimatedPropAdapter<Props>;
}

export function useAnimatedReactionForRuntime<Prepared>(
    runtime: AnimatedRuntime,
    prepare: WorkletFunctionLike<() => Prepared>,
    react: WorkletFunctionLike<(prepared: Prepared, previous: Prepared | null) => void>,
    dependencies: DependencyList = [],
) {
    const id = useStableId(runtime);
    const registration: NativeWorkletRegistration = {
        id,
        kind: "reaction",
        worklets: {
            prepare: serializeWorklet(prepare, "useAnimatedReaction prepare"),
            react: serializeWorklet(react, "useAnimatedReaction react"),
        },
        dependencies,
    };
    useWorkletRegistration(runtime, registration, [prepare, react, ...dependencies]);
}

export function useFrameCallbackForRuntime(
    runtime: AnimatedRuntime,
    callback: WorkletFunctionLike<(frameInfo: FrameInfo) => void>,
    autostart = true,
): FrameCallback {
    if (runtime.adapter.capabilities?.frameCallbacks !== true) {
        throw new UnsupportedPlatformError("useFrameCallback", runtime.adapter.name);
    }
    const id = useStableId(runtime);
    const active = React.useRef(autostart);
    useWorkletRegistration(runtime, {
        id,
        kind: "frame",
        worklets: { callback: serializeWorklet(callback, "useFrameCallback") },
        options: { active: autostart },
    }, [callback, autostart]);
    return React.useMemo(() => ({
        setActive(next: boolean) {
            active.current = next;
            runtime.setWorkletActive(id, next);
        },
        get isActive() {
            return active.current;
        },
    }), [runtime, id]);
}

export function useFrameTimestampForRuntime(runtime: AnimatedRuntime) {
    const timestamp = useRuntimeMutable(runtime, runtime.getTimestamp());
    const callback = React.useMemo(() => createInternalWorklet(
        (frame: FrameInfo) => {
            timestamp.value = frame.timestamp;
        },
        { timestamp },
        "spotifyplus:useFrameTimestamp",
    ), [timestamp]);
    useFrameCallbackForRuntime(runtime, callback);
    return timestamp;
}

export function useTimestampForRuntime(runtime: AnimatedRuntime, isActive = true) {
    const timestamp = useRuntimeMutable(runtime, 0);
    const state = React.useMemo<{ start: number | null }>(() => ({ start: null }), []);
    if (!isActive) {
        state.start = null;
    }
    const callback = React.useMemo(() => createInternalWorklet(
        (frame: FrameInfo) => {
            if (state.start === null) {
                state.start = frame.timestamp;
            }
            timestamp.value = frame.timestamp - state.start;
        },
        { state, timestamp },
        "spotifyplus:useTimestamp",
    ), [state, timestamp]);
    useFrameCallbackForRuntime(runtime, callback, isActive);
    return timestamp;
}

export function useAnimatedRefForRuntime<Component>(runtime: AnimatedRuntime): AnimatedRef<Component> {
    const id = useStableId(runtime);
    const ref = React.useRef<AnimatedRef<Component> | null>(null);
    if (ref.current === null) {
        const animatedRef = function (component?: Component | null) {
            if (arguments.length > 0) {
                animatedRef.current = component ?? null;
            }
            return runtime.resolveViewTag(animatedRef.current);
        } as AnimatedRef<Component>;
        animatedRef.current = null;
        Object.defineProperty(animatedRef, "__animatedRefId", { value: id, enumerable: true });
        animatedRef.getTag = () => runtime.resolveViewTag(animatedRef.current);
        ref.current = animatedRef;
    }
    return ref.current;
}

export function useEventForRuntime<Event>(
    runtime: AnimatedRuntime,
    handler: WorkletFunctionLike<(event: Event) => void>,
    eventNames: readonly string[] = [],
    rebuild = false,
): AnimatedEventHandler<Event> {
    const id = useStableId(runtime);
    const worklet = serializeWorklet(handler, "useEvent");
    useWorkletRegistration(runtime, {
        id,
        kind: "event",
        worklets: { handler: worklet },
        eventNames,
        options: { rebuild },
    }, [handler, rebuild, ...eventNames]);

    return React.useMemo(() => {
        const eventHandler = ((event: Event) => handler(event)) as AnimatedEventHandler<Event>;
        Object.defineProperty(eventHandler, ANIMATED_PAYLOAD_KEY, {
            value: marker(runtime, "event", id),
            enumerable: true,
        });
        Object.defineProperty(eventHandler, "eventNames", {
            value: Object.freeze([...eventNames]),
            enumerable: true,
        });
        return eventHandler;
    }, [runtime, id, handler, ...eventNames]);
}

export interface AnimatedScrollEvent {
    eventName?: string;
    contentOffset?: { x: number; y: number };
    contentSize?: { width: number; height: number };
    layoutMeasurement?: { width: number; height: number };
    velocity?: { x: number; y: number };
    [key: string]: unknown;
}

export interface AnimatedScrollHandlers<Context extends Record<string, unknown> = Record<string, unknown>> {
    onScroll?: WorkletFunctionLike<(event: AnimatedScrollEvent, context: Context) => void>;
    onBeginDrag?: WorkletFunctionLike<(event: AnimatedScrollEvent, context: Context) => void>;
    onEndDrag?: WorkletFunctionLike<(event: AnimatedScrollEvent, context: Context) => void>;
    onMomentumBegin?: WorkletFunctionLike<(event: AnimatedScrollEvent, context: Context) => void>;
    onMomentumEnd?: WorkletFunctionLike<(event: AnimatedScrollEvent, context: Context) => void>;
}

export function useAnimatedScrollHandlerForRuntime<Context extends Record<string, unknown>>(
    runtime: AnimatedRuntime,
    handlers: AnimatedScrollHandlers<Context> | WorkletFunctionLike<(event: AnimatedScrollEvent) => void>,
    dependencies: DependencyList = [],
): AnimatedEventHandler<AnimatedScrollEvent> {
    const id = useStableId(runtime);
    const context = React.useRef<Context>({} as Context);
    const normalized: AnimatedScrollHandlers<Context> = typeof handlers === "function"
        ? { onScroll: handlers }
        : handlers;
    const worklets: Record<string, ReturnType<typeof serializeWorklet>> = {};
    for (const [name, handler] of Object.entries(normalized)) {
        if (handler) {
            worklets[name] = serializeWorklet(handler, `useAnimatedScrollHandler.${name}`);
        }
    }
    const eventNames = Object.keys(worklets);
    useWorkletRegistration(runtime, {
        id,
        kind: "event",
        worklets,
        eventNames,
        dependencies,
        options: { eventType: "scroll" },
    }, [handlers, ...dependencies]);

    return React.useMemo(() => {
        const eventHandler = ((event: AnimatedScrollEvent) => {
            const eventName = event.eventName ?? "onScroll";
            const handler = normalized[eventName as keyof AnimatedScrollHandlers<Context>]
                ?? normalized.onScroll;
            handler?.(event, context.current);
        }) as AnimatedEventHandler<AnimatedScrollEvent>;
        Object.defineProperty(eventHandler, ANIMATED_PAYLOAD_KEY, {
            value: marker(runtime, "event", id),
            enumerable: true,
        });
        Object.defineProperty(eventHandler, "eventNames", {
            value: Object.freeze(eventNames),
            enumerable: true,
        });
        return eventHandler;
    }, [runtime, id, handlers]);
}

export function useComposedEventHandlerForRuntime<Event>(
    runtime: AnimatedRuntime,
    handlers: readonly (AnimatedEventHandler<Event> | null | undefined)[],
): AnimatedEventHandler<Event> {
    const id = useStableId(runtime);
    const activeHandlers = handlers.filter((handler): handler is AnimatedEventHandler<Event> => !!handler);
    const eventNames = Array.from(new Set(activeHandlers.flatMap(handler => [...handler.eventNames])));
    const composedIds = activeHandlers.map(handler => handler[ANIMATED_PAYLOAD_KEY].id);
    useWorkletRegistration(runtime, {
        id,
        kind: "event",
        worklets: {},
        eventNames,
        options: { composedEventIds: composedIds },
    }, [...composedIds, ...eventNames]);
    return React.useMemo(() => {
        const eventHandler = ((event: Event) => {
            for (const handler of activeHandlers) {
                handler(event);
            }
        }) as AnimatedEventHandler<Event>;
        Object.defineProperty(eventHandler, ANIMATED_PAYLOAD_KEY, {
            value: marker(runtime, "event", id),
            enumerable: true,
        });
        Object.defineProperty(eventHandler, "eventNames", {
            value: Object.freeze(eventNames),
            enumerable: true,
        });
        return eventHandler;
    }, [runtime, id, ...activeHandlers]);
}

export function useHandler<Context extends Record<string, unknown>>(
    _handlers: Readonly<Record<string, unknown>>,
    dependencies: DependencyList = [],
) {
    const context = React.useRef<Context>({} as Context);
    const previousDependencies = React.useRef<DependencyList | null>(null);
    const doDependenciesDiffer = previousDependencies.current === null
        || dependencies.length !== previousDependencies.current.length
        || dependencies.some((dependency, index) => !Object.is(dependency, previousDependencies.current?.[index]));
    previousDependencies.current = dependencies;
    return { context: context.current, doDependenciesDiffer, useWeb: false };
}

export function useScrollOffsetForRuntime(
    runtime: AnimatedRuntime,
    animatedRef: AnimatedRef<unknown>,
    providedOffset?: SharedValue<number>,
) {
    if (runtime.adapter.capabilities?.scroll !== true || !runtime.adapter.registerSource) {
        throw new UnsupportedPlatformError("useScrollOffset", runtime.adapter.name);
    }
    const fallbackOffset = useRuntimeMutable(runtime, 0);
    const offset = providedOffset ?? fallbackOffset;
    const sourceId = useStableId(runtime);
    React.useLayoutEffect(() => {
        const viewTag = runtime.resolveViewTag(animatedRef);
        if (viewTag === null) {
            throw new Error("useScrollOffset received an animated ref that is not attached to a scroll view.");
        }
        const marker = offset[SHARED_VALUE_KEY];
        if (marker.runtimeId !== runtime.runtimeId) {
            throw new Error("useScrollOffset cannot update a SharedValue from another animated runtime.");
        }
        runtime.registerSource({
            id: sourceId,
            kind: "scrollOffset",
            targetMutableIds: { offset: marker.id },
            config: { viewTag },
        });
        return () => runtime.unregisterSource(sourceId);
    }, [runtime, animatedRef, offset, sourceId]);
    return offset;
}

function sensorInitialValue(sensorType: SensorType): SensorValue {
    const common: SensorValue3D = { x: 0, y: 0, z: 0, interfaceOrientation: 0 };
    if (sensorType !== SensorType.ROTATION) {
        return common;
    }
    const rotation: SensorValueRotation = {
        ...common,
        qw: 1,
        qx: 0,
        qy: 0,
        qz: 0,
        yaw: 0,
        pitch: 0,
        roll: 0,
    };
    return rotation;
}

export function useAnimatedSensorForRuntime(
    runtime: AnimatedRuntime,
    sensorType: SensorType,
    config: SensorConfig = {},
): AnimatedSensor {
    if (runtime.adapter.capabilities?.sensors !== true || !runtime.adapter.registerSource) {
        throw new UnsupportedPlatformError("useAnimatedSensor", runtime.adapter.name);
    }
    const sensor = useRuntimeMutable(runtime, sensorInitialValue(sensorType));
    const sourceId = useStableId(runtime);
    const registered = React.useRef(true);
    React.useEffect(() => {
        runtime.registerSource({
            id: sourceId,
            kind: "sensor",
            targetMutableIds: { sensor: sensor.id },
            config: { sensorType, ...config },
        });
        registered.current = true;
        return () => {
            registered.current = false;
            runtime.unregisterSource(sourceId);
        };
    }, [runtime, sourceId, sensorType, config.interval, config.adjustToInterfaceOrientation, config.iosReferenceFrame]);
    return React.useMemo(() => ({
        sensor,
        isAvailable: true,
        config,
        unregister() {
            if (registered.current) {
                registered.current = false;
                runtime.unregisterSource(sourceId);
            }
        },
    }), [runtime, sensor, sourceId, config]);
}

export function useAnimatedKeyboardForRuntime(
    runtime: AnimatedRuntime,
    options: AnimatedKeyboardOptions = {},
): AnimatedKeyboardInfo {
    if (runtime.adapter.capabilities?.keyboard !== true || !runtime.adapter.registerSource) {
        throw new UnsupportedPlatformError("useAnimatedKeyboard", runtime.adapter.name);
    }
    const height = useRuntimeMutable(runtime, 0);
    const state = useRuntimeMutable(runtime, KeyboardState.CLOSED);
    const sourceId = useStableId(runtime);
    React.useEffect(() => {
        runtime.registerSource({
            id: sourceId,
            kind: "keyboard",
            targetMutableIds: { height: height.id, state: state.id },
            config: { ...options },
        });
        return () => runtime.unregisterSource(sourceId);
    }, [
        runtime,
        sourceId,
        options.isStatusBarTranslucentAndroid,
        options.isNavigationBarTranslucentAndroid,
    ]);
    return React.useMemo(() => ({ height, state }), [height, state]);
}

export function usePlaybackClockForRuntime(
    runtime: AnimatedRuntime,
    options: PlaybackClockOptions = {},
) {
    if (runtime.adapter.capabilities?.playbackClock !== true || !runtime.adapter.registerSource) {
        throw new UnsupportedPlatformError("usePlaybackClock", runtime.adapter.name);
    }
    const value = useRuntimeMutable(runtime, options.offset ?? 0);
    const sourceId = useStableId(runtime);
    React.useEffect(() => {
        runtime.registerSource({
            id: sourceId,
            kind: "playbackClock",
            targetMutableIds: { value: value.id },
            config: {
                unit: options.unit ?? "ms",
                offset: options.offset ?? 0,
            },
        });
        return () => runtime.unregisterSource(sourceId);
    }, [runtime, sourceId, options.unit, options.offset]);
    return value;
}

export function useReducedMotionForRuntime(runtime: AnimatedRuntime) {
    if (!runtime.adapter.getReducedMotion) {
        throw new UnsupportedPlatformError("useReducedMotion", runtime.adapter.name);
    }
    const [reduced, setReduced] = React.useState(() => runtime.adapter.getReducedMotion!(runtime.scope));
    React.useEffect(() => runtime.adapter.subscribeReducedMotion?.(runtime.scope, setReduced), [runtime]);
    return reduced;
}

export interface ReducedMotionConfigProps {
    mode?: ReduceMotionSetting;
    children?: React.ReactNode;
}

export function createReducedMotionConfig(runtime: AnimatedRuntime) {
    return function ReducedMotionConfig({ mode = ReduceMotion.System, children }: ReducedMotionConfigProps) {
        if (!runtime.adapter.setReducedMotionOverride) {
            throw new UnsupportedPlatformError("ReducedMotionConfig", runtime.adapter.name);
        }
        React.useEffect(() => {
            runtime.adapter.setReducedMotionOverride!(runtime.scope, mode);
            return () => runtime.adapter.setReducedMotionOverride!(runtime.scope, null);
        }, [mode]);
        return React.createElement(React.Fragment, null, children);
    };
}

interface CollectedStyle {
    regularStyle: unknown;
    styleIds: number[];
}

function collectStyle(style: unknown, runtime: AnimatedRuntime): CollectedStyle {
    if (isAnimatedStylePayload(style)) {
        const payload = style[ANIMATED_PAYLOAD_KEY];
        if (payload.runtimeId !== runtime.runtimeId) {
            throw new Error("Animated styles cannot be shared across script-bound animated runtimes.");
        }
        return { regularStyle: style.initialValue, styleIds: [payload.id] };
    }
    if (!Array.isArray(style)) {
        return { regularStyle: style, styleIds: [] };
    }

    const regularStyle: unknown[] = [];
    const styleIds: number[] = [];
    for (const entry of style) {
        const collected = collectStyle(entry, runtime);
        if (collected.regularStyle !== undefined && collected.regularStyle !== null && collected.regularStyle !== false) {
            regularStyle.push(collected.regularStyle);
        }
        styleIds.push(...collected.styleIds);
    }
    return { regularStyle, styleIds };
}

function collectEventIds(props: Readonly<Record<string, unknown>>, runtime: AnimatedRuntime) {
    const eventIds: Record<string, number> = {};
    for (const [name, value] of Object.entries(props)) {
        if (!isAnimatedEventHandler(value)) {
            continue;
        }
        const payload = value[ANIMATED_PAYLOAD_KEY];
        if (payload.runtimeId !== runtime.runtimeId) {
            throw new Error("Animated event handlers cannot be shared across script-bound animated runtimes.");
        }
        eventIds[name] = payload.id;
    }
    return eventIds;
}

export function createAnimatedComponentForRuntime<Props extends object>(
    runtime: AnimatedRuntime,
    Component: React.ComponentType<Props>,
): React.ComponentType<AnimatedComponentProps<Props>> {
    const AnimatedComponent = React.forwardRef<unknown, AnimatedComponentProps<Props>>((props, forwardedRef) => {
        const hostRef = React.useRef<unknown>(null);
        const layoutBoundary = useLayoutAnimationBoundary();
        const {
            animatedProps,
            entering,
            exiting,
            layout,
            style,
            ...rest
        } = props as AnimatedComponentProps<Props> & {
            style?: unknown;
        };
        const effectiveEntering = layoutBoundary.skipEntering ? undefined : entering;
        const effectiveExiting = layoutBoundary.skipExiting ? undefined : exiting;
        const collectedStyle = collectStyle(style, runtime);
        const propsId = animatedProps?.[ANIMATED_PAYLOAD_KEY].id;
        if (animatedProps && animatedProps[ANIMATED_PAYLOAD_KEY].runtimeId !== runtime.runtimeId) {
            throw new Error("Animated props cannot be shared across script-bound animated runtimes.");
        }
        const initialAnimatedProps = animatedProps?.initialValue ?? {};
        const hostProps = {
            ...rest,
            ...initialAnimatedProps,
            style: collectedStyle.regularStyle,
            ref: hostRef,
        } as unknown as Props;
        const eventIds = collectEventIds(rest as Record<string, unknown>, runtime);
        const bindingKey = JSON.stringify({
            styleIds: collectedStyle.styleIds,
            propsId,
            eventIds,
            entering: effectiveEntering,
            exiting: effectiveExiting,
            layout,
        });

        React.useImperativeHandle(forwardedRef, () => hostRef.current, []);
        useAnimatedBindingEffect(() => {
            const hasBindings = collectedStyle.styleIds.length > 0
                || propsId !== undefined
                || Object.keys(eventIds).length > 0
                || effectiveEntering !== undefined
                || effectiveExiting !== undefined
                || layout !== undefined;
            if (!hasBindings) {
                return;
            }
            const viewTag = runtime.resolveViewTag(hostRef.current);
            if (viewTag === null) {
                throw new Error("Animated component did not expose a native view tag.");
            }
            runtime.bindView({
                viewTag,
                styleIds: collectedStyle.styleIds,
                propsIds: propsId === undefined ? [] : [propsId],
                eventIds,
                entering: effectiveEntering,
                exiting: effectiveExiting,
                layout,
            });
            return () => runtime.unbindView(viewTag);
        }, [runtime, bindingKey]);

        return React.createElement(Component, hostProps);
    });
    AnimatedComponent.displayName = `Animated.${Component.displayName ?? Component.name ?? "Component"}`;
    return AnimatedComponent as unknown as React.ComponentType<AnimatedComponentProps<Props>>;
}

function unsupportedHost(name: string, adapterName: string): React.ComponentType<any> {
    const Component = function UnsupportedAnimatedHost() {
        throw new UnsupportedPlatformError(`Animated.${name}`, adapterName);
    };
    Component.displayName = `Animated.${name}`;
    return Component;
}

export function installAnimatedComponents(
    runtime: AnimatedRuntime,
    hosts: AnimatedHostComponents = {},
) {
    const install = (name: keyof AnimatedHostComponents) => {
        const Host = hosts[name];
        return Host
            ? createAnimatedComponentForRuntime(runtime, Host)
            : unsupportedHost(name, runtime.adapter.name);
    };
    return {
        View: install("View"),
        Text: install("Text"),
        Image: install("Image"),
        ScriptView: install("ScriptView"),
        RenderView: install("RenderView"),
        CanvasView: install("CanvasView"),
        ScrollView: install("ScrollView"),
        FlatList: install("FlatList"),
    };
}

export function createRuntimeBindings(runtime: AnimatedRuntime, hosts?: AnimatedHostComponents) {
    const components = installAnimatedComponents(runtime, hosts);
    const GestureDetector = createGestureDetector(
        runtime.adapter,
        runtime.scope,
        () => runtime.allocateId(),
    );

    return {
        runtime,
        dispose: () => runtime.dispose(),
        makeMutable: <Value>(initial: Value) => makeMutableForRuntime(runtime, initial),
        useSharedValue: <Value = number>(initial: Value) => useSharedValueForRuntime(runtime, initial),
        useDerivedValue: <Value>(updater: WorkletFunctionLike<() => Value>, dependencies?: DependencyList) => (
            useDerivedValueForRuntime(runtime, updater, dependencies)
        ),
        useAnimatedStyle: <Value extends object>(
            updater: WorkletFunctionLike<() => AnimatedStyle<Value>>,
            dependencies?: DependencyList,
        ) => useAnimatedStyleForRuntime(runtime, updater, dependencies),
        useAnimatedProps: <Value extends object>(
            updater: WorkletFunctionLike<() => Value>,
            dependencies?: DependencyList,
            adapters?: readonly AnimatedPropAdapter<any>[],
        ) => useAnimatedPropsForRuntime(runtime, updater, dependencies, adapters),
        useAnimatedReaction: <Prepared>(
            prepare: WorkletFunctionLike<() => Prepared>,
            react: WorkletFunctionLike<(prepared: Prepared, previous: Prepared | null) => void>,
            dependencies?: DependencyList,
        ) => useAnimatedReactionForRuntime(runtime, prepare, react, dependencies),
        useFrameCallback: (
            callback: WorkletFunctionLike<(frameInfo: FrameInfo) => void>,
            autostart?: boolean,
        ) => useFrameCallbackForRuntime(runtime, callback, autostart),
        useFrameTimestamp: () => useFrameTimestampForRuntime(runtime),
        useTimestamp: (isActive = true) => useTimestampForRuntime(runtime, isActive),
        useAnimatedRef: <Component = unknown>() => useAnimatedRefForRuntime<Component>(runtime),
        useEvent: <Event>(
            handler: WorkletFunctionLike<(event: Event) => void>,
            eventNames?: readonly string[],
            rebuild?: boolean,
        ) => useEventForRuntime(runtime, handler, eventNames, rebuild),
        useAnimatedScrollHandler: <Context extends Record<string, unknown> = Record<string, unknown>>(
            handlers: AnimatedScrollHandlers<Context> | WorkletFunctionLike<(event: AnimatedScrollEvent) => void>,
            dependencies?: DependencyList,
        ) => useAnimatedScrollHandlerForRuntime(runtime, handlers, dependencies),
        useComposedEventHandler: <Event>(handlers: readonly (AnimatedEventHandler<Event> | null | undefined)[]) => (
            useComposedEventHandlerForRuntime(runtime, handlers)
        ),
        useScrollOffset: (ref: AnimatedRef<unknown>, providedOffset?: SharedValue<number>) => (
            useScrollOffsetForRuntime(runtime, ref, providedOffset)
        ),
        useScrollViewOffset: (ref: AnimatedRef<unknown>, providedOffset?: SharedValue<number>) => (
            useScrollOffsetForRuntime(runtime, ref, providedOffset)
        ),
        useAnimatedSensor: (sensorType: SensorType, config?: SensorConfig) => (
            useAnimatedSensorForRuntime(runtime, sensorType, config)
        ),
        useAnimatedKeyboard: (options?: AnimatedKeyboardOptions) => useAnimatedKeyboardForRuntime(runtime, options),
        usePlaybackClock: (options?: PlaybackClockOptions) => usePlaybackClockForRuntime(runtime, options),
        useReducedMotion: () => useReducedMotionForRuntime(runtime),
        useWorkletCallback: <Callback extends (...args: any[]) => any>(
            callback: WorkletFunctionLike<Callback>,
            dependencies: DependencyList = [],
        ) => {
            validateWorklet(callback, "useWorkletCallback");
            return React.useCallback(callback, [callback, ...dependencies]);
        },
        ReducedMotionConfig: createReducedMotionConfig(runtime),
        createAnimatedComponent: <Props extends object>(Component: React.ComponentType<Props>) => (
            createAnimatedComponentForRuntime(runtime, Component)
        ),
        cancelAnimation: <Value>(sharedValue: SharedValue<Value>) => runtime.cancelAnimation(sharedValue),
        scheduleOnUI: <Args extends unknown[]>(
            worklet: WorkletFunctionLike<(...args: Args) => unknown>,
            ...args: Args
        ) => runtime.scheduleOnUI(worklet, args),
        runOnUI: <Args extends unknown[]>(worklet: WorkletFunctionLike<(...args: Args) => unknown>) => (
            (...args: Args) => runtime.scheduleOnUI(worklet, args)
        ),
        runOnUIAsync: <Args extends unknown[], Result>(
            worklet: WorkletFunctionLike<(...args: Args) => Result>,
        ) => (...args: Args) => new Promise<Result>((resolve, reject) => {
            const complete = (succeeded: boolean, value: Result | unknown) => {
                if (succeeded) {
                    resolve(value as Result);
                } else {
                    reject(value);
                }
            };
            Object.defineProperty(complete, "__spotifyPlusOneShotRNCallback", {
                value: true,
                enumerable: false,
            });
            const task = createInternalWorklet(
                () => {
                    try {
                        complete(true, worklet(...args));
                    } catch (error) {
                        const failure = error instanceof Error
                            ? { name: error.name, message: error.message, stack: error.stack }
                            : { name: "Error", message: String(error) };
                        complete(false, failure);
                    }
                },
                { args, complete, worklet },
                "spotifyplus:runOnUIAsync",
            );
            runtime.scheduleOnUI(task, []);
        }),
        executeOnUIRuntimeSync: <Args extends unknown[], Result>(
            worklet: WorkletFunctionLike<(...args: Args) => Result>,
        ) => (...args: Args) => runtime.executeOnUISync(worklet, args),
        runOnUISync: <Args extends unknown[], Result>(
            worklet: WorkletFunctionLike<(...args: Args) => Result>,
        ) => (...args: Args) => runtime.executeOnUISync(worklet, args),
        scheduleOnRN: <Args extends unknown[]>(fn: (...args: Args) => unknown, ...args: Args) => (
            runtime.scheduleOnRN(fn, args)
        ),
        runOnRNAsync: <Args extends unknown[], Result>(fn: (...args: Args) => Result, ...args: Args) => (
            new Promise<Result>((resolve, reject) => {
                runtime.scheduleOnRN(() => {
                    try {
                        resolve(fn(...args));
                    } catch (error) {
                        reject(error);
                    }
                }, []);
            })
        ),
        runOnJS: <Args extends unknown[]>(fn: (...args: Args) => unknown) => (
            (...args: Args) => runtime.scheduleOnRN(fn, args)
        ),
        createWorkletRuntime: (name: string, initializer?: WorkletFunctionLike<() => void>) => (
            runtime.createWorkletRuntime(name, initializer)
        ),
        runOnRuntime: <Args extends unknown[]>(
            workletRuntime: WorkletRuntimeHandle,
            worklet: WorkletFunctionLike<(...args: Args) => unknown>,
        ) => (...args: Args) => runtime.scheduleOnRuntime(workletRuntime, worklet, args),
        scheduleOnRuntime: <Args extends unknown[]>(
            workletRuntime: WorkletRuntimeHandle,
            worklet: WorkletFunctionLike<(...args: Args) => unknown>,
            ...args: Args
        ) => runtime.scheduleOnRuntime(workletRuntime, worklet, args),
        measure: (ref: unknown) => runtime.measure(ref),
        scrollTo: (ref: unknown, x: number, y: number, animated = false) => runtime.scrollTo(ref, x, y, animated),
        scrollToOffset: (ref: unknown, options: { offset: number; animated?: boolean }) => (
            runtime.scrollTo(ref, 0, options.offset, options.animated ?? false)
        ),
        dispatchCommand: (ref: unknown, command: string, args?: readonly unknown[]) => (
            runtime.dispatchCommand(ref, command, args)
        ),
        setNativeProps: (ref: unknown, props: Readonly<Record<string, unknown>>) => runtime.setNativeProps(ref, props),
        getViewProp: <Value>(ref: unknown, propName: string) => runtime.getViewProp<Value>(ref, propName),
        getRelativeCoords: (ref: unknown, absoluteX: number, absoluteY: number) => {
            const measured = runtime.measure(ref);
            return measured ? { x: absoluteX - measured.pageX, y: absoluteY - measured.pageY } : null;
        },
        enableLayoutAnimations: (enabled = true) => runtime.enableLayoutAnimations(enabled),
        getTimestamp: () => runtime.getTimestamp(),
        callMicrotasks: () => Promise.resolve(),
        isSharedValue: isAnimatedNodeLike,
        Gesture,
        GestureDetector: GestureDetector as GestureDetectorComponent,
        ...components,
    };
}
