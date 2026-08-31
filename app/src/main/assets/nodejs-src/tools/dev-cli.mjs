#!/usr/bin/env node
import { execFile as execFileCallback } from "node:child_process";
import fs from "node:fs";
import http from "node:http";
import path from "node:path";
import { promisify } from "node:util";
import {
    DEFAULT_DEV_PORT,
    parseCliArgs,
} from "./cli-options.mjs";
import {
    colorizeDevLogOutput,
    createDevSourceMapper,
    formatDevLogEntry,
} from "./dev-source-map.mjs";
import { bundleExtension, readDeclaredAssets } from "./extension-build.mjs";

const execFile = promisify(execFileCallback);
const RUNTIME_PORT = 37846;
const WATCH_EXTENSIONS = new Set([
    ".js",
    ".jsx",
    ".ts",
    ".tsx",
    ".json",
    ".ttf",
    ".otf",
    ".ttc",
    ".png",
    ".jpg",
    ".jpeg",
    ".gif",
    ".webp",
    ".bmp",
    ".svg",
    ".txt",
    ".xml",
    ".mp3",
    ".ogg",
    ".wav",
    ".m4a",
    ".mp4",
    ".webm",
]);
const IGNORED_DIRECTORIES = new Set(["node_modules", ".git", "dist", "build", ".gradle"]);

function shouldColorizeOutput() {
    if (Object.hasOwn(process.env, "FORCE_COLOR")) return process.env.FORCE_COLOR !== "0";
    if (Object.hasOwn(process.env, "NO_COLOR")) return false;
    return process.stdout.isTTY === true || process.stderr.isTTY === true;
}

function printHelp() {
    console.log(`Usage:
  spotifyplus dev [extensionDir] [options]
  spotifyplus build [extensionDir] [options]

Build options:
  --entry <path>          source entry, relative to the extension directory
  --outfile <path>        output bundle, relative to the extension directory
  --minify                minify the output bundle
  --sourcemap             write an external source map

Dev options:
  --adb <path>            adb executable to use (default: adb)
  --device <serial>       adb device serial
  --port <port>           forwarded hot reload port (default: ${DEFAULT_DEV_PORT})
  --debounce-ms <ms>      file change debounce (default: 150)

All commands:
  --help, -h              show this help
`);
}

async function adb(options, args) {
    const fullArgs = [];
    if (options.device) fullArgs.push("-s", options.device);
    fullArgs.push(...args);

    try {
        return await execFile(options.adb, fullArgs, { windowsHide: true });
    } catch (error) {
        const stderr = error?.stderr ? String(error.stderr).trim() : "";
        const stdout = error?.stdout ? String(error.stdout).trim() : "";
        const details = stderr || stdout || error?.message || "adb command failed";
        throw new Error(`${options.adb} ${fullArgs.join(" ")} failed: ${details}`);
    }
}

async function postHotReloadBundle(port, buildInfo) {
    const assets = await readDeclaredAssets(buildInfo.scriptDir, buildInfo.manifest);
    const body = JSON.stringify({
        buildId: buildInfo.buildId,
        manifest: buildInfo.manifest,
        source: buildInfo.source,
        assets,
    });

    return new Promise((resolve, reject) => {
        const request = http.request({
            headers: {
                "content-length": Buffer.byteLength(body),
                "content-type": "application/json; charset=utf-8",
            },
            host: "127.0.0.1",
            method: "POST",
            path: "/hot-reload",
            port,
        }, response => {
            response.setEncoding("utf8");
            let responseBody = "";
            response.on("data", chunk => {
                responseBody += chunk;
            });
            response.on("end", () => {
                const status = response.statusCode ?? 0;
                if (status >= 200 && status < 300) {
                    resolve(responseBody);
                    return;
                }

                let responsePayload = null;
                try {
                    responsePayload = JSON.parse(responseBody);
                } catch { }
                const error = new Error(
                    responsePayload?.error
                        ? `SpotifyPlus runtime returned HTTP ${status}: ${responsePayload.error}`
                        : `SpotifyPlus runtime returned HTTP ${status}: ${responseBody}`,
                );
                error.runtimeStack = responsePayload?.stack;
                reject(error);
            });
        });

        request.on("error", error => {
            const connectionError = new Error(
                `Could not reach SpotifyPlus runtime on forwarded port ${port}. Open Spotify and wait for the script runtime to start, then try again. ${error.message}`,
            );
            connectionError.runtimeUnavailable = true;
            reject(connectionError);
        });
        request.setTimeout(5000, () => {
            request.destroy(new Error(`Timed out connecting to SpotifyPlus runtime on forwarded port ${port}`));
        });
        request.end(body);
    });
}

