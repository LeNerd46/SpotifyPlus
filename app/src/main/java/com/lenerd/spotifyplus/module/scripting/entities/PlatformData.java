package com.lenerd.spotifyplus.module.scripting.entities;

public class PlatformData {
    public String clientVersion;
    public String osName;
    public String osVersion;
    public int sdkVersion;

    public PlatformData(String clientVersion, String osName, String osVersion, int sdkVersion) {
        this.clientVersion = clientVersion;
        this.osName = osName;
        this.osVersion = osVersion;
        this.sdkVersion = sdkVersion;
    }
}
