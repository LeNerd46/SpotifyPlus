import React from "react";
import type {
    AnimatedRuntimeScope,
    NativeAnimationAdapter,
    SerializedWorklet,
    WorkletFunctionLike,
} from "./types";
import { UnsupportedPlatformError, serializeWorklet } from "./worklets";

export const GESTURE_MARKER_KEY = "__spotifyPlusGesture" as const;

export enum Directions {
    RIGHT = 1,
    LEFT = 2,
    UP = 4,
    DOWN = 8,
}

export enum MouseButton {
    LEFT = 1,
    RIGHT = 2,
    MIDDLE = 4,
    BUTTON_4 = 8,
    BUTTON_5 = 16,
    ALL = 31,
}

export enum GestureState {
    UNDETERMINED = 0,
    FAILED = 1,
    BEGAN = 2,
    CANCELLED = 3,
    ACTIVE = 4,
    END = 5,
}

export interface GestureTouchEvent {
    id: number;
    x: number;
    y: number;
    absoluteX: number;
    absoluteY: number;
}

export interface GestureEvent {
    handlerTag: number;
    state: GestureState;
    numberOfPointers: number;
    x: number;
    y: number;
    absoluteX: number;
    absoluteY: number;
    velocityX?: number;
    velocityY?: number;
    translationX?: number;
    translationY?: number;
    scale?: number;
    focalX?: number;
    focalY?: number;
    rotation?: number;
    anchorX?: number;
    anchorY?: number;
    duration?: number;
}

export type GestureCallback<Event extends GestureEvent = GestureEvent> = (event: Event) => void;
export type GestureEndCallback<Event extends GestureEvent = GestureEvent> = (event: Event, success: boolean) => void;

type GestureCallbackName =
    | "onBegin"
    | "onStart"
    | "onUpdate"
    | "onChange"
    | "onEnd"
    | "onFinalize"
    | "onTouchesDown"
    | "onTouchesMove"
    | "onTouchesUp"
    | "onTouchesCancelled";

export interface SerializedGesture {
    readonly [GESTURE_MARKER_KEY]: true;
    readonly type: string;
    readonly config: Readonly<Record<string, unknown>>;
    readonly callbacks: Readonly<Record<string, SerializedWorklet>>;
    readonly children?: readonly SerializedGesture[];
    readonly registration?: {
        readonly version: 2;
        readonly scriptId: string;
        readonly generation: string | number;
        readonly id: number;
    };
}

export abstract class BaseGesture<Event extends GestureEvent = GestureEvent> {
    readonly [GESTURE_MARKER_KEY] = true as const;
    readonly gestureConfig: Record<string, unknown> = {};
    readonly gestureCallbacks = new Map<GestureCallbackName, WorkletFunctionLike<any>>();

    protected constructor(readonly type: string) { }

    enabled(value: boolean) {
        this.gestureConfig.enabled = value;
        return this;
    }

    shouldCancelWhenOutside(value: boolean) {
        this.gestureConfig.shouldCancelWhenOutside = value;
        return this;
    }

    hitSlop(value: number | Readonly<Record<string, number>>) {
        this.gestureConfig.hitSlop = value;
        return this;
    }

    runOnJS(value: boolean) {
        this.gestureConfig.runOnJS = value;
        return this;
    }

    withTestId(testId: string) {
        this.gestureConfig.testId = testId;
        return this;
    }

    cancelsTouchesInView(value: boolean) {
        this.gestureConfig.cancelsTouchesInView = value;
        return this;
    }

    simultaneousWithExternalGesture(...gestures: BaseGesture[]) {
        this.gestureConfig.simultaneousWith = gestures;
        return this;
    }

    requireExternalGestureToFail(...gestures: BaseGesture[]) {
        this.gestureConfig.requireToFail = gestures;
        return this;
    }

    blocksExternalGesture(...gestures: BaseGesture[]) {
        this.gestureConfig.blocks = gestures;
        return this;
    }

    onBegin(callback: WorkletFunctionLike<GestureCallback<Event>>) {
        return this.callback("onBegin", callback);
    }

    onStart(callback: WorkletFunctionLike<GestureCallback<Event>>) {
        return this.callback("onStart", callback);
    }

    onUpdate(callback: WorkletFunctionLike<GestureCallback<Event>>) {
        return this.callback("onUpdate", callback);
    }

    onChange(callback: WorkletFunctionLike<GestureCallback<Event>>) {
        return this.callback("onChange", callback);
    }

    onEnd(callback: WorkletFunctionLike<GestureEndCallback<Event>>) {
        return this.callback("onEnd", callback);
    }

    onFinalize(callback: WorkletFunctionLike<GestureEndCallback<Event>>) {
        return this.callback("onFinalize", callback);
    }

