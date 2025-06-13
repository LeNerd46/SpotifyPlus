package com.lenerd46.spotifyplus.curveinterpolator;

import java.util.List;

/*
 *
 * Java implementation of curve-interpolator by kjerandp
 * https://github.com/kjerandp/curve-interpolator
 *
 */

public class Vector {
    public List<Double> number;
    public VectorType type;

    public Vector(List<Double> number) {
        this.number = number;
    }

    public Vector(List<Double> number, VectorType type) {
        this.number = number;
        this.type = type;
    }
}
