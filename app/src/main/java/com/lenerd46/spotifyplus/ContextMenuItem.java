package com.lenerd46.spotifyplus;

public class ContextMenuItem {
    public int id;
    public String title;
    public String type;
    public Runnable callback;

    public ContextMenuItem(int id, String title, String type, Runnable callback) {
        this.id = id;
        this.title = title;
        this.type = type;
        this.callback = callback;
    }
}
