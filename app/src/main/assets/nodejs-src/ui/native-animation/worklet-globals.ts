import type { WorkletGlobalsManifest } from "./types";

/**
 * Allowlist understood by the compiler and UI-runtime bootstrap. The native
 * adapter must install these names before evaluating worklets whose metadata
 * contains `globals`. This avoids attempting to serialize external SDK modules.
 */
export const WORKLET_GLOBAL_EXPORTS = Object.freeze([
    "ReduceMotion",
    "Extrapolation",
    "RuntimeKind",
    "SensorType",
    "KeyboardState",
    "createAnimatedPropAdapter",
    "Easing",
    "clamp",
    "interpolate",
    "interpolateColor",
    "contrastColor",
    "processColor",
    "convertToRGBA",
    "DynamicColorIOS",
    "withTiming",
    "withSpring",
    "withDecay",
    "withDelay",
    "withRepeat",
    "withSequence",
    "withClamp",
    "defineAnimation",
    "withCustomAnimation",
    "isAnimation",
    "isSharedValue",
    "isWorkletFunction",
    "cancelAnimation",
    "scheduleOnRN",
    "runOnJS",
    "scheduleOnUI",
    "runOnUI",
    "scheduleOnRuntime",
    "measure",
    "scrollTo",
    "dispatchCommand",
    "setNativeProps",
    "getViewProp",
    "getRelativeCoords",
    "getTimestamp",
    "getRuntimeKind",
] as const);

export type WorkletGlobalExport = typeof WORKLET_GLOBAL_EXPORTS[number];

export const WORKLET_GLOBALS_MANIFEST: WorkletGlobalsManifest = Object.freeze({
    version: 2,
    exports: WORKLET_GLOBAL_EXPORTS,
});

export function isAllowedWorkletGlobal(name: string): name is WorkletGlobalExport {
    return (WORKLET_GLOBAL_EXPORTS as readonly string[]).includes(name);
}
