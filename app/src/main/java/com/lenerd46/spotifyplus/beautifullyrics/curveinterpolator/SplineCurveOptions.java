package com.lenerd46.spotifyplus.beautifullyrics.curveinterpolator;

/*
 *
 * Java implementation of curve-interpolator by kjerandp
 * https://github.com/kjerandp/curve-interpolator
 *
 */

public class SplineCurveOptions extends CurveParameters{
    public final boolean closed;

    public SplineCurveOptions(double tension, double alpha, boolean closed) {
        super(tension, alpha);
        this.closed = closed;
    }
}
