package com.lenerd46.spotifyplus.scripting;

import android.util.Log;
import de.robv.android.xposed.XposedBridge;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public class Debug implements SpotifyPlusApi {
    String scriptName = "null";

    @Override
    public void register(Scriptable scope, Context ctx, String name) {
        ScriptableObject.putProperty(scope, "console", Context.javaToJS(this, scope));
        scriptName = name;
    }

    public void log(String message) {
        XposedBridge.log("[SpotifyPlus] [" + scriptName + "] " + message);
        Log.d("SpotifyPlus.Scripts", message);
    }
    public void warn(String message) {
        XposedBridge.log("[SpotifyPlus] [" + scriptName + "][WARN] " + message);
        Log.d("SpotifyPlus.Scripts", message);
    }
    public void error(String message) {
        XposedBridge.log("[SpotifyPlus] [" + scriptName + "][ERROR] " + message);
        Log.d("SpotifyPlus.Scripts", message);
    }
}
