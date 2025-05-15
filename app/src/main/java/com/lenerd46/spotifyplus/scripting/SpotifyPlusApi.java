package com.lenerd46.spotifyplus.scripting;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;

public interface SpotifyPlusApi {
    void register(ScriptableObject scope, Context ctx, String name);
}
