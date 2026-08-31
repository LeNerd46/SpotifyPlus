import React from "react";
import {
    startNativeAnimation,
    stopNativeAnimation,
    updateNodeProps,
} from "./renderer";

const useAnimatedBindingEffect = React.useLayoutEffect ?? React.useEffect;

export type EndCallback = (result: { finished: boolean }) => void;
export type EasingFunction = (value: number) => number;
export type ExtrapolateType = "extend" | "clamp" | "identity";
export type AnimatedNodeLike = AnimatedNode;
export type ListenerCallback = (state: { value: number }) => void;
export type AnimationConfig = {
    useNativeDriver?: boolean;
    isInteraction?: boolean;
    delay?: number;
};
export type TimingAnimationConfig = AnimationConfig & {
    toValue: number | Value;
    duration?: number;
    easing?: EasingFunction;
};
export type SpringAnimationConfig = AnimationConfig & {
    toValue: number | Value;
    velocity?: number;
    tension?: number;
    friction?: number;
    stiffness?: number;
    damping?: number;
    mass?: number;
    overshootClamping?: boolean;
    restSpeedThreshold?: number;
    restDisplacementThreshold?: number;
    duration?: number;
};
export type DecayAnimationConfig = AnimationConfig & {
    velocity: number;
    deceleration?: number;
};
export type InterpolationConfig = {
    inputRange: number[];
    outputRange: Array<number | string>;
    easing?: EasingFunction;
    extrapolate?: ExtrapolateType;
    extrapolateLeft?: ExtrapolateType;
    extrapolateRight?: ExtrapolateType;
};
export type CompositeAnimation = {
    start(callback?: EndCallback): void;
    stop(): void;
    reset(): void;
};

type Binding = { id: number; nodeId: number; prop: string; node: AnimatedNode };
type RunningAnimation = { stop(): void; reset?(): void };

let nextListenerId = 1;
let nextBindingId = 1;
let nextNativeAnimationId = 1;
const raf =
    typeof requestAnimationFrame === "function"
        ? requestAnimationFrame
        : (callback: (time: number) => void) =>
            setTimeout(() => callback(Date.now()), 16) as unknown as number;
const caf =
    typeof cancelAnimationFrame === "function"
        ? cancelAnimationFrame
        : (id: number) =>
            clearTimeout(id as unknown as ReturnType<typeof setTimeout>);

export abstract class AnimatedNode {
    readonly __isSpotifyPlusAnimatedNode = true;
    protected bindings = new Set<Binding>();
    protected children = new Set<AnimatedNode>();
    protected listeners = new Map<string, ListenerCallback>();
    abstract __getValue(): any;
    __attachChild(child: AnimatedNode) {
        this.children.add(child);
    }
    __detachChild(child: AnimatedNode) {
        this.children.delete(child);
    }
    __addBinding(binding: Binding) {
        this.bindings.add(binding);
    }
    __removeBinding(binding: Binding) {
        this.bindings.delete(binding);
    }
    __getValueAtRoot(root: Value, rootValue: number): any {
        return this.__getValue();
    }
    __collectBindings(out: Binding[]) {
        for (const binding of this.bindings) out.push(binding);
        for (const child of this.children) child.__collectBindings(out);
    }
    __notify() {
        const value = Number(this.__getValue());
        for (const listener of this.listeners.values()) listener({ value });
        for (const child of this.children) child.__notify();
    }
    addListener(callback: ListenerCallback): string {
        const id = String(nextListenerId++);
        this.listeners.set(id, callback);
        return id;
    }
    removeListener(id: string) {
        this.listeners.delete(id);
    }
    removeAllListeners() {
        this.listeners.clear();
    }
    interpolate(config: InterpolationConfig) {
        return new AnimatedInterpolation(this, config);
    }
}

