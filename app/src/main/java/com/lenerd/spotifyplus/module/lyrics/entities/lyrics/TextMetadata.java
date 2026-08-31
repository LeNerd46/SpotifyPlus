package com.lenerd.spotifyplus.module.lyrics.entities.lyrics;

import androidx.annotation.Nullable;
import com.google.gson.annotations.SerializedName;

public class TextMetadata {
    @SerializedName("Text")
    public String text;
    @Nullable
    public String romanizedText;
}
