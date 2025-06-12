package com.lenerd46.spotifyplus;

public class SpotifyPlayerState {
    public int position = -1;
    public int duration = -1;
    public String uri = null;
    public boolean isPlaying = false;

    @Override
    public String toString() {
        return "PlaybackInfo{" +
                "positionMs=" + position +
                ", durationMs=" + duration +
                ", trackUri='" + uri + '\'' +
                ", isPlaying=" + isPlaying +
                '}';
    }
}
