package com.lenerd46.spotifyplus.curveinterpolator;

/*
 *
 * Java implementation of curve-interpolator by kjerandp
 * https://github.com/kjerandp/curve-interpolator
 *
 */

public class NumArray4 {
    public double[] values = new double[4];

    public NumArray4(double a, double b, double c, double d) {
        values[0] = a;
        values[1] = b;
        values[2] = c;
        values[3] = d;
    }

    public double get(int i) {
        return values[i];
    }
}
