import { isAnimation } from "./animations";
import type {
    AnimatedRuntimeScope,
    Animation,
    DerivedValue,
    MeasuredDimensions,
    NativeAnimationAdapter,
    NativeSourceRegistration,
    NativeViewBinding,
    NativeWorkletRegistration,
    SharedValue,
    WorkletFunctionLike,
    WorkletRuntimeHandle,
} from "./types";
import { SHARED_VALUE_KEY, type SharedValueMarker } from "./types";
import {
    UnsupportedPlatformError,
    serializeWorklet,
} from "./worklets";
import { WORKLET_GLOBALS_MANIFEST } from "./worklet-globals";

let nextRuntimeId = 1;
let nextObjectId = 1;

function createRuntimeId(scope: AnimatedRuntimeScope) {
    const suffix = nextRuntimeId++;
    return `${scope.scriptId}:${scope.generation}:${suffix}`;
}

function getFallbackViewTag(ref: unknown): number | null {
    if (typeof ref === "number" && Number.isFinite(ref)) {
        return ref;
    }
    if (!ref || typeof ref !== "object") {
        return null;
    }
    const candidate = ref as {
        nodeId?: unknown;
        id?: unknown;
        current?: unknown;
        getNativeNodeId?: () => unknown;
        getTag?: () => unknown;
    };
    if (candidate.current && candidate.current !== candidate) {
        return getFallbackViewTag(candidate.current);
    }
    const raw = typeof candidate.getTag === "function"
        ? candidate.getTag()
        : typeof candidate.getNativeNodeId === "function"
            ? candidate.getNativeNodeId()
            : candidate.nodeId ?? candidate.id;
    return typeof raw === "number" && Number.isFinite(raw) ? raw : null;
}

export class MutableValue<Value> implements SharedValue<Value> {
    readonly [SHARED_VALUE_KEY]: SharedValueMarker;
    private current: Value;
    private readonly listeners = new Map<number, (value: Value) => void>();
    private nativeSubscription?: () => void;
    private disposed = false;

    constructor(
        readonly id: number,
        private readonly runtime: AnimatedRuntime,
        initial: Value,
    ) {
        this.current = initial;
        this[SHARED_VALUE_KEY] = Object.freeze({
            version: 2,
            scriptId: runtime.scope.scriptId,
            generation: runtime.scope.generation,
            runtimeId: runtime.runtimeId,
            id,
        });
        runtime.adapter.createMutable(runtime.scope, { id, initial });
    }

    get value(): Value {
        return this.get();
    }

    set value(next: Value | Animation<Value>) {
        this.set(next);
    }

    get() {
        this.assertActive();
        if (this.runtime.adapter.readMutable) {
            this.current = this.runtime.adapter.readMutable<Value>(this.runtime.scope, this.id) as Value;
        }
        return this.current;
    }

    set(next: Value | Animation<Value> | ((current: Value) => Value)) {
        this.assertActive();
        const resolved = typeof next === "function"
            ? (next as (current: Value) => Value)(this.get())
            : next;
        if (!isAnimation(resolved)) {
            this.current = resolved as Value;
        }
        this.runtime.adapter.writeMutable(this.runtime.scope, {
            id: this.id,
            value: resolved as Value | Animation<Value>,
        });
        if (!this.nativeSubscription && !isAnimation(resolved)) {
            this.notify(this.current);
        }
    }

    modify(modifier?: (current: Value) => Value, forceUpdate = true) {
        const current = this.get();
        const next = modifier ? modifier(current) : current;
        if (forceUpdate || !Object.is(current, next)) {
            this.set(next);
        }
    }

    addListener(listenerId: number, listener: (value: Value) => void) {
        this.assertActive();
        this.listeners.set(listenerId, listener);
        this.ensureNativeSubscription();
    }

    removeListener(listenerId: number) {
        this.listeners.delete(listenerId);
        if (this.listeners.size === 0) {
            this.nativeSubscription?.();
            this.nativeSubscription = undefined;
        }
    }

