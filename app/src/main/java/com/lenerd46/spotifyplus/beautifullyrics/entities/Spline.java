package com.lenerd46.spotifyplus.beautifullyrics.entities;

/*
 *
 * Java implementation of cubic spline by morganherlocker
 * https://github.com/morganherlocker/cubic-spline
 *
 */

import java.util.ArrayList;
import java.util.List;

public class Spline {
    public final List<Double> xs;
    public final List<Double> ys;
    public final List<Double> ks;

    public Spline(List<Double> xs, List<Double> ys) {
        this.xs = xs;
        this.ys = ys;

        List<Double> list = new ArrayList<Double>();

        for(int i = 0; i < xs.size(); i++) {
            list.add(0d);
        }

        this.ks = getNaturalKs(list);
    }

    private List<Double> getNaturalKs(List<Double> ks) {
        int n = xs.size() - 1;
        List<List<Double>> a = zerosMat(n + 1, n + 2);

        for(int i = 1; i < n; i++) {
            a.get(i).set(i - 1, 1 / (xs.get(i) - xs.get(i - 1)));
            a.get(i).set(i, 2 * (1 / (xs.get(i) - xs.get(i - 1)) + 1 / (xs.get(i + 1) - xs.get(i))));
            a.get(i).set(i + 1, 1 / (xs.get(i + 1) - xs.get(i)));
            a.get(i).set(n + 1, 3 * ((ys.get(i) - ys.get(i - 1)) / ((xs.get(i) - xs.get(i - 1)) * (xs.get(i) - xs.get(i - 1))) + (ys.get(i + 1) - ys.get(i)) / ((xs.get(i + 1) - xs.get(i)) * (xs.get(i + 1) - xs.get(i)))));
        }

        a.get(0).set(0, 2 / (xs.get(1) - xs.get(0)));
        a.get(0).set(1, 1 / (xs.get(1) - xs.get(0)));
        a.get(0).set(n + 1, (3 * (ys.get(1) - ys.get(0))) / ((xs.get(1) - xs.get(0)) * (xs.get(1) - xs.get(0))));

        a.get(n).set(n - 1, 1 / (xs.get(n) - xs.get(n - 1)));
        a.get(n).set(n, 2 / (xs.get(n) - xs.get(n - 1)));
        a.get(n).set(n + 1, (3 * (ys.get(n) - ys.get(n - 1))) / ((xs.get(n) - xs.get(n - 1)) * (xs.get(n) - xs.get(n - 1))));

        return solve(a, ks);
    }

    private int getIndexBefore(double target) {
        int low = 0;
        int high = xs.size();
        int mid = 0;

        while(low < high) {
            mid = (int)Math.floor((double)((low + high) / 2));

            if(xs.get(mid) < target && mid != low) {
                low = mid;
            } else if(xs.get(mid) >= target && mid != high) {
                high = mid;
            } else {
                high = low;
            }
        }

        if(low == xs.size() - 1) {
            return xs.size() - 1;
        }

        return low + 1;
    }

    public double at(double x) {
        int i = getIndexBefore(x);

        double t = (x - xs.get(i - 1)) / (xs.get(i) - xs.get(i - 1));

        double a = ks.get(i - 1) * (xs.get(i) - xs.get(i - 1)) - (ys.get(i) - ys.get(i - 1));
        double b = -ks.get(i) * (xs.get(i) - xs.get(i - 1)) + (ys.get(i) - ys.get(i - 1));
        double q = (1 - t) * ys.get(i - 1) + t * ys.get(i) + t * (1 - t) * (a * (1 - t) + b * t);

        return q;
    }

    private List<Double> solve(List<List<Double>> a, List<Double> ks) {
        int m = a.size();
        int h = 0;
        int k = 0;

        while(h < m && k <= m) {
            int iMax = 0;
            double max = Double.NEGATIVE_INFINITY;

            for(int i = h; i < m; i++) {
                double v = Math.abs(a.get(i).get(k));

                if(v > max) {
                    iMax = i;
                    max = v;
                }
            }

            if(a.get(iMax).get(k) == 0) {
                k++;
            } else {
                swapRows(a, h, iMax);

                for(int i = h + 1; i < m; i++) {
                    double f = a.get(i).get(k) / a.get(h).get(k);
                    a.get(i).set(k, 0d);

                    for(int j = k + 1; j <= m; j++) {
                        a.get(i).set(j, a.get(i).get(j) - a.get(h).get(j) * f);
                    }
                }

                h++;
                k++;
            }
        }

        for(int i = m - 1; i >= 0; i--) {
            double v = 0;

            if(a.get(i).get(i) != 0) {
                v = a.get(i).get(m) / a.get(i).get(i);
            }

            ks.set(i, v);

            for(int j = i - 1; j >= 0; j--) {
                a.get(j).set(m, a.get(j).get(m) - a.get(j).get(i) * v);
                a.get(j).set(i, 0d);
            }
        }

        return ks;
    }

    private void swapRows(List<List<Double>> m, int k, int l) {
        var p = m.get(k);
        m.set(k, m.get(l));
        m.set(l, p);
    }

    private List<List<Double>> zerosMat(double r, int c) {
        List<List<Double>> a = new ArrayList<>();

        for(int i = 0; i < r; i++) {
            List<Double> list = new ArrayList<>();

            for(int j = 0; j < c; j++) {
                list.add(0d);
            }

            a.add(list);
        }

        return a;
    }
}
