package com.lenerd.spotifyplus.module.scripting;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.lenerd.spotifyplus.module.SpotifyHook;
import com.lenerd.spotifyplus.module.Utils;
import com.lenerd.spotifyplus.module.hooks.PlayerHook;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Internal Spotify 9.1.82.2160 services. Local RPCs may also be called through JNI. */
public final class SpotifyServices {
    private final ClassLoader loader;
    private final Context context;
    private final okhttp3.OkHttpClient http = new okhttp3.OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build();
    private Object queue;
    private Object queueCore;
    private static final String MODEL = "com.spotify.player.model.";
    private static final String LIBRARY = "spotify.your_library.esperanto.proto.";
    private static final String COLLECTION = "spotify.collection.esperanto.proto.";
    private static final String PLAYLIST = "spotify.playlist.esperanto.proto.";
    private static final String CONNECT = "com.spotify.connect.esperanto.proto.";
    private static volatile SpotifyServices active;
    private Object connectSubscription;
    private Object observedCore;
    private volatile JSONArray devices;
    private String deviceId;
    private JSONObject lastState;
    private long lastSeekAt;
    private long lastSeekPosition;
    private boolean lastSeekFromState;

    public static void onSeekAcknowledged(long position, long previous) {
        SpotifyServices services = active;
        if (services != null) services.seekEvent(position, previous, false);
    }
    private synchronized void seekEvent(long position, long previous, boolean fromState) {
        long now = android.os.SystemClock.elapsedRealtime();
        boolean duplicate = lastSeekFromState != fromState && now - lastSeekAt < 2000
                && Math.abs(position - lastSeekPosition) < 1500;
        lastSeekAt = now;
        lastSeekPosition = position;
        lastSeekFromState = fromState;
        if (duplicate) return;
        try { event("trackSeeked", new JSONObject().put("positionMs", position).put("previousPositionMs", previous)); }
        catch (Exception error) { android.util.Log.e("SpotifyPlus", "Could not publish seek", error); }
    }

    public SpotifyServices(ClassLoader loader, Context context) {
        this.loader = loader; this.context = context;
        active = this;
        new Handler(Looper.getMainLooper()).post(SpotifyServices::onCoreReady);
    }
    public static void onCoreReady() {
        SpotifyServices services = active;
        if (services != null) services.observeConnect();
    }
    public static void onPlayerState(Object state) {
        if (state == null) return;
        Utils.playerState = state;
        SpotifyServices services = active;
        if (services != null) services.observeState(state);
    }
    private void event(String name, JSONObject payload) {
        try { SpotifyNativeBridge.sendJsonEvent("spotify.event", new JSONObject().put("name", name).put("payload", payload)); }
        catch (Throwable error) { android.util.Log.e("SpotifyPlus", "Could not publish " + name, error); }
    }
    private synchronized void observeState(Object state) {
        try {
            Object optional = call(state, "track");
            Object track = (Boolean) call(optional, "c") ? call(optional, "b") : null;
            Object position = call(state, "positionAsOfTimestamp");
            long positionMs = (Boolean) call(position, "c") ? ((Number) call(position, "b")).longValue() : 0;
            boolean paused = (Boolean) call(state, "isPaused");
            boolean playing = (Boolean) call(state, "isPlaying") && !paused;
            Object options = call(state, "options");
            String repeat = (Boolean) call(options, "repeatingTrack") ? "track" : (Boolean) call(options, "repeatingContext") ? "context" : "off";
            JSONObject current = new JSONObject().put("contextUri", call(state, "contextUri"))
                    .put("trackUri", track == null ? JSONObject.NULL : call(track, "uri"))
                    .put("uid", track == null ? "" : call(track, "uid"))
                    .put("isPlaying", playing).put("isPaused", paused)
                    .put("isBuffering", call(state, "isBuffering"))
                    .put("positionMs", positionMs).put("timestamp", call(state, "timestamp"))
                    .put("shuffle", call(options, "shufflingContext")).put("repeat", repeat);
            Object speed = call(state, "playbackSpeed");
            current.put("speed", (Boolean) call(speed, "c") ? ((Number) call(speed, "b")).doubleValue() : 0.0);
            JSONObject previous = lastState;
            if (previous != null && current.getLong("timestamp") < previous.getLong("timestamp")) return;
            lastState = current;
            if (previous == null) return; // The first snapshot establishes a baseline, not a change.
            if (!Objects.equals(previous.opt("contextUri"), current.opt("contextUri")))
                event("contextChanged", new JSONObject().put("uri", current.get("contextUri")).put("previousUri", previous.get("contextUri")));
            boolean sameTrack = Objects.equals(previous.opt("trackUri"), current.opt("trackUri")) && Objects.equals(previous.opt("uid"), current.opt("uid"));
            if (!sameTrack) event("songChanged", new JSONObject().put("uri", current.get("trackUri")).put("previousUri", previous.get("trackUri")));
            if (playing != previous.getBoolean("isPlaying") || paused != previous.getBoolean("isPaused"))
                event("playPause", new JSONObject().put("isPlaying", playing).put("isPaused", paused));
            if (current.getBoolean("shuffle") != previous.getBoolean("shuffle"))
                event("shuffleChanged", new JSONObject().put("enabled", current.getBoolean("shuffle")));
            if (!repeat.equals(previous.getString("repeat"))) event("repeatChanged", new JSONObject().put("mode", repeat));
            long elapsed = current.getLong("timestamp") - previous.getLong("timestamp");
            long expected = previous.getLong("positionMs") + (previous.getBoolean("isPlaying") && !previous.getBoolean("isBuffering") ? (long) (Math.max(0, elapsed) * previous.getDouble("speed")) : 0);
            if (sameTrack && track != null && elapsed >= 0 && Math.abs(positionMs - expected) > 1500)
                seekEvent(positionMs, expected, true);
        } catch (Exception error) { android.util.Log.e("SpotifyPlus", "Could not observe player state", error); }
    }

