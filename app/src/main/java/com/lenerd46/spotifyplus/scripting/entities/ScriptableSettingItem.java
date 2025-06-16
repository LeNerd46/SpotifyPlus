package com.lenerd46.spotifyplus.scripting.entities;

import com.lenerd46.spotifyplus.SettingItem;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.annotations.JSConstructor;
import org.mozilla.javascript.annotations.JSGetter;
import org.mozilla.javascript.annotations.JSSetter;

public class ScriptableSettingItem extends ScriptableObject {
    private SettingItem settingItem;

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

    @JSGetter
    public String getDescription() {
        return settingItem.description;
    }

    @JSGetter
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

        if(settingItem.onValueChange != null) {
            settingItem.onValueChange.onValueChanged(value);
        }
    }

    @JSSetter
    public void setRange(Object min, Object max) {
        settingItem.minValue = min;
        settingItem.maxValue = max;
    }

    @JSSetter
    public void setEnabled(boolean enabled) {
        settingItem.enabled = enabled;
    }

    // setOnNavigate
}
