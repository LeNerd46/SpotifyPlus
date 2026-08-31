package com.lenerd.spotifyplus;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

public final class SettingsSync {
    public static void putBooleanLocal(Context context, String key, boolean value) {
        long now = System.currentTimeMillis();
        SharedPreferences prefs = context.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
        prefs.edit().putBoolean(key, value).putLong(key + "__updated_at", now).apply();
    }

    public static void putStringLocal(Context context, String key, String value) {
        long now = System.currentTimeMillis();
        SharedPreferences prefs = context.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
        prefs.edit().putString(key, value).putLong(key + "__updated_at", now).apply();
    }

    public static void syncLocalToRemote(Context context, SharedPreferences remotePrefs) {
        SharedPreferences local = context.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);

        Map<String, ?> all = local.getAll();
        SharedPreferences.Editor remoteEditor = remotePrefs.edit();
        boolean changed = false;

        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith("__updated_at")) continue;

            long localTs = local.getLong(key + "__updated_at", 0L);
            long remoteTs = remotePrefs.getLong(key + "__updated_at", 0L);

            if (localTs > remoteTs) {
                Object value = entry.getValue();
                if (value instanceof Boolean) remoteEditor.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) remoteEditor.putInt(key, (Integer) value);
                else if (value instanceof Long) remoteEditor.putLong(key, (Long) value);
                else if (value instanceof Float) remoteEditor.putFloat(key, (Float) value);
                else if (value instanceof String) remoteEditor.putString(key, (String) value);

                remoteEditor.putLong(key + "__updated_at", localTs);
                changed = true;
            }
        }

        if (changed) remoteEditor.apply();
    }

    public static void syncRemoteToLocal(Context context, SharedPreferences remotePrefs) {
        SharedPreferences local = context.getSharedPreferences("SpotifyPlus", Context.MODE_PRIVATE);
        Map<String, ?> all = remotePrefs.getAll();
        SharedPreferences.Editor localEditor = local.edit();
        boolean changed = false;

        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith("__updated_at")) continue;

            long remoteTs = remotePrefs.getLong(key + "__updated_at", 0L);
            long localTs = local.getLong(key + "__updated_at", 0L);

            if (remoteTs > localTs) {
                Object value = entry.getValue();
                if (value instanceof Boolean) localEditor.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer) localEditor.putInt(key, (Integer) value);
                else if (value instanceof Long) localEditor.putLong(key, (Long) value);
                else if (value instanceof Float) localEditor.putFloat(key, (Float) value);
                else if (value instanceof String) localEditor.putString(key, (String) value);

                localEditor.putLong(key + "__updated_at", remoteTs);
                changed = true;
            }
        }

        if (changed) localEditor.apply();
    }
}