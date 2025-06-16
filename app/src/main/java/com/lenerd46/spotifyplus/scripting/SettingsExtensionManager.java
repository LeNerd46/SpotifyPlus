package com.lenerd46.spotifyplus.scripting;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public class SettingsExtensionManager implements SpotifyPlusApi {
    @Override
    public void register(Scriptable scope, Context ctx, String name) {
        ScriptableObject.putProperty(scope, "SettingsExtensionManager", Context.javaToJS(this, scope));
    }

    public void registerSettingsPage() {

    }
}
