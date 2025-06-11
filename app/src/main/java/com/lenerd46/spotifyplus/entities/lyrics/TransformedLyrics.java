package com.lenerd46.spotifyplus.entities.lyrics;

import androidx.annotation.Nullable;
import com.lenerd46.spotifyplus.entities.NaturalAlignment;

public class TransformedLyrics {
    public NaturalAlignment naturalAlignment;
    public String language;
    @Nullable
    public String romanizedLanguage;

    public ProviderLyrics lyrics;
}
