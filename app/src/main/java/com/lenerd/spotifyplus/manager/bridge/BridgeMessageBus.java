package com.lenerd.spotifyplus.manager.bridge;

import android.util.Log;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class BridgeMessageBus {
    private static final List<BridgeMessageListener> listeners = new CopyOnWriteArrayList<>();

    private BridgeMessageBus() { }

    public static void register(BridgeMessageListener listener) {
        listeners.add(listener);
    }

    public static void unregister(BridgeMessageListener listener) {
        listeners.remove(listener);
    }

    public static void dispatch(String id, String type, String name, JSONObject payload) {
        for(BridgeMessageListener listener : listeners) {
            try {
                listener.onMessage(id, type, name, payload);
            } catch(Throwable t) {
                Log.e("SpotifyPlus", "Bridge listener failed", t);
            }
        }
    }
}
