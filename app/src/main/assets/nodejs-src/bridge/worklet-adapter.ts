import { Logger } from "../core/logger";
import {
    clearWorkletProps,
    dispatchViewCommand,
    getNodeSurfaceId,
    updateNodeProps,
} from "../ui/renderer";
import type {
    AnimatedRuntimeScope,
    NativeAnimationAdapter,
    NativeSourceRegistration,
    NativeViewBinding,
    NativeWorkletRegistration,
    SerializedWorklet,
    WorkletRuntimeHandle,
    WorkletGlobalsManifest,
} from "../ui/native-animation/types";
import { SHARED_VALUE_KEY, type SharedValueMarker } from "../ui/native-animation/types";
import { getWorkletMetadata } from "../ui/native-animation/worklets";
import { Bridge } from "./bridge";
import { hasObjectTag, isPlainObject } from "./shareable-types";

const WORKLET_GLOBAL_MODULE = "spotifyplus/react/reanimated";
const INTERNAL_SURFACE = "__spotifyplus_internal__";

type RegisteredMapper = {
    readonly mapperId: string;
    readonly workletId: string;
    readonly surfaceId: string;
    readonly nodeId: number;
    readonly priority: number;
    readonly runEveryFrame: boolean;
    active: boolean;
};

type RegistrationState = {
    readonly registration: NativeWorkletRegistration;
    readonly nativeWorkletIds: Set<string>;
    readonly mappers: Map<string, RegisteredMapper>;
    primaryWorkletId: string;
};

type ViewBindingState = {
    readonly binding: NativeViewBinding;
    readonly mapperIds: Set<string>;
    readonly layoutWorkletIds: Set<string>;
};

type SourceBindingState = {
    readonly sourceId: string;
    readonly sharedValueIds: string[];
};

type RnCallbackPayload = {
    scriptId?: string;
    generation?: string | number;
    functionId?: string;
    args?: unknown[];
};

type ShareableMarker = {
    readonly __spotifyPlusShareable: string;
    readonly [key: string]: unknown;
};

type ShareableSerializationOptions = {
    readonly inlineWorklets?: boolean;
    readonly rnCallbackIds?: Set<string>;
};

const WORKLET_GLOBAL_SOURCE = String.raw`(function () {
  const animation = (type, toValue, config, children, callback) => ({
    __spotifyPlusAnimation: true,
    type,
    ...(toValue === undefined ? {} : { toValue }),
    ...(config ? { config } : {}),
    ...(children ? { children } : {}),
    ...(callback ? { callback } : {}),
  });
  const ReduceMotion = Object.freeze({ System: 'system', Always: 'always', Never: 'never' });
  const Extrapolation = Object.freeze({ IDENTITY: 'identity', CLAMP: 'clamp', EXTEND: 'extend' });
  const RuntimeKind = Object.freeze({ ReactNative: 'reactNative', UI: 'ui', Worker: 'worker' });
  const SensorType = Object.freeze({
    ACCELEROMETER: 1,
    GYROSCOPE: 2,
    GRAVITY: 3,
    MAGNETIC_FIELD: 4,
    ROTATION: 5,
    USER_ACCELERATION: 6,
  });
  const KeyboardState = Object.freeze({ UNKNOWN: 0, OPENING: 1, OPEN: 2, CLOSING: 3, CLOSED: 4 });
  const createAnimatedPropAdapter = (adapter, nativeProps = []) => {
    Object.defineProperty(adapter, 'nativeProps', { value: Object.freeze([...nativeProps]), enumerable: true });
    return adapter;
  };
  const clamp = (value, minimum, maximum) => Math.min(maximum, Math.max(minimum, value));
  const interpolate = (value, input, output, options) => {
    const leftMode = typeof options === 'string' ? options : options?.extrapolateLeft ?? options?.extrapolate ?? 'extend';
    const rightMode = typeof options === 'string' ? options : options?.extrapolateRight ?? options?.extrapolate ?? 'extend';
    let index = 0;
    while (index < input.length - 2 && value > input[index + 1]) index++;
    const inputStart = input[index];
    const inputEnd = input[index + 1];
    if (value < inputStart && leftMode === 'identity') return value;
    if (value > inputEnd && rightMode === 'identity') return value;
    const raw = inputEnd === inputStart ? 0 : (value - inputStart) / (inputEnd - inputStart);
    const progress = value < inputStart && leftMode === 'clamp'
      ? 0
      : value > inputEnd && rightMode === 'clamp'
        ? 1
        : raw;
    return output[index] + (output[index + 1] - output[index]) * progress;
  };
  const namedColors = Object.freeze({
    black: 0xff000000,
    white: 0xffffffff,
    red: 0xffff0000,
    green: 0xff008000,
    blue: 0xff0000ff,
    transparent: 0x00000000,
  });
  const processColor = value => {
    if (typeof value === 'number') return value >>> 0;
    if (typeof value !== 'string') return null;
    const text = value.trim().toLowerCase();
    if (text in namedColors) return namedColors[text];
    if (/^#[0-9a-f]{6}$/.test(text)) return (0xff000000 | Number.parseInt(text.slice(1), 16)) >>> 0;
    if (/^#[0-9a-f]{8}$/.test(text)) return Number.parseInt(text.slice(1), 16) >>> 0;
    return null;
  };
  const convertToRGBA = value => {
    const color = processColor(value);
    if (color === null) return null;
    return {
      r: (color >>> 16) & 255,
      g: (color >>> 8) & 255,
      b: color & 255,
      a: ((color >>> 24) & 255) / 255,
    };
  };
  const interpolateColor = (value, input, output, options) => {
    const colors = output.map(convertToRGBA);
    if (colors.some(color => color === null)) return output[0];
    const channels = channel => interpolate(value, input, colors.map(color => color[channel]), options);
    const r = Math.round(clamp(channels('r'), 0, 255));
    const g = Math.round(clamp(channels('g'), 0, 255));
    const b = Math.round(clamp(channels('b'), 0, 255));
    const a = Math.round(clamp(channels('a'), 0, 1) * 255);
    return ((a << 24) | (r << 16) | (g << 8) | b) >>> 0;
  };
  const contrastColor = value => {
    const color = convertToRGBA(value);
    if (color === null) return 'white';
    const linear = channel => {
      const normalized = channel / 255;
      return normalized <= 0.04045 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4;
    };
    const luminance = 0.2126 * linear(color.r) + 0.7152 * linear(color.g) + 0.0722 * linear(color.b);
    return (luminance + 0.05) / 0.05 > 1.05 / (luminance + 0.05) ? 'black' : 'white';
  };
  const DynamicColorIOS = () => {
    const error = new Error('DynamicColorIOS is not supported by SpotifyPlus on Android.');
    error.name = 'UnsupportedPlatformError';
    throw error;
  };
  const easing = (type, evaluator, config) => {
    const fn = value => evaluator(clamp(value, 0, 1));
    Object.defineProperty(fn, '__spotifyPlusEasing', { value: { type, ...(config ?? {}) }, enumerable: true });
    return fn;
  };
  const Easing = {
    linear: easing('linear', value => value),
    ease: easing('ease', value => value * value * (3 - 2 * value)),
    quad: easing('quad', value => value * value),
    cubic: easing('cubic', value => value * value * value),
    sin: easing('sin', value => 1 - Math.cos(value * Math.PI / 2)),
    circle: easing('circle', value => 1 - Math.sqrt(1 - value * value)),
    exp: easing('exp', value => value === 0 ? 0 : 2 ** (10 * (value - 1))),
    poly: power => easing('poly', value => value ** power, { power }),
    bezier: (x1, y1, x2, y2) => easing('bezier', value => {
      const inverse = 1 - value;
      return 3 * inverse * inverse * value * y1 + 3 * inverse * value * value * y2 + value ** 3;
    }, { x1, y1, x2, y2 }),
    bezierFn: (x1, y1, x2, y2) => Easing.bezier(x1, y1, x2, y2),
    steps: (count, next) => easing('steps', value => (next ? Math.ceil(value * count) : Math.floor(value * count)) / count, { count, next }),
    in: value => easing('in', progress => value(progress), { easing: value.__spotifyPlusEasing }),
    out: value => easing('out', progress => 1 - value(1 - progress), { easing: value.__spotifyPlusEasing }),
    inOut: value => easing('inOut', progress => progress < 0.5 ? value(progress * 2) / 2 : 1 - value((1 - progress) * 2) / 2, { easing: value.__spotifyPlusEasing }),
  };
  const callbackConfig = callback => callback ? { callback } : {};
  const withTiming = (toValue, config = {}, callback) => animation('timing', toValue, {
    duration: config.duration ?? 300,
    easing: config.easing?.__spotifyPlusEasing ?? config.easing ?? Easing.inOut(Easing.quad).__spotifyPlusEasing,
    reduceMotion: config.reduceMotion ?? ReduceMotion.System,
  }, undefined, callback);
  const withSpring = (toValue, config = {}, callback) => animation('spring', toValue, {
    ...config,
    reduceMotion: config.reduceMotion ?? ReduceMotion.System,
  }, undefined, callback);
  const withDecay = (config = {}, callback) => animation('decay', undefined, {
    velocity: config.velocity ?? 0,
    deceleration: config.deceleration ?? 0.998,
    velocityFactor: config.velocityFactor ?? 1,
    rubberBandEffect: config.rubberBandEffect ?? false,
    rubberBandFactor: config.rubberBandFactor ?? 0.6,
    clamp: config.clamp,
    reduceMotion: config.reduceMotion ?? ReduceMotion.System,
  }, undefined, callback);
  const withDelay = (delayMs, child, reduceMotion = ReduceMotion.System) => animation('delay', child?.toValue, { delayMs, reduceMotion }, [child]);
  const withRepeat = (child, numberOfReps = 2, reverse = false, callback, reduceMotion = ReduceMotion.System) => animation('repeat', child?.toValue, { numberOfReps, reverse, reduceMotion }, [child], callback);
  const withSequence = (...values) => {
    const reduceMotion = typeof values[0] === 'string' ? values.shift() : ReduceMotion.System;
    return animation('sequence', values[values.length - 1]?.toValue, { reduceMotion }, values);
  };
  const withClamp = (config, child) => animation('clamp', child?.toValue, { ...config }, [child]);
  const defineAnimation = (startingValue, factory) => animation('custom', startingValue, { factory });
  const withCustomAnimation = defineAnimation;
  const isAnimation = value => value !== null && typeof value === 'object' && value.__spotifyPlusAnimation === true;
  const isSharedValue = value => value !== null && typeof value === 'object' && 'value' in value && typeof value.get === 'function' && typeof value.set === 'function';
  const isWorkletFunction = value => typeof value === 'function';
  const scheduleOnRN = (fn, ...args) => fn(...args);
  const runOnJS = fn => (...args) => fn(...args);
  const scheduleOnUI = (fn, ...args) => queueMicrotask(() => fn(...args));
  const runOnUI = fn => (...args) => scheduleOnUI(fn, ...args);
  const scheduleOnRuntime = (_runtime, fn, ...args) => scheduleOnUI(fn, ...args);
  const cancelAnimation = shared => shared && typeof shared.cancel === 'function' ? shared.cancel() : false;
  const measure = ref => ref?.measure?.() ?? null;
  const scrollTo = (ref, x, y, animated) => ref?.scrollTo?.(x, y, animated) ?? false;
  const dispatchCommand = (ref, command, args) => ref?.dispatchCommand?.(command, args) ?? false;
  const setNativeProps = (ref, props) => ref?.setNativeProps?.(props) ?? false;
  const getViewProp = (ref, name) => ref?.[name];
  const getRelativeCoords = (ref, x, y) => ref?.getRelativeCoords?.(x, y) ?? null;
  const getTimestamp = () => performance.now();
  const getRuntimeKind = () => RuntimeKind.UI;
  return {
    ReduceMotion,
    Extrapolation,
    RuntimeKind,
    SensorType,
    KeyboardState,
    createAnimatedPropAdapter,
    Easing,
    clamp,
    interpolate,
    interpolateColor,
    contrastColor,
    processColor,
    convertToRGBA,
    DynamicColorIOS,
    withTiming,
    withSpring,
    withDecay,
    withDelay,
    withRepeat,
    withSequence,
    withClamp,
    defineAnimation,
    withCustomAnimation,
    isAnimation,
    isSharedValue,
    isWorkletFunction,
    cancelAnimation,
    scheduleOnRN,
    runOnJS,
    scheduleOnUI,
    runOnUI,
    scheduleOnRuntime,
    measure,
    scrollTo,
    dispatchCommand,
    setNativeProps,
    getViewProp,
    getRelativeCoords,
    getTimestamp,
    getRuntimeKind,
  };
})()`;

