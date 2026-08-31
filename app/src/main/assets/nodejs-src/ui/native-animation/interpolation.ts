import {
    Extrapolation,
    type ExtrapolationConfig,
    type ExtrapolationType,
    type InterpolateOptions,
} from "./types";
import { UnsupportedPlatformError } from "./worklets";

export type ColorSpace = "RGB" | "HSV" | "LAB";

export interface InterpolateRGBOptions {
    gamma?: number;
}

export interface InterpolateHSVOptions {
    useCorrectedHSVInterpolation?: boolean;
}

export type InterpolateColorOptions = InterpolateRGBOptions & InterpolateHSVOptions;

interface RGBA {
    r: number;
    g: number;
    b: number;
    a: number;
}

const NAMED_COLORS: Readonly<Record<string, string>> = {
    transparent: "#00000000",
    black: "#000000",
    white: "#ffffff",
    red: "#ff0000",
    green: "#008000",
    blue: "#0000ff",
    yellow: "#ffff00",
    cyan: "#00ffff",
    magenta: "#ff00ff",
    gray: "#808080",
    grey: "#808080",
};

export function clamp(value: number, minimum: number, maximum: number) {
    if (minimum > maximum) {
        throw new RangeError("clamp minimum cannot be greater than maximum.");
    }
    return Math.min(maximum, Math.max(minimum, value));
}

function normalizeExtrapolation(options?: InterpolateOptions): Required<ExtrapolationConfig> {
    if (typeof options === "string") {
        return { extrapolateLeft: options, extrapolateRight: options };
    }
    return {
        extrapolateLeft: options?.extrapolateLeft ?? Extrapolation.EXTEND,
        extrapolateRight: options?.extrapolateRight ?? Extrapolation.EXTEND,
    };
}

function validateRanges(inputRange: readonly number[], outputRange: readonly unknown[]) {
    if (inputRange.length < 2 || inputRange.length !== outputRange.length) {
        throw new RangeError("Interpolation ranges must have the same length and contain at least two values.");
    }
    for (let index = 1; index < inputRange.length; index++) {
        if (inputRange[index] < inputRange[index - 1]) {
            throw new RangeError("The input range must be monotonically increasing.");
        }
    }
}

function findSegment(value: number, inputRange: readonly number[]) {
    if (value <= inputRange[0]) {
        return 0;
    }
    for (let index = 1; index < inputRange.length; index++) {
        if (value <= inputRange[index]) {
            return index - 1;
        }
    }
    return inputRange.length - 2;
}

function applyExtrapolation(
    value: number,
    edge: number,
    outputEdge: number,
    mode: ExtrapolationType,
) {
    if (mode === Extrapolation.IDENTITY) {
        return value;
    }
    if (mode === Extrapolation.CLAMP) {
        return outputEdge;
    }
    return null;
}

export function interpolate(
    value: number,
    inputRange: readonly number[],
    outputRange: readonly number[],
    options?: InterpolateOptions,
) {
    validateRanges(inputRange, outputRange);
    const extrapolation = normalizeExtrapolation(options);

    if (value < inputRange[0]) {
        const extrapolated = applyExtrapolation(
            value,
            inputRange[0],
            outputRange[0],
            extrapolation.extrapolateLeft,
        );
        if (extrapolated !== null) {
            return extrapolated;
        }
    }

    const finalIndex = inputRange.length - 1;
    if (value > inputRange[finalIndex]) {
        const extrapolated = applyExtrapolation(
            value,
            inputRange[finalIndex],
            outputRange[finalIndex],
            extrapolation.extrapolateRight,
        );
        if (extrapolated !== null) {
            return extrapolated;
        }
    }

    const index = findSegment(value, inputRange);
    const inputStart = inputRange[index];
    const inputEnd = inputRange[index + 1];
    const outputStart = outputRange[index];
    const outputEnd = outputRange[index + 1];
    if (inputEnd === inputStart) {
        return outputEnd;
    }
    const progress = (value - inputStart) / (inputEnd - inputStart);
    return outputStart + progress * (outputEnd - outputStart);
}

function parseHex(value: string): RGBA | null {
    const hex = value.slice(1);
    if (![3, 4, 6, 8].includes(hex.length) || !/^[0-9a-f]+$/i.test(hex)) {
        return null;
    }
    const expanded = hex.length <= 4
        ? hex.split("").map(character => character + character).join("")
        : hex;
    const hasAlpha = expanded.length === 8;
    return {
        r: parseInt(expanded.slice(0, 2), 16),
        g: parseInt(expanded.slice(2, 4), 16),
        b: parseInt(expanded.slice(4, 6), 16),
        a: hasAlpha ? parseInt(expanded.slice(6, 8), 16) / 255 : 1,
    };
}

