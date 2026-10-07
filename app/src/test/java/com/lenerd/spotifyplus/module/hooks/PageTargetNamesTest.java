package com.lenerd.spotifyplus.module.hooks;

import org.junit.Test;
import static org.junit.Assert.*;

public class PageTargetNamesTest {
    @Test public void entitySubpagesDoNotReplaceTheirParentPage() {
        assertEquals("album.page", PageTargetNames.route("spotify:album:abc?ref=home"));
        assertEquals("artist.page", PageTargetNames.route("spotify:artist:abc"));
        assertEquals("artist.discography.page", PageTargetNames.route("spotify:artist:abc:releases:album"));
        assertNull(PageTargetNames.route("spotify:artist:abc:about"));
        assertNull(PageTargetNames.route("spotify:album:abc:tracks"));
        assertNull(PageTargetNames.route("https://open.spotify.com/artist/abc"));
    }
    @Test public void legacyPlaylistRouteIsNotAUserProfile() {
        assertEquals("playlist.page", PageTargetNames.route("spotify:user:owner:playlist:abc"));
        assertEquals("playlist.page", PageTargetNames.route("spotify:playlist:abc"));
        assertEquals("profile.page", PageTargetNames.route("spotify:user:owner"));
        assertNull(PageTargetNames.route("spotify:user:owner:followers"));
    }
    @Test public void tomeMetadataRecognizesSettingsAndDiscographyWithoutGuessingFromReferrers() {
        assertEquals("settings.page", PageTargetNames.pageType("SETTINGS_PLAYBACK"));
        assertEquals("settings.page", PageTargetNames.route("spotify:settings:account"));
        assertEquals("artist.discography.page", PageTargetNames.pageType("ARTIST_RELEASES"));
        assertEquals("profile.page", PageTargetNames.pageType("PROFILE"));
        assertEquals("playlist.page", PageTargetNames.pageType("PLAYLIST"));
        assertEquals("lyrics.page", PageTargetNames.pageType("LYRICS_FULLSCREEN"));
        assertNull(PageTargetNames.pageType("LYRICS_SHARE"));
        assertNull(PageTargetNames.pageType("AUTHOR_RELEASES"));
        assertNull(PageTargetNames.pageType(null));
        assertNull(PageTargetNames.route(null));
    }
}
