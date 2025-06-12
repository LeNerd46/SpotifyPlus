package com.lenerd46.spotifyplus;

public class SpotifyTrack {
    public final String title;
    public final String artist;
    public final String album;
    public final String uri;
    public final long position;
    public final String color;
    public final long lastUpdated;
    public final String imageId;

    public SpotifyTrack(String title, String artist, String album, String uri, long position, String color, long lastUpdated, String imageId) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.uri = uri;
        this.position = position;
        this.color = color;
        this.lastUpdated = lastUpdated;
        this.imageId = imageId.split(":").length > 0 ? imageId.split(":")[2] : null;
    }
}
