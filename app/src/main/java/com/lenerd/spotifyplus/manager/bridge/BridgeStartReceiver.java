package com.lenerd.spotifyplus.manager.bridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BridgeStartReceiver extends BroadcastReceiver {
    public static final String ACTION_START_BRIDGE =
            "com.lenerd.spotifyplus.action.START_BRIDGE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_START_BRIDGE.equals(intent.getAction())) return;
        Intent serviceIntent = new Intent(context, BridgeService.class);

        try {
            context.startForegroundService(serviceIntent);
        } catch (Throwable t) {
            Log.e("SpotifyPlus", "Failed to start bridge service", t);
        }
    }
}