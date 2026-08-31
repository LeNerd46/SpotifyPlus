import React from 'react';
import Reconciler from 'react-reconciler';
import { DefaultEventPriority } from 'react-reconciler/constants';
import {
    getNativeAssetRegistration,
    NativeAssetRegistration,
} from '../core/extension-assets';

const HOST_CONTEXT = {};
let currentUpdatePriority = DefaultEventPriority;

type RegisteredEventHandler = {
    eventName: string;
    handler: Function;
};

type AnimatedEventMarker = {
    version: 2;
    kind: 'event';
    scriptId: string;
    generation: string | number;
    runtimeId: string | number;
    id: string | number;
};

export type MeasureCallback = (
    x: number,
    y: number,
    width: number,
    height: number,
    pageX: number,
    pageY: number,
) => void;

export type MeasureInWindowCallback = (
    x: number,
    y: number,
    width: number,
    height: number,
) => void;

export type ScrollToOptions = { x?: number; y?: number; animated?: boolean };
export type ScrollToEndOptions = { animated?: boolean };
export type ScrollToIndexOptions = { index: number; animated?: boolean; viewOffset?: number; viewPosition?: number };
export type ScrollToOffsetOptions = { offset: number; animated?: boolean };

export interface NativeComponentRef {
    readonly nodeId: number;
    readonly type: string;
    readonly mounted: boolean;

    getNativeNodeId(): number;
    setNativeProps(props: Record<string, any>): void;

    focus(): void;
    blur(): void;

    measure(callback: MeasureCallback): void;
    measureInWindow(callback: MeasureInWindowCallback): void;

    scrollTo(options?: ScrollToOptions | number, y?: number, animated?: boolean): void;
    scrollToEnd(options?: ScrollToEndOptions): void;
    flashScrollIndicators(): void;

    dispatchCommand(command: string, args?: Record<string, any>, callback?: (payload: any) => void): void;
    command(command: string, args?: Record<string, any>, callback?: (payload: any) => void): void;
}

const eventHandlers = new Map<number, RegisteredEventHandler>();
const nodeEventIds = new Map<number, Set<number>>();
const eventPreviewIds = new WeakMap<Function, number>();
let nextEventId = 1;
let nextEventPreviewId = 1;

function getEventPreviewId(handler: Function): number {
    const existing = eventPreviewIds.get(handler);
    if (existing !== undefined) return existing;
    const id = nextEventPreviewId++;
    eventPreviewIds.set(handler, id);
    return id;
}

function registerEventHandler(nodeId: number, eventName: string, handler: Function): number {
    const id = nextEventId++;
    eventHandlers.set(id, { eventName, handler });

    let ids = nodeEventIds.get(nodeId);
    if (!ids) {
        ids = new Set<number>();
        nodeEventIds.set(nodeId, ids);
    }

    ids.add(id);
    return id;
}

function registerWorkletEventHandler(nodeId: number): number {
    const id = nextEventId++;
    let ids = nodeEventIds.get(nodeId);
    if (!ids) {
        ids = new Set<number>();
        nodeEventIds.set(nodeId, ids);
    }
    ids.add(id);
    return id;
}

function getAnimatedEventMarker(value: unknown): AnimatedEventMarker | null {
    if (typeof value !== 'function') return null;
    const marker = (value as any).__spotifyPlusAnimated;
    if (!marker || marker.version !== 2 || marker.kind !== 'event') return null;
    if (typeof marker.scriptId !== 'string' || marker.scriptId.length === 0) return null;
    return marker as AnimatedEventMarker;
}

function unregisterEventHandler(nodeId: number, id: number) {
    eventHandlers.delete(id);

    const ids = nodeEventIds.get(nodeId);
    if (!ids) return;

    ids.delete(id);
    if (ids.size === 0) nodeEventIds.delete(nodeId);
}

function registerOneShotEventHandler(nodeId: number, eventName: string, handler: Function): number {
    let id = 0;
    id = registerEventHandler(nodeId, eventName, (payload: any) => {
        try {
            handler(payload);
        } finally {
            unregisterEventHandler(nodeId, id);
        }
    });
    return id;
}

