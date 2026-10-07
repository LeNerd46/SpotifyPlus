package com.lenerd.spotifyplus.module.fingerprint;

import android.util.Log;

/** Keeps the resolver usable in plain JVM tests as well as Android processes. */
final class FingerprintLog {
    private static final String TAG = "SpotifyPlus/Fingerprint";
    private FingerprintLog() {}
    static void debug(String message) { write(Log.DEBUG, message, null); }
    static void info(String message) { write(Log.INFO, message, null); }
    static void warn(String message) { write(Log.WARN, message, null); }
    static void warn(String message, Throwable error) { write(Log.WARN, message, error); }
    private static void write(int priority, String message, Throwable error) {
        try {
            if (error == null) Log.println(priority, TAG, message);
            else Log.println(priority, TAG, message + "\n" + Log.getStackTraceString(error));
        } catch (RuntimeException ignored) {
            // android.jar logging methods are stubs during local unit tests.
        }
    }
}
