package com.lenerd46.spotifyplus.beautifullyrics.entities.lyrics;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class LineSyncedLyrics extends TimeMetadata {
    public final String type = "Line";
    @SerializedName("Content")
    public List<Object> content;
}