function clearNodeEventHandlers(nodeId: number) {
    const ids = nodeEventIds.get(nodeId);
    if (!ids) return;

    for (const id of ids) eventHandlers.delete(id);
    nodeEventIds.delete(nodeId);
}

function normalizeEventPayload(eventName: string, payload: any) {
    switch (eventName) {
        case 'onChangeText':
            return payload?.text ?? '';
        case 'onValueChange':
        case 'onSlidingStart':
        case 'onSlidingComplete':
            return payload && Object.prototype.hasOwnProperty.call(payload, 'value') ? payload.value : payload?.checked ?? 0;
        default:
            return payload;
    }
}

export function dispatchReactEvent(eventId: number, payload?: any) {
    const entry = eventHandlers.get(eventId);
    if (!entry) return;

    try {
        entry.handler(normalizeEventPayload(entry.eventName, payload));
    } catch (error) {
        console.error('Failed running react event handler', error);
    }
}


function isPlainObject(value: unknown): value is Record<string, any> {
    return !!value && typeof value === 'object' && !Array.isArray(value);
}

function flattenStyleValue(style: any): Record<string, any> {
    if (!style) return {};
    if (Array.isArray(style)) {
        const result: Record<string, any> = {};
        for (const entry of style) Object.assign(result, flattenStyleValue(entry));
        return result;
    }
    if (isPlainObject(style)) return { ...style };
    return {};
}

function preprocessProps(props: Record<string, any> | null | undefined): Record<string, any> {
    if (!props) return {};
    const { style, children, ref, ...rest } = props;
    return { ...flattenStyleValue(style), ...rest, ...(children !== undefined ? { children } : {}) };
}

