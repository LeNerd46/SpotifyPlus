package com.lenerd.spotifyplus.module.scripting;

public class ScriptContextMenu {
    public final String id;
    public final String scriptId;
    public final String title;
    public int resourceId;
    public final java.util.Set<String> types;
    public final boolean hasCallback;
    public final boolean disabled;

    public boolean matchesUri(String uri) {
        if (types == null) return true;
        if (uri == null) return false;
        String[] parts = uri.split(":", 3);
        return parts.length == 3 && parts[0].equals("spotify") && types.contains(parts[1]);
    }

    public ScriptContextMenu(String id, String scriptId, String title) {
        this(id, scriptId, title, null, false, false);
    }

    public ScriptContextMenu(String id, String scriptId, String title, java.util.Set<String> types,
                             boolean hasCallback, boolean disabled) {
        this.types = types == null ? null : java.util.Set.copyOf(types);
        this.hasCallback = hasCallback;
        this.disabled = disabled;
        this.id = id;
        this.scriptId = scriptId;
        this.title = title;
    }
}