function parseRgb(value: string): RGBA | null {
    const match = value.match(/^rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)(?:\s*,\s*([\d.]+))?\s*\)$/i);
    if (!match) {
        return null;
    }
    return {
        r: clamp(Number(match[1]), 0, 255),
        g: clamp(Number(match[2]), 0, 255),
        b: clamp(Number(match[3]), 0, 255),
        a: clamp(match[4] === undefined ? 1 : Number(match[4]), 0, 1),
    };
}

function parseColor(value: string | number): RGBA {
    if (typeof value === "number") {
        const color = value >>> 0;
        const hasExplicitAlpha = color > 0x00ffffff || value < 0;
        return {
            a: hasExplicitAlpha ? ((color >>> 24) & 0xff) / 255 : 1,
            r: (color >>> 16) & 0xff,
            g: (color >>> 8) & 0xff,
            b: color & 0xff,
        };
    }

    const normalized = value.trim().toLowerCase();
    const resolved = NAMED_COLORS[normalized] ?? normalized;
    const parsed = resolved.startsWith("#") ? parseHex(resolved) : parseRgb(resolved);
    if (!parsed) {
        throw new TypeError(`Unsupported color value: ${value}`);
    }
    return parsed;
}

function rgbaToString(value: RGBA) {
    const r = Math.round(clamp(value.r, 0, 255));
    const g = Math.round(clamp(value.g, 0, 255));
    const b = Math.round(clamp(value.b, 0, 255));
    const a = Math.round(clamp(value.a, 0, 1) * 10000) / 10000;
    return `rgba(${r}, ${g}, ${b}, ${a})`;
}

function rgbaToNumber(value: RGBA) {
    const alpha = Math.round(clamp(value.a, 0, 1) * 255);
    return (
        (alpha << 24)
        | (Math.round(clamp(value.r, 0, 255)) << 16)
        | (Math.round(clamp(value.g, 0, 255)) << 8)
        | Math.round(clamp(value.b, 0, 255))
    );
}

function rgbToHsv({ r, g, b, a }: RGBA) {
    const red = r / 255;
    const green = g / 255;
    const blue = b / 255;
    const maximum = Math.max(red, green, blue);
    const minimum = Math.min(red, green, blue);
    const delta = maximum - minimum;
    let hue = 0;
    if (delta !== 0) {
        if (maximum === red) hue = 60 * (((green - blue) / delta) % 6);
        else if (maximum === green) hue = 60 * ((blue - red) / delta + 2);
        else hue = 60 * ((red - green) / delta + 4);
    }
    if (hue < 0) hue += 360;
    return { h: hue, s: maximum === 0 ? 0 : delta / maximum, v: maximum, a };
}

function hsvToRgb(h: number, s: number, v: number, a: number): RGBA {
    const chroma = v * s;
    const hue = ((h % 360) + 360) % 360;
    const section = hue / 60;
    const x = chroma * (1 - Math.abs((section % 2) - 1));
    let red = 0;
    let green = 0;
    let blue = 0;
    if (section < 1) [red, green] = [chroma, x];
    else if (section < 2) [red, green] = [x, chroma];
    else if (section < 3) [green, blue] = [chroma, x];
    else if (section < 4) [green, blue] = [x, chroma];
    else if (section < 5) [red, blue] = [x, chroma];
    else [red, blue] = [chroma, x];
    const offset = v - chroma;
    return { r: (red + offset) * 255, g: (green + offset) * 255, b: (blue + offset) * 255, a };
}

function rgbToLab(value: RGBA) {
    const linear = [value.r, value.g, value.b].map(channel => {
        const normalized = channel / 255;
        return normalized <= 0.04045 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4;
    });
    const x = (linear[0] * 0.4124 + linear[1] * 0.3576 + linear[2] * 0.1805) / 0.95047;
    const y = linear[0] * 0.2126 + linear[1] * 0.7152 + linear[2] * 0.0722;
    const z = (linear[0] * 0.0193 + linear[1] * 0.1192 + linear[2] * 0.9505) / 1.08883;
    const convert = (component: number) => component > 0.008856
        ? component ** (1 / 3)
        : 7.787 * component + 16 / 116;
    const fx = convert(x);
    const fy = convert(y);
    const fz = convert(z);
    return { l: 116 * fy - 16, a: 500 * (fx - fy), b: 200 * (fy - fz), alpha: value.a };
}

