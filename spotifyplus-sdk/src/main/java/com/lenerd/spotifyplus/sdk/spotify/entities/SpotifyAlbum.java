package com.lenerd.spotifyplus.sdk.spotify.entities;

public class SpotifyAlbum {
    public final String title;
    public final String artist;
    public final String release;
    public final String image;

    public SpotifyAlbum(String title, String artist, String release, String image) {
        this.title = title;
        this.artist = artist;
        this.release = release;
        this.image = image;
    }
}
