package com.lenerd46.spotifyplus.curveinterpolator;

import android.graphics.Path;

import java.util.*;
import java.util.stream.Collectors;

import static com.lenerd46.spotifyplus.curveinterpolator.SplineCurve.getControlPoints;

/*
 *
 * Java implementation of curve-interpolator by kjerandp
 * https://github.com/kjerandp/curve-interpolator
 *
 */

public abstract class AbstractCurveMapper implements CurveMapper {
    protected double subDivisions;
    protected Map<String, Object> cache;
    protected List<Vector> points;
    protected double alpha = 0.0;
    protected double tension = 0.5;
    protected boolean closed = false;
    protected Runnable onInvalidateCache;

    public AbstractCurveMapper(Runnable onInvalidateCache) {
        this.onInvalidateCache = onInvalidateCache;
        this.cache = new HashMap<>();
        this.cache.put("arcLengths", null);
        this.cache.put("coefficients", null);
    }

    protected void invalidateCache() {
        if(this.points == null) return;

        this.cache = new HashMap<>();
        this.cache.put("arcLengths", null);
        this.cache.put("coefficients", null);

        if(this.onInvalidateCache != null) onInvalidateCache.run();
    }

    @Override
    public abstract double lengthAt(double u);
    @Override
    public abstract double getT(double u);
    @Override
    public abstract double getU(double t);

    @Override
    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        if(Double.isFinite(alpha) && alpha != this.alpha) {
            this.invalidateCache();
            this.alpha = alpha;
        }
    }

    @Override
    public double getTension() {
        return tension;
    }

    public void setTension(double tension) {
        if(Double.isFinite(tension) && tension != this.tension) {
            this.invalidateCache();
            this.tension = tension;
        }
    }

    @Override
    public List<Vector> getPoints() {
        return points;
    }

    public void setPoints(List<Vector> points) {
        if(points == null || points.size() < 2) throw new Error("At least 2 control points are required!");

        this.points = points;
        this.invalidateCache();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        if(this.closed != closed) {
            invalidateCache();
            this.closed = closed;
        }
    }

    @Override
    public void reset() {
        this.invalidateCache();
    }

    @Override
    public Vector evaluateForT(SegmentFunction func, double t, Vector target) {
        var segmentInfo = SplineCurve.getSegmentIndexAndT(t, points, closed);
        List<NumArray4> coefficients = getCoefficients(segmentInfo.index);

        return SplineSegment.evaluateForT(func, segmentInfo.weight, coefficients, target);
    }

    @Override
    public List<NumArray4> getCoefficients(int idx) {
        if(this.points == null) return null;

        Map<Integer, List<NumArray4>> coefficients = (Map<Integer, List<NumArray4>>) cache.get("coefficients");
        if(coefficients == null) {
            coefficients = new HashMap<>();
            this.cache.put("coefficients", coefficients);
        }

        if(!coefficients.containsKey(idx)) {
            List<Vector> controlPoints = getControlPoints(idx, points, closed);
            List<NumArray4> coefficientsForIdx = SplineSegment.calculateCoefficients(controlPoints.get(0), controlPoints.get(1), controlPoints.get(2), controlPoints.get(3), new CurveParameters(tension, alpha) {
            });

            coefficients.put(idx, coefficientsForIdx);
        }

        return coefficients.get(idx);
    }
}