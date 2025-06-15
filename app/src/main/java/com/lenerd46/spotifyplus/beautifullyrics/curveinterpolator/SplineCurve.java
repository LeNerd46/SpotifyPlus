package com.lenerd46.spotifyplus.beautifullyrics.curveinterpolator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
 *
 * Java implementation of curve-interpolator by kjerandp
 * https://github.com/kjerandp/curve-interpolator
 *
 */

public class SplineCurve {
    public static Vector extrapolateContolPoint(Vector u, Vector v) {
        final ArrayList<Double> e = new ArrayList<>();

        for(int i = 0; i < u.number.size(); i++) {
            e.add(0.0);
        }

        for(int i = 0; i < u.number.size(); i++) {
            e.set(i, 2 * u.number.get(i) - v.number.get(i));
        }

        return new Vector(e);
    }

    public static List<Vector> getControlPoints(int idx, List<Vector> points, boolean closed) {
        final int maxIndex = points.size() - 1;

        Vector p0;
        Vector p1;
        Vector p2;
        Vector p3;

        if(closed) {
            p0 = points.get(idx - 1 < 0 ? maxIndex : idx - 1);
            p1 = points.get(idx % points.size());
            p2 = points.get((idx + 1) % points.size());
            p3 = points.get((idx + 2) % points.size());
        } else {
            if(idx == maxIndex) throw new Error("There is no spline segment at this index for a closed curve!");

            p1 = points.get(idx);
            p2 = points.get(idx + 1);
            p0 = idx > 0 ? points.get(idx - 1) : extrapolateContolPoint(p1, p2);
            p3 = idx < maxIndex - 1 ? points.get(idx + 2) : extrapolateContolPoint(p2, p1);
        }

        return new ArrayList<>(Arrays.asList(p0, p1, p2, p3));
    }

    public static SegmentIndexAndT getSegmentIndexAndT(double ct, List<Vector> points, boolean closed) {
        final int nPoints = closed ? points.size() : points.size() - 1;
        if(ct == 1.0) return new SegmentIndexAndT(nPoints - 1, 1.0);

        final double p = nPoints * ct;
        final double index = Math.floor(p);
        final double weight = p - index;

        return new SegmentIndexAndT((int)index, weight);
    }
}
