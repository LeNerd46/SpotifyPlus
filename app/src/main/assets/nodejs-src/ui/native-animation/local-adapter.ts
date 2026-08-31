import { isAnimation } from "./animations";
import type {
    AnimatedRuntimeScope,
    NativeAnimationAdapter,
    NativeMutableRequest,
    NativeMutableWrite,
    NativeWorkletRegistration,
    SerializedWorklet,
} from "./types";

interface MutableRecord {
    value: unknown;
    readonly listeners: Set<(value: unknown) => void>;
}

function scopeKey(scope: AnimatedRuntimeScope) {
    return `${scope.scriptId}\u0000${scope.generation}\u0000${scope.surfaceId ?? ""}`;
}

function schedule(callback: () => void) {
    if (typeof queueMicrotask === "function") {
        queueMicrotask(callback);
    } else {
        Promise.resolve().then(callback);
    }
}

/**
 * Deterministic JS fallback used by the SDK and tests. It provides live mutable
 * values and scheduling, but intentionally does not claim UI, event, or view support.
 */
export function createJavaScriptAnimationAdapter(): NativeAnimationAdapter {
    const mutables = new Map<string, MutableRecord>();
    const registrations = new Map<string, NativeWorkletRegistration>();

    const mutableKey = (scope: AnimatedRuntimeScope, id: number) => `${scopeKey(scope)}\u0000mutable:${id}`;
    const registrationKey = (scope: AnimatedRuntimeScope, id: number) => `${scopeKey(scope)}\u0000worklet:${id}`;

    return {
        name: "JavaScript fallback",
        capabilities: {
            worklets: true,
            workletGlobals: true,
        },
        createMutable<Value>(scope: AnimatedRuntimeScope, request: NativeMutableRequest<Value>) {
            mutables.set(mutableKey(scope, request.id), {
                value: request.initial,
                listeners: new Set(),
            });
        },
        readMutable<Value>(scope: AnimatedRuntimeScope, id: number) {
            return mutables.get(mutableKey(scope, id))?.value as Value | undefined;
        },
        writeMutable<Value>(scope: AnimatedRuntimeScope, request: NativeMutableWrite<Value>) {
            const key = mutableKey(scope, request.id);
            const record = mutables.get(key);
            if (!record) {
                return;
            }

            const next = isAnimation(request.value)
                ? request.value.toValue ?? record.value
                : request.value;
            record.value = next;
            for (const listener of record.listeners) {
                listener(next);
            }
        },
        subscribeMutable<Value>(
            scope: AnimatedRuntimeScope,
            id: number,
            listener: (value: Value) => void,
        ) {
            const record = mutables.get(mutableKey(scope, id));
            if (!record) {
                return () => undefined;
            }
            const typedListener = listener as (value: unknown) => void;
            record.listeners.add(typedListener);
            return () => record.listeners.delete(typedListener);
        },
        releaseMutable(scope: AnimatedRuntimeScope, id: number) {
            mutables.delete(mutableKey(scope, id));
        },
        registerWorklet(scope: AnimatedRuntimeScope, registration: NativeWorkletRegistration) {
            registrations.set(registrationKey(scope, registration.id), registration);
        },
        updateWorklet(scope: AnimatedRuntimeScope, registration: NativeWorkletRegistration) {
            registrations.set(registrationKey(scope, registration.id), registration);
        },
        unregisterWorklet(scope: AnimatedRuntimeScope, id: number) {
            registrations.delete(registrationKey(scope, id));
        },
        setWorkletActive() { },
        scheduleOnUI(_scope: AnimatedRuntimeScope, worklet: SerializedWorklet, args: readonly unknown[]) {
            schedule(() => worklet.callable(...args));
        },
        executeOnUISync<Result>(
            _scope: AnimatedRuntimeScope,
            worklet: SerializedWorklet<(...args: any[]) => Result>,
            args: readonly unknown[],
        ) {
            return worklet.callable(...args);
        },
        scheduleOnRN(_scope: AnimatedRuntimeScope, fn: (...args: any[]) => unknown, args: readonly unknown[]) {
            schedule(() => fn(...args));
        },
        cancelAnimation() { },
        getTimestamp() {
            return typeof performance !== "undefined" ? performance.now() : Date.now();
        },
    };
}
