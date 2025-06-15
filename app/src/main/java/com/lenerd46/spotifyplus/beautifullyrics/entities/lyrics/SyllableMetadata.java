package com.lenerd46.spotifyplus.beautifullyrics.entities.lyrics;

import com.google.gson.annotations.SerializedName;

public class SyllableMetadata extends VocalMetadata {
    @SerializedName("IsPartOfWord")
    public boolean isPartOfWord;
}
