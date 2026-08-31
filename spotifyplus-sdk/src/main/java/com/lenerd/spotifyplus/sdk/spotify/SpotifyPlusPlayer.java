package com.lenerd.spotifyplus.sdk.spotify;

import com.lenerd.spotifyplus.sdk.spotify.entities.SpotifyTrack;

public interface SpotifyPlusPlayer {
    void play();
    void pause();
    void togglePlay();
    void skipNext();
    void skipPrevious();
    void seek(long positionMs);
    SpotifyTrack getCurrentTrack();
    double getPlaybackPosition();
}
