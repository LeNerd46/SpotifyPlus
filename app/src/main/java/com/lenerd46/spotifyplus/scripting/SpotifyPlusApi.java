package com.lenerd46.spotifyplus.scripting;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public interface SpotifyPlusApi {
    void register(Scriptable scope, Context ctx);
}