    dispose() {
        if (this.disposed) {
            return;
        }
        this.disposed = true;
        this.nativeSubscription?.();
        this.nativeSubscription = undefined;
        this.listeners.clear();
        this.runtime.releaseMutable(this.id);
    }

    private ensureNativeSubscription() {
        if (this.nativeSubscription || !this.runtime.adapter.subscribeMutable) {
            return;
        }
        this.nativeSubscription = this.runtime.adapter.subscribeMutable<Value>(
            this.runtime.scope,
            this.id,
            value => {
                this.current = value;
                this.notify(value);
            },
        );
    }

    private notify(value: Value) {
        for (const listener of this.listeners.values()) {
            listener(value);
        }
    }

    private assertActive() {
        this.runtime.assertActive();
        if (this.disposed) {
            throw new Error("This SharedValue has been released with its animated runtime.");
        }
    }
}

export class DerivedValueView<Value> implements DerivedValue<Value> {
    readonly [SHARED_VALUE_KEY]: SharedValueMarker & { readonly derived: true };

    constructor(readonly mutable: MutableValue<Value>) {
        this[SHARED_VALUE_KEY] = Object.freeze({
            ...mutable[SHARED_VALUE_KEY],
            derived: true,
        });
    }

    get value(): Value {
        return this.mutable.value;
    }

    get() {
        return this.mutable.get();
    }

    addListener(listenerId: number, listener: (value: Value) => void) {
        this.mutable.addListener(listenerId, listener);
    }

    removeListener(listenerId: number) {
        this.mutable.removeListener(listenerId);
    }
}

export class AnimatedRuntime {
    readonly runtimeId: string;
    private readonly mutableIds = new Set<number>();
    private readonly workletIds = new Set<number>();
    private readonly sourceIds = new Set<number>();
    private readonly customRuntimes = new Set<WorkletRuntimeHandle>();
    private disposed = false;

    constructor(
        readonly adapter: NativeAnimationAdapter,
        readonly scope: AnimatedRuntimeScope,
    ) {
        this.runtimeId = createRuntimeId(scope);
        this.scope = Object.freeze({ ...scope });
        this.adapter.installWorkletGlobals?.(this.scope, WORKLET_GLOBALS_MANIFEST);
    }

    allocateId() {
        this.assertActive();
        return nextObjectId++;
    }

    makeMutable<Value>(initial: Value) {
        const mutable = new MutableValue(this.allocateId(), this, initial);
        this.mutableIds.add(mutable.id);
        return mutable;
    }

    releaseMutable(id: number) {
        if (!this.mutableIds.delete(id)) {
            return;
        }
        this.adapter.releaseMutable?.(this.scope, id);
    }

    registerWorklet(registration: NativeWorkletRegistration) {
        this.assertActive();
        this.assertWorkletGlobals(Object.values(registration.worklets));
        this.adapter.registerWorklet(this.scope, registration);
        this.workletIds.add(registration.id);
    }

    updateWorklet(registration: NativeWorkletRegistration) {
        this.assertActive();
        this.assertWorkletGlobals(Object.values(registration.worklets));
        if (this.adapter.updateWorklet) {
            this.adapter.updateWorklet(this.scope, registration);
        } else {
            this.adapter.unregisterWorklet(this.scope, registration.id);
            this.adapter.registerWorklet(this.scope, registration);
        }
        this.workletIds.add(registration.id);
    }

    unregisterWorklet(id: number) {
        if (!this.workletIds.delete(id)) {
            return;
        }
        this.adapter.unregisterWorklet(this.scope, id);
    }

    setWorkletActive(id: number, active: boolean) {
        if (!this.adapter.setWorkletActive) {
            throw new UnsupportedPlatformError("Frame callback activation", this.adapter.name);
        }
        this.adapter.setWorkletActive(this.scope, id, active);
    }

    registerSource(registration: NativeSourceRegistration) {
        if (!this.adapter.registerSource) {
            throw new UnsupportedPlatformError(`${registration.kind} source`, this.adapter.name);
        }
        this.adapter.registerSource(this.scope, registration);
        this.sourceIds.add(registration.id);
    }

