import React, { useEffect, useMemo, useState } from 'react';
import { SpotifyPlus } from 'spotifyplus';
import { Pressable, ScrollView, Text, View } from 'spotifyplus/react';
import { ExtensionSettingItemRenderer } from './settings-components';
import { ExtensionSetting, ExtensionSettingItem, ExtensionSettingSection, ExtensionSettings } from './settings';
import { AndroidBackButtonEvent } from 'spotifyplus';

type Page = 'home' | 'extensions' | 'developer' | 'about';

interface DevExtension {
    id: string;
    name: string;
    path: string;
    installedAt: string;
    version?: string;
    description?: string;
    author?: string;
}

export interface LocalExtensionInfo {
    id: string;
    name: string;
    version?: string;
    description?: string;
    author?: string;
    path: string;
    installedAt: string;
}

const SPOTIFY_PLUS_VERSION = '0.10.0';
const MARKETPLACE_VERSION = '0.1.0';

const Elevated = (globalThis as any).__spotifyplus_elevated__ as {
    getDeveloperMode?: () => boolean;
    setDeveloperMode?: (enabled: boolean) => void;
    pickLocalExtensionsFolder?: () => boolean;
    getLocalExtensionsFolderDisplayName?: () => string | null;
    listLocalExtensions?: () => DevExtension[];
    refreshLocalExtensions?: () => DevExtension[];
    listInstalledExtensions: () => LocalExtensionInfo[];
    settingsTest(): string;
    getExtensionSettings(): ExtensionSettings[];
    emitToExtension(extensionId: string, eventName: string, payload?: unknown): void;
    settingsChanged(extensionId: string, setting: ExtensionSetting): void;
} | undefined;

const readDeveloperMode = () => Elevated?.getDeveloperMode?.() ?? false;
const readFolderName = () => Elevated?.getLocalExtensionsFolderDisplayName?.() ?? undefined;
const readLocalExtensions = () => Elevated?.listLocalExtensions?.() ?? [];

const App = () => {
    const [page, setPage] = useState<Page>('home');
    const [developerMode, setDeveloperModeState] = useState(readDeveloperMode);
    const [localFolder, setLocalFolder] = useState<string | undefined>(readFolderName);
    const [devExtensions, setDevExtensions] = useState<DevExtension[]>(readLocalExtensions);

    const title = useMemo(() => {
        if (page === 'extensions') return 'Extensions';
        if (page === 'developer') return 'Developer';
        if (page === 'about') return 'About';
        return 'Settings';
    }, [page]);

    const goBack = () => {
        if (page === 'home') {
            //@ts-ignore
            SpotifyPlus.Surfaces.close();
        } else {
            setPage('home');
        }
    };

    useEffect(() => {
        const handleBack = (event: AndroidBackButtonEvent) => {
            event.preventDefault();
            if (page !== 'home') {
                setPage('home');
            } else {
                SpotifyPlus.Surfaces.close();
            }
        };

        SpotifyPlus.on('android.backPressed', handleBack);
        return () => SpotifyPlus.off('android.backPressed', handleBack);
    })

    const refreshLocalExtensions = () => {
        setLocalFolder(readFolderName());
        setDevExtensions(Elevated?.refreshLocalExtensions?.() ?? readLocalExtensions());
    };

    const setDeveloperMode = (value: boolean) => {
        Elevated?.setDeveloperMode?.(value);
        setDeveloperModeState(value);
        if (value) refreshLocalExtensions();
        else setDevExtensions([]);
    };

    const chooseLocalFolder = () => {
        if (!Elevated?.pickLocalExtensionsFolder?.()) return;

        setTimeout(refreshLocalExtensions, 1200);
        setTimeout(refreshLocalExtensions, 3000);
    };

    return (
        <View style={{ flex: 1, backgroundColor: '#121212' }}>
            <Header title={title} showBack={page !== 'home'} onBack={goBack} />

            {page === 'home' && (
                <ScrollView style={{ flex: 1 }} contentContainerStyle={{ paddingTop: 12, paddingBottom: 32 }}>
                    <SettingsCategory icon='⬡' title='Extensions' subtitle='Extension settings' onPress={() => setPage('extensions')} />
                    <SettingsCategory icon='⌘' title='Developer' subtitle='Developer mode • Local extensions' onPress={() => setPage('developer')} />
                    <SettingsCategory icon='ⓘ' title='About' subtitle='Version • GitHub' onPress={() => setPage('about')} />
                </ScrollView>
            )}

            {page === 'extensions' && <ExtensionsPage />}

            {page === 'developer' && (
                <DeveloperPage developerMode={developerMode} setDeveloperMode={setDeveloperMode} localFolder={localFolder} chooseLocalFolder={chooseLocalFolder} refreshLocalExtensions={refreshLocalExtensions} devExtensions={developerMode ? devExtensions : []} />
            )}

            {page === 'about' && <AboutPage />}
        </View>
    );
};

