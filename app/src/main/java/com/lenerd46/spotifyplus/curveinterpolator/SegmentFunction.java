package com.lenerd46.spotifyplus.curveinterpolator;

/*
 *
 * Java implementation of curve-interpolator by kjerandp
 * https://github.com/kjerandp/curve-interpolator
 *
 */

@FunctionalInterface
public interface SegmentFunction {
    double apply(double t, NumArray4 coefficients);
}
