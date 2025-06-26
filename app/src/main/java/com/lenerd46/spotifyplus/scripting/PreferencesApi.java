package com.lenerd46.spotifyplus.scripting;

import android.content.SharedPreferences;
import com.lenerd46.spotifyplus.References;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.annotations.JSFunction;

public class PreferencesApi implements SpotifyPlusApi {
    SharedPreferences prefs;

    @Override
    public void register(Scriptable scope, Context ctx) {
        ScriptableObject.putProperty(scope, "Preferences", Context.javaToJS(this, scope));
        prefs = (SharedPreferences) ctx.getThreadLocal("prefs");
    }

    @JSFunction
    public Object get(String property, Object defaultValue) {
        var preferences = prefs.getAll();

        if(preferences.containsKey(property)) {
            return preferences.get(property);
        } else {
            return defaultValue;
        }
    }

    @JSFunction
    public void set(String property, Object value) {
        if(value instanceof Boolean) {
            prefs.edit().putBoolean(property, (Boolean) value).apply();
        } else if(value instanceof Integer) {
            prefs.edit().putInt(property, (Integer) value).apply();
        } else if(value instanceof Long) {
            prefs.edit().putLong(property, (Long) value).apply();
        } else if(value instanceof Float) {
            prefs.edit().putFloat(property, (Float) value).apply();
        } else {
            prefs.edit().putString(property, value.toString()).apply();
        }
    }
}
