package com.lenerd46.spotifyplus.entities;

public interface SyncableVocals {
    public void animate(double songTimestamp, double deltaTime, boolean isImmediate);
    public boolean isActive();
}
