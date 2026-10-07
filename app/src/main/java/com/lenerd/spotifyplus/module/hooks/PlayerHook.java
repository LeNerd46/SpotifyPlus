package com.lenerd.spotifyplus.module.hooks;

import com.lenerd.spotifyplus.module.SpotifyCallback;
import com.lenerd.spotifyplus.module.SpotifyHook;
import com.lenerd.spotifyplus.module.Utils;
import com.lenerd.spotifyplus.module.scripting.SpotifyNativeBridge;
import com.lenerd.spotifyplus.module.scripting.SpotifyServices;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Spotify 9.1.82.2160: core dispatcher, independent of lyrics and now-playing UI. */
public class PlayerHook extends SpotifyHook {
    private volatile Object controller;
    private Method dispatch;
    public static volatile Object core;
    public static volatile Object contextPlayer;
    public static volatile String username;

    @Override protected void hookSetup() throws NoSuchMethodException, ClassNotFoundException {
        Class<?> type = classLoader.loadClass("p.ghw");
        dispatch = type.getDeclaredMethod("a", classLoader.loadClass("p.nno0"));
        hook(dispatch);
        hook(type.getDeclaredConstructor(classLoader.loadClass("p.q1k"),
                classLoader.loadClass("p.jca0"), boolean.class));
        hook(classLoader.loadClass("p.tgw").getDeclaredConstructor(classLoader.loadClass("p.q1k"),
                classLoader.loadClass("p.jca0"), boolean.class, java.util.List.class));
        SpotifyNativeBridge.registerHandler("player", this);
        hook(classLoader.loadClass("com.spotify.player.model.AutoValue_PlayerState$Builder").getDeclaredMethod("build"));
        hook(classLoader.loadClass("com.spotify.connectivity.auth.CredentialsStorage$StoredCredentialsAndUsername")
                .getDeclaredConstructor(classLoader.loadClass("com.spotify.authentication.credentials.SerializableCredentials"), String.class, boolean.class));
    }

    @Override protected void beforeHook(SpotifyCallback callback) { }
    @Override protected void afterHook(SpotifyCallback callback) {
        if (callback.getThrowable() != null) return;
        if (callback.getMember().equals(dispatch)) {
            Object request = callback.getArgs()[0];
            if (!request.getClass().getName().equals("p.fno0")) return;
            try {
                long target = request.getClass().getField("a").getLong(request);
                long previous = Utils.getCurrentPlaybackPosition();
                Class<?> consumer = classLoader.loadClass("io.reactivex.rxjava3.functions.Consumer");
                Object observer = Proxy.newProxyInstance(classLoader, new Class<?>[]{consumer}, (proxy, method, args) -> {
                    if (method.getName().equals("accept")) {
                        Object result = args[0];
                        boolean failed = result != null && classLoader.loadClass("p.v8f").isInstance(result)
                                && (Boolean) classLoader.loadClass("p.v8f").getMethod("c").invoke(result);
                        if (!failed) SpotifyServices.onSeekAcknowledged(target, previous);
                    }
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("equals")) return proxy == args[0];
                    if (method.getName().equals("toString")) return "SpotifyPlusSeekObserver";
                    return null;
                });
                Object wrapped = classLoader.loadClass("io.reactivex.rxjava3.core.Single")
                        .getMethod("doOnSuccess", consumer).invoke(callback.getResult(), observer);
                callback.setResult(wrapped);
            } catch (Exception error) { logError(error); }
        } else if (callback.getMember().getName().equals("build")) {
            SpotifyServices.onPlayerState(callback.getResult());
            NowPlayingUITarget.refresh();
        }
        else capture(callback.getThisObject());
    }

    private void capture(Object value) {
        if (value == null) return;
        if (value.getClass().getName().equals("p.ghw")) { controller = value; core = value; SpotifyServices.onCoreReady(); }
        if (value.getClass().getName().equals("p.tgw")) contextPlayer = value;
        if (value.getClass().getName().equals("com.spotify.connectivity.auth.CredentialsStorage$StoredCredentialsAndUsername")) {
            try { username = (String) value.getClass().getMethod("getCanonicalUsername").invoke(value); }
            catch (Exception error) { logError(error); }
        }
    }

    public Object command(String command, Object... args) throws Exception {
        Object target = controller;
        if (target == null) throw new IllegalStateException("Spotify player is not ready");
        Object request;
        switch (command) {
            case "play": request = classLoader.loadClass("p.dno0").getConstructor(String.class, boolean.class).newInstance("spotifyplus", false); break;
            case "pause": request = classLoader.loadClass("p.ano0").getConstructor(String.class, boolean.class).newInstance("spotifyplus", false); break;
            case "togglePlay":
                if (Utils.playerState == null) throw new IllegalStateException("Spotify player state is not ready");
                boolean paused = (Boolean) classLoader.loadClass("com.spotify.player.model.PlayerState").getMethod("isPaused").invoke(Utils.playerState);
                return command(paused ? "play" : "pause");
            case "seek":
                long position = ((Number) args[0]).longValue();
                if (position < 0) throw new IllegalArgumentException("Position must be non-negative milliseconds");
                request = classLoader.loadClass("p.fno0").getConstructor(long.class).newInstance(position); break;
            case "skipNext": request = PlayerRequests.create(classLoader.loadClass("p.ino0")); break;
            case "skipPrevious": request = PlayerRequests.create(classLoader.loadClass("p.kno0")); break;
            default: throw new IllegalArgumentException("Unknown player command: " + command);
        }
        return dispatch.invoke(target, request);
    }

    @Override public Object handle(String command, Object[] args) {
        try {
            Object single = command(command, args);
            Class<?> consumer = classLoader.loadClass("io.reactivex.rxjava3.functions.Consumer");
            classLoader.loadClass("io.reactivex.rxjava3.core.Single")
                    .getMethod("subscribe", consumer, consumer).invoke(single, consumer(consumer, false), consumer(consumer, true));
            return null;
        } catch (Exception e) { throw new IllegalStateException("Player command failed: " + command, e); }
    }

    private Object consumer(Class<?> type, boolean error) {
        return Proxy.newProxyInstance(classLoader, new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("accept") && error) logError((Throwable) args[0]);
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            if (method.getName().equals("toString")) return "SpotifyPlusPlayerConsumer";
            return null;
        });
    }
    public static Object dispatchCommand(String command, Object... args) throws Exception {
        PlayerHook instance = getHook(PlayerHook.class);
        if (instance == null) throw new IllegalStateException("Spotify player is not ready");
        return instance.command(command, args);
    }
    public static void seekTo(long positionMs) {
        PlayerHook instance = getHook(PlayerHook.class);
        if (instance == null) throw new IllegalStateException("Spotify player is not ready");
        instance.handle("seek", new Object[]{positionMs});
    }
    public static void togglePlay() {
        PlayerHook instance = getHook(PlayerHook.class);
        if (instance != null) instance.handle("togglePlay", new Object[0]);
    }
}
