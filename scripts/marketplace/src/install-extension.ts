import { marketplaceManifestSchema } from './fetch-metadata';
import { InstalledExtensionInfo, MarketplaceExtension, MarketplaceRepository } from './types/extension';

interface ExtensionInstallFile {
    path: string;
    data: Uint8Array;
}

interface ElevatedMarketplaceApi {
    listInstalledExtensions(): InstalledExtensionInfo[];
    installExtension(request: {
        manifest: unknown;
        files: ExtensionInstallFile[];
    }): InstalledExtensionInfo;
    uninstallExtension(extensionId: string): InstalledExtensionInfo;
}

interface GitHubCommitResponse {
    sha?: string;
    message?: string;
}

interface GitHubTreeEntry {
    path?: string;
    type?: string;
}

interface GitHubTreeResponse {
    tree?: GitHubTreeEntry[];
    truncated?: boolean;
    message?: string;
}

export interface InstallProgress {
    completed: number;
    total: number;
}

const DOWNLOAD_CONCURRENCY = 4;

export const listInstalledExtensions = (): InstalledExtensionInfo[] => {
    return getElevatedApi().listInstalledExtensions();
};

export const uninstallMarketplaceExtension = (extensionId: string): InstalledExtensionInfo => {
    return getElevatedApi().uninstallExtension(extensionId);
};

export const installMarketplaceExtension = async (extension: MarketplaceExtension, onProgress?: (progress: InstallProgress) => void,): Promise<InstalledExtensionInfo> => {
    if (!extension.repository) {
        throw new Error('This extension does not include a repository source');
    }

    const { repository } = extension;
    const commit = await fetchCommit(repository);
    const manifest = await fetchPinnedManifest(repository, commit, extension.id);
    const packagePaths = await resolvePackagePaths(repository, commit, manifest);
    const files = await downloadPackageFiles(repository, commit, packagePaths, onProgress);

    return getElevatedApi().installExtension({
        manifest,
        files,
    });
};

const getElevatedApi = (): ElevatedMarketplaceApi => {
    const elevated = (globalThis as any).__spotifyplus_elevated__ as ElevatedMarketplaceApi | undefined;
    if (!elevated?.installExtension || !elevated?.uninstallExtension || !elevated?.listInstalledExtensions) {
        throw new Error('Marketplace installation is unavailable in this Spotify Plus build');
    }
    return elevated;
};

const fetchCommit = async (repository: MarketplaceRepository): Promise<string> => {
    const url = `${githubApiBase(repository)}/commits/${encodeURIComponent(repository.branch)}`;
    const response = await fetch(url);
    const body = await response.json() as GitHubCommitResponse;

    if (!response.ok || !body.sha) {
        throw new Error(body.message || `GitHub could not resolve ${repository.branch}`);
    }
    return body.sha;
};

const fetchPinnedManifest = async (repository: MarketplaceRepository, commit: string, extensionId: string,) => {
    const response = await fetch(rawRepositoryUrl(repository, commit, 'manifest.json'));
    if (!response.ok) throw new Error(`Could not download manifest.json (${response.status})`);

    const value = await response.json();
    const manifests = Array.isArray(value) ? value : [value];
    const selected = manifests.find((manifest) => {
        return manifest && typeof manifest === 'object' && manifest.id === extensionId;
    });
    if (!selected) throw new Error(`manifest.json no longer contains ${extensionId}`);

    const parsed = marketplaceManifestSchema.safeParse(selected);
    if (!parsed.success) throw new Error(`The repository manifest is invalid: ${parsed.error.message}`);

    return parsed.data;
};

const resolvePackagePaths = async (repository: MarketplaceRepository, commit: string, manifest: ReturnType<typeof marketplaceManifestSchema.parse>,): Promise<string[]> => {
    const response = await fetch(`${githubApiBase(repository)}/git/trees/${commit}?recursive=1`);
    const body = await response.json() as GitHubTreeResponse;

    if (!response.ok || !body.tree) {
        throw new Error(body.message || 'GitHub could not list the extension files');
    }

    if (body.truncated) {
        throw new Error('The repository is too large for GitHub to return a complete file list');
    }

    const repositoryFiles = new Set(body.tree.filter((entry) => entry.type === 'blob' && typeof entry.path === 'string').map((entry) => normalizeRepositoryPath(entry.path!, 'repository file')),);
    const mainPath = normalizeRepositoryPath(manifest.main, 'manifest.main');
    const mainDirectory = repositoryDirectory(mainPath);
    const nativePath = manifest.native ? joinRepositoryPath(mainDirectory, normalizeRelativeEntryPath(manifest.native.apk, 'manifest.native.apk')) : undefined;
    const assetPatterns = (manifest.assets ?? []).map((pattern, index) => ({
        pattern: normalizeRelativeEntryPath(pattern, `manifest.assets[${index}]`, true),
        expression: globExpression(normalizeRelativeEntryPath(pattern, `manifest.assets[${index}]`, true)),
        matches: 0,
    }));

    const selectedPaths = new Set<string>();

    requireRepositoryFile(repositoryFiles, mainPath, 'main entry');
    selectedPaths.add(mainPath);

    if (nativePath) {
        requireRepositoryFile(repositoryFiles, nativePath, 'native APK');
        selectedPaths.add(nativePath);
    }

    for (const repositoryPath of repositoryFiles) {
        const entryRelativePath = relativeToEntryDirectory(mainDirectory, repositoryPath);
        if (entryRelativePath === undefined) continue;

        for (const asset of assetPatterns) {
            if (!asset.expression.test(entryRelativePath)) continue;
            asset.matches += 1;
            selectedPaths.add(repositoryPath);
        }
    }

    const unmatched = assetPatterns.filter((asset) => asset.matches === 0).map((asset) => asset.pattern);
    if (unmatched.length > 0) {
        throw new Error(`Asset patterns matched no repository files: ${unmatched.join(', ')}`);
    }
    return Array.from(selectedPaths).sort();
};

