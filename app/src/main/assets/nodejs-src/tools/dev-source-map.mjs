import { SourceMap } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";

const INLINE_SOURCE_MAP_PATTERN = /sourceMappingURL=data:application\/json(?:;charset=[^;,]+)?;base64,([^\r\n]+)/;
const STACK_LOCATION_PATTERN = /((?:file:\/\/\/|\/|[A-Za-z]:[\\/])[^()\r\n]+?):(\d+):(\d+)/g;
const DEV_LOG_COLORS = {
    error: "\u001b[31m",
    log: "\u001b[36m",
    warn: "\u001b[33m",
};
const ANSI_RESET = "\u001b[0m";

function readInlineSourceMap(bundleSource) {
    const match = String(bundleSource).match(INLINE_SOURCE_MAP_PATTERN);
    if (!match) return null;

    try {
        return JSON.parse(Buffer.from(match[1], "base64").toString("utf8"));
    } catch {
        return null;
    }
}

function locationBasename(location) {
    let filePath = location;
    if (filePath.startsWith("file:///")) {
        try {
            filePath = fileURLToPath(filePath);
        } catch { }
    }
    return path.posix.basename(filePath.replaceAll("\\", "/"));
}

function resolveOriginalSource(scriptDir, source) {
    if (source.startsWith("file:")) {
        try {
            return fileURLToPath(source);
        } catch { }
    }
    if (path.isAbsolute(source)) return path.normalize(source);
    return path.resolve(scriptDir, source);
}

export function createDevSourceMapper(bundleSource, scriptDir, generatedFile) {
    const payload = readInlineSourceMap(bundleSource);
    const generatedBasename = path.basename(generatedFile);
    const sourceMap = payload ? new SourceMap(payload) : null;

    const mapLocation = (location, lineText, columnText) => {
        if (!sourceMap || locationBasename(location) !== generatedBasename) return null;

        const line = Number.parseInt(lineText, 10);
        const column = Number.parseInt(columnText, 10);
        if (!Number.isFinite(line) || !Number.isFinite(column) || line < 1 || column < 1) return null;

        const entry = sourceMap.findEntry(line - 1, column - 1);
        if (!entry.originalSource) return null;

        return `${resolveOriginalSource(scriptDir, entry.originalSource)}:${entry.originalLine + 1}:${entry.originalColumn + 1}`;
    };

    return {
        map(text) {
            return String(text).replace(
                STACK_LOCATION_PATTERN,
                (match, location, line, column) => mapLocation(location, line, column) ?? match,
            );
        },
        firstOriginalLocation(text) {
            let firstLocation = null;
            String(text).replace(STACK_LOCATION_PATTERN, (match, location, line, column) => {
                firstLocation ??= mapLocation(location, line, column);
                return match;
            });
            return firstLocation;
        },
    };
}

export function formatDevLogEntry(entry, sourceMapper) {
    const prefix = entry.scriptId ? `[${entry.scriptId}]` : "[node]";
    const message = sourceMapper.map(entry.message ?? "");
    const originalLocation = sourceMapper.firstOriginalLocation(entry.stack ?? "");
    if (!originalLocation || message.includes(originalLocation)) return `${prefix} ${message}`;
    return `${prefix} ${message}\n    at ${originalLocation}`;
}

export function colorizeDevLogOutput(output, level, enabled) {
    if (!enabled) return output;
    const color = DEV_LOG_COLORS[level] ?? DEV_LOG_COLORS.log;
    return `${color}${output}${ANSI_RESET}`;
}