    private synchronized void observeConnect() {
        Object core = PlayerHook.core;
        if (core == null || observedCore == core) return;
        try {
            if (connectSubscription != null) call(connectSubscription, "dispose");
            Object builder = call(type(CONNECT + "ConnectMessages$StateRequest"), "s");
            call(builder, "q"); // Include this phone as well as remote devices.
            Object stream = call(field(core, "a"), "callStream", "spotify.connect.esperanto.proto.ConnectService", "State", call(builder, "build"));
            Class<?> consumer = type("io.reactivex.rxjava3.functions.Consumer");
            Object next = Proxy.newProxyInstance(loader, new Class<?>[]{consumer}, (proxy, method, args) -> {
                if (method.getName().equals("accept")) updateDevices(call(type(CONNECT + "ConnectMessages$StateResponse"), "t", args[0]));
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == args[0];
                return null;
            });
            Object error = Proxy.newProxyInstance(loader, new Class<?>[]{consumer}, (proxy, method, args) -> {
                if (method.getName().equals("accept")) { devices = null; observedCore = null; android.util.Log.e("SpotifyPlus", "Connect stream failed", (Throwable) args[0]); }
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == args[0];
                return null;
            });
            observedCore = core;
            connectSubscription = call(stream, "subscribe", next, error);
        } catch (Exception error) { observedCore = null; android.util.Log.e("SpotifyPlus", "Could not observe Connect", error); }
    }
    private synchronized void updateDevices(Object state) throws Exception {
        JSONArray result = new JSONArray();
        JSONObject current = null;
        for (Object device : (Iterable<?>) call(state, "o")) {
            String id = (String) call(device, "q");
            if ((Boolean) call(device, "getIsLocal")) id = "local_device";
            else if (id.isEmpty()) id = (String) call(device, "I");
            JSONObject json = new JSONObject().put("id", id).put("name", call(device, "getName"))
                    .put("type", call(device, "W")).put("isActive", call(device, "w"))
                    .put("isLocal", call(device, "getIsLocal")).put("isDisabled", call(device, "z"))
                    .put("supportsVolume", call(device, "U")).put("volume", call(device, "X"));
            result.put(json);
            if (json.getBoolean("isActive")) current = json;
        }
        boolean hadState = devices != null;
        devices = result;
        String nextId = current == null ? null : current.getString("id");
        if (hadState && !Objects.equals(deviceId, nextId)) event("deviceChanged", new JSONObject()
                .put("device", current == null ? JSONObject.NULL : current).put("previousDeviceId", deviceId == null ? JSONObject.NULL : deviceId));
        deviceId = nextId;
    }
    private Class<?> type(String name) throws ClassNotFoundException { return loader.loadClass(name); }

