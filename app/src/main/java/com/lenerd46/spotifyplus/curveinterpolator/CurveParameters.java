package com.lenerd46.spotifyplus.curveinterpolator;

/*
 *
 * Java implementation of curve-interpolator by kjerandp
 * https://github.com/kjerandp/curve-interpolator
 *
 */

public class CurveParameters {
    public final double tension;
    public final double alpha;

    public CurveParameters(double tension, double alpha) {
        this.tension = tension;
        this.alpha = alpha;
    }
}
