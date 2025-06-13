package com.lenerd46.spotifyplus.curveinterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/*
 *
 * Java implementation of curve-interpolator by kjerandp
 * https://github.com/kjerandp/curve-interpolator
 *
 */

public class SplineSegment {
    public static NumArray4 calcKnotSequence(Vector p0, Vector p1, Vector p2, Vector p3, double alpha) {
        if(alpha == 0) return new NumArray4(0, 1, 2, 3);

        final double t1 = deltaT(p1, p0, alpha);
        final double t2 = deltaT(p2, p1, alpha) + t1;
        final double t3 = deltaT(p3, p2, alpha) + t2;

        return new NumArray4(0, t1, t2, t3);
    }

    private static double deltaT(Vector u, Vector v, double alpha) {
        return Math.pow(CurvyMath.sumOfSquares(u, v), 0.5 * alpha);
    }

    public static List<NumArray4> calculateCoefficients(Vector p0, Vector p1, Vector p2, Vector p3, CurveParameters options) {
        final double tension = Double.isFinite(options.tension) ? options.tension : 0.5d;
        final double alpha = Double.isFinite(options.alpha) ? options.alpha : 0;
        final NumArray4 knotSequence = alpha > 0 ? calcKnotSequence(p0, p1, p2, p3, alpha) : null;
        final List<NumArray4> coefficientList = new ArrayList<>(p0.number.size());

        for(int i = 0; i < p0.number.size(); i++) {
            coefficientList.add(new NumArray4(0.0, 0.0, 0.0, 0.0));
        }

        for(int k = 0; k < p0.number.size(); k++) {
            double u = 0;
            double v = 0;

            final double v0 = p0.number.get(k);
            final double v1 = p1.number.get(k);
            final double v2 = p2.number.get(k);
            final double v3 = p3.number.get(k);

            if(knotSequence == null) {
                u = (1 - tension) * (v2 - v0) * 0.5;
                v = (1 - tension) * (v3 - v1) * 0.5;
            } else {
                final double t0 = knotSequence.get(0);
                final double t1 = knotSequence.get(1);
                final double t2 = knotSequence.get(2);
                final double t3 = knotSequence.get(3);

                if(t1 - t2 != 0) {
                    if(t0 - t1 != 0 && t0 - t2 != 0) {
                        u = (1 - tension) * (t2 - t1) * ((v0 - v1) / (t0 - t1) - (v0 - v2) / (t0 - t2) + (v1 - v2) / (t1 - t2));
                    }

                    if(t1 - t3 != 0 && t2 - t3 != 0) {
                        v = (1 - tension) * (t2 - t1) * ((v1 - v2) / (t1 - t2) - (v1 - v3) / (t1 - t3) + (v2 - v3) / (t2 - t3));
                    }
                }
            }

            final double a = (2 * v1 - 2 * v2 + u + v);
            final double b = (-3 * v1 + 3 * v2 - 2 * u - v);
            final double c = u;
            final double d = v1;

            coefficientList.set(k, new NumArray4(a, b, c, d));
        }

        return coefficientList;
    }

    public static double valueAtT(double t, NumArray4 coefficients) {
        final double t2 = t * t;
        final double t3 = t * t2;

        final double a = coefficients.get(0);
        final double b = coefficients.get(1);
        final double c = coefficients.get(2);
        final double d = coefficients.get(3);

        return a * t3 + b * t2 + c * t + d;
    }

    public static List<Double> findRootsOfT(double lookup, NumArray4 coefficients) {
        double a = coefficients.get(0);
        double b = coefficients.get(1);
        double c = coefficients.get(2);
        double d = coefficients.get(3);
        double x = d - lookup;

        if(a == 0 && b == 0 && c == 0 && x == 0) {
            return List.of(0d); // Whole segment matches - how to deal with this?
        }
        final double eps = Math.pow(2, -42);

        List<Double> roots = Utils.getCubicRoots(a, b, c, x);
        return roots.stream().filter(t -> t > eps && t <= 1 + eps).map(t -> Utils.clamp(t, 0, 1)).collect(Collectors.toList());
    }

    public static Vector evaluateForT(SegmentFunction func, double t, List<NumArray4> coefficients, Vector target) {
        if(target == null) {
            List<Double> buf = new ArrayList<>();

            for(int i = 0; i < coefficients.size(); i++) {
                buf.add(0.0);
            }

            target = new Vector(buf);
        }

        for(int k = 0; k < coefficients.size(); k++) {
            target.number.set(k, func.apply(t, coefficients.get(k)));
        }

        return target;
    }

    public static double derivativeAtT(double t, NumArray4 coefficients) {
        double t2 = t * t;
        double a = coefficients.get(0);
        double b = coefficients.get(1);
        double c = coefficients.get(2);

        return 3 * a * t2 + 2 * b * t + c;
    }
}
