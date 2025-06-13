package com.lenerd46.spotifyplus.curveinterpolator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.lenerd46.spotifyplus.curveinterpolator.CurvyMath.magnitude;
import static com.lenerd46.spotifyplus.curveinterpolator.Utils.getGaussianQuadraturePointsAndWeights;

/*
 *
 * Java implementation of curve-interpolator by kjerandp
 * https://github.com/kjerandp/curve-interpolator
 *
 */

public class NumericalCurveMapper extends AbstractCurveMapper {
    private final int nSamples;
    private List<List<Double>> gauss;

    public NumericalCurveMapper(int nQuadraturePoints, int nInverseSamples, Runnable onInvalidateCache) {
        super(onInvalidateCache);

        try {
            this.gauss = getGaussianQuadraturePointsAndWeights(nQuadraturePoints);
        } catch (Exception e) { }

        this.nSamples = nInverseSamples == -1 ? 21 : nInverseSamples;
    }

    @Override
    public void invalidateCache() {
        super.invalidateCache();

        this.cache.remove("arcLengths");
        this.cache.remove("samples");
    }

    public List<Double> getArcLengths() {
        if(!this.cache.containsKey("arcLengths")) {
            this.cache.put("arcLengths", this.computeArcLengths());
        }

        return (List<Double>) this.cache.get("arcLengths");
    }

    public List<List<Double>> getSamples(int idx) {
        if(this.points == null) return null;
        if(!this.cache.containsKey("samples")) {
            this.cache.put("samples", new HashMap<Integer, List<List<Double>>>());
        }

        if(!((List<List<Double>>)this.cache.get("samples")).contains(idx)) {
            final int samples = this.nSamples;
            final List<Double> lengths = new ArrayList<>();
            final List<Double> slopes = new ArrayList<>();
            final var coefficients = this.getCoefficients(idx);

            for(int i = 0; i < samples; ++i) {
                final int ti = i / (samples - 1);
                lengths.add(this.computeArcLength(idx, 0.0, ti));
                final double dtln = magnitude(SplineSegment.evaluateForT(SplineSegment::derivativeAtT, ti, coefficients, null));
                double slope = dtln == 0 ? 0 : 1 / dtln;
                // Avoid extreme slopes for near linear curve at the segment endpoints (high tension parameter value)
                if(this.tension > 0.95) {
                    slope = Utils.clamp(slope, -1, 1);
                }

                slopes.add(slope);
            }

            // Precalculate the cubic interpolant coefficients
            final int nCoeff = samples - 1;
            List<Double> dis = new ArrayList<>(); // Degree 3 coefficients
            List<Double> cis = new ArrayList<>(); // Degree 2 coefficients

            double liPrev = lengths.get(0);
            double tdiPrev = slopes.get(0);
            final double step = 1.0 / nCoeff;

            for(int i = 1; i < nCoeff; ++i) {
                double li = liPrev;
                liPrev = lengths.get(i + 1);

                double lDiff = liPrev - li;
                double tdi = tdiPrev;
                double tdiNext = slopes.get(i + 1);

                tdiPrev = tdiNext;
                double si = step / lDiff;
                double di = (tdi + tdiNext - 2 * si) / (lDiff * lDiff);
                double ci = (3 * si - 2 * tdi - tdiNext) / lDiff;

                dis.add(di);
                cis.add(ci);
            }

            var listThing = (HashMap<Integer, List<List<Double>>>)this.cache.get("samples");
            listThing.put(idx, List.of(lengths, slopes, cis, dis));
            this.cache.put("samples", listThing);
        }

        return ((HashMap<Integer, List<List<Double>>>)this.cache.get("samples")).get(idx);
    }

    public double computeArcLength(int index, double t0, double t1) {
        if(t0 == t1) return 0;

        var coefficients = this.getCoefficients(index);
        double z = (t1 - t0) * 0.5d;

        double sum = 0;
        for(int i = 0; i < this.gauss.size(); i++) {
            List<Double> gaussThing = this.gauss.get(i);
            double T = gaussThing.get(0);
            double C = gaussThing.get(1);

            double t = z * T + z + t0;
            double dtln = magnitude(SplineSegment.evaluateForT(SplineSegment::derivativeAtT, t, coefficients, null));

            sum += C * dtln;
        }

        return z * sum;
    }

    public List<Double> computeArcLengths() {
        if(this.points == null) return null;
        List<Double> lengths = new ArrayList<>();
        lengths.add(0d);

        int nPoints = this.closed ? this.points.size() : this.points.size() - 1;
        double tl = 0;
        for(int i = 0; i < nPoints; i++) {
            double length = this.computeArcLength(i, 0d, 1d);
            tl += length;
            lengths.add(tl);
        }

        return lengths;
    }

    public double inverse(int idx, double len) {
        int nCoeff = this.nSamples - 1;
        double step = 1.0 / nCoeff;
        List<List<Double>> samples = this.getSamples(idx);
        List<Double> lengths = samples.get(0);
        List<Double> slopes = samples.get(1);
        List<Double> cis = samples.get(2);
        List<Double> dis = samples.get(3);
        double length = lengths.get(lengths.size() - 1);

        if (len >= length) {
            return 1.0;
        }

        if(len <= 0) {
            return 0.0;
        }

        int i = Math.max(0, Utils.binarySearch(len, lengths));
        double ti = i * step;

        if(lengths.get(i) == len) {
            return ti;
        }

        double tdi = slopes.get(i);
        double di = dis.get(i);
        double ci = cis.get(i);
        double ld = len - lengths.get(i);

        return ((di * ld + ci) * ld + tdi) * ld + ti;
    }

    public double lengthAt(double u) {
        var arcLengths = this.getArcLengths();
        return u * arcLengths.get(arcLengths.size() - 1);
    }

    public double getT(double u) {
        var arcLengths = this.getArcLengths();
        int il = arcLengths.size();
        double targetArcLength = u * arcLengths.get(il - 1);

        int i = Utils.binarySearch(targetArcLength, arcLengths);
        int ti = i / (il - 1);

        if(arcLengths.get(i) == targetArcLength) {
            return ti;
        }

        double len = targetArcLength - arcLengths.get(i);
        double fraction = this.inverse(i, len);

        return (i + fraction) / (il - 1);
    }

    public double getU(double t) {
        if(t == 0) return 0;
        if(t == 1) return 1;

        var arcLengths = this.getArcLengths();
        int al = arcLengths.size() - 1;
        double totalLength = arcLengths.get(al);

        double tIdx = t * al;

        int subIdx = (int)Math.floor(tIdx);
        double l1 = arcLengths.get((int)tIdx);

        if(tIdx == subIdx) return l1 / totalLength;

        double t0 = tIdx - subIdx;
        double fraction = this.computeArcLength(subIdx, 0, t0);

        return (l1 + fraction) / totalLength;
    }
}