export class Value extends AnimatedNode {
    private value: number;
    private offset = 0;
    private animation: RunningAnimation | null = null;
    constructor(value: number) {
        super();
        this.value = value;
    }
    __getValue() {
        return this.value + this.offset;
    }
    __getOffset() {
        return this.offset;
    }
    __setValue(value: number, notify = true) {
        this.value = value;
        if (notify) this.__notify();
    }
    __getValueAtRoot(root: Value, rootValue: number): any {
        return this === root ? rootValue + this.offset : this.__getValue();
    }
    __stopCurrentAnimation() {
        if (!this.animation) return;
        const animation = this.animation;
        this.animation = null;
        animation.stop();
    }
    __setRunningAnimation(animation: RunningAnimation | null) {
        this.animation = animation;
    }
    setValue(value: number) {
        this.__stopCurrentAnimation();
        this.__setValue(value);
    }
    setOffset(offset: number) {
        this.offset = offset;
        this.__notify();
    }
    flattenOffset() {
        this.value += this.offset;
        this.offset = 0;
        this.__notify();
    }
    extractOffset() {
        this.offset += this.value;
        this.value = 0;
        this.__notify();
    }
    stopAnimation(callback?: (value: number) => void) {
        this.__stopCurrentAnimation();
        callback?.(this.__getValue());
    }
    resetAnimation(callback?: (value: number) => void) {
        this.__stopCurrentAnimation();
        this.value = 0;
        this.offset = 0;
        this.__notify();
        callback?.(this.__getValue());
    }
}

class AnimatedInterpolation extends AnimatedNode {
    constructor(
        private parent: AnimatedNode,
        private config: InterpolationConfig,
    ) {
        super();
        parent.__attachChild(this);
    }
    __getValue() {
        return interpolateValue(Number(this.parent.__getValue()), this.config);
    }
    __getValueAtRoot(root: Value, rootValue: number): any {
        return interpolateValue(
            Number(this.parent.__getValueAtRoot(root, rootValue)),
            this.config,
        );
    }
}

class AnimatedBinaryOp extends AnimatedNode {
    constructor(
        private left: number | AnimatedNode,
        private right: number | AnimatedNode,
        private op: (a: number, b: number) => number,
    ) {
        super();
        if (isAnimatedNode(left)) left.__attachChild(this);
        if (isAnimatedNode(right)) right.__attachChild(this);
    }
    __getValue() {
        return this.op(resolveNumber(this.left), resolveNumber(this.right));
    }
    __getValueAtRoot(root: Value, rootValue: number): any {
        return this.op(
            resolveNumberAtRoot(this.left, root, rootValue),
            resolveNumberAtRoot(this.right, root, rootValue),
        );
    }
}

export class ValueXY {
    x: Value;
    y: Value;
    constructor(value?: { x?: number; y?: number }) {
        this.x = new Value(value?.x ?? 0);
        this.y = new Value(value?.y ?? 0);
    }
    setValue(value: { x: number; y: number }) {
        this.x.setValue(value.x);
        this.y.setValue(value.y);
    }
    setOffset(value: { x: number; y: number }) {
        this.x.setOffset(value.x);
        this.y.setOffset(value.y);
    }
    flattenOffset() {
        this.x.flattenOffset();
        this.y.flattenOffset();
    }
    extractOffset() {
        this.x.extractOffset();
        this.y.extractOffset();
    }
    stopAnimation(callback?: (value: { x: number; y: number }) => void) {
        this.x.stopAnimation();
        this.y.stopAnimation();
        callback?.({ x: this.x.__getValue(), y: this.y.__getValue() });
    }
    getLayout() {
        return { left: this.x, top: this.y };
    }
    getTranslateTransform() {
        return [{ translateX: this.x }, { translateY: this.y }];
    }
}

