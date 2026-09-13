package com.lenerd46.spotifyplus.theme;

public final class ImmersiveArtworkGeometry {
    private ImmersiveArtworkGeometry() { }

    public static int height(int width, int viewportHeight, float aspect) {
        if (!(aspect > 0) || !Float.isFinite(aspect)) aspect = 1f;
        float naturalHeight = width / aspect;
        float target = Math.max(naturalHeight, viewportHeight * .78f);
        return Math.max(1, Math.min(viewportHeight, Math.round(Math.min(target, naturalHeight * 1.65f))));
    }

    public static float[] scale(int width, int height, float aspect) {
        if (!(aspect > 0) || !Float.isFinite(aspect)) aspect = 1f;
        float viewportAspect = width / (float) Math.max(1, height);
        return aspect > viewportAspect ? new float[]{aspect / viewportAspect, 1f}
                : new float[]{1f, viewportAspect / aspect};
    }

    public static float[] playerScale(int width, int height, float aspect) {
        float[] crop = scale(width, height, aspect);

        if (height > width && crop[0] > 1f) crop[0] = Math.max(1f, crop[0] / 1.12f);
        return crop;
    }
}
