package com.lenerd.spotifyplus.module.scripting;

import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.*;

public class ScriptContextMenuTest {
    @Test public void omittedTypesIncludeUnknownAndUnsupportedContexts() {
        ScriptContextMenu menu = new ScriptContextMenu("id", "script", "title");
        assertTrue(menu.matchesUri(null));
        assertTrue(menu.matchesUri("spotify:episode:one"));
        assertTrue(menu.matchesUri("spotify:track:one"));
    }

    @Test public void selectedTypesMatchOnlyTheSelectedEntity() {
        ScriptContextMenu menu = new ScriptContextMenu("id", "script", "title", Set.of("album", "playlist"), true, false);
        assertTrue(menu.matchesUri("spotify:album:one"));
        assertTrue(menu.matchesUri("spotify:playlist:one"));
        assertFalse(menu.matchesUri("spotify:track:one"));
        assertFalse(menu.matchesUri("spotify:artist:one"));
        assertFalse(menu.matchesUri("spotify:episode:one"));
        assertFalse(menu.matchesUri(null));
        assertFalse(menu.matchesUri("album"));
        assertFalse(menu.matchesUri("other:album:one"));
    }

    @Test public void emptyTypesMatchNothing() {
        ScriptContextMenu menu = new ScriptContextMenu("id", "script", "title", Set.of(), false, false);
        assertFalse(menu.matchesUri("spotify:track:one"));
        assertFalse(menu.matchesUri(null));
    }
}