export const Easing = {
    linear: (t: number) => t,
    ease: (t: number) => (t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2),
    quad: (t: number) => t * t,
    cubic: (t: number) => t * t * t,
    sin: (t: number) => 1 - Math.cos((t * Math.PI) / 2),
    circle: (t: number) => 1 - Math.sqrt(1 - Math.min(1, t * t)),
    exp: (t: number) => (t === 0 ? 0 : Math.pow(2, 10 * t - 10)),
    back:
        (s = 1.70158) =>
            (t: number) =>
                t * t * ((s + 1) * t - s),
    bounce: (t: number) => {
        const n1 = 7.5625;
        const d1 = 2.75;
        if (t < 1 / d1) return n1 * t * t;
        if (t < 2 / d1) return n1 * (t -= 1.5 / d1) * t + 0.75;
        if (t < 2.5 / d1) return n1 * (t -= 2.25 / d1) * t + 0.9375;
        return n1 * (t -= 2.625 / d1) * t + 0.984375;
    },
    in: (easing: EasingFunction) => (t: number) => easing(t),
    out: (easing: EasingFunction) => (t: number) => 1 - easing(1 - t),
    inOut: (easing: EasingFunction) => (t: number) =>
        t < 0.5 ? easing(t * 2) / 2 : 1 - easing((1 - t) * 2) / 2,
};

function isAnimatedNode(value: unknown): value is AnimatedNode {
    return (
        !!value &&
        typeof value === "object" &&
        (value as any).__isSpotifyPlusAnimatedNode === true
    );
}
function resolveNumber(value: number | AnimatedNode) {
    return isAnimatedNode(value) ? Number(value.__getValue()) : Number(value);
}
function resolveNumberAtRoot(
    value: number | AnimatedNode,
    root: Value,
    rootValue: number,
) {
    return isAnimatedNode(value)
        ? Number(value.__getValueAtRoot(root, rootValue))
        : Number(value);
}
function normalizeEasingName(easing?: EasingFunction) {
    if (!easing) return "easeInOut";
    if (easing === Easing.linear) return "linear";
    if (easing === Easing.ease) return "easeInOut";
    if (easing === Easing.bounce) return "bounce";
    return "easeInOut";
}
function clamp01(value: number) {
    return Math.max(0, Math.min(1, value));
}
function lerp(a: number, b: number, t: number) {
    return a + (b - a) * t;
}
function frameLoop(step: (time: number) => boolean) {
    let frame = 0;
    const tick = (time: number) => {
        if (step(time)) frame = raf(tick);
    };
    frame = raf(tick);
    return () => caf(frame);
}

function interpolateValue(
    input: number,
    config: InterpolationConfig,
): number | string {
    const inputRange = config.inputRange;
    const outputRange = config.outputRange;
    if (inputRange.length !== outputRange.length || inputRange.length < 2)
        return outputRange[0] ?? input;
    let index = 1;
    while (index < inputRange.length - 1 && input > inputRange[index]) index++;
    const inputMin = inputRange[index - 1];
    const inputMax = inputRange[index];
    const outputMin = outputRange[index - 1];
    const outputMax = outputRange[index];
    const leftMode = config.extrapolateLeft ?? config.extrapolate ?? "extend";
    const rightMode = config.extrapolateRight ?? config.extrapolate ?? "extend";
    if (input < inputMin) {
        if (leftMode === "identity") return input;
        if (leftMode === "clamp") input = inputMin;
    }
    if (input > inputMax) {
        if (rightMode === "identity") return input;
        if (rightMode === "clamp") input = inputMax;
    }
    const rawT =
        inputMax === inputMin ? 0 : (input - inputMin) / (inputMax - inputMin);
    const t = config.easing ? config.easing(rawT) : rawT;
    if (typeof outputMin === "number" && typeof outputMax === "number")
        return lerp(outputMin, outputMax, t);
    return interpolateString(String(outputMin), String(outputMax), t);
}

