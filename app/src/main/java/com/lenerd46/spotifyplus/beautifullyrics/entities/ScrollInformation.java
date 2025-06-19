package com.lenerd46.spotifyplus.beautifullyrics.entities;

import android.view.View;

public class ScrollInformation {
    public final View view;
    public final boolean immediate;

    public ScrollInformation(View view, boolean immediate) {
        this.view = view;
        this.immediate = immediate;
    }
}
