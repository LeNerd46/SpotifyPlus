package com.lenerd.spotifyplus.manager.scripting;

import org.json.JSONObject;

public interface NodePacketSink {
    void sendToNode(JSONObject packet);
}
