package com.lenerd46.spotifyplus.beautifullyrics.sync;

import com.lenerd46.spotifyplus.R;
import com.lenerd46.spotifyplus.References;
import android.media.session.MediaController;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.os.SystemClock;

public final class LyricsSyncPlayer {
    private final MediaController controller;
    private final LyricsSyncDuration duration;
    private com.lenerd46.spotifyplus.hooks.LyricsSyncPlaybackSettings.Lease protection;
    public void protect(android.content.Context context) {
        if (protection != null) return;
        pause();
        protection = com.lenerd46.spotifyplus.hooks.LyricsSyncPlaybackSettings.suspend(context);
    }
    public boolean ready() { return protection == null || protection.ready; }
    public String protectionError() { return protection == null ? null : protection.error; }
    public void close() {
        pause();
        if (protection != null) protection.close();
    }
    public boolean guardEnd() {
        if (!playing() || !hasKnownDuration()) return false;
        if (position() < durationMs() / 1000d - 0.5) return false;
        pause();
        return true;
    }

    public LyricsSyncPlayer(MediaController controller, long trackDuration, double lyricsEndSeconds) {
        if (controller == null || controller.getPlaybackState() == null)
            throw new IllegalArgumentException(References.getString(R.string.sync_playback_unavailable));

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
        if (ready()) {
            if (hasKnownDuration() && position() >= durationMs() / 1000d - 0.5) seek(Math.max(0, durationMs() / 1000d - 3));
            controller.getTransportControls().play();
        }
    }

    public void seek(double seconds) {
        refreshDuration();
        controller.getTransportControls().seekTo(Math.round(duration.clampMs(seconds * 1000)));
    }
}
