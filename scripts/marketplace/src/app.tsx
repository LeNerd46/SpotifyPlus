import React, { useEffect, useRef, useState } from 'react';
import { SpotifyPlus, type AndroidBackButtonEvent } from 'spotifyplus';
import { Image, Pressable, ScrollView, Text, View } from 'spotifyplus/react';
import Animated, { useAnimatedStyle, useSharedValue, withSpring, withTiming } from 'spotifyplus/react/reanimated';
import Colors from './colors';
import { getAuthorNames, MarketplaceExtension, PLACEHOLDER_IMAGE } from './types/extension';
import ExtensionPage from './extension-page';
import InstalledPage from './installed-page';
import SearchPage from './search';
import { fetchManifest, fetchRepos } from './fetch-metadata';
import { listInstalledExtensions } from './install-extension';

export const placeholderImage = SpotifyPlus.Assets.image(PLACEHOLDER_IMAGE);
const downloadIcon = SpotifyPlus.Assets.image('assets/download.png');
const searchIcon = SpotifyPlus.Assets.image('assets/search.png');
const closeIcon = SpotifyPlus.Assets.image('assets/close.png');
const backIcon = SpotifyPlus.Assets.image('assets/back.png');

const categories = [
    { label: 'All', icon: '✦' },
    { label: 'Extensions', icon: '⌁' },
    { label: 'Themes', icon: '◐' },
    { label: 'Tools', icon: '⚡' }
];

