import crypto from 'crypto';
import fs from 'fs';
import path from 'path';

export type ExtensionAssetKind = 'asset' | 'font' | 'image';

export interface ExtensionAsset {
    readonly type: 'extension-asset';
    readonly uri: string;
    readonly mimeType: string;
    readonly name: string;
}

export type FontWeight = 100 | 200 | 300 | 400 | 500 | 600 | 700 | 800 | 900;
export type FontStyle = 'normal' | 'italic';

export interface ExtensionFontAsset extends ExtensionAsset {
    readonly assetKind: 'font';
}

export interface ExtensionFontFace {
    source: string;
    weight?: FontWeight;
    style?: FontStyle;
}

export interface ExtensionFontFamily {
    readonly type: 'extension-font-family';
    readonly faces: ReadonlyArray<{
        readonly source: ExtensionFontAsset;
        readonly weight: FontWeight;
        readonly style: FontStyle;
    }>;
}

export interface ExtensionAssetsApi {
    resolve(relativePath: string): ExtensionAsset;
    image(relativePath: string): ExtensionAsset;
    font(relativePath: string): ExtensionFontAsset;
    fontFamily(faces: ReadonlyArray<ExtensionFontFace>): ExtensionFontFamily;
    readText(relativePath: string): string;
    readJson<T = unknown>(relativePath: string): T;
    readBytes(relativePath: string): Uint8Array;
}

export interface NativeAssetRegistration {
    assetId: string;
    scriptId: string;
    generation: number;
    rootPath: string;
    manifestPath: string;
    filePath: string;
    mimeType: string;
    kind: ExtensionAssetKind;
}

const registrations = new WeakMap<object, NativeAssetRegistration>();

export function createExtensionAssetsApi(
    scriptId: string,
    generation: number,
    assetDirectory: string,
    manifestDirectory: string,
    declaredAssets: readonly string[],
): ExtensionAssetsApi {
    const rootPath = fs.realpathSync(assetDirectory);
    const manifestPath = fs.realpathSync(path.join(manifestDirectory, 'manifest.json'));
    const patterns = declaredAssets.map(normalizeAssetPattern);
    const resolvedAssets = new Map<string, ExtensionAsset>();

    const resolveFile = (relativePath: string) => {
        const normalizedPath = normalizeRelativeAssetPath(relativePath);
        if (!patterns.some(pattern => matchesAssetPattern(normalizedPath, pattern))) {
            throw new Error(`Asset '${normalizedPath}' is not declared in manifest.assets`);
        }

        const candidatePath = path.resolve(rootPath, ...normalizedPath.split('/'));
        const filePath = fs.realpathSync(candidatePath);
        assertPathInsideRoot(rootPath, filePath);
        const stat = fs.statSync(filePath);
        if (!stat.isFile()) throw new Error(`Extension asset is not a file: ${normalizedPath}`);
        return { filePath, normalizedPath };
    };

    const resolve = (relativePath: string, kind: ExtensionAssetKind): ExtensionAsset => {
        const { filePath, normalizedPath } = resolveFile(relativePath);
        const cacheKey = `${kind}:${normalizedPath}`;
        const cached = resolvedAssets.get(cacheKey);
        if (cached) return cached;

        const mimeType = mimeTypeForPath(filePath);
        if (kind === 'font' && !isFontMimeType(mimeType)) {
            throw new Error(`Unsupported font asset '${normalizedPath}'. Use .ttf, .otf, or .ttc.`);
        }
        if (kind === 'image' && !isImageMimeType(mimeType)) {
            throw new Error(`Unsupported image asset '${normalizedPath}'`);
        }

        const assetId = `spotifyplus-asset://${crypto.randomUUID()}`;
        const reference = Object.freeze({
            type: 'extension-asset' as const,
            uri: assetId,
            mimeType,
            name: path.basename(filePath),
            ...(kind === 'font' ? { assetKind: 'font' as const } : {}),
        });
        registrations.set(reference, {
            assetId,
            scriptId,
            generation,
            rootPath,
            manifestPath,
            filePath,
            mimeType,
            kind,
        });
        resolvedAssets.set(cacheKey, reference);
        return reference;
    };

    const api: ExtensionAssetsApi = {
        resolve: relativePath => resolve(relativePath, 'asset'),
        image: relativePath => resolve(relativePath, 'image'),
        font: relativePath => resolve(relativePath, 'font') as ExtensionFontAsset,
        fontFamily: faces => {
            if (!Array.isArray(faces) || faces.length === 0) {
                throw new Error('Assets.fontFamily requires at least one font face');
            }

            return Object.freeze({
                type: 'extension-font-family' as const,
                faces: Object.freeze(faces.map(face => Object.freeze({
                    source: resolve(face.source, 'font') as ExtensionFontAsset,
                    weight: normalizeFontWeight(face.weight),
                    style: face.style === 'italic' ? 'italic' as const : 'normal' as const,
                }))),
            });
        },
        readText: relativePath => fs.readFileSync(resolveFile(relativePath).filePath, 'utf8'),
        readJson: <T = unknown>(relativePath: string): T => JSON.parse(
            fs.readFileSync(resolveFile(relativePath).filePath, 'utf8'),
        ) as T,
        readBytes: relativePath => Uint8Array.from(fs.readFileSync(resolveFile(relativePath).filePath)),
    };
    return Object.freeze(api);
}

