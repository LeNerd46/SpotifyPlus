package com.lenerd46.spotifyplus.beautifullyrics.entities.lyrics;

import com.google.gson.annotations.SerializedName;

public class TimeMetadata {
    @SerializedName("StartTime")
    public double startTime;
    @SerializedName("EndTime")
    public double endTime;
}