function interpolateString(from: string, to: string, t: number) {
    const fromNumber = parseFloat(from);
    const toNumber = parseFloat(to);
    const suffix =
        to.replace(String(toNumber), "") || from.replace(String(fromNumber), "");
    if (Number.isFinite(fromNumber) && Number.isFinite(toNumber))
        return `${lerp(fromNumber, toNumber, t)}${suffix}`;
    return t < 1 ? from : to;
}
function toNativeNumber(value: unknown): number | null {
    if (typeof value === "number") return value;
    if (typeof value !== "string") return null;
    const text = value.trim().toLowerCase();
    if (text.endsWith("deg")) return parseFloat(text);
    if (text.endsWith("rad")) return (parseFloat(text) * 180) / Math.PI;
    const numeric = parseFloat(text);
    return Number.isFinite(numeric) ? numeric : null;
}
function isNativeProp(prop: string) {
    return [
        "alpha",
        "opacity",
        "translationX",
        "translationY",
        "translationZ",
        "translateX",
        "translateY",
        "translateZ",
        "scale",
        "scaleX",
        "scaleY",
        "rotate",
        "rotation",
        "rotationX",
        "rotationY",
        "elevation",
    ].includes(prop);
}

function makeAnimation(
    startFn: (finish: EndCallback) => RunningAnimation,
): CompositeAnimation {
    let running: RunningAnimation | null = null;
    return {
        start(callback?: EndCallback) {
            running = startFn((result) => {
                running = null;
                callback?.(result);
            });
        },
        stop() {
            if (!running) return;
            const current = running;
            running = null;
            current.stop();
        },
        reset() {
            running?.reset?.();
        },
    };
}

function tryStartNativeTiming(
    value: Value,
    config: TimingAnimationConfig,
    type: "timing" | "spring",
    callback: EndCallback,
): RunningAnimation | null {
    if (!config.useNativeDriver) return null;
    const toValue = resolveNumber(config.toValue);
    const fromValue = value.__getValue();
    const bindings: Binding[] = [];
    value.__collectBindings(bindings);
    const nativeBindings = bindings.filter((binding) =>
        isNativeProp(binding.prop),
    );
    if (nativeBindings.length === 0) return null;
    const ids: number[] = [];
    const byNode = new Map<
        number,
        Array<{ property: string; from: number; to: number }>
    >();
    for (const binding of nativeBindings) {
        const from = toNativeNumber(
            binding.node.__getValueAtRoot(value, fromValue),
        );
        const to = toNativeNumber(binding.node.__getValueAtRoot(value, toValue));
        if (from == null || to == null) continue;
        const tracks = byNode.get(binding.nodeId) ?? [];
        tracks.push({
            property: binding.prop === "opacity" ? "alpha" : binding.prop === "rotate" ? "rotation" : binding.prop,
            from,
            to,
        });
        byNode.set(binding.nodeId, tracks);
    }
    if (byNode.size === 0) return null;
    for (const [nodeId, tracks] of byNode) {
        const animationId = nextNativeAnimationId++;
        ids.push(animationId);
        startNativeAnimation(nodeId, {
            animationId,
            type,
            duration: config.duration ?? 300,
            delay: config.delay ?? 0,
            easing: normalizeEasingName(config.easing),
            tracks,
        });
    }
    value.__setRunningAnimation({
        stop: () => {
            for (const id of ids) stopNativeAnimation(id);
        },
    });
    const timeout = setTimeout(
        () => {
            value.__setRunningAnimation(null);
            value.__setValue(toValue);
            callback({ finished: true });
        },
        (config.duration ?? 300) + (config.delay ?? 0) + 20,
    );
    return {
        stop: () => {
            clearTimeout(timeout);
            for (const id of ids) stopNativeAnimation(id);
            callback({ finished: false });
        },
    };
}

