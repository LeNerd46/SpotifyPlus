package com.lenerd46.spotifyplus;

import java.util.List;

public class SettingItem {
    public enum Type {
        TOGGLE,
        SLIDER,
        TEXT_INPUT,
        NAVIGATION,
        BUTTON,
        DROPDOWN
    }

    public final String title;
    public final String description;
    public final Type type;

    public Object value;
    public Object minValue;
    public Object maxValue;
    public List<String> options;

    public Runnable onNavigate;
    public SettingValueChangeListener onValueChange;
    public boolean enabled = true;

    public SettingItem(String title, String description, Type type) {
        this.title = title;
        this.description = description;
        this.type = type;
    }

    public SettingItem setValue(Object value) {
        this.value = value;
        return this;
    }

    public SettingItem setRange(Object minValue, Object maxValue) {
        this.minValue = minValue;
        this.maxValue = maxValue;
        return this;
    }

    public SettingItem setOptions(List<String> options) {
        this.options = options;
        return this;
    }

    public SettingItem setOnValueChange(SettingValueChangeListener listener) {
        this.onValueChange = listener;
        return this;
    }

    public SettingItem setOnNavigate(Runnable action) {
        this.onNavigate = action;
        return this;
    }

    public SettingItem setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public interface SettingValueChangeListener {
        void onValueChanged(Object newValue);
    }

    public static class SettingSection {
        public final String title;
        public List<SettingItem> items;

        public SettingSection(String title, List<SettingItem> items) {
            this.title = title;
            this.items = items;
        }
    }
}