export function getNativeAssetRegistration(value: unknown): NativeAssetRegistration | undefined {
    if (!value || typeof value !== 'object') return undefined;
    return registrations.get(value);
}

export function normalizeAssetPattern(pattern: string): string {
    if (typeof pattern !== 'string' || pattern.trim().length === 0) {
        throw new Error('manifest.assets entries must be non-empty strings');
    }
    return normalizeRelativeAssetPath(pattern.trim(), true);
}

export function matchesAssetPattern(relativePath: string, pattern: string): boolean {
    let expression = '';
    for (let index = 0; index < pattern.length; index += 1) {
        const character = pattern[index];
        if (character === '*' && pattern[index + 1] === '*') {
            const followedBySlash = pattern[index + 2] === '/';
            expression += followedBySlash ? '(?:.*/)?' : '.*';
            index += followedBySlash ? 2 : 1;
            continue;
        }
        if (character === '*') {
            expression += '[^/]*';
            continue;
        }
        if (character === '?') {
            expression += '[^/]';
            continue;
        }
        expression += /[.+^${}()|[\]\\]/.test(character) ? `\\${character}` : character;
    }
    return new RegExp(`^${expression}$`).test(relativePath);
}

function normalizeRelativeAssetPath(value: string, allowGlob = false): string {
    if (typeof value !== 'string' || value.trim().length === 0 || value.includes('\0')) {
        throw new Error('Extension asset path must be a non-empty relative path');
    }
    if (path.isAbsolute(value) || /^[a-zA-Z]:/.test(value)) {
        throw new Error(`Extension asset path must be relative: ${value}`);
    }

    const normalized = value.replace(/\\/g, '/').replace(/^\.\//, '');
    const segments = normalized.split('/');
    if (segments.some(segment => segment === '' || segment === '.' || segment === '..')) {
        throw new Error(`Extension asset path escapes its extension directory: ${value}`);
    }
    if (!allowGlob && /[*?]/.test(normalized)) {
        throw new Error(`Extension asset path cannot contain glob characters: ${value}`);
    }
    return normalized;
}

function assertPathInsideRoot(rootPath: string, filePath: string): void {
    const relative = path.relative(rootPath, filePath);
    if (relative === '' || (!relative.startsWith(`..${path.sep}`) && relative !== '..' && !path.isAbsolute(relative))) {
        return;
    }
    throw new Error(`Extension asset escaped its extension directory: ${filePath}`);
}

function normalizeFontWeight(weight: FontWeight | undefined): FontWeight {
    if (weight === undefined) return 400;
    const rounded = Math.round(Number(weight) / 100) * 100;
    return Math.max(100, Math.min(900, rounded)) as FontWeight;
}

function isFontMimeType(mimeType: string): boolean {
    return mimeType === 'font/ttf' || mimeType === 'font/otf' || mimeType === 'font/collection';
}

function isImageMimeType(mimeType: string): boolean {
    return mimeType === 'image/png'
        || mimeType === 'image/jpeg'
        || mimeType === 'image/gif'
        || mimeType === 'image/webp'
        || mimeType === 'image/bmp';
}

function mimeTypeForPath(filePath: string): string {
    const extension = path.extname(filePath).toLowerCase();
    return ({
        '.ttf': 'font/ttf',
        '.otf': 'font/otf',
        '.ttc': 'font/collection',
        '.png': 'image/png',
        '.jpg': 'image/jpeg',
        '.jpeg': 'image/jpeg',
        '.gif': 'image/gif',
        '.webp': 'image/webp',
        '.bmp': 'image/bmp',
        '.svg': 'image/svg+xml',
        '.json': 'application/json',
        '.txt': 'text/plain',
        '.xml': 'application/xml',
        '.mp3': 'audio/mpeg',
        '.ogg': 'audio/ogg',
        '.wav': 'audio/wav',
        '.m4a': 'audio/mp4',
        '.mp4': 'video/mp4',
        '.webm': 'video/webm',
    } as Record<string, string>)[extension] ?? 'application/octet-stream';
}
