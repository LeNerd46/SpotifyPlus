import type { InstalledExtensionInfo, MarketplaceExtension } from './types/extension';

// Accept the numeric version formats used by extensions, including four-part versions.
const parseVersion = (version: string) => {
    const match = /^v?(\d+(?:\.\d+)*)(?:-([\da-zA-Z-]+(?:\.[\da-zA-Z-]+)*))?(?:\+[\da-zA-Z.-]+)?$/.exec(version.trim());
    return match ? { parts: match[1].split('.').map(BigInt), prerelease: match[2]?.split('.') } : null;
};

export const isNewerVersion = (available: string, installed?: string): boolean => {
    if (!installed) return false;
    const next = parseVersion(available);
    const current = parseVersion(installed);
    if (!next || !current) return false;
    for (let i = 0; i < Math.max(next.parts.length, current.parts.length); i++) {
        const a = next.parts[i] ?? 0n;
        const b = current.parts[i] ?? 0n;
        if (a !== b) return a > b;
    }
    if (!next.prerelease || !current.prerelease) return !!current.prerelease && !next.prerelease;
    for (let i = 0; i < Math.max(next.prerelease.length, current.prerelease.length); i++) {
        const a = next.prerelease[i];
        const b = current.prerelease[i];
        if (a === b) continue;
        if (a === undefined || b === undefined) return b === undefined;
        const numericA = /^\d+$/.test(a);
        const numericB = /^\d+$/.test(b);
        if (numericA && numericB) {
            if (BigInt(a) === BigInt(b)) continue;
            return BigInt(a) > BigInt(b);
        }
        if (numericA !== numericB) return numericB;
        return a > b;
    }
    return false;
};

export const hasUpdate = (installed: InstalledExtensionInfo, extension?: MarketplaceExtension): boolean => {
    return !!extension?.repository && extension.id === installed.id && isNewerVersion(extension.version, installed.version);
};
