package com.lenerd46.spotifyplus.scripting.entities;

import com.lenerd46.spotifyplus.SettingItem;
import com.lenerd46.spotifyplus.hooks.SettingsFlyoutHook;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.annotations.JSConstructor;
import org.mozilla.javascript.annotations.JSFunction;
import org.mozilla.javascript.annotations.JSGetter;

import java.util.List;
import java.util.stream.Collectors;

public class ScriptableSettingSection extends ScriptableObject {
    private SettingItem.SettingSection section;

    public ScriptableSettingSection() { }

    @Override
    public String getClassName() {
        return "SettingSection";
    }

    @JSConstructor
    public void jsConstructor(String title, Object jsList) {
        @SuppressWarnings("unchecked")
        List<ScriptableSettingItem> list = (List<ScriptableSettingItem>) Context.jsToJava(jsList, List.class);

        section = new SettingItem.SettingSection(title, list.stream().map(x -> x.getSettingItem()).collect(Collectors.toList()));
    }

    @JSGetter
    public String getTitle() {
        return section.title;
    }

    @JSGetter
    public Object getItems() {
        return Context.javaToJS(section.items, getParentScope());
    }

    @JSFunction
    public void register(String title) {
        SettingsFlyoutHook.registerSettingSection(title, section);
    }
}
