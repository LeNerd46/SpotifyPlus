import React from 'react';
import type { UIApi, UIComponentProps } from './target-api';

/** Opt-in debug fixture. No action runs until its button is pressed. */
export function installPagePartsProbe(api: UIApi, openNowPlaying: () => void) {
    api.overlay('home.page', () => React.createElement('Button', {
        position: 'absolute', top: 120, right: 16, text: 'Open paused Now Playing', onPress: openNowPlaying,
    }));
    for (const page of ['album.page', 'artist.page', 'artist.discography.page', 'playlist.page',
        'lyrics.page', 'settings.page', 'profile.page'] as const) {
        api.replace(page, ({ Original }) => React.createElement('View', { width: '100%', height: '100%' },
            React.createElement(Original), React.createElement('Text', { position: 'absolute', top: 70, color: '#1ed760' }, `UI proof: ${page}`)));
    }
    for (const target of ['contextMenu.root', 'navigation.drawer'] as const) {
        api.replace(target, function PartsProbe({ context, Original, NativePart }: UIComponentProps) {
            const [native, setNative] = React.useState(false);
            const [error, setError] = React.useState('');
            const parts = context.parts ?? [];
            return React.createElement('View', { width: '100%', height: '100%', paddingTop: 40 },
                React.createElement('Text', { color: '#1ed760' }, `UI proof: ${target} (${parts.length} parts)`),
                React.createElement('Button', { text: native ? 'Show native parts' : 'Show Original', onPress: () => setNative(value => !value) }),
                error ? React.createElement('Text', {}, error) : null,
                React.createElement('View', { flex: 1, width: '100%' }, native ? React.createElement(Original) :
                    React.createElement('ScrollView', { width: '100%', height: '100%' },
                        React.createElement('View', { width: '100%' }, ...parts.map(part => {
                        // Exercise action invocation without mounting its native row.
                        if (part.kind === 'action' && /sleep|settings/i.test(part.title ?? '')) return React.createElement('Button', {
                            key: part.id, text: `Run native: ${part.title}`, disabled: !part.enabled,
                            onPress: () => { void api.invokeAction(context.instanceId, part.id).catch(error => setError(String(error))); },
                        });
                        return React.createElement(NativePart, { key: part.id, id: part.id });
                    })))),
            );
        });
    }
}
