package com.lenerd46.spotifyplus.beautifullyrics.entities.interludes;

public class AnimatedDot {
    public final double start;
    public final double duration;
    public final double glowDuration;

    public final DotLiveText liveText;

    public AnimatedDot(final double start, final double duration, final double glowDuration, final DotLiveText liveText) {
        this.start = start;
        this.duration = duration;
        this.glowDuration = glowDuration;
        
        this.liveText = liveText;
    }
}
