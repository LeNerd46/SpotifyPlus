import Animated, {
    getWorkletMetadata,
    useAnimatedStyle,
    useDerivedValue,
    useSharedValue,
    withSpring,
    type WorkletMetadata,
} from "spotifyplus/react/reanimated";
import ClassicAnimated from "spotifyplus/react/animated";
import {
    Gesture,
    GestureDetector,
    GestureState,
} from "spotifyplus/react/Gesture";
import { View } from "spotifyplus/react";
import {
    spotifyPlusWorkletsPlugin,
    transformWorklets,
} from "spotifyplus/build";
import {
    SpotifyPlus,
    type AndroidBackButtonEvent,
    type ExtensionAsset,
    type ExtensionEventEmitterApi,
    type ExtensionEventHandler,
    type ExtensionSettings,
} from "spotifyplus";
import type {
    ImageProps,
    TextStyle,
} from "spotifyplus/react";

const progress = useSharedValue(0);
const classicProgress = new ClassicAnimated.Value(0);
const classicOpacity = classicProgress.interpolate({
    inputRange: [0, 1],
    outputRange: [0, 1],
});
ClassicAnimated.timing(classicProgress, {
    toValue: 1,
    duration: 160,
    useNativeDriver: false,
});
const springProgress = useDerivedValue(() => withSpring(progress.value, {
    energyThreshold: 0.000001,
}));
const style = useAnimatedStyle(() => ({
    opacity: springProgress.value * 0.5,
    transform: [{ scale: withSpring(1) }],
}));
const gesture = Gesture.Pan().onUpdate(event => {
    progress.value = event.translationX ?? 0;
});
const metadata: WorkletMetadata | null = getWorkletMetadata(() => undefined);
const plugin = spotifyPlusWorkletsPlugin({ rootDir: process.cwd() });
const transformed = transformWorklets("function demo() { 'worklet'; return 1; }");
const image: ExtensionAsset = SpotifyPlus.Assets.image("assets/cover.png");
const drawer = new SpotifyPlus.SideDrawer("SDK consumer", () => undefined, image);
const font = SpotifyPlus.Assets.fontFamily([
    { source: "assets/font.ttf", weight: 400 },
]);
const imageProps: ImageProps = { source: image };
const textStyle: TextStyle = {
    fontFamily: font,
    fontWeight: 400,
};
const cachedMetadata = SpotifyPlus.Platform.Storage.Cache.read<{ etag: string }>("marketplace/search.json");
SpotifyPlus.Platform.Storage.Cache.write("marketplace/search.json", {
    etag: "test-etag",
});
SpotifyPlus.Platform.Storage.Cache.write("marketplace/icon.png", new Uint8Array());
SpotifyPlus.Platform.Storage.delete("marketplace/search.json");
SpotifyPlus.Platform.Storage.Cache.delete("marketplace/icon.png");
const openedAutomatically = SpotifyPlus.Navigation.open("mailto:developer@example.com");
const openedInSpotify = SpotifyPlus.Navigation.openSpotify("spotify:album:4aawyAB9vmqN3uQ7FjRGTy");
const openedInBrowser = SpotifyPlus.Navigation.openExternal("https://spotifyplus.dev");
const wentBack = SpotifyPlus.Navigation.back();
const closedSurface = SpotifyPlus.Surfaces.close();
const settings: ExtensionSettings = {
    extensionId: "sdk-consumer",
    settings: [
        {
            title: "Playback",
            items: [
                {
                    id: "enabled",
                    label: "Enabled",
                    type: "toggle",
                    value: true,
                },
            ],
        },
    ],
};
SpotifyPlus.Settings.registerSetting(settings.settings[0]);
SpotifyPlus.Settings.registerSettings(settings.settings);
const handleAndroidBack = (event: AndroidBackButtonEvent) => {
    if (event.surfaceId === "sideDrawer") {
        event.preventDefault();
    }

    const prevented: boolean = event.defaultPrevented;
    void prevented;
};

