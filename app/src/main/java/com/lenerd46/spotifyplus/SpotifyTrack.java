package com.lenerd46.spotifyplus;

public class SpotifyTrack {
    public final String title;
    public final String artist;
    public final String album;
    public final String uri;
    public final long position;
    public final String color;

    public SpotifyTrack(String title, String artist, String album, String uri, long position, String color) {
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.uri = uri;
        this.position = position;
        this.color = color;
    }
}
