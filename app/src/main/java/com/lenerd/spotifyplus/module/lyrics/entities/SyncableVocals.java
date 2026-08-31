package com.lenerd.spotifyplus.module.lyrics.entities;

public interface SyncableVocals {
    void animate(double songTimestamp, double deltaTime, boolean isImmediate);
}