function startDevLogStream(port, onEntry) {
    let stopped = false;
    let activeRequest = null;
    let reconnectHandle = null;
    let unsupportedWarningShown = false;

    const scheduleReconnect = () => {
        if (stopped || reconnectHandle) return;
        reconnectHandle = setTimeout(() => {
            reconnectHandle = null;
            connect();
        }, 500);
    };

    const connect = () => {
        if (stopped) return;

        let reconnectScheduled = false;
        const reconnect = () => {
            if (reconnectScheduled) return;
            reconnectScheduled = true;
            scheduleReconnect();
        };

        const request = http.request({
            headers: {
                accept: "text/event-stream",
            },
            host: "127.0.0.1",
            method: "GET",
            path: "/dev-logs",
            port,
        }, response => {
            if (response.statusCode !== 200) {
                response.resume();
                if (!unsupportedWarningShown) {
                    unsupportedWarningShown = true;
                    console.warn(
                        `[spotifyplus] runtime log streaming is unavailable (HTTP ${response.statusCode ?? 0}); install the current debug build to mirror console output`,
                    );
                }
                reconnect();
                return;
            }

            unsupportedWarningShown = false;
            response.setEncoding("utf8");
            let buffer = "";
            response.on("data", chunk => {
                buffer += chunk;
                const events = buffer.split(/\r?\n\r?\n/);
                buffer = events.pop() ?? "";

                for (const event of events) {
                    const data = event
                        .split(/\r?\n/)
                        .filter(line => line.startsWith("data:"))
                        .map(line => line.slice(5).trimStart())
                        .join("\n");
                    if (!data) continue;

                    try {
                        onEntry(JSON.parse(data));
                    } catch (error) {
                        console.warn(`[spotifyplus] ignored malformed runtime log entry: ${error.message}`);
                    }
                }
            });
            response.on("end", reconnect);
            response.on("error", reconnect);
        });

        activeRequest = request;
        request.on("error", reconnect);
        request.end();
    };

    connect();
    return () => {
        stopped = true;
        clearTimeout(reconnectHandle);
        activeRequest?.destroy();
    };
}

async function notifyDevice(options, buildInfo) {
    await adb(options, ["forward", `tcp:${options.port}`, `tcp:${RUNTIME_PORT}`]);
    await postHotReloadBundle(options.port, buildInfo);
}

function shouldWatchFile(filePath) {
    return WATCH_EXTENSIONS.has(path.extname(filePath).toLowerCase());
}

function watchRecursively(root, onChange) {
    const watchers = [];

    const watchDirectory = directory => {
        const watcher = fs.watch(directory, (eventType, fileName) => {
            const changedPath = fileName ? path.join(directory, fileName.toString()) : directory;
            if (shouldWatchFile(changedPath)) onChange(eventType, changedPath);

            fs.promises.stat(changedPath).then(stats => {
                if (stats.isDirectory() && !IGNORED_DIRECTORIES.has(path.basename(changedPath))) {
                    watchDirectory(changedPath);
                }
            }).catch(() => { });
        });
        watchers.push(watcher);
    };

    const walk = directory => {
        watchDirectory(directory);
        for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
            if (!entry.isDirectory() || IGNORED_DIRECTORIES.has(entry.name)) continue;
            walk(path.join(directory, entry.name));
        }
    };

    try {
        watchers.push(fs.watch(root, { recursive: true }, (eventType, fileName) => {
            const changedPath = fileName ? path.join(root, fileName.toString()) : root;
            if (shouldWatchFile(changedPath)) onChange(eventType, changedPath);
        }));
    } catch {
        walk(root);
    }

    return () => {
        for (const watcher of watchers) watcher.close();
    };
}

