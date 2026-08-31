import { inspect } from "util";

export class Logger {
    constructor(private readonly tag: string) { }

    info(message: string, ...args: unknown[]): void {
        console.log(`[${this.tag}] ${formatLogArgs([message, ...args])}`);
    }

    warn(message: string, ...args: unknown[]): void {
        console.warn(`[${this.tag}] ${formatLogArgs([message, ...args])}`);
    }

    error(message: string, ...args: unknown[]): void {
        console.error(`[${this.tag}] ${formatLogArgs([message, ...args])}`);
    }

    child(childTag: string): Logger {
        return new Logger(`${this.tag}:${childTag}`);
    }
}

export interface SafeFormatOptions {
    maxDepth?: number;
    maxArrayItems?: number;
    maxObjectKeys?: number;
    maxStringLength?: number;
    maxOutputLength?: number;
}

export interface DevLogFormatOptions extends SafeFormatOptions {
    breakLength?: number;
}

const DEFAULT_OPTIONS: Required<SafeFormatOptions> = {
    maxDepth: 3,
    maxArrayItems: 20,
    maxObjectKeys: 30,
    maxStringLength: 500,
    maxOutputLength: 2000,
};

const DEFAULT_DEV_LOG_OPTIONS: Required<DevLogFormatOptions> = {
    maxDepth: 5,
    maxArrayItems: 50,
    maxObjectKeys: 50,
    maxStringLength: 2000,
    maxOutputLength: 12000,
    breakLength: 100,
};

export function formatLogArgs(args: unknown[], options: SafeFormatOptions = {}): string {
    const opts = { ...DEFAULT_OPTIONS, ...options };
    const seen = new WeakSet<object>();
    const output = args.map(arg => safeFormat(arg, opts, 0, seen)).join(" ");
    return truncate(output, opts.maxOutputLength);
}

export function formatDevLogArgs(args: unknown[], options: DevLogFormatOptions = {}): string {
    const opts = { ...DEFAULT_DEV_LOG_OPTIONS, ...options };
    const seen = new WeakSet<object>();
    const output = args.map(arg => {
        if (typeof arg === "string") {
            const parsed = tryParseJsonContainer(arg);
            if (parsed === undefined) return truncate(arg, opts.maxStringLength);
            arg = parsed;
        }
        if (arg instanceof Error) return formatError(arg, opts);
        if (arg == null || typeof arg !== "object") return safeFormat(arg, opts, 0, seen);

        return inspect(sanitizeForDevLog(arg, opts, 0, seen), {
            breakLength: opts.breakLength,
            colors: false,
            compact: false,
            depth: null,
            maxArrayLength: null,
            maxStringLength: null,
        });
    }).join(" ");
    return truncate(output, opts.maxOutputLength);
}

function tryParseJsonContainer(value: string): object | undefined {
    const trimmed = value.trim();
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return undefined;

    try {
        const parsed = JSON.parse(trimmed);
        return parsed && typeof parsed === "object" ? parsed : undefined;
    } catch {
        return undefined;
    }
}

function sanitizeForDevLog(
    value: unknown,
    options: Required<DevLogFormatOptions>,
    depth: number,
    seen: WeakSet<object>,
): unknown {
    if (value == null) return value;
    if (typeof value === "string") return truncate(value, options.maxStringLength);
    if (typeof value === "number" || typeof value === "boolean" || typeof value === "bigint") return value;
    if (typeof value === "function") return `[Function ${value.name || "anonymous"}]`;
    if (value instanceof Error) return formatError(value, options);
    if (typeof value !== "object") return String(value);
    if (seen.has(value)) return "[Circular]";
    if (depth >= options.maxDepth) return summaryFor(value);
    seen.add(value);

    if (Array.isArray(value)) {
        const items = value.slice(0, options.maxArrayItems).map(item => (
            sanitizeForDevLog(item, options, depth + 1, seen)
        ));
        if (value.length > options.maxArrayItems) items.push(`... ${value.length - options.maxArrayItems} more`);
        return items;
    }

    const entries = Object.entries(value as Record<string, unknown>);
    const sanitizedEntries = entries.slice(0, options.maxObjectKeys).map(([key, entry]) => [
        key,
        sanitizeForDevLog(entry, options, depth + 1, seen),
    ] as const);
    if (entries.length > options.maxObjectKeys) {
        sanitizedEntries.push([
            `... ${entries.length - options.maxObjectKeys} more keys`,
            "[Truncated]",
        ]);
    }
    return Object.fromEntries(sanitizedEntries);
}

function safeFormat(value: unknown, options: Required<SafeFormatOptions>, depth: number, seen: WeakSet<object>): string {
    if (value == null) return String(value);
    if (typeof value === "string") return truncate(value, options.maxStringLength);
    if (typeof value === "number" || typeof value === "boolean" || typeof value === "bigint") return String(value);
    if (typeof value === "function") return `[Function ${value.name || "anonymous"}]`;
    if (value instanceof Error) return formatError(value, options);
    if (depth >= options.maxDepth) return summaryFor(value);

    if (typeof value === "object") {
        if (seen.has(value)) return "[Circular]";
        seen.add(value);

        if (Array.isArray(value)) {
            const items = value.slice(0, options.maxArrayItems).map(item => safeFormat(item, options, depth + 1, seen));
            if (value.length > options.maxArrayItems) items.push(`... ${value.length - options.maxArrayItems} more`);
            return `[${items.join(", ")}]`;
        }

        const entries = Object.entries(value as Record<string, unknown>);
        const parts = entries.slice(0, options.maxObjectKeys).map(([key, entry]) => `${key}: ${safeFormat(entry, options, depth + 1, seen)}`);
        if (entries.length > options.maxObjectKeys) parts.push(`... ${entries.length - options.maxObjectKeys} more`);
        return `{ ${parts.join(", ")} }`;
    }

    return truncate(String(value), options.maxStringLength);
}

function formatError(error: Error, options: Required<SafeFormatOptions>): string {
    const heading = `${error.name}: ${truncate(error.message, options.maxStringLength)}`;
    if (!error.stack) return heading;

    const stack = truncate(error.stack, options.maxStringLength);
    const firstLineEnd = stack.indexOf("\n");
    if (firstLineEnd >= 0) return `${heading}${stack.slice(firstLineEnd)}`;
    return stack === `${error.name}: ${error.message}` ? heading : `${heading}\n${stack}`;
}

function summaryFor(value: unknown): string {
    if (Array.isArray(value)) return `[Array(${value.length})]`;
    if (value && typeof value === "object") return `[${value.constructor?.name || "Object"}]`;
    return String(value);
}

function truncate(value: string, maxLength: number): string {
    if (value.length <= maxLength) return value;
    return `${value.slice(0, Math.max(0, maxLength - 20))}... <truncated>`;
}