interface HeaderProps {
    title: string;
    showBack: boolean;
    onBack: () => void;
}

const Header = ({ title, showBack, onBack }: HeaderProps) => {
    return (
        <View style={{ height: 56, backgroundColor: '#282828', flexDirection: 'row', alignItems: 'center', paddingHorizontal: 12 }}>
            <Pressable onPress={onBack} style={{ width: 36, height: 44, alignItems: 'center', justifyContent: 'center' }}>
                <Text style={{ color: '#ffffff', fontSize: 32, lineHeight: 36 }}>{showBack ? '‹' : ''}</Text>
            </Pressable>

            <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center' }}>
                <Text style={{ color: '#ffffff', fontSize: 16, fontWeight: '700' }}>{title}</Text>
            </View>

            <Pressable style={{ width: 36, height: 44, alignItems: 'center', justifyContent: 'center' }}>
                <Text style={{ color: '#ffffff', fontSize: 28, lineHeight: 32 }}>⌕</Text>
            </Pressable>
        </View>
    );
};

interface SettingsCategoryProps {
    icon: string;
    title: string;
    subtitle: string;
    onPress: () => void;
}

const SettingsCategory = ({ icon, title, subtitle, onPress }: SettingsCategoryProps) => {
    return (
        <Pressable onPress={onPress} style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 14, paddingVertical: 10 }}>
            <View style={{ width: 36, alignItems: 'center', justifyContent: 'center', marginRight: 0 }}>
                <Text style={{ color: '#ffffff', fontSize: 25 }}>{icon}</Text>
            </View>

            <View style={{ flex: 1, paddingLeft: 0 }}>
                <Text style={{ color: '#ffffff', fontSize: 16, fontWeight: '600' }}>{title}</Text>
                <Text style={{ color: '#b3b3b3', fontSize: 13, marginTop: 4 }}>{subtitle}</Text>
            </View>
        </Pressable>
    );
};

const ExtensionsPage = () => {
    const [extensions, setExtensions] = useState<ExtensionSettings[]>(() => readExtensionSettings());
    const installedExtensions = Elevated?.listInstalledExtensions?.() ?? [];

    const getExtensionName = (extensionId: string) => {
        return installedExtensions.find(extension => extension.id === extensionId)?.name ?? extensionId;
    };

    const changeSetting = (extensionId: string, setting: ExtensionSetting) => {
        setExtensions(current => current.map(extension => {
            if (extension.extensionId !== extensionId) {
                return extension;
            }

            return {
                ...extension,
                settings: extension.settings.map(section => ({
                    ...section,
                    items: section.items.map(item => (
                        'id' in item && item.id === setting.id ? setting : item
                    ))
                }))
            };
        }));
        Elevated?.settingsChanged(extensionId, setting);
    };

    if (extensions.length === 0) {
        return (
            <ScrollView style={{ flex: 1 }} contentContainerStyle={{ paddingHorizontal: 12, paddingTop: 24, paddingBottom: 40 }}>
                <Text style={{ color: '#ffffff', fontSize: 22, fontWeight: '800', marginBottom: 14 }}>Extension settings</Text>

                <View style={{ backgroundColor: '#1f1f1f', borderRadius: 12, padding: 16 }}>
                    <Text style={{ color: '#ffffff', fontSize: 16, fontWeight: '700', marginBottom: 6 }}>Nothing here yet</Text>
                    <Text style={{ color: '#b3b3b3', fontSize: 14, lineHeight: 20 }}>None of your extensions have any settings.</Text>
                </View>
            </ScrollView>
        );
    }

    return (
        <ScrollView style={{ flex: 1 }} contentContainerStyle={{ paddingHorizontal: 12, paddingTop: 24, paddingBottom: 40 }}>
            <Text style={{ color: '#ffffff', fontSize: 22, fontWeight: '800', marginBottom: 14 }}>Extension settings</Text>

            {extensions.map(extension => (
                <View key={extension.extensionId} style={{ marginBottom: 24 }}>
                    <Text style={{ color: '#ffffff', fontSize: 18, fontWeight: '700', marginHorizontal: 2, marginBottom: 10 }}>
                        {getExtensionName(extension.extensionId)}
                    </Text>

                    {extension.settings.map((section, sectionIndex) => (
                        <View
                            key={`${extension.extensionId}-${section.title ?? sectionIndex}`}
                            style={{ backgroundColor: '#1f1f1f', borderRadius: 12, overflow: 'hidden', marginBottom: 12 }}
                        >
                            {(section.title || section.description) && (
                                <View style={{ paddingHorizontal: 14, paddingTop: 14, paddingBottom: 8 }}>
                                    {section.title && (
                                        <Text style={{ color: '#ffffff', fontSize: 16, fontWeight: '700' }}>
                                            {section.title}
                                        </Text>
                                    )}

                                    {section.description && (
                                        <Text style={{ color: '#b3b3b3', fontSize: 13, lineHeight: 19, marginTop: section.title ? 4 : 0 }}>
                                            {section.description}
                                        </Text>
                                    )}
                                </View>
                            )}

                            {section.items.map((item, itemIndex) => (
                                <ExtensionSettingItemRenderer
                                    key={getSettingItemKey(item, itemIndex)}
                                    item={item}
                                    onChange={setting => changeSetting(extension.extensionId, setting)}
                                />
                            ))}
                        </View>
                    ))}
                </View>
            ))}
        </ScrollView>
    );
};

