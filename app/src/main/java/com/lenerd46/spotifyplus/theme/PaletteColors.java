package com.lenerd46.spotifyplus.theme;

public final class PaletteColors {
    public final int[] backgrounds;
    public final int[] foregrounds;

    public PaletteColors(int[] backgrounds, int[] foregrounds) {
        this.backgrounds = backgrounds.clone();
        this.foregrounds = foregrounds.clone();
    }

    public int map(int color, PaletteColors next, boolean foreground) {

        if ((color >>> 24) == 0) return color;
        int[] first = foreground ? foregrounds : backgrounds;
        int[] second = foreground ? backgrounds : foregrounds;
        int[] nextFirst = foreground ? next.foregrounds : next.backgrounds;
        int[] nextSecond = foreground ? next.backgrounds : next.foregrounds;
        for (int i = 0; i < first.length; i++) if (color == first[i]) return nextFirst[i];
        for (int i = 0; i < second.length; i++) if (color == second[i]) return nextSecond[i];

        for (int i = 0; i < first.length; i++) {
            if ((first[i] >>> 24) == 255 && (color & 0xFFFFFF) == (first[i] & 0xFFFFFF))
                return (color & 0xFF000000) | (nextFirst[i] & 0xFFFFFF);
        }
        return color;
    }
}
