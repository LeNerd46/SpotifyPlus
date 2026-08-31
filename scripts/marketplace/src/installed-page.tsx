import React, { useEffect, useMemo, useState } from 'react';
import { SpotifyPlus } from 'spotifyplus';
import { Image, Pressable, ScrollView, Text, View } from 'spotifyplus/react';
import Colors from './colors';
import { installMarketplaceExtension, InstallProgress, listInstalledExtensions, uninstallMarketplaceExtension } from './install-extension';
import { InstalledExtensionInfo, MarketplaceExtension } from './types/extension';
import { placeholderImage } from './app';

interface Props {
    extensions: MarketplaceExtension[];
    onInstalledChanged: () => void;
    onSelectExtension: (extension: MarketplaceExtension) => void;
}

const InstalledPage = ({ extensions, onInstalledChanged, onSelectExtension, }: Props) => {
    const [installed, setInstalled] = useState<InstalledExtensionInfo[]>([]);

    const catalogById = useMemo(() => {
        return new Map(extensions.map((extension) => [extension.id, extension]));
    }, [extensions]);

    const refreshInstalled = () => {
        try {
            setInstalled(listInstalledExtensions());
        } catch (error) {
            console.error('Failed to list installed marketplace extensions', error);
            setInstalled([]);
        }
    };

    useEffect(() => {
        refreshInstalled();
    }, []);

    const changed = () => {
        refreshInstalled();
        onInstalledChanged();
    };

    return (
        <ScrollView style={{ flex: 1 }} contentContainerStyle={{ paddingHorizontal: 16, paddingTop: 20, paddingBottom: 80 }} >
            <Text textColor={Colors.onSurface} fontSize={28} fontWeight='bold' >
                Installed extensions
            </Text>
            <Text textColor={Colors.onSurfaceVariant} fontSize={14} style={{ marginTop: 6, marginBottom: 24, lineHeight: 20 }} >
                Manage your installed extensions
            </Text>

            {installed.length === 0 ? (
                <View style={{ padding: 24, borderRadius: 16, backgroundColor: Colors.surfaceContainer, borderWidth: 1, borderColor: Colors.outlineVariant }} >
                    <Text textColor={Colors.onSurface} fontSize={17} fontWeight='bold' >
                        Nothing installed yet
                    </Text>
                    <Text textColor={Colors.onSurfaceVariant} fontSize={14} style={{ marginTop: 8, lineHeight: 20 }} >
                        Extensions you install from the marketplace will appear here.
                    </Text>
                </View>
            ) : (
                <View style={{ gap: 12 }}>
                    {installed.map((item) => (
                        <InstalledExtensionCard key={item.id} installed={item} extension={catalogById.get(item.id)} onChanged={changed} onSelectExtension={onSelectExtension} />
                    ))}
                </View>
            )}
        </ScrollView>
    );
};

interface InstalledExtensionCardProps {
    installed: InstalledExtensionInfo;
    extension?: MarketplaceExtension;
    onChanged: () => void;
    onSelectExtension: (extension: MarketplaceExtension) => void;
}