const readExtensionSettings = (): ExtensionSettings[] => {
    const extensions = Elevated?.getExtensionSettings?.() ?? [];

    return extensions
        .map(extension => ({
            ...extension,
            settings: normalizeSettingSections(extension.settings)
        }))
        .filter(extension => extension.settings.some(section => section.items.length > 0));
};

const normalizeSettingSections = (settings: ExtensionSettingSection[]): ExtensionSettingSection[] => {
    if (settings.every(setting => Array.isArray(setting.items))) {
        return settings;
    }

    return [{ items: settings as unknown as ExtensionSettingItem[] }];
};

const getSettingItemKey = (item: ExtensionSettingItem, index: number) => {
    if ('id' in item) {
        return item.id;
    }

    if (item.type === 'header') {
        return `header-${item.label}-${index}`;
    }

    return `divider-${index}`;
};

interface DeveloperPageProps {
    developerMode: boolean;
    setDeveloperMode: (value: boolean) => void;
    localFolder?: string;
    chooseLocalFolder: () => void;
    refreshLocalExtensions: () => void;
    devExtensions: DevExtension[];
}

const DeveloperPage = ({ developerMode, setDeveloperMode, localFolder, chooseLocalFolder, refreshLocalExtensions, devExtensions }: DeveloperPageProps) => {
    return (
        <ScrollView style={{ flex: 1 }} contentContainerStyle={{ paddingTop: 22, paddingBottom: 40 }}>
            <SectionTitle title='Developer preferences' />

            <SettingToggle title='Developer mode' description='Enables local extension loading and extra debugging tools.' value={developerMode} onValueChange={setDeveloperMode} />

            <View style={{ paddingHorizontal: 12, paddingTop: 18, paddingBottom: 16 }}>
                <Text style={{ color: '#ffffff', fontSize: 16, fontWeight: '500', marginBottom: 4 }}>Local extensions folder</Text>
                <Text style={{ color: '#b3b3b3', fontSize: 13, lineHeight: 19, marginBottom: 14 }}>
                    Choose a folder that contains unpacked Spotify Plus extensions for development.
                </Text>

                {localFolder && (
                    <View style={{ backgroundColor: '#1f1f1f', borderRadius: 8, padding: 10, marginBottom: 14 }}>
                        <Text style={{ color: '#b3b3b3', fontSize: 12, marginBottom: 3 }}>Current folder</Text>
                        <Text style={{ color: '#ffffff', fontSize: 13 }}>{localFolder}</Text>
                    </View>
                )}

                <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                    <Pressable onPress={chooseLocalFolder} style={{ alignSelf: 'flex-start', backgroundColor: '#ffffff', borderRadius: 999, paddingHorizontal: 18, paddingVertical: 9, marginRight: 10 }}>
                        <Text style={{ color: '#000000', fontSize: 13, fontWeight: '800' }}>{localFolder ? 'Change folder' : 'Choose folder'}</Text>
                    </Pressable>

                    <Pressable onPress={refreshLocalExtensions} style={{ alignSelf: 'flex-start', backgroundColor: '#2a2a2a', borderRadius: 999, paddingHorizontal: 18, paddingVertical: 9 }}>
                        <Text style={{ color: '#ffffff', fontSize: 13, fontWeight: '800' }}>Refresh</Text>
                    </Pressable>
                </View>
            </View>

            <Divider />

            <SectionTitle title='Local extensions' />

            {devExtensions.length === 0 ? (
                <View style={{ paddingHorizontal: 12, paddingTop: 8 }}>
                    <Text style={{ color: '#b3b3b3', fontSize: 14 }}>No local extensions were found in this folder.</Text>
                </View>
            ) : (
                <View style={{ paddingTop: 2 }}>
                    {devExtensions.map(extension => <DevExtensionRow key={extension.id} extension={extension} />)}
                </View>
            )}
        </ScrollView>
    );
};

interface SettingToggleProps {
    title: string;
    description?: string;
    value: boolean;
    onValueChange: (value: boolean) => void;
}

