package com.lenerd46.spotifyplus.beautifullyrics.entities;

public class Blob {
    public final float centerX;
    public final float centerY;
    public final float radius;
    public final float scale;
    public final float rotation;
    public byte opacity;
    public final boolean opposite;

    public Blob(float x, float y, float radius, float scale, float rotation, boolean opposite) {
        this.centerX = x;
        this.centerY = y;
        this.radius = radius;
        this.scale = scale;
        this.rotation = rotation;
        this.opposite = opposite;
    }
}
