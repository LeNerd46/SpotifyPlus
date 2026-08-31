package com.lenerd.spotifyplus.module.lyrics.curveinterpolator;

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
