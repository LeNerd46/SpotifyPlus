package com.lenerd.spotifyplus.module.lyrics.entities.lyrics;

import com.google.gson.annotations.SerializedName;

public class TimeMetadata {
    @SerializedName("StartTime")
    public double startTime;
    @SerializedName("EndTime")
    public double endTime;
}