async function runBuild(options) {
    const buildInfo = await bundleExtension({
        entryPath: options.entryPath,
        logLevel: "info",
        minify: options.minify,
        outfile: options.outfile,
        scriptDir: options.scriptDir,
        sourcemap: options.sourcemap,
        write: true,
    });
    console.log(`[spotifyplus] built ${buildInfo.manifest.id} -> ${buildInfo.outfile}`);
}

async function runDev(options) {
    await adb(options, ["forward", `tcp:${options.port}`, `tcp:${RUNTIME_PORT}`]);
    console.log(`[spotifyplus] forwarding http://127.0.0.1:${options.port} to the SpotifyPlus runtime`);
    console.log(`[spotifyplus] watching ${options.scriptDir}`);

    let buildInFlight = false;
    let pendingBuild = false;
    let reconnectBuildHandle = null;
    let runtimeUnavailableReported = false;
    let activeScriptId = null;
    let sourceMapper = createDevSourceMapper("", options.scriptDir, "index.js");
    const colorizeOutput = shouldColorizeOutput();

    const closeLogStream = startDevLogStream(options.port, entry => {
        const mapper = !entry.scriptId || entry.scriptId === activeScriptId
            ? sourceMapper
            : createDevSourceMapper("", options.scriptDir, "index.js");
        const output = colorizeDevLogOutput(
            formatDevLogEntry(entry, mapper),
            entry.level,
            colorizeOutput,
        );
        if (entry.level === "error") console.error(output);
        else if (entry.level === "warn") console.warn(output);
        else console.log(output);
    });

    const rebuild = async reason => {
        if (buildInFlight) {
            pendingBuild = true;
            return;
        }

        buildInFlight = true;
        clearTimeout(reconnectBuildHandle);
        reconnectBuildHandle = null;
        try {
            const buildInfo = await bundleExtension({
                entryPath: options.entryPath,
                scriptDir: options.scriptDir,
                sourcemap: "inline",
                write: false,
            });
            activeScriptId = buildInfo.manifest.id;
            sourceMapper = createDevSourceMapper(
                buildInfo.source,
                buildInfo.scriptDir,
                buildInfo.manifest.main,
            );
            await notifyDevice(options, buildInfo);
            runtimeUnavailableReported = false;
            console.log(`[spotifyplus] reloaded ${buildInfo.manifest.id} (${buildInfo.buildId})`);
        } catch (error) {
            const runtimeUnavailable = error?.runtimeUnavailable === true;
            if (!runtimeUnavailable || !runtimeUnavailableReported) {
                console.error(`[spotifyplus] ${reason} failed`);
                console.error(sourceMapper.map(error?.runtimeStack ?? error?.message ?? error));
                if (runtimeUnavailable) console.error("[spotifyplus] retrying when the runtime is available...");
                if (Array.isArray(error?.errors)) {
                    for (const item of error.errors) console.error(item.text ?? item);
                }
            }
            runtimeUnavailableReported = runtimeUnavailable;
            if (runtimeUnavailable) reconnectBuildHandle = setTimeout(
                () => rebuild("runtime connection retry"),
                1000,
            );
        } finally {
            buildInFlight = false;
            if (pendingBuild) {
                pendingBuild = false;
                await rebuild("queued rebuild");
            }
        }
    };

    let debounceHandle = null;
    const scheduleRebuild = (_eventType, filePath) => {
        clearTimeout(debounceHandle);
        debounceHandle = setTimeout(
            () => rebuild(`rebuild after ${path.relative(options.scriptDir, filePath)}`),
            options.debounceMs,
        );
    };

    const closeWatchers = watchRecursively(options.scriptDir, scheduleRebuild);
    await rebuild("initial build");

    const close = () => {
        clearTimeout(reconnectBuildHandle);
        closeLogStream();
        closeWatchers();
        process.exit(0);
    };
    process.on("SIGINT", close);
    process.on("SIGTERM", close);
}

async function run() {
    const options = parseCliArgs(process.argv.slice(2));
    if (options.help) {
        printHelp();
        return;
    }
    if (options.command === "build") {
        await runBuild(options);
        return;
    }
    await runDev(options);
}

run().catch(error => {
    console.error(error?.stack ?? error);
    process.exit(1);
});