export function timing(
    value: Value,
    config: TimingAnimationConfig,
): CompositeAnimation {
    return makeAnimation((callback) => {
        value.__stopCurrentAnimation();
        const native = tryStartNativeTiming(value, config, "timing", callback);
        if (native) {
            value.__setRunningAnimation(native);
            return native;
        }
        const from = value.__getValue();
        const to = resolveNumber(config.toValue);
        const duration = Math.max(0, config.duration ?? 300);
        const easing = config.easing ?? Easing.inOut(Easing.ease);
        let stopped = false;
        let cancel = () => { };
        const startTime = Date.now() + (config.delay ?? 0);
        cancel = frameLoop((time) => {
            if (stopped) return false;
            if (time < startTime) return true;
            const t = duration === 0 ? 1 : clamp01((time - startTime) / duration);
            value.__setValue(lerp(from, to, easing(t)) - value.__getOffset());
            if (t >= 1) {
                value.__setRunningAnimation(null);
                callback({ finished: true });
                return false;
            }
            return true;
        });
        const animation = {
            stop: () => {
                stopped = true;
                cancel();
                callback({ finished: false });
            },
        };
        value.__setRunningAnimation(animation);
        return animation;
    });
}

export function spring(
    value: Value,
    config: SpringAnimationConfig,
): CompositeAnimation {
    return makeAnimation((callback) => {
        value.__stopCurrentAnimation();
        const native = tryStartNativeTiming(
            value,
            {
                ...config,
                duration: config.duration ?? 500,
                easing: Easing.out(Easing.back(1.15)),
            } as TimingAnimationConfig,
            "spring",
            callback,
        );
        if (native) {
            value.__setRunningAnimation(native);
            return native;
        }
        const to = resolveNumber(config.toValue);
        let position = value.__getValue();
        let velocity = config.velocity ?? 0;
        const stiffness = config.stiffness ?? config.tension ?? 100;
        const damping = config.damping ?? config.friction ?? 14;
        const mass = config.mass ?? 1;
        const restSpeed = config.restSpeedThreshold ?? 0.05;
        const restDistance = config.restDisplacementThreshold ?? 0.05;
        let lastTime = Date.now();
        let stopped = false;
        const cancel = frameLoop((time) => {
            if (stopped) return false;
            const dt = Math.min(0.064, Math.max(0.001, (time - lastTime) / 1000));
            lastTime = time;
            const force = -stiffness * (position - to);
            const dampingForce = -damping * velocity;
            const acceleration = (force + dampingForce) / mass;
            velocity += acceleration * dt;
            position += velocity * dt;
            if (
                config.overshootClamping &&
                ((to >= value.__getValue() && position > to) ||
                    (to <= value.__getValue() && position < to))
            )
                position = to;
            value.__setValue(position - value.__getOffset());
            if (
                Math.abs(velocity) <= restSpeed &&
                Math.abs(to - position) <= restDistance
            ) {
                value.__setValue(to - value.__getOffset());
                value.__setRunningAnimation(null);
                callback({ finished: true });
                return false;
            }
            return true;
        });
        const animation = {
            stop: () => {
                stopped = true;
                cancel();
                callback({ finished: false });
            },
        };
        value.__setRunningAnimation(animation);
        return animation;
    });
}

export function decay(
    value: Value,
    config: DecayAnimationConfig,
): CompositeAnimation {
    return makeAnimation((callback) => {
        value.__stopCurrentAnimation();
        let velocity = config.velocity;
        const deceleration = config.deceleration ?? 0.997;
        let position = value.__getValue();
        let lastTime = Date.now();
        let stopped = false;
        const cancel = frameLoop((time) => {
            if (stopped) return false;
            const dtMs = Math.max(1, time - lastTime);
            lastTime = time;
            position += (velocity * dtMs) / 16.667;
            velocity *= Math.pow(deceleration, dtMs);
            value.__setValue(position - value.__getOffset());
            if (Math.abs(velocity) < 0.05) {
                value.__setRunningAnimation(null);
                callback({ finished: true });
                return false;
            }
            return true;
        });
        const animation = {
            stop: () => {
                stopped = true;
                cancel();
                callback({ finished: false });
            },
        };
        value.__setRunningAnimation(animation);
        return animation;
    });
}

