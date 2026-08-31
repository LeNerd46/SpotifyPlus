import React, { useEffect, useState } from 'react';
import { SpotifyPlus } from 'spotifyplus';
import { Image, Pressable, ScrollView, Text, TextInput, View } from 'spotifyplus/react';
import Colors from './colors';
import { getAuthorNames, MarketplaceExtension, PLACEHOLDER_IMAGE } from './types/extension';
import { fetchManifest, searchRepos } from './fetch-metadata';

const placeholderImage = SpotifyPlus.Assets.image('assets/placeholder.jpg');
const SEARCH_DEBOUNCE_MS = 500;

const categories = [
    { id: 'ui', name: 'UI', color: '#E8115B', icon: '▣' },
    { id: 'utility', name: 'Utility', color: '#1DB954', icon: '⚡' },
    { id: 'social', name: 'Social', color: '#5038A0', icon: '●' },
    { id: 'experimental', name: 'Experimental', color: '#BA5D07', icon: '✦' },
];

interface SearchPageProps {
    onSelectExtension: (extension: MarketplaceExtension) => void;
    extensionList: MarketplaceExtension[];
}

const SearchPage = ({ onSelectExtension, extensionList }: SearchPageProps) => {
    const [query, setQuery] = useState('');
    const [extensions, setExtensions] = useState<MarketplaceExtension[]>([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        const searchQuery = query.trim();

        if (!searchQuery) {
            setExtensions([]);
            setLoading(false);
            return;
        }

        let cancelled = false;
        setLoading(true);

        const search = async () => {
            const repos = await searchRepos(searchQuery);
            const items: MarketplaceExtension[] = [];

            for (const repo of repos.items) {
                const manifest = await fetchManifest(repo.owner.login, repo.name, repo.default_branch, repo.stargazers_count, repo.license?.spdx_id ?? 'No license');

                if (manifest?.length) {
                    items.push(...manifest.map((extension) => ({
                        ...extension,
                        archived: repo.archived,
                        lastUpdated: repo.pushed_at,
                        created: repo.created_at,
                    })));
                }
            }

            items.sort((a, b) => b.stars - a.stars);
            return items;
        };

        const timeout = setTimeout(() => {
            search()
                .then((res) => {
                    if (!cancelled) {
                        setExtensions(res);
                        setLoading(false);
                    }
                })
                .catch(() => {
                    if (!cancelled) {
                        setExtensions([]);
                        setLoading(false);
                    }
                });
        }, SEARCH_DEBOUNCE_MS);

        return () => {
            cancelled = true;
            clearTimeout(timeout);
        };
    }, [query]);

    const hasQuery = query.trim().length > 0;

    return (
        <ScrollView style={{ flex: 1, backgroundColor: Colors.background }} contentContainerStyle={{ padding: 16, paddingBottom: 32 }}>
            <View style={{ marginBottom: 32 }}>
                <Text textColor={Colors.onSurface} fontSize={32} fontWeight='bold' style={{ marginBottom: 16 }}>Search</Text>

                <View style={{ height: 48, borderRadius: 999, backgroundColor: '#ffffff', flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16 }}>
                    <Text textColor='#666666' fontSize={20} style={{ marginRight: 10 }}>⌕</Text>
                    <TextInput
                        value={query}
                        onChangeText={setQuery}
                        placeholder='What do you want to install?'
                        placeholderTextColor='#666666'
                        textColor='#121212'
                        fontSize={16}
                        fontWeight='500'
                        returnKeyType='search'
                        singleLine={true}
                        style={{ flex: 1, height: 48, paddingHorizontal: 0, backgroundColor: 'transparent' }}
                    />
                </View>
            </View>

            {hasQuery ? (
                <SearchResults
                    query={query.trim()}
                    extensions={extensions}
                    loading={loading}
                    onSelectExtension={onSelectExtension}
                />
            ) : (
                <>
                    {/* <View style={{ marginBottom: 32 }}>
                        <Text textColor={Colors.onSurface} fontSize={20} fontWeight='bold' style={{ marginBottom: 16 }}>Browse All</Text>

                        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 16 }}>
                            {categories.map((category) => (
                                <CategoryCard
                                    key={category.id}
                                    name={category.name}
                                    color={category.color}
                                    icon={category.icon}
                                />
                            ))}
                        </View>
                    </View> */}

                    <View>
                        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 16 }}>
                            <Text textColor={Colors.onSurface} fontSize={20} fontWeight='bold'>Trending Now</Text>
                        </View>

                        <View style={{ gap: 12 }}>
                            {extensionList.map((extension) => (
                                <TrendingItem key={extension.id} extension={extension} onSelectExtension={onSelectExtension} />
                            ))}
                        </View>
                    </View>
                </>
            )}
        </ScrollView>
    );
};

