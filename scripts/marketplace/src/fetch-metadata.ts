import { SpotifyPlus } from "spotifyplus";
import z from "zod";
import { parseMarketplaceChangelog } from "./changelog";
import { MarketplaceExtension } from "./types/extension";
import { placeholderImage } from "./app";

const CACHE_TTL = 15 * 60 * 1000;

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
    changelog: z.string().optional(),
    assets: z.array(z.string()).optional(),
    branch: z.string().optional(),
    native: nativeSchema.optional(),
    githubUrl: z.string().optional(),
    license: z.string().optional(),
    stars: z.number().optional(),
    banner: z.string().optional(),
});

export async function fetchRepos(page = 1) {
    let url = `https://api.github.com/search/repositories?q=topic:spotifyplus-extensions&per_page=100`;
    if (page) url += `&page=${page}`;

    const cache: any = await SpotifyPlus.Platform.Storage.Cache.read(`page-${page}`);
    const repos: any = cache ? JSON.parse(cache) : await fetch(url).then((res) => res.json()).catch(() => null);

    if (!repos.items) {
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

    if (!repos.items) {
        SpotifyPlus.toast('Failed to fetch extensions', 'long');
        return { items: [] };
    }

    return {
        ...repos,
        pageCount: repos.items.length,
        items: repos.items
    };
}

export async function fetchManifest(user: string, repo: string, branch: string, starCount: number, license: string) {
    try {
        const cache = JSON.parse(await SpotifyPlus.Platform.Storage.Cache.read(`${user}-${repo}`) as string ?? 'null');
        if (cache && Date.now() - cache.fetchedAt < CACHE_TTL) {
            return cache.data as MarketplaceExtension[];
        }

        const url = `https://raw.githubusercontent.com/${user}/${repo}/${branch}/manifest.json`;
        const response = await fetch(url).then((res) => res.json()).catch(() => null);
        if (!response) return null;
        const manifests = Array.isArray(response) ? response : [response];

        const parsedManifests = manifests.flatMap((manifest: any) => {
            const parsed = marketplaceManifestSchema.safeParse(manifest);
            if (parsed.success) return [parsed.data];

            console.warn(`Invalid manifest from ${user}/${repo}`, parsed.error);
            return [];
        });

        const manifestsArray: MarketplaceExtension[] = await Promise.all(parsedManifests.map(async (manifest: any) => {
            const selectedBranch = manifest.branch || branch;
            const rawChangelog = manifest.changelog?.endsWith('.json') ? await fetch(`https://raw.githubusercontent.com/${user}/${repo}/${selectedBranch}/${manifest.changelog}`).then((res) => res.json()).catch(() => null) : null;
            let changelog;

            if (rawChangelog) {
                try {
                    changelog = parseMarketplaceChangelog(rawChangelog);
                } catch (error) {
                    console.warn(`Invalid changelog from ${user}/${repo}`, error);
                }
            }

            const item: MarketplaceExtension = {
                id: manifest.id,
                name: manifest.name,
                description: manifest.description,
                version: manifest.version,
                api: manifest.api,
                main: manifest.main,

                authors: formatAuthors(manifest.authors, user),
                tags: manifest.tags,
                preview: manifest.preview ?? placeholderImage,
                changelog: changelog,
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
        }));

        SpotifyPlus.Platform.Storage.Cache.write(`${user}-${repo}`, JSON.stringify({
            fetchedAt: Date.now(),
            data: manifestsArray
        }));

        return manifestsArray;
    } catch (e) {
        console.error(e);
        return null;
    }
}

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
    const u = decodeURI(url).trim().toLowerCase();
    if (u.startsWith("javascript:") || u.startsWith("data:") || u.startsWith("vbscript:")) return "about:blank";
    return url;
};

const resolveRepositoryAsset = (value: string | undefined, user: string, repo: string, branch: string,) => {
    if (!value) return undefined;
    if (value.startsWith('http')) return value;

    return `https://raw.githubusercontent.com/${user}/${repo}/${branch}/${value}`;
};
