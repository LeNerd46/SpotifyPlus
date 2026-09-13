package com.lenerd46.spotifyplus.beautifullyrics.sync;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.*;

public final class LyricsSyncStore {
    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("SpotifyPlusLyricsDrafts", Context.MODE_PRIVATE);
    }

    static LyricsSyncSession load(Context context, String trackId) {
        String saved = preferences(context).getString(trackId, null);
        if (saved == null) return null;
        try {
            return new LyricsSyncSession(JsonParser.parseString(saved).getAsJsonObject());
        } catch (RuntimeException invalidDraft) {
            return null;
        }
    }

    public static JsonObject source(Context context, String trackId) {
        LyricsSyncSession session = load(context, trackId);
        return session == null ? null : session.source;
    }

    static void save(Context context, String trackId, String saved) {
        preferences(context).edit().putString(trackId, saved).apply();
    }

    static void remove(Context context, String trackId) {
        preferences(context).edit().remove(trackId).apply();
    }
}
