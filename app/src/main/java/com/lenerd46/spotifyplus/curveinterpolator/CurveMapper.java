package com.lenerd46.spotifyplus.curveinterpolator;

import java.util.ArrayList;
import java.util.List;

/*
 *
 * Java implementation of curve-interpolator by kjerandp
 * https://github.com/kjerandp/curve-interpolator
 *
 */

public interface CurveMapper {
    double getAlpha();
    double getTension();
    List<Vector> getPoints();
    boolean isClosed();

    Vector evaluateForT(SegmentFunction func, double t, Vector target);
    double lengthAt(double u);
    double getT(double u);
    double getU(double t);
    List<NumArray4> getCoefficients(int idx);
    void reset();
}
