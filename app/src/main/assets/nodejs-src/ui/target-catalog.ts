import type { UITargetName } from './target-api';

/** Public names are independent of the adapters verified for a particular Spotify build. */
export const BUILTIN_TARGETS: readonly UITargetName[] = [
    'home.page', 'home.header', 'home.section', 'home.item',
    'search.page', 'search.field', 'search.filters', 'search.results', 'search.item',
    'library.page', 'library.header', 'library.filters', 'library.list', 'library.item',
    ...(['playlist', 'album', 'artist'] as const).flatMap(area =>
        (['page', 'header', 'actions', 'section', 'list', 'item'] as const).map(region => `${area}.${region}` as UITargetName)),
    'nowPlaying.page', 'nowPlaying.header', 'nowPlaying.artwork', 'nowPlaying.trackInfo', 'nowPlaying.controls', 'nowPlaying.seekBar', 'nowPlaying.card',
    'miniPlayer.root', 'miniPlayer.artwork', 'miniPlayer.trackInfo', 'miniPlayer.controls',
    'queue.page', 'queue.header', 'queue.content', 'queue.item',
    'lyrics.page', 'lyrics.header', 'lyrics.content', 'lyrics.line',
    'artist.discography.page', 'settings.page', 'profile.page',
    'contextMenu.root', 'contextMenu.header', 'contextMenu.actions', 'contextMenu.action',
    'navigation.bar', 'navigation.item', 'navigation.drawer',
];