function numericGeneration(scope: AnimatedRuntimeScope) {
    const generation = Number(scope.generation);
    if (!Number.isSafeInteger(generation) || generation < 0) {
        throw new TypeError(`Worklet generation must be a non-negative safe integer, received ${scope.generation}.`);
    }
    return generation;
}

function scopeKey(scope: AnimatedRuntimeScope) {
    return `${scope.scriptId}:${numericGeneration(scope)}`;
}

function mapperPriority(kind: NativeWorkletRegistration["kind"]) {
    switch (kind) {
        case "derived":
            return 10;
        case "reaction":
            return 20;
        case "style":
        case "props":
            return 30;
        case "event":
        case "gesture":
            return 40;
        case "frame":
            return 50;
    }
}

function isSerializedWorklet(value: unknown): value is SerializedWorklet {
    if (!isPlainObject(value)) {
        return false;
    }
    return isPlainObject(value.metadata)
        && value.metadata.version === 2
        && typeof value.metadata.code === "string"
        && typeof value.callable === "function";
}

function getSharedValueMarker(value: unknown): SharedValueMarker | null {
    if (!value || (typeof value !== "object" && typeof value !== "function")) {
        return null;
    }
    const marker = (value as Record<string, unknown>)[SHARED_VALUE_KEY];
    if (!marker || typeof marker !== "object") {
        return null;
    }
    const candidate = marker as Partial<SharedValueMarker>;
    if (candidate.version !== 2
        || typeof candidate.scriptId !== "string"
        || (typeof candidate.generation !== "string" && typeof candidate.generation !== "number")
        || typeof candidate.runtimeId !== "string"
        || typeof candidate.id !== "number") {
        return null;
    }
    return candidate as SharedValueMarker;
}

function bytesFromArrayBuffer(buffer: ArrayBuffer) {
    return Array.from(new Uint8Array(buffer));
}

export class SpotifyPlusWorkletAdapter implements NativeAnimationAdapter {
    readonly name = "SpotifyPlus Android UI V8";
    readonly capabilities = Object.freeze({
        worklets: true,
        workletGlobals: true,
        viewBindings: true,
        events: true,
        gestures: true,
        frameCallbacks: true,
        playbackClock: true,
        sensors: true,
        keyboard: true,
        layoutAnimations: true,
        cssAnimations: true,
        customRuntimes: true,
        measure: true,
        scroll: true,
        commands: true,
        setNativeProps: true,
    });

    private readonly registrations = new Map<string, Map<number, RegistrationState>>();
    private readonly viewBindings = new Map<string, Map<number, ViewBindingState>>();
    private readonly sourceBindings = new Map<string, Map<number, SourceBindingState>>();
    private readonly installedGlobals = new Set<string>();
    private readonly registeredNativeWorklets = new Map<string, Set<string>>();
    private readonly workletReferenceCounts = new Map<string, Map<string, number>>();
    private readonly nativeWorkletCallables = new Map<string, Map<string, Function>>();
    private readonly nativeWorkletRnCallbackIds = new Map<string, Map<string, Set<string>>>();
    private readonly sharedValueRnCallbackIds = new Map<string, Map<string, Set<string>>>();
    private readonly customRuntimeHandles = new Map<string, Set<string>>();
    private readonly rnCallbacks = new Map<string, (...args: any[]) => unknown>();
    private readonly oneShotRnCallbacks = new Set<string>();
    private readonly capturedWorkletIds = new WeakMap<Function, string>();
    private activeWorkletOwner: Set<string> | null = null;
    private nextRnCallbackId = 1;
    private nextCapturedWorkletId = 1;
    private nextScheduledWorkletId = 1;
    private nextSubscriptionId = 1;
    private nextReducedMotionSubscriptionId = 1;
    private nextCustomRuntimeId = 1;
    private readonly errorPollHandle: NodeJS.Timeout;
    private disposed = false;

