package com.lenerd46.spotifyplus.curveinterpolator;

/*
 *
 * Java implementation of curve-interpolator by kjerandp
 * https://github.com/kjerandp/curve-interpolator
 *
 */

public class CurveInterpolatorOptions extends SplineCurveOptions {
    public final double arcDivisions;
    public final double numericalApproximationOrder;
    public final double numericalInverseSamples;
    public final double lMargin;

    public CurveInterpolatorOptions(double tension, double alpha, boolean closed, double arcDivisions, double numericalApproximationOrder, double numericalInverseSamples, double lMargin) {
        super(tension, alpha, closed);

        this.arcDivisions = arcDivisions;
        this.numericalApproximationOrder = numericalApproximationOrder;
        this.numericalInverseSamples = numericalInverseSamples;
        this.lMargin = lMargin;
    }
}
