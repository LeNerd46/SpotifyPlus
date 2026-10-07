package com.lenerd.spotifyplus.module.fingerprint;

import org.luckypray.dexkit.DexKitBridge;

/** Services available while evaluating a fingerprint. */
public record FingerprintContext(DexKitBridge bridge, ClassLoader classLoader) {
    public FingerprintContext {
        if (classLoader == null) throw new IllegalArgumentException("classLoader cannot be null");
    }
}
