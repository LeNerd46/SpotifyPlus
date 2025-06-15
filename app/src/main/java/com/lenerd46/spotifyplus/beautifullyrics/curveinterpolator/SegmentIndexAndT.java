package com.lenerd46.spotifyplus.beautifullyrics.curveinterpolator;

/*
 *
 * Java implementation of curve-interpolator by kjerandp
 * https://github.com/kjerandp/curve-interpolator
 *
 */

public class SegmentIndexAndT {
    public final int index;
    public final double weight;

    public SegmentIndexAndT(int index, double weight) {
        this.index = index;
        this.weight = weight;
    }
}
