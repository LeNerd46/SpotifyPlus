package com.lenerd.spotifyplus.module.lyrics.entities.lyrics;

import androidx.annotation.Nullable;
import com.lenerd.spotifyplus.module.lyrics.entities.NaturalAlignment;

public class TransformedLyrics {
    public NaturalAlignment naturalAlignment;
    public String language;
    @Nullable
    public String romanizedLanguage;

    public ProviderLyrics lyrics;
}