export function delay(time: number): CompositeAnimation {
    let timeout: ReturnType<typeof setTimeout> | null = null;
    return {
        start(callback?: EndCallback) {
            timeout = setTimeout(() => callback?.({ finished: true }), time);
        },
        stop() {
            if (timeout) clearTimeout(timeout);
            timeout = null;
        },
        reset() {
            this.stop();
        },
    };
}
export function sequence(animations: CompositeAnimation[]): CompositeAnimation {
    let current = 0;
    let stopped = false;
    return {
        start(callback?: EndCallback) {
            const run = () => {
                if (stopped) return callback?.({ finished: false });
                if (current >= animations.length) return callback?.({ finished: true });
                animations[current++].start((result) =>
                    result.finished ? run() : callback?.({ finished: false }),
                );
            };
            run();
        },
        stop() {
            stopped = true;
            animations[current]?.stop();
        },
        reset() {
            current = 0;
            stopped = false;
            for (const animation of animations) animation.reset();
        },
    };
}
export function parallel(
    animations: CompositeAnimation[],
    config?: { stopTogether?: boolean },
): CompositeAnimation {
    let done = 0;
    let stopped = false;
    const composite: CompositeAnimation = {
        start(callback?: EndCallback) {
            done = 0;
            stopped = false;
            if (animations.length === 0) return callback?.({ finished: true });
            animations.forEach((animation) => animation.start((result) => {
                if (!result.finished && config?.stopTogether !== false) composite.stop();
                done++;
                if (done === animations.length && !stopped) callback?.({ finished: result.finished });
            }));
        },
        stop() {
            stopped = true;
            for (const animation of animations) animation.stop();
        },
        reset() {
            done = 0;
            stopped = false;
            for (const animation of animations) animation.reset();
        },
    };
    return composite;
}
export function stagger(
    time: number,
    animations: CompositeAnimation[],
): CompositeAnimation {
    return parallel(
        animations.map((animation, index) =>
            sequence([delay(time * index), animation]),
        ),
    );
}
export function loop(
    animation: CompositeAnimation,
    config?: { iterations?: number },
): CompositeAnimation {
    let count = 0;
    let stopped = false;
    const iterations = config?.iterations ?? -1;
    return {
        start(callback?: EndCallback) {
            const run = () => {
                if (stopped) return callback?.({ finished: false });
                if (iterations !== -1 && count >= iterations)
                    return callback?.({ finished: true });
                count++;
                animation.reset();
                animation.start((result) =>
                    result.finished ? run() : callback?.({ finished: false }),
                );
            };
            run();
        },
        stop() {
            stopped = true;
            animation.stop();
        },
        reset() {
            count = 0;
            stopped = false;
            animation.reset();
        },
    };
}
export function add(a: number | AnimatedNode, b: number | AnimatedNode) {
    return new AnimatedBinaryOp(a, b, (x, y) => x + y);
}
export function subtract(a: number | AnimatedNode, b: number | AnimatedNode) {
    return new AnimatedBinaryOp(a, b, (x, y) => x - y);
}
export function multiply(a: number | AnimatedNode, b: number | AnimatedNode) {
    return new AnimatedBinaryOp(a, b, (x, y) => x * y);
}
export function divide(a: number | AnimatedNode, b: number | AnimatedNode) {
    return new AnimatedBinaryOp(a, b, (x, y) => (y === 0 ? 0 : x / y));
}
export function modulo(a: number | AnimatedNode, b: number | AnimatedNode) {
    return new AnimatedBinaryOp(a, b, (x, y) => (y === 0 ? 0 : x % y));
}
export function event(
    mapping: any[],
    config?: { listener?: (...args: any[]) => void },
) {
    return (...args: any[]) => {
        applyEventMapping(mapping[0], args[0]);
        config?.listener?.(...args);
    };
}
function applyEventMapping(mapping: any, payload: any) {
    if (!mapping || !payload) return;
    for (const key of Object.keys(mapping)) {
        const target = mapping[key];
        const value = payload[key];
        if (target instanceof Value && typeof value === "number")
            target.setValue(value);
        else if (isPlainObject(target)) applyEventMapping(target, value);
    }
}
function isPlainObject(value: unknown): value is Record<string, any> {
    return !!value && typeof value === "object" && !Array.isArray(value);
}

