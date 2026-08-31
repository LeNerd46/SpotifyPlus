export interface ScriptManifest {
    id: string;
    name: string;
    version: string;
    main: string;
    description?: string;
    author?: string;
    permissions: string[];
    api: number;
    assets: string[];
    native?: NativeObject;
}

export const CURRENT_SCRIPT_API = 2;
export const LEGACY_SCRIPT_API = 1;

interface NativeObject {
    apk: string;
    pluginClass: string;
}

export function parseManifest(raw: unknown): ScriptManifest {
    if (!raw || typeof raw !== 'object') throw new Error('Manifest must be an object');
    const manifest = raw as Record<string, unknown>;

    const id = ensureString(manifest.id, 'manifest.id');
    const name = ensureString(manifest.name, 'manifest.name');
    const version = ensureString(manifest.version, 'manifest.version');
    const main = normalizeManifestPath(ensureString(manifest.main, 'manifest.main'), 'manifest.main');
    const description = optionalString(manifest.description, 'manifest.description');
    const author = optionalString(manifest.author, 'manifest.author');
    if (manifest.api !== undefined && typeof manifest.api !== 'number') {
        throw new Error('manifest.api must be an integer');
    }
    const api = manifest.api === undefined ? LEGACY_SCRIPT_API : manifest.api;
    if (!Number.isInteger(api)) throw new Error('manifest.api must be an integer');
    if (api !== LEGACY_SCRIPT_API && api !== CURRENT_SCRIPT_API) {
        throw new Error(`Unsupported manifest.api ${api}. SpotifyPlus 0.11 supports API 1 compatibility bundles and transformed API 2 bundles.`);
    }

    const permissions = Array.isArray(manifest.permissions) ? manifest.permissions.map((item, index) => ensureString(item, `manifest.permissions[${index}]`)) : [];
    const assets = Array.isArray(manifest.assets)
        ? manifest.assets.map((item, index) => normalizeManifestPath(
            ensureString(item, `manifest.assets[${index}]`),
            `manifest.assets[${index}]`,
            true,
        ))
        : [];
    const native = parseNativeObject(manifest.native, 'manifest.native');

    return { id, name, version, main, description, author, permissions, api, assets, native };
}

function parseNativeObject(raw: unknown, fieldName: string): NativeObject | undefined {
    if (!raw || typeof raw !== 'object') return undefined;
    const native = raw as Record<string, unknown>;

    const apk = normalizeManifestPath(ensureString(native.apk, `${fieldName}.apk`), `${fieldName}.apk`);
    const pluginClass = ensureString(native.pluginClass, `${fieldName}.pluginClass`);

    return { apk, pluginClass };
}

function ensureString(value: unknown, fieldName: string): string {
    if (typeof value !== 'string' || value.trim().length === 0) throw new Error(`${fieldName} must be a non-empty string`);
    return value;
}

function optionalString(value: unknown, fieldName: string): string | undefined {
    if (value === undefined || value === null) return undefined;
    if (typeof value !== 'string') throw new Error(`${fieldName} must be a string`);
    return value;
}

function normalizeManifestPath(value: string, fieldName: string, allowGlob = false): string {
    if (value.includes('\0') || value.startsWith('/') || value.startsWith('\\') || /^[a-zA-Z]:/.test(value)) {
        throw new Error(`${fieldName} must be a relative path`);
    }

    const normalized = value.trim().replace(/\\/g, '/').replace(/^\.\//, '');
    const segments = normalized.split('/');
    if (segments.some(segment => !segment || segment === '.' || segment === '..')) {
        throw new Error(`${fieldName} cannot escape its base directory`);
    }
    if (!allowGlob && /[*?]/.test(normalized)) {
        throw new Error(`${fieldName} cannot contain glob characters`);
    }
    return normalized;
}
