package com.lenerd.spotifyplus.module.lyrics.entities.interlude;

import com.lenerd.spotifyplus.module.lyrics.Spring;

public class MainSprings {
    public final Spring scale;
    public final Spring yOffset;
    public final Spring opacity;

    public MainSprings(Spring scale, Spring yOffset, Spring opacity) {
        this.scale = scale;
        this.yOffset = yOffset;
        this.opacity = opacity;
    }
}
