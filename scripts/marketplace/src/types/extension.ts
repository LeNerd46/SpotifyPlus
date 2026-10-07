export interface MarketplaceAuthor {
    name: string;
    url?: string;
}

export interface MarketplaceNativeExtension {
    apk: string;
    pluginClass: string;
}

export interface MarketplaceRepository {
    owner: string;
    name: string;
    branch: string;
}

export interface InstalledExtensionInfo {
    id: string;
    name: string;
    version?: string;
    description?: string;
    author?: string;
    path: string;
    installedAt: string;
}

export interface MarketplaceExtension {
    id: string;
    name: string;
    description: string;
    version: string;
    api: number;
    main: string;
    authors?: MarketplaceAuthor[];
    tags?: string[];
    preview?: string;
    /** Markdown body of the repository's latest GitHub release. */
    changelog?: string;
    /** Repository root at the release tag, for relative Markdown links and images. */
    changelogBaseUrl?: string;
    assets?: string[];
    native?: MarketplaceNativeExtension;
    githubUrl?: string;
    license?: string;
    stars: number;
    banner?: string;
    repository?: MarketplaceRepository;
}

export const PLACEHOLDER_IMAGE = 'assets/placeholder.jpg';
export const UNKNOWN_AUTHOR = 'Unknown developer';

export const getAuthorNames = (extension: MarketplaceExtension) => {
    const names = extension.authors?.map((author) => author.name.trim()).filter(Boolean);
    return names?.length ? names.join(', ') : UNKNOWN_AUTHOR;
};

export const mockExtensions: MarketplaceExtension[] = [
    {
        id: 'com.lenerd.beautifullyrics',
        name: 'Beautiful Lyrics',
        description: 'Adds beautiful synchronized lyrics to Spotify with custom themes and translation support.',
        version: '1.0.0',
        authors: [
            {
                name: 'LeNerd46',
                url: 'https://www.github.com/LeNerd46',
            },
            {
                name: 'Devon Shoutz',
                url: 'https://github.com/devonshoutz',
            },
        ],
        tags: [
            'lyrics',
            'themes',
            'translation',
        ],
        changelog: '## 1.0.0\n\n- Added **synchronized lyrics**.\n- Added custom themes and translation support.',
        api: 2,
        assets: [
            'assets/**/*',
        ],
        main: 'index.js',
        native: {
            apk: 'lyrics.apk',
            pluginClass: 'com.lenerd.lyricsnative.NativePlugin',
        },
        githubUrl: 'https://github.com/LeNerd46/SpotifyPlus',
        license: 'GPL-3.0',
        stars: 4.8,
    },
    {
        id: 'com.spotifyplus.themeengine',
        name: 'Theme Engine',
        description: 'Customize Spotify colors, surfaces, typography, and accent styles.',
        version: '1.4.3',
        authors: [
            {
                name: 'SystemVibe',
                url: 'https://github.com/systemvibe',
            },
        ],
        tags: [
            'themes',
            'customization',
        ],
        api: 2,
        main: 'index.js',
        githubUrl: 'https://github.com/example/theme-engine',
        license: 'GPL-3.0',
        stars: 3.2,
    },
    {
        id: 'com.spotifyplus.queuetools',
        name: 'Queue Tools',
        description: 'Adds batch removal, duplicate detection, and smarter shuffle tools directly inside Spotify.',
        version: '0.9.1',
        tags: [
            'queue',
            'playback',
            'utility',
        ],
        api: 2,
        main: 'index.js',
        license: 'Apache-2.0',
        stars: 2.4,
    },
];

export const lyricsPlus = mockExtensions[0];
export default mockExtensions;
