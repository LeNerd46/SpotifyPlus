package com.lenerd46.spotifyplus.theme;

public final class BackdropBlur {
    public static void apply(int[] pixels, int[] scratch, int width, int height, int radius) {
        pass(pixels, scratch, width, height, radius, true);
        pass(scratch, pixels, width, height, radius, false);
    }

    private static void pass(int[] input, int[] output, int width, int height, int radius, boolean horizontal) {
        int length = horizontal ? width : height;
        int lines = horizontal ? height : width;
        int step = horizontal ? 1 : width;
        int count = radius * 2 + 1;
        for (int line = 0; line < lines; line++) {
            int start = horizontal ? line * width : line;
            int red = 0, green = 0, blue = 0;
            for (int k = -radius; k <= radius; k++) {
                int color = input[start + Math.max(0, Math.min(length - 1, k)) * step];
                red += (color >>> 16) & 255;
                green += (color >>> 8) & 255;
                blue += color & 255;
            }
            for (int position = 0; position < length; position++) {
                output[start + position * step] = 0xFF000000 | (red / count << 16) | (green / count << 8) | blue / count;
                int outgoing = input[start + Math.max(0, position - radius) * step];
                int incoming = input[start + Math.min(length - 1, position + radius + 1) * step];
                red += ((incoming >>> 16) & 255) - ((outgoing >>> 16) & 255);
                green += ((incoming >>> 8) & 255) - ((outgoing >>> 8) & 255);
                blue += (incoming & 255) - (outgoing & 255);
            }
        }
    }

    private BackdropBlur() {}
}
