package com.lenerd46.spotifyplus.scripting;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;

public class SpotifyPlayer implements SpotifyPlusApi {
    @Override
    public void register(ScriptableObject scope, Context ctx, String name) {
        ScriptableObject.putProperty(scope, "spotifyPlayer", Context.javaToJS(this, scope));
    }


}
