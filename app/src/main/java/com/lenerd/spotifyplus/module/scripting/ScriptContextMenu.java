package com.lenerd.spotifyplus.module.scripting;

public class ScriptContextMenu {
    public final String id;
    public final String scriptId;
    public final String title;
    public int resourceId;

    public ScriptContextMenu(String id, String scriptId, String title) {
        this.id = id;
        this.scriptId = scriptId;
        this.title = title;
    }
}
