import { SpotifyPlus } from 'spotifyplus';
import App from './app';
import { loadCatalog } from './catalog';

void loadCatalog().catch((error) => console.warn('Marketplace startup update check failed', error));

const marketplaceIcon = SpotifyPlus.Assets.image('assets/marketplace.png');

new SpotifyPlus.SideDrawer('Marketplace', () => {
    return <App />
}, marketplaceIcon).register();
