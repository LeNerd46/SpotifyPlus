import * as AnimationAPI from "./animations";
import type React from "react";
import type {
    CanvasView as HostCanvasView,
    FlatList as HostFlatList,
    FlatListProps as HostFlatListProps,
    Image as HostImage,
    ImageProps as HostImageProps,
    RenderView as HostRenderView,
    ScriptView as HostScriptView,
    ScriptViewProps as HostScriptViewProps,
    ScrollView as HostScrollView,
    ScrollViewProps as HostScrollViewProps,
    Text as HostText,
    TextProps as HostTextProps,
    View as HostView,
    ViewProps as HostViewProps,
} from "../components";
import * as GestureAPI from "./gesture";
import {
    createRuntimeBindings,
    createAnimatedPropAdapter,
    isAnimatedEventHandler,
    isAnimatedNodeLike,
    isAnimatedPropsPayload,
    isAnimatedStylePayload,
    installAnimatedComponents,
    useHandler,
} from "./hooks";
import * as InterpolationAPI from "./interpolation";
import * as LayoutAPI from "./layout";
import { createJavaScriptAnimationAdapter } from "./local-adapter";
import { AnimatedRuntime, createAnimatedRuntime } from "./runtime";
import * as TypeValues from "./types";
import type {
    AnimatedComponentProps,
    AnimatedHostComponents,
    AnimatedRuntimeScope,
    NativeAnimationAdapter,
} from "./types";
import {
    RuntimeKind,
    UnsupportedPlatformError,
    WorkletValidationError,
    getRuntimeKind,
    getWorkletMetadata,
    isWorkletFunction,
    isWorkletRuntime,
    serializeWorklet,
    validateWorklet,
} from "./worklets";
import {
    WORKLET_GLOBAL_EXPORTS,
    WORKLET_GLOBALS_MANIFEST,
    isAllowedWorkletGlobal,
} from "./worklet-globals";

export enum ReanimatedLogLevel {
    warn = 1,
    error = 2,
}

export interface ReanimatedLoggerConfig {
    level?: ReanimatedLogLevel;
    strict?: boolean;
}

let loggerConfig: Required<ReanimatedLoggerConfig> = {
    level: ReanimatedLogLevel.warn,
    strict: true,
};

export function configureReanimatedLogger(config: ReanimatedLoggerConfig) {
    loggerConfig = {
        level: config.level ?? ReanimatedLogLevel.warn,
        strict: config.strict ?? true,
    };
}

export function getReanimatedLoggerConfig() {
    return { ...loggerConfig };
}

export * from "./types";
export * from "./animations";
export * from "./interpolation";
export * from "./layout";
export * from "./gesture";
export {
    AnimatedRuntime,
    RuntimeKind,
    UnsupportedPlatformError,
    WorkletValidationError,
    WORKLET_GLOBAL_EXPORTS,
    WORKLET_GLOBALS_MANIFEST,
    createAnimatedRuntime,
    createAnimatedPropAdapter,
    createJavaScriptAnimationAdapter,
    getRuntimeKind,
    getWorkletMetadata,
    installAnimatedComponents,
    isAllowedWorkletGlobal,
    isAnimatedEventHandler,
    isAnimatedNodeLike,
    isAnimatedPropsPayload,
    isAnimatedStylePayload,
    isWorkletFunction,
    isWorkletRuntime,
    serializeWorklet,
    useHandler,
    validateWorklet,
};

const STATIC_API = {
    ...TypeValues,
    ...AnimationAPI,
    ...InterpolationAPI,
    ...LayoutAPI,
    ...GestureAPI,
    ReanimatedLogLevel,
    RuntimeKind,
    UnsupportedPlatformError,
    WorkletValidationError,
    WORKLET_GLOBAL_EXPORTS,
    WORKLET_GLOBALS_MANIFEST,
    createAnimatedRuntime,
    createAnimatedPropAdapter,
    createJavaScriptAnimationAdapter,
    configureReanimatedLogger,
    getReanimatedLoggerConfig,
    getRuntimeKind,
    getWorkletMetadata,
    isAllowedWorkletGlobal,
    isAnimatedEventHandler,
    isAnimatedNodeLike,
    isAnimatedPropsPayload,
    isAnimatedStylePayload,
    isWorkletFunction,
    isWorkletRuntime,
    serializeWorklet,
    useHandler,
    validateWorklet,
};

/**
 * Creates an API-2 facade permanently bound to one script generation. Loaders
 * must create a new module when a script is reloaded and dispose the old module.
 */
export function createAnimatedModule(
    adapter: NativeAnimationAdapter,
    scope: AnimatedRuntimeScope,
    hosts?: AnimatedHostComponents,
) {
    const runtime = createAnimatedRuntime(adapter, scope);
    const bindings = createRuntimeBindings(runtime, hosts);
    return Object.freeze({
        ...STATIC_API,
        ...bindings,
        createAnimatedModule,
        createAnimatedRuntime,
    });
}

export type AnimatedModule = ReturnType<typeof createAnimatedModule>;

