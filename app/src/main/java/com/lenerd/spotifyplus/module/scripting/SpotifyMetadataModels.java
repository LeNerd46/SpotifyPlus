package com.lenerd.spotifyplus.module.scripting;

import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Typed views of Spotify's internal metadata responses. Unknown fields remain available through toJson(). */
public final class SpotifyMetadataModels {
    private SpotifyMetadataModels() { }

    private static JSONObject object(JSONObject json, String key) { JSONObject value = json.optJSONObject(key); return value == null ? new JSONObject() : value; }
    private static JSONArray array(JSONObject json, String key) { JSONArray value = json.optJSONArray(key); return value == null ? new JSONArray() : value; }
    private static String text(JSONObject json, String key) { return json.optString(key, ""); }
    private static List<String> ids(JSONArray values, String key, String kind) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.optJSONObject(i);
            if (item != null && !text(item, key).isEmpty()) result.add(uri(kind, text(item, key)));
        }
        return Collections.unmodifiableList(result);
    }
    private static String uri(String kind, String gid) {
        if (gid == null || !gid.matches("[0-9a-fA-F]{32}")) return "";
        BigInteger number = new BigInteger(gid, 16);
        String alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder value = new StringBuilder();
        do { BigInteger[] pair = number.divideAndRemainder(BigInteger.valueOf(62)); value.append(alphabet.charAt(pair[1].intValue())); number = pair[0]; } while (number.signum() > 0);
        while (value.length() < 22) value.append('0');
        return "spotify:" + kind + ":" + value.reverse();
    }
    private static String image(JSONObject item) {
        String fileId = text(item, "file_id");
        return fileId.isEmpty() ? "" : "https://i.scdn.co/image/" + fileId;
    }
    private static String preferredImage(JSONObject group) {
        JSONArray images = array(group, "image");
        JSONObject selected = null;
        for (int i = 0; i < images.length(); i++) {
            JSONObject item = images.optJSONObject(i);
            if (item == null) continue;
            if (selected == null || "LARGE".equals(text(item, "size"))) selected = item;
            if ("LARGE".equals(text(item, "size"))) break;
        }
        return selected == null ? "" : image(selected);
    }
    public static final class ArtistRef {
        public final String uri, name;
        public ArtistRef(JSONObject json) { uri = uri("artist", text(json, "gid")); name = text(json, "name"); }
    }
    public static final class Disc {
        public final int number;
        public final List<String> tracks;
        public Disc(JSONObject json) { number = json.optInt("number"); tracks = ids(array(json, "track"), "gid", "track"); }
    }
    public static final class Album {
        public final String uri, name, image, label, type;
        public final int popularity, year, month, day;
        public final List<ArtistRef> artists;
        public final List<Disc> discs;
        private final JSONObject raw;
        public Album(JSONObject json, String requestedUri) {
            raw = json; uri = json.optString("canonical_uri", requestedUri); name = text(json, "name");
            label = text(json, "label"); type = text(json, "type"); popularity = json.optInt("popularity");
            image = preferredImage(object(json, "cover_group"));
            JSONObject date = object(json, "date"); year = date.optInt("year"); month = date.optInt("month"); day = date.optInt("day");
            List<ArtistRef> a = new ArrayList<>();
            JSONArray sourceArtists = array(json, "artist");
            for (int i = 0; i < sourceArtists.length(); i++) if (sourceArtists.optJSONObject(i) != null) a.add(new ArtistRef(sourceArtists.optJSONObject(i)));
            artists = Collections.unmodifiableList(a);
            List<Disc> d = new ArrayList<>();
            JSONArray sourceDiscs = array(json, "disc");
            for (int i = 0; i < sourceDiscs.length(); i++) if (sourceDiscs.optJSONObject(i) != null) d.add(new Disc(sourceDiscs.optJSONObject(i)));
            discs = Collections.unmodifiableList(d);
        }
        public JSONObject toJson() { return raw; }
    }
    public static final class Artist {
        public final String uri, name, image;
        public final int popularity;
        public final List<String> topTracks, albums, singles, compilations, appearsOn;
        private final JSONObject raw;
        public Artist(JSONObject json, String requestedUri) {
            raw = json; uri = requestedUri; name = text(json, "name"); popularity = json.optInt("popularity");
            image = preferredImage(object(json, "portrait_group"));
            List<String> tracks = new ArrayList<>();
            JSONArray territories = array(json, "top_track");
            for (int i = 0; i < territories.length(); i++) {
                JSONObject territory = territories.optJSONObject(i);
                if (territory != null) tracks.addAll(ids(array(territory, "track"), "gid", "track"));
            }
            topTracks = Collections.unmodifiableList(tracks);
            albums = groupedAlbums(json, "album_group"); singles = groupedAlbums(json, "single_group");
            compilations = groupedAlbums(json, "compilation_group"); appearsOn = groupedAlbums(json, "appears_on_group");
        }
        private static List<String> groupedAlbums(JSONObject json, String key) {
            List<String> result = new ArrayList<>();
            JSONArray groups = array(json, key);
            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.optJSONObject(i);
                if (group != null) result.addAll(ids(array(group, "album"), "gid", "album"));
            }
            return Collections.unmodifiableList(result);
        }
        public JSONObject toJson() { return raw; }
    }
    public static final class PlaylistItem {
        public final String uri, addedBy, timestamp, itemId;
        public PlaylistItem(JSONObject json) {
            uri = text(json, "uri"); JSONObject attributes = object(json, "attributes");
            addedBy = text(attributes, "addedBy"); timestamp = text(attributes, "timestamp"); itemId = text(attributes, "itemId");
        }
    }
    public static final class Playlist {
        public final String uri, revision, name, picture, ownerUsername, timestamp, createdAt;
        public final int length, position;
        public final boolean truncated, userCreated, canEditItems, canEditMetadata;
        public final List<PlaylistItem> items;
        private final JSONObject raw;
        public Playlist(JSONObject json, String requestedUri) {
            raw = json; uri = requestedUri; revision = text(json, "revision"); length = json.optInt("length");
            JSONObject attributes = object(json, "attributes"); name = text(attributes, "name");
            // picture is an opaque/base64 metadata value, NOT a CDN image ID or URL.
            picture = text(attributes, "picture"); ownerUsername = text(json, "ownerUsername");
            timestamp = text(json, "timestamp"); createdAt = text(json, "createdAt"); userCreated = json.optBoolean("isUserCreated");
            JSONObject capabilities = object(json, "capabilities");
            canEditItems = capabilities.optBoolean("canEditItems"); canEditMetadata = capabilities.optBoolean("canEditMetadata");
            JSONObject contents = object(json, "contents"); position = contents.optInt("pos"); truncated = contents.optBoolean("truncated");
            List<PlaylistItem> values = new ArrayList<>(); JSONArray source = array(contents, "items");
            for (int i = 0; i < source.length(); i++) if (source.optJSONObject(i) != null) values.add(new PlaylistItem(source.optJSONObject(i)));
            items = Collections.unmodifiableList(values);
        }
        public JSONObject toJson() { return raw; }
    }
}
