package com.lenerd.spotifyplus.module.scripting;

public class ScriptSideDrawerItem {
    public final String id;
    public final String scriptId;
    public final String title;
    public final String iconAssetId;
    public int resourceId;
    public Object icon;

    public ScriptSideDrawerItem(String id, String scriptId, String title, String iconAssetId) {
        this.id = id;
        this.scriptId = scriptId;
        this.title = title;
        this.iconAssetId = iconAssetId;
    }
}
