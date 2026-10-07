import { SpotifyPlus } from 'spotifyplus';
import App from './app';

const icon = SpotifyPlus.Assets.image('assets/settings.png');

new SpotifyPlus.SideDrawer('Spotify Plus Settings', () => {
    return <App />
}, icon).register();