interface SearchResultsProps {
    query: string;
    extensions: MarketplaceExtension[];
    loading: boolean;
    onSelectExtension: (extension: MarketplaceExtension) => void;
}

const SearchResults = ({ query, extensions, loading, onSelectExtension }: SearchResultsProps) => {
    if (loading) {
        return (
            <View>
                <Text textColor={Colors.onSurface} fontSize={20} fontWeight='bold'>
                    Searching for “{query}”
                </Text>
                <Text textColor={Colors.onSurfaceVariant} fontSize={14} style={{ marginTop: 6, marginBottom: 18 }}>
                    Looking through the marketplace…
                </Text>

                <View style={{ gap: 12 }}>
                    {[0, 1, 2].map((item) => (
                        <View
                            key={item}
                            style={{ height: 112, borderRadius: 16, backgroundColor: Colors.surfaceContainer }}
                        />
                    ))}
                </View>
            </View>
        );
    }

    if (extensions.length === 0) {
        return (
            <View style={{ minHeight: 280, paddingHorizontal: 24, alignItems: 'center', justifyContent: 'center' }}>
                <View style={{ width: 64, height: 64, borderRadius: 32, backgroundColor: Colors.surfaceContainer, alignItems: 'center', justifyContent: 'center' }}>
                    <Text textColor={Colors.primary} fontSize={30}>⌕</Text>
                </View>
                <Text textColor={Colors.onSurface} fontSize={20} fontWeight='bold' style={{ marginTop: 18, textAlign: 'center' }}>
                    No results for “{query}”
                </Text>
                <Text textColor={Colors.onSurfaceVariant} fontSize={14} style={{ marginTop: 8, lineHeight: 20, textAlign: 'center' }}>
                    Check the spelling or try a broader search.
                </Text>
            </View>
        );
    }

    return (
        <View>
            <View style={{ marginBottom: 16 }}>
                <Text textColor={Colors.onSurface} fontSize={20} fontWeight='bold'>
                    Results for “{query}”
                </Text>
                <Text textColor={Colors.onSurfaceVariant} fontSize={13} style={{ marginTop: 5 }}>
                    {extensions.length} {extensions.length === 1 ? 'extension' : 'extensions'} found
                </Text>
            </View>

            <View style={{ gap: 12 }}>
                {extensions.map((extension) => (
                    <SearchResultCard
                        key={extension.id}
                        extension={extension}
                        onPress={() => onSelectExtension(extension)}
                    />
                ))}
            </View>
        </View>
    );
};

interface SearchResultCardProps {
    extension: MarketplaceExtension;
    onPress: () => void;
}

