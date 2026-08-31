import path from "node:path";

export const DEFAULT_DEV_PORT = 37846;

function parsePositiveInteger(value, name) {
    const parsed = Number(value);
    if (!Number.isInteger(parsed) || parsed <= 0) throw new Error(`${name} must be a positive integer`);
    return parsed;
}

export function parseCliArgs(argv, cwd = process.cwd()) {
    const args = [...argv];
    let command = "dev";

    if (args[0] === "dev" || args[0] === "build") command = args.shift();
    else if (args[0] === "help") {
        args.shift();
        args.unshift("--help");
    }

    const options = {
        adb: process.env.ADB || "adb",
        command,
        debounceMs: 150,
        device: "",
        entryPath: undefined,
        help: false,
        minify: false,
        outfile: undefined,
        port: DEFAULT_DEV_PORT,
        scriptDir: cwd,
        sourcemap: false,
    };
    const positional = [];

    for (let index = 0; index < args.length; index++) {
        const argument = args[index];
        if (argument === "--help" || argument === "-h") {
            options.help = true;
            continue;
        }
        if (argument === "--minify") {
            options.minify = true;
            continue;
        }
        if (argument === "--sourcemap") {
            options.sourcemap = true;
            continue;
        }
        if (!argument.startsWith("--")) {
            positional.push(argument);
            continue;
        }

        const separator = argument.indexOf("=");
        const rawName = separator === -1 ? argument.slice(2) : argument.slice(2, separator);
        const inlineValue = separator === -1 ? undefined : argument.slice(separator + 1);
        const value = inlineValue ?? args[++index];
        if (value == null || value.startsWith("--")) throw new Error(`Missing value for --${rawName}`);

        switch (rawName) {
            case "adb":
                options.adb = value;
                break;
            case "debounce-ms":
                options.debounceMs = parsePositiveInteger(value, "--debounce-ms");
                break;
            case "device":
                options.device = value;
                break;
            case "entry":
                options.entryPath = value;
                break;
            case "outfile":
                options.outfile = value;
                break;
            case "port":
                options.port = parsePositiveInteger(value, "--port");
                break;
            default:
                throw new Error(`Unknown option --${rawName}`);
        }
    }

    if (positional.length > 1) throw new Error("Expected at most one extension directory");
    if (positional[0]) options.scriptDir = positional[0];
    options.scriptDir = path.resolve(cwd, options.scriptDir);

    if (command === "dev" && options.outfile) throw new Error("--outfile is only available with spotifyplus build");
    return options;
}
