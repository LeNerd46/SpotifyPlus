package com.lenerd46.spotifyplus.beautifullyrics.entities.lyrics;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class SyllableVocalSet {
    @SerializedName("Type")
    public String type = "Vocal";
    @SerializedName("OppositeAligned")
    public boolean oppositeAligned;

    @SerializedName("Lead")
    public SyllableVocal lead;
    @SerializedName("Background")
    public List<SyllableVocal> background;
}
