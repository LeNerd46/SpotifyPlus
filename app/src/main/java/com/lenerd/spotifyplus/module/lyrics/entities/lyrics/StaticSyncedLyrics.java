package com.lenerd.spotifyplus.module.lyrics.entities.lyrics;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class StaticSyncedLyrics {
    public final String type = "Static";
    @SerializedName("Lines")
    public List<TextMetadata> lines;
}