function labToRgb(l: number, a: number, b: number, alpha: number): RGBA {
    const fy = (l + 16) / 116;
    const fx = a / 500 + fy;
    const fz = fy - b / 200;
    const convert = (component: number) => {
        const cubed = component ** 3;
        return cubed > 0.008856 ? cubed : (component - 16 / 116) / 7.787;
    };
    const x = 0.95047 * convert(fx);
    const y = convert(fy);
    const z = 1.08883 * convert(fz);
    const linear = [
        x * 3.2406 + y * -1.5372 + z * -0.4986,
        x * -0.9689 + y * 1.8758 + z * 0.0415,
        x * 0.0557 + y * -0.204 + z * 1.057,
    ];
    const convertChannel = (channel: number) => 255 * (channel <= 0.0031308
        ? 12.92 * channel
        : 1.055 * channel ** (1 / 2.4) - 0.055);
    return {
        r: convertChannel(linear[0]),
        g: convertChannel(linear[1]),
        b: convertChannel(linear[2]),
        a: alpha,
    };
}

function mix(start: number, end: number, progress: number) {
    return start + (end - start) * progress;
}

function interpolatePair(
    start: RGBA,
    end: RGBA,
    progress: number,
    colorSpace: ColorSpace,
    options: InterpolateColorOptions,
): RGBA {
    if (colorSpace === "HSV") {
        const from = rgbToHsv(start);
        const to = rgbToHsv(end);
        let delta = to.h - from.h;
        if (options.useCorrectedHSVInterpolation !== false && Math.abs(delta) > 180) {
            delta -= Math.sign(delta) * 360;
        }
        return hsvToRgb(
            from.h + delta * progress,
            mix(from.s, to.s, progress),
            mix(from.v, to.v, progress),
            mix(from.a, to.a, progress),
        );
    }

    if (colorSpace === "LAB") {
        const from = rgbToLab(start);
        const to = rgbToLab(end);
        return labToRgb(
            mix(from.l, to.l, progress),
            mix(from.a, to.a, progress),
            mix(from.b, to.b, progress),
            mix(from.alpha, to.alpha, progress),
        );
    }

    const gamma = options.gamma ?? 2.2;
    const interpolateChannel = (from: number, to: number) => (
        mix((from / 255) ** gamma, (to / 255) ** gamma, progress) ** (1 / gamma)
    ) * 255;
    return {
        r: interpolateChannel(start.r, end.r),
        g: interpolateChannel(start.g, end.g),
        b: interpolateChannel(start.b, end.b),
        a: mix(start.a, end.a, progress),
    };
}

export function interpolateColor(
    value: number,
    inputRange: readonly number[],
    outputRange: readonly (string | number)[],
    colorSpace: ColorSpace = "RGB",
    options: InterpolateColorOptions = {},
) {
    validateRanges(inputRange, outputRange);
    const index = findSegment(value, inputRange);
    const startInput = inputRange[index];
    const endInput = inputRange[index + 1];
    const progress = endInput === startInput ? 1 : clamp((value - startInput) / (endInput - startInput), 0, 1);
    const result = interpolatePair(
        parseColor(outputRange[index]),
        parseColor(outputRange[index + 1]),
        progress,
        colorSpace,
        options,
    );
    return outputRange.every(color => typeof color === "number") ? rgbaToNumber(result) : rgbaToString(result);
}

export function processColor(value: string | number) {
    try {
        return rgbaToNumber(parseColor(value));
    } catch {
        return null;
    }
}

export function convertToRGBA(value: string | number) {
    const color = parseColor(value);
    return [color.r, color.g, color.b, color.a] as const;
}

export function contrastColor(value: string | number): "white" | "black" {
    let color: RGBA;
    try {
        color = parseColor(value);
    } catch {
        return "white";
    }
    const linearChannel = (channel: number) => {
        const normalized = channel / 255;
        return normalized <= 0.04045
            ? normalized / 12.92
            : ((normalized + 0.055) / 1.055) ** 2.4;
    };
    const luminance = 0.2126 * linearChannel(color.r)
        + 0.7152 * linearChannel(color.g)
        + 0.0722 * linearChannel(color.b);
    const contrastWithBlack = (luminance + 0.05) / 0.05;
    const contrastWithWhite = 1.05 / (luminance + 0.05);
    return contrastWithBlack > contrastWithWhite ? "black" : "white";
}

export interface DynamicColorIOSConfig {
    light: string | number;
    dark: string | number;
    highContrastLight?: string | number;
    highContrastDark?: string | number;
}

export function DynamicColorIOS(_config: DynamicColorIOSConfig): never {
    throw new UnsupportedPlatformError("DynamicColorIOS", "SpotifyPlus Android UI V8");
}
