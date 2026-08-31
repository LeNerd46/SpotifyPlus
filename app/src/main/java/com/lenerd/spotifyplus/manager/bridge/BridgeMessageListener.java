package com.lenerd.spotifyplus.manager.bridge;

import org.json.JSONObject;

public interface BridgeMessageListener {
    void onMessage(String id, String type, String name, JSONObject payload);
}
