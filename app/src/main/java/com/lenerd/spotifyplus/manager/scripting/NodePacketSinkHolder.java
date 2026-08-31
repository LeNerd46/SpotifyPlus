package com.lenerd.spotifyplus.manager.scripting;

import androidx.annotation.Nullable;

public class NodePacketSinkHolder {
    private static volatile NodePacketSink sink;

    private NodePacketSinkHolder() { }

    public static void set(@Nullable NodePacketSink value) {
        sink = value;
    }

    @Nullable
    public static NodePacketSink get() {
        return sink;
    }
}