const App = () => {
    const [selectedExtension, setSelectedExtension] = useState<MarketplaceExtension | null>(null);
    const [searchActive, setSearchActive] = useState(false);
    const [installedActive, setInstalledActive] = useState(false);
    const [installedCount, setInstalledCount] = useState(0);
    const [loading, setLoading] = useState(true);
    const [extensions, setExtensions] = useState<MarketplaceExtension[]>([]);
    const navigationStateRef = useRef({ installedActive, searchActive, selectedExtension });

    navigationStateRef.current = { installedActive, searchActive, selectedExtension };

    const refreshInstalledCount = () => {
        try {
            setInstalledCount(listInstalledExtensions().length);
        } catch {
            setInstalledCount(0);
        }
    };

    useEffect(() => {
        refreshInstalledCount();
    }, []);

    useEffect(() => {
        const getExtensions = async () => {
            const repos = await fetchRepos();
            const items: MarketplaceExtension[] = [];

            for (const repo of repos.items) {
                const manifest = await fetchManifest(repo.owner.login, repo.name, repo.default_branch, repo.stargazers_count, repo.license?.spdx_id ?? 'No license');


                if (manifest?.length) {
                    items.push(...manifest.map((extension) => ({
                        ...extension,
                        archived: repo.archived,
                        lastUpdated: repo.pushed_at,
                        created: repo.created_at
                    })));
                }
            }

            items.sort((a, b) => b.stars - a.stars);
            return items;
        };

        getExtensions().then((res) => { setExtensions(res); setLoading(res ? false : true) });
    }, []);

    useEffect(() => {
        const handleBack = (event: AndroidBackButtonEvent) => {
            event.preventDefault();

            const { installedActive: isInstalledActive, searchActive: isSearchActive, selectedExtension: currentExtension } = navigationStateRef.current;

            if (currentExtension || isSearchActive || isInstalledActive) {
                setSelectedExtension(null);
                setSearchActive(false);
                setInstalledActive(false);
                return;
            }

            SpotifyPlus.Surfaces.close();
        };

        SpotifyPlus.on('android.backPressed', handleBack);
        return () => SpotifyPlus.off('android.backPressed', handleBack);
    }, []);

    if (selectedExtension) {
        return (
            <View style={{ flex: 1, backgroundColor: Colors.background }}>
                <Header showBack onBack={() => setSelectedExtension(null)} />
                <ExtensionPage extension={selectedExtension} onInstalledChanged={refreshInstalledCount} />
            </View>
        );
    }

    if (installedActive) {
        return (
            <View style={{ flex: 1, backgroundColor: Colors.background }}>
                <Header showBack onBack={() => setInstalledActive(false)} />
                <InstalledPage extensions={extensions} onInstalledChanged={refreshInstalledCount} onSelectExtension={setSelectedExtension} />
            </View>
        );
    }

    if (searchActive) {
        return (
            <View style={{ flex: 1, backgroundColor: Colors.background }}>
                <Header showBack onBack={() => setSearchActive(false)} />
                <SearchPage onSelectExtension={setSelectedExtension} extensionList={extensions} />
            </View>
        );
    }

    if (loading) {
        return (
            <View style={{ flex: 1, backgroundColor: Colors.background }}>
                <Text style={{ flex: 1, justifyContent: 'center' }}>Loading...</Text>
            </View>
        );
    }

    return (
        <View style={{ flex: 1, backgroundColor: Colors.background }}>
            <Header onClose={() => SpotifyPlus.Surfaces.close()} onSearch={() => setSearchActive(true)} onInstalled={() => setInstalledActive(true)} />

            <ScrollView style={{ flex: 1 }} contentContainerStyle={{ paddingBottom: 64 }} >
                <View style={{ paddingHorizontal: 16, paddingTop: 24 }}>
                    <Text textColor={Colors.onSurface} fontSize={30} fontWeight='bold' style={{ lineHeight: 36 }} >
                        Make Spotify yours.
                    </Text>
                    <Text textColor={Colors.onSurfaceVariant} fontSize={15} style={{ marginTop: 6, lineHeight: 21 }} >
                        Discover extensions, themes, and tools built for Spotify Plus.
                    </Text>
                </View>

                {/* <View style={{ marginTop: 28 }}>
                    <SectionHeader title='Explore' />
                    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: 16, gap: 10 }} >
                        {categories.map((category, index) => (
                            <CategoryPill key={category.label} label={category.label} icon={category.icon} active={index === 0} onPress={() => setSearchActive(true)} />
                        ))}
                    </ScrollView>
                </View> */}

                {/* <View style={{ marginTop: 30 }}>
                    <SectionHeader title='Popular extensions' />
                    <View style={{ paddingHorizontal: 16, gap: 10 }}>
                        {extensions.map((extension, index) => (
                            <ExtensionRow key={extension.id} extension={extension} rank={index + 1} onPress={() => setSelectedExtension(extension)} />
                        ))}
                    </View>
                </View> */}

                <View style={{ marginTop: 30 }}>
                    <SectionHeader title='All extensions' />
                    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: 16, gap: 12 }} >
                        {[...extensions].reverse().map((extension) => (
                            <CompactCard key={extension.id} extension={extension} onPress={() => setSelectedExtension(extension)} />
                        ))}
                    </ScrollView>
                </View>
            </ScrollView>
        </View>
    );
};

interface HeaderProps {
    showBack?: boolean;
    onBack?: () => void;
    onClose?: () => void;
    onSearch?: () => void;
    onInstalled?: () => void;
}

const Header = ({ showBack = false, onBack, onClose, onSearch, onInstalled }: HeaderProps) => (
    <View style={{ height: 64, marginTop: 16, paddingHorizontal: 16, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: Colors.surface }} >
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
            {showBack && (
                <Pressable onPress={onBack} style={({ pressed }) => ({
                    width: 36,
                    height: 36,
                    borderRadius: 18,
                    alignItems: 'center',
                    justifyContent: 'center',
                    backgroundColor: pressed ? Colors.surfaceContainerHighest : Colors.surfaceContainer
                })} >
                    <Image source={backIcon} width={24} height={24} />
                </Pressable>
            )}

            <Text textColor={Colors.onSurface} fontSize={20} fontWeight='bold'>Spotify Plus Marketplace</Text>
        </View>

        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
            {!showBack && (
                <>
                    <Pressable onPress={onInstalled} style={({ pressed }) => ({
                        width: 40,
                        height: 40,
                        borderRadius: 20,
                        backgroundColor: pressed ? Colors.surfaceContainerHighest : Colors.surfaceContainer,
                        alignItems: 'center',
                        justifyContent: 'center'
                    })} >
                        <Image source={downloadIcon} width={18} height={18} />
                    </Pressable>

                    <Pressable onPress={onSearch} style={({ pressed }) => ({
                        width: 40,
                        height: 40,
                        borderRadius: 20,
                        backgroundColor: pressed ? Colors.surfaceContainerHighest : Colors.surfaceContainer,
                        alignItems: 'center',
                        justifyContent: 'center'
                    })} >
                        <Image source={searchIcon} width={18} height={18} />
                    </Pressable>
                    <Pressable onPress={onClose} style={({ pressed }) => ({
                        width: 40,
                        height: 40,
                        borderRadius: 20,
                        backgroundColor: pressed ? Colors.surfaceContainerHighest : Colors.surfaceContainer,
                        alignItems: 'center',
                        justifyContent: 'center'
                    })} >
                        <Image source={closeIcon} width={18} height={18} />
                    </Pressable>
                </>
            )}
        </View>
    </View>
);

