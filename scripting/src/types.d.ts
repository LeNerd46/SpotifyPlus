/** Functions related to debugging */
declare namespace Debug {
    /**
     * Logs a message to both the Xposed log and the Android log
     * @param message The message to log
     */
    function log(message: string): void;
}

/** Represents a Spotify track */
declare class SpotifyTrack {
    /** The title of the track */
    title: string;
    /** The ID of the track */
    id: string;
    /** The artist who created the track */
    artist: SpotifyArtist;
    /** The album the track appears on */
    album: SpotifyAlbum;
    /** The Spotify URI of the track (spotify:track:2n5sAzeWh5LqnV9cGBjgGr)*/
    uri: string;
    /** The length of the track in (?) */
    duration: number;
    /** Whether Spotify believes this track is explicit or not */
    explicit: boolean;
    /** Whether Spotify has lyrics for this song */
    hasLyrics: boolean;
}

/** Represents a Spotify album */
declare class SpotifyAlbum {
    /** The title of the album */
    title: string;
    /** The ID of the album */
    id: string;
    /** The Spotify URI for this album (spotify:album:1NAmidJlEaVgA3MpcPFYGq) */
    uri: string;
    /** The artist who created the album */
    artist: SpotifyArtist;
}

/** Represents a Spotify artist */
declare class SpotifyArtist {
    /** The name of the artist */
    name: string;
    /** The ID of the artist */
    id: string;
    /** The Spotify URI for this artist (spotify:artist:06HL4z0CvFAxyc27GXpf02) */
    uri: string;
}