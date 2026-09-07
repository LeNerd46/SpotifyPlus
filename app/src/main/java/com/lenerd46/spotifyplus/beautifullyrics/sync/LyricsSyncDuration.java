package com.lenerd46.spotifyplus.beautifullyrics.sync;

public final class LyricsSyncDuration {
    private long knownMs;
    private final long estimatedMs;

    public LyricsSyncDuration(long trackDurationMs, double lyricsEndSeconds) {
        knownMs = Math.max(0, trackDurationMs);
        double end = Double.isFinite(lyricsEndSeconds) ? Math.max(0, lyricsEndSeconds) : 0;
        estimatedMs = Math.max(30000, Math.round(Math.min(end, 86400) * 1000) + 30000);
    }

    public void updateMetadata(long durationMs) {
        if (durationMs > 0) knownMs = durationMs;
    }

    public boolean isKnown() {
        return knownMs > 0;
    }

    public long rangeMs(double positionSeconds) {
        if (isKnown()) return knownMs;
        long positionMs = Double.isFinite(positionSeconds) ? Math.round(Math.max(0, positionSeconds) * 1000) : 0;

        return Math.max(estimatedMs, positionMs + 30000);
    }

    public double clampMs(double positionMs) {
        return Math.max(0, isKnown() ? Math.min(knownMs, positionMs) : positionMs);
    }
}
