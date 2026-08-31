import React, { useEffect, useMemo, useState } from 'react';
import { SpotifyPlus } from 'spotifyplus';
import { Image, Pressable, ScrollView, Text, View } from 'spotifyplus/react';
import Colors from './colors';
import { formatReleaseDate, parseMarketplaceChangelog, } from './changelog';
import { installMarketplaceExtension, InstallProgress, listInstalledExtensions, uninstallMarketplaceExtension, } from './install-extension';
import { InstalledExtensionInfo, MarketplaceAuthor, MarketplaceChangelog, MarketplaceChangelogChange, MarketplaceChangelogRelease, MarketplaceExtension, UNKNOWN_AUTHOR, } from './types/extension';
import { placeholderImage } from './app';

interface ChangelogResult {
    releases: MarketplaceChangelog;
    error?: string;
}

const githubIcon = SpotifyPlus.Assets.image('assets/github.png');

const loadChangelog = (source?: string | MarketplaceChangelog): ChangelogResult => {
    if (!source) {
        return { releases: [] };
    }

    try {
        const value = typeof source === 'string' ? SpotifyPlus.Assets.readJson<unknown>(source) : source;
        return { releases: parseMarketplaceChangelog(value) };
    } catch (error) {
        return { releases: [], error: error instanceof Error ? error.message : 'Unknown changelog error' };
    }
};

interface Props {
    extension: MarketplaceExtension;
    onInstalledChanged?: () => void;
}

