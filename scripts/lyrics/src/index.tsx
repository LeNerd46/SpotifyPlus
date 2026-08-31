import { SpotifyPlus } from 'spotifyplus';
import App from './app';

SpotifyPlus.log('This script is running!');

SpotifyPlus.Surfaces.register('lyrics-view', (surface: any) => {
    return <App />
});