function flattenStyle(style: any): Record<string, any> {
    if (!style) return {};
    if (Array.isArray(style))
        return style.reduce(
            (out, entry) => Object.assign(out, flattenStyle(entry)),
            {},
        );
    return isPlainObject(style) ? { ...style } : {};
}
function normalizeAnimatedPropKey(key: string) {
    if (key === "opacity") return "alpha";
    if (key === "translateX") return "translationX";
    if (key === "translateY") return "translationY";
    if (key === "translateZ") return "translationZ";
    if (key === "rotate") return "rotation";
    if (key === "rotateX") return "rotationX";
    if (key === "rotateY") return "rotationY";
    return key;
}
function mapAnimatedPropKey(key: string) {
    if (key === "opacity") return "alpha";
    if (key === "translateX") return "translationX";
    if (key === "translateY") return "translationY";
    if (key === "translateZ") return "translationZ";
    if (key === "rotate") return "rotation";
    if (key === "rotateX") return "rotationX";
    if (key === "rotateY") return "rotationY";
    return key;
}

function expandAnimatedPropKeys(key: string) {
    if (key === "scale") return ["scaleX", "scaleY"];
    return [mapAnimatedPropKey(key)];
}

function normalizeAnimatedValue(prop: string, value: any) {
    if (prop === "rotation" || prop === "rotationX" || prop === "rotationY") return toNativeNumber(value) ?? value;
    return value;
}
function resolveAnimatedStyle(style: any): any {
    if (!style) return style;
    if (Array.isArray(style)) return style.map(resolveAnimatedStyle);
    const input = flattenStyle(style);
    const output: Record<string, any> = {};
    for (const [key, value] of Object.entries(input)) {
        if (key === "transform" && Array.isArray(value)) {
            output.transform = value.map((item) => {
                const entry: Record<string, any> = {};
                for (const [transformKey, transformValue] of Object.entries(item as Record<string, any>)) {
                    const mappedKey = mapAnimatedPropKey(transformKey);
                    entry[mappedKey] = isAnimatedNode(transformValue) ? normalizeAnimatedValue(mappedKey, transformValue.__getValue()) : transformValue;
                }
                return entry;
            });
        }
        else
            output[key] = isAnimatedNode(value)
                ? normalizeAnimatedValue(key, value.__getValue())
                : value;
    }
    return output;
}
function collectAnimatedStyleBindings(style: any, out: Array<{ prop: string; node: AnimatedNode }>) {
    const input = flattenStyle(style);
    for (const [key, value] of Object.entries(input)) {
        if (key === "transform" && Array.isArray(value)) {
            for (const item of value) {
                for (const [transformKey, transformValue] of Object.entries(item as Record<string, any>)) {
                    if (!isAnimatedNode(transformValue)) continue;
                    for (const prop of expandAnimatedPropKeys(transformKey)) out.push({ prop, node: transformValue });
                }
            }
        } else if (isAnimatedNode(value)) {
            for (const prop of expandAnimatedPropKeys(key)) out.push({ prop, node: value });
        }
    }
}

export type AnimatedComponentProps<Props> = Omit<Props, "style"> & {
    style?: any;
};

