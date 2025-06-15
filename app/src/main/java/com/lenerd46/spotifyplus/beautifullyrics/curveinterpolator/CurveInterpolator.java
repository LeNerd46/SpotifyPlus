package com.lenerd46.spotifyplus.beautifullyrics.curveinterpolator;

import java.util.*;
import java.util.stream.Collectors;

import static com.lenerd46.spotifyplus.beautifullyrics.curveinterpolator.Utils.copyValues;

/*
 *
 * Java implementation of curve-interpolator by kjerandp
 * https://github.com/kjerandp/curve-interpolator
 *
 */

public class CurveInterpolator {
    private double lMargin;
    private CurveMapper curveMapper;
    private Map<String, Object> cache = new HashMap<String, Object>();

    public CurveInterpolator(Vector[] points) {
        double tension = 0.5d;
        double alpha = 0d;
        boolean closed = false;

        AbstractCurveMapper curveMapper = new NumericalCurveMapper(24, 21, this::invalidateCache);
        curveMapper.alpha = 0d;
        curveMapper.tension = 0.5d;
        curveMapper.closed = false;
        curveMapper.points = Arrays.asList(points);

        this.lMargin = 1 - curveMapper.tension;
        this.curveMapper = curveMapper;
    }

    public double getTimeFromPosition(double position, boolean clampInput) {
        return this.curveMapper.getT(clampInput ? Utils.clamp(position, 0, 1) : position);
    }

    public double getPositionFromTime(double t, boolean clampInput) {
        return this.curveMapper.getU(clampInput ? Utils.clamp(t, 0, 1) : t);
    }

    public double getPositionFromLength(double length, boolean clampInput) {
        double l = clampInput ? Utils.clamp(length, 0, this.length()) : this.length();
        return this.curveMapper.getU(l / this.length());
    }

    public double getLengthAt(double position, boolean clampInput) {
        return this.curveMapper.lengthAt(clampInput ? Utils.clamp(position, 0, 1) : position);
    }

    public double getTimeAtKnot(int index) {
        if(index < 0 || index > this.points().size() - 1) throw new Error("Invalid index!");
        if(index == 0) return 0; // First knot
        if(!this.closed() && index == this.points().size() - 1) return 1; // Last knot

        int nCount = this.closed() ? this.points().size() : this.points().size() - 1;

        return index / nCount;
    }

    public double getPositionAtKnot(int index) {
        return this.getPositionFromTime(this.getTimeAtKnot(index), false);
    }

    public Vector getPointAtTime(double t, VectorType target) {
        t = Utils.clamp(t, 0.0, 1.0);

        if(t == 0) {
            return copyValues(this.points().get(0), null);
        } else if(t == 1) {
            return copyValues(this.closed() ? this.points().get(0) : this.points().get(this.points().size() - 1), null);
        }

        return this.curveMapper.evaluateForT(SplineSegment::valueAtT, t, null);
    }

    public Vector getPointAt(double position) {
        return this.getPointAtTime(position, null);
    }

    public List<Vector> getIntersects(double v, int axis, int max, double margin) {
        if(margin == -3284324) margin = lMargin;

        var solutions = getIntersectsAsTime(v, axis, max, margin).stream().map(t -> getPointAtTime(t, null)).collect(Collectors.toList());
        return Math.abs(max) == 1 ? (solutions.size() == 1 ? List.of(solutions.get(0)) : null) : solutions;
    }

    public List<Double> getIntersectsAsTime(double v, int axis, int max, double margin) {
        int k = axis;
        Set<Double> solutions = new HashSet<>();
        int nPoints = this.closed() ? this.points().size() : this.points().size() - 1;

        for(int i = 0; i < nPoints && (max == 0 || solutions.size() < Math.abs(max)); i += 1) {
            int idx = (max < 0 ? nPoints - (i + 1) : i);

            List<Vector> controlPoints = SplineCurve.getControlPoints(idx, this.points(), this.closed());
            Vector p1 = controlPoints.get(0);
            Vector p2 = controlPoints.get(1);
            var coefficients = this.curveMapper.getCoefficients(idx);

            double vmin;
            double vmax;

            if(p1.number.get(k) < p2.number.get(k)) {
                vmin = p1.number.get(k);
                vmax = p2.number.get(k);
            } else {
                vmin = p2.number.get(k);
                vmax = p1.number.get(k);
            }

            if(v - margin <= vmax && v + margin >= vmin) {
                var ts = new ArrayList<>(SplineSegment.findRootsOfT(v, coefficients.get(k)));

                if(max < 0) {
                    ts.sort(Comparator.reverseOrder());
                } else if(max >= 0) {
                    ts.sort(Comparator.naturalOrder());
                }

                for(int j = 0; j < ts.size(); j++) {
                    var nt = (ts.get(j) + idx) / nPoints; // Normalize t
                    solutions.add(nt);

                    if(max != 0 && solutions.size() == Math.abs(max)) {
                        break;
                    }
                }
            }
        }

        return new ArrayList<>(solutions);
    }

    private CurveInterpolator invalidateCache() {
        this.cache = new HashMap<>();
        return this;
    }

    public double length() {
        return this.curveMapper.lengthAt(1);
    }

    public List<Vector> points() {
        return this.curveMapper.getPoints();
    }

    public double tension() {
        return this.curveMapper.getTension();
    }

    public double alpha() {
        return this.curveMapper.getAlpha();
    }

    public boolean closed() {
        return this.curveMapper.isClosed();
    }
}
