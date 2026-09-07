package com.lenerd46.spotifyplus.beautifullyrics.sync;

import android.media.session.MediaController;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.os.SystemClock;

public final class LyricsSyncPlayer {
    private final MediaController controller;
    private final LyricsSyncDuration duration;

    public LyricsSyncPlayer(MediaController controller, long trackDuration, double lyricsEndSeconds) {
        if (controller == null || controller.getPlaybackState() == null)
            throw new IllegalArgumentException("Spotify playback controls are not ready. Play the song and try again.");

        this.controller = controller;
        duration = new LyricsSyncDuration(trackDuration, lyricsEndSeconds);

        refreshDuration();
    }

    private void refreshDuration() {
        MediaMetadata metadata = controller.getMetadata();
        if (metadata != null) duration.updateMetadata(metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
    }

    public boolean hasKnownDuration() {
        refreshDuration();
        return duration.isKnown();
    }

    public long durationMs() {
        refreshDuration();
        return duration.rangeMs(position());
    }

    public double position() {
        return positionAtUptime(SystemClock.uptimeMillis());
    }

    public double positionAtUptime(long eventTime) {
        refreshDuration();
        PlaybackState s = controller.getPlaybackState();

        if (s == null) return -1;

        double ms = s.getPosition();
        if (ms < 0) return -1;

        if (s.getState() == PlaybackState.STATE_PLAYING) {
            long dispatchDelay = Math.max(0, SystemClock.uptimeMillis() - eventTime);
            ms += (SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime() - dispatchDelay) * s.getPlaybackSpeed();
        }

        return duration.clampMs(ms) / 1000d;
    }

    public boolean playing() {
        PlaybackState s = controller.getPlaybackState();
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }

    public void pause() {
        controller.getTransportControls().pause();
    }

    public void play() {
        controller.getTransportControls().play();
    }

    public void seek(double seconds) {
        refreshDuration();
        controller.getTransportControls().seekTo(Math.round(duration.clampMs(seconds * 1000)));
    }
}
