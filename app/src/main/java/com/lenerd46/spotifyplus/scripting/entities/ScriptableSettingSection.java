package com.lenerd46.spotifyplus.scripting.entities;

import com.lenerd46.spotifyplus.SettingItem;
import com.lenerd46.spotifyplus.hooks.RemoveCreateButtonHook;
import com.lenerd46.spotifyplus.hooks.SettingsFlyoutHook;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.annotations.JSConstructor;
import org.mozilla.javascript.annotations.JSFunction;
import org.mozilla.javascript.annotations.JSGetter;
import org.mozilla.javascript.annotations.JSSetter;

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

    @JSSetter
    public void setItems(Object items) {
        section.items = (List<SettingItem>)Context.jsToJava(items, List.class);
    }

    @JSFunction
    public void register(String title) {
        Integer id = (Integer) Context.getCurrentContext().getThreadLocal("id");
        RemoveCreateButtonHook.registerSettingSection(title, id, section);
    }
}
