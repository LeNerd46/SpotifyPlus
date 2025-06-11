package com.lenerd46.spotifyplus.entities;

import java.util.List;

public class AnimatedSyllable {
    public final double start;
    public final double duration;

    public final double startScale;
    public final double durationScale;

    public final LiveText liveText;

    public final String type;
    public final List<AnimatedLetter> letters;

    public AnimatedSyllable(double start, double duration, double startScale, double durationScale, LiveText liveText, String type, List<AnimatedLetter> letters) {
        this.start = start;
        this.duration = duration;
        this.startScale = startScale;
        this.durationScale  = durationScale;
        this.liveText = liveText;
        this.type = type;
        this.letters = letters;
    }
}