SpotifyPlus.on("android.backPressed", handleAndroidBack);
SpotifyPlus.off("android.backPressed", handleAndroidBack);

interface SettingsChangedPayload {
    key: string;
    value: unknown;
}

const extensionEvents: ExtensionEventEmitterApi = SpotifyPlus.Events;
const handleSettingChanged: ExtensionEventHandler<SettingsChangedPayload> = event => {
    void event.key;
    void event.value;
};

extensionEvents.on<SettingsChangedPayload>("settings.changed", handleSettingChanged);
extensionEvents.once<SettingsChangedPayload>("settings.changed", handleSettingChanged);
extensionEvents.off<SettingsChangedPayload>("settings.changed", handleSettingChanged);
void extensionEvents.emit<SettingsChangedPayload>("settings.changed", {
    key: "enabled",
    value: true,
});

void Animated.View;
void ClassicAnimated.View;
void classicOpacity;
void GestureDetector;
void GestureState.ACTIVE;
void View;
void gesture;
void metadata;
void plugin;
void style;
void transformed;
void imageProps;
void drawer;
void textStyle;
void closedSurface;
void cachedMetadata;
void openedAutomatically;
void openedInSpotify;
void openedInBrowser;
void wentBack;

// Public package coverage for the Spotify 9.1.82.2160 services.
import type { ConnectDevice, PlaybackState, QueueSnapshot, RepeatMode, SearchResponse, SpotifyEvents } from "spotifyplus";
async function exerciseSpotifyServices() {
    const queue: QueueSnapshot = SpotifyPlus.Queue.get();
    SpotifyPlus.Queue.move(queue, 1, 0);
    SpotifyPlus.Queue.add("spotify:track:abc");
    SpotifyPlus.Platform.Clipboard.writeText("Hello");
    const clipboard: string | null = SpotifyPlus.Platform.Clipboard.readText();
    const albums = await SpotifyPlus.Library.list({ type: "album", limit: 10 });
    if (albums.items[0]) await SpotifyPlus.Player.playContext(albums.items[0], 2);
    SpotifyPlus.Library.like("spotify:track:abc");
    const playlist = SpotifyPlus.Playlists.create("Extension playlist");
    const page = await SpotifyPlus.Playlists.get(playlist);
    SpotifyPlus.Playlists.moveTracks(playlist, page.items.map(item => item.rowId));
    const devices: ConnectDevice[] = SpotifyPlus.Connect.getDevices();
    if (devices[0]) await SpotifyPlus.Connect.transfer(devices[0]);
    const mode: RepeatMode = "repeat";
    SpotifyPlus.Player.setRepeat(mode);
    const search: SearchResponse = await SpotifyPlus.Search.search("Miles Davis", { limit: 10 });
    const user = await SpotifyPlus.User.getCurrent();
    return { clipboard, search, user };
}
SpotifyPlus.Events.on("deviceChanged", event => {
    const device: ConnectDevice | null = event.device;
    void device;
});
SpotifyPlus.Events.once("trackSeeked", event => {
    const position: number = event.positionMs;
    void position;
});
const changed: SpotifyEvents["songChanged"] = { uri: null, previousUri: null };
void changed;
void exerciseSpotifyServices;


// Context-menu filters retain the positional callback/disabled API.
new SpotifyPlus.ContextMenu('Filtered action', uri => console.log(uri), uri => !!uri, false, ['track', 'album']).register();
new SpotifyPlus.ContextMenu('Artist action', () => {}, undefined, false, 'artist').register();
const drawerOpened: Promise<void> = SpotifyPlus.SideDrawer.open();
const menuOpened: Promise<void> = SpotifyPlus.ContextMenu.open({ uri: 'spotify:track:abc123' }, {
    contextUri: 'https://open.spotify.com/playlist/def456',
});
const nowPlayingMenuOpened: Promise<void> = SpotifyPlus.ContextMenu.openNowPlaying();
void drawerOpened; void menuOpened; void nowPlayingMenuOpened;
// @ts-expect-error menu items require a URI
SpotifyPlus.ContextMenu.open({ name: 'Track' });
// @ts-expect-error episodes are not supported as explicit filters
new SpotifyPlus.ContextMenu('Invalid filter', () => {}, undefined, false, 'episode');