const InstalledExtensionCard = ({ installed, extension, onChanged, onSelectExtension, }: InstalledExtensionCardProps) => {
    const [imageFailed, setImageFailed] = useState(false);
    const [updating, setUpdating] = useState(false);
    const [uninstalling, setUninstalling] = useState(false);
    const [confirmUninstall, setConfirmUninstall] = useState(false);
    const [progress, setProgress] = useState<InstallProgress | null>(null);
    const updateAvailable = extension?.repository && extension.version !== installed.version;

    useEffect(() => {
        setImageFailed(false);
    }, [extension?.preview]);

    const update = async () => {
        if (!extension || !updateAvailable || updating || uninstalling) return;

        setUpdating(true);
        setProgress({ completed: 0, total: 0 });

        try {
            const result = await installMarketplaceExtension(extension, setProgress);
            SpotifyPlus.toast(`${result.name} updated`, 'long');

            onChanged();
        } catch (error) {
            const message = error instanceof Error ? error.message : 'Unknown update error';
            console.error(`Failed to update ${installed.id}`, error);
            SpotifyPlus.toast(`Update failed: ${message}`, 'long');
        } finally {
            setUpdating(false);
            setProgress(null);
        }
    };

    const uninstall = () => {
        if (updating || uninstalling) return;

        if (!confirmUninstall) {
            setConfirmUninstall(true);
            return;
        }

        setUninstalling(true);

        try {
            const result = uninstallMarketplaceExtension(installed.id);
            SpotifyPlus.toast(`${result.name} uninstalled`, 'long');

            onChanged();
        } catch (error) {
            const message = error instanceof Error ? error.message : 'Unknown uninstall error';
            console.error(`Failed to uninstall ${installed.id}`, error);
            SpotifyPlus.toast(`Uninstall failed: ${message}`, 'long');

            setUninstalling(false);
            setConfirmUninstall(false);
        }
    };

    const updateLabel = updating ? progress?.total ? `Downloading ${progress.completed}/${progress.total}` : 'Preparing…' : 'Update';

    return (
        <View style={{ padding: 16, borderRadius: 16, backgroundColor: Colors.surfaceContainer, borderWidth: 1, borderColor: Colors.outlineVariant, }} >
            <Pressable disabled={!extension} onPress={() => extension && onSelectExtension(extension)} style={({ pressed }) => ({ flexDirection: 'row', alignItems: 'center', gap: 14, opacity: pressed ? 0.78 : 1, })} >
                <Image source={!imageFailed && extension?.preview ? extension.preview : placeholderImage} onError={() => setImageFailed(true)} resizeMode='cover' style={{ width: 58, height: 58, borderRadius: 14, backgroundColor: Colors.surfaceContainerHigh, }} />
                <View style={{ flex: 1 }}>
                    <Text textColor={Colors.onSurface} fontSize={17} fontWeight='bold' >
                        {installed.name}
                    </Text>
                    <Text textColor={Colors.onSurfaceVariant} fontSize={12} style={{ marginTop: 3 }} >
                        {installed.author || 'Unknown developer'}
                    </Text>
                    <Text textColor={updateAvailable ? Colors.primary : Colors.secondary} fontSize={12} style={{ marginTop: 5 }} >
                        Installed v{installed.version ?? 'unknown'}
                        {updateAvailable ? `  •  v${extension?.version} available` : ''}
                    </Text>
                </View>

                {extension && (
                    <Text textColor={Colors.primary} fontSize={22}>
                        ›
                    </Text>
                )}
            </Pressable>

            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, marginTop: 16, }} >
                {updateAvailable && (
                    <ActionButton label={updateLabel} disabled={updating || uninstalling} primary onPress={update} />
                )}
                <ActionButton label={uninstalling ? 'Uninstalling…' : confirmUninstall ? 'Confirm uninstall' : 'Uninstall'} disabled={updating || uninstalling} destructive onPress={uninstall} />
                {confirmUninstall && !uninstalling && (
                    <ActionButton label='Cancel' onPress={() => setConfirmUninstall(false)} />
                )}
            </View>
        </View>
    );
};

interface ActionButtonProps {
    label: string;
    disabled?: boolean;
    primary?: boolean;
    destructive?: boolean;
    onPress: () => void;
}

const ActionButton = ({ label, disabled = false, primary = false, destructive = false, onPress, }: ActionButtonProps) => (
    <Pressable disabled={disabled} onPress={onPress}
        style={({ pressed }) => ({
            paddingHorizontal: 15,
            paddingVertical: 10,
            borderRadius: 20,
            borderWidth: 1,
            borderColor: destructive ? '#ff7373' : primary ? Colors.primary : Colors.outlineVariant,
            backgroundColor: primary ? pressed ? '#349748' : Colors.primary : pressed ? Colors.surfaceContainerHighest : Colors.surfaceContainerHigh,
            opacity: disabled ? 0.65 : 1
        })}
    >
        <Text textColor={destructive ? '#ff9a9a' : primary ? Colors.onPrimary : Colors.onSurface} fontSize={12} fontWeight='bold' >
            {label}
        </Text>
    </Pressable>
);

export default InstalledPage;