export function createAnimatedComponent<P extends { style?: any }>(
    Component: React.ComponentType<P>,
): React.ComponentType<AnimatedComponentProps<P>> {
    const AnimatedComponent = React.forwardRef<any, P>((props, forwardedRef) => {
        const hostRef = React.useRef<any>(null);
        const bindingsRef = React.useRef<Binding[]>([]);
        const propsRef = React.useRef(props);
        propsRef.current = props;
        React.useImperativeHandle(forwardedRef, () => hostRef.current);
        useAnimatedBindingEffect(() => {
            const nodeId = hostRef.current?.getNativeNodeId?.()
                ?? hostRef.current?.nodeId
                ?? hostRef.current?.id;
            if (typeof nodeId !== "number") return;
            for (const binding of bindingsRef.current) binding.node.__removeBinding(binding);
            bindingsRef.current = [];
            const bindingSpecs: Array<{ prop: string; node: AnimatedNode }> = [];
            collectAnimatedStyleBindings(propsRef.current.style, bindingSpecs);
            const listenerIds: Array<{ node: AnimatedNode; id: string }> = [];
            const initialPatch: Record<string, any> = {};
            for (const spec of bindingSpecs) {
                const binding: Binding = { id: nextBindingId++, nodeId, prop: spec.prop, node: spec.node };
                spec.node.__addBinding(binding);
                bindingsRef.current.push(binding);
                initialPatch[spec.prop] = normalizeAnimatedValue(spec.prop, spec.node.__getValue());
                const listenerId = spec.node.addListener(() => updateNodeProps(nodeId, { [spec.prop]: normalizeAnimatedValue(spec.prop, spec.node.__getValue()) }));
                listenerIds.push({ node: spec.node, id: listenerId });
            }
            if (Object.keys(initialPatch).length > 0) updateNodeProps(nodeId, initialPatch);
            return () => {
                for (const binding of bindingsRef.current) binding.node.__removeBinding(binding);
                for (const listener of listenerIds) listener.node.removeListener(listener.id);
                bindingsRef.current = [];
            };
        });
        const resolvedProps = {
            ...(props as any),
            style: resolveAnimatedStyle((props as any).style),
            ref: hostRef,
        };
        return React.createElement(Component as any, resolvedProps);
    });
    AnimatedComponent.displayName = `Animated.${(Component as any).displayName ?? Component.name ?? "Component"}`;
    return AnimatedComponent as unknown as React.ComponentType<AnimatedComponentProps<P>>;
}

export function useAnimatedValue(initialValue: number) {
    const ref = React.useRef<Value | null>(null);
    if (ref.current == null) ref.current = new Value(initialValue);
    return ref.current;
}

const AnimatedCore = {
    Value,
    ValueXY,
    timing,
    spring,
    decay,
    delay,
    sequence,
    parallel,
    stagger,
    loop,
    add,
    subtract,
    multiply,
    divide,
    modulo,
    event,
    createAnimatedComponent,
    useAnimatedValue,
    Easing,
};

export type AnimatedHostComponents = Readonly<Record<string, React.ComponentType<any> | undefined>>;
export type AnimatedComponents<Hosts extends AnimatedHostComponents> = {
    readonly [Name in keyof Hosts]: Hosts[Name] extends React.ComponentType<infer Props>
        ? React.ComponentType<AnimatedComponentProps<Props>>
        : never;
};

export function createAnimatedModule<const Hosts extends AnimatedHostComponents = {}>(
    hosts: Hosts = {} as Hosts,
): Readonly<typeof AnimatedCore & AnimatedComponents<Hosts>> {
    const components = Object.fromEntries(
        Object.entries(hosts)
            .filter((entry): entry is [string, React.ComponentType<any>] => entry[1] != null)
            .map(([name, Component]) => [name, createAnimatedComponent(Component)]),
    );
    return Object.freeze({
        ...AnimatedCore,
        ...components,
    }) as Readonly<typeof AnimatedCore & AnimatedComponents<Hosts>>;
}

const Animated = Object.freeze(AnimatedCore);
export default Animated;
