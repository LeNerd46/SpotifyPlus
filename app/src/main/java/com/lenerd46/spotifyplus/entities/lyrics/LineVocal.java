package com.lenerd46.spotifyplus.entities.lyrics;

import com.google.gson.annotations.SerializedName;

public class LineVocal extends VocalMetadata {
    public final String Type = "Vocal";
    @SerializedName("OppositeAligned")
    public boolean oppositeAligned;
}