const headerRegistration = SpotifyPlus.UI.replace('contextMenu.header', ({ context, Original }) => {
    const uri: string | null = context.uri;
    const NativeContent: import('react').ComponentType = Original;
    void uri; void NativeContent;
    return null;
});
import type { UIInstance, UIPart, UITargetName } from 'spotifyplus';
const addedPages: UITargetName[] = ['album.page', 'artist.page', 'artist.discography.page', 'playlist.page',
    'lyrics.page', 'settings.page', 'profile.page', 'navigation.drawer', 'contextMenu.root'];
for (const page of addedPages) SpotifyPlus.UI.inspect(page);
SpotifyPlus.UI.replace('navigation.drawer', ({ context, NativePart }) => {
    const parts: readonly UIPart[] = context.parts ?? [];
    const Row: import('react').ComponentType<{ id: string }> = NativePart;
    if (parts[0]) void SpotifyPlus.UI.invokeAction(context.instanceId, parts[0].id);
    void Row;
    return null;
});
const liveMenus: Promise<UIInstance[]> = SpotifyPlus.UI.listInstances('contextMenu.root');
void liveMenus;
// @ts-expect-error opaque native IDs are strings
SpotifyPlus.UI.invokeAction('menu', 42);
headerRegistration.dispose();
SpotifyPlus.UI.insertAfter('nowPlaying.controls', () => null);
SpotifyPlus.UI.inspect({ screen: 'nowPlaying.page', resourceId: 'com.spotify.music:id/content' });
// @ts-expect-error target names are checked
SpotifyPlus.UI.replace('not-a-target', () => null);
// @ts-expect-error selectors must use exactly one boundary identifier
SpotifyPlus.UI.inspect({ screen: 'home.page', resourceId: 'content', composeTag: 'content' });

// Local API contracts must remain direct values (await would also accept a Promise).
const localTrack = 'spotify:track:abc123';
const likedNow: boolean = SpotifyPlus.Library.isLiked(localTrack);
const savedNow: boolean[] = SpotifyPlus.Library.contains([localTrack]);
const playerNow: PlaybackState = SpotifyPlus.Player.getState();
const queueNow: QueueSnapshot = SpotifyPlus.Queue.get();
const devicesNow: ConnectDevice[] = SpotifyPlus.Connect.getDevices();
const deviceNow: ConnectDevice | null = SpotifyPlus.Connect.getCurrentDevice();
const clipboardNow: string | null = SpotifyPlus.Platform.Clipboard.readText();
const seekNow: void = SpotifyPlus.Player.seek(1000);
const savedCommand: void = SpotifyPlus.Library.like(localTrack);
const copiedNow: void = SpotifyPlus.Platform.Clipboard.writeText(localTrack);
const storedNow: boolean | null = SpotifyPlus.Platform.Storage.get<boolean>('enabled');
const fileNow: string | Uint8Array | null = SpotifyPlus.Platform.Storage.read<string>('text');
const cacheNow: string | Uint8Array | null = SpotifyPlus.Platform.Storage.Cache.read<string>('text');
void likedNow; void savedNow; void playerNow; void queueNow; void devicesNow; void deviceNow;
void clipboardNow; void seekNow; void savedCommand; void copiedNow; void storedNow; void fileNow; void cacheNow;

const createdNow: { uri: string; name: string } = SpotifyPlus.Playlists.create('Local playlist');
const addedNow: void = SpotifyPlus.Playlists.addTracks(createdNow, [localTrack]);
void createdNow; void addedNow;
