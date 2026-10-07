package com.lenerd.spotifyplus.module.hooks;

/** Route classification deliberately excludes entity subpages and referrer URIs. */
public final class PageTargetNames {
    private PageTargetNames() { }
    public static String route(String uri) {
        if (uri == null) return null;
        String route = uri.split("\\?", 2)[0];
        if (route.equals("spotify:home")) return "home.page";
        if (route.equals("spotify:search") || route.startsWith("spotify:search:")) return "search.page";
        if (route.equals("spotify:collection") || route.equals("spotify:library")) return "library.page";
        if (route.equals("spotify:settings") || route.equals("spotify:preferences") || route.equals("spotify:config")
                || route.startsWith("spotify:settings:")) return "settings.page";
        if (route.matches("spotify:artist:[^:]+:(releases|albums|singles|compilations)(:[^:]+)*")) return "artist.discography.page";
        if (route.matches("spotify:user:[^:]+")) return "profile.page";
        if (route.matches("spotify:user:[^:]+:playlist:[^:]+")) return "playlist.page";
        for (String kind : new String[]{"playlist", "album", "artist"})
            if (route.matches("spotify:" + kind + ":[^:]+")) return kind + ".page";
        return null;
    }
    public static String pageType(String type) {
        if (type == null) return null;
        return switch (type) {
            case "ARTIST_RELEASES" -> "artist.discography.page";
            case "PROFILE" -> "profile.page";
            case "PLAYLIST", "PLAYLIST_ONDEMAND", "FREE_TIER_PLAYLIST", "FREE_TIER_PLAYLIST_ONDEMAND" -> "playlist.page";
            case "LYRICS_FULLSCREEN" -> "lyrics.page";
            case "SETTINGS", "SETTINGS_ABOUT", "SETTINGS_ACCOUNT", "SETTINGS_CONNECTIVITY",
                    "SETTINGS_CONTENT_PERSONALIZATION", "SETTINGS_INTEGRATIONS", "SETTINGS_MEDIA_QUALITY",
                    "SETTINGS_PLAYBACK", "SETTINGS_SOCIAL" -> "settings.page";
            default -> null;
        };
    }
}