    constructor(
        private readonly bridge: Bridge,
        private readonly logger: Logger,
        private readonly expectedScope: Readonly<AnimatedRuntimeScope>,
    ) {
        this.bridge.on("worklet:runOnRN", this.handleRunOnRn);
        this.errorPollHandle = setInterval(() => this.logNativeErrors(), 250);
        this.errorPollHandle.unref?.();
    }

    installWorkletGlobals(scope: AnimatedRuntimeScope, manifest: WorkletGlobalsManifest) {
        this.assertScope(scope);
        if (manifest.version !== 2) {
            throw new Error(`Unsupported worklet globals manifest version ${manifest.version}.`);
        }
        const key = scopeKey(scope);
        if (this.installedGlobals.has(key)) {
            return;
        }
        this.requireResult(
            this.bridge.installWorkletGlobals(
                scope.scriptId,
                numericGeneration(scope),
                WORKLET_GLOBAL_MODULE,
                WORKLET_GLOBAL_SOURCE,
            ),
            scope,
            "install worklet globals",
        );
        this.installedGlobals.add(key);
    }

    createMutable<Value>(scope: AnimatedRuntimeScope, request: { id: number; initial: Value }) {
        this.assertScope(scope);
        this.writeSharedValue(scope, request.id, request.initial);
    }

    readMutable<Value>(scope: AnimatedRuntimeScope, id: number): Value | undefined {
        this.assertScope(scope);
        const value = this.bridge.getWorkletSharedValue<unknown>(
            scope.scriptId,
            numericGeneration(scope),
            String(id),
        );
        return this.deserializeShareable(scope, value) as Value | undefined;
    }

    writeMutable<Value>(scope: AnimatedRuntimeScope, request: { id: number; value: Value }) {
        this.assertScope(scope);
        this.writeSharedValue(scope, request.id, request.value);
    }

    releaseMutable(scope: AnimatedRuntimeScope, id: number) {
        this.assertScope(scope);
        const callbacks = this.sharedValueRnCallbackIds.get(scopeKey(scope))?.get(String(id));
        if (callbacks) {
            this.releaseRnCallbacks(callbacks);
            this.sharedValueRnCallbackIds.get(scopeKey(scope))?.delete(String(id));
        }
        this.bridge.deleteWorkletSharedValue(scope.scriptId, numericGeneration(scope), String(id));
    }

    subscribeMutable<Value>(
        scope: AnimatedRuntimeScope,
        id: number,
        listener: (value: Value) => void,
    ) {
        this.assertScope(scope);
        const subscriptionId = this.nextSubscriptionId++;
        const workletId = `subscription:${id}:${subscriptionId}`;
        const mapperId = `${workletId}@internal`;
        const callbackId = `rn:${scopeKey(scope)}:${this.nextRnCallbackId++}`;
        this.rnCallbacks.set(callbackId, listener as (...args: any[]) => unknown);
        try {
            this.registerSyntheticWorklet(
                scope,
                workletId,
                "(function () { let initialized = false; let previous; return function () { const current = shared.value; if (!initialized) { initialized = true; previous = current; return null; } if (!Object.is(current, previous)) { previous = current; callback(current); } return null; }; })()",
                {
                    callback: {
                        __spotifyPlusShareable: "rnFunction",
                        id: callbackId,
                    } satisfies ShareableMarker,
                    shared: this.sharedValueMarker(id),
                },
            );
            this.requireResult(
                this.bridge.registerWorkletMapper(
                    scope.scriptId,
                    numericGeneration(scope),
                    mapperId,
                    workletId,
                    INTERNAL_SURFACE,
                    -1,
                    15,
                    false,
                ),
                scope,
                `subscribe to SharedValue ${id}`,
            );
        } catch (error) {
            this.bridge.unregisterWorklet(scope.scriptId, numericGeneration(scope), workletId);
            this.getScoped(this.registeredNativeWorklets, scope).delete(workletId);
            this.rnCallbacks.delete(callbackId);
            throw error;
        }

        let active = true;
        return () => {
            if (!active) {
                return;
            }
            active = false;
            this.bridge.unregisterWorkletMapper(scope.scriptId, numericGeneration(scope), mapperId);
            this.bridge.unregisterWorklet(scope.scriptId, numericGeneration(scope), workletId);
            this.getScoped(this.registeredNativeWorklets, scope).delete(workletId);
            this.rnCallbacks.delete(callbackId);
        };
    }

    registerWorklet(scope: AnimatedRuntimeScope, registration: NativeWorkletRegistration) {
        this.assertScope(scope);
        const registrations = this.getScoped(this.registrations, scope);
        if (registrations.has(registration.id)) {
            this.unregisterWorklet(scope, registration.id);
        }

        const state: RegistrationState = {
            registration,
            nativeWorkletIds: new Set(),
            mappers: new Map(),
            primaryWorkletId: String(registration.id),
        };
        registrations.set(registration.id, state);

        const previousOwner = this.activeWorkletOwner;
        this.activeWorkletOwner = state.nativeWorkletIds;
        try {
            this.installRegistration(scope, state);
            for (const view of this.getScoped(this.viewBindings, scope).values()) {
                this.attachRegistrationToView(scope, state, view);
            }
        } catch (error) {
            this.unregisterWorklet(scope, registration.id);
            throw error;
        } finally {
            this.activeWorkletOwner = previousOwner;
        }
    }

    updateWorklet(scope: AnimatedRuntimeScope, registration: NativeWorkletRegistration) {
        this.unregisterWorklet(scope, registration.id);
        this.registerWorklet(scope, registration);
    }

    unregisterWorklet(scope: AnimatedRuntimeScope, id: number) {
        this.assertScope(scope);
        const registrations = this.getScoped(this.registrations, scope);
        const state = registrations.get(id);
        if (!state) {
            return;
        }
        for (const mapper of state.mappers.values()) {
            this.bridge.unregisterWorkletMapper(scope.scriptId, numericGeneration(scope), mapper.mapperId);
            this.detachMapperFromView(scope, mapper.mapperId, mapper.nodeId);
        }
        state.mappers.clear();
        this.releaseRegistrationWorklets(scope, state);
        registrations.delete(id);
    }

    setWorkletActive(scope: AnimatedRuntimeScope, id: number, active: boolean) {
        this.assertScope(scope);
        const state = this.getScoped(this.registrations, scope).get(id);
        if (!state) {
            throw new Error(`Cannot ${active ? "activate" : "deactivate"} unknown worklet ${id}.`);
        }
        for (const mapper of state.mappers.values()) {
            if (mapper.active === active) {
                continue;
            }
            if (active) {
                this.registerMapper(scope, mapper);
            } else {
                this.bridge.unregisterWorkletMapper(scope.scriptId, numericGeneration(scope), mapper.mapperId);
            }
            mapper.active = active;
        }
    }

    bindView(scope: AnimatedRuntimeScope, binding: NativeViewBinding) {
        this.assertScope(scope);
        const surfaceId = getNodeSurfaceId(binding.viewTag);
        if (!surfaceId) {
            throw new Error(`Animated view ${binding.viewTag} is not attached to a SpotifyPlus surface.`);
        }

        this.unbindView(scope, binding.viewTag);
        const state: ViewBindingState = {
            binding,
            mapperIds: new Set(),
            layoutWorkletIds: new Set(),
        };
        this.getScoped(this.viewBindings, scope).set(binding.viewTag, state);

        for (const registrationId of [...binding.styleIds, ...binding.propsIds]) {
            const registration = this.getScoped(this.registrations, scope).get(registrationId);
            if (registration) {
                this.attachRegistrationToView(scope, registration, state, surfaceId);
            }
        }

        if (binding.entering || binding.exiting || binding.layout) {
            dispatchViewCommand(binding.viewTag, "configureWorkletLayout", {
                entering: this.serializeLayoutDescriptor(
                    scope,
                    binding.entering,
                    `layout:${binding.viewTag}:entering`,
                    state.layoutWorkletIds,
                ),
                exiting: this.serializeLayoutDescriptor(
                    scope,
                    binding.exiting,
                    `layout:${binding.viewTag}:exiting`,
                    state.layoutWorkletIds,
                ),
                layout: this.serializeLayoutDescriptor(
                    scope,
                    binding.layout,
                    `layout:${binding.viewTag}:layout`,
                    state.layoutWorkletIds,
                ),
            });
        }
    }

