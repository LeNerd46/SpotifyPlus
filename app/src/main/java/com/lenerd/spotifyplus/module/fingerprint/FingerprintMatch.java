package com.lenerd.spotifyplus.module.fingerprint;

/** A candidate produced by a fingerprint. Higher scores are preferred. */
public record FingerprintMatch<T>(T target, int score, String source) {
    public FingerprintMatch {
        if (target == null) throw new IllegalArgumentException("target cannot be null");
        if (source == null || source.isBlank()) source = "unnamed";
    }
}
