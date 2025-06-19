package com.lenerd46.spotifyplus.scripting;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public class SettingsExtensionManager implements SpotifyPlusApi {
    @Override
    public void register(Scriptable scope, Context ctx, String name) {
        ScriptableObject.putProperty(scope, "SettingsExtensionManager", Context.javaToJS(this, scope));
    }

    // I just realized you need an entire view for this. This will not be implemented for a while
    // But pretty much, you will be able to easily add an item to the flyout menu, just like the settings and marketplace
    public void registerMenuButton() {

    }
}
