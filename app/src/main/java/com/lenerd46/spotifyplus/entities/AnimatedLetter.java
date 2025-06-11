package com.lenerd46.spotifyplus.entities;

public class AnimatedLetter {
    public final double start;
    public final double duration;
    public final double glowDuration;

    public final LiveText liveText;

    public AnimatedLetter(double start, double duration, double glowDuration, LiveText liveText) {
        this.start = start;
        this.duration = duration;
        this.glowDuration = glowDuration;
        this.liveText = liveText;
    }
}