    onTouchesDown(callback: WorkletFunctionLike<GestureCallback<Event>>) {
        return this.callback("onTouchesDown", callback);
    }

    onTouchesMove(callback: WorkletFunctionLike<GestureCallback<Event>>) {
        return this.callback("onTouchesMove", callback);
    }

    onTouchesUp(callback: WorkletFunctionLike<GestureCallback<Event>>) {
        return this.callback("onTouchesUp", callback);
    }

    onTouchesCancelled(callback: WorkletFunctionLike<GestureCallback<Event>>) {
        return this.callback("onTouchesCancelled", callback);
    }

    option(name: string, value: unknown) {
        this.gestureConfig[name] = value;
        return this;
    }

    callback(name: GestureCallbackName, callback: WorkletFunctionLike<any>) {
        this.gestureCallbacks.set(name, callback);
        return this;
    }

    serialize(): SerializedGesture {
        const callbacks: Record<string, SerializedWorklet> = {};
        for (const [name, callback] of this.gestureCallbacks) {
            callbacks[name] = serializeWorklet(callback, `Gesture.${this.type}.${name}`);
        }

        return {
            [GESTURE_MARKER_KEY]: true,
            type: this.type,
            config: sanitizeGestureConfig(this.gestureConfig),
            callbacks,
        };
    }
}

class TapGesture extends BaseGesture {
    constructor() { super("tap"); }
    minPointers(value: number) { return this.option("minPointers", value); }
    maxDuration(value: number) { return this.option("maxDuration", value); }
    maxDelay(value: number) { return this.option("maxDelay", value); }
    numberOfTaps(value: number) { return this.option("numberOfTaps", value); }
    maxDistance(value: number) { return this.option("maxDistance", value); }
    maxDeltaX(value: number) { return this.option("maxDeltaX", value); }
    maxDeltaY(value: number) { return this.option("maxDeltaY", value); }
}

class PanGesture extends BaseGesture {
    constructor() { super("pan"); }
    minDistance(value: number) { return this.option("minDistance", value); }
    minPointers(value: number) { return this.option("minPointers", value); }
    maxPointers(value: number) { return this.option("maxPointers", value); }
    activeOffsetX(value: number | readonly [number, number]) { return this.option("activeOffsetX", value); }
    activeOffsetY(value: number | readonly [number, number]) { return this.option("activeOffsetY", value); }
    failOffsetX(value: number | readonly [number, number]) { return this.option("failOffsetX", value); }
    failOffsetY(value: number | readonly [number, number]) { return this.option("failOffsetY", value); }
    averageTouches(value: boolean) { return this.option("averageTouches", value); }
    enableTrackpadTwoFingerGesture(value: boolean) { return this.option("enableTrackpadTwoFingerGesture", value); }
    activateAfterLongPress(value: number) { return this.option("activateAfterLongPress", value); }
    mouseButton(value: MouseButton) { return this.option("mouseButton", value); }
}

class LongPressGesture extends BaseGesture {
    constructor() { super("longPress"); }
    minDuration(value: number) { return this.option("minDuration", value); }
    maxDistance(value: number) { return this.option("maxDistance", value); }
    numberOfPointers(value: number) { return this.option("numberOfPointers", value); }
    mouseButton(value: MouseButton) { return this.option("mouseButton", value); }
}

class FlingGesture extends BaseGesture {
    constructor() { super("fling"); }
    direction(value: Directions) { return this.option("direction", value); }
    numberOfPointers(value: number) { return this.option("numberOfPointers", value); }
    mouseButton(value: MouseButton) { return this.option("mouseButton", value); }
}

class PinchGesture extends BaseGesture {
    constructor() { super("pinch"); }
}

class RotationGesture extends BaseGesture {
    constructor() { super("rotation"); }
}

class NativeGesture extends BaseGesture {
    constructor() { super("native"); }
    shouldActivateOnStart(value: boolean) { return this.option("shouldActivateOnStart", value); }
    disallowInterruption(value: boolean) { return this.option("disallowInterruption", value); }
}

class ManualGesture extends BaseGesture {
    constructor() { super("manual"); }
}

class ComposedGesture extends BaseGesture {
    constructor(type: "race" | "simultaneous" | "exclusive", readonly gestures: readonly BaseGesture[]) {
        super(type);
    }

    override serialize(): SerializedGesture {
        const serialized = super.serialize();
        return { ...serialized, children: this.gestures.map(gesture => gesture.serialize()) };
    }
}

function sanitizeGestureConfig(config: Readonly<Record<string, unknown>>) {
    const output: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(config)) {
        if (Array.isArray(value) && value.every(item => item instanceof BaseGesture)) {
            output[key] = value.map(item => (item as BaseGesture).serialize());
        } else {
            output[key] = value;
        }
    }
    return output;
}

