package com.lenerd46.spotifyplus.beautifullyrics.entities;

final class SparseArtworkColors {
    private SparseArtworkColors() {}

    static boolean recover(int[] pixels, int width, int height) {
        int dark = 0, colorful = 0;
        int[] seeds = new int[64];
        float[] scores = new float[64];
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
            int max = Math.max(r, Math.max(g, b));
            int chroma = max - Math.min(r, Math.min(g, b));
            if (max < 40) dark++;
            if (max < 75 || chroma < 45) continue;
            colorful++;
            int cell = Math.min(7, (i / width) * 8 / height) * 8
                    + Math.min(7, (i % width) * 8 / width);
            float score = chroma * max;
            if (score > scores[cell]) {
                scores[cell] = score;
                seeds[cell] = i;
            }
        }
        // Leave monochrome, earth tones and ordinary colorful covers entirely alone.
        if (dark < pixels.length * 0.65f || colorful < pixels.length * 0.008f
                || colorful > pixels.length * 0.30f) return false;
        int[] source = pixels.clone();
        for (int i = 0; i < pixels.length; i++) {
            float r = 0, g = 0, b = 0, total = 0;
            for (int cell = 0; cell < seeds.length; cell++) {
                if (scores[cell] == 0) continue;
                int seed = seeds[cell];
                float dx = (i % width - seed % width) / (float) width;
                float dy = (i / width - seed / width) / (float) height;
                float distance = 0.012f + dx * dx + dy * dy;
                float weight = 1f / (distance * distance * distance);
                int color = source[seed];
                r += ((color >> 16) & 255) * weight;
                g += ((color >> 8) & 255) * weight;
                b += (color & 255) * weight;
                total += weight;
            }
            // Spread actual artwork colors into soft pools without adding invented hues.
            pixels[i] = 0xFF000000 | (Math.round(r / total * 0.85f) << 16)
                    | (Math.round(g / total * 0.85f) << 8) | Math.round(b / total * 0.85f);
        }
        return true;
    }
}
