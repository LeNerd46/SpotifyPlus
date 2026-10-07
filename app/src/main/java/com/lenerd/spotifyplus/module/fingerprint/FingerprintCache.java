package com.lenerd.spotifyplus.module.fingerprint;

interface FingerprintCache {
    String get(String key);
    void put(String key, String descriptor);
    void remove(String key);
}
