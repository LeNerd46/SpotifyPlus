package com.lenerd46.spotifyplus.scripting.entities;

import android.content.SharedPreferences;
import com.lenerd46.spotifyplus.References;
import com.lenerd46.spotifyplus.SettingItem;
import de.robv.android.xposed.XposedBridge;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.annotations.JSConstructor;
import org.mozilla.javascript.annotations.JSFunction;
import org.mozilla.javascript.annotations.JSGetter;
import org.mozilla.javascript.annotations.JSSetter;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public class ScriptableSettingItem extends ScriptableObject {
    private SettingItem settingItem;
    private Function valueChangedFunction;

    public ScriptableSettingItem() { }

    public SettingItem getSettingItem() {
        return settingItem;
    }

    @Override
    public String getClassName() {
        return "SettingItem";
    }

    @JSConstructor
    public void jsConstructor(String title, String description, String typeName) {
        SettingItem.Type type = SettingItem.Type.valueOf(typeName.toUpperCase());
        settingItem = new SettingItem(title, description, type);
    }

    @JSGetter
    public String getTitle() {
        return settingItem.title;
    }

    @JSSetter
    public void setTitle(String title) { }

    @JSGetter
    public String getDescription() {
        return settingItem.description;
    }

    @JSSetter
    public void setDescription(String description) { }

    @JSGetter @JSSetter
    public String getType() {
        return settingItem.type.name();
    }

    @JSGetter @JSSetter
    public Object getValue() {
        return settingItem.value;
    }

    @JSSetter
    public void setValue(Object value) {
        settingItem.value = value;
    }

    @JSGetter
    public Object getRange() {
        return Context.javaToJS(new Object[] { settingItem.minValue, settingItem.maxValue }, getParentScope());
    }

    @JSSetter
    public void setRange(Object range) {
        // item.range = [0, 100]

        Object[] values = (Object[]) Context.jsToJava(range, Object[].class);
        settingItem.minValue = ((Double)values[0]).floatValue();
        settingItem.maxValue = ((Double)values[1]).floatValue();
    }

    @JSGetter
    public String[] getOptions() {
        return settingItem.options.toArray(new String[0]);
    }

    @JSSetter
    public void setOptions(String[] options) {
        settingItem.options = Arrays.asList(options);
    }

    @JSFunction
    public void useDefaultHandling(String title, Object defaultValue) {
        Context context = Context.getCurrentContext();
        SharedPreferences prefs = (SharedPreferences) context.getThreadLocal("prefs");

        switch(settingItem.type) {
            case SLIDER:
                settingItem.setValue(prefs.getInt(title, prefs.getInt(title, ((Double)defaultValue).intValue())));
                break;

            case TOGGLE:
                settingItem.setValue(prefs.getBoolean(title, prefs.getBoolean(title, (Boolean)defaultValue)));
                break;

            case TEXT_INPUT:
            case DROPDOWN:
                settingItem.setValue(prefs.getString(title, prefs.getString(title, defaultValue.toString())));
                break;
        }

        settingItem.setOnValueChange(value -> {
            switch(settingItem.type) {
                case SLIDER:
                    prefs.edit().putInt(title, (int)value).apply();
                    break;

                case TOGGLE:
                    prefs.edit().putBoolean(title, (boolean)value).apply();
                    break;

                case TEXT_INPUT:
                case DROPDOWN:
                    prefs.edit().putString(title, (String)value).apply();
                    break;
            }
        });
    }

    @JSGetter
    public boolean getEnabled() {
        return settingItem.enabled;
    }

    @JSSetter
    public void setEnabled(boolean enabled) {
        XposedBridge.log("[SpotifyPlus] SettingItem.setEnabled: " + enabled);
        settingItem.enabled = enabled;
    }

    @JSGetter
    public Object getOnValueChanged() {
        return Context.javaToJS(valueChangedFunction, getParentScope());
    }

    @JSSetter
    public void setOnValueChanged(Object callback) {
        if(callback instanceof Function) {
            valueChangedFunction = (Function)callback;

            settingItem.setOnValueChange(value -> {
                Context ctx = Context.getCurrentContext();
                Scriptable scope = getParentScope();

                ((Function) callback).call(ctx, scope, this, new Object[] { value });
            });
        }
    }

    // setOnNavigate
}
