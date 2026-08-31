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
