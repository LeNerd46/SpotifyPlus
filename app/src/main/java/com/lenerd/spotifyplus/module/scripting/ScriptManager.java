package com.lenerd.spotifyplus.module.scripting;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.lenerd.spotifyplus.BuildConfig;
import com.lenerd.spotifyplus.manager.bridge.BridgeMessageBus;
import com.lenerd.spotifyplus.manager.bridge.BridgeMessageListener;
import com.lenerd.spotifyplus.manager.bridge.BridgeRouter;
import com.lenerd.spotifyplus.module.SpotifyHook;
import com.lenerd.spotifyplus.module.Utils;
import com.lenerd.spotifyplus.module.hooks.LocalExtensionHook;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ScriptManager implements BridgeMessageListener {
    private static boolean initialized = false;
    private static boolean nodeStarted = false;
    private final Activity activity;
    private static ScriptManager instance;

    public ScriptManager(Activity activity) {
        BridgeMessageBus.register(this);
        this.activity = activity;
        if (instance == null) {
            instance = this;
        }

        if (!initialized) {
            try {
                File outDir = new File(activity.getCodeCacheDir(), "spotifyplus-libs");
                if (!outDir.exists() && !outDir.mkdirs())
                    throw new IllegalStateException("Failed to create cache directory");

                extractAndLoad(Utils.MODULE_APK_PATH, outDir, "libnative-lib.so");
                extractAndLoad(Utils.MODULE_APK_PATH, outDir, "libnode.so");

//                try {
//                    new Thread(() -> {
//                        SpotifyNativeBridge bridge = new SpotifyNativeBridge();
//                        initializeNativeBridge(bridge);
//                    }).start();
//                } catch (Exception e) {
//                    Log.e("SpotifyPlus", "Failed to initialize Spotify Native Bridge", e);
//                }

//                nativeInit();

                initialized = true;
            } catch (Exception e) {
                Log.e("SpotifyPlus", "Failed to get library", e);
            }
        }
    }

    public synchronized void start() {
        if (nodeStarted) return;
        nodeStarted = true;

        File projectDir = new File(activity.getFilesDir(), "nodejs");

        try {
            AssetManager moduleAssets = Utils.getModuleAssetManager();
            if (moduleAssets == null) throw new IllegalStateException("Module assets not available");

            getAssetFolder(moduleAssets, "nodejs", projectDir.getAbsolutePath());
            copyNodeAddon(moduleAssets, projectDir);
        } catch (Exception e) {
            Log.e("SpotifyPlus", "Failed to load host JS script", e);
            return;
        }

        File hostFile = new File(projectDir, "host.js");
        File elevatedScripts = new File(projectDir, "elevated");
        File marketplaceScripts = new File(projectDir, "scriptss");
        File localScripts = LocalExtensionHook.getLocalExtensionCacheDir(activity);
        File installedScripts = new File(activity.getFilesDir(), "spotifyplus-installed-extensions");
        File optimizedDirectory = new File(activity.getCodeCacheDir(), "script-dex/");
        if(!optimizedDirectory.exists()) optimizedDirectory.mkdirs();
        if (LocalExtensionHook.getDeveloperMode(activity)) {
            LocalExtensionHook.refreshLocalExtensions(activity);
        }

        JSONObject hostConfig = new JSONObject();
        try {
            hostConfig.put("elevatedRoot", elevatedScripts.getAbsolutePath());
            hostConfig.put("localRoot", localScripts.getAbsolutePath());
            hostConfig.put("installedRoot", installedScripts.getAbsolutePath());
            hostConfig.put("marketplaceRoot", marketplaceScripts.getAbsolutePath());
            hostConfig.put("developerMode", LocalExtensionHook.getDeveloperMode(activity));
            hostConfig.put("allowElevatedHotReload", BuildConfig.DEBUG);
        } catch (Exception e) {
            Log.e("SpotifyPlus", "Failed to build script host config", e);
        }

        new Thread(() -> {
            ScriptViewHost.preloadNativeLibraries(activity.getApplicationContext());
            SpotifyNativeBridge bridge = new SpotifyNativeBridge(activity.getClassLoader(), marketplaceScripts, optimizedDirectory, activity);
            initializeNativeBridge(bridge, new String[]{"node", hostFile.getAbsolutePath(), hostConfig.toString()});
        }).start();
    }

    private void getAssetFolder(AssetManager assetManager, String assetPath, String outPath) throws IOException {
        String[] assets = assetManager.list(assetPath);
        if (assets == null) return;

        if (assets.length == 0) {
            File outFile = new File(outPath);

            if(outFile.exists()) {
                outFile.setWritable(true, false);
                if(!outFile.delete()) throw new IOException("Failed to delete existing file: " + outFile);
            }

            File parent = outFile.getParentFile();
            if(parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Failed to create directory: " + parent.getAbsolutePath());

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

    public static void send(String id, String type, String name, JSONObject platform) {
        try {
            JSONObject packet = new JSONObject();
            if (!id.isBlank()) packet.put("id", id);
            if (!type.isBlank()) packet.put("type", type);
            if (!name.isBlank()) packet.put("name", name);

            try {
                packet.put("payload", platform);
            } catch (Exception ignored) {
                packet.put("payload", platform.toString());
            }

            instance.sendToNode(packet);
        } catch (Exception e) {
            Log.e("SpotifyPlus", "Failed to queue bridge message", e);
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

            new Handler(Looper.getMainLooper()).post(() -> BridgeMessageBus.dispatch(id, type, name, payload));
        } catch (Exception e) {
            Log.e("SpotifyPlus", "Failed to parse message from node", e);
        }
    }

    private void copyNodeAddon(AssetManager assetManager, File projectDir) throws IOException {
        String abi = android.os.Build.SUPPORTED_ABIS[0];
        String assetPath = "nodejs/addons/" + abi + "/spotifyplus_bridge.node";
        File dest = new File(projectDir, "spotifyplus_bridge.node");

        try (InputStream in = assetManager.open(assetPath); FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            out.flush();

        }

        dest.setReadable(true, false);
        dest.setExecutable(true, false);
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

    @Override
    public void onMessage(String id, String type, String name, JSONObject payload) {
//        String tag = name.split("\\.")[0];
//
//        SpotifyHook hook = handlers.get(tag);
//        if (hook == null) {
//            Log.w("SpotifyPlus", "Unknown command: " + name);
//            return;
//        }
//
//        try {
//            hook.handle(id, name.split("\\.")[1], payload);
//        } catch (Exception e) {
//            Log.e("SpotifyPlus", e.getMessage(), e);
//        }
    }

    private static void extractAndLoad(String apkPath, File outDir, String libName) throws Exception {
        ZipEntry entry = findBestLibEntry(apkPath, libName);
        if (entry == null) {
            Log.d("SpotifyPlus", "Library not found in APK, skipping: " + libName);
            return;
        }

        File outFile = new File(outDir, libName);
        extractEntry(apkPath, entry, outFile);
        Log.d("SpotifyPlus", "Loading " + outFile.getAbsolutePath());
        System.load(outFile.getAbsolutePath());
    }

    private static ZipEntry findBestLibEntry(String apkPath, String libName) throws Exception {
        try (ZipFile zip = new ZipFile(apkPath)) {
            for (String abi : Build.SUPPORTED_ABIS) {
                ZipEntry entry = zip.getEntry("lib/" + abi + "/" + libName);
                if (entry != null) return entry;
            }

            ZipEntry fallback = zip.getEntry("lib/arm64-v8a/" + libName);
            if (fallback != null) return fallback;

            fallback = zip.getEntry("lib/armeabi-v7a/" + libName);
            if (fallback != null) return fallback;

            fallback = zip.getEntry("lib/x86_64/" + libName);
            if (fallback != null) return fallback;

            fallback = zip.getEntry("lib/x86/" + libName);
            return fallback;
        }
    }

    private static void extractEntry(String apkPath, ZipEntry entry, File outFile) throws Exception {
        try (ZipFile zip = new ZipFile(apkPath); InputStream in = zip.getInputStream(entry); FileOutputStream out = new FileOutputStream(outFile, false)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            out.flush();
        }
    }

    public native int startNodeWithArguments(String[] arguments);

    private native void nativeInit();

    private native void nativeSendToNode(String json);

    private native boolean initializeNativeBridge(SpotifyNativeBridge bridge, String[] args);
}
