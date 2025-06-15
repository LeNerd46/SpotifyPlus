package com.lenerd46.spotifyplus.scripting.entities;

import com.lenerd46.spotifyplus.SpotifyTrack;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.annotations.JSGetter;

public class ScriptableSpotifyTrack extends ScriptableObject {
    SpotifyTrack track;
    String title;

    public ScriptableSpotifyTrack() {}

    @Override
    public String getClassName() {
        return "SpotifyTrack";
    }

    public void setTrack(SpotifyTrack spotifyTrack) {
        this.track = spotifyTrack;
    }

    @JSGetter
    public String getTitle() {
        return title;
    }

    @JSGetter
    public String getArtist() {
        return track.artist;
    }

    @JSGetter
    public String getAlbum() {
        return track.album;
    }

    @JSGetter
    public String getUri() {
        return track.uri;
    }
}
