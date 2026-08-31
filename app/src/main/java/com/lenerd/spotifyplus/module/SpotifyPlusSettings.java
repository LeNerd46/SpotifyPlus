package com.lenerd.spotifyplus.module;

public class SpotifyPlusSettings {
    // General
    public static String lastfmUsername = "null";
    public static StartupPage startupPage = StartupPage.HOME;
    public static boolean checkForUpdates = true;
    public static boolean removeCreateButton = false;
    public static boolean animatedAlbumArtworkEnabled = true;
    public static boolean blockAds = false;
    public static boolean blockTelemetry = true;

    // Lyrics
    public static AnimationStyle animationStyle = AnimationStyle.DEFAULT;
    public static String activeFont = "sf-pro-display-bold.ttf";
    public static LyricsFont userSelectedFont = LyricsFont.APPLE;
    public static InterludeDuration interludeDuration = InterludeDuration.SPOTIFY_PLUS;
    public static LineSpacing lineSpacing = LineSpacing.DEFAULT;
    public static BackgroundQuality backgroundQuality = BackgroundQuality.HIGH;
    public static boolean enabledBackground = true;
    public static boolean lineGradient = true;
    public static boolean appleMusicScroll = true;

    public enum StartupPage {
        HOME,
        SEARCH,
        EXPLORE,
        LIBRARY
    }

    public enum AnimationStyle {
        DEFAULT,
        APPLE
    }

    public enum LyricsFont {
        SPOTIFY,
        DEFAULT,
        APPLE
    }

    public enum InterludeDuration {
        BEAUTIFUL_LYRICS,
        SPICY,
        SPOTIFY_PLUS,
        APPLE
    }

    public enum LineSpacing {
        COMPACT,
        DEFAULT,
        SPACIOUS,
        MORE,
        MAX
    }

    public enum BackgroundQuality {
        HIGH,
        MID,
        LOW,
        SUPER_LOW
    }
}
