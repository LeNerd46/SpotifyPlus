package com.lenerd46.spotifyplus.theme;

public final class AnimatedThemePalette {
    public static final int SCRIM = 0x33000000;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int SUBDUED = 0xFFEBEDF2;
    public static final int SURFACE = 0xB31A202C;
    public static final int TINTED = 0x85202732;
    public static final int HIGHLIGHT = 0xB33B4555;
    public static final int PRESS = 0xD14B5768;
    public static final int NAVIGATION = 0xEE0A0E16;
    public static final int TRACK_GLASS = 0x26111823;
    public static final int LIST_GLASS = 0x14111823;
    public static final int CONTROL_GLASS = 0x80404B60;

    public static int scrimAlphaFor(int color) {
        int low = SCRIM >>> 24, high = 255;
        while (low < high) {
            int alpha = (low + high) / 2;
            double remaining = (255 - alpha) / 255.0;
            double luminance = 0.2126 * linear(Math.round(((color >>> 16) & 255) * remaining))
                    + 0.7152 * linear(Math.round(((color >>> 8) & 255) * remaining))
                    + 0.0722 * linear(Math.round((color & 255) * remaining));
            if (luminance <= 0.145) high = alpha;
            else low = alpha + 1;
        }
        return low;
    }

    private static double linear(long channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private AnimatedThemePalette() {}
}