    unbindView(scope: AnimatedRuntimeScope, viewTag: number) {
        this.assertScope(scope);
        const views = this.getScoped(this.viewBindings, scope);
        const view = views.get(viewTag);
        if (!view) {
            return;
        }
        for (const mapperId of view.mapperIds) {
            this.bridge.unregisterWorkletMapper(scope.scriptId, numericGeneration(scope), mapperId);
            for (const registration of this.getScoped(this.registrations, scope).values()) {
                registration.mappers.delete(mapperId);
            }
        }
        for (const workletId of view.layoutWorkletIds) {
            this.unregisterNativeWorklet(scope, workletId);
        }
        views.delete(viewTag);
        clearWorkletProps(viewTag);
    }

    scheduleOnUI(scope: AnimatedRuntimeScope, worklet: SerializedWorklet, args: readonly unknown[]) {
        this.assertScope(scope);
        const workletId = `scheduled:${this.nextScheduledWorkletId++}:${worklet.metadata.hash}`;
        const callbackIds = new Set<string>();
        const cleanupId = this.registerRnCallback(scope, () => {
            this.releaseRnCallbacks(callbackIds);
        }, true);
        const options: ShareableSerializationOptions = {
            inlineWorklets: true,
            rnCallbackIds: callbackIds,
        };

        try {
            const inlineWorklet = this.serializeInlineWorklet(scope, worklet, options);
            const payload = this.serializeShareable(scope, args, new WeakSet<object>(), options);
            this.registerSyntheticWorklet(
                scope,
                workletId,
                "function (...args) { try { return body(...args); } finally { cleanup(); } }",
                {
                    body: inlineWorklet,
                    cleanup: {
                        __spotifyPlusShareable: "rnFunction",
                        id: cleanupId,
                    } satisfies ShareableMarker,
                },
            );
            this.requireResult(
                this.bridge.scheduleWorklet(scope.scriptId, numericGeneration(scope), workletId, payload as unknown[]),
                scope,
                `schedule worklet ${workletId}`,
            );
            this.getScoped(this.registeredNativeWorklets, scope).delete(workletId);
        } catch (error) {
            this.unregisterNativeWorklet(scope, workletId);
            this.rnCallbacks.delete(cleanupId);
            this.oneShotRnCallbacks.delete(cleanupId);
            this.releaseRnCallbacks(callbackIds);
            throw error;
        }
    }

    scheduleOnRN(_scope: AnimatedRuntimeScope, fn: (...args: any[]) => unknown, args: readonly unknown[]) {
        queueMicrotask(() => {
            try {
                fn(...args);
            } catch (error) {
                this.logger.error("A function scheduled from Animated.scheduleOnRN failed", error);
            }
        });
    }

    createWorkletRuntime(
        scope: AnimatedRuntimeScope,
        name: string,
        initializer?: SerializedWorklet,
    ): WorkletRuntimeHandle {
        this.assertScope(scope);
        const handle = Object.freeze({
            id: `runtime:${this.nextCustomRuntimeId++}`,
            name,
        });
        const key = scopeKey(scope);
        let handles = this.customRuntimeHandles.get(key);
        if (!handles) {
            handles = new Set();
            this.customRuntimeHandles.set(key, handles);
        }
        handles.add(String(handle.id));
        if (initializer) {
            this.scheduleOnUI(scope, initializer, []);
        }
        return handle;
    }

    releaseWorkletRuntime(scope: AnimatedRuntimeScope, runtime: WorkletRuntimeHandle) {
        this.assertScope(scope);
        this.customRuntimeHandles.get(scopeKey(scope))?.delete(String(runtime.id));
    }

    scheduleOnRuntime(
        scope: AnimatedRuntimeScope,
        runtime: WorkletRuntimeHandle,
        worklet: SerializedWorklet,
        args: readonly unknown[],
    ) {
        this.assertScope(scope);
        if (!this.customRuntimeHandles.get(scopeKey(scope))?.has(String(runtime.id))) {
            throw new Error(`Unknown or released worklet runtime ${runtime.name}.`);
        }
        this.scheduleOnUI(scope, worklet, args);
    }

    cancelAnimation(scope: AnimatedRuntimeScope, mutableId: number) {
        this.assertScope(scope);
        this.bridge.cancelWorkletAnimation(scope.scriptId, numericGeneration(scope), String(mutableId));
    }

    getTimestamp() {
        return typeof performance === "undefined" ? Date.now() : performance.now();
    }

    resolveViewTag(_scope: AnimatedRuntimeScope, ref: unknown): number | null {
        if (typeof ref === "number" && Number.isFinite(ref)) {
            return ref;
        }
        if (!ref || (typeof ref !== "object" && typeof ref !== "function")) {
            return null;
        }
        const candidate = ref as {
            current?: unknown;
            nodeId?: unknown;
            id?: unknown;
            getNativeNodeId?: () => unknown;
            getTag?: () => unknown;
        };
        if (candidate.current && candidate.current !== candidate) {
            return this.resolveViewTag(_scope, candidate.current);
        }
        const value = typeof candidate.getTag === "function"
            ? candidate.getTag()
            : typeof candidate.getNativeNodeId === "function"
                ? candidate.getNativeNodeId()
                : candidate.nodeId ?? candidate.id;
        return typeof value === "number" && Number.isFinite(value) ? value : null;
    }

    measure() {
        return null;
    }

    scrollTo(_scope: AnimatedRuntimeScope, command: { viewTag: number; x?: number; y?: number; animated?: boolean }) {
        dispatchViewCommand(command.viewTag, "scrollTo", {
            x: command.x ?? 0,
            y: command.y ?? 0,
            animated: command.animated ?? false,
        });
    }

    dispatchCommand(
        _scope: AnimatedRuntimeScope,
        viewTag: number,
        command: string,
        args: readonly unknown[],
    ) {
        dispatchViewCommand(viewTag, command, { args: [...args] });
    }

    setNativeProps(_scope: AnimatedRuntimeScope, viewTag: number, props: Readonly<Record<string, unknown>>) {
        updateNodeProps(viewTag, { ...props });
    }

    registerSource(scope: AnimatedRuntimeScope, registration: NativeSourceRegistration) {
        this.assertScope(scope);
        const sources = this.getScoped(this.sourceBindings, scope);
        if (sources.has(registration.id)) {
            this.unregisterSource(scope, registration.id);
        }
        const bindings: SourceBindingState = {
            sourceId: this.sourceId(registration),
            sharedValueIds: [],
        };
        try {
            for (const [name, mutableId] of Object.entries(registration.targetMutableIds)) {
                const sourceId = this.sourceId(registration, name);
                const sharedValueId = String(mutableId);
                this.requireResult(
                    this.bridge.registerWorkletSource(
                        scope.scriptId,
                        numericGeneration(scope),
                        sourceId,
                        sharedValueId,
                        registration.config ?? {},
                    ),
                    scope,
                    `register ${registration.kind} source`,
                );
                bindings.sharedValueIds.push(`${sourceId}\u0000${sharedValueId}`);
            }
        } catch (error) {
            for (const entry of bindings.sharedValueIds) {
                const separator = entry.indexOf("\u0000");
                this.bridge.unregisterWorkletSource(
                    scope.scriptId,
                    numericGeneration(scope),
                    entry.slice(0, separator),
                    entry.slice(separator + 1),
                );
            }
            throw error;
        }
        sources.set(registration.id, bindings);
    }

