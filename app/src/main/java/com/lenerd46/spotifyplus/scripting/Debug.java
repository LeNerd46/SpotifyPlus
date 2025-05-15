package com.lenerd46.spotifyplus.scripting;

import android.util.Log;
import de.robv.android.xposed.XposedBridge;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;

public class Debug implements SpotifyPlusApi {
    String scriptName = "null";

    @Override
    public void register(ScriptableObject scope, Context ctx, String name) {
        ScriptableObject.putProperty(scope, "Debug", Context.javaToJS(this, scope));
        scriptName = name;
    }

    public void log(String message) {
        XposedBridge.log("[SpotifyPlus] [" + scriptName + "] " + message);
        Log.d("SpotifyPlus.Scripts", message);
    }
}