export function isGesture(value: unknown): value is BaseGesture | SerializedGesture {
    return value !== null
        && typeof value === "object"
        && (value as Partial<SerializedGesture>)[GESTURE_MARKER_KEY] === true;
}

export function serializeGesture(value: BaseGesture | SerializedGesture) {
    return value instanceof BaseGesture ? value.serialize() : value;
}

export const Gesture = {
    Tap: () => new TapGesture(),
    Pan: () => new PanGesture(),
    LongPress: () => new LongPressGesture(),
    Fling: () => new FlingGesture(),
    Pinch: () => new PinchGesture(),
    Rotation: () => new RotationGesture(),
    Native: () => new NativeGesture(),
    Manual: () => new ManualGesture(),
    Race: (...gestures: BaseGesture[]) => new ComposedGesture("race", gestures),
    Simultaneous: (...gestures: BaseGesture[]) => new ComposedGesture("simultaneous", gestures),
    Exclusive: (...gestures: BaseGesture[]) => new ComposedGesture("exclusive", gestures),
};

export interface GestureDetectorProps {
    gesture: BaseGesture | SerializedGesture;
    children: React.ReactElement;
}

export type GestureDetectorComponent = React.ComponentType<GestureDetectorProps>;

export function createGestureDetector(
    adapter: NativeAnimationAdapter,
    scope: AnimatedRuntimeScope,
    allocateId: () => number = createStandaloneGestureId,
): GestureDetectorComponent {
    if (adapter.capabilities?.gestures !== true) {
        return function UnsupportedGestureDetector() {
            throw new UnsupportedPlatformError("GestureDetector", adapter.name);
        };
    }

    return function GestureDetector({ gesture, children }: GestureDetectorProps) {
        const serialized = React.useMemo(() => serializeGesture(gesture), [gesture]);
        const registrationId = React.useRef<number | null>(null);
        if (registrationId.current === null) {
            registrationId.current = allocateId();
        }
        const id = registrationId.current;
        const worklets = React.useMemo(() => collectGestureWorklets(serialized), [serialized]);
        React.useEffect(() => {
            adapter.registerWorklet(scope, {
                id,
                kind: "gesture",
                worklets,
                options: { gesture: gestureShape(serialized) },
            });
            return () => adapter.unregisterWorklet(scope, id);
        }, [adapter, scope, id, serialized]);
        const registeredGesture = React.useMemo<SerializedGesture>(() => ({
            ...serialized,
            registration: {
                version: 2,
                scriptId: scope.scriptId,
                generation: scope.generation,
                id,
            },
        }), [serialized, scope.scriptId, scope.generation, id]);
        return React.cloneElement(children, {
            [GESTURE_MARKER_KEY]: registeredGesture,
        } as Record<string, unknown>);
    };
}

export const GestureDetector: GestureDetectorComponent = function HostlessGestureDetector() {
    throw new UnsupportedPlatformError("GestureDetector");
};

export function createGestureModule(adapter: NativeAnimationAdapter, scope: AnimatedRuntimeScope) {
    return {
        Gesture,
        GestureDetector: createGestureDetector(adapter, scope),
        Directions,
        MouseButton,
        GestureState,
    };
}

let nextStandaloneGestureId = -1;

function createStandaloneGestureId() {
    return nextStandaloneGestureId--;
}

function collectGestureWorklets(
    gesture: SerializedGesture,
    prefix = "gesture",
    output: Record<string, SerializedWorklet> = {},
) {
    for (const [name, worklet] of Object.entries(gesture.callbacks)) {
        output[`${prefix}.${name}`] = worklet;
    }
    gesture.children?.forEach((child, index) => {
        collectGestureWorklets(child, `${prefix}.${index}`, output);
    });
    return output;
}

function gestureShape(gesture: SerializedGesture): Readonly<Record<string, unknown>> {
    return {
        type: gesture.type,
        config: bridgeSafeGestureValue(gesture.config),
        callbackNames: Object.keys(gesture.callbacks),
        children: gesture.children?.map(gestureShape),
    };
}

function bridgeSafeGestureValue(value: unknown): unknown {
    if (Array.isArray(value)) {
        return value.map(bridgeSafeGestureValue);
    }
    if (!value || typeof value !== "object") {
        return value;
    }
    if ((value as Partial<SerializedGesture>)[GESTURE_MARKER_KEY] === true) {
        return gestureShape(value as SerializedGesture);
    }
    const output: Record<string, unknown> = {};
    for (const [key, child] of Object.entries(value)) {
        output[key] = bridgeSafeGestureValue(child);
    }
    return output;
}