const SettingToggle = ({ title, description, value, onValueChange }: SettingToggleProps) => {
    return (
        <Pressable onPress={() => onValueChange(!value)} style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 12, paddingVertical: 12 }}>
            <View style={{ flex: 1, paddingRight: 16 }}>
                <Text style={{ color: '#ffffff', fontSize: 16, fontWeight: '500' }}>{title}</Text>
                {description && <Text style={{ color: '#b3b3b3', fontSize: 13, lineHeight: 19, marginTop: 4 }}>{description}</Text>}
            </View>

            <Toggle value={value} />
        </Pressable>
    );
};

interface ToggleProps {
    value: boolean;
}

const Toggle = ({ value }: ToggleProps) => {
    return (
        <View style={{ width: 54, height: 32, borderRadius: 999, borderWidth: 2, borderColor: value ? '#1ed760' : '#a7a7a7', backgroundColor: value ? '#1ed760' : '#333333', justifyContent: 'center', paddingHorizontal: 4 }}>
            <View style={{ width: 22, height: 22, borderRadius: 999, backgroundColor: value ? '#121212' : '#b3b3b3', marginLeft: value ? 20 : 0 }} />
        </View>
    );
};

interface DevExtensionRowProps {
    extension: DevExtension;
}

const DevExtensionRow = ({ extension }: DevExtensionRowProps) => {
    return (
        <View style={{ paddingHorizontal: 12, paddingVertical: 12 }}>
            <Text style={{ color: '#ffffff', fontSize: 16, fontWeight: '600' }}>{extension.name}</Text>
            <Text style={{ color: '#b3b3b3', fontSize: 13, marginTop: 4 }}>{extension.version ? `Version ${extension.version} • ` : ''}Installed {formatDate(extension.installedAt)}</Text>
            <Text style={{ color: '#777777', fontSize: 12, marginTop: 5 }}>{extension.path}</Text>
        </View>
    );
};

const AboutPage = () => {
    return (
        <ScrollView style={{ flex: 1 }} contentContainerStyle={{ paddingTop: 18, paddingBottom: 40 }}>
            <AboutRow title='Spotify Plus version' value={SPOTIFY_PLUS_VERSION} />
            <AboutRow title='Marketplace version' value={MARKETPLACE_VERSION} />

            <AboutLink title='GitHub' onPress={() => SpotifyPlus.Navigation.open('https://github.com/lenerd/spotifyplus')} />
            <AboutLink title='Telegram' onPress={() => SpotifyPlus.Navigation.open('https://t.me/spotifyplus')} />

            <View style={{ marginTop: 28, paddingHorizontal: 12, flexDirection: 'row', alignItems: 'center' }}>
                <Text style={{ flex: 1, color: '#ffffff', fontSize: 15 }}>Made With ❤️ By</Text>
                <Text style={{ color: '#ffffff', fontSize: 15, fontWeight: '700' }}>Devon Shoutz</Text>
            </View>
        </ScrollView>
    );
};

interface AboutRowProps {
    title: string;
    value: string;
}

const AboutRow = ({ title, value }: AboutRowProps) => {
    return (
        <View style={{ minHeight: 56, paddingHorizontal: 12, flexDirection: 'row', alignItems: 'center' }}>
            <Text style={{ flex: 1, color: '#ffffff', fontSize: 16 }}>{title}</Text>
            <Text style={{ color: '#b3b3b3', fontSize: 14 }}>{value}</Text>
        </View>
    );
};

interface AboutLinkProps {
    title: string;
    onPress: () => void;
}

const AboutLink = ({ title, onPress }: AboutLinkProps) => {
    return (
        <Pressable onPress={onPress} style={{ minHeight: 56, paddingHorizontal: 12, flexDirection: 'row', alignItems: 'center' }}>
            <Text style={{ flex: 1, color: '#ffffff', fontSize: 16 }}>{title}</Text>
            <Text style={{ color: '#b3b3b3', fontSize: 22 }}>↗</Text>
        </Pressable>
    );
};

interface SectionTitleProps {
    title: string;
}

const SectionTitle = ({ title }: SectionTitleProps) => {
    return (
        <View style={{ paddingHorizontal: 12, paddingBottom: 14 }}>
            <Text style={{ color: '#ffffff', fontSize: 22, fontWeight: '800' }}>{title}</Text>
        </View>
    );
};

const Divider = () => {
    return <View style={{ height: 1, backgroundColor: '#2a2a2a', marginTop: 8, marginBottom: 22 }} />;
};

const formatDate = (value: string) => {
    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return 'Unknown';
    }

    const months = [
        'January',
        'February',
        'March',
        'April',
        'May',
        'June',
        'July',
        'August',
        'September',
        'October',
        'November',
        'December'
    ];
    const month = months[date.getMonth()];
    const day = date.getDate();
    const year = date.getFullYear();

    return `${month} ${day}, ${year}`;
};

export default App;