const downloadPackageFiles = async (repository: MarketplaceRepository, commit: string, paths: string[], onProgress?: (progress: InstallProgress) => void,): Promise<ExtensionInstallFile[]> => {
    const files = new Array<ExtensionInstallFile>(paths.length);
    let nextIndex = 0;
    let completed = 0;
    onProgress?.({ completed, total: paths.length });

    const worker = async () => {
        while (nextIndex < paths.length) {
            const index = nextIndex;
            nextIndex += 1;
            const repositoryPath = paths[index];
            const response = await fetch(rawRepositoryUrl(repository, commit, repositoryPath));
            if (!response.ok) throw new Error(`Could not download ${repositoryPath} (${response.status})`);

            files[index] = {
                path: repositoryPath,
                data: new Uint8Array(await response.arrayBuffer()),
            };
            completed += 1;
            onProgress?.({ completed, total: paths.length });
        }
    };

    await Promise.all(Array.from({ length: Math.min(DOWNLOAD_CONCURRENCY, paths.length) }, () => worker(),),);
    return files;
};

const githubApiBase = (repository: MarketplaceRepository): string => {
    return `https://api.github.com/repos/${encodeURIComponent(repository.owner)}/${encodeURIComponent(repository.name)}`;
};

const rawRepositoryUrl = (repository: MarketplaceRepository, commit: string, repositoryPath: string,): string => {
    const encodedPath = repositoryPath.split('/').map(encodeURIComponent).join('/');
    return `https://raw.githubusercontent.com/${encodeURIComponent(repository.owner)}/${encodeURIComponent(repository.name)}/${commit}/${encodedPath}`;
};

const normalizeRepositoryPath = (value: string, fieldName: string): string => {
    const normalized = normalizeRelativeEntryPath(value, fieldName);
    if (/[*?]/.test(normalized)) throw new Error(`${fieldName} cannot contain glob characters`);
    return normalized;
};

const normalizeRelativeEntryPath = (value: string, fieldName: string, allowGlob = false,): string => {
    if (typeof value !== 'string' || !value.trim() || value.includes('\0')) {
        throw new Error(`${fieldName} must be a non-empty relative path`);
    }

    if (value.startsWith('/') || value.startsWith('\\') || /^[a-zA-Z]:/.test(value)) {
        throw new Error(`${fieldName} must be a relative path`);
    }

    const normalized = value.trim().replace(/\\/g, '/').replace(/^\.\//, '');
    const segments = normalized.split('/');

    if (segments.some((segment) => !segment || segment === '.' || segment === '..')) {
        throw new Error(`${fieldName} cannot escape its base directory`);
    }
    if (!allowGlob && /[*?]/.test(normalized)) {
        throw new Error(`${fieldName} cannot contain glob characters`);
    }

    return normalized;
};

const repositoryDirectory = (repositoryPath: string): string => {
    const separator = repositoryPath.lastIndexOf('/');
    return separator === -1 ? '' : repositoryPath.slice(0, separator);
};

const joinRepositoryPath = (directory: string, relativePath: string): string => {
    return directory ? `${directory}/${relativePath}` : relativePath;
};

const relativeToEntryDirectory = (directory: string, repositoryPath: string): string | undefined => {
    if (!directory) return repositoryPath;
    const prefix = `${directory}/`;
    return repositoryPath.startsWith(prefix) ? repositoryPath.slice(prefix.length) : undefined;
};

const requireRepositoryFile = (repositoryFiles: Set<string>, repositoryPath: string, description: string,) => {
    if (!repositoryFiles.has(repositoryPath)) {
        throw new Error(`The ${description} is missing from the repository: ${repositoryPath}`);
    }
};

const globExpression = (pattern: string): RegExp => {
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
    return new RegExp(`^${expression}$`);
};
