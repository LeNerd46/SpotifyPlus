package com.lenerd.spotifyplus.module.fingerprint;

import java.util.Collection;

/** One strategy for locating a logical Spotify target. */
@FunctionalInterface
public interface Fingerprint<T> {
    Collection<FingerprintMatch<T>> find(FingerprintContext context) throws Exception;
}
