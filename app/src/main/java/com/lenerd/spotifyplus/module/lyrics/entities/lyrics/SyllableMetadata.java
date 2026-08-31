package com.lenerd.spotifyplus.module.lyrics.entities.lyrics;

import com.google.gson.annotations.SerializedName;

public class SyllableMetadata extends VocalMetadata {
    @SerializedName("IsPartOfWord")
    public boolean isPartOfWord;
}
