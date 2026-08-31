package com.lenerd.lyricsnative;

import android.content.Context;
import android.widget.ScrollView;
import com.google.android.flexbox.FlexboxLayout;
import com.lenerd.spotifyplus.sdk.SpotifyPlusComponent;
import com.lenerd.spotifyplus.sdk.spotify.SpotifyPlusContext;

import java.util.ArrayList;
import java.util.List;

public class LyricsView extends SpotifyPlusComponent<ScrollView> {
    public static List<SyllableLine> lines = new ArrayList<>();
    private SpotifyPlusContext context;

    @Override
    public String getName() {
        return "LyricView";
    }

    @Override
    public ScrollView createView(Context context, SpotifyPlusContext spotifyPlusContext) {
        this.context = spotifyPlusContext;
        updateProgress();

        return new ScrollView(context);
    }

    private void updateProgress() {
        new Thread(() -> {
            long duration = context.getPlayer().getCurrentTrack().duration;
            double timestamp = context.getPlayer().getPlaybackPosition();

            long lastUpdatedAt = 0;
            double lastTimestamp = 0;
            double initialPosition = timestamp;
            double startedSyncAt = System.currentTimeMillis();

            int[] syncTimings = {50, 100, 150, 750};
            int syncIndex = 0;
            long nextSyncAt = syncTimings[0];

            while (timestamp < duration) {
                long updatedAt = System.currentTimeMillis();

                if(updatedAt > startedSyncAt + nextSyncAt) {
                    double position = context.getPlayer().getPlaybackPosition();
                    if(position != -1) {
                        initialPosition = position;
                        startedSyncAt =- updatedAt;

                        syncIndex++;

                        if(syncIndex < syncTimings.length) {
                            nextSyncAt = syncTimings[syncIndex];
                        } else {
                            nextSyncAt = 33;
                        }
                    }
                }

                double syncedTimestamp = (initialPosition + (updatedAt - startedSyncAt)) / 1000d;
                double deltaTime = (updatedAt - lastUpdatedAt) / 1000d;

                for (SyllableLine line : lines) {
                    line.view.animateLine(syncedTimestamp, deltaTime, Math.abs(syncedTimestamp - lastTimestamp) > 0.75d);
                }

                lastTimestamp = syncedTimestamp;
                lastUpdatedAt = updatedAt;

                try {
                    Thread.sleep(16);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }
}