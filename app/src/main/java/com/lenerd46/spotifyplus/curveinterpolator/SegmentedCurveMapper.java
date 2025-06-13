package com.lenerd46.spotifyplus.curveinterpolator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.lenerd46.spotifyplus.curveinterpolator.CurvyMath.distance;
import static com.lenerd46.spotifyplus.curveinterpolator.Utils.binarySearch;

/*
 *
 * Java implementation of curve-interpolator by kjerandp
 * https://github.com/kjerandp/curve-interpolator
 *
 */

public class SegmentedCurveMapper extends AbstractCurveMapper {
    public final double subDivisions;

    public SegmentedCurveMapper(double subDivisions, Runnable onInvalidateCache) {
        super(onInvalidateCache);

        this.subDivisions = subDivisions;
    }

    public List<Double> getArcLengths() {
        if(!this.cache.containsKey("arcLengths")) {
            this.cache.put("arcLengths", this.computeArcLengths());
        }

        return (List<Double>)this.cache.get("arcLengths");
    }

    @Override
    public void invalidateCache() {
        super.invalidateCache();
        this.cache.remove("arcLengths");
    }

    public List<Double> computeArcLengths() {
        List<Double> lengths = new ArrayList<Double>();
        Vector current;
        Vector last = this.evaluateForT(SplineSegment::valueAtT, 0, null);
        double sum = 0;

        lengths.add(0d);

        for(int p = 1; p <= this.subDivisions; p++) {
            current = this.evaluateForT(SplineSegment::valueAtT, p / this.subDivisions, null);
            sum += distance(current, last);
            lengths.add(sum);
            last = current;
        }

        return lengths;
    }

    public double lengthAt(double u) {
        try {
            List<Double> lengths = computeArcLengths();
            return u * lengths.get(lengths.size() - 1);
        } catch (Exception e) {}

        return 0;
    }

    public double getT(double u) {
        final List<Double> arcLengths = computeArcLengths();
        final int il = arcLengths.size();
        final double targetArcLength = u * arcLengths.get(il - 1);

        final int i = binarySearch(targetArcLength, arcLengths);
        if(arcLengths.get(i) == targetArcLength) {
            return i / (il - 1);
        }

        final double lengthBefore = arcLengths.get(i);
        final double lengthAfter = arcLengths.get(i + 1);
        final double segmentLength = lengthAfter - lengthBefore;

        final double segmentFraction = (targetArcLength - lengthBefore) / segmentLength;

        return (i + segmentFraction) / (il - 1);
    }

    public double getU(double t) {
        if(t == 0) return 0;
        if(t == 1) return 1;

        final List<Double> arcLengths = this.getArcLengths();
        final int al = arcLengths.size() - 1;
        final double totalLength = arcLengths.get(al);

        // Need to denormalize t to find the matching length
        final double tIdx = t * al;

        final int subIdx = (int)Math.floor(tIdx);
        final double l1 = arcLengths.get(subIdx);

        if(tIdx == subIdx) return l1 / totalLength;

        // Measure the length between t0 at subIdx and t
        final double t0 = subIdx / al;
        final Vector p0 = this.evaluateForT(SplineSegment::valueAtT, t0, null);
        final Vector p1 = this.evaluateForT(SplineSegment::valueAtT, t, null);
        final double l = l1 + distance(p0, p1);

        return l / totalLength;
    }
}
