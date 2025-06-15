package com.lenerd46.spotifyplus.beautifullyrics.entities.lyrics;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class SyllableVocal extends TimeMetadata {
    @SerializedName("Syllables")
    public List<SyllableMetadata> syllables;
}
