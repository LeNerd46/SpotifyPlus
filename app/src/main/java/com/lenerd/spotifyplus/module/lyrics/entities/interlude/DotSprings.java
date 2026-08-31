package com.lenerd.spotifyplus.module.lyrics.entities.interlude;

import com.lenerd.spotifyplus.module.lyrics.Spring;

public class DotSprings {
    public final Spring scale;
    public final Spring yOffset;
    public final Spring glow;
    public final Spring opacity;

    public DotSprings(final Spring scale, final Spring yOffset, final Spring glow, final Spring opacity) {
        this.scale = scale;
        this.yOffset = yOffset;
        this.glow = glow;
        this.opacity = opacity;
    }
}
