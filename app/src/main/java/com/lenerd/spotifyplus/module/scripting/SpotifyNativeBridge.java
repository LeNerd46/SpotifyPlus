package com.lenerd.spotifyplus.module.scripting;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;

import com.lenerd.spotifyplus.module.SpotifyHook;
import com.lenerd.spotifyplus.module.Utils;
import com.lenerd.spotifyplus.sdk.spotify.entities.SpotifyTrack;
import com.lenerd.spotifyplus.module.scripting.entities.PlatformData;

import com.lenerd.spotifyplus.module.scripting.nativestuff.NativeComponentRegistry;
import com.lenerd.spotifyplus.module.scripting.nativestuff.SpotifyPlusContextImplementation;
import com.lenerd.spotifyplus.sdk.SpotifyPlusPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SpotifyNativeBridge {
    private static final String TAG = "SpotifyPlus:NativeBridge";
    private static final Map<String, SpotifyHook> handlers = new HashMap<>();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final Map<String, ScriptViewHost> surfaceHosts = new ConcurrentHashMap<>();
    private static final Set<String> registeredSurfaces = ConcurrentHashMap.newKeySet();
    public static final NativeComponentRegistry scriptRegistry = new NativeComponentRegistry();

    static {
        WorkletRuntimeManager manager = WorkletRuntimeManager.getInstance();
        manager.setUpdateSink(SpotifyNativeBridge::applyWorkletUpdates);
        manager.setHostFunctionSink(new WorkletRuntimeManager.HostFunctionSink() {
            @Override
            public String measure(String surfaceId, int nodeId) {
                ScriptViewHost host = surfaceHosts.get(surfaceId);
                return host != null ? host.measureWorkletView(nodeId) : "null";
            }

            @Override
            public String getRelativeCoords(String surfaceId, int nodeId, double absoluteX, double absoluteY) {
                ScriptViewHost host = surfaceHosts.get(surfaceId);
                return host != null
                    ? host.getWorkletRelativeCoords(nodeId, absoluteX, absoluteY)
                    : "null";
            }

            @Override
            public boolean scrollTo(String surfaceId, int nodeId, double x, double y, boolean animated) {
                ScriptViewHost host = surfaceHosts.get(surfaceId);
                return host != null && host.scrollWorkletView(nodeId, x, y, animated);
            }

            @Override
            public boolean dispatchCommand(String surfaceId, int nodeId, String command, String argsJson) {
                ScriptViewHost host = surfaceHosts.get(surfaceId);
                return host != null && host.dispatchWorkletCommand(nodeId, command, argsJson);
            }
        });
        manager.setSourceLifecycleSink(new WorkletRuntimeManager.SourceLifecycleSink() {
            @Override
            public void activate(String sourceId, String configJson) {
                setScrollOffsetSourceActive(sourceId, configJson, true);
            }

            @Override
            public void deactivate(String sourceId, String configJson) {
                setScrollOffsetSourceActive(sourceId, configJson, false);
            }
        });
    }

    private static void setScrollOffsetSourceActive(
        String sourceId,
        String configJson,
        boolean active
    ) {
        if (sourceId == null || !sourceId.startsWith("scrollOffset:")) return;
        int nodeId = -1;
        try {
            nodeId = Integer.parseInt(sourceId.substring("scrollOffset:".length()));
        } catch (Exception ignored) {
            try {
                JSONObject config = new JSONObject(configJson != null ? configJson : "{}");
                nodeId = config.optInt("viewTag", -1);
            } catch (Exception ignoredConfig) {
                return;
            }
        }
        if (nodeId < 0) return;
        for (ScriptViewHost host : surfaceHosts.values()) {
            if (host.setScrollOffsetSourceActive(nodeId, active)) return;
        }
        Log.w(TAG, "Scroll offset source " + sourceId + " did not resolve to a mounted view");
    }

    private final ClassLoader classLoader;
    private final File scriptDirectory;
    private final File optimizedDirectory;
    private final Context context;

    public static class StorageReadResult {
        public boolean found;
        public String type;
        public String value;
        public String data;

        public StorageReadResult() {
            this(false, "", null, null);
        }

        public StorageReadResult(boolean found, String type, String value, String data) {
            this.found = found;
            this.type = type;
            this.value = value;
            this.data = data;
        }
    }

    public SpotifyNativeBridge(ClassLoader classLoader, File scriptDirectory, File optimizedDirectory, Context context) {
        this.classLoader = classLoader;
        this.scriptDirectory = scriptDirectory;
        this.optimizedDirectory = optimizedDirectory;
        this.context = context;
        WorkletRuntimeManager.getInstance().attachContext(context);
    }

    private static Object invokeHandler(String type, String id, Object... args) {
        SpotifyHook hook = handlers.get(type);
        if (hook == null) {
            Log.w(TAG, "Handler not registered for type: " + type);
            return null;
        }

        try {
            return hook.handle(id, args);
        } catch (Throwable e) {
            Log.e(TAG, "Failed to invoke handler " + type + ":" + id, e);
            return null;
        }
    }

    public void loadApk(String scriptId, String apkPath, String pluginClass) {
        try {
            Log.d("DexLoader", "Loading " + pluginClass);
            File apkFile = new File(apkPath);
            apkFile.setReadable(true, false);
            apkFile.setWritable(false, false);
            apkFile.setExecutable(false, false);
            Log.d("DexLoader", apkFile.getAbsolutePath());

            if (apkFile.exists()) {
                Log.d("DexLoader", "APK file exists!");
                ScriptDexLoader loader = new ScriptDexLoader(context);
                ClassLoader scriptLoader = loader.loadDex(apkFile, optimizedDirectory, SpotifyPlusPlugin.class.getClassLoader());

                try {
                    dalvik.system.DexFile dex = new dalvik.system.DexFile(apkFile);
                    java.util.Enumeration<String> entries = dex.entries();

                    while (entries.hasMoreElements()) {
                        String name = entries.nextElement();
                        if (name.contains("lyrics")) Log.d("DexLoader", "Class in dex: " + name);
                    }

                    dex.close();
                } catch (Throwable t) {
                    Log.e("DexLoader", "Failed listing dex classes", t);
                }

//                SpotifyPlusPlugin plugin = loader.loadPluginFromDexFile(apkFile, optimizedDirectory, SpotifyPlusPlugin.class.getClassLoader(), pluginClass);
                SpotifyPlusPlugin plugin = loader.loadPlugin(scriptLoader, pluginClass);
                SpotifyPlusContextImplementation context = new SpotifyPlusContextImplementation(this);
                scriptRegistry.setContext(context);
                scriptRegistry.beginScriptRegistration(scriptId);
                try {
                    plugin.register(scriptRegistry, context);
                } finally {
                    scriptRegistry.endScriptRegistration();
                }
            } else {
                Log.d("DexLoader", "Did not find " + apkFile.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load APK file " + apkPath, e);
        }
    }

    private static void applyWorkletUpdates(
        long frameTimeNanos,
        String[] surfaceIds,
        int[] nodeIds,
        String[] properties,
        int[] valueTypes,
        double[] numberValues,
        String[] stringValues
    ) {
        Map<String, PendingWorkletProperty> pendingProperties = new LinkedHashMap<>();
        Map<String, Boolean> touchedSurfaces = new HashMap<>();
        for (int index = 0; index < surfaceIds.length; index += 1) {
            ScriptViewHost host = surfaceHosts.get(surfaceIds[index]);
            if (host == null) continue;

            String[] path = decodeWorkletPropertyPath(properties[index]);
            if (path.length == 0 || path[0].isEmpty()) continue;
            Object value = decodeWorkletValue(
                valueTypes[index],
                numberValues[index],
                stringValues[index]
            );
            if (value == UNKNOWN_WORKLET_VALUE) {
                Log.w(TAG, "Ignoring unknown worklet value type " + valueTypes[index]);
                continue;
            }

            String key = surfaceIds[index] + "\u0000" + nodeIds[index] + "\u0000" + path[0];
            PendingWorkletProperty pending = pendingProperties.get(key);
            if (pending == null) {
                pending = new PendingWorkletProperty(
                    surfaceIds[index],
                    host,
                    nodeIds[index],
                    path[0]
                );
                pendingProperties.put(key, pending);
            }

            try {
                pending.assign(path, value);
            } catch (Exception error) {
                Log.e(TAG, "Failed reconstructing worklet property " + properties[index], error);
            }
        }

        for (PendingWorkletProperty pending : pendingProperties.values()) {
            boolean needsLayout = pending.host.applyWorkletProperty(
                pending.nodeId,
                pending.property,
                pending.value == JSONObject.NULL ? null : pending.value
            );
            touchedSurfaces.merge(pending.surfaceId, needsLayout, (left, right) -> left || right);
        }

        for (Map.Entry<String, Boolean> entry : touchedSurfaces.entrySet()) {
            ScriptViewHost host = surfaceHosts.get(entry.getKey());
            if (host != null) host.finishWorkletFrame(entry.getValue());
        }
    }

    private static final Object UNKNOWN_WORKLET_VALUE = new Object();

    private static Object decodeWorkletValue(int valueType, double numberValue, String stringValue) {
        switch (valueType) {
            case WorkletRuntimeManager.VALUE_TYPE_NULL:
                return JSONObject.NULL;
            case WorkletRuntimeManager.VALUE_TYPE_NUMBER:
                return numberValue;
            case WorkletRuntimeManager.VALUE_TYPE_BOOLEAN:
                return numberValue != 0;
            case WorkletRuntimeManager.VALUE_TYPE_STRING:
                return stringValue;
            case WorkletRuntimeManager.VALUE_TYPE_OBJECT:
                return new JSONObject();
            case WorkletRuntimeManager.VALUE_TYPE_ARRAY:
                return new JSONArray();
            default:
                return UNKNOWN_WORKLET_VALUE;
        }
    }

    private static String[] decodeWorkletPropertyPath(String propertyPath) {
        if (propertyPath == null || propertyPath.isEmpty()) return new String[0];
        String[] segments = propertyPath.split("/", -1);
        for (int index = 0; index < segments.length; index += 1) {
            segments[index] = segments[index]
                .replace("~1", "/")
                .replace("~0", "~");
        }
        return segments;
    }

    private static final class PendingWorkletProperty {
        final String surfaceId;
        final ScriptViewHost host;
        final int nodeId;
        final String property;
        Object value = JSONObject.NULL;

        PendingWorkletProperty(
            String surfaceId,
            ScriptViewHost host,
            int nodeId,
            String property
        ) {
            this.surfaceId = surfaceId;
            this.host = host;
            this.nodeId = nodeId;
            this.property = property;
        }

        void assign(String[] path, Object nextValue) throws Exception {
            if (path.length == 1) {
                value = nextValue;
                return;
            }

            if (value == JSONObject.NULL) {
                value = isArrayIndex(path[1]) ? new JSONArray() : new JSONObject();
            }

            Object parent = value;
            for (int index = 1; index < path.length - 1; index += 1) {
                String segment = path[index];
                String nextSegment = path[index + 1];
                Object child = getChild(parent, segment);
                if (child == null || child == JSONObject.NULL) {
                    child = isArrayIndex(nextSegment) ? new JSONArray() : new JSONObject();
                    setChild(parent, segment, child);
                }
                parent = child;
            }

            setChild(parent, path[path.length - 1], nextValue);
        }

        private static Object getChild(Object parent, String segment) {
            if (parent instanceof JSONObject object) {
                return object.opt(segment);
            }
            if (parent instanceof JSONArray array && isArrayIndex(segment)) {
                return array.opt(Integer.parseInt(segment));
            }
            return null;
        }

        private static void setChild(Object parent, String segment, Object child) throws Exception {
            Object safeChild = child == null ? JSONObject.NULL : child;
            if (parent instanceof JSONObject object) {
                object.put(segment, safeChild);
                return;
            }
            if (parent instanceof JSONArray array && isArrayIndex(segment)) {
                int arrayIndex = Integer.parseInt(segment);
                while (array.length() <= arrayIndex) array.put(JSONObject.NULL);
                array.put(arrayIndex, safeChild);
                return;
            }
            throw new IllegalArgumentException("Invalid worklet property container at " + segment);
        }

        private static boolean isArrayIndex(String value) {
            if (value == null || value.isEmpty()) return false;
            for (int index = 0; index < value.length(); index += 1) {
                if (!Character.isDigit(value.charAt(index))) return false;
            }
            return true;
        }
    }

    public void unregisterScript(String scriptId) {
        scriptRegistry.unregisterScript(scriptId);
        Set<String> removedAssets = ExtensionAssetRegistry.unregisterScript(scriptId);
        ScriptViewHost.evictExtensionAssetCaches(removedAssets);
        try {
            invokeHandler("menu", "unregisterScript", scriptId);
            invokeHandler("side", "unregisterScript", scriptId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister script " + scriptId, e);
        }
    }

    public PlatformData getPlatformData() {
        return Utils.platformData;
    }

    public String getAccessToken() {
        return Utils.token;
    }

    public SpotifyTrack getCurrentTrack() {
        return Utils.getTrack(classLoader);
    }

    public SpotifyTrack getTrack(String uri) {
        return null;
    }

    public double getPlaybackPosition() {
        try {
            return Utils.getCurrentPlaybackPosition();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get playback position", e);
            return 0.0;
        }
    }

    public void seek(long position) {
        try {
            SpotifyHook hook = handlers.get("player");
            if (hook == null) {
                Log.w(TAG, "Player hook not registered");
                return;
            }

            hook.handle("seek", new Object[]{position});
        } catch (Exception e) {
            Log.e(TAG, "Failed to seek", e);
        }
    }

    public void play() {
        try {
            SpotifyHook hook = handlers.get("player");
            if (hook == null) {
                Log.w(TAG, "Player hook not registered");
                return;
            }

            hook.handle("play", new Object[]{});
        } catch (Exception e) {
            Log.e(TAG, "Failed to play", e);
        }
    }

    public void pause() {
        try {
            SpotifyHook hook = handlers.get("player");
            if (hook == null) {
                Log.w(TAG, "Player hook not registered");
                return;
            }

            hook.handle("pause", new Object[]{});
        } catch (Exception e) {
            Log.e(TAG, "Failed to pause", e);
        }
    }

    public void togglePlay() {
        try {
            SpotifyHook hook = handlers.get("player");
            if (hook == null) {
                Log.w(TAG, "Player hook not registered");
                return;
            }

            hook.handle("togglePlay", new Object[]{});
        } catch (Exception e) {
            Log.e(TAG, "Failed to togglePlay", e);
        }
    }

    public void skipNext() {
        try {
            SpotifyHook hook = handlers.get("player");
            if (hook == null) {
                Log.w(TAG, "Player hook not registered");
                return;
            }

            hook.handle("skipNext", new Object[]{});
        } catch (Exception e) {
            Log.e(TAG, "Failed to skipNext", e);
        }
    }

    public void skipPrevious() {
        try {
            SpotifyHook hook = handlers.get("player");
            if (hook == null) {
                Log.w(TAG, "Player hook not registered");
                return;
            }

            hook.handle("skipPrevious", new Object[]{});
        } catch (Exception e) {
            Log.e(TAG, "Failed to skipPrevious", e);
        }
    }

    public void toast(String text, boolean longLength) {
        try {
            invokeHandler("ui", "toast", text, longLength);
        } catch (Exception e) {
            Log.e(TAG, "Failed to show toast", e);
        }
    }

    public boolean navigate(String uri, String target) {
        try {
            Object result = invokeHandler("system", "navigate", uri, target);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            Log.e(TAG, "Failed to navigate", e);
            return false;
        }
    }

    /** Retained for native bridges extracted by an earlier SpotifyPlus build. */
    public void openUri(String uri) {
        navigate(uri, "auto");
    }

    public boolean navigateBack() {
        try {
            Object result = invokeHandler("system", "navigateBack");
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            Log.e(TAG, "Failed to navigate back", e);
            return false;
        }
    }

    public void storageSet(String scriptId, String key, String value) {
        try {
            invokeHandler("storage", "set", scriptId, key, value);
        } catch (Exception e) {
            Log.e(TAG, "Failed to storageSet", e);
        }
    }

    public String storageGet(String scriptId, String key) {
        try {
            Object result = invokeHandler("storage", "get", scriptId, key);
            return result instanceof String ? (String) result : null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to storageGet", e);
            return null;
        }
    }

    public void storageRemove(String scriptId, String key) {
        try {
            invokeHandler("storage", "remove", scriptId, key);
        } catch (Exception e) {
            Log.e(TAG, "Failed to storageRemove", e);
        }
    }

    public void storageWriteText(String scriptId, String path, String value) {
        try {
            invokeHandler("storage", "writeText", scriptId, path, value);
        } catch (Exception e) {
            Log.e(TAG, "Failed to storageWriteText", e);
        }
    }

    public void storageWriteJson(String scriptId, String path, String value) {
        try {
            invokeHandler("storage", "writeJson", scriptId, path, value);
        } catch (Exception e) {
            Log.e(TAG, "Failed to storageWriteJson", e);
        }
    }

    public void storageWriteBinary(String scriptId, String path, String data) {
        try {
            invokeHandler("storage", "writeBinary", scriptId, path, data);
        } catch (Exception e) {
            Log.e(TAG, "Failed to storageWriteBinary", e);
        }
    }

    public StorageReadResult storageRead(String scriptId, String path) {
        try {
            Object result = invokeHandler("storage", "read", scriptId, path);
            return result instanceof StorageReadResult ? (StorageReadResult) result : new StorageReadResult();
        } catch (Exception e) {
            Log.e(TAG, "Failed to storageRead", e);
            return new StorageReadResult();
        }
    }

    public boolean getDeveloperMode() {
        try {
            Object result = invokeHandler("elevated", "getDeveloperMode");
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            Log.e(TAG, "Failed to getDeveloperMode", e);
            return false;
        }
    }

    public void setDeveloperMode(boolean enabled) {
        try {
            invokeHandler("elevated", "setDeveloperMode", enabled);
        } catch (Exception e) {
            Log.e(TAG, "Failed to setDeveloperMode", e);
        }
    }

    public boolean pickLocalExtensionsFolder() {
        try {
            Object result = invokeHandler("elevated", "pickLocalExtensionsFolder");
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            Log.e(TAG, "Failed to pickLocalExtensionsFolder", e);
            return false;
        }
    }

    public String getLocalExtensionsFolderDisplayName() {
        try {
            Object result = invokeHandler("elevated", "getLocalExtensionsFolderDisplayName");
            return result instanceof String ? (String) result : null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to getLocalExtensionsFolderDisplayName", e);
            return null;
        }
    }

    public String listLocalExtensions() {
        try {
            Object result = invokeHandler("elevated", "listLocalExtensions");
            return result instanceof String ? (String) result : "[]";
        } catch (Exception e) {
            Log.e(TAG, "Failed to listLocalExtensions", e);
            return "[]";
        }
    }

    public String refreshLocalExtensions() {
        try {
            Object result = invokeHandler("elevated", "refreshLocalExtensions");
            return result instanceof String ? (String) result : "[]";
        } catch (Exception e) {
            Log.e(TAG, "Failed to refreshLocalExtensions", e);
            return "[]";
        }
    }

    public void registerContextMenu(String id, String scriptId, String title) {
        try {
            invokeHandler("menu", "register", id, scriptId, title);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register context menu", e);
        }
    }

    public void registerSideDrawer(String id, String scriptId, String title) {
        try {
            invokeHandler("side", "register", id, scriptId, title);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register side drawer", e);
        }
    }

    public void registerSideDrawerWithIcon(String id, String scriptId, String title, String iconRegistrationJson) {
        try {
            JSONObject registration = new JSONObject(iconRegistrationJson);
            ExtensionAssetRegistry.register(registration);
            invokeHandler("side", "register", id, scriptId, title, registration.getString("assetId"));
        } catch (Exception e) {
            Log.e(TAG, "Failed to register side drawer icon", e);
            registerSideDrawer(id, scriptId, title);
        }
    }

    public void registerSurface(String surfaceId) {
        registeredSurfaces.add(surfaceId);
    }

    public void unregisterSurface(String surfaceId) {
        if (!registeredSurfaces.remove(surfaceId)) return;
        invokeHandler("side", "surfaceClosed", surfaceId);
    }

    public void commitSurface(String surfaceId, String opsJson) {
        if (!registeredSurfaces.contains(surfaceId)) return;

        mainHandler.post(() -> {
            try {
                applySurfaceOpsNow(surfaceId, new JSONArray(opsJson));
            } catch (Exception e) {
                Log.e(TAG, "Failed to apply commit for surface " + surfaceId, e);
            }
        });
    }

    /** Called from native platform/task-runner threads; the manager posts to main. */
    public void requestWorkletFrame() {
        WorkletRuntimeManager.getInstance().requestFrame();
    }

    /** Called once for each non-empty native worklet frame, already on main. */
    public void dispatchWorkletUpdates(
        long frameTimeNanos,
        String[] surfaceIds,
        int[] nodeIds,
        String[] properties,
        int[] valueTypes,
        double[] numberValues,
        String[] stringValues
    ) {
        WorkletRuntimeManager.getInstance().dispatchUpdates(
            frameTimeNanos,
            surfaceIds,
            nodeIds,
            properties,
            valueTypes,
            numberValues,
            stringValues
        );
    }

    public String measureWorkletView(String surfaceId, int nodeId) {
        return WorkletRuntimeManager.getInstance().measure(surfaceId, nodeId);
    }

    public String getWorkletRelativeCoords(
        String surfaceId,
        int nodeId,
        double absoluteX,
        double absoluteY
    ) {
        return WorkletRuntimeManager.getInstance().getRelativeCoords(
            surfaceId,
            nodeId,
            absoluteX,
            absoluteY
        );
    }

    public boolean scrollWorkletView(
        String surfaceId,
        int nodeId,
        double x,
        double y,
        boolean animated
    ) {
        return WorkletRuntimeManager.getInstance().scrollTo(
            surfaceId,
            nodeId,
            x,
            y,
            animated
        );
    }

    public boolean dispatchWorkletCommand(
        String surfaceId,
        int nodeId,
        String command,
        String argsJson
    ) {
        return WorkletRuntimeManager.getInstance().dispatchCommand(
            surfaceId,
            nodeId,
            command,
            argsJson
        );
    }

    public void activateWorkletSource(String sourceId, String configJson) {
        WorkletRuntimeManager.getInstance().activateSource(sourceId, configJson);
    }

    public void deactivateWorkletSource(String sourceId, String configJson) {
        WorkletRuntimeManager.getInstance().deactivateSource(sourceId, configJson);
    }

    public static void attachSurfaceHost(String surfaceId, ViewGroup root) {
        Runnable attach = () -> {
            var existing = surfaceHosts.get(surfaceId);
            if (existing != null) {
                if (existing.getHostRoot() == root) return;
                existing.dispose();
            }

            surfaceHosts.put(surfaceId, new ScriptViewHost(surfaceId, root));
        };

        if (Looper.myLooper() == Looper.getMainLooper()) attach.run();
        else mainHandler.post(attach);
    }

    public static void detachSurfaceHost(String surfaceId) {
        Runnable detach = () -> {
            ScriptViewHost existing = surfaceHosts.remove(surfaceId);
            if (existing != null) existing.dispose();
        };

        if (Looper.myLooper() == Looper.getMainLooper()) detach.run();
        else mainHandler.post(detach);
    }

    public static void applySurfaceOps(String surfaceId, JSONArray ops) {
        mainHandler.post(() -> applySurfaceOpsNow(surfaceId, ops));
    }

    private static void applySurfaceOpsNow(String surfaceId, JSONArray ops) {
        ScriptViewHost host = surfaceHosts.get(surfaceId);
        if (host == null) {
            Log.w(TAG, "commitSurface ignored because host was missing for " + surfaceId);
            return;
        }

        host.applyOps(ops);
    }

    public static void registerHandler(String type, SpotifyHook hook) {
        handlers.put(type, hook);
    }

    public static native void sendEvent(String type, String payload);
}