const ExtensionPage = ({ extension, onInstalledChanged }: Props) => {
    const [bannerFailed, setBannerFailed] = useState(false);
    const [previewFailed, setPreviewFailed] = useState(false);
    const [installed, setInstalled] = useState<InstalledExtensionInfo | null>(null);
    const [installing, setInstalling] = useState(false);
    const [uninstalling, setUninstalling] = useState(false);
    const [confirmUninstall, setConfirmUninstall] = useState(false);
    const [installProgress, setInstallProgress] = useState<InstallProgress | null>(null);

    useEffect(() => {
        setBannerFailed(false);
    }, [extension.banner]);

    useEffect(() => {
        setPreviewFailed(false);
    }, [extension.preview]);

    useEffect(() => {
        try {
            const current = listInstalledExtensions().find((item) => item.id === extension.id) ?? null;
            setInstalled(current);
        } catch {
            setInstalled(null);
        }

        setInstalling(false);
        setUninstalling(false);
        setConfirmUninstall(false);
        setInstallProgress(null);
    }, [extension.id]);

    const authors = extension.authors?.length ? extension.authors : [{ name: UNKNOWN_AUTHOR }];
    const changelog = useMemo(() => loadChangelog(extension.changelog), [extension.changelog]);
    const isInstalledVersion = installed?.version === extension.version;
    const installLabel = getInstallLabel(extension, installed, installing, installProgress,);
    const installDisabled = installing || uninstalling || isInstalledVersion || !extension.repository;

    const install = async () => {
        if (installDisabled) return;

        setInstalling(true);
        setInstallProgress({ completed: 0, total: 0 });

        try {
            const result = await installMarketplaceExtension(extension, setInstallProgress);
            setInstalled(result);
            onInstalledChanged?.();

            SpotifyPlus.toast(`${result.name} ${installed ? 'updated' : 'installed'}`, 'long');
        } catch (error) {
            const message = error instanceof Error ? error.message : 'Unknown installation error';
            console.error(`Failed to install ${extension.id}`, error);

            SpotifyPlus.toast(`Install failed: ${message}`, 'long');
        } finally {
            setInstalling(false);
            setInstallProgress(null);
        }
    };

    const uninstall = () => {
        if (!installed || installing || uninstalling) return;
        if (!confirmUninstall) {
            setConfirmUninstall(true);
            return;
        }

        setUninstalling(true);

        try {
            const result = uninstallMarketplaceExtension(extension.id);
            setInstalled(null);
            setConfirmUninstall(false);
            onInstalledChanged?.();

            SpotifyPlus.toast(`${result.name} uninstalled`, 'long');
        } catch (error) {
            const message = error instanceof Error ? error.message : 'Unknown uninstall error';
            console.error(`Failed to uninstall ${extension.id}`, error);

            SpotifyPlus.toast(`Uninstall failed: ${message}`, 'long');
        } finally {
            setUninstalling(false);
        }
    };

    return (
        <View style={{ flex: 1, backgroundColor: Colors.background }}>
            <ScrollView style={{ flex: 1 }} contentContainerStyle={{ paddingBottom: 96 }}>
                <View style={{ height: 200, backgroundColor: '#41ac58', overflow: 'hidden' }}>
                    {!bannerFailed && <Image source={extension.banner} onError={() => setBannerFailed(true)} resizeMode='cover' style={{ position: 'absolute', width: '100%', height: '100%' }} />}
                    <View style={{ flex: 1, backgroundColor: Colors.background, opacity: 0.45 }} />
                </View>

                <View style={{ paddingHorizontal: 16, marginTop: -48 }}>
                    <View style={{ width: 96, height: 96, padding: 4, borderRadius: 12, backgroundColor: Colors.surfaceContainer, borderWidth: 4, borderColor: Colors.background }}>
                        <Image source={!previewFailed && extension.preview ? extension.preview : placeholderImage} onError={() => setPreviewFailed(true)} resizeMode='cover' style={{ flex: 1, borderRadius: 8, backgroundColor: Colors.surfaceContainerHigh }} />
                    </View>

                    <Text textColor={Colors.onSurface} fontSize={32} fontWeight='bold' style={{ marginTop: 8 }}>{extension.name}</Text>
                    <Text textColor={Colors.secondary} fontSize={16} fontWeight='500' style={{ marginTop: 4 }}>
                        {authors.map((author) => author.name).join(', ')} • v{extension.version}
                    </Text>

                    <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 12, marginTop: 24, }} >
                        <Pressable disabled={installDisabled} onPress={install} style={({ pressed }) => ({
                            borderWidth: 1,
                            borderRadius: 24,
                            borderColor: installDisabled ? Colors.outlineVariant : '#41ac58',
                            paddingHorizontal: 24,
                            backgroundColor: installDisabled ? Colors.surfaceContainerHigh : pressed ? '#349748' : '#41ac58',
                            paddingVertical: 12,
                            opacity: installing ? 0.8 : 1
                        })}
                        >
                            <Text textColor={installDisabled ? Colors.onSurfaceVariant : Colors.onPrimary}>
                                {installLabel}
                            </Text>
                        </Pressable>

                        {installed && (
                            <Pressable disabled={installing || uninstalling} onPress={uninstall} style={({ pressed }) => ({
                                borderWidth: 1,
                                borderRadius: 24,
                                borderColor: '#ff7373',
                                paddingHorizontal: 20,
                                paddingVertical: 12,
                                backgroundColor: pressed ? Colors.surfaceContainerHighest : Colors.surfaceContainerHigh,
                                opacity: installing || uninstalling ? 0.65 : 1
                            })}
                            >
                                <Text textColor='#ff9a9a'>
                                    {uninstalling
                                        ? 'Uninstalling…'
                                        : confirmUninstall
                                            ? 'Confirm uninstall'
                                            : 'Uninstall'}
                                </Text>
                            </Pressable>
                        )}

                        {installed && confirmUninstall && !uninstalling && (
                            <Pressable onPress={() => setConfirmUninstall(false)} style={({ pressed }) => ({
                                borderWidth: 1,
                                borderRadius: 24,
                                borderColor: Colors.outlineVariant,
                                paddingHorizontal: 20,
                                paddingVertical: 12,
                                backgroundColor: pressed ? Colors.surfaceContainerHighest : Colors.surfaceContainerHigh
                            })}
                            >
                                <Text textColor={Colors.onSurface}>
                                    Cancel
                                </Text>
                            </Pressable>
                        )}

                        {extension.githubUrl && (
                            <Pressable style={{ flexDirection: 'row', alignItems: 'center', gap: 4, borderWidth: 1, borderRadius: 24, borderColor: 'gray', paddingHorizontal: 24, paddingVertical: 12 }} onPress={() => SpotifyPlus.Navigation.open(extension.githubUrl!)}>
                                <Image source={githubIcon} width={24} height={24} />
                                <Text>GitHub</Text>
                            </Pressable>
                        )}
                    </View>

                    <SectionTitle title='About this extension' />

                    <Text textColor={Colors.onSurfaceVariant} fontSize={16} fontWeight='500' style={{ lineHeight: 24 }}>
                        {extension.description}
                    </Text>

                    <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginTop: 24 }}>
                        {(extension.tags ?? []).map((tag) => (
                            <FeatureChip key={tag} label={tag} />
                        ))}
                    </View>

                    <ChangelogCard hasSource={Boolean(extension.changelog)} result={changelog} />

                    <InfoCard extension={extension} />

                    <DeveloperCard authors={authors} />
                </View>
            </ScrollView>
        </View>
    );
};