interface AnimatedCardProps {
    children: React.ReactNode;
    onPress: () => void;
    style?: any;
    contentStyle?: any;
}

const AnimatedCard = ({ children, onPress, style, contentStyle }: AnimatedCardProps) => {
    const scale = useSharedValue(1);
    const animatedStyle = useAnimatedStyle(() => {
        'worklet';
        return {
            transform: [{ scale: scale.value }],
        };
    });

    const pressIn = () => {
        scale.value = withTiming(0.975, { duration: 90 });
    };

    const pressOut = () => {
        scale.value = withSpring(1, {
            stiffness: 360,
            damping: 22,
        });
    };

    return (
        <Animated.View style={[style, animatedStyle]}>
            <Pressable onPress={onPress} onPressIn={pressIn} onPressOut={pressOut} style={({ pressed }) => [contentStyle, pressed && { backgroundColor: Colors.surfaceContainerHighest }]} >
                {children}
            </Pressable>
        </Animated.View>
    );
};

const SectionHeader = ({ title }: { title: string }) => (
    <View style={{ paddingHorizontal: 16, marginBottom: 12 }}>
        <Text textColor={Colors.onSurface} fontSize={21} fontWeight='bold'>{title}</Text>
    </View>
);

const FeaturedCard = ({ extension, onPress }: { extension: MarketplaceExtension; onPress: () => void }) => (
    <AnimatedCard onPress={onPress} contentStyle={{ minHeight: 156, padding: 18, borderRadius: 18, backgroundColor: Colors.surfaceContainer, borderWidth: 1, borderColor: Colors.outlineVariant, overflow: 'hidden' }} >
        <View style={{ position: 'absolute', width: 180, height: 180, borderRadius: 90, right: -68, top: -76, backgroundColor: Colors.primary, opacity: 0.12 }} />
        <View style={{ flexDirection: 'row', gap: 16, alignItems: 'center' }}>
            <ExtensionIcon extension={extension} size={76} />
            <View style={{ flex: 1 }}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
                    <Text textColor={Colors.onSurface} fontSize={20} fontWeight='bold'>
                        {extension.name}
                    </Text>
                </View>
                <Text textColor={Colors.onSurfaceVariant} fontSize={13} style={{ marginTop: 3 }}>
                    {getAuthorNames(extension)}
                </Text>
                <Text textColor={Colors.onSurfaceVariant} fontSize={13} style={{ marginTop: 12, lineHeight: 18 }} >
                    {extension.description}
                </Text>
            </View>
        </View>
        <View style={{ marginTop: 16, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }} >
            <Text textColor={Colors.primary} fontSize={13} fontWeight='bold'>
                {extension.stars === undefined ? 'No stars' : `★ ${extension.stars}`}
            </Text>
            <Text textColor={Colors.onSurface} fontSize={13} fontWeight='bold'>View details  ›</Text>
        </View>
    </AnimatedCard>
);

interface CategoryPillProps {
    label: string;
    icon: string;
    active: boolean;
    onPress: () => void;
}

