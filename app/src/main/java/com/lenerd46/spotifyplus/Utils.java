package com.lenerd46.spotifyplus;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class Utils {
    private static final String IDENTITY_BASE_URL = "https://spclient.wg.spotify.com/identity/v3/user/username/";
    private static final OkHttpClient HTTP = new OkHttpClient();
    private static final Object USER_LOCK = new Object();

    private static volatile SpotifyUser cachedUser;
    private static volatile String cachedForToken;
    private static volatile String cachedForUsername;
    private static CompletableFuture<SpotifyUser> pendingUser;

    private Utils() {
    }

    public static CompletableFuture<SpotifyUser> getSpotifyUser() {
        return getSpotifyUser(false);
    }

    public static CompletableFuture<SpotifyUser> getUser() {
        return getSpotifyUser();
    }
    public static CompletableFuture<SpotifyUser> refreshSpotifyUser() {
        return getSpotifyUser(true);
    }

    public static SpotifyUser getCachedSpotifyUser() {
        String token = References.accessToken;
        String username = References.spotifyUsername;
        return token != null && token.equals(cachedForToken) && username != null && username.equals(cachedForUsername) ? cachedUser : null;
    }

    public static void clearSpotifyUserCache() {
        synchronized (USER_LOCK) {
            cachedUser = null;
            cachedForToken = null;
            cachedForUsername = null;
            pendingUser = null;
        }
    }

    private static CompletableFuture<SpotifyUser> getSpotifyUser(boolean forceRefresh) {
        String token = References.accessToken;
        String username = References.spotifyUsername;

        if (token == null || token.isBlank()) {
            CompletableFuture<SpotifyUser> unavailable = new CompletableFuture<>();
            unavailable.completeExceptionally(new IllegalStateException("Spotify access token is not available yet; wait until Spotify has loaded its content"));

            return unavailable;
        }

        if (username == null || username.isBlank()) {
            CompletableFuture<SpotifyUser> unavailable = new CompletableFuture<>();
            unavailable.completeExceptionally(new IllegalStateException("Spotify username is not available yet; restart Spotify and wait until login finishes"));

            return unavailable;
        }

        synchronized (USER_LOCK) {
            boolean sameIdentity = token.equals(cachedForToken) && username.equals(cachedForUsername);
            if (!forceRefresh && sameIdentity && cachedUser != null) {
                return CompletableFuture.completedFuture(cachedUser);
            }

            if (sameIdentity && pendingUser != null && !pendingUser.isDone()) {
                return pendingUser;
            }

            CompletableFuture<SpotifyUser> result = new CompletableFuture<>();

            cachedForToken = token;
            cachedForUsername = username;
            pendingUser = result;

            requestUser(token, username, result);
            return result;
        }
    }

    private static void requestUser(String token, String username, CompletableFuture<SpotifyUser> result) {
        HttpUrl url = HttpUrl.get(IDENTITY_BASE_URL).newBuilder().addPathSegment(username).build();
        Request.Builder requestBuilder = new Request.Builder().url(url).header("Authorization", "Bearer " + token).header("Accept", "application/x-protobuf").header("App-Platform", "Android");

        String clientToken = References.clientToken;

        if (clientToken != null && !clientToken.isBlank()) {
            requestBuilder.header("Client-Token", clientToken);
        }

        Request request = requestBuilder.build();

        HTTP.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException error) {
                finishFailure(token, result, error);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    ResponseBody body = response.body();
                    byte[] protobuf = body == null ? new byte[0] : body.bytes();

                    if (!response.isSuccessful()) {
                        throw new IOException("Spotify identity profile failed with HTTP " + response.code() + " for username " + username);
                    }

                    ClassLoader classLoader = References.spotifyClassLoader;

                    if (classLoader == null) {
                        throw new IllegalStateException("Spotify class loader is not available");
                    }

                    SpotifyUser user = SpotifyUser.fromInternalProfile(protobuf, classLoader);

                    synchronized (USER_LOCK) {
                        if (token.equals(cachedForToken) && username.equals(cachedForUsername)) {
                            cachedUser = user;
                        }

                        if (pendingUser == result) pendingUser = null;
                    }

                    result.complete(user);
                } catch (Throwable error) {
                    finishFailure(token, result, error);
                }
            }
        });
    }

    private static void finishFailure(String token, CompletableFuture<SpotifyUser> result, Throwable error) {
        synchronized (USER_LOCK) {
            if (pendingUser == result) pendingUser = null;

            if (token.equals(cachedForToken) && cachedUser == null) {
                cachedForToken = null;
                cachedForUsername = null;
            }
        }
        
        result.completeExceptionally(error);
    }
}