    unregisterSource(scope: AnimatedRuntimeScope, id: number) {
        this.assertScope(scope);
        const sources = this.getScoped(this.sourceBindings, scope);
        const binding = sources.get(id);
        if (!binding) {
            return;
        }
        for (const entry of binding.sharedValueIds) {
            const separator = entry.indexOf("\u0000");
            const sourceId = entry.slice(0, separator);
            const sharedValueId = entry.slice(separator + 1);
            this.bridge.unregisterWorkletSource(
                scope.scriptId,
                numericGeneration(scope),
                sourceId,
                sharedValueId,
            );
        }
        sources.delete(id);
    }

    getReducedMotion() {
        return this.bridge.getWorkletReducedMotion();
    }

    subscribeReducedMotion(scope: AnimatedRuntimeScope, listener: (reduced: boolean) => void) {
        this.assertScope(scope);
        const sharedValueId = -1_000_000_000 - this.nextReducedMotionSubscriptionId++;
        this.bridge.setWorkletSharedValue(
            scope.scriptId,
            numericGeneration(scope),
            String(sharedValueId),
            this.bridge.getWorkletReducedMotion(),
        );
        const unsubscribe = this.subscribeMutable(scope, sharedValueId, listener);
        try {
            this.requireResult(
                this.bridge.registerWorkletSource(
                    scope.scriptId,
                    numericGeneration(scope),
                    "reducedMotion",
                    String(sharedValueId),
                    {},
                ),
                scope,
                "subscribe to reduced-motion state",
            );
        } catch (error) {
            unsubscribe();
            this.bridge.deleteWorkletSharedValue(
                scope.scriptId,
                numericGeneration(scope),
                String(sharedValueId),
            );
            throw error;
        }
        return () => {
            unsubscribe();
            this.bridge.unregisterWorkletSource(
                scope.scriptId,
                numericGeneration(scope),
                "reducedMotion",
                String(sharedValueId),
            );
            this.bridge.deleteWorkletSharedValue(
                scope.scriptId,
                numericGeneration(scope),
                String(sharedValueId),
            );
        };
    }

    setReducedMotionOverride(
        scope: AnimatedRuntimeScope,
        mode: "system" | "always" | "never" | null,
    ) {
        this.assertScope(scope);
        this.requireResult(
            this.bridge.setWorkletReducedMotionOverride(
                scope.scriptId,
                numericGeneration(scope),
                mode,
            ),
            scope,
            "configure reduced-motion override",
        );
    }

    configureLayoutAnimations(_scope: AnimatedRuntimeScope, _enabled: boolean) {
        // Layout descriptors are attached to each bound view. This hook exists
        // to match Reanimated's global enable/disable switch.
    }

    disposeRuntimeScope(scope: AnimatedRuntimeScope) {
        if (this.disposed) {
            return;
        }
        const key = scopeKey(scope);
        const views = this.viewBindings.get(key);
        if (views) {
            for (const viewTag of [...views.keys()]) {
                this.unbindView(scope, viewTag);
            }
        }
        this.registrations.delete(key);
        this.viewBindings.delete(key);
        this.sourceBindings.delete(key);
        this.registeredNativeWorklets.delete(key);
        this.workletReferenceCounts.delete(key);
        this.nativeWorkletCallables.delete(key);
        const nativeCallbackSets = this.nativeWorkletRnCallbackIds.get(key);
        if (nativeCallbackSets) {
            for (const callbacks of nativeCallbackSets.values()) {
                this.releaseRnCallbacks(callbacks);
            }
        }
        this.nativeWorkletRnCallbackIds.delete(key);
        const sharedCallbackSets = this.sharedValueRnCallbackIds.get(key);
        if (sharedCallbackSets) {
            for (const callbacks of sharedCallbackSets.values()) {
                this.releaseRnCallbacks(callbacks);
            }
        }
        this.sharedValueRnCallbackIds.delete(key);
        this.customRuntimeHandles.delete(key);
        this.installedGlobals.delete(key);
        this.bridge.disposeWorkletContext(scope.scriptId, numericGeneration(scope));
        this.bridge.off("worklet:runOnRN", this.handleRunOnRn);
        clearInterval(this.errorPollHandle);
        this.rnCallbacks.clear();
        this.oneShotRnCallbacks.clear();
        this.disposed = true;
    }

    private readonly handleRunOnRn = (payload: RnCallbackPayload) => {
        if (!payload?.scriptId || payload.generation === undefined || !payload.functionId) {
            return;
        }
        const eventScope = scopeKey({
            scriptId: payload.scriptId,
            generation: payload.generation,
        });
        if (eventScope !== scopeKey(this.expectedScope)) {
            return;
        }
        if (!payload.functionId.startsWith(`rn:${eventScope}:`)) {
            return;
        }
        const callback = payload?.functionId ? this.rnCallbacks.get(payload.functionId) : undefined;
        if (!callback) {
            return;
        }
        if (this.oneShotRnCallbacks.delete(payload.functionId!)) {
            this.rnCallbacks.delete(payload.functionId!);
        }
        const rawArgs = Array.isArray(payload.args) ? payload.args : [];
        const args = rawArgs.map(value => this.deserializeShareable({
            scriptId: payload.scriptId!,
            generation: payload.generation!,
        }, value));
        queueMicrotask(() => {
            try {
                callback(...args);
            } catch (error) {
                this.logger.error(`RN callback ${payload.functionId} failed`, error);
            }
        });
    };

    private installRegistration(scope: AnimatedRuntimeScope, state: RegistrationState) {
        const { registration } = state;
        const entries = Object.entries(registration.worklets);

        if ((registration.kind === "style" || registration.kind === "props") && entries.length === 1) {
            state.primaryWorkletId = String(registration.id);
            this.registerSerializedWorklet(scope, state.primaryWorkletId, entries[0][1]);
            state.nativeWorkletIds.add(state.primaryWorkletId);
            return;
        }

        const workletIds: Record<string, string> = {};
        for (const [name, worklet] of entries) {
            const workletId = entries.length === 1 && (registration.kind === "event" || registration.kind === "frame")
                ? String(registration.id)
                : `${registration.id}:${name}`;
            workletIds[name] = workletId;
            this.registerSerializedWorklet(scope, workletId, worklet);
            state.nativeWorkletIds.add(workletId);
        }

        if (registration.kind === "derived") {
            const targetId = registration.targetMutableIds?.[0];
            if (targetId === undefined || !workletIds.updater) {
                throw new Error(`Derived worklet ${registration.id} is missing its updater or target SharedValue.`);
            }
            state.primaryWorkletId = String(registration.id);
            this.registerSyntheticWorklet(
                scope,
                state.primaryWorkletId,
                "function () { target.value = updater(); return null; }",
                {
                    target: this.sharedValueMarker(targetId),
                    updater: this.workletMarker(workletIds.updater),
                },
            );
            state.nativeWorkletIds.add(state.primaryWorkletId);
            this.installInternalMapper(scope, state, false);
            return;
        }

        if (registration.kind === "reaction") {
            if (!workletIds.prepare || !workletIds.react) {
                throw new Error(`Reaction worklet ${registration.id} is missing prepare or react.`);
            }
            state.primaryWorkletId = String(registration.id);
            this.registerSyntheticWorklet(
                scope,
                state.primaryWorkletId,
                "(function () { let previous = null; return function () { const current = prepare(); react(current, previous); previous = current; return null; }; })()",
                {
                    prepare: this.workletMarker(workletIds.prepare),
                    react: this.workletMarker(workletIds.react),
                },
            );
            state.nativeWorkletIds.add(state.primaryWorkletId);
            this.installInternalMapper(scope, state, false);
            return;
        }

        if (registration.kind === "frame") {
            state.primaryWorkletId = workletIds.callback ?? String(registration.id);
            this.installInternalMapper(scope, state, true, registration.options?.active !== false);
            return;
        }

        if (registration.kind === "event" || registration.kind === "gesture") {
            const composedIds = registration.options?.composedEventIds;
            if (Array.isArray(composedIds) && composedIds.length > 0) {
                const callbacks = composedIds.map(id => this.workletMarker(String(id)));
                state.primaryWorkletId = String(registration.id);
                this.registerSyntheticWorklet(
                    scope,
                    state.primaryWorkletId,
                    "function (event) { for (const callback of callbacks) callback(event); return null; }",
                    { callbacks },
                );
                state.nativeWorkletIds.add(state.primaryWorkletId);
                return;
            }
            if (entries.length === 1) {
                state.primaryWorkletId = workletIds[entries[0][0]];
                if (state.primaryWorkletId !== String(registration.id)) {
                    this.registerSyntheticWorklet(
                        scope,
                        String(registration.id),
                        "function (event) { return handler(event); }",
                        { handler: this.workletMarker(state.primaryWorkletId) },
                    );
                    state.primaryWorkletId = String(registration.id);
                    state.nativeWorkletIds.add(state.primaryWorkletId);
                }
                return;
            }
            state.primaryWorkletId = String(registration.id);
            const handlers = Object.fromEntries(
                Object.entries(workletIds).map(([name, id]) => [name, this.workletMarker(id)]),
            );
            this.registerSyntheticWorklet(
                scope,
                state.primaryWorkletId,
                "(function () { const context = {}; return function (event) { const handler = handlers[event?.eventName] ?? handlers.handler ?? handlers.onScroll; return handler ? handler(event, context) : undefined; }; })()",
                { handlers },
            );
            state.nativeWorkletIds.add(state.primaryWorkletId);
        }
    }