const CategoryPill = ({ label, icon, active, onPress }: CategoryPillProps) => (
    <AnimatedCard onPress={onPress} contentStyle={{
        minHeight: 42,
        paddingHorizontal: 15,
        borderRadius: 21,
        flexDirection: 'row',
        alignItems: 'center',
        gap: 7,
        backgroundColor: active ? Colors.primary : Colors.surfaceContainer,
        borderWidth: 1,
        borderColor: active ? Colors.primary : Colors.outlineVariant
    }} >
        <Text textColor={active ? Colors.onPrimary : Colors.primary} fontSize={14}>{icon}</Text>
        <Text textColor={active ? Colors.onPrimary : Colors.onSurface} fontSize={13} fontWeight='bold' >
            {label}
        </Text>
    </AnimatedCard>
);

interface ExtensionRowProps {
    extension: MarketplaceExtension;
    rank: number;
    onPress: () => void;
}

const ExtensionRow = ({ extension, rank, onPress }: ExtensionRowProps) => (
    <AnimatedCard onPress={onPress} contentStyle={{ minHeight: 84, padding: 12, borderRadius: 15, backgroundColor: Colors.surfaceContainer, flexDirection: 'row', alignItems: 'center' }} >
        <Text textColor={Colors.onSurfaceVariant} fontSize={13} fontWeight='bold' style={{ width: 24, textAlign: 'center', marginRight: 8 }} >
            {rank}
        </Text>

        <ExtensionIcon extension={extension} size={56} />
        <View style={{ flex: 1, marginLeft: 12 }}>
            <Text textColor={Colors.onSurface} fontSize={16} fontWeight='bold'>
                {extension.name}
            </Text>
            <Text textColor={Colors.onSurfaceVariant} fontSize={12} style={{ marginTop: 3 }}>
                {getAuthorNames(extension)}
            </Text>
            <Text textColor={Colors.onSurfaceVariant} fontSize={12} style={{ marginTop: 5 }}>
                {extension.stars === undefined ? 'Unrated' : `★ ${extension.stars}`}
            </Text>
        </View>
        <View style={{ width: 34, height: 34, borderRadius: 17, backgroundColor: Colors.surfaceContainerHigh, alignItems: 'center', justifyContent: 'center' }} >
            <Text textColor={Colors.primary} fontSize={20}>›</Text>
        </View>
    </AnimatedCard>
);

const CompactCard = ({ extension, onPress }: { extension: MarketplaceExtension; onPress: () => void }) => (
    <AnimatedCard onPress={onPress} style={{ width: 174 }} contentStyle={{ minHeight: 170, padding: 14, borderRadius: 16, backgroundColor: Colors.surfaceContainer, borderWidth: 1, borderColor: Colors.outlineVariant }} >
        <ExtensionIcon extension={extension} size={52} />
        <Text textColor={Colors.onSurface} fontSize={16} fontWeight='bold' style={{ marginTop: 14 }} >
            {extension.name}
        </Text>
        <Text textColor={Colors.onSurfaceVariant} fontSize={12} style={{ marginTop: 4 }}>
            {getAuthorNames(extension)}
        </Text>
        <View style={{ marginTop: 16, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }} >
            <Text textColor={Colors.primary} fontSize={12} fontWeight='bold'>
                {extension.stars === undefined ? 'Unrated' : `★ ${extension.stars}`}
            </Text>
            <Text textColor={Colors.onSurface} fontSize={12} fontWeight='bold'>VIEW ›</Text>
        </View>
    </AnimatedCard>
);

const ExtensionIcon = ({ extension, size }: { extension: MarketplaceExtension; size: number }) => {
    const [failed, setFailed] = useState(false);

    useEffect(() => {
        setFailed(false);
    }, [extension.preview]);

    return (
        <Image source={!failed && extension.preview ? extension.preview : placeholderImage} onError={() => setFailed(true)} resizeMode='cover' style={{
            width: size,
            height: size,
            borderRadius: Math.round(size * 0.25),
            backgroundColor: Colors.surfaceContainerHigh
        }} />
    );
};

export default App;
