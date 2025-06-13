package com.lenerd46.spotifyplus.entities.interludes;

import com.lenerd46.spotifyplus.entities.Spring;

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
