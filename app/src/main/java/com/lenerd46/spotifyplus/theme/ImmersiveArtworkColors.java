package com.lenerd46.spotifyplus.theme;

public final class ImmersiveArtworkColors {
    private ImmersiveArtworkColors() { }

    public static int background(int[] pixels) {
        int[] count = new int[512], red = new int[512], green = new int[512], blue = new int[512];
        int winner = -1;
        for (int pixel : pixels) {
            if ((pixel >>> 24) < 128) continue;
            int r = (pixel >> 16) & 255, g = (pixel >> 8) & 255, b = pixel & 255;
            int bucket = ((r >> 5) << 6) | ((g >> 5) << 3) | (b >> 5);
            count[bucket]++;
            red[bucket] += r; green[bucket] += g; blue[bucket] += b;
            if (winner < 0 || count[bucket] > count[winner]) winner = bucket;
        }
        if (winner < 0) return 0xff121212;
        int r = red[winner] / count[winner], g = green[winner] / count[winner], b = blue[winner] / count[winner];
        float scale = Math.min(.8f, 105f / Math.max(1, Math.max(r, Math.max(g, b))));
        return 0xff000000 | (Math.round(r * scale) << 16) | (Math.round(g * scale) << 8) | Math.round(b * scale);
    }
}
