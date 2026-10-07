import { SpotifyPlus } from "spotifyplus";
import z from "zod";
import { MarketplaceExtension } from "./types/extension";

const CACHE_TTL = 15 * 60 * 1000;
const MANIFEST_CACHE_VERSION = 4;

const authorSchema = z.object({
    name: z.string(),
    url: z.string().optional(),
});

const nativeSchema = z.object({
    apk: z.string(),
    pluginClass: z.string(),
});

export const marketplaceManifestSchema = z.object({
    id: z.string(),
    name: z.string(),
    description: z.string(),
    version: z.string(),
    api: z.number(),
    main: z.string(),

    authors: z.array(authorSchema).optional(),
    tags: z.array(z.string()).optional(),
    preview: z.string().optional(),
    assets: z.array(z.string()).optional(),
    branch: z.string().optional(),
    native: nativeSchema.optional(),
    githubUrl: z.string().optional(),
    license: z.string().optional(),
    stars: z.number().optional(),
    banner: z.string().optional(),
});

export async function fetchRepos(page = 1, fresh = false) {
    let url = `https://api.github.com/search/repositories?q=topic:spotifyplus-extensions&per_page=100`;
    if (page) url += `&page=${page}`;

    const cache: any = fresh ? null : SpotifyPlus.Platform.Storage.Cache.read(`page-${page}`);
    const repos: any = cache ? JSON.parse(cache) : await fetch(url).then((res) => res.json()).catch(() => null);

    if (!repos?.items) {
        if (fresh) throw new Error('Could not check for extension updates. Please try again.');
        SpotifyPlus.toast('Failed to fetch extensions', 'long');
        return { items: [] };
    }

    SpotifyPlus.Platform.Storage.Cache.write(`page-${page}`, JSON.stringify(repos));

    return {
        ...repos,
        pageCount: repos.items.length,
        items: repos.items
    }
}

export async function searchRepos(query: string, page = 1) {
    let url = `https://api.github.com/search/repositories?q=${encodeURIComponent(query)}+topic:spotifyplus-extensions&per_page=100`;
    if (page) url += `&page=${page}`;

    const repos = await fetch(url).then((res) => res.json()).catch(() => null);

    if (!repos?.items) {
        SpotifyPlus.toast('Failed to fetch extensions', 'long');
        return { items: [] };
    }

    return {
        ...repos,
        pageCount: repos.items.length,
        items: repos.items
    };
}

export async function fetchManifest(user: string, repo: string, branch: string, starCount: number, license: string, fresh = false) {
    try {
        const cache = fresh ? null : JSON.parse(SpotifyPlus.Platform.Storage.Cache.read(`${user}-${repo}`) as string ?? 'null');
        if (cache?.schemaVersion === MANIFEST_CACHE_VERSION && Date.now() - cache.fetchedAt < CACHE_TTL) {
            return cache.data as MarketplaceExtension[];
        }

        const url = `https://raw.githubusercontent.com/${user}/${repo}/${branch}/manifest.json`;
        const response = await fetch(url).then((res) => res.ok === false ? null : res.json()).catch(() => null);
        if (!response) return null;
        const manifests = Array.isArray(response) ? response : [response];

        const parsedManifests = manifests.flatMap((manifest: any) => {
            const parsed = marketplaceManifestSchema.safeParse(manifest);
            if (parsed.success) return [parsed.data];

            console.warn(`Invalid manifest from ${user}/${repo}`, parsed.error);
            return [];
        });

        const latestRelease = parsedManifests.length ? await fetchLatestRelease(user, repo) : undefined;
        const manifestsArray: MarketplaceExtension[] = parsedManifests.map((manifest: any) => {
            const selectedBranch = manifest.branch || branch;

            const item: MarketplaceExtension = {
                id: manifest.id,
                name: manifest.name,
                description: manifest.description,
                version: manifest.version,
                api: manifest.api,
                main: manifest.main,

                authors: formatAuthors(manifest.authors, user),
                tags: manifest.tags,
                preview: resolveRepositoryAsset(manifest.preview, user, repo, selectedBranch),
                changelog: latestRelease?.body,
                changelogBaseUrl: latestRelease
                    ? `https://raw.githubusercontent.com/${user}/${repo}/${encodeURIComponent(latestRelease.tag ?? selectedBranch)}/`
                    : undefined,
                assets: manifest.assets,
                native: manifest.native,
                githubUrl: `https://www.github.com/${user}/${repo}`,
                license,
                stars: starCount,
                banner: resolveRepositoryAsset(manifest.banner, user, repo, selectedBranch),
                repository: {
                    owner: user,
                    name: repo,
                    branch: selectedBranch,
                },
            }

            return item;
        });

        SpotifyPlus.Platform.Storage.Cache.write(`${user}-${repo}`, JSON.stringify({
            schemaVersion: MANIFEST_CACHE_VERSION,
            fetchedAt: Date.now(),
            data: manifestsArray
        }));

        return manifestsArray;
    } catch (e) {
        console.error(e);
        return null;
    }
}

/** One release request per repository, even when its manifest lists multiple extensions. */
const fetchLatestRelease = async (user: string, repo: string): Promise<{ body: string; tag?: string } | undefined> => {
    try {
        const response = await fetch(`https://api.github.com/repos/${encodeURIComponent(user)}/${encodeURIComponent(repo)}/releases/latest`, {
            headers: { Accept: 'application/vnd.github+json' },
        });
        if (response.status === 404) return undefined;
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const release = await response.json() as { body?: unknown; tag_name?: unknown } | null;
        if (typeof release?.body !== 'string' || !release.body.trim()) return undefined;
        return {
            body: release.body,
            tag: typeof release.tag_name === 'string' && release.tag_name.trim() ? release.tag_name : undefined,
        };
    } catch (error) {
        console.warn(`Could not load latest release from ${user}/${repo}`, error);
        return undefined;
    }
};

const formatAuthors = (authors: { name: string, url: string }[], user: string) => {
    let parsedAuthors: { name: string, url: string }[] = [];

    if (authors && authors.length > 0) {
        parsedAuthors = authors.map((author) => ({
            name: author.name,
            url: sanitizeUrl(author.url)
        }));
    } else {
        parsedAuthors.push({
            name: user,
            url: `https://www.github.com/${user}`
        });
    }

    return parsedAuthors;
}

const sanitizeUrl = (url: string) => {
    if (!url) return url;
    const u = decodeURI(url).trim().toLowerCase();
    if (u.startsWith("javascript:") || u.startsWith("data:") || u.startsWith("vbscript:")) return "about:blank";
    return url;
};

const resolveRepositoryAsset = (value: string | undefined, user: string, repo: string, branch: string,) => {
    if (!value) return undefined;
    if (value.startsWith('http')) return value;

    return `https://raw.githubusercontent.com/${user}/${repo}/${branch}/${value}`;
};
