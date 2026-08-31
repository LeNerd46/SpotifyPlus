import { SpotifyPlus } from 'spotifyplus';
import App from './app';

const marketplaceIcon = SpotifyPlus.Assets.image('assets/marketplace.png');

new SpotifyPlus.SideDrawer('Marketplace', () => {
    return <App />
}, marketplaceIcon).register();
