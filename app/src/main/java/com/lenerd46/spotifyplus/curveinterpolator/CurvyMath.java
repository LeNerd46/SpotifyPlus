package com.lenerd46.spotifyplus.curveinterpolator;

/*
 *
 * Java implementation of curve-interpolator by kjerandp
 * https://github.com/kjerandp/curve-interpolator
 *
 */

public class CurvyMath {
    public static double sumOfSquares(Vector v1, Vector v2) {
        double sumOfSquares = 0;

        for(int i = 0; i < v1.number.size(); i++) {
            sumOfSquares += (v1.number.get(i) - v2.number.get(i)) * (v1.number.get(i) - v2.number.get(i));
        }

        return sumOfSquares;
    }

    public static double distance(Vector p1, Vector p2) {
        final double sqrs = sumOfSquares(p1, p2);
        return sqrs == 0 ? 0 : Math.sqrt(sqrs);
    }

    public static double magnitude(Vector v) {
        double sumOfSquares = 0;

        for(int i = 0; i < v.number.size(); i++) {
            sumOfSquares += (v.number.get(i)) * v.number.get(i);
        }

        return Math.sqrt(sumOfSquares);
    }
}