    private installInternalMapper(
        scope: AnimatedRuntimeScope,
        state: RegistrationState,
        runEveryFrame: boolean,
        active = true,
    ) {
        const mapper: RegisteredMapper = {
            mapperId: `${state.registration.id}@internal`,
            workletId: state.primaryWorkletId,
            surfaceId: INTERNAL_SURFACE,
            nodeId: -1,
            priority: mapperPriority(state.registration.kind),
            runEveryFrame,
            active,
        };
        state.mappers.set(mapper.mapperId, mapper);
        if (active) {
            this.registerMapper(scope, mapper);
        }
    }

    private registerMapper(scope: AnimatedRuntimeScope, mapper: RegisteredMapper) {
        this.requireResult(
            this.bridge.registerWorkletMapper(
                scope.scriptId,
                numericGeneration(scope),
                mapper.mapperId,
                mapper.workletId,
                mapper.surfaceId,
                mapper.nodeId,
                mapper.priority,
                mapper.runEveryFrame,
            ),
            scope,
            `register mapper ${mapper.mapperId}`,
        );
    }

    private attachRegistrationToView(
        scope: AnimatedRuntimeScope,
        registration: RegistrationState,
        view: ViewBindingState,
        knownSurfaceId?: string,
    ) {
        const registrationId = registration.registration.id;
        const isReferenced = view.binding.styleIds.includes(registrationId)
            || view.binding.propsIds.includes(registrationId);
        if (!isReferenced) {
            return;
        }
        const mapperId = `${registrationId}@${view.binding.viewTag}`;
        if (view.mapperIds.has(mapperId)) {
            return;
        }
        const surfaceId = knownSurfaceId ?? getNodeSurfaceId(view.binding.viewTag);
        if (!surfaceId) {
            throw new Error(`Animated view ${view.binding.viewTag} is not attached to a SpotifyPlus surface.`);
        }
        const mapper: RegisteredMapper = {
            mapperId,
            workletId: registration.primaryWorkletId,
            surfaceId,
            nodeId: view.binding.viewTag,
            priority: mapperPriority(registration.registration.kind),
            runEveryFrame: false,
            active: true,
        };
        this.registerMapper(scope, mapper);
        registration.mappers.set(mapper.mapperId, mapper);
        view.mapperIds.add(mapper.mapperId);
    }

    private detachMapperFromView(scope: AnimatedRuntimeScope, mapperId: string, nodeId: number) {
        if (nodeId < 0) {
            return;
        }
        const view = this.getScoped(this.viewBindings, scope).get(nodeId);
        view?.mapperIds.delete(mapperId);
    }

    private serializeLayoutDescriptor(
        scope: AnimatedRuntimeScope,
        value: unknown,
        path: string,
        workletIds: Set<string>,
    ): unknown {
        if (isSerializedWorklet(value)) {
            const workletId = `${path}:${value.metadata.hash}`;
            this.registerSerializedWorklet(scope, workletId, value);
            workletIds.add(workletId);
            return {
                __spotifyPlusLayoutWorklet: true,
                scriptId: scope.scriptId,
                generation: numericGeneration(scope),
                workletId,
            };
        }
        if (Array.isArray(value)) {
            return value.map((entry, index) => this.serializeLayoutDescriptor(
                scope,
                entry,
                `${path}:${index}`,
                workletIds,
            ));
        }
        if (!value || typeof value !== "object") {
            return value;
        }
        return Object.fromEntries(
            Object.entries(value).map(([name, child]) => [
                name,
                this.serializeLayoutDescriptor(scope, child, `${path}:${name}`, workletIds),
            ]),
        );
    }

    private registerSerializedWorklet(scope: AnimatedRuntimeScope, workletId: string, worklet: SerializedWorklet) {
        this.retainOwnedWorklet(scope, workletId);
        const registered = this.getScoped(this.registeredNativeWorklets, scope);
        if (registered.has(workletId)) {
            return;
        }
        const callbackIds = new Set<string>();
        const closure: Record<string, unknown> = {};
        for (const [name, value] of Object.entries(worklet.metadata.closure)) {
            closure[name] = this.serializeShareable(
                scope,
                value,
                new WeakSet<object>(),
                { rnCallbackIds: callbackIds },
            );
        }
        for (const [localName, exportName] of Object.entries(worklet.metadata.globals ?? {})) {
            closure[localName] = {
                __spotifyPlusShareable: "workletGlobal",
                module: WORKLET_GLOBAL_MODULE,
                name: exportName,
            } satisfies ShareableMarker;
        }
        try {
            this.requireResult(
                this.bridge.registerWorklet(
                    scope.scriptId,
                    numericGeneration(scope),
                    workletId,
                    worklet.metadata.code,
                    closure,
                ),
                scope,
                `register worklet ${workletId}`,
            );
        } catch (error) {
            this.releaseRnCallbacks(callbackIds);
            throw error;
        }
        registered.add(workletId);
        this.getNativeWorkletCallbackMap(scope).set(workletId, callbackIds);
        let callables = this.nativeWorkletCallables.get(scopeKey(scope));
        if (!callables) {
            callables = new Map();
            this.nativeWorkletCallables.set(scopeKey(scope), callables);
        }
        callables.set(workletId, worklet.callable);
    }

    private registerSyntheticWorklet(
        scope: AnimatedRuntimeScope,
        workletId: string,
        code: string,
        closure: Readonly<Record<string, unknown>>,
    ) {
        this.retainOwnedWorklet(scope, workletId);
        const registered = this.getScoped(this.registeredNativeWorklets, scope);
        if (registered.has(workletId)) {
            return;
        }
        this.requireResult(
            this.bridge.registerWorklet(scope.scriptId, numericGeneration(scope), workletId, code, closure),
            scope,
            `register internal worklet ${workletId}`,
        );
        registered.add(workletId);
    }

    private retainOwnedWorklet(scope: AnimatedRuntimeScope, workletId: string) {
        const owner = this.activeWorkletOwner;
        if (!owner || owner.has(workletId)) {
            return;
        }
        owner.add(workletId);
        const key = scopeKey(scope);
        let references = this.workletReferenceCounts.get(key);
        if (!references) {
            references = new Map();
            this.workletReferenceCounts.set(key, references);
        }
        references.set(workletId, (references.get(workletId) ?? 0) + 1);
    }

    private releaseRegistrationWorklets(scope: AnimatedRuntimeScope, state: RegistrationState) {
        const key = scopeKey(scope);
        const references = this.workletReferenceCounts.get(key);
        for (const workletId of state.nativeWorkletIds) {
            const remaining = (references?.get(workletId) ?? 1) - 1;
            if (remaining > 0) {
                references?.set(workletId, remaining);
                continue;
            }
            references?.delete(workletId);
            this.unregisterNativeWorklet(scope, workletId);
        }
        state.nativeWorkletIds.clear();
        if (references?.size === 0) {
            this.workletReferenceCounts.delete(key);
        }
    }

