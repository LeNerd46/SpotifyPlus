package com.lenerd46.spotifyplus.beautifullyrics.entities.lyrics;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class SyllableSyncedLyrics extends TimeMetadata {
    public final String type = "Sylalble";
    @SerializedName("Content")
    public List<Object> content;
}
