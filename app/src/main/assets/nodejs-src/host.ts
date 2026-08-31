import path from 'path';
import { Logger } from './core/logger';
import { HostConfig, HostRuntime } from './loader/host-runtime';
import { ScriptLoader } from './loader/script-loader';

const logger = new Logger('SpotifyPlusHost');

function resolveHostConfig(): HostConfig {
    const args = process.argv.slice(2);
    if (args.length > 0 && args[0]?.trim().startsWith('{')) {
        try {
            const parsed = JSON.parse(args[0]) as HostConfig;

            return {
                elevatedRoot: parsed.elevatedRoot ? path.resolve(parsed.elevatedRoot) : undefined,
                localRoot: parsed.localRoot ? path.resolve(parsed.localRoot) : undefined,
                installedRoot: parsed.installedRoot ? path.resolve(parsed.installedRoot) : undefined,
                marketplaceRoot: parsed.marketplaceRoot ? path.resolve(parsed.marketplaceRoot) : undefined,
                developerMode: !!parsed.developerMode,
                allowElevatedHotReload: !!parsed.allowElevatedHotReload
            };
        } catch (error) {
            logger.error('Failed to parse host config', error);
        }
    }

    if (args.length > 0) return { marketplaceRoot: path.resolve(args[0]), developerMode: false };
    return { marketplaceRoot: path.join(__dirname, 'scripts'), developerMode: false };
}

async function main(): Promise<void> {
    logger.info(`host.ts starting, __dirname=${__dirname}`);

    const config = resolveHostConfig();
    const runtime = new HostRuntime(logger.child('Runtime'), config);
    const loader = new ScriptLoader(runtime, logger.child('Loader'));
    runtime.setScriptLoader(loader);

    runtime.start();
    runtime.log('Spotify is ready!');

    if (config.elevatedRoot) loader.loadFromRoot(config.elevatedRoot, 'elevated');
    if (config.developerMode && config.localRoot) loader.loadFromRoot(config.localRoot, 'user');
    if (config.installedRoot) loader.loadFromRoot(config.installedRoot, 'user');
    if (config.marketplaceRoot) loader.loadFromRoot(config.marketplaceRoot, 'user');
    // runtime.sendEvent('hostReady', { config });

    runtime.log('Host ready');
}

main().catch(error => {
    console.error('Host crashed', error);
});
