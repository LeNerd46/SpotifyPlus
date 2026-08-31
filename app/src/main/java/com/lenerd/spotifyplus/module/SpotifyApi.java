package com.lenerd.spotifyplus.module;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lenerd.spotifyplus.manager.bridge.BridgeClient;
import com.lenerd.spotifyplus.module.scripting.ScriptManager;
import com.lenerd.spotifyplus.module.scripting.SpotifyNativeBridge;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class SpotifyApi extends SpotifyHook {
    @Override
    protected void hookSetup() throws NoSuchMethodException, ClassNotFoundException, NoSuchFieldException {
        SpotifyNativeBridge.registerHandler("internal", this);
    }

    @Override
    protected void beforeHook(SpotifyCallback callback) {
    }

    @Override
    protected void afterHook(SpotifyCallback callback) {
    }

    @Override
    public Object handle(String command, Object[] args) {
        if (command.equals("getTrack")) {
            try {
                String uri = (String) args[0];

                getTrack(uri, new SpotifyResponseCallback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        Log.d("SpotifyPlus", response.toString());
//                        ScriptManager.send(id, "response", "internal.getTrack", response);
                    }

                    @Override
                    public void onError(Exception e) {
                        logError(e);
//                        ScriptManager.send(id, "response", "internal.getTrack", new JSONObject());
                    }
                });
            } catch (Exception e) {
                logError(e);
            }
        }

        return null;
    }

    public static void getTrack(String uri, SpotifyResponseCallback callback) {
        try {
            String id = uri.split(":")[2];
            String gid = spotifyToGid(id);

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url("https://spclient.wg.spotify.com/metadata/4/track/" + gid + "?market=from_token").header("Content-Type", "application/json").header("Authorization", "Bearer " + Utils.token).header("Client-Token", Utils.clientToken).header("Accept", "application/json").header("Accept-Language", "en").header("Spotify-App-Language", Utils.spotifyVersion).build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            callback.onError(new Exception("Response failed: " + response.code()));
                            return;
                        }

                        String content = response.body().string();
                        if (content.isEmpty()) {
                            callback.onError(new Exception("Empty response"));
                            return;
                        }

                        var json = new JsonParser().parseString(content).getAsJsonObject();
                        JSONObject responseJson = new JSONObject();
                        responseJson.put("title", json.get("name").getAsString());
                        responseJson.put("trackNumber", json.get("number").getAsString());
                        responseJson.put("duration", json.get("duration").getAsLong());
                        responseJson.put("explicit", json.has("explicit") && json.get("explicit").getAsBoolean());
                        responseJson.put("uri", json.get("canonical_uri").getAsString());

                        var externalIds = json.get("external_id").getAsJsonArray().asList();
                        externalIds.stream().map(JsonElement::getAsJsonObject).filter(obj -> {
                            var type = obj.get("type");
                            return type != null && type.getAsString().equals("isrc");
                        }).findFirst().ifPresent(obj -> {
                            try {
                                responseJson.put("isrc", obj.get("id").getAsString());
                            } catch (JSONException ignored) {
                            }
                        });

                        JSONObject albumJson = new JSONObject();
                        var album = json.get("album").getAsJsonObject();
                        var release = album.get("date").getAsJsonObject();
                        var images = album.get("cover_group").getAsJsonObject().get("image").getAsJsonArray().asList();

                        albumJson.put("title", album.get("name").getAsString());
                        albumJson.put("artist", album.get("label").getAsString());
                        albumJson.put("release", release.get("year").getAsString() + "-" + release.get("month").getAsString() + "-" + release.get("day").getAsString());

                        images.stream().map(JsonElement::getAsJsonObject).filter(obj -> {
                            var size = obj.get("size");
                            return size != null && size.getAsString().equals("LARGE");
                        }).findFirst().ifPresent(obj -> {
                            try {
                                albumJson.put("image", "https://i.scdn.co/image/" + obj.get("file_id").getAsString());
                            } catch (JSONException ignored) {
                            }
                        });

                        responseJson.put("album", albumJson);

                        var artists = json.get("artist").getAsJsonArray().asList();
                        List<String> responseArtists = new ArrayList<>();
                        artists.forEach(obj -> {
                            responseArtists.add(obj.getAsJsonObject().get("name").getAsString());
                        });

                        responseJson.put("artist", responseArtists.get(0));
                        responseJson.put("artists", responseArtists.toArray(new String[0]));

                        callback.onSuccess(responseJson);
                    } catch (Exception e) {
                        Log.e("SpotifyPlus", e.getMessage(), e);
                    }
                }

                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    Log.e("SpotifyPlus", "Failed to get track information", e);
                    callback.onError(e);
                }
            });
        } catch (Exception e) {
            Log.e("SpotifyPlus", "Failed to get track information", e);
        }
    }

    private static String spotifyToGid(String id) {
        String alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        java.math.BigInteger value = java.math.BigInteger.ZERO;
        java.math.BigInteger base = java.math.BigInteger.valueOf(62);

        for (int i = 0; i < id.length(); i++) {
            int digit = alphabet.indexOf(id.charAt(i));
            if (digit < 0) throw new IllegalArgumentException("Invalid Spotify ID character: " + id.charAt(i));
            value = value.multiply(base).add(java.math.BigInteger.valueOf(digit));
        }

        return String.format("%032x", value);
    }

    public interface SpotifyResponseCallback {
        void onSuccess(JSONObject response);

        void onError(Exception e);
    }

}
