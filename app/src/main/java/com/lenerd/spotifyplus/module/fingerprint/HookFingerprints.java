package com.lenerd.spotifyplus.module.fingerprint;

import org.luckypray.dexkit.DexKitBridge;

import java.io.File;

/** Process-wide entry point used by hooks. */
public final class HookFingerprints {
    private static volatile FingerprintResolver resolver;
    private HookFingerprints() {}

    public static synchronized void initialize(DexKitBridge bridge, ClassLoader loader, String sourceDir, String dataDir) {
        File apk = new File(sourceDir);
        String identity = apk.getAbsolutePath() + ":" + apk.length() + ":" + apk.lastModified();
        File cache = new File(dataDir, "cache/spotifyplus/fingerprints.properties");
        resolver = new FingerprintResolver(new FingerprintContext(bridge, loader), new FileFingerprintCache(cache, identity));
    }

    public static FingerprintResolver get() {
        FingerprintResolver value = resolver;
        if (value == null) throw new IllegalStateException("Fingerprint resolver has not been initialized");
        return value;
    }
}
