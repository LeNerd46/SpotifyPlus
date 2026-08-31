import React from 'react';
import App from './app';
import Marketplace from './marketplace';
import YogaTestPage from './test';
import Test from './app';
import AnimatedTestScreen from './AnimatedTestScreen';
import ExampleScriptView from './CustomScriptViewTest';

console.log('Loading bookmarks!');
const main = async () => {
    //@ts-expect-error
    let bookmarks: string[] = await SpotifyPlus.Platform.Storage.read<string[]>('bookmarkss.json') || [];

    //@ts-expect-error
    new SpotifyPlus.SideDrawer('Marketplace', () => {
        // return <Marketplace SpotifyPlus={SpotifyPlus} />
        return <ExampleScriptView artwork='https://image-cdn-fa.spotifycdn.com/image/ab67616d0000b2734dcb6c5df15cf74596ab25a4' />

        // //@ts-expect-error
        // return <App bookmarks={bookmarks} SpotifyPlus={SpotifyPlus} onDeleteTrack={(uri: string) => {
        //     bookmarks = bookmarks.filter(b => b !== uri);
        //     //@ts-expect-error
        //     SpotifyPlus.Platform.Storage.write('bookmarkss.json', bookmarks);
        // }} />;
    }).register();

    //@ts-expect-error
    new SpotifyPlus.ContextMenu('Bookmark this song', (uri: string) => {
        console.log(`Bookmarking song: ${uri}`);
        bookmarks.push(uri);
        //@ts-expect-error
        SpotifyPlus.Platform.Storage.write('bookmarkss.json', bookmarks);
    }).register();
};

main();