    // Resolve overloads only within a named method. These are not DexKit fingerprints.
    private Object call(Object receiver, String name, Object... args) throws Exception {
        Class<?> owner = receiver instanceof Class<?> ? (Class<?>) receiver : receiver.getClass();
        for (Method method : owner.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length || method.isBridge()) continue;
            Class<?>[] types = method.getParameterTypes();
            boolean match = true;
            for (int i = 0; i < types.length; i++) {
                Class<?> t = types[i];
                if (t == boolean.class) t = Boolean.class;
                if (t == long.class) t = Long.class;
                if (t == int.class) t = Integer.class;
                if (args[i] != null && !t.isInstance(args[i])) { match = false; break; }
            }
            if (match) {
                method.setAccessible(true);
                return method.invoke(receiver instanceof Class<?> ? null : receiver, args);
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + name);
    }
    private Object create(String name, Object... args) throws Exception {
        for (Constructor<?> ctor : type(name).getConstructors()) {
            if (ctor.getParameterCount() == args.length) return ctor.newInstance(args);
        }
        throw new NoSuchMethodException(name + " constructor");
    }
    private Object field(Object value, String name) throws Exception { return value.getClass().getField(name).get(value); }
    private Object ready(Object value) { if (value == null) throw new IllegalStateException("Spotify service is not ready"); return value; }
    private Object rpc(String service, String method, Object request, String responseClass, String parser) throws Exception {
        Object client = field(ready(PlayerHook.core), "a");
        byte[] bytes = (byte[]) await(call(client, "callSingle", service, method, request));
        return call(type(responseClass), parser, bytes);
    }
    private ArrayList<String> strings(JSONArray values) throws Exception {
        ArrayList<String> result = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) result.add(values.getString(i));
        if (result.isEmpty() || result.size() > 100) throw new IllegalArgumentException("Expected 1 to 100 items");
        return result;
    }
    private void status(int code, String reason) {
        if (code != 0 && (code < 200 || code >= 300)) throw new IllegalStateException("Spotify " + code + ": " + reason);
    }
    private Object await(Object single) throws Exception {
        Object timed = call(single, "timeout", 15L, TimeUnit.SECONDS);
        Object value = call(timed, "blockingGet");
        if (value != null && type("p.v8f").isInstance(value) && (Boolean) call(value, "c")) {
            throw new IllegalStateException(String.valueOf(call(call(value, "a"), "g")));
        }
        return value;
    }
    private synchronized Object queue() throws Exception {
        Object core = ready(PlayerHook.core);
        if (queueCore != core) {
            queue = create("p.ihw", field(core, "a"), field(core, "b"));
            queueCore = core;
        }
        return queue;
    }
    private Object queueState() throws Exception {
        Object stream = field(queue(), "c");
        return await(call(stream, "A")); // FlowableElementAtSingle in 9.1.82.2160
    }
    private JSONArray tracks(Object values) throws Exception {
        JSONArray result = new JSONArray();
        for (Object track : (Iterable<?>) values) result.put(track(track));
        return result;
    }
    private JSONObject track(Object track) throws Exception {
        return new JSONObject().put("uri", call(track, "uri")).put("uid", call(track, "uid"))
                .put("metadata", new JSONObject((Map<?, ?>) call(track, "metadata")));
    }
    private JSONObject queueJson(Object state) throws Exception {
        Object current = call(state, "track");
        return new JSONObject().put("revision", call(state, "revision"))
                .put("current", (Boolean) call(current, "c") ? track(call(current, "b")) : JSONObject.NULL)
                .put("next", tracks(call(state, "nextTracks"))).put("previous", tracks(call(state, "prevTracks")));
    }

    /** Explicit allowlist prevents HTTP, stream loading, or UI launch work on Node's thread. */
    public Object executeSync(String operation, JSONObject args) throws Exception {
        switch (operation) {
            case "player.state": case "player.play": case "player.pause": case "player.togglePlay":
            case "player.skipNext": case "player.skipPrevious": case "player.seek":
            case "player.shuffle": case "player.repeat": case "player.toggleShuffle": case "player.cycleRepeat":
            case "queue.get": case "queue.add": case "queue.remove": case "queue.move": case "queue.clear":
            case "library.contains": case "library.save": case "library.remove":
            case "playlists.create": case "playlists.delete": case "playlists.move":
            case "playlists.addTracks": case "playlists.removeTracks": case "playlists.moveTracks":
            case "connect.devices": case "connect.current":
            case "clipboard.read": case "clipboard.write": case "clipboard.clear":
                return execute(operation, args);
            default: throw new IllegalArgumentException("Operation requires the asynchronous API: " + operation);
        }
    }

    public Object execute(String operation, JSONObject args) throws Exception {
        switch (operation) {
            case "side.open": case "menu.open": case "menu.openNowPlaying":
                return NativeMenuLauncher.execute(loader, operation, args);
            case "player.state": {
                synchronized (this) {
                    if (lastState == null && Utils.playerState != null) observeState(Utils.playerState);
                    if (lastState == null) throw new IllegalStateException("Spotify player state is not ready");
                    return new JSONObject(lastState.toString()).put("positionMs", Utils.getCurrentPlaybackPosition());
                }
            }
            case "track.get": return metadata(args.getString("uri"));
            case "album.get": { SpotifyMetadataModels.Album value = getAlbum(args.getString("uri")); return value == null ? JSONObject.NULL : value.toJson(); }
            case "artist.get": { SpotifyMetadataModels.Artist value = getArtist(args.getString("uri")); return value == null ? JSONObject.NULL : value.toJson(); }
            case "playlists.getMetadata": { SpotifyMetadataModels.Playlist value = getPlaylistMetadata(args.getString("uri")); return value == null ? JSONObject.NULL : value.toJson(); }
            case "player.play": case "player.pause": case "player.togglePlay":
            case "player.skipNext": case "player.skipPrevious": case "player.seek": {
                String command = operation.substring("player.".length());
                Object single = command.equals("seek") ? PlayerHook.dispatchCommand(command, args.getLong("positionMs")) : PlayerHook.dispatchCommand(command);
                await(single);
                return JSONObject.NULL;
            }
            case "search": {
                String query = args.getString("query").trim();
                if (query.isEmpty() || query.length() > 1000) throw new IllegalArgumentException("Search query must contain 1 to 1000 characters");
                int limit = args.optInt("limit", 20);
                if (limit < 1 || limit > 100) throw new IllegalArgumentException("Search limit must be between 1 and 100");
                Object maker = ready(com.lenerd.spotifyplus.module.hooks.ServicesHook.retrofit);
                Object service = call(maker, "createWebgateService", type("p.xfy0"), "android-search-onlineproto");
                Map<String, String> params = new HashMap<>();
                params.put("request_id", UUID.randomUUID().toString());
                params.put("timestamp", String.valueOf(System.currentTimeMillis()));
                params.put("query", query);
                // Match Spotify's authenticated search request, including its request metadata.
                params.put("entity_types", "track,artist,album,playlist");
                params.put("limit", String.valueOf(limit));
                params.put("locale", args.optString("locale", context.getResources().getConfiguration().getLocales().get(0).toLanguageTag()));
                // Main search returns names as well as URIs and propagates HTTP failures.
                // The native endpoint's snippets can be empty even for matching entities.
                Object body = await(call(service, "a", params, Collections.emptyMap()));
                if (body == null) throw new IllegalStateException("Spotify search returned no response body");
                JSONArray items = new JSONArray();
                for (Object entity : (Iterable<?>) call(body, "q")) {
                    String uri = (String) call(entity, "getUri");
                    if (uri.isEmpty()) continue;
                    items.put(new JSONObject().put("uri", uri).put("text", call(entity, "getName")));
                    if (items.length() == limit) break;
                }
                return new JSONObject().put("query", query).put("items", items);
            }
            case "connect.devices": case "connect.current": {
                observeConnect();
                JSONArray snapshot = devices;
                if (snapshot == null) throw new IllegalStateException("Spotify Connect is not ready");
                if (operation.equals("connect.devices")) return snapshot;
                for (int i = 0; i < snapshot.length(); i++) if (snapshot.getJSONObject(i).getBoolean("isActive")) return snapshot.getJSONObject(i);
                return JSONObject.NULL;
            }
            case "connect.transfer": {
                String id = args.getString("deviceId");
                if (id.isBlank()) throw new IllegalArgumentException("Device ID cannot be empty");
                boolean local = id.equals("local_device");
                Object builder = call(type(CONNECT + (local ? "ConnectMessages$PullRequest" : "ConnectMessages$TransferRequest")), local ? "o" : "p");
                if (!local) call(builder, "m", id);
                Object response = rpc("spotify.connect.esperanto.proto.ConnectService", local ? "Pull" : "Transfer", call(builder, "build"), CONNECT + "CommonMessages$StatusResponse", "o");
                if (!call(response, "n").toString().equals("OK")) throw new IllegalStateException("Spotify Connect transfer failed: " + call(response, "n"));
                return JSONObject.NULL;
            }
            case "playlists.get": return playlist(args);
            case "playlists.create": case "playlists.delete": case "playlists.move":
            case "playlists.addTracks": case "playlists.removeTracks": case "playlists.moveTracks":
                return modifyPlaylist(operation, args);
            case "user.get": return user();
            case "library.save": case "library.remove": {
                ArrayList<String> uris = strings(args.getJSONArray("uris"));
                ArrayList<String> collection = new ArrayList<>();
                for (String uri : uris) {
                    if (uri.startsWith("spotify:playlist:")) {
                        modifyPlaylist(operation.equals("library.save") ? "playlists.follow" : "playlists.delete", new JSONObject().put("uri", uri));
                    } else collection.add(uri);
                }
                if (!collection.isEmpty()) {
                    Object builder = call(type(COLLECTION + "CollectionAddRemoveItemsRequest"), "q");
                    call(builder, "q", collection);
                    Object response = rpc("spotify.collection_esperanto.proto.CollectionService",
                            operation.equals("library.save") ? "Add" : "Remove", call(builder, "build"),
                            COLLECTION + "CollectionAddRemoveItemsResponse", "o");
                    Object status = call(response, "n");
                    status((Integer) call(status, "o"), (String) call(status, "q"));
                }
                return JSONObject.NULL;
            }
            case "library.contains": {
                ArrayList<String> uris = strings(args.getJSONArray("uris"));
                Object builder = call(type(LIBRARY + "YourLibraryContainsRequest"), "q");
                call(builder, "m", uris);
                Object response = rpc("spotify.your_library_esperanto.proto.YourLibraryService", "Contains", call(builder, "build"),
                        LIBRARY + "YourLibraryContainsResponse", "q");
                String error = (String) call(response, "p");
                if (!error.isEmpty()) throw new IllegalStateException(error);
                Map<String, Boolean> membership = new HashMap<>();
                for (Object entity : (Iterable<?>) call(response, "o")) membership.put((String) call(entity, "getUri"), (Boolean) call(entity, "o"));
                JSONArray result = new JSONArray();
                for (String uri : uris) {
                    if (!membership.containsKey(uri)) throw new IllegalStateException("Spotify omitted library membership for " + uri);
                    result.put(membership.get(uri));
                }
                return result;
            }
            case "library.list": return library(args);
            case "clipboard.read": case "clipboard.write": case "clipboard.clear":
                return clipboard(operation, args);
            case "queue.get": return queueJson(queueState());
            case "queue.add": {
                for (String uri : strings(args.getJSONArray("uris"))) {
                    Object track = call(type(MODEL + "ContextTrack"), "create", uri);
                    await(call(queue(), "a", track));
                }
                return JSONObject.NULL;
            }
            case "queue.remove": case "queue.move": case "queue.clear": {
                Object state = queueState();
                String revision = (String) call(state, "revision");
                if (!revision.equals(args.getString("revision"))) throw new IllegalStateException("Queue changed; refresh it before editing");
                List<Object> next = new ArrayList<>((List<?>) call(state, "nextTracks"));
                if (operation.equals("queue.clear")) next.clear();
                else {
                    int from = args.getInt("index");
                    if (from < 0 || from >= next.size()) throw new IllegalArgumentException("Queue index out of bounds");
                    Object entry = next.remove(from);
                    if (operation.equals("queue.move")) {
                        int to = args.getInt("toIndex");
                        if (to < 0 || to > next.size()) throw new IllegalArgumentException("Queue destination out of bounds");
                        next.add(to, entry);
                    }
                }
                Object command = call(type(MODEL + "command.SetQueueCommand"), "create", revision, next, call(state, "prevTracks"));
                await(call(queue(), "b", command));
                return JSONObject.NULL;
            }
            case "player.shuffle": case "player.repeat": case "player.toggleShuffle": case "player.cycleRepeat": {
                Object core = ready(PlayerHook.core);
                Object service = create("p.hhw", field(core, "a"), field(core, "b"));
                if (operation.equals("player.shuffle") || operation.equals("player.toggleShuffle")) {
                    boolean enabled = operation.equals("player.shuffle") ? args.getBoolean("enabled")
                            : !(Boolean) call(call(ready(Utils.playerState), "options"), "shufflingContext");
                    await(call(service, "a", enabled));
                }
                else {
                    String mode;
                    if (operation.equals("player.cycleRepeat")) {
                        Object options = call(ready(Utils.playerState), "options");
                        mode = (Boolean) call(options, "repeatingTrack") ? "off"
                                : (Boolean) call(options, "repeatingContext") ? "track" : "context";
                    } else mode = args.getString("mode");
                    if (!Set.of("off", "context", "track").contains(mode)) throw new IllegalArgumentException("Invalid repeat mode");
                    await(call(service, "c", !mode.equals("off"), mode.equals("track")));
                }
                return JSONObject.NULL;
            }
            case "player.playContext": {
                long index = args.getLong("index");
                if (index < 0) throw new IllegalArgumentException("Context index must be non-negative");
                Object skip = call(type(MODEL + "command.options.SkipToTrack"), "fromIndices", index, 0L);
                Object options = call(type(MODEL + "command.options.PreparePlayOptions"), "builder");
                call(options, "skipTo", skip);
                Object context = call(type(MODEL + "Context"), "fromUri", args.getString("uri"));
                Object origin = call(type(MODEL + "PlayOrigin"), "create", "spotifyplus");
                Object builder = call(type(MODEL + "command.PlayCommand"), "builder", context, origin);
                call(builder, "options", call(options, "build"));
                await(call(ready(PlayerHook.contextPlayer), "a", call(builder, "build")));
                return JSONObject.NULL;
            }
            default: throw new IllegalArgumentException("Unknown Spotify operation: " + operation);
        }
    }

    private Object playlist(JSONObject args) throws Exception {
        int offset = args.optInt("offset", 0), limit = args.optInt("limit", 50);
        if (offset < 0 || limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid pagination");
        Object range = call(type(PLAYLIST + "PlaylistRange"), "p");
        call(range, "q", offset);
        call(range, "m", limit);
        Object query = call(type(PLAYLIST + "PlaylistQuery"), "I");
        call(query, "A", call(range, "build"));
        Object builder = call(type(PLAYLIST + "PlaylistGetRequest"), "q");
        call(builder, "t", args.getString("uri"));
        call(builder, "s", call(query, "build"));
        call(builder, "m", playlistReadPolicy(loader));
        Object response = rpc("spotify.playlist_esperanto.proto.PlaylistDataService", "Get", call(builder, "build"), PLAYLIST + "PlaylistGetResponse", "s");
        checkPlaylistStatus(call(response, "r"));
        Object data = call(response, "o");
        if ((Boolean) call(data, "y")) throw new IllegalStateException("Playlist is still loading; retry the read");
        Object metadata = call(call(data, "C"), "q");
        JSONArray items = new JSONArray();
        for (Object item : (Iterable<?>) call(data, "w")) {
            String uri = (String) call(item, "getUri");
            Object track = call(item, uri.startsWith("spotify:episode:") ? "s" : "y");
            items.put(new JSONObject().put("uri", uri).put("rowId", call(item, "w"))
                    .put("name", call(track, "getName")).put("addedAt", call(item, "o")));
        }
        Object pictures = call(metadata, "z");
        String imageUri = "";
        for (String size : new String[]{"getXlargeLink", "getLargeLink", "getStandardLink", "getSmallLink"}) {
            String link = (String) call(pictures, size);
            if (!link.isEmpty()) { imageUri = link; break; }
        }
        return new JSONObject().put("uri", args.getString("uri")).put("name", call(metadata, "getName"))
                .put("imageUri", imageUri)
                .put("description", call(metadata, "getDescription")).put("ownedBySelf", call(metadata, "w"))
                .put("items", items).put("offset", offset).put("limit", limit).put("total", call(data, "E"));
    }
    /** Get returns undecorated rows unless every field we read is explicitly requested. */
    private Object playlistReadPolicy(ClassLoader loader) throws Exception {
        String policies = "com.spotify.playlist.policy.proto.";
        String common = "com.spotify.cosmos.util.policy.proto.";
        Object playlist = call(loader.loadClass(policies + "PlaylistDecorationPolicy"), "g0");
        call(playlist, "R"); // Name.
        call(playlist, "C"); // Description.
        call(playlist, "Z"); // Playlist artwork, including custom covers and generated mosaics.
        call(playlist, "W"); // Owned by self.
        call(playlist, "h0"); // Unranged length, used for pagination.
        call(playlist, "O"); // Loaded, including loadingContents in the response.

        Object item = call(loader.loadClass(policies + "PlaylistItemDecorationPolicy"), "G");
        call(item, "C", true); // URI (not included by default).
        call(item, "A"); // Row ID: preserves duplicate occurrences for edits.
        call(item, "r"); // Added timestamp.

        Object trackMetadata = call(loader.loadClass(common + "TrackDecorationPolicy"), "newBuilder");
        call(trackMetadata, "setName", true);
        Object track = call(loader.loadClass(policies + "PlaylistTrackDecorationPolicy"), "E");
        call(track, "E", call(trackMetadata, "build"));

        Object episodeMetadata = call(loader.loadClass(common + "EpisodeDecorationPolicy"), "newBuilder");
        call(episodeMetadata, "setName", true);
        Object episode = call(loader.loadClass(policies + "PlaylistEpisodeDecorationPolicy"), "C");
        call(episode, "t", call(episodeMetadata, "build"));

        Object policy = call(loader.loadClass(policies + "PlaylistRequestDecorationPolicy"), "v");
        call(policy, "w", call(playlist, "build"));
        call(policy, "u", call(item, "build"));
        call(policy, "y", call(track, "build"));
        call(policy, "s", call(episode, "build"));
        return call(policy, "build");
    }
    private void checkPlaylistStatus(Object value) throws Exception {
        int code = (Integer) call(value, "getStatusCode");
        if (code < 200 || code >= 300) throw new IllegalStateException("Spotify " + code + ": " + call(value, "p"));
    }
    private Object modifyPlaylist(String operation, JSONObject args) throws Exception {
        Object modification = call(type("com.spotify.playlist.proto.ModificationRequest"), "B");
        boolean root = Set.of("playlists.create", "playlists.delete", "playlists.move", "playlists.follow").contains(operation);
        switch (operation) {
            case "playlists.create":
                String name = args.getString("name").trim();
                if (name.isEmpty()) throw new IllegalArgumentException("Playlist name cannot be empty");
                call(modification, "B", "create"); call(modification, "C", true);
                call(modification, "A", name); call(modification, "w", "start");
                Object creation = call(type("com.spotify.playlist.proto.CreationInfo"), "p");
                call(creation, "m", type("p.t6l").getField("ORIGIN_TYPE_FRESH").get(null));
                call(modification, "y", call(creation, "build"));
                break;
            case "playlists.follow":
                call(modification, "B", "add"); call(modification, "q", List.of(args.getString("uri")));
                call(modification, "t", "end"); break;
            case "playlists.delete":
                call(modification, "B", "remove"); call(modification, "m", List.of(args.getString("uri")));
                call(modification, "x", false); break;
            case "playlists.move":
                call(modification, "B", "move"); call(modification, "m", List.of(args.getString("uri")));
                call(modification, "w", args.optString("before", "start")); break;
            case "playlists.addTracks":
                call(modification, "B", "add"); call(modification, "q", strings(args.getJSONArray("uris")));
                call(modification, "t", "end"); break;
            case "playlists.removeTracks":
                call(modification, "B", "remove"); call(modification, "m", strings(args.getJSONArray("rowIds"))); break;
            case "playlists.moveTracks":
                call(modification, "B", "move"); call(modification, "m", strings(args.getJSONArray("rowIds")));
                if (args.has("before")) call(modification, "w", args.getString("before"));
                else call(modification, "t", "end");
                break;
        }
        Object request = call(type(PLAYLIST + (root ? "RootlistModificationRequest" : "PlaylistModificationRequest")), "p");
        if (root) {
            call(request, "q", call(modification, "build"));
            call(request, "m", "");
        } else {
            call(request, "m", call(modification, "build"));
            call(request, "q", args.getString("uri"));
        }
        Object response = rpc("spotify.playlist_esperanto.proto." + (root ? "RootlistModificationService" : "PlaylistService"),
                "Modify", call(request, "build"), PLAYLIST + (root ? "RootlistModificationResponse" : "PlaylistModificationResponse"), "p");
        checkPlaylistStatus(call(response, "o"));
        if (operation.equals("playlists.create")) {
            String uri = (String) call(call(response, "n"), "getUri");
            if (uri.isEmpty()) throw new IllegalStateException("Spotify returned no URI for the created playlist");
            return new JSONObject().put("uri", uri).put("name", args.getString("name"));
        }
        return JSONObject.NULL;
    }

    /** Fetch a metadata entity using the same authenticated client as track.get. */
    private JSONObject getEntityJson(String uri, String kind) throws Exception {
        if (!Set.of("track", "album", "artist", "playlist").contains(kind)) throw new IllegalArgumentException("Invalid entity type");
        String prefix = "spotify:" + kind + ":";
        if (uri == null || !uri.matches("spotify:" + kind + ":[A-Za-z0-9]{22}"))
            throw new IllegalArgumentException("Expected a Spotify " + kind + " URI");
        String token = Utils.token;
        if (token == null || token.isBlank()) throw new IllegalStateException("Spotify session is not ready");
        okhttp3.HttpUrl.Builder url = okhttp3.HttpUrl.get("https://spclient.wg.spotify.com/").newBuilder();
        if (kind.equals("playlist")) url.addPathSegments("playlist/v2/playlist").addPathSegment(uri.substring(prefix.length()));
        else url.addPathSegments("metadata/4").addPathSegment(kind).addPathSegment(spotifyHex(uri.substring(prefix.length())));
        url.addQueryParameter("market", "from_token");
        okhttp3.Request.Builder request = new okhttp3.Request.Builder().url(url.build())
                .header("Authorization", "Bearer " + token).header("Accept", "application/json");
        if (Utils.clientToken != null) request.header("Client-Token", Utils.clientToken);
        try (okhttp3.Response response = http.newCall(request.build()).execute()) {
            if (response.code() == 404) return null;
            if (!response.isSuccessful() || response.body() == null)
                throw new IllegalStateException("Spotify " + kind + " request failed: " + response.code());
            return new JSONObject(response.body().string());
        }
    }

    private static String spotifyHex(String id) {
        String alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        java.math.BigInteger gid = java.math.BigInteger.ZERO;
        for (char character : id.toCharArray()) {
            int digit = alphabet.indexOf(character);
            if (digit < 0) throw new IllegalArgumentException("Invalid Spotify ID");
            gid = gid.multiply(java.math.BigInteger.valueOf(62)).add(java.math.BigInteger.valueOf(digit));
        }
        if (gid.bitLength() > 128) throw new IllegalArgumentException("Spotify ID exceeds 128 bits");
        return String.format(Locale.ROOT, "%032x", gid);
    }

    public SpotifyMetadataModels.Album getAlbum(String uri) throws Exception {
        JSONObject data = getEntityJson(uri, "album");
        return data == null ? null : new SpotifyMetadataModels.Album(data, uri);
    }

    public SpotifyMetadataModels.Artist getArtist(String uri) throws Exception {
        JSONObject data = getEntityJson(uri, "artist");
        return data == null ? null : new SpotifyMetadataModels.Artist(data, uri);
    }

    public SpotifyMetadataModels.Playlist getPlaylistMetadata(String uri) throws Exception {
        JSONObject data = getEntityJson(uri, "playlist");
        return data == null ? null : new SpotifyMetadataModels.Playlist(data, uri);
    }

    private Object metadata(String uri) throws Exception {
        JSONObject data = getEntityJson(uri, "track");
        if (data == null) return JSONObject.NULL;
        JSONArray artists = new JSONArray();
        JSONArray sourceArtists = data.optJSONArray("artist");
        if (sourceArtists != null) for (int i = 0; i < sourceArtists.length(); i++) artists.put(sourceArtists.getJSONObject(i).optString("name"));
        String artist = artists.optString(0, "");
        JSONObject sourceAlbum = data.optJSONObject("album");
        JSONObject album = new JSONObject().put("title", sourceAlbum == null ? "" : sourceAlbum.optString("name"))
                .put("artist", artist).put("image", "");
        JSONObject covers = sourceAlbum == null ? null : sourceAlbum.optJSONObject("cover_group");
        JSONArray images = covers == null ? null : covers.optJSONArray("image");
        if (images != null && images.length() > 0) {
            JSONObject image = images.getJSONObject(images.length() - 1);
            for (int i = 0; i < images.length(); i++) if ("LARGE".equals(images.getJSONObject(i).optString("size"))) image = images.getJSONObject(i);
            album.put("image", "https://i.scdn.co/image/" + image.getString("file_id"));
        }
        return new JSONObject().put("uri", data.optString("canonical_uri", uri)).put("title", data.optString("name"))
                .put("artist", artist).put("artists", artists).put("album", album)
                .put("durationMs", data.optLong("duration")).put("trackNumber", data.optInt("number"))
                .put("explicit", data.optBoolean("explicit"));
    }

    private Object user() throws Exception {
        String username = PlayerHook.username;
        String token = Utils.token;
        if (username == null || username.isBlank() || token == null || token.isBlank()) throw new IllegalStateException("Spotify session is not ready");
        okhttp3.HttpUrl url = okhttp3.HttpUrl.get("https://spclient.wg.spotify.com/identity/v3/user/username/")
                .newBuilder().addPathSegment(username).build();
        okhttp3.Request.Builder request = new okhttp3.Request.Builder().url(url).header("Authorization", "Bearer " + token)
                .header("Accept", "application/x-protobuf").header("App-Platform", "Android");
        if (Utils.clientToken != null) request.header("Client-Token", Utils.clientToken);
        try (okhttp3.Response response = http.newCall(request.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IllegalStateException("Spotify profile request failed: " + response.code());
            Object profile = call(call(type("com.spotify.identity.proto.v3.Identity$UserProfile"), "parser"), "c", response.body().bytes());
            JSONArray images = new JSONArray();
            for (Object image : (Iterable<?>) call(profile, "r")) images.put(new JSONObject().put("url", call(image, "getUrl"))
                    .put("width", call(image, "o")).put("height", call(image, "n")));
            return new JSONObject().put("username", call(call(profile, "x"), "getValue"))
                    .put("displayName", call(call(profile, "u"), "getValue"))
                    .put("uri", "spotify:user:" + username).put("images", images);
        }
    }

    private Object library(JSONObject args) throws Exception {
        int offset = args.optInt("offset", 0), limit = args.optInt("limit", 50);
        if (offset < 0 || limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid pagination");
        Object header = call(type(LIBRARY + "YourLibraryRequestHeader"), "G");
        call(header, "F", offset);
        call(header, "C", limit);
        call(header, "K"); // Ask for the count so filtered collections can paginate past page one.
        call(header, "E", false); // Include pinned items in the ordinary result list.
        String kind = args.optString("type", "all");
        if (!kind.equals("all")) {
            if (!Set.of("album", "artist", "playlist").contains(kind)) throw new IllegalArgumentException("Invalid library type");
            Object filters = call(type("spotify.your_library.proto.YourLibraryConfig$YourLibraryFilters"), "r");
            // R8 obfuscates enum fields; their constant names remain available through Enum.name().
            Object selected = Arrays.stream(type("p.dsd1").getEnumConstants())
                    .filter(value -> ((Enum<?>) value).name().equals(kind.toUpperCase(Locale.ROOT)))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Library filter unavailable: " + kind));
            call(filters, "q", selected);
            call(header, "u", call(filters, "build"));
        }
        Object builder = call(type(LIBRARY + "YourLibraryRequest"), "q");
        call(builder, "q", call(header, "build"));
        Object observable = call(field(ready(PlayerHook.core), "a"), "callStream",
                "spotify.your_library_esperanto.proto.YourLibraryService", "StreamAll", call(builder, "build"));
        Class<?> predicate = type("io.reactivex.rxjava3.functions.Predicate");
        Object loaded = Proxy.newProxyInstance(loader, new Class<?>[]{predicate}, (proxy, method, values) -> {
            if (method.getName().equals("test")) {
                Object response = call(type(LIBRARY + "YourLibraryResponse"), "s", values[0]);
                status((Integer) call(response, "getStatusCode"), (String) call(response, "p"));
                return !(Boolean) call(call(response, "q"), "q");
            }
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == values[0];
            return "SpotifyPlusLibraryLoaded";
        });
        byte[] bytes = (byte[]) await(call(call(observable, "filter", loaded), "firstOrError"));
        Object response = call(type(LIBRARY + "YourLibraryResponse"), "s", bytes);
        JSONArray items = new JSONArray();
        for (Object entity : (Iterable<?>) call(response, "n")) {
            Object info = call(entity, "e");
            String uri = (String) call(info, "getUri");
            items.put(new JSONObject().put("uri", uri).put("name", call(info, "getName"))
                    .put("imageUri", call(info, "q")).put("pinned", call(info, "u")));
        }
        return new JSONObject().put("items", items).put("offset", offset).put("limit", limit)
                .put("total", call(call(response, "q"), "t"));
    }

    private Object clipboard(String operation, JSONObject args) throws Exception {
        // ClipboardManager is a Binder service and is safe to call from Node's thread.
            ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (operation.equals("clipboard.write")) { manager.setPrimaryClip(ClipData.newPlainText("SpotifyPlus", args.getString("text"))); return JSONObject.NULL; }
            if (operation.equals("clipboard.clear")) { manager.clearPrimaryClip(); return JSONObject.NULL; }
            ClipData clip = manager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return JSONObject.NULL;
            CharSequence text = clip.getItemAt(0).coerceToText(context);
            return text == null ? JSONObject.NULL : text.toString();
    }
}