    unregisterSource(id: number) {
        if (!this.sourceIds.delete(id)) {
            return;
        }
        this.adapter.unregisterSource?.(this.scope, id);
    }

    bindView(binding: NativeViewBinding) {
        if (!this.adapter.bindView) {
            throw new UnsupportedPlatformError("Animated view bindings", this.adapter.name);
        }
        this.adapter.bindView(this.scope, binding);
    }

    unbindView(viewTag: number) {
        this.adapter.unbindView?.(this.scope, viewTag);
    }

    resolveViewTag(ref: unknown) {
        return this.adapter.resolveViewTag?.(this.scope, ref) ?? getFallbackViewTag(ref);
    }

    scheduleOnUI<Args extends unknown[]>(worklet: WorkletFunctionLike<(...args: Args) => unknown>, args: Args) {
        const serialized = serializeWorklet(worklet, "scheduleOnUI");
        this.assertWorkletGlobals([serialized]);
        this.adapter.scheduleOnUI(this.scope, serialized, args);
    }

    executeOnUISync<Args extends unknown[], Result>(
        worklet: WorkletFunctionLike<(...args: Args) => Result>,
        args: Args,
    ) {
        if (!this.adapter.executeOnUISync) {
            throw new UnsupportedPlatformError("executeOnUIRuntimeSync", this.adapter.name);
        }
        const serialized = serializeWorklet(worklet, "executeOnUIRuntimeSync");
        this.assertWorkletGlobals([serialized]);
        return this.adapter.executeOnUISync(this.scope, serialized, args);
    }

    scheduleOnRN<Args extends unknown[]>(fn: (...args: Args) => unknown, args: Args) {
        this.adapter.scheduleOnRN(this.scope, fn, args);
    }

    cancelAnimation<Value>(sharedValue: SharedValue<Value>) {
        const id = this.getMutableId(sharedValue);
        this.adapter.cancelAnimation(this.scope, id);
    }

    getTimestamp() {
        return this.adapter.getTimestamp?.(this.scope)
            ?? (typeof performance === "undefined" ? Date.now() : performance.now());
    }

    measure(ref: unknown): MeasuredDimensions | null {
        const viewTag = this.requireViewTag(ref, "measure");
        if (!this.adapter.measure) {
            throw new UnsupportedPlatformError("measure", this.adapter.name);
        }
        return this.adapter.measure(this.scope, viewTag);
    }

    scrollTo(ref: unknown, x: number, y: number, animated: boolean) {
        const viewTag = this.requireViewTag(ref, "scrollTo");
        if (!this.adapter.scrollTo) {
            throw new UnsupportedPlatformError("scrollTo", this.adapter.name);
        }
        this.adapter.scrollTo(this.scope, { viewTag, x, y, animated });
    }

    dispatchCommand(ref: unknown, command: string, args: readonly unknown[] = []) {
        const viewTag = this.requireViewTag(ref, "dispatchCommand");
        if (!this.adapter.dispatchCommand) {
            throw new UnsupportedPlatformError("dispatchCommand", this.adapter.name);
        }
        this.adapter.dispatchCommand(this.scope, viewTag, command, args);
    }

    setNativeProps(ref: unknown, props: Readonly<Record<string, unknown>>) {
        const viewTag = this.requireViewTag(ref, "setNativeProps");
        if (!this.adapter.setNativeProps) {
            throw new UnsupportedPlatformError("setNativeProps", this.adapter.name);
        }
        this.adapter.setNativeProps(this.scope, viewTag, props);
    }

    getViewProp<Value>(ref: unknown, propName: string) {
        const viewTag = this.requireViewTag(ref, "getViewProp");
        if (!this.adapter.getViewProp) {
            throw new UnsupportedPlatformError("getViewProp", this.adapter.name);
        }
        return this.adapter.getViewProp<Value>(this.scope, viewTag, propName);
    }