    private unregisterNativeWorklet(scope: AnimatedRuntimeScope, workletId: string) {
        this.bridge.unregisterWorklet(scope.scriptId, numericGeneration(scope), workletId);
        this.getScoped(this.registeredNativeWorklets, scope).delete(workletId);
        this.nativeWorkletCallables.get(scopeKey(scope))?.delete(workletId);
        const callbacks = this.nativeWorkletRnCallbackIds.get(scopeKey(scope))?.get(workletId);
        if (callbacks) {
            this.releaseRnCallbacks(callbacks);
            this.nativeWorkletRnCallbackIds.get(scopeKey(scope))?.delete(workletId);
        }
    }

    private getNativeWorkletCallbackMap(scope: AnimatedRuntimeScope) {
        const key = scopeKey(scope);
        let callbacks = this.nativeWorkletRnCallbackIds.get(key);
        if (!callbacks) {
            callbacks = new Map();
            this.nativeWorkletRnCallbackIds.set(key, callbacks);
        }
        return callbacks;
    }

    private registerRnCallback(
        scope: AnimatedRuntimeScope,
        callback: (...args: any[]) => unknown,
        oneShot = false,
        owner?: Set<string>,
    ) {
        const id = `rn:${scopeKey(scope)}:${this.nextRnCallbackId++}`;
        this.rnCallbacks.set(id, callback);
        if (oneShot) {
            this.oneShotRnCallbacks.add(id);
        }
        owner?.add(id);
        return id;
    }

    private releaseRnCallbacks(callbackIds: Iterable<string>) {
        for (const callbackId of callbackIds) {
            this.rnCallbacks.delete(callbackId);
            this.oneShotRnCallbacks.delete(callbackId);
        }
    }

    private deserializeShareable(scope: AnimatedRuntimeScope, value: unknown): unknown {
        if (Array.isArray(value)) {
            return value.map(item => this.deserializeShareable(scope, item));
        }
        if (!value || typeof value !== "object") {
            return value;
        }

        const marker = value as Partial<ShareableMarker>;
        const kind = marker.__spotifyPlusShareable;
        if (!kind) {
            return Object.fromEntries(
                Object.entries(value).map(([name, child]) => [name, this.deserializeShareable(scope, child)]),
            );
        }

        switch (kind) {
            case "undefined":
                return undefined;
            case "bigint":
                return BigInt(String(marker.value));
            case "regexp":
                return new RegExp(String(marker.source ?? ""), String(marker.flags ?? ""));
            case "date":
                return new Date(String(marker.value));
            case "arrayBuffer":
                return Uint8Array.from((marker.bytes as number[] | undefined) ?? []).buffer;
            case "typedArray": {
                const name = String(marker.name ?? "Uint8Array");
                const constructor = (globalThis as Record<string, any>)[name];
                if (typeof constructor !== "function") {
                    throw new TypeError(`Unknown typed array ${name}.`);
                }
                const bytes = Uint8Array.from((marker.values as number[] | undefined) ?? []);
                return name === "DataView" ? new DataView(bytes.buffer) : new constructor(bytes.buffer);
            }
            case "map":
                return new Map(
                    ((marker.entries as unknown[][] | undefined) ?? []).map(([key, child]) => [
                        this.deserializeShareable(scope, key),
                        this.deserializeShareable(scope, child),
                    ]),
                );
            case "set":
                return new Set(
                    ((marker.values as unknown[] | undefined) ?? []).map(child => (
                        this.deserializeShareable(scope, child)
                    )),
                );
            case "rnFunction":
                return this.rnCallbacks.get(String(marker.id));
            case "worklet":
                return this.nativeWorkletCallables.get(scopeKey(scope))?.get(String(marker.id));
            case "uiFunction": {
                const workletId = `ui-function:${this.nextCapturedWorkletId++}:${String(marker.id)}`;
                this.registerSyntheticWorklet(
                    scope,
                    workletId,
                    "function (...args) { return callback(...args); }",
                    { callback: value },
                );
                return (...args: unknown[]) => {
                    const serialized = this.serializeShareable(scope, args, new WeakSet<object>());
                    this.requireResult(
                        this.bridge.scheduleWorklet(
                            scope.scriptId,
                            numericGeneration(scope),
                            workletId,
                            serialized as unknown[],
                        ),
                        scope,
                        `schedule UI callback ${marker.id}`,
                    );
                };
            }
            case "view":
                return Object.freeze({
                    surfaceId: String(marker.surfaceId),
                    nodeId: Number(marker.nodeId),
                });
            case "sharedValue": {
                const sharedValueId = String(marker.id);
                const read = () => this.deserializeShareable(
                    scope,
                    this.bridge.getWorkletSharedValue(
                        scope.scriptId,
                        numericGeneration(scope),
                        sharedValueId,
                    ),
                );
                const write = (next: unknown) => this.bridge.setWorkletSharedValue(
                    scope.scriptId,
                    numericGeneration(scope),
                    sharedValueId,
                    this.serializeShareable(scope, next, new WeakSet<object>()),
                );
                return {
                    get value() {
                        return read();
                    },
                    set value(next: unknown) {
                        write(next);
                    },
                    get: read,
                    set: write,
                };
            }
            default:
                return value;
        }
    }

    private serializeShareable(
        scope: AnimatedRuntimeScope,
        value: unknown,
        seen: WeakSet<object>,
        options: ShareableSerializationOptions = {},
    ): unknown {
        if (value === null || typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
            return value;
        }
        if (value === undefined) {
            return { __spotifyPlusShareable: "undefined" } satisfies ShareableMarker;
        }
        if (typeof value === "bigint") {
            return { __spotifyPlusShareable: "bigint", value: String(value) } satisfies ShareableMarker;
        }
        if (typeof value === "symbol") {
            throw new TypeError("Symbols cannot be captured by a SpotifyPlus worklet.");
        }
        if (typeof value === "function") {
            const metadata = getWorkletMetadata(value);
            if (metadata) {
                if (options.inlineWorklets) {
                    return this.serializeInlineWorklet(scope, {
                        metadata,
                        callable: value as (...args: any[]) => unknown,
                    }, options);
                }
                const id = this.getCapturedWorkletId(value, metadata.hash);
                this.registerSerializedWorklet(scope, id, {
                    metadata,
                    callable: value as (...args: any[]) => unknown,
                });
                return this.workletMarker(id);
            }
            const id = this.registerRnCallback(
                scope,
                value as (...args: any[]) => unknown,
                (value as { __spotifyPlusOneShotRNCallback?: unknown }).__spotifyPlusOneShotRNCallback === true,
                options.rnCallbackIds,
            );
            return { __spotifyPlusShareable: "rnFunction", id } satisfies ShareableMarker;
        }
        const sharedValue = getSharedValueMarker(value);
        if (sharedValue) {
            if (sharedValue.scriptId !== scope.scriptId
                || String(sharedValue.generation) !== String(scope.generation)) {
                throw new TypeError("A worklet cannot capture a SharedValue from another script generation.");
            }
            return this.sharedValueMarker(sharedValue.id);
        }
        if (isSerializedWorklet(value)) {
            if (options.inlineWorklets) {
                return this.serializeInlineWorklet(scope, value, options);
            }
            const id = this.getCapturedWorkletId(value.callable, value.metadata.hash);
            this.registerSerializedWorklet(scope, id, value);
            return this.workletMarker(id);
        }
        if (seen.has(value as object)) {
            throw new TypeError("Circular values cannot be captured by a SpotifyPlus worklet.");
        }
        seen.add(value as object);
        try {
            const viewTag = this.resolveViewTag(scope, value);
            const surfaceId = viewTag === null ? undefined : getNodeSurfaceId(viewTag);
            if (viewTag !== null && surfaceId) {
                return {
                    __spotifyPlusShareable: "view",
                    surfaceId,
                    nodeId: viewTag,
                } satisfies ShareableMarker;
            }
            if (Array.isArray(value)) {
                return Array.from(value, item => this.serializeShareable(scope, item, seen, options));
            }
            if (hasObjectTag(value, "Map")) {
                const map = value as Map<unknown, unknown>;
                return {
                    __spotifyPlusShareable: "map",
                    entries: Array.from(map, ([key, item]) => [
                        this.serializeShareable(scope, key, seen, options),
                        this.serializeShareable(scope, item, seen, options),
                    ]),
                } satisfies ShareableMarker;
            }
            if (hasObjectTag(value, "Set")) {
                const set = value as Set<unknown>;
                return {
                    __spotifyPlusShareable: "set",
                    values: Array.from(set, item => this.serializeShareable(scope, item, seen, options)),
                } satisfies ShareableMarker;
            }
            if (hasObjectTag(value, "RegExp")) {
                const regexp = value as RegExp;
                return {
                    __spotifyPlusShareable: "regexp",
                    source: regexp.source,
                    flags: regexp.flags,
                } satisfies ShareableMarker;
            }
            if (hasObjectTag(value, "Date")) {
                return {
                    __spotifyPlusShareable: "date",
                    value: (value as Date).toISOString(),
                } satisfies ShareableMarker;
            }
            if (hasObjectTag(value, "ArrayBuffer")) {
                return {
                    __spotifyPlusShareable: "arrayBuffer",
                    bytes: bytesFromArrayBuffer(value as ArrayBuffer),
                } satisfies ShareableMarker;
            }
            if (ArrayBuffer.isView(value)) {
                const typed = value as ArrayBufferView & { constructor: { name?: string } };
                return {
                    __spotifyPlusShareable: "typedArray",
                    name: typed.constructor.name ?? "Uint8Array",
                    values: Array.from(new Uint8Array(typed.buffer, typed.byteOffset, typed.byteLength)),
                } satisfies ShareableMarker;
            }
            if (!isPlainObject(value)) {
                const name = (value as { constructor?: { name?: string } }).constructor?.name ?? "unknown";
                throw new TypeError(`Unsupported captured value of type ${name}.`);
            }
            const output: Record<string, unknown> = {};
            for (const [name, child] of Object.entries(value)) {
                output[name] = this.serializeShareable(scope, child, seen, options);
            }
            return output;
        } finally {
            seen.delete(value as object);
        }
    }

