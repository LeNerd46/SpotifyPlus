package com.lenerd.spotifyplus.module.hooks;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;
import com.lenerd.spotifyplus.manager.bridge.BridgeClient;
import com.lenerd.spotifyplus.module.SpotifyCallback;
import com.lenerd.spotifyplus.module.SpotifyHook;
import com.lenerd.spotifyplus.module.scripting.ScriptManager;
import com.lenerd.spotifyplus.module.scripting.SpotifyNativeBridge;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class StorageHook extends SpotifyHook {
    private static final String CACHE_SCRIPT_ID_PREFIX = "spotifyplus-cache:";
    private static final String FILE_DELETE_SCRIPT_ID_PREFIX = "spotifyplus-delete:";
    private static final String CACHE_DIRECTORY = "spotifyplus-storage";
    private static final String TYPE_TEXT = "text";
    private static final String TYPE_JSON = "json";
    private static final String TYPE_BINARY = "binary";

    @Override
    protected void hookSetup() throws NoSuchMethodException, ClassNotFoundException, NoSuchFieldException {
        SpotifyNativeBridge.registerHandler("storage", this);
    }

    @Override
    protected void beforeHook(SpotifyCallback callback) {
    }

    @Override
    protected void afterHook(SpotifyCallback callback) {
    }

    @Override
    public Object handle(String command, Object[] args) {
        return switch (command) {
            case "set" -> {
                handleSet((String) args[0], (String) args[1], args.length > 2 ? args[2] : null);
                yield null;
            }
            case "get" -> handleGet((String) args[0], (String) args[1]);
            case "remove" -> {
                handleRemove((String) args[0], (String) args[1]);
                yield null;
            }
            case "writeText" -> {
                handleWriteText((String) args[0], (String) args[1], (String) args[2]);
                yield null;
            }
            case "writeJson" -> {
                handleWriteJson((String) args[0], (String) args[1], (String) args[2]);
                yield null;
            }
            case "writeBinary" -> {
                handleWriteBinary((String) args[0], (String) args[1], (String) args[2]);
                yield null;
            }
            case "read" -> handleRead((String) args[0], (String) args[1]);
            default -> null;
        };
    }

    private void handleSet(String scriptId, String key, Object value) {
        try {
            if(currentActivity == null) return;

            SharedPreferences prefs = currentActivity.getSharedPreferences(scriptId, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            if(value == null || value == JSONObject.NULL) editor.remove(key).apply();
            else editor.putString(key, String.valueOf(value)).apply();
        } catch(Exception e) {
            logError(e);
        }
    }

    private String handleGet(String scriptId, String key) {
        try {
            if(currentActivity == null) return null;

            SharedPreferences prefs = currentActivity.getSharedPreferences(scriptId, Context.MODE_PRIVATE);
            Object value = prefs.getAll().get(key);
            return value == null ? null : String.valueOf(value);
        } catch(Exception e) {
            logError(e);
            return null;
        }
    }

    private void handleRemove(String scriptId, String key) {
        try {
            if(currentActivity == null) return;

            if(scriptId.startsWith(FILE_DELETE_SCRIPT_ID_PREFIX)) {
                handleDelete(scriptId.substring(FILE_DELETE_SCRIPT_ID_PREFIX.length()), key);
                return;
            }

            SharedPreferences prefs = currentActivity.getSharedPreferences(scriptId, Context.MODE_PRIVATE);
            prefs.edit().remove(key).apply();
        } catch(Exception e) {
            logError(e);
        }
    }

    private void handleWriteText(String scriptId, String scriptPath, String value) {
        writeFile(scriptId, scriptPath, value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8), TYPE_TEXT);
    }

    private void handleWriteJson(String scriptId, String scriptPath, String value) {
        writeFile(scriptId, scriptPath, value == null ? "null".getBytes(StandardCharsets.UTF_8) : value.getBytes(StandardCharsets.UTF_8), TYPE_JSON);
    }

    private void handleWriteBinary(String scriptId, String scriptPath, String data) {
        writeFile(scriptId, scriptPath, data == null ? new byte[0] : Base64.decode(data, Base64.NO_WRAP), TYPE_BINARY);
    }

    private SpotifyNativeBridge.StorageReadResult handleRead(String scriptId, String scriptPath) {
        try {
            if(currentActivity == null) return new SpotifyNativeBridge.StorageReadResult();

            File scriptDir = getScriptDirectory(scriptId);
            File file = resolveScriptFile(scriptDir, scriptPath);

            if(!file.exists() || !file.isFile()) return new SpotifyNativeBridge.StorageReadResult();

            byte[] bytes = Files.readAllBytes(file.toPath());
            String type = readMetaType(file);
            if(type == null || type.isEmpty()) type = isValidUtf8(bytes) ? TYPE_TEXT : TYPE_BINARY;

            if(TYPE_BINARY.equals(type)) {
                String data = Base64.encodeToString(bytes, Base64.NO_WRAP);
                return new SpotifyNativeBridge.StorageReadResult(true, TYPE_BINARY, null, data);
            }

            String value = new String(bytes, StandardCharsets.UTF_8);
            if(TYPE_JSON.equals(type)) return new SpotifyNativeBridge.StorageReadResult(true, TYPE_JSON, value, null);

            return new SpotifyNativeBridge.StorageReadResult(true, TYPE_TEXT, value, null);
        } catch(Exception e) {
            logError(e);
            return new SpotifyNativeBridge.StorageReadResult();
        }
    }

    private void writeFile(String scriptId, String scriptPath, byte[] bytes, String type) {
        try {
            if(currentActivity == null) return;

            File scriptDir = getScriptDirectory(scriptId);
            if(!scriptDir.exists()) scriptDir.mkdirs();

            File file = resolveScriptFile(scriptDir, scriptPath);
            File parent = file.getParentFile();
            if(parent != null && !parent.exists()) parent.mkdirs();

            Files.write(file.toPath(), bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            writeMetaType(file, type);
        } catch(Exception e) {
            logError(e);
        }
    }

    private void handleDelete(String scriptId, String scriptPath) {
        try {
            if(currentActivity == null) return;

            File scriptDir = getScriptDirectory(scriptId);
            File file = resolveScriptFile(scriptDir, scriptPath);
            if(file.isDirectory()) throw new IOException("Storage delete only supports files");

            Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(getMetaFile(file).toPath());
        } catch(Exception e) {
            logError(e);
        }
    }

    private File getScriptDirectory(String scriptId) throws IOException {
        boolean cache = scriptId.startsWith(CACHE_SCRIPT_ID_PREFIX);
        String resolvedScriptId = cache ? scriptId.substring(CACHE_SCRIPT_ID_PREFIX.length()) : scriptId;
        File storageRoot = cache
                ? new File(currentActivity.getCacheDir(), CACHE_DIRECTORY)
                : currentActivity.getFilesDir();

        return resolveScriptFile(storageRoot, resolvedScriptId);
    }

    private File resolveScriptFile(File scriptDir, String relativePath) throws IOException {
        File target = new File(scriptDir, relativePath);

        String root = scriptDir.getCanonicalPath();
        String path = target.getCanonicalPath();

        if (!path.startsWith(root + File.separator) && !path.equals(root)) {
            throw new SecurityException("Path escapes script sandbox");
        }

        return target;
    }

    private File getMetaFile(File file) {
        return new File(file.getParentFile(), "." + file.getName() + ".spmeta");
    }

    private void writeMetaType(File file, String type) throws IOException {
        File meta = getMetaFile(file);
        Files.write(meta.toPath(), type.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private String readMetaType(File file) {
        try {
            File meta = getMetaFile(file);
            if (!meta.exists()) return null;
            return new String(Files.readAllBytes(meta.toPath()), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isValidUtf8(byte[] bytes) {
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
            decoder.onMalformedInput(CodingErrorAction.REPORT);
            decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
