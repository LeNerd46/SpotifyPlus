package com.lenerd46.spotifyplus.beautifullyrics.sync;

import com.lenerd46.spotifyplus.beautifullyrics.sync.LyricsSyncDraft.Part;
import com.lenerd46.spotifyplus.beautifullyrics.sync.LyricsSyncDraft.Vocal;

final class LyricsSyncPreview {
    static double end(Vocal vocal) {
        double end = -1;
        for (Part part : vocal.parts()) end = Math.max(end, part.end);
        return end;
    }

    static boolean finished(Part part, double vocalEnd, double now) {
        return part.end >= 0 && now >= part.end && now >= vocalEnd + .3;
    }
}
