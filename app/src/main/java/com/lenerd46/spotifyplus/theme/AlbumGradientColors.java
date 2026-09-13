package com.lenerd46.spotifyplus.theme;

public final class AlbumGradientColors {
    public static int[] stops(int artwork, int background, boolean animated) {

        return new int[]{artwork, animated ? artwork & 0x00FFFFFF : background};
    }

    public static boolean matchesBaseFade(int start, int end, int resourceStart, int resourceEnd) {
        return (start == resourceStart || start == 0x00181818 || start == 0x00121212)
                && (end == resourceEnd || end == 0xFF121212);
    }
    private AlbumGradientColors() {}
}