    createWorkletRuntime(
        name: string,
        initializer?: WorkletFunctionLike<() => void>,
    ) {
        if (!this.adapter.createWorkletRuntime) {
            throw new UnsupportedPlatformError("createWorkletRuntime", this.adapter.name);
        }
        const serializedInitializer = initializer
            ? serializeWorklet(initializer, "createWorkletRuntime")
            : undefined;
        if (serializedInitializer) {
            this.assertWorkletGlobals([serializedInitializer]);
        }
        const handle = this.adapter.createWorkletRuntime(
            this.scope,
            name,
            serializedInitializer,
        );
        this.customRuntimes.add(handle);
        return handle;
    }

    scheduleOnRuntime<Args extends unknown[]>(
        runtime: WorkletRuntimeHandle,
        worklet: WorkletFunctionLike<(...args: Args) => unknown>,
        args: Args,
    ) {
        if (!this.adapter.scheduleOnRuntime) {
            throw new UnsupportedPlatformError("runOnRuntime", this.adapter.name);
        }
        const serialized = serializeWorklet(worklet, "runOnRuntime");
        this.assertWorkletGlobals([serialized]);
        this.adapter.scheduleOnRuntime(
            this.scope,
            runtime,
            serialized,
            args,
        );
    }

    enableLayoutAnimations(enabled = true) {
        if (!this.adapter.configureLayoutAnimations) {
            throw new UnsupportedPlatformError("Layout animations", this.adapter.name);
        }
        this.adapter.configureLayoutAnimations(this.scope, enabled);
    }

    dispose() {
        if (this.disposed) {
            return;
        }
        for (const id of this.workletIds) {
            this.adapter.unregisterWorklet(this.scope, id);
        }
        for (const id of this.sourceIds) {
            this.adapter.unregisterSource?.(this.scope, id);
        }
        for (const id of this.mutableIds) {
            this.adapter.releaseMutable?.(this.scope, id);
        }
        for (const runtime of this.customRuntimes) {
            this.adapter.releaseWorkletRuntime?.(this.scope, runtime);
        }
        this.workletIds.clear();
        this.sourceIds.clear();
        this.mutableIds.clear();
        this.customRuntimes.clear();
        this.adapter.disposeRuntimeScope?.(this.scope);
        this.disposed = true;
    }

    private requireViewTag(ref: unknown, operation: string) {
        const tag = this.resolveViewTag(ref);
        if (tag === null) {
            throw new Error(`${operation} received an animated ref that is not attached to a native view.`);
        }
        return tag;
    }

    private assertWorkletGlobals(worklets: readonly { metadata: { globals?: Readonly<Record<string, string>> } }[]) {
        const requiresGlobals = worklets.some(worklet => Object.keys(worklet.metadata.globals ?? {}).length > 0);
        if (requiresGlobals && this.adapter.capabilities?.workletGlobals !== true) {
            throw new UnsupportedPlatformError("Imported Animated worklet globals", this.adapter.name);
        }
    }

    private getMutableId<Value>(value: SharedValue<Value>) {
        const marker = value?.[SHARED_VALUE_KEY];
        if (!marker || marker.runtimeId !== this.runtimeId) {
            throw new TypeError("The SharedValue belongs to another animation runtime.");
        }
        return marker.id;
    }

    assertActive() {
        if (this.disposed) {
            throw new Error(`Animated runtime ${this.runtimeId} has already been disposed.`);
        }
    }
}

export function createAnimatedRuntime(
    adapter: NativeAnimationAdapter,
    scope: AnimatedRuntimeScope,
) {
    if (!adapter || typeof adapter !== "object") {
        throw new TypeError("createAnimatedRuntime requires a NativeAnimationAdapter.");
    }
    if (!scope?.scriptId || scope.generation === undefined || scope.generation === null) {
        throw new TypeError("createAnimatedRuntime requires an immutable scriptId and generation scope.");
    }
    return new AnimatedRuntime(adapter, scope);
}
