import {
    WORKLET_METADATA_KEY,
    type SerializedWorklet,
    type WorkletFunction,
    type WorkletMetadata,
    type WorkletRuntimeHandle,
} from "./types";
import { isAllowedWorkletGlobal } from "./worklet-globals";

export class UnsupportedPlatformError extends Error {
    readonly feature: string;
    readonly adapterName?: string;

    constructor(feature: string, adapterName?: string) {
        super(
            adapterName
                ? `${feature} is not supported by the ${adapterName} animation adapter.`
                : `${feature} is unavailable because no native animation adapter is installed.`,
        );
        this.name = "UnsupportedPlatformError";
        this.feature = feature;
        this.adapterName = adapterName;
    }
}

export class WorkletValidationError extends Error {
    readonly apiName: string;

    constructor(apiName: string, detail: string) {
        super(`${apiName} expected a compiled worklet. ${detail}`);
        this.name = "WorkletValidationError";
        this.apiName = apiName;
    }
}

export enum RuntimeKind {
    ReactNative = "reactNative",
    UI = "ui",
    Worker = "worker",
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return value !== null && typeof value === "object" && !Array.isArray(value);
}

function hasCanonicalMetadata(value: unknown): value is WorkletMetadata {
    return isRecord(value)
        && value.version === 2
        && typeof value.hash === "string"
        && value.hash.length > 0
        && typeof value.code === "string"
        && value.code.length > 0
        && isRecord(value.closure);
}

function normalizeCompatibilityMetadata(fn: WorkletFunction): WorkletMetadata | null {
    if (fn.__workletHash === undefined || !fn.__initData?.code) {
        return null;
    }

    return {
        version: 2,
        hash: String(fn.__workletHash),
        code: fn.__initData.code,
        closure: fn.__closure ?? {},
        ...(fn.__initData.globals ? { globals: fn.__initData.globals } : {}),
        ...(fn.__initData.location ? { location: fn.__initData.location } : {}),
        ...(fn.__initData.sourceMap ? { sourceMap: fn.__initData.sourceMap } : {}),
    };
}

export function getWorkletMetadata(value: unknown): WorkletMetadata | null {
    if (typeof value !== "function") {
        return null;
    }

    const fn = value as WorkletFunction;
    const canonical = fn[WORKLET_METADATA_KEY];
    if (hasCanonicalMetadata(canonical)) {
        return canonical;
    }

    return normalizeCompatibilityMetadata(fn);
}

export function isWorkletFunction(value: unknown): value is WorkletFunction {
    return getWorkletMetadata(value) !== null;
}

export function validateWorklet<T extends (...args: any[]) => any>(
    value: T,
    apiName: string,
): asserts value is WorkletFunction<T> {
    if (typeof value !== "function") {
        throw new WorkletValidationError(apiName, `Received ${typeof value} instead of a function.`);
    }

    const rawMetadata = (value as WorkletFunction)[WORKLET_METADATA_KEY];
    if (rawMetadata && rawMetadata.version !== 2) {
        throw new WorkletValidationError(
            apiName,
            `Metadata version ${String(rawMetadata.version)} is incompatible with API 2. Rebuild the script with the current SpotifyPlus compiler.`,
        );
    }

    const metadata = getWorkletMetadata(value);
    if (metadata) {
        for (const [identifier, exportName] of Object.entries(metadata.globals ?? {})) {
            if (!identifier || typeof exportName !== "string" || !isAllowedWorkletGlobal(exportName)) {
                throw new WorkletValidationError(
                    apiName,
                    `The imported worklet global ${identifier || "<empty>"} -> ${String(exportName)} is not in the API-2 allowlist.`,
                );
            }
        }
        return;
    }

    const source = Function.prototype.toString.call(value);
    const hasDirective = /(?:^|[{;]\s*)["']worklet["']\s*;/.test(source);
    if (hasDirective) {
        throw new WorkletValidationError(
            apiName,
            "The function contains a worklet directive but has no serialized metadata. Build the script with the SpotifyPlus worklet transform.",
        );
    }

    throw new WorkletValidationError(
        apiName,
        "Use a `worklet` directive or pass the function directly to an auto-workletized API, then rebuild with the SpotifyPlus worklet transform.",
    );
}

export function serializeWorklet<T extends (...args: any[]) => any>(
    value: T,
    apiName: string,
): SerializedWorklet<T> {
    validateWorklet(value, apiName);
    return {
        metadata: getWorkletMetadata(value)!,
        callable: value,
    };
}

function stableHash(source: string) {
    let hash = 0x811c9dc5;
    for (let index = 0; index < source.length; index++) {
        hash ^= source.charCodeAt(index);
        hash = Math.imul(hash, 0x01000193);
    }
    return `internal-${(hash >>> 0).toString(16).padStart(8, "0")}`;
}

/** Marks facade-owned helper functions. User code should rely on the build transform instead. */
export function createInternalWorklet<T extends (...args: any[]) => any>(
    fn: T,
    closure: Readonly<Record<string, unknown>> = {},
    location = "spotifyplus:animated-internal",
    globals?: Readonly<Record<string, string>>,
): WorkletFunction<T> {
    const worklet = fn as WorkletFunction<T> & { [WORKLET_METADATA_KEY]: WorkletMetadata };
    const code = Function.prototype.toString.call(fn);
    worklet[WORKLET_METADATA_KEY] = {
        version: 2,
        hash: stableHash(`${location}:${code}`),
        code,
        closure,
        ...(globals ? { globals } : {}),
        location,
    };
    return worklet;
}

export function isWorkletRuntime(value: unknown): value is WorkletRuntimeHandle {
    return isRecord(value)
        && (typeof value.id === "string" || typeof value.id === "number")
        && typeof value.name === "string";
}

export function getRuntimeKind(): RuntimeKind {
    const globalValue = globalThis as typeof globalThis & {
        __spotifyPlusRuntimeKind?: RuntimeKind;
        _WORKLET?: boolean;
    };

    if (globalValue.__spotifyPlusRuntimeKind) {
        return globalValue.__spotifyPlusRuntimeKind;
    }

    return globalValue._WORKLET === true ? RuntimeKind.UI : RuntimeKind.ReactNative;
}
