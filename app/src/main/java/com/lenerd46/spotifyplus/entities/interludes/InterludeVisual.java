package com.lenerd46.spotifyplus.entities.interludes;

import com.google.android.flexbox.FlexboxLayout;
import com.lenerd46.spotifyplus.entities.SyncableVocals;
import com.lenerd46.spotifyplus.entities.lyrics.Interlude;

public class InterludeVisual implements SyncableVocals {
    public InterludeVisual(FlexboxLayout lineContainer, Interlude interludeMetadata) {

    }

    @Override
    public void animate(double songTimestamp, double deltaTime, boolean isImmediate) {

    }

    @Override
    public boolean isActive() {
        return false;
    }
}
