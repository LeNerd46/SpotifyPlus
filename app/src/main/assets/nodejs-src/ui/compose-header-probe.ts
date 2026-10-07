import React from 'react';
import type { UIComponentProps } from './target-api';

/** Only mounted by the debug-only native ComposeHeaderProbe. Removed after the integration gate. */
export function ComposeHeaderProbe({ Original, context }: UIComponentProps) {
    const [expanded, setExpanded] = React.useState(false);
    const [showOriginal, setShowOriginal] = React.useState(true);
    const [fail, setFail] = React.useState(false);
    if (fail) throw new Error('Intentional UI probe render failure');
    return React.createElement('View', { padding: 12, width: '100%' },
        React.createElement('Text', { color: '#1ed760', fontSize: 16 }, 'Spotify Plus · React header proof'),
        React.createElement('Button', { text: expanded ? 'Collapse React content' : 'Expand React content', onPress: () => setExpanded(value => !value) }),
        React.createElement('Button', { text: showOriginal ? 'Hide native header' : 'Restore native header', onPress: () => setShowOriginal(value => !value) }),
        React.createElement('Button', { text: 'Test render failure', onPress: () => setFail(true) }),
        expanded ? React.createElement('Text', { color: '#ffffff', fontSize: 16 }, 'This content is measured inside Spotify’s Compose layout. The original header below remains interactive.') : null,
        showOriginal ? React.createElement(Original) : React.createElement('Text', {}, `Replacement for ${context.title}`),
    );
}

export function NowPlayingProbe({ Original, context }: UIComponentProps) {
    const [visible, setVisible] = React.useState(true);
    return React.createElement('View', { width: '100%', height: '100%' },
        React.createElement('Text', {}, `Now Playing UI proof: ${context.title ?? context.uri ?? ''}`),
        React.createElement('Button', { text: visible ? 'Hide native page' : 'Restore native page', onPress: () => setVisible(value => !value) }),
        React.createElement('View', { flex: 1, width: '100%' }, visible ? React.createElement(Original) : null),
    );
}
