package com.lenerd.spotifyplus.manager.scripting;

import android.app.Activity;
import android.content.res.AssetManager;
import android.util.Log;
import com.lenerd.spotifyplus.manager.bridge.BridgeRouter;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ScriptManager implements NodePacketSink {
    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("node");
    }

    private static boolean nodeStarted = false;
    private final Activity activity;

    public ScriptManager(Activity activity) {
        this.activity = activity;
        nativeInit();
        NodePacketSinkHolder.set(this);
    }

    public synchronized void start() {
        if (nodeStarted) return;
        nodeStarted = true;

        File projectDir = new File(activity.getFilesDir(), "nodejs");

        try {
            getAssetFolder(activity.getAssets(), "nodejs", projectDir.getAbsolutePath());
            copyNodeAddon(projectDir);
        } catch (Exception e) {
            Log.e("SpotifyPlus", "Failed to load host JS script", e);
            return;
        }

        File hostFile = new File(projectDir, "host.js");
        File scripts = new File(projectDir, "scriptss");

        new Thread(() -> {
            int result = startNodeWithArguments(new String[]{"node", hostFile.getAbsolutePath(), scripts.getAbsolutePath()});
        }).start();
    }

    private void getAssetFolder(AssetManager assetManager, String assetPath, String outPath) throws IOException {
        String[] assets = assetManager.list(assetPath);
        if (assets == null) return;

        if (assets.length == 0) {
            try (InputStream in = assetManager.open(assetPath); FileOutputStream out = new FileOutputStream(outPath)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                out.flush();
            }
            return;
        }

        File outDir = new File(outPath);
        if (!outDir.exists() && !outDir.mkdirs()) throw new IOException("Failed to create directory: " + outPath);

        for (String asset : assets) {
            String childAssetPath = assetPath + "/" + asset;
            String childOutPath = outPath + "/" + asset;
            getAssetFolder(assetManager, childAssetPath, childOutPath);
        }
    }

    public void sendToNode(JSONObject packet) {
        nativeSendToNode(packet.toString());
    }

    public void onMessageFromNode(String json) {
        Log.i("SpotifyPlus", "Message from node: " + json);

        try {
            JSONObject packet = new JSONObject(json);

            String id = packet.optString("id", null);
            String type = packet.optString("type", "");
            String name = packet.optString("name", "");
            JSONObject payload = packet.optJSONObject("payload");

            BridgeRouter.send(id, type, name, payload);
        } catch (Exception e) {
            Log.e("SpotifyPlus", "Failed to parse message from node", e);
        }
    }

    public void testPing() {
        try {
            JSONObject packet = new JSONObject();
            packet.put("type", "event");
            packet.put("name", "ping");
            packet.put("payload", new JSONObject());
            nativeSendToNode(packet.toString());
        } catch (Exception e) {
            Log.e("SpotifyPlus", "Failed to send ping", e);
        }
    }

    private void copyNodeAddon(File projectDir) throws IOException {
        String abi = android.os.Build.SUPPORTED_ABIS[0];
        String assetPath = "nodejs/addons/" + abi + "/spotifyplus_bridge.node";
        File dest = new File(projectDir, "spotifyplus_bridge.node");

        try (InputStream in = activity.getAssets().open(assetPath); FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            out.flush();
        }

        dest.setReadable(true, false);
        dest.setExecutable(true, false);
    }

    public native int startNodeWithArguments(String[] arguments);

    private native void nativeInit();

    private native void nativeSendToNode(String json);
}