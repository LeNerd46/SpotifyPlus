package com.lenerd.spotifyplus.module.fingerprint;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Small process-safe-enough persistent cache. Entries are scoped to one Spotify APK build. */
final class FileFingerprintCache implements FingerprintCache {
    private final File file;
    private final Properties values = new Properties();

    FileFingerprintCache(File file, String apkIdentity) {
        this.file = file;
        load(apkIdentity);
    }

    private synchronized void load(String apkIdentity) {
        if (file.isFile()) {
            try (InputStream input = Files.newInputStream(file.toPath())) {
                values.load(input);
            } catch (Exception error) {
                FingerprintLog.warn("Could not read fingerprint cache", error);
                values.clear();
            }
        }
        if (!apkIdentity.equals(values.getProperty("@apk"))) {
            values.clear();
            values.setProperty("@apk", apkIdentity);
            save();
        }
    }

    @Override public synchronized String get(String key) { return values.getProperty(key); }

    @Override public synchronized void put(String key, String descriptor) {
        values.setProperty(key, descriptor);
        save();
    }

    @Override public synchronized void remove(String key) {
        if (values.remove(key) != null) save();
    }

    private void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null) Files.createDirectories(parent.toPath());
            File temporary = new File(parent, file.getName() + ".tmp");
            try (OutputStream output = Files.newOutputStream(temporary.toPath())) {
                values.store(output, "SpotifyPlus fingerprint mappings - generated, do not edit");
            }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception error) {
            FingerprintLog.warn("Could not write fingerprint cache", error);
        }
    }
}