const SearchResultCard = ({ extension, onPress }: SearchResultCardProps) => {
    const [imageFailed, setImageFailed] = useState(false);

    useEffect(() => {
        setImageFailed(false);
    }, [extension.preview]);

    const tags = extension.tags?.slice(0, 2) ?? [];

    return (
        <Pressable
            onPress={onPress}
            style={({ pressed }) => ({
                padding: 14,
                borderRadius: 16,
                backgroundColor: pressed ? Colors.surfaceContainerHighest : Colors.surfaceContainer,
                borderWidth: 1,
                borderColor: Colors.outlineVariant,
            })}
        >
            <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                <Image
                    source={!imageFailed && extension.preview ? extension.preview : placeholderImage}
                    onError={() => setImageFailed(true)}
                    resizeMode='cover'
                    style={{ width: 58, height: 58, borderRadius: 14, backgroundColor: Colors.surfaceContainerHigh }}
                />

                <View style={{ flex: 1, marginLeft: 13 }}>
                    <Text textColor={Colors.onSurface} fontSize={16} fontWeight='bold' numberOfLines={1}>
                        {extension.name}
                    </Text>
                    <Text textColor={Colors.onSurfaceVariant} fontSize={12} style={{ marginTop: 3 }} numberOfLines={1}>
                        {getAuthorNames(extension)} • v{extension.version}
                    </Text>
                    <Text textColor={Colors.onSurfaceVariant} fontSize={12} style={{ marginTop: 7 }} numberOfLines={2}>
                        {extension.description}
                    </Text>
                </View>

                <Text textColor={Colors.primary} fontSize={24} style={{ marginLeft: 10 }}>›</Text>
            </View>

            <View style={{ marginTop: 12, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                <View style={{ flexDirection: 'row', gap: 6 }}>
                    {tags.map((tag) => (
                        <View key={tag} style={{ paddingHorizontal: 9, paddingVertical: 4, borderRadius: 10, backgroundColor: Colors.surfaceContainerHigh }}>
                            <Text textColor={Colors.onSurfaceVariant} fontSize={10} fontWeight='bold'>
                                {tag}
                            </Text>
                        </View>
                    ))}
                </View>

                <Text textColor={Colors.primary} fontSize={12} fontWeight='bold'>
                    {extension.stars === undefined ? 'Unrated' : `★ ${extension.stars}`}
                </Text>
            </View>
        </Pressable>
    );
};

const CategoryCard = ({ name, color, icon }: { name: string; color: string; icon: string }) => {
    return (
        <View style={{ width: '47%', height: 64, aspectRatio: 1, borderRadius: 12, backgroundColor: color, padding: 16, overflow: 'hidden' }}>
            <Text textColor='#ffffff' fontSize={24} fontWeight='bold'>{name}</Text>

            <View style={{ position: 'absolute', right: -12, bottom: -12, width: 88, height: 88, borderRadius: 12, backgroundColor: 'rgba(255,255,255,0.18)', alignItems: 'center', justifyContent: 'center' }}>
                <Text textColor='#ffffff' fontSize={42} fontWeight='bold'>{icon}</Text>
            </View>
        </View>
    );
};

const TrendingItem = ({ extension, onSelectExtension }: { extension: MarketplaceExtension, onSelectExtension: (extension: MarketplaceExtension) => void }) => {
    const [failed, setFailed] = useState(false);

    useEffect(() => {
        setFailed(false);
    }, [extension.preview]);

    return (
        <Pressable style={{ flexDirection: 'row', alignItems: 'center', padding: 16, borderRadius: 12, backgroundColor: Colors.surfaceContainer }} onPress={() => onSelectExtension(extension)}>
            {/* <Image source={!failed && extension.preview ? extension.preview : placeholderImage} onError={() => setFailed(true)} resizeMode='cover' style={{ width: 48, height: 48, borderRadius: 8, backgroundColor: Colors.surfaceContainer, marginRight: 16 }} /> */}

            <View style={{ flex: 1 }}>
                <Text textColor={Colors.onSurface} fontSize={16} fontWeight='bold'>{extension.name}</Text>
                <Text textColor={Colors.onSurfaceVariant} fontSize={14} style={{ marginTop: 2 }}>
                    {getAuthorNames(extension)}
                </Text>
            </View>

            <Text textColor={Colors.onSurfaceVariant} fontSize={26}>›</Text>
        </Pressable>
    );
};

export default SearchPage;