const getInstallLabel = (extension: MarketplaceExtension, installed: InstalledExtensionInfo | null, installing: boolean, progress: InstallProgress | null,): string => {
    if (installing) {
        if (progress?.total) return `Downloading ${progress.completed}/${progress.total}`;
        return 'Preparing…';
    }

    if (!extension.repository) return 'Unavailable';
    if (installed?.version === extension.version) return '✓  Installed';
    if (installed) return '↓  Update';

    return '↓  Install';
};

const Badge = ({ label, primary = false }: { label: string; primary?: boolean }) => (
    <View style={{ paddingHorizontal: 8, paddingVertical: 4, borderRadius: 999, backgroundColor: primary ? Colors.onPrimary : Colors.surfaceContainerHigh, borderWidth: 1, borderColor: primary ? Colors.primary : Colors.outlineVariant }}>
        <Text textColor={primary ? Colors.primary : Colors.onSurfaceVariant} fontSize={12} fontWeight='bold'>{label.toUpperCase()}</Text>
    </View>
);

const Button = ({ label, primary = false }: { label: string; primary?: boolean }) => (
    <View style={{ height: 48, paddingHorizontal: 28, borderRadius: 999, backgroundColor: primary ? Colors.primary : 'transparent', borderWidth: primary ? 0 : 1, borderColor: Colors.outline, alignItems: 'center', justifyContent: 'center' }}>
        <Text textColor={primary ? Colors.onPrimary : Colors.onSurface} fontSize={12} fontWeight='bold'>{label}</Text>
    </View>
);

const SectionTitle = ({ title }: { title: string }) => (
    <Text textColor={Colors.onSurface} fontSize={20} fontWeight='bold' style={{ marginTop: 32, marginBottom: 16 }}>{title}</Text>
);

const FeatureChip = ({ label }: { label: string }) => (
    <View style={{ paddingHorizontal: 16, paddingVertical: 12, borderRadius: 12, backgroundColor: Colors.surfaceContainer, borderWidth: 1, borderColor: Colors.outlineVariant }}>
        <Text textColor={Colors.onSurface} fontSize={14}>{label}</Text>
    </View>
);

const ChangelogCard = ({ hasSource, result, }: { hasSource: boolean; result: ChangelogResult; }) => (
    <View style={{ marginTop: 32, padding: 24, borderRadius: 12, backgroundColor: Colors.surfaceContainer, borderWidth: 1, borderColor: Colors.outlineVariant }} >
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12, marginBottom: 16 }} >
            <Text textColor={Colors.onSurface} fontSize={20} fontWeight='bold' >
                Changelog
            </Text>
            {result.releases[0] && (
                <Text textColor={Colors.onSurfaceVariant} fontSize={12} fontWeight='bold' >
                    {formatReleaseDate(result.releases[0].release)}
                </Text>
            )}
        </View>

        {!hasSource && (
            <ChangelogMessage text='No changelog available.' />
        )}

        {hasSource && result.error && (
            <ChangelogMessage text="This extension's changelog could not be loaded." />
        )}

        {hasSource && !result.error && result.releases.length === 0 && (
            <ChangelogMessage text='No changelog entries available.' />
        )}

        {result.releases.map((release, index) => (
            <ChangelogReleaseView key={`${release.version}-${release.release}-${index}`} release={release} first={index === 0} />
        ))}
    </View>
);

const ChangelogMessage = ({ text }: { text: string }) => (
    <Text textColor={Colors.onSurfaceVariant} fontSize={14} style={{ lineHeight: 20 }} >
        {text}
    </Text>
);

