import { fetchManifest, fetchRepos } from './fetch-metadata';
import type { MarketplaceExtension } from './types/extension';

export interface CatalogResult {
    extensions: MarketplaceExtension[];
    incomplete: boolean;
}

let catalog: Promise<CatalogResult> | undefined;

// Shared by startup and every marketplace surface: one fresh check per extension load.
export const loadCatalog = (retry = false): Promise<CatalogResult> => {
    if (retry) catalog = undefined;
    return catalog ??= fetchCatalog();
};

const fetchCatalog = async (): Promise<CatalogResult> => {
    const items = new Map<string, MarketplaceExtension>();
    let incomplete = false;
    let page = 1;
    let more = true;
    while (more) {
        const repos = await fetchRepos(page, true);
        for (const repo of repos.items) {
            const manifests = await fetchManifest(repo.owner.login, repo.name, repo.default_branch, repo.stargazers_count, repo.license?.spdx_id ?? 'No license', true);
            if (!manifests) incomplete = true;
            for (const extension of manifests ?? []) {
                items.set(extension.id, { ...extension, archived: repo.archived, lastUpdated: repo.pushed_at, created: repo.created_at } as MarketplaceExtension);
            }
        }
        more = repos.items.length === 100 && page * 100 < Math.min(repos.total_count ?? 1000, 1000);
        page++;
    }
    return { extensions: [...items.values()].sort((a, b) => b.stars - a.stars), incomplete };
};
