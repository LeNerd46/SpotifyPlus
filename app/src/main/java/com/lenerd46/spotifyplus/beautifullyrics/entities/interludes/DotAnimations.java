package com.lenerd46.spotifyplus.beautifullyrics.entities.interludes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DotAnimations {
    public final double yOffsetDamping;
    public final double yOffsetFrequency;

    public final double scaleDamping;
    public final double scaleFrequency;

    public final double glowDamping;
    public final double glowFrequency;

    public final List<Map.Entry<Double, Double>> scaleRange;
    public final List<Map.Entry<Double, Double>> yOffsetRange;
    public final List<Map.Entry<Double, Double>> glowRange;
    public final List<Map.Entry<Double, Double>> opacityRange;

    public DotAnimations(double yOffsetDamping, double yOffsetFrequency, double scaleDamping, double scaleFrequency, double glowDamping, double glowFrequency) {
        this.yOffsetDamping = yOffsetDamping;
        this.yOffsetFrequency = yOffsetFrequency;

        this.scaleDamping = scaleDamping;
        this.scaleFrequency = scaleFrequency;

        this.glowDamping = glowDamping;
        this.glowFrequency = glowFrequency;

        this.opacityRange = new ArrayList<>();
        this.scaleRange = new ArrayList<>();
        this.yOffsetRange = new ArrayList<>();
        this.glowRange = new ArrayList<>();
    }
}
