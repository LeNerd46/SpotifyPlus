package com.lenerd.spotifyplus.module.lyrics.entities;

import com.lenerd.spotifyplus.module.lyrics.Springs;

public class LiveText {
    public final Object object;
    public final Springs springs;

    public LiveText(Object object, Springs springs) {
        this.object = object;
        this.springs = springs;
    }
}