function toBridgeValue(
    value: any,
    seen = new WeakSet<object>(),
    registerAssets = false,
): any {
    if (value === undefined) return null;
    if (value === null || typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return value;
    if (typeof value === 'function') return '[function]';
    if (typeof value !== 'object') return `[${typeof value}]`;
    if (React.isValidElement(value)) return '[react_element]';
    const asset = getNativeAssetRegistration(value);
    if (asset) {
        if (registerAssets) pendingAssetRegistrations.set(asset.assetId, asset);
        return {
            type: 'extension-asset',
            uri: asset.assetId,
            mimeType: asset.mimeType,
            assetKind: asset.kind,
        };
    }
    if (seen.has(value)) return '[circular]';
    seen.add(value);
    if (Array.isArray(value)) {
        return value.map(item => toBridgeValue(item, seen, registerAssets));
    }
    const output: Record<string, any> = {};
    for (const [key, child] of Object.entries(value)) {
        output[key] = toBridgeValue(child, seen, registerAssets);
    }
    return output;
}

function encodeProps(
    props: Record<string, any> | null | undefined,
    nodeId: number,
    registerEvents: boolean,
) {
    const result: Record<string, any> = {};
    if (!props) return result;

    for (const [key, value] of Object.entries(props)) {
        if (key === 'children' || key === 'ref') continue;
        if (isEventProp(key, value)) {
            const marker = getAnimatedEventMarker(value);
            if (marker) {
                result[key] = registerEvents
                    ? {
                        __type: 'worklet_event_handler',
                        id: registerWorkletEventHandler(nodeId),
                        nodeId,
                        eventName: key,
                        scriptId: marker.scriptId,
                        generation: Number(marker.generation),
                        workletId: String(marker.id),
                    }
                    : {
                        __type: 'worklet_event_handler_preview',
                        eventName: key,
                        scriptId: marker.scriptId,
                        generation: Number(marker.generation),
                        runtimeId: String(marker.runtimeId),
                        workletId: String(marker.id),
                    };
            } else {
                result[key] = registerEvents
                    ? { __type: 'event_handler', id: registerEventHandler(nodeId, key, value), eventName: key }
                    : {
                        __type: 'event_handler_preview',
                        eventName: key,
                        handlerId: getEventPreviewId(value),
                    };
            }
        } else {
            result[key] = toBridgeValue(value, new WeakSet<object>(), registerEvents);
        }
    }

    return result;
}

function createPublicInstance(instance: HostNode): NativeComponentRef {
    const getSurfaceId = () => instance.surfaceId ?? nodeSurfaceIds.get(instance.id);

    const runCommand = (command: string, args?: Record<string, any>, callback?: (payload: any) => void) => {
        const surfaceId = getSurfaceId();
        if (!surfaceId) return;

        const eventId = callback ? registerOneShotEventHandler(instance.id, command, callback) : undefined;

        dispatchSurfaceOps(surfaceId, [{
            op: 'viewCommand',
            nodeId: instance.id,
            command,
            args: toBridgeValue(args ?? {}),
            ...(eventId !== undefined ? { eventId } : {}),
        }]);
    };

    const publicInstance: NativeComponentRef & {
        scrollToIndex(options: ScrollToIndexOptions): void;
        scrollToOffset(options: ScrollToOffsetOptions): void;
    } = {
        get nodeId() {
            return instance.id;
        },

        get type() {
            return instance.type;
        },

        get mounted() {
            return !!getSurfaceId();
        },

        getNativeNodeId() {
            return instance.id;
        },

        setNativeProps(props: Record<string, any>) {
            updateNodeProps(instance.id, preprocessProps(props));
        },

        focus() {
            runCommand('focus');
        },

        blur() {
            runCommand('blur');
        },

        measure(callback: MeasureCallback) {
            runCommand('measure', {}, payload => {
                callback(
                    payload?.x ?? 0,
                    payload?.y ?? 0,
                    payload?.width ?? 0,
                    payload?.height ?? 0,
                    payload?.pageX ?? 0,
                    payload?.pageY ?? 0,
                );
            });
        },

        measureInWindow(callback: MeasureInWindowCallback) {
            runCommand('measureInWindow', {}, payload => {
                callback(
                    payload?.pageX ?? payload?.x ?? 0,
                    payload?.pageY ?? payload?.y ?? 0,
                    payload?.width ?? 0,
                    payload?.height ?? 0,
                );
            });
        },

        scrollTo(options?: { x?: number; y?: number; animated?: boolean } | number, y = 0, animated = true) {
            if (typeof options === 'number') {
                runCommand('scrollTo', { x: options, y, animated });
                return;
            }

            runCommand('scrollTo', {
                x: options?.x ?? 0,
                y: options?.y ?? 0,
                animated: options?.animated !== false,
            });
        },

        scrollToEnd(options?: { animated?: boolean }) {
            runCommand('scrollToEnd', { animated: options?.animated !== false });
        },

        flashScrollIndicators() {
            runCommand('flashScrollIndicators');
        },

        scrollToIndex(options: ScrollToIndexOptions) {
            runCommand('scrollToIndex', options);
        },

        scrollToOffset(options: ScrollToOffsetOptions) {
            runCommand('scrollToOffset', options);
        },

        dispatchCommand(command: string, args?: Record<string, any>, callback?: (payload: any) => void) {
            runCommand(command, args, callback);
        },

        command(command: string, args?: Record<string, any>, callback?: (payload: any) => void) {
            runCommand(command, args, callback);
        },
    };

    return publicInstance;
}

type HostNode = {
    id: number;
    type: string;
    surfaceId?: string;
    props: Record<string, any>;
    children: Array<HostNode | TextNode>;
    publicInstance?: NativeComponentRef;
};

type TextNode = {
    id: number;
    type: 'TEXT_INSTANCE';
    surfaceId?: string;
    text: string;
};

type RootContainer = {
    children: Array<HostNode | TextNode>;
};

export type MutationOp =
    | {
        op: 'registerAsset';
        assetId: string;
        scriptId: string;
        generation: number;
        rootPath: string;
        manifestPath: string;
        filePath: string;
        mimeType: string;
        kind: string;
    }
    | { op: 'createNode'; id: number; type: string; props: Record<string, any> }
    | { op: 'createText'; id: number; text: string }
    | { op: 'appendChild'; parentId: number; childId: number }
    | { op: 'appendToRoot'; childId: number }
    | { op: 'insertBefore'; parentId: number; childId: number; beforeChildId: number }
    | { op: 'insertInRootBefore'; childId: number; beforeChildId: number }
    | { op: 'removeChild'; parentId: number; childId: number }
    | { op: 'removeFromRoot'; childId: number }
    | { op: 'updateProps'; id: number; props: Record<string, any> }
    | { op: 'updateText'; id: number; text: string }
    | { op: 'setAnimatedProps'; nodeId: number; props: Record<string, any> }
    | { op: 'removeAnimatedProps'; nodeId: number }
    | { op: 'clearWorkletProps'; nodeId: number }
    | { op: 'updateAnimatedValue'; valueId: number; value: any; animation?: Record<string, any> }
    | { op: 'cancelAnimatedValue'; valueId: number }
    | { op: 'updateSharedValue'; valueId: number; value: any; animation?: Record<string, any> }
    | { op: 'startNativeAnimation'; nodeId: number; animationId: number; type?: string; duration?: number; delay?: number; easing?: string; tracks: Array<{ property: string; from: number; to: number }> }
    | { op: 'stopNativeAnimation'; animationId: number }
    | { op: 'scriptViewCommand'; nodeId: number; command: string; args?: Record<string, any> }
    | { op: 'destroyNode'; id: number }
    | { op: 'viewCommand'; nodeId: number; command: string; args?: Record<string, any>; eventId?: number };

let nextId = 1;
let pendingOps: MutationOp[] = [];
const pendingAssetRegistrations = new Map<string, NativeAssetRegistration>();
const registeredSurfaceAssets = new Map<string, Set<string>>();
const nodeSurfaceIds = new Map<number, string>();

type CommitDispatcher = (surfaceId: string, ops: MutationOp[]) => void;
let commitDispatcher: CommitDispatcher | null = null;

export function setCommitDispatcher(dispatcher: CommitDispatcher | null) {
    commitDispatcher = dispatcher;
}

function createId() {
    return nextId++;
}

function isEventProp(key: string, value: unknown): value is Function {
    return key.startsWith('on') && typeof value === 'function';
}

function sanitizeProps(props: Record<string, any> | null | undefined) {
    const result: Record<string, any> = {};
    if (!props) return result;

    for (const [key, value] of Object.entries(props)) {
        if (key === 'children' || key === 'ref') continue;
        if (!isEventProp(key, value)) result[key] = toBridgeValue(value);
    }

    return result;
}

function serializeNode(node: HostNode | TextNode): any {
    if (node.type === 'TEXT_INSTANCE') {
        return {
            id: node.id,
            type: node.type,
            text: (node as TextNode).text,
        };
    }

    return {
        id: node.id,
        type: node.type,
        props: (node as HostNode).props,
        children: (node as HostNode).children.map(serializeNode),
    };
}

type CommitListener = (ops: MutationOp[], tree: any | null) => void;

const commitListeners = new Map<string, CommitListener>();

export function setCommitListener(surfaceId: string, listener: CommitListener) {
    commitListeners.set(surfaceId, listener);
}

export function clearCommitListener(surfaceId: string) {
    commitListeners.delete(surfaceId);
}

function assignSurfaceId(instance: HostNode | TextNode, surfaceId: string) {
    instance.surfaceId = surfaceId;
    nodeSurfaceIds.set(instance.id, surfaceId);
    if (instance.type !== 'TEXT_INSTANCE') for (const child of (instance as HostNode).children) assignSurfaceId(child, surfaceId);
}

function clearSurfaceId(instance: HostNode | TextNode) {
    nodeSurfaceIds.delete(instance.id);
    delete instance.surfaceId;
    if (instance.type !== 'TEXT_INSTANCE') for (const child of (instance as HostNode).children) clearSurfaceId(child);
}

export function dispatchSurfaceOps(surfaceId: string, ops: MutationOp[]) {
    if (commitDispatcher) commitDispatcher(surfaceId, ops);
}

export function getNodeSurfaceId(nodeId: number): string | undefined {
    return nodeSurfaceIds.get(nodeId);
}

export function dispatchViewCommand(
    nodeId: number,
    command: string,
    args?: Record<string, any>,
    callback?: (payload: any) => void,
) {
    const surfaceId = nodeSurfaceIds.get(nodeId);
    if (!surfaceId) return;

    const eventId = callback ? registerOneShotEventHandler(nodeId, command, callback) : undefined;

    dispatchSurfaceOps(surfaceId, [{
        op: 'viewCommand',
        nodeId,
        command,
        args: toBridgeValue(args ?? {}),
        ...(eventId !== undefined ? { eventId } : {}),
    }]);
}

export function updateNodeProps(nodeId: number, props: Record<string, any>) {
    const surfaceId = nodeSurfaceIds.get(nodeId);
    if (!surfaceId) return;
    dispatchSurfaceOps(surfaceId, [{ op: 'updateProps', id: nodeId, props: encodeProps(props, nodeId, false) }]);
}

export function setAnimatedProps(nodeId: number, props: Record<string, any>) {
    const surfaceId = nodeSurfaceIds.get(nodeId);
    if (!surfaceId) return;
    dispatchSurfaceOps(surfaceId, [{ op: 'setAnimatedProps', nodeId, props: toBridgeValue(props) }]);
}

export function removeAnimatedProps(nodeId: number) {
    const surfaceId = nodeSurfaceIds.get(nodeId);
    if (!surfaceId) return;
    dispatchSurfaceOps(surfaceId, [{ op: 'removeAnimatedProps', nodeId }]);
}

export function clearWorkletProps(nodeId: number) {
    const surfaceId = nodeSurfaceIds.get(nodeId);
    if (!surfaceId) return;
    dispatchSurfaceOps(surfaceId, [{ op: 'clearWorkletProps', nodeId }]);
}

export function updateAnimatedValue(valueId: number, value: any, animation?: Record<string, any>) {
    const surfaceIds = new Set(nodeSurfaceIds.values());
    if (surfaceIds.size === 0) return;
    for (const surfaceId of surfaceIds) {
        dispatchSurfaceOps(surfaceId, [{
            op: 'updateAnimatedValue',
            valueId,
            value: toBridgeValue(value),
            ...(animation ? { animation: toBridgeValue(animation) } : {}),
        }]);
    }
}

export function cancelAnimatedValue(valueId: number) {
    const surfaceIds = new Set(nodeSurfaceIds.values());
    if (surfaceIds.size === 0) return;
    for (const surfaceId of surfaceIds) dispatchSurfaceOps(surfaceId, [{ op: 'cancelAnimatedValue', valueId }]);
}

export function updateSharedValue(valueId: number, value: any, animation?: Record<string, any>) {
    const surfaceIds = new Set(nodeSurfaceIds.values());
    if (surfaceIds.size === 0) return;
    for (const surfaceId of surfaceIds) dispatchSurfaceOps(surfaceId, [{ op: 'updateSharedValue', valueId, value: toBridgeValue(value), ...(animation ? { animation: toBridgeValue(animation) } : {}) }]);
}

export function startNativeAnimation(nodeId: number, config: { animationId: number; type?: string; duration?: number; delay?: number; easing?: string; tracks: Array<{ property: string; from: number; to: number }> }) {
    const surfaceId = nodeSurfaceIds.get(nodeId);
    if (!surfaceId) return;
    dispatchSurfaceOps(surfaceId, [{ op: 'startNativeAnimation', nodeId, ...config }]);
}

export function stopNativeAnimation(animationId: number) {
    for (const surfaceId of new Set(nodeSurfaceIds.values())) dispatchSurfaceOps(surfaceId, [{ op: 'stopNativeAnimation', animationId }]);
}

export function dispatchScriptViewCommand(nodeId: number, command: string, args?: Record<string, any>) {
    const surfaceId = nodeSurfaceIds.get(nodeId);
    if (!surfaceId) return;
    dispatchSurfaceOps(surfaceId, [{ op: 'scriptViewCommand', nodeId, command, args: toBridgeValue(args ?? {}) }]);
}

function destroyInstance(instance: HostNode | TextNode | null | undefined) {
    if (!instance) return;
    clearNodeEventHandlers(instance.id);
    clearSurfaceId(instance);
    pendingOps.push({ op: 'destroyNode', id: instance.id });
}

function releaseSubtree(instance: HostNode | TextNode | null | undefined) {
    if (!instance) return;

    clearNodeEventHandlers(instance.id);
    clearSurfaceId(instance);

    if (instance.type !== 'TEXT_INSTANCE') {
        for (const child of (instance as HostNode).children) releaseSubtree(child);
    }
}

const DEBUG_COMMITS = false;
const hostConfig = {
    supportsMutation: true,
    isPrimaryRenderer: true,
    now: Date.now,

    getRootHostContext() {
        return HOST_CONTEXT;
    },

    getChildHostContext(parentHostContext: object) {
        return parentHostContext;
    },

    getPublicInstance(instance: HostNode) {
        if (!instance.publicInstance) instance.publicInstance = createPublicInstance(instance);
        return instance.publicInstance;
    },

    prepareForCommit() {
        return null;
    },


    resetAfterCommit(container: RootContainer) {
        const surfaceId = ((container as any).__surfaceId ?? (container as any).surfaceId) as string;
        const surfaceAssets = registeredSurfaceAssets.get(surfaceId) ?? new Set<string>();
        const assetOps: MutationOp[] = [];
        for (const registration of pendingAssetRegistrations.values()) {
            if (surfaceAssets.has(registration.assetId)) continue;
            surfaceAssets.add(registration.assetId);
            assetOps.push({ op: 'registerAsset', ...registration });
        }
        registeredSurfaceAssets.set(surfaceId, surfaceAssets);
        const ops = [...assetOps, ...pendingOps];
        pendingOps = [];
        pendingAssetRegistrations.clear();

        if (ops.length === 0) return;

        if (commitDispatcher) {
            commitDispatcher(surfaceId, ops);
            return;
        }

        const listener = commitListeners.get(surfaceId);
        if (listener) listener(ops, null);
    },

    preparePortalMount() { },

    shouldSetTextContent() {
        return false;
    },

    createInstance(type: string, props: any): HostNode {
        const processedProps = preprocessProps(props ?? {});
        const { children, ...rest } = processedProps;
        const id = createId();

        const node: HostNode = {
            id,
            type,
            props: encodeProps(rest, id, true),
            children: [],
        };

        pendingOps.push({ op: 'createNode', id: node.id, type: node.type, props: node.props });

        return node;
    },

    createTextInstance(text: string): TextNode {
        const node: TextNode = {
            id: createId(),
            type: 'TEXT_INSTANCE',
            text,
        };

        pendingOps.push({
            op: 'createText',
            id: node.id,
            text: node.text,
        });

        return node;
    },

    appendInitialChild(parent: HostNode, child: HostNode | TextNode) {
        parent.children.push(child);
        if (parent.surfaceId) assignSurfaceId(child, parent.surfaceId);
        pendingOps.push({ op: 'appendChild', parentId: parent.id, childId: child.id });
    },

    appendChild(parent: HostNode, child: HostNode | TextNode) {
        parent.children.push(child);
        if (parent.surfaceId) assignSurfaceId(child, parent.surfaceId);
        pendingOps.push({ op: 'appendChild', parentId: parent.id, childId: child.id });
    },

    appendChildToContainer(container: RootContainer, child: HostNode | TextNode) {
        container.children.push(child);
        assignSurfaceId(child, (container as any).__surfaceId as string);
        pendingOps.push({ op: 'appendToRoot', childId: child.id });
    },

    insertBefore(parent: HostNode, child: HostNode | TextNode, beforeChild: HostNode | TextNode) {
        const existingIndex = parent.children.indexOf(child);
        if (existingIndex >= 0) parent.children.splice(existingIndex, 1);

        const beforeIndex = parent.children.indexOf(beforeChild);
        if (beforeIndex >= 0) parent.children.splice(beforeIndex, 0, child);
        else parent.children.push(child);
        if (parent.surfaceId) assignSurfaceId(child, parent.surfaceId);

        pendingOps.push({
            op: 'insertBefore',
            parentId: parent.id,
            childId: child.id,
            beforeChildId: beforeChild.id,
        });
    },

    insertInContainerBefore(container: RootContainer, child: HostNode | TextNode, beforeChild: HostNode | TextNode) {
        const existingIndex = container.children.indexOf(child);
        if (existingIndex >= 0) container.children.splice(existingIndex, 1);

        const beforeIndex = container.children.indexOf(beforeChild);
        if (beforeIndex >= 0) container.children.splice(beforeIndex, 0, child);
        else container.children.push(child);
        assignSurfaceId(child, (container as any).__surfaceId as string);

        pendingOps.push({
            op: 'insertInRootBefore',
            childId: child.id,
            beforeChildId: beforeChild.id,
        });
    },

    removeChild(parent: HostNode, child: HostNode | TextNode) {
        parent.children = parent.children.filter(c => c !== child);
        releaseSubtree(child);

        pendingOps.push({ op: 'removeChild', parentId: parent.id, childId: child.id });
        pendingOps.push({ op: 'destroyNode', id: child.id });
    },

    removeChildFromContainer(container: RootContainer, child: HostNode | TextNode) {
        container.children = container.children.filter(c => c !== child);
        releaseSubtree(child);

        pendingOps.push({ op: 'removeFromRoot', childId: child.id });
        pendingOps.push({ op: 'destroyNode', id: child.id });
    },

    finalizeInitialChildren() {
        return false;
    },

    prepareUpdate(instance: HostNode, _type: string, oldProps: any, newProps: any) {
        return buildPropDiff(instance.id, oldProps, newProps) ? true : null;
    },

    commitUpdate(instance: HostNode, _type: string, oldProps: any, newProps: any) {
        const diff = buildPropDiff(instance.id, oldProps, newProps);
        if (!diff) return;

        if (diff.eventChanged) clearNodeEventHandlers(instance.id);

        const encoded = encodeProps(diff.changed, instance.id, true);
        instance.props = applyEncodedPatch(instance.props, encoded);

        pendingOps.push({
            op: 'updateProps',
            id: instance.id,
            props: encoded,
        });
    },

    commitTextUpdate(textInstance: TextNode, _oldText: string, newText: string) {
        textInstance.text = newText;
        pendingOps.push({
            op: 'updateText',
            id: textInstance.id,
            text: newText,
        });
    },

    resetTextContent(instance: HostNode) {
        instance.children = instance.children.filter(child => child.type !== 'TEXT_INSTANCE');
    },

    clearContainer(container: RootContainer) {
        container.children = [];
    },

    scheduleTimeout: setTimeout,
    cancelTimeout: clearTimeout,
    noTimeout: -1,

    getInstanceFromNode() {
        return null;
    },

    beforeActiveInstanceBlur() { },
    afterActiveInstanceBlur() { },
    prepareScopeUpdate() { },
    getInstanceFromScope() {
        return null;
    },

    detachDeletedInstance(instance: HostNode | TextNode) {
        // React calls this as cleanup after deletion. Do not send native ops here.
        releaseSubtree(instance);
    },

    supportsMicrotasks: true,
    scheduleMicrotask: queueMicrotask,

    setCurrentUpdatePriority(newPriority: number) {
        currentUpdatePriority = newPriority;
    },

    getCurrentUpdatePriority() {
        return currentUpdatePriority;
    },

    resolveUpdatePriority() {
        return currentUpdatePriority || DefaultEventPriority;
    },

    getCurrentEventPriority() {
        return currentUpdatePriority || DefaultEventPriority;
    },

    trackSchedulerEvent() { },

    resolveEventType() {
        return null;
    },

    resolveEventTimeStamp() {
        return Date.now();
    },

    shouldAttemptEagerTransition() {
        return false;
    },

    requestPostPaintCallback(callback: (time: number) => void) {
        setTimeout(() => callback(Date.now()), 0);
    },

    maySuspendCommit() {
        return false;
    },

    preloadInstance() {
        return true;
    },

    startSuspendingCommit() { },
    suspendInstance() { },
    waitForCommitToBeReady() {
        return null;
    },

    resetFormInstance() { },

    NotPendingTransition: null,
    HostTransitionContext: {
        $$typeof: Symbol.for('react.context'),
        Consumer: null as any,
        Provider: null as any,
        _currentValue: null,
        _currentValue2: null,
        _threadCount: 0,
    },
};

const reconciler = Reconciler(hostConfig as any);
const noop = () => { };

export interface RenderRoot {
    render(element: React.ReactNode): void;
    unmount(): void;
    getTree(): any;
}

function buildPropDiff(nodeId: number, oldProps: any, newProps: any) {
    const oldProcessed = preprocessProps(oldProps ?? {});
    const newProcessed = preprocessProps(newProps ?? {});
    const oldPreview = encodeProps(oldProcessed, nodeId, false);
    const newPreview = encodeProps(newProcessed, nodeId, false);
    const keys = new Set([...Object.keys(oldPreview), ...Object.keys(newPreview)]);
    const changed: Record<string, any> = {};
    let hasChanges = false;
    let eventChanged = false;

    for (const key of keys) {
        if (key === 'children') continue;
        const oldValue = oldPreview[key];
        const newValue = newPreview[key];
        if (bridgeValuesEqual(oldValue, newValue)) continue;
        changed[key] = Object.prototype.hasOwnProperty.call(newProcessed, key) ? newProcessed[key] : undefined;
        hasChanges = true;
        if (isEventProp(key, oldProcessed[key]) || isEventProp(key, newProcessed[key])) eventChanged = true;
    }

    if (!hasChanges) return null;

    if (eventChanged) {
        for (const [key, value] of Object.entries(newProcessed)) {
            if (isEventProp(key, value)) changed[key] = value;
        }
    }

    return { changed, eventChanged };
}

function applyEncodedPatch(target: Record<string, any>, patch: Record<string, any>) {
    const next = { ...target };
    for (const [key, value] of Object.entries(patch)) {
        if (value === null) delete next[key];
        else next[key] = value;
    }
    return next;
}

function destroySubtree(instance: HostNode | TextNode | null | undefined) {
    if (!instance) return;

    clearNodeEventHandlers(instance.id);
    clearSurfaceId(instance);

    if (instance.type !== 'TEXT_INSTANCE') {
        for (const child of (instance as HostNode).children) destroySubtree(child);
    }

    pendingOps.push({ op: 'destroyNode', id: instance.id });
}

function bridgeValuesEqual(left: any, right: any): boolean {
    if (Object.is(left, right)) return true;
    if (left == null || right == null) return false;
    if (typeof left !== typeof right) return false;
    if (typeof left !== "object") return false;

    if (Array.isArray(left) || Array.isArray(right)) {
        if (!Array.isArray(left) || !Array.isArray(right) || left.length !== right.length) return false;
        for (let i = 0; i < left.length; i++) if (!bridgeValuesEqual(left[i], right[i])) return false;
        return true;
    }

    const leftKeys = Object.keys(left);
    const rightKeys = Object.keys(right);
    if (leftKeys.length !== rightKeys.length) return false;

    for (const key of leftKeys) {
        if (!Object.prototype.hasOwnProperty.call(right, key)) return false;
        if (!bridgeValuesEqual(left[key], right[key])) return false;
    }

    return true;
}

function logReactError(kind: string) {
    return (error: any, errorInfo?: any) => {
        console.error(`[React ${kind}]`, {
            message: error?.message,
            stack: error?.stack,
            name: error?.name,
            error: String(error),
            errorInfo,
        });
    };
}

export function createRoot(surfaceId: string): RenderRoot {
    const container: RootContainer = { children: [], };
    (container as any).surfaceId = surfaceId;
    (container as any).__surfaceId = surfaceId;

    const root = reconciler.createContainer(container, 0, null, false, null, '', logReactError('uncaught'), logReactError('caught'), logReactError('recoverable'), noop);

    return {
        render(element: React.ReactNode) {
            try {
                reconciler.updateContainerSync(element, root, null, null);
                reconciler.flushSyncWork();
            } catch (error: any) {
                console.error("React root render threw", {
                    surfaceId,
                    message: error?.message,
                    stack: error?.stack,
                    error: String(error),
                    rootChildCount: container.children.length,
                });
                throw error;
            }
        },

        unmount() {
            reconciler.updateContainerSync(null, root, null, null);
            reconciler.flushSyncWork();
        },
        getTree() {
            return container;
        },
    };
}