const defaultAdapter = createJavaScriptAnimationAdapter();
const defaultRuntime = createAnimatedRuntime(defaultAdapter, {
    scriptId: "spotifyplus:animated-hostless",
    generation: 0,
});
const defaultBindings = createRuntimeBindings(defaultRuntime);

export const makeMutable = defaultBindings.makeMutable;
export const useSharedValue = defaultBindings.useSharedValue;
export const useDerivedValue = defaultBindings.useDerivedValue;
export const useAnimatedStyle = defaultBindings.useAnimatedStyle;
export const useAnimatedProps = defaultBindings.useAnimatedProps;
export const useAnimatedReaction = defaultBindings.useAnimatedReaction;
export const useFrameCallback = defaultBindings.useFrameCallback;
export const useFrameTimestamp = defaultBindings.useFrameTimestamp;
export const useTimestamp = defaultBindings.useTimestamp;
export const useAnimatedRef = defaultBindings.useAnimatedRef;
export const useEvent = defaultBindings.useEvent;
export const useAnimatedScrollHandler = defaultBindings.useAnimatedScrollHandler;
export const useComposedEventHandler = defaultBindings.useComposedEventHandler;
export const useScrollOffset = defaultBindings.useScrollOffset;
export const useScrollViewOffset = defaultBindings.useScrollViewOffset;
export const useAnimatedSensor = defaultBindings.useAnimatedSensor;
export const useAnimatedKeyboard = defaultBindings.useAnimatedKeyboard;
export const usePlaybackClock = defaultBindings.usePlaybackClock;
export const useReducedMotion = defaultBindings.useReducedMotion;
export const ReducedMotionConfig = defaultBindings.ReducedMotionConfig;
export const createAnimatedComponent = defaultBindings.createAnimatedComponent;
export const cancelAnimation = defaultBindings.cancelAnimation;
export const scheduleOnUI = defaultBindings.scheduleOnUI;
export const runOnUI = defaultBindings.runOnUI;
export const runOnUIAsync = defaultBindings.runOnUIAsync;
export const executeOnUIRuntimeSync = defaultBindings.executeOnUIRuntimeSync;
export const runOnUISync = defaultBindings.runOnUISync;
export const scheduleOnRN = defaultBindings.scheduleOnRN;
export const runOnRNAsync = defaultBindings.runOnRNAsync;
export const runOnJS = defaultBindings.runOnJS;
export const createWorkletRuntime = defaultBindings.createWorkletRuntime;
export const runOnRuntime = defaultBindings.runOnRuntime;
export const scheduleOnRuntime = defaultBindings.scheduleOnRuntime;
export const measure = defaultBindings.measure;
export const scrollTo = defaultBindings.scrollTo;
export const scrollToOffset = defaultBindings.scrollToOffset;
export const dispatchCommand = defaultBindings.dispatchCommand;
export const setNativeProps = defaultBindings.setNativeProps;
export const getViewProp = defaultBindings.getViewProp;
export const getRelativeCoords = defaultBindings.getRelativeCoords;
export const enableLayoutAnimations = defaultBindings.enableLayoutAnimations;
export const GestureDetector = defaultBindings.GestureDetector;
export const View = defaultBindings.View as React.ComponentType<AnimatedComponentProps<HostViewProps, HostView>>;
export const Text = defaultBindings.Text as React.ComponentType<AnimatedComponentProps<HostTextProps, HostText>>;
export const Image = defaultBindings.Image as React.ComponentType<AnimatedComponentProps<HostImageProps, HostImage>>;
export const ScriptView = defaultBindings.ScriptView as React.ComponentType<
    AnimatedComponentProps<HostScriptViewProps, HostScriptView>
>;
export const RenderView = defaultBindings.RenderView as React.ComponentType<
    AnimatedComponentProps<HostScriptViewProps, HostRenderView>
>;
export const CanvasView = defaultBindings.CanvasView as React.ComponentType<
    AnimatedComponentProps<HostScriptViewProps, HostCanvasView>
>;
export const ScrollView = defaultBindings.ScrollView as React.ComponentType<
    AnimatedComponentProps<HostScrollViewProps, HostScrollView>
>;
export const FlatList = defaultBindings.FlatList as {
    <Item>(props: AnimatedComponentProps<HostFlatListProps<Item>, HostFlatList<Item>>): React.ReactNode;
};

export const useWorkletCallback = defaultBindings.useWorkletCallback;
export const getTimestamp = defaultBindings.getTimestamp;
export const callMicrotasks = defaultBindings.callMicrotasks;
export const isSharedValue = defaultBindings.isSharedValue;

export const Animated = Object.freeze({
    ...STATIC_API,
    ...defaultBindings,
    createAnimatedModule,
    createAnimatedRuntime,
    useWorkletCallback,
    getTimestamp,
    callMicrotasks,
    isSharedValue,
    View,
    Text,
    Image,
    ScriptView,
    RenderView,
    CanvasView,
    ScrollView,
    FlatList,
});

export default Animated;
