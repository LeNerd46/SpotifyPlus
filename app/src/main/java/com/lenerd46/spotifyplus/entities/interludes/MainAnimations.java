package com.lenerd46.spotifyplus.entities.interludes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainAnimations {
    public final double yOffsetDamping;
    public final double yOffsetFrequency;
    public final double scaleDamping;
    public final double scaleFrequency;

    public final List<Map.Entry<Double, Double>> baseScaleRange;
    public final List<Map.Entry<Double, Double>> opacityRange;
    public final List<Map.Entry<Double, Double>> yOffsetRange;

    public MainAnimations(double yOffsetDamping, double yOffsetFrequency, double scaleDamping, double scaleFrequency) {
        this.yOffsetDamping = yOffsetDamping;
        this.yOffsetFrequency = yOffsetFrequency;
        this.scaleDamping = scaleDamping;
        this.scaleFrequency = scaleFrequency;

        baseScaleRange = new ArrayList<>();
        opacityRange = new ArrayList<>();
        yOffsetRange = new ArrayList<>();
    }
}