const ChangelogReleaseView = ({ release, first, }: { release: MarketplaceChangelogRelease; first: boolean; }) => (
    <View style={{ marginTop: first ? 0 : 20, paddingTop: first ? 0 : 20, }} >
        {!first && (
            <View style={{ position: 'absolute', top: 0, left: 0, right: 0, height: 1, backgroundColor: Colors.outlineVariant }} />
        )}

        <Text textColor={Colors.onSurface} fontSize={16} fontWeight='bold' >
            v{release.version}
        </Text>

        {release.sections.map((section, index) => (
            <View key={`${section.heading}-${index}`} style={{ marginTop: 16 }} >
                <Text textColor={Colors.primary} fontSize={14} fontWeight='bold' style={{ marginBottom: 10 }} >
                    {section.heading}
                </Text>

                {section.changes.map((change, changeIndex) => (
                    <ChangelogChangeView key={`${typeof change === 'string' ? change : change.text}-${changeIndex}`} change={change} />
                ))}
            </View>
        ))}
    </View>
);

const ChangelogChangeView = ({ change, }: { change: MarketplaceChangelogChange; }) => {
    const details = typeof change === 'string' ? { text: change } : change;

    return (
        <View style={{ marginBottom: 10 }}>
            <View style={{ flexDirection: 'row', gap: 10 }}>
                <View style={{ width: 6, height: 6, borderRadius: 3, backgroundColor: Colors.primary, marginTop: 7 }} />
                <Text textColor={Colors.onSurfaceVariant} fontSize={14} style={{ flex: 1, lineHeight: 20 }} >
                    {details.text}
                </Text>
            </View>

            {details.subLines?.map((line, index) => (
                <View key={`${line}-${index}`} style={{ flexDirection: 'row', gap: 8, marginTop: 6, marginLeft: 16 }} >
                    <Text textColor={Colors.secondary} fontSize={13}>–</Text>
                    <Text textColor={Colors.secondary} fontSize={13} style={{ flex: 1, lineHeight: 18 }} >
                        {line}
                    </Text>
                </View>
            ))}
        </View>
    );
};

const InfoCard = ({ extension }: Props) => (
    <View style={{ marginTop: 32, padding: 24, borderRadius: 12, backgroundColor: Colors.surfaceContainerHigh, borderWidth: 1, borderColor: Colors.outlineVariant }}>
        <Text textColor={Colors.onSurface} fontSize={12} fontWeight='bold' style={{ marginBottom: 16 }}>INFORMATION</Text>
        <InfoRow label='Stars' value={extension.stars?.toString() ?? '0'} />
        <InfoRow label='License' value={extension.license ?? 'Not specified'} />
    </View>
);

const InfoRow = ({ label, value }: { label: string; value: string }) => (
    <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 12 }}>
        <Text textColor={Colors.onSurfaceVariant} fontSize={14}>{label}</Text>
        <Text textColor={Colors.onSurface} fontSize={14} fontWeight='bold'>{value}</Text>
    </View>
);

const DeveloperCard = ({ authors }: { authors: MarketplaceAuthor[] }) => (
    <View style={{ marginTop: 32, padding: 24, borderRadius: 12, backgroundColor: Colors.surfaceContainerHigh, borderWidth: 1, borderColor: Colors.outlineVariant }}>
        <Text textColor={Colors.onSurface} fontSize={12} fontWeight='bold' style={{ marginBottom: 16 }}>
            {authors.length === 1 ? 'DEVELOPER' : 'DEVELOPERS'}
        </Text>

        {authors.map((author, index) => (
            <AuthorRow key={`${author.name}-${author.url ?? index}`} author={author} last={index === authors.length - 1} />
        ))}
    </View>
);

const AuthorRow = ({ author, last }: { author: MarketplaceAuthor; last: boolean }) => (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: 16, marginBottom: last ? 0 : 20 }}>
        <Image source={`https://www.github.com/${author.name}.png`} resizeMode='cover' style={{ width: 48, height: 48, borderRadius: 24, backgroundColor: Colors.surfaceContainerHighest }} />

        <View style={{ flex: 1 }}>
            <Text textColor={Colors.onSurface} fontSize={14} fontWeight='bold'>{author.name}</Text>
            {author.url && (
                <Text textColor={Colors.onSurfaceVariant} fontSize={12} style={{ marginTop: 3 }}>
                    {author.url}
                </Text>
            )}
        </View>
    </View>
);

const NavItem = ({ icon, label, active = false }: { icon: string; label: string; active?: boolean }) => (
    <View style={{ alignItems: 'center' }}>
        <Text textColor={active ? Colors.primary : Colors.secondary} fontSize={22}>{icon}</Text>
        <Text textColor={active ? Colors.primary : Colors.secondary} fontSize={12} fontWeight='bold'>{label}</Text>
    </View>
);

export default ExtensionPage;
