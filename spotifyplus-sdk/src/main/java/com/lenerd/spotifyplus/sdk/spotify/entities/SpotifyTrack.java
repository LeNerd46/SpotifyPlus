package com.lenerd.spotifyplus.sdk.spotify.entities;

public class SpotifyTrack {
    public final String title;
    public final String artist;
    public final String[] artists;
    public final SpotifyAlbum album;
    public final String uri;
    public final String id;
    public final long duration;
    public final long position;
    public final String color;
    public final long lastUpdated;
    public final String imageId;
    public final boolean saved;
    public final boolean explicit;
    public final int trackNumber;

    public SpotifyTrack(String title, String artist, String[] artists, SpotifyAlbum album, String uri, long position, String color, long lastUpdated, String imageId, long duration, boolean saved,  boolean explicit, int  trackNumber) {
        this.title = title;
        this.artist = artist;
        this.artists = artists;
        this.album = album;
        this.uri = uri;
        this.id = uri.split(":")[2];
        this.position = position;
        this.color = color;
        this.lastUpdated = lastUpdated;
        this.imageId = imageId.split(":").length > 0 ? imageId.split(":")[2] : null;
        this.duration = duration;
        this.saved = saved;
        this.explicit = explicit;
        this.trackNumber = trackNumber;
    }
}