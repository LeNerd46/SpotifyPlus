package com.lenerd46.spotifyplus.entities.lyrics;

import com.google.gson.annotations.SerializedName;

public class Interlude {
    public TimeMetadata time;
    public final SyncType type = SyncType.INTERLUDE;

    @SerializedName("StartTime")
    private void setStartTime(double startTime) {
        time.startTime = startTime;
    }

    @SerializedName("EndTime")
    private void setEndTime(double endTime) {
        time.endTime = endTime;
    }
}
