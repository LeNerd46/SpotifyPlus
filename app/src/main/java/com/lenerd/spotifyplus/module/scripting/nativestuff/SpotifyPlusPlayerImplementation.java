package com.lenerd.spotifyplus.module.scripting.nativestuff;

import com.lenerd.spotifyplus.module.scripting.SpotifyNativeBridge;
import com.lenerd.spotifyplus.sdk.spotify.SpotifyPlusPlayer;
import com.lenerd.spotifyplus.sdk.spotify.entities.SpotifyTrack;

public class SpotifyPlusPlayerImplementation implements SpotifyPlusPlayer {
    private final SpotifyNativeBridge bridge;

    public SpotifyPlusPlayerImplementation(SpotifyNativeBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void play() {
        bridge.play();
    }

    @Override
    public void pause() {
        bridge.pause();
    }

    @Override
    public void togglePlay() {
        bridge.togglePlay();
    }

    @Override
    public void skipNext() {
        bridge.skipNext();
    }

    @Override
    public void skipPrevious() {
        bridge.skipPrevious();
    }

    @Override
    public void seek(long positionMs) {
        bridge.seek(positionMs);
    }

    @Override
    public SpotifyTrack getCurrentTrack() {
        return bridge.getCurrentTrack();
    }

    @Override
    public double getPlaybackPosition() {
        return bridge.getPlaybackPosition();
    }
}