    private serializeInlineWorklet(
        scope: AnimatedRuntimeScope,
        worklet: SerializedWorklet,
        options: ShareableSerializationOptions,
    ) {
        const closure: Record<string, unknown> = {};
        for (const [name, value] of Object.entries(worklet.metadata.closure)) {
            closure[name] = this.serializeShareable(
                scope,
                value,
                new WeakSet<object>(),
                options,
            );
        }
        for (const [localName, exportName] of Object.entries(worklet.metadata.globals ?? {})) {
            closure[localName] = {
                __spotifyPlusShareable: "workletGlobal",
                module: WORKLET_GLOBAL_MODULE,
                name: exportName,
            } satisfies ShareableMarker;
        }
        return {
            __spotifyPlusShareable: "inlineWorklet",
            metadata: {
                ...worklet.metadata,
                closure,
            },
        } satisfies ShareableMarker;
    }

    private writeSharedValue(scope: AnimatedRuntimeScope, id: number, value: unknown) {
        const isAnimation = isPlainObject(value) && value.__spotifyPlusAnimation === true;
        const callbackIds = new Set<string>();
        const serialized = this.serializeShareable(
            scope,
            value,
            new WeakSet<object>(),
            { inlineWorklets: true, rnCallbackIds: callbackIds },
        );
        let cleanupId: string | undefined;
        if (isAnimation && callbackIds.size > 0 && isPlainObject(serialized)) {
            cleanupId = this.registerRnCallback(scope, () => {
                this.releaseRnCallbacks(callbackIds);
            }, true);
            serialized.__spotifyPlusCleanup = {
                __spotifyPlusShareable: "rnFunction",
                id: cleanupId,
            } satisfies ShareableMarker;
        }
        try {
            this.requireResult(
                this.bridge.setWorkletSharedValue(
                    scope.scriptId,
                    numericGeneration(scope),
                    String(id),
                    serialized,
                ),
                scope,
                `write SharedValue ${id}`,
            );
            const callbacksByValue = this.getSharedValueCallbackMap(scope);
            const previousCallbacks = callbacksByValue.get(String(id));
            if (previousCallbacks) {
                this.releaseRnCallbacks(previousCallbacks);
            }
            if (!isAnimation && callbackIds.size > 0) {
                callbacksByValue.set(String(id), callbackIds);
            } else {
                callbacksByValue.delete(String(id));
            }
        } catch (error) {
            this.releaseRnCallbacks(callbackIds);
            if (cleanupId) {
                this.rnCallbacks.delete(cleanupId);
                this.oneShotRnCallbacks.delete(cleanupId);
            }
            throw error;
        }
    }

    private sharedValueMarker(id: string | number): ShareableMarker {
        return { __spotifyPlusShareable: "sharedValue", id: String(id) };
    }

    private getSharedValueCallbackMap(scope: AnimatedRuntimeScope) {
        const key = scopeKey(scope);
        let callbacks = this.sharedValueRnCallbackIds.get(key);
        if (!callbacks) {
            callbacks = new Map();
            this.sharedValueRnCallbackIds.set(key, callbacks);
        }
        return callbacks;
    }

    private workletMarker(id: string): ShareableMarker {
        return { __spotifyPlusShareable: "worklet", id };
    }

    private getCapturedWorkletId(callable: Function, hash: string) {
        const existing = this.capturedWorkletIds.get(callable);
        if (existing) {
            return existing;
        }
        const id = `captured:${this.nextCapturedWorkletId++}:${hash}`;
        this.capturedWorkletIds.set(callable, id);
        return id;
    }

    private sourceId(registration: NativeSourceRegistration, targetName?: string) {
        switch (registration.kind) {
            case "playbackClock":
                return "playbackClock";
            case "reducedMotion":
                return "reducedMotion";
            case "frameTimestamp":
                return "frame";
            case "sensor":
                return `sensor:${String(registration.config?.sensorType ?? "unknown")}`;
            case "keyboard":
                return targetName === "state" ? "keyboardState" : "keyboardHeight";
            case "scrollOffset":
                return `scrollOffset:${String(registration.config?.viewTag ?? "unknown")}`;
        }
    }

    private getScoped<Value>(map: Map<string, Value>, scope: AnimatedRuntimeScope): Value;
    private getScoped<Value>(map: Map<string, Map<number, Value>>, scope: AnimatedRuntimeScope): Map<number, Value>;
    private getScoped(
        map: Map<string, Set<string>>,
        scope: AnimatedRuntimeScope,
    ): Set<string>;
    private getScoped(
        map: Map<string, unknown>,
        scope: AnimatedRuntimeScope,
    ): any {
        const key = scopeKey(scope);
        let scoped = map.get(key);
        if (!scoped) {
            scoped = map === this.registeredNativeWorklets ? new Set<string>() : new Map<number, unknown>();
            map.set(key, scoped);
        }
        return scoped;
    }

    private assertScope(scope: AnimatedRuntimeScope) {
        if (this.disposed) {
            throw new Error("This SpotifyPlus worklet adapter has already been disposed.");
        }
        numericGeneration(scope);
        if (scopeKey(scope) !== scopeKey(this.expectedScope)) {
            throw new Error(
                `Animation adapter scope mismatch: expected ${scopeKey(this.expectedScope)}, received ${scopeKey(scope)}.`,
            );
        }
    }

    private requireResult(result: boolean, scope: AnimatedRuntimeScope, operation: string) {
        if (result) {
            return;
        }
        const nativeErrors = this.bridge.drainWorkletErrors();
        const detail = nativeErrors.length > 0 ? `: ${JSON.stringify(nativeErrors[nativeErrors.length - 1])}` : "";
        throw new Error(`Failed to ${operation} for ${scope.scriptId}@${scope.generation}${detail}`);
    }

    private logNativeErrors() {
        for (const error of this.bridge.drainWorkletErrors()) {
            this.logger.error(`UI worklet failure: ${JSON.stringify(error)}`);
        }
    }
}

export function createSpotifyPlusWorkletAdapter(
    bridge: Bridge,
    logger: Logger,
    scope: Readonly<AnimatedRuntimeScope>,
) {
    if (!bridge.isWorkletRuntimeAvailable()) {
        throw new Error("The SpotifyPlus UI worklet runtime is unavailable on this Android ABI.");
    }
    return new SpotifyPlusWorkletAdapter(bridge, logger, scope);
